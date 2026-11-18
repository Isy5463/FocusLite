package com.focuslite.app.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslite.app.FocusLiteApplication
import com.focuslite.app.ui.dashboard.DashboardScreen
import com.focuslite.app.ui.dashboard.DashboardViewModel
import com.focuslite.app.ui.webview.CleanWebPlatform
import com.focuslite.app.ui.webview.CleanWebViewScreen

private val FocusDarkColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFA7FFEB),
    secondary = Color(0xFFB39DDB),
    secondaryContainer = Color(0xFF311B92),
    onSecondaryContainer = Color(0xFFEDE7F6),
    background = Color(0xFF0F1416),
    surface = Color(0xFF181E22)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FocusLiteApplication
        setContent {
            MaterialTheme(colorScheme = FocusDarkColorScheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(app.repository))
                    var activeWebPlatform by remember { mutableStateOf<CleanWebPlatform?>(null) }
                    if (activeWebPlatform != null) {
                        CleanWebViewScreen(platform = activeWebPlatform!!, onClose = { activeWebPlatform = null })
                    } else {
                        DashboardScreen(viewModel = dashboardViewModel, onOpenCleanWebView = { platform -> activeWebPlatform = platform })
                    }
                }
            }
        }
    }
}
