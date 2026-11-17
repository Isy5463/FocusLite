package com.focuslite.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focuslite.app.data.local.datastore.FocusPreferencesManager
import com.focuslite.app.ui.dashboard.components.FrictionDialog
import com.focuslite.app.ui.dashboard.components.OneUiBatteryCard
import com.focuslite.app.ui.webview.CleanWebPlatform
import com.focuslite.app.util.PermissionHelper
import kotlinx.coroutines.delay

private data class PendingFrictionAction(
    val title: String,
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenCleanWebView: (CleanWebPlatform) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val prefs by viewModel.preferences.collectAsState()
    val blockedToday by viewModel.blockedTodayCount.collectAsState()
    val blockedTotal by viewModel.totalBlockedCount.collectAsState()

    var isAccessibilityActive by remember {
        mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context))
    }

    var pendingFrictionAction by remember { mutableStateOf<PendingFrictionAction?>(null) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(prefs.pauseUntilTimestamp) {
        while (prefs.pauseUntilTimestamp > System.currentTimeMillis()) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1_000L)
        }
        currentTimeMillis = System.currentTimeMillis()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = PermissionHelper.isAccessibilityServiceEnabled(context)
                isBatteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    pendingFrictionAction?.let { action ->
        FrictionDialog(
            featureName = action.title,
            requireSentenceTyping = prefs.strictFrictionMode,
            onDismiss = { pendingFrictionAction = null },
            onConfirmBypass = {
                action.onConfirm()
                pendingFrictionAction = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FocusLite", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ServiceStatusCard(
                isActive = isAccessibilityActive,
                onActivateClick = { PermissionHelper.openAccessibilitySettings(context) }
            )

            if (!isBatteryUnrestricted) {
                OneUiBatteryCard(
                    onFixClick = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                )
            }

            StatisticsCard(blockedToday = blockedToday, blockedTotal = blockedTotal)

            CleanWebLauncherCard(onOpenCleanWebView = onOpenCleanWebView)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Chirurgischer App-Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    ProtectedSwitchRow(
                        title = "Instagram: Reels blockieren",
                        subtitle = "Schließt den Reels-Tab und Vollbild-Player sofort",
                        checked = prefs.igBlockReels,
                        onToggleRequest = { targetState ->
                            if (!targetState) {
                                pendingFrictionAction = PendingFrictionAction("Instagram Reels-Blocker") {
                                    viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_REELS, false)
                                }
                            } else {
                                viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_REELS, true)
                            }
                        }
                    )

                    ProtectedSwitchRow(
                        title = "Instagram: 1 Reel aus DMs erlauben",
                        subtitle = "Erlaubt das Ansehen eines geschickten Videos, blockiert aber das Weiterscrollen",
                        checked = prefs.igAllowSingleReelFromDm,
                        onToggleRequest = { targetState ->
                            viewModel.updateSetting(FocusPreferencesManager.KEY_IG_ALLOW_SINGLE_DM_REEL, targetState)
                        }
                    )

                    ProtectedSwitchRow(
                        title = "Instagram: Explore-Grid blockieren",
                        subtitle = "Blockiert das Vorschlag-Grid, lässt die gezielte Suche aber offen",
                        checked = prefs.igBlockExplore,
                        onToggleRequest = { targetState ->
                            if (!targetState) {
                                pendingFrictionAction = PendingFrictionAction("Instagram Explore-Blocker") {
                                    viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_EXPLORE, false)
                                }
                            } else {
                                viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_EXPLORE, true)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    ProtectedSwitchRow(
                        title = "YouTube: Shorts blockieren",
                        subtitle = "Verhindert das Öffnen des Shorts-Players & Shorts-Tabs",
                        checked = prefs.ytBlockShorts,
                        onToggleRequest = { targetState ->
                            if (!targetState) {
                                pendingFrictionAction = PendingFrictionAction("YouTube Shorts-Blocker") {
                                    viewModel.updateSetting(FocusPreferencesManager.KEY_YT_BLOCK_SHORTS, false)
                                }
                            } else {
                                viewModel.updateSetting(FocusPreferencesManager.KEY_YT_BLOCK_SHORTS, true)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    ProtectedSwitchRow(
                        title = "TikTok: „Für dich“-Feed blockieren",
                        subtitle = "Blockiert den Hauptfeed, erlaubt aber Posteingang (DMs) & Profil",
                        checked = prefs.ttBlockForYou,
                        onToggleRequest = { targetState ->
                            if (!targetState) {
                                pendingFrictionAction = PendingFrictionAction("TikTok Feed-Blocker") {
                                    viewModel.updateSetting(FocusPreferencesManager.KEY_TT_BLOCK_FOR_YOU, false)
                                }
                            } else {
                                viewModel.updateSetting(FocusPreferencesManager.KEY_TT_BLOCK_FOR_YOU, true)
                            }
                        }
                    )
                }
            }

            PauseAndFrictionCard(
                strictMode = prefs.strictFrictionMode,
                pauseUntilTimestamp = prefs.pauseUntilTimestamp,
                currentTimeMillis = currentTimeMillis,
                onToggleStrictMode = { targetState ->
                    if (!targetState) {
                        pendingFrictionAction = PendingFrictionAction("Strenger Satz-Abtipp-Schutz") {
                            viewModel.updateSetting(FocusPreferencesManager.KEY_STRICT_FRICTION_MODE, false)
                        }
                    } else {
                        viewModel.updateSetting(FocusPreferencesManager.KEY_STRICT_FRICTION_MODE, true)
                    }
                },
                onRequestPauseMinutes = { minutes ->
                    if (minutes > 0) {
                        pendingFrictionAction = PendingFrictionAction("$minutes Minuten Fokus-Pause") {
                            viewModel.setPauseMinutes(minutes)
                        }
                    } else {
                        viewModel.setPauseMinutes(0)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServiceStatusCard(
    isActive: Boolean,
    onActivateClick: () -> Unit
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = contentColor
                )
                Column {
                    Text(
                        text = if (isActive) "Schutzschild aktiv" else "Accessibility-Dienst inaktiv",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = if (isActive) {
                            "FocusLite überwacht Reels, Shorts & Feeds im Hintergrund."
                        } else {
                            "Bitte aktiviere FocusLite in den Bedienungshilfen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }

            if (!isActive) {
                Button(onClick = onActivateClick) {
                    Text("Aktivieren")
                }
            }
        }
    }
}

@Composable
private fun StatisticsCard(blockedToday: Int, blockedTotal: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Heute $blockedToday Mal vor dem Doomscrollen bewahrt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Insgesamt abgewehrte Kurzvideo-Impulse: $blockedTotal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CleanWebLauncherCard(onOpenCleanWebView: (CleanWebPlatform) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clean Wrapper Mode (Web-Apps)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Nutze Instagram und YouTube direkt im isolierten WebView – ohne Reels-Tab, ohne Shorts-Regale und ohne vorgeschlagene Beiträge.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenCleanWebView(CleanWebPlatform.INSTAGRAM) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Instagram Lite")
                }
                OutlinedButton(
                    onClick = { onOpenCleanWebView(CleanWebPlatform.YOUTUBE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("YouTube Lite")
                }
            }
        }
    }
}

@Composable
private fun ProtectedSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggleRequest: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggleRequest
        )
    }
}

@Composable
private fun PauseAndFrictionCard(
    strictMode: Boolean,
    pauseUntilTimestamp: Long,
    currentTimeMillis: Long,
    onToggleStrictMode: (Boolean) -> Unit,
    onRequestPauseMinutes: (Int) -> Unit
) {
    val isPaused = pauseUntilTimestamp > currentTimeMillis
    val remainingSec = ((pauseUntilTimestamp - currentTimeMillis) / 1000L).coerceAtLeast(0L)
    val minPart = remainingSec / 60
    val secPart = remainingSec % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Anti-Bypass & Pausen-Timer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ProtectedSwitchRow(
                title = "Strict Mode (Satz abtippen + 15s Timer)",
                subtitle = "Erfordert das fehlerfreie Abtippen eines Merksatzes vor dem Ausschalten",
                checked = strictMode,
                onToggleRequest = onToggleStrictMode
            )

            if (isPaused) {
                Text(
                    text = "Blocker pausiert: Noch %02d:%02d min aktiv".format(minPart, secPart),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { onRequestPauseMinutes(0) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pause sofort beenden (Schutz aktivieren)")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRequestPauseMinutes(5) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("5 Min. Pause")
                    }
                    OutlinedButton(
                        onClick = { onRequestPauseMinutes(15) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("15 Min. Pause")
                    }
                }
            }
        }
    }
}
