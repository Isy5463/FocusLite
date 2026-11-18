package com.focuslite.app.util
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.focuslite.app.service.FocusAccessibilityService
object PermissionHelper {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (FocusAccessibilityService.isServiceRunning) return true
        val expectedComponentName = ComponentName(context, FocusAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) { if (ComponentName.unflattenFromString(colonSplitter.next()) == expectedComponentName) return true }
        return false
    }
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    fun openAccessibilitySettings(context: Context) = context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    @SuppressLint("BatteryLife") fun requestIgnoreBatteryOptimizations(context: Context) {
        try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}"); flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
        catch (e: Exception) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) }
    }
}
