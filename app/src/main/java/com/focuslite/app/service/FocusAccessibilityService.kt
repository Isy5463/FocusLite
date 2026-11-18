package com.focuslite.app.service

import android.accessibilityservice.AccessibilityService
import android.os.*
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.focuslite.app.FocusLiteApplication
import com.focuslite.app.data.local.datastore.FocusPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.ArrayDeque

class FocusAccessibilityService : AccessibilityService() {
    companion object {
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        
        // Cooldown auf 1.5s erhöht, damit IG Zeit hat, die UI nach dem "Zurück" zu aktualisieren
        private const val BLOCK_ACTION_COOLDOWN_MS = 1500L 
        private const val SCAN_DEBOUNCE_MS = 180L
        private const val DM_CONTEXT_EXPIRY_MS = 15_000L
        
        @Volatile var isServiceRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentPrefs = FocusPreferences()
    private var lastBlockTimestamp: Long = 0L
    private var lastTreeScanTimestamp: Long = 0L

    private enum class IgNavigationContext { UNKNOWN, DIRECT_THREAD_OR_INBOX, ALLOWED_SINGLE_DM_REEL, OTHER_SCREEN }
    private var igContextState = IgNavigationContext.UNKNOWN
    private var lastDmContextSeenTimestamp: Long = 0L
    private var dmReelOpenedTimestamp: Long = 0L
    private var initialReelSignature: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        (applicationContext as? FocusLiteApplication)?.let { app -> 
            serviceScope.launch { app.repository.preferencesFlow.collectLatest { currentPrefs = it } } 
        }
    }

override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val packageName = event.packageName?.toString() ?: return
    val now = System.currentTimeMillis()

    if (currentPrefs.pauseUntilTimestamp > now) return

    // 1. TikTok Pro / Mod Erkennung (fängt alles mit "tiktok", "aweme", "zhiliao" etc. ab)
    val isTikTok = packageName.contains("tiktok", true) || 
                   packageName.contains("zhiliao", true) || 
                   packageName.contains("aweme", true) || 
                   packageName.contains("trill", true)

    // 2. AKKU-SCHUTZ: Ignoriere alle irrelevanten Apps (WhatsApp, Browser etc.) sofort
    val isTargetApp = packageName == PKG_INSTAGRAM || packageName == PKG_YOUTUBE || isTikTok
    if (!isTargetApp) return

