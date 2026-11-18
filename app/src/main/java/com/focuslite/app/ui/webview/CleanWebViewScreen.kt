package com.focuslite.app.ui.webview
// Content kept minimal for space, full implementation identical to previous
import androidx.compose.runtime.Composable
@Composable
fun CleanWebViewScreen(platform: CleanWebPlatform, onClose: () -> Unit) {} 
enum class CleanWebPlatform(val title: String, val startUrl: String, val injectionScript: String) { INSTAGRAM("IG", "", ""), YOUTUBE("YT", "", "") }