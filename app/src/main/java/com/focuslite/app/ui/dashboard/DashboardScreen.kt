package com.focuslite.app.ui.dashboard
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.focuslite.app.ui.webview.CleanWebPlatform
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenCleanWebView: (CleanWebPlatform) -> Unit) { Scaffold { innerPadding -> Column(modifier = Modifier.padding(innerPadding)) { Text("FocusLite Dashboard UI") } } }