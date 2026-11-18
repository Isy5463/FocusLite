package com.focuslite.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private data class PendingFrictionAction(val title: String, val onConfirm: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenCleanWebView: (CleanWebPlatform) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs by viewModel.preferences.collectAsState()
    val blockedToday by viewModel.blockedTodayCount.collectAsState()
    val blockedTotal by viewModel.totalBlockedCount.collectAsState()

    var isAccessibilityActive by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
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
            onConfirmBypass = { action.onConfirm(); pendingFrictionAction = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FocusLite", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val containerColor = if (isAccessibilityActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            val contentColor = if (isAccessibilityActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(imageVector = if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = contentColor)
                        Column {
                            Text(text = if (isAccessibilityActive) "Schutzschild aktiv" else "Accessibility-Dienst inaktiv", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                            Text(text = if (isAccessibilityActive) "FocusLite überwacht Reels, Shorts & Feeds im Hintergrund." else "Bitte aktiviere FocusLite in den Bedienungshilfen.", style = MaterialTheme.typography.bodySmall, color = contentColor)
                        }
                    }
                    if (!isAccessibilityActive) { Button(onClick = { PermissionHelper.openAccessibilitySettings(context) }) { Text("Aktivieren") } }
                }
            }

            if (!isBatteryUnrestricted) { OneUiBatteryCard(onFixClick = { PermissionHelper.requestIgnoreBatteryOptimizations(context) }) }

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Heute $blockedToday Mal vor dem Doomscrollen bewahrt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Insgesamt abgewehrte Kurzvideo-Impulse: $blockedTotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Clean Wrapper Mode (Web-Apps)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "Nutze Instagram und YouTube direkt im isolierten WebView – ohne Reels-Tab, ohne Shorts-Regale und ohne vorgeschlagene Beiträge.", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onOpenCleanWebView(CleanWebPlatform.INSTAGRAM) }, modifier = Modifier.weight(1f)) { Text("Instagram Lite") }
                        OutlinedButton(onClick = { onOpenCleanWebView(CleanWebPlatform.YOUTUBE) }, modifier = Modifier.weight(1f)) { Text("YouTube Lite") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Chirurgischer App-Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    ProtectedSwitchRow("Instagram: Reels blockieren", "Schließt den Reels-Tab und Vollbild-Player sofort", prefs.igBlockReels) { t -> if (!t) pendingFrictionAction = PendingFrictionAction("Instagram Reels-Blocker") { viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_REELS, false) } else viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_REELS, true) }
                    ProtectedSwitchRow("Instagram: 1 Reel aus DMs erlauben", "Erlaubt das Ansehen eines geschickten Videos, blockiert aber das Weiterscrollen", prefs.igAllowSingleReelFromDm) { t -> viewModel.updateSetting(FocusPreferencesManager.KEY_IG_ALLOW_SINGLE_DM_REEL, t) }
                    ProtectedSwitchRow("Instagram: Explore-Grid blockieren", "Blockiert das Vorschlag-Grid, lässt die gezielte Suche aber offen", prefs.igBlockExplore) { t -> if (!t) pendingFrictionAction = PendingFrictionAction("Instagram Explore-Blocker") { viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_EXPLORE, false) } else viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_EXPLORE, true) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ProtectedSwitchRow("YouTube: Shorts blockieren", "Verhindert das Öffnen des Shorts-Players & Shorts-Tabs", prefs.ytBlockShorts) { t -> if (!t) pendingFrictionAction = PendingFrictionAction("YouTube Shorts-Blocker") { viewModel.updateSetting(FocusPreferencesManager.KEY_YT_BLOCK_SHORTS, false) } else viewModel.updateSetting(FocusPreferencesManager.KEY_YT_BLOCK_SHORTS, true) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ProtectedSwitchRow("TikTok: „Für dich“-Feed blockieren", "Blockiert den Hauptfeed, erlaubt aber Posteingang (DMs) & Profil", prefs.ttBlockForYou) { t -> if (!t) pendingFrictionAction = PendingFrictionAction("TikTok Feed-Blocker") { viewModel.updateSetting(FocusPreferencesManager.KEY_TT_BLOCK_FOR_YOU, false) } else viewModel.updateSetting(FocusPreferencesManager.KEY_TT_BLOCK_FOR_YOU, true) }
                    ProtectedSwitchRow("TikTok: Posteingang/DMs erlauben", "Ausnahme für den Nachrichten-Tab", prefs.ttAllowInbox) { t -> viewModel.updateSetting(FocusPreferencesManager.KEY_TT_ALLOW_INBOX, t) }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Anti-Bypass & Pausen-Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    ProtectedSwitchRow("Strict Mode (Satz abtippen + 15s Timer)", "Erfordert das fehlerfreie Abtippen eines Merksatzes vor dem Ausschalten", prefs.strictFrictionMode) { t -> if (!t) pendingFrictionAction = PendingFrictionAction("Strenger Satz-Abtipp-Schutz") { viewModel.updateSetting(FocusPreferencesManager.KEY_STRICT_FRICTION_MODE, false) } else viewModel.updateSetting(FocusPreferencesManager.KEY_STRICT_FRICTION_MODE, true) }
                    
                    val isPaused = prefs.pauseUntilTimestamp > currentTimeMillis
                    if (isPaused) {
                        val rem = ((prefs.pauseUntilTimestamp - currentTimeMillis) / 1000L).coerceAtLeast(0L)
                        Text(text = "Blocker pausiert: Noch %02d:%02d min aktiv".format(rem / 60, rem % 60), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Button(onClick = { viewModel.setPauseMinutes(0) }, modifier = Modifier.fillMaxWidth()) { Text("Pause sofort beenden") }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { pendingFrictionAction = PendingFrictionAction("5 Minuten Fokus-Pause") { viewModel.setPauseMinutes(5) } }, modifier = Modifier.weight(1f)) { Text("5 Min. Pause") }
                            OutlinedButton(onClick = { pendingFrictionAction = PendingFrictionAction("15 Minuten Fokus-Pause") { viewModel.setPauseMinutes(15) } }, modifier = Modifier.weight(1f)) { Text("15 Min. Pause") }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProtectedSwitchRow(title: String, subtitle: String, checked: Boolean, onToggleRequest: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggleRequest)
    }
}
