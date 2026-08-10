package dev.nutty.proxy.agent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** System-setting hand-offs used by onboarding and the Settings readiness list. */
object AlwaysOnSetup {
    const val NOTIFICATION_REQUEST_CODE = 1001

    fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(activity)) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }
    }

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun requestBatteryUnrestricted(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${activity.packageName}")))
    }

    fun backgroundDataRestricted(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

    fun openDataSettings(activity: Activity) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
        } else {
            // The system-wide data usage screen was added in API 28.  The
            // per-app page is the closest safe destination on Android 8.0/8.1.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        }
        activity.startActivity(intent)
    }

    fun openAppSettings(activity: Activity) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${activity.packageName}")))
    }
}
