package dev.nutty.proxy.agent

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
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

    /**
     * "Unrestricted" in an OEM's App info screen and Android's separate Doze
     * allow-list are not the same setting.  The latter made the checklist show
     * a false warning on phones where the app was already allowed to run in the
     * background.  Check the restriction Android actually exposes for this app.
     */
    fun backgroundBatteryRestricted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        } else {
            false
        }

    fun requestBatteryUnrestricted(activity: Activity) {
        // There is no portable Android intent for the vendor-specific
        // "Unrestricted" switch.  App details is the truthful destination;
        // it is where Android exposes the applicable battery control.
        openAppSettings(activity)
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