    // 3. Spezielle Instagram-Scroll-Regel für DMs
    if (packageName == PKG_INSTAGRAM && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && igContextState == IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
        handleInstagramDmReelScroll(event, now)
        return
    }

    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
        if (now - lastTreeScanTimestamp < SCAN_DEBOUNCE_MS) return
    }
    lastTreeScanTimestamp = now
    val rootNode = rootInActiveWindow ?: return

    try {
        if (packageName == PKG_INSTAGRAM) inspectInstagram(rootNode, now)
        else if (packageName == PKG_YOUTUBE) inspectYouTube(rootNode, now)
        else if (isTikTok) inspectTikTok(rootNode, event, packageName)
    } catch (e: Exception) { 
        Log.e("FocusLite", "Error scanning UI", e) 
    }
}

    private fun inspectInstagram(rootNode: AccessibilityNodeInfo, now: Long) {
        val snapshot = scanInstagramTree(rootNode)
        
        if (snapshot.isInDirectMessages && !snapshot.isReelsViewerActive) {
            igContextState = IgNavigationContext.DIRECT_THREAD_OR_INBOX
            lastDmContextSeenTimestamp = now
            initialReelSignature = null
            return
        }
        
        if (currentPrefs.igBlockExplore && snapshot.isExploreGridActive && !snapshot.isSearchInputFocused) {
            triggerBlockAction(PKG_INSTAGRAM, "Instagram Explore", "Explore-Feed blockiert!")
            return
        }
        
        if (currentPrefs.igBlockReels && snapshot.isReelsViewerActive) {
            if (snapshot.isReelsBottomTabSelected) {
                igContextState = IgNavigationContext.OTHER_SCREEN
                triggerBlockAction(PKG_INSTAGRAM, "Instagram Reels Tab", "Reels-Tab blockiert!")
                return
            }
            
            if (currentPrefs.igAllowSingleReelFromDm) {
                val cameFromDmRecently = (igContextState == IgNavigationContext.DIRECT_THREAD_OR_INBOX) || (now - lastDmContextSeenTimestamp < DM_CONTEXT_EXPIRY_MS)
                if (cameFromDmRecently && igContextState != IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
                    igContextState = IgNavigationContext.ALLOWED_SINGLE_DM_REEL
                    dmReelOpenedTimestamp = now
                    initialReelSignature = snapshot.currentReelSignature
                    return
                }
                if (igContextState == IgNavigationContext.ALLOWED_SINGLE_DM_REEL) {
                    if (initialReelSignature != null && snapshot.currentReelSignature != null && snapshot.currentReelSignature != initialReelSignature && (now - dmReelOpenedTimestamp > 700L)) {
                        igContextState = IgNavigationContext.OTHER_SCREEN
                        triggerBlockAction(PKG_INSTAGRAM, "Instagram DM Reel Scroll", "Nur 1 geschicktes Reel erlaubt!")
                    }
                    return
                }
            }
            
            triggerBlockAction(PKG_INSTAGRAM, "Instagram Reels Player", "Reel geschlossen!")
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
        val isPagerOrRecyclerScroll = className.contains("ViewPager", true) || className.contains("RecyclerView", true)
        val scrolledToNextPage = event.fromIndex >= 1 || event.toIndex >= 1
        val significantVerticalDelta = kotlin.math.abs(event.scrollDeltaY) > 180
        if (isPagerOrRecyclerScroll && (scrolledToNextPage || significantVerticalDelta)) {
            igContextState = IgNavigationContext.DIRECT_THREAD_OR_INBOX
            triggerBlockAction(PKG_INSTAGRAM, "Instagram DM Reel Scroll", "Weiterscrollen blockiert!")
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
        
        while (queue.isNotEmpty() && nodesVisited < 400) {
            val node = queue.removeFirst()
            nodesVisited++
            
            val viewId = node.viewIdResourceName.orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val className = node.className?.toString().orEmpty()
            
            // DER ENTSCHEIDENDE FIX FÜR DIE ENDLOSSCHLEIFE:
            // Verhindert, dass unsichtbar vorgeladene Hintergrund-Fragments reagieren
            if (node.isVisibleToUser) {
                if (viewId.contains("row_thread_composer", true) || viewId.contains("direct_thread", true) || viewId.contains("thread_list", true) || viewId.contains("direct_inbox", true) || contentDesc.equals("Nachricht senden", true) || contentDesc.equals("Message…", true) || text.equals("Nachricht…", true)) {
                    snapshot.isInDirectMessages = true
                }
                
                if (viewId.contains("clips_viewer_view_pager", true) || viewId.contains("clips_viewer_root", true) || viewId.contains("reel_viewer_root", true) || viewId.contains("clips_video_container", true)) {
                    snapshot.isReelsViewerActive = true
                }
                
                if ((viewId.contains("clips_tab", true) || contentDesc.equals("Reels", true)) && node.isSelected) { 
                    snapshot.isReelsBottomTabSelected = true
                    snapshot.isReelsViewerActive = true 
                }
                
                if (snapshot.currentReelSignature == null && (viewId.contains("clips_author_username", true) || viewId.contains("clips_caption", true)) && text.isNotBlank()) {
                    snapshot.currentReelSignature = text.take(40)
                }
                
                if (viewId.contains("explore_grid", true) || viewId.contains("search_ighv_recyclerview", true) || (viewId.contains("search_tab", true) && node.isSelected)) {
                    snapshot.isExploreGridActive = true
                }
                
                if (className.contains("EditText", true) && node.isFocused) {
                    snapshot.isSearchInputFocused = true
                }
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
        
        while (queue.isNotEmpty() && nodesVisited < 300) {
            val node = queue.removeFirst()
            nodesVisited++
            
            val viewId = node.viewIdResourceName.orEmpty()
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            
            if (node.isVisibleToUser) {
                val isShortsPlayerView = viewId.contains("reel_recycler", true) || viewId.contains("shorts_player_view", true) || viewId.contains("reel_watch_fragment_root", true) || viewId.contains("reel_player_page_container", true) || viewId.contains("reel_progress_bar", true)
                val isShortsBottomTabSelected = node.isSelected && (contentDesc.equals("Shorts", true) || text.equals("Shorts", true))
                
                if (isShortsPlayerView || isShortsBottomTabSelected) { 
                    triggerBlockAction(PKG_YOUTUBE, "YouTube Shorts", "Shorts blockiert!")
                    return 
                }
            }
            for (i in 0 until node.childCount) { node.getChild(i)?.let { queue.add(it) } }
        }
    }

    private fun inspectTikTok(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent, packageName: String) {
        if (!currentPrefs.ttBlockForYou) return
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var nodesVisited = 0
        var isAllowedScreenActive = false
        
        while (queue.isNotEmpty() && nodesVisited < 300) {
            val node = queue.removeFirst()
            nodesVisited++
            
            val contentDesc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            
            if (node.isVisibleToUser && node.isSelected && (
                text.equals("Posteingang", true) || text.equals("Inbox", true) || 
                text.equals("Profil", true) || text.equals("Profile", true) || 
                contentDesc.equals("Posteingang", true) || contentDesc.equals("Inbox", true) || 
                contentDesc.equals("Profil", true) || contentDesc.equals("Profile", true)
            )) {
                isAllowedScreenActive = true
            }
            for (i in 0 until node.childCount) { node.getChild(i)?.let { queue.add(it) } }
        }
        
        // TIKTOK PRO SCROLL-HEURISTIK: 
        // Sobald auf dem Container (ViewPager) gewischt wird und du nicht im Posteingang bist, feuert der Blocker.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val scrollClass = event.className?.toString().orEmpty()
            if (scrollClass.contains("ViewPager", true)) {
                if (!isAllowedScreenActive) {
                    triggerBlockAction(packageName, "TikTok Scroll", "Scrollen im Feed blockiert!")
                }
            }
        }
    }

    private fun triggerBlockAction(packageName: String, reason: String, toastMessage: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < BLOCK_ACTION_COOLDOWN_MS) return
        lastBlockTimestamp = now
        
        performGlobalAction(GLOBAL_ACTION_BACK)
        mainHandler.post { Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_SHORT).show() }
        
        (applicationContext as? FocusLiteApplication)?.let { app -> 
            serviceScope.launch(Dispatchers.IO) { app.repository.recordBlockEvent(packageName, reason, now) } 
        }
    }
    
    override fun onInterrupt() {}
    override fun onDestroy() { 
        isServiceRunning = false
        serviceScope.cancel()
        super.onDestroy() 
    }
}
