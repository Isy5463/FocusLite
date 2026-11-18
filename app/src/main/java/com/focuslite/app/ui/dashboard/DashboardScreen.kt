package com.focuslite.app.ui.dashboard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focuslite.app.data.local.datastore.FocusPreferencesManager
import com.focuslite.app.ui.webview.CleanWebPlatform

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenCleanWebView: (CleanWebPlatform) -> Unit) {
    val prefs by viewModel.preferences.collectAsState()
    
    Scaffold(topBar = { @OptIn(ExperimentalMaterial3Api::class) TopAppBar(title = { Text("FocusLite Dashboard") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Instagram", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Reels blockieren"); Switch(checked = prefs.igBlockReels, onCheckedChange = { viewModel.updateSetting(FocusPreferencesManager.KEY_IG_BLOCK_REELS, it) }) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1 Reel aus DMs erlauben"); Switch(checked = prefs.igAllowSingleReelFromDm, onCheckedChange = { viewModel.updateSetting(FocusPreferencesManager.KEY_IG_ALLOW_SINGLE_DM_REEL, it) }) }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TikTok (inkl. Pro/Mods)", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Für dich-Feed blockieren"); Switch(checked = prefs.ttBlockForYou, onCheckedChange = { viewModel.updateSetting(FocusPreferencesManager.KEY_TT_BLOCK_FOR_YOU, it) }) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Posteingang/DMs erlauben"); Switch(checked = prefs.ttAllowInbox, onCheckedChange = { viewModel.updateSetting(FocusPreferencesManager.KEY_TT_ALLOW_INBOX, it) }) }
                }
            }
        }
    }
}
