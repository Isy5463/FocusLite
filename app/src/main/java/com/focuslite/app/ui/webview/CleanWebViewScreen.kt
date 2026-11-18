package com.focuslite.app.ui.webview
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

enum class CleanWebPlatform(val title: String, val startUrl: String, val injectionScript: String) {
    INSTAGRAM("Instagram LiteView (Fokus)", "https://www.instagram.com/", WebFilterScripts.INSTAGRAM_CLEAN_JS),
    YOUTUBE("YouTube LiteView (Ohne Shorts)", "https://m.youtube.com/feed/subscriptions", WebFilterScripts.YOUTUBE_CLEAN_JS)
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CleanWebViewScreen(platform: CleanWebPlatform, onClose: () -> Unit) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(platform.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Neu laden") }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Schließen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (progress > 0f && progress < 1f) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        val cookieManager = CookieManager.getInstance(); cookieManager.setAcceptCookie(true); cookieManager.setAcceptThirdPartyCookies(this, true)
                        settings.apply { javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true; cacheMode = WebSettings.LOAD_DEFAULT; mediaPlaybackRequiresUserGesture = false; userAgentString = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36" }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (url.contains("/reels/") || url.contains("/shorts/")) { view?.evaluateJavascript(platform.injectionScript, null); return true }
                                return false
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { super.onPageStarted(view, url, favicon); view?.evaluateJavascript(platform.injectionScript, null); canGoBack = view?.canGoBack() ?: false }
                            override fun onPageFinished(view: WebView?, url: String?) { super.onPageFinished(view, url); view?.evaluateJavascript(platform.injectionScript, null); CookieManager.getInstance().flush(); canGoBack = view?.canGoBack() ?: false }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) { super.doUpdateVisitedHistory(view, url, isReload); view?.evaluateJavascript(platform.injectionScript, null); canGoBack = view?.canGoBack() ?: false }
                        }
                        webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f } }
                        loadUrl(platform.startUrl); webViewRef = this
                    }
                }
            )
        }
    }
}
