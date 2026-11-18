package com.focuslite.app.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslite.app.FocusLiteApplication
import com.focuslite.app.ui.dashboard.*
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); val app = application as FocusLiteApplication; setContent { DashboardScreen(viewModel = viewModel(factory = DashboardViewModel.Factory(app.repository)), onOpenCleanWebView = {}) } } }