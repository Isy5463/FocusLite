package com.focuslite.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focuslite.app.FocusLiteApplication
import com.focuslite.app.data.local.datastore.FocusPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessibilitySvc"

        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_ASIA = "com.ss.android.ugc.trill"

        private const val BLOCK_ACTION_COOLDOWN_MS = 900L
        private const val SCAN_DEBOUNCE_MS = 180L
        private const val DM_CONTEXT_EXPIRY_MS = 15_000L

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentPrefs = FocusPreferences()

    private var lastBlockTimestamp: Long = 0L
    private var lastTreeScanTimestamp: Long = 0L

    private enum class IgNavigationContext {
        UNKNOWN,
        DIRECT_THREAD_OR_INBOX,
        ALLOWED_SINGLE_DM_REEL,
        OTHER_SCREEN
    }

    private var igContextState: IgNavigationContext = IgNavigationContext.UNKNOWN
    private var lastDmContextSeenTimestamp: Long = 0L
    private var dmReelOpenedTimestamp: Long = 0L
    private var initialReelSignature: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "FocusAccessibilityService erfolgreich verbunden.")

        val app = applicationContext as? FocusLiteApplication
        if (app != null) {
            serviceScope.launch {
                app.repository.preferencesFlow.collectLatest { prefs ->
                    currentPrefs = prefs
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        val now = System.currentTimeMillis()
        if (currentPrefs.pauseUntilTimestamp > now) {
            return
        }

        if (packageName == PKG_INSTAGRAM &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            igContextState == IgNavigationContext.ALLOWED_SINGLE_DM_REEL
        ) {
            handleInstagramDmReelScroll(event, now)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastTreeScanTimestamp < SCAN_DEBOUNCE_MS) {
                return
            }
        }
        lastTreeScanTimestamp = now

        val rootNode = rootInActiveWindow ?: return

        try {
            when (packageName) {
                PKG_INSTAGRAM -> inspectInstagram(rootNode, event, now)
                PKG_YOUTUBE -> inspectYouTube(rootNode, now)
                PKG_TIKTOK_GLOBAL, PKG_TIKTOK_ASIA -> inspectTikTok(rootNode, now)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Analyse des UI-Baums für $packageName", e)
        }
    }

    private fun inspectInstagram(
        rootNode: AccessibilityNodeInfo,
        event: AccessibilityEvent,
        now: Long
    ) {
        val snapshot = scanInstagramTree(rootNode)

        if (snapshot.isInDirectMessages && !snapshot.isReelsViewerActive) {
            igContextState = IgNavigationContext.DIRECT_THREAD_OR_INBOX
            lastDmContextSeenTimestamp = now
            initialReelSignature = null
            return
        }

        if (currentPrefs.igBlockExplore && snapshot.isExploreGridActive && !snapshot.isSearchInputFocused) {
            triggerBlockAction(
                packageName = PKG_INSTAGRAM,
                reason = "Instagram Explore",
                toastMessage = "Explore-Feed ist blockiert – nutze die gezielte Suche!"
            )
            return
        }

        if (currentPrefs.igBlockReels && snapshot.isReelsViewerActive) {
            if (snapshot.isReelsBottomTabSelected) {
                igContextState = IgNavigationContext.OTHER_SCREEN
                triggerBlockAction(
                    packageName = PKG_INSTAGRAM,
                    reason = "Instagram Reels Tab",
                    toastMessage = "Reels-Tab ist blockiert – Fokus behalten!"
                )
                return
            }

            if (currentPrefs.igAllowSingleReelFromDm) {
                val cameFromDmRecently = (igContextState == IgNavigationContext.DIRECT_THREAD_OR_INBOX) ||
                        (now - lastDmContextSeenTimestamp < DM_CONTEXT_EXPIRY_MS)

                if (cameFromDmRecently && igContextState != IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
                    igContextState = IgNavigationContext.ALLOWED_SINGLE_DM_REEL
                    dmReelOpenedTimestamp = now
                    initialReelSignature = snapshot.currentReelSignature
                    return
                }

                if (igContextState == IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
                    if (initialReelSignature != null &&
                        snapshot.currentReelSignature != null &&
                        snapshot.currentReelSignature != initialReelSignature &&
                        (now - dmReelOpenedTimestamp > 700L)
                    ) {
                        igContextState = IgNavigationContext.OTHER_SCREEN
                        triggerBlockAction(
                            packageName = PKG_INSTAGRAM,
                            reason = "Instagram DM Reel Weiterscrollen",
                            toastMessage = "Nur 1 geschicktes Reel erlaubt – kein Weiterscrollen!"
                        )
                    }
                    return
                }
            }

            triggerBlockAction(
                packageName = PKG_INSTAGRAM,
                reason = "Instagram Reels Player",
                toastMessage = "Reels sind blockiert – Fokus behalten!"
            )
            return
        }

        if (!snapshot.isInDirectMessages && !snapshot.isReelsViewerActive) {
            if (igContextState == IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
                igContextState = IgNavigationContext.DIRECT_THREAD_OR_INBOX
                lastDmContextSeenTimestamp = now
            } else if (now - lastDmContextSeenTimestamp > DM_CONTEXT_EXPIRY_MS) {
                igContextState = IgNavigationContext.OTHER_SCREEN
            }
        }
    }

    private fun handleInstagramDmReelScroll(event: AccessibilityEvent, now: Long) {
        if (now - dmReelOpenedTimestamp < 650L) return

        val className = event.className?.toString().orEmpty()
        val isPagerOrRecyclerScroll = className.contains("ViewPager", ignoreCase = true) ||
                className.contains("RecyclerView", ignoreCase = true)

        val scrolledToNextPage = event.fromIndex >= 1 || event.toIndex >= 1
        val significantVerticalDelta = kotlin.math.abs(event.scrollDeltaY) > 180

        if (isPagerOrRecyclerScroll && (scrolledToNextPage || significantVerticalDelta)) {
            igContextState = IgNavigationContext.DIRECT_THREAD_OR_INBOX
            triggerBlockAction(
                packageName = PKG_INSTAGRAM,
                reason = "Instagram DM Reel Scroll",
                toastMessage = "Geschicktes Reel beendet – Weiterscrollen blockiert!"
            )
        }
    }

    private data class InstagramTreeSnapshot(
        var isInDirectMessages: Boolean = false,
        var isReelsViewerActive: Boolean = false,
        var isReelsBottomTabSelected: Boolean = false,
        var isExploreGridActive: Boolean = false,
        var isSearchInputFocused: Boolean = false,
        var currentReelSignature: String? = null
    )

    private fun scanInstagramTree(root: AccessibilityNodeInfo): InstagramTreeSnapshot {
        val snapshot = InstagramTreeSnapshot()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var nodesVisited = 0
        val maxNodes = 350

        while (queue.isNotEmpty() && nodesVisited < maxNodes) {
            val node = queue.removeFirst()
            nodesVisited++

            val viewId = node.viewIdResourceName.orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val className = node.className?.toString().orEmpty()

            if (viewId.contains("row_thread_composer", ignoreCase = true) ||
                viewId.contains("direct_thread", ignoreCase = true) ||
                viewId.contains("thread_list", ignoreCase = true) ||
                viewId.contains("direct_inbox", ignoreCase = true) ||
                contentDesc.equals("Nachricht senden", ignoreCase = true) ||
                contentDesc.equals("Message…", ignoreCase = true) ||
                text.equals("Nachricht…", ignoreCase = true)
            ) {
                snapshot.isInDirectMessages = true
            }

            if (viewId.contains("clips_viewer_view_pager", ignoreCase = true) ||
                viewId.contains("clips_viewer_root", ignoreCase = true) ||
                viewId.contains("reel_viewer_root", ignoreCase = true) ||
                viewId.contains("clips_video_container", ignoreCase = true)
            ) {
                snapshot.isReelsViewerActive = true
            }

            if ((viewId.contains("clips_tab", ignoreCase = true) ||
                        contentDesc.equals("Reels", ignoreCase = true)) && node.isSelected
            ) {
                snapshot.isReelsBottomTabSelected = true
                snapshot.isReelsViewerActive = true
            }

            if (snapshot.currentReelSignature == null &&
                (viewId.contains("clips_author_username", ignoreCase = true) ||
                        viewId.contains("clips_caption", ignoreCase = true)) &&
                text.isNotBlank()
            ) {
                snapshot.currentReelSignature = text.take(40)
            }

            if (viewId.contains("explore_grid", ignoreCase = true) ||
                viewId.contains("search_ighv_recyclerview", ignoreCase = true) ||
                (viewId.contains("search_tab", ignoreCase = true) && node.isSelected)
            ) {
                snapshot.isExploreGridActive = true
            }

            if (className.contains("EditText", ignoreCase = true) && node.isFocused) {
                snapshot.isSearchInputFocused = true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return snapshot
    }

    private fun inspectYouTube(rootNode: AccessibilityNodeInfo, now: Long) {
        if (!currentPrefs.ytBlockShorts) return

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        var nodesVisited = 0
        val maxNodes = 300

        while (queue.isNotEmpty() && nodesVisited < maxNodes) {
            val node = queue.removeFirst()
            nodesVisited++

            val viewId = node.viewIdResourceName.orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()

            val isShortsPlayerView = viewId.contains("reel_recycler", ignoreCase = true) ||
                    viewId.contains("shorts_player_view", ignoreCase = true) ||
                    viewId.contains("reel_watch_fragment_root", ignoreCase = true) ||
                    viewId.contains("reel_player_page_container", ignoreCase = true) ||
                    viewId.contains("reel_progress_bar", ignoreCase = true)

            val isShortsBottomTabSelected = node.isSelected && (
                    contentDesc.equals("Shorts", ignoreCase = true) ||
                            text.equals("Shorts", ignoreCase = true)
                    )

            if (isShortsPlayerView || isShortsBottomTabSelected) {
                triggerBlockAction(
                    packageName = PKG_YOUTUBE,
                    reason = "YouTube Shorts",
                    toastMessage = "YouTube Shorts sind blockiert – Fokus behalten!"
                )
                return
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    private fun inspectTikTok(rootNode: AccessibilityNodeInfo, now: Long) {
        if (!currentPrefs.ttBlockForYou) return

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        var nodesVisited = 0
        val maxNodes = 300

        var isForYouOrHomeSelected = false
        var isAllowedScreenActive = false

        while (queue.isNotEmpty() && nodesVisited < maxNodes) {
            val node = queue.removeFirst()
            nodesVisited++

            val viewId = node.viewIdResourceName.orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()

            if (node.isSelected && (
                        text.equals("Posteingang", ignoreCase = true) ||
                                text.equals("Inbox", ignoreCase = true) ||
                                text.equals("Profil", ignoreCase = true) ||
                                text.equals("Profile", ignoreCase = true) ||
                                contentDesc.equals("Posteingang", ignoreCase = true) ||
                                contentDesc.equals("Inbox", ignoreCase = true) ||
                                contentDesc.equals("Profil", ignoreCase = true) ||
                                contentDesc.equals("Profile", ignoreCase = true)
                        )
            ) {
                isAllowedScreenActive = true
            }

            if (node.isSelected && (
                        text.equals("Für dich", ignoreCase = true) ||
                                text.equals("For You", ignoreCase = true) ||
                                text.equals("Startseite", ignoreCase = true) ||
                                text.equals("Home", ignoreCase = true) ||
                                text.equals("Entdecken", ignoreCase = true) ||
                                text.equals("Friends", ignoreCase = true) ||
                                contentDesc.equals("Für dich", ignoreCase = true) ||
                                contentDesc.equals("For You", ignoreCase = true)
                        )
            ) {
                isForYouOrHomeSelected = true
            }

            if (viewId.contains("feed_tab", ignoreCase = true) ||
                viewId.contains("main_tab", ignoreCase = true)
            ) {
                if (text.contains("Für dich", ignoreCase = true) || text.contains("For You", ignoreCase = true)) {
                    isForYouOrHomeSelected = true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (isForYouOrHomeSelected && !isAllowedScreenActive) {
            triggerBlockAction(
                packageName = PKG_TIKTOK_GLOBAL,
                reason = "TikTok For-You-Feed",
                toastMessage = "TikTok For-You-Feed ist blockiert – nutze nur den Posteingang!"
            )
        }
    }

    private fun triggerBlockAction(
        packageName: String,
        reason: String,
        toastMessage: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < BLOCK_ACTION_COOLDOWN_MS) {
            return
        }
        lastBlockTimestamp = now

        performGlobalAction(GLOBAL_ACTION_BACK)
        triggerSubtleHapticFeedback()

        mainHandler.post {
            Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
        }

        val app = applicationContext as? FocusLiteApplication
        app?.let {
            serviceScope.launch(Dispatchers.IO) {
                it.repository.recordBlockEvent(
                    packageName = packageName,
                    reason = reason,
                    timestamp = now
                )
            }
        }
    }

    private fun triggerSubtleHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration konnte nicht ausgelöst werden", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService wurde vom System unterbrochen.")
    }

    override fun onDestroy() {
        isServiceRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
