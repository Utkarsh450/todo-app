package com.utkarsh.todo.feature.reminders.platform

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What an alert needs from the phone before it can show up on the screen the way Google Calendar's does. */
enum class MissingPermission { NOTIFICATIONS, FULL_SCREEN, EXACT_ALARMS }

/**
 * Checks the three permissions alerts depend on: notifications (Android 13+), full-screen alerts (Android 14+, the
 * person can switch them off per app) and exact alarms (Android 12+). The first one that is missing is asked for.
 */
@Singleton
class AlertPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: ReminderNotifications,
) {
    fun missing(): MissingPermission? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> MissingPermission.NOTIFICATIONS
        !notifications.canShowFullScreen() -> MissingPermission.FULL_SCREEN
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() -> MissingPermission.EXACT_ALARMS
        else -> null
    }

    /** The settings page where [permission] is switched on (notifications use the system permission dialog instead). */
    fun settingsIntent(permission: MissingPermission): Intent {
        val app = Uri.parse("package:${context.packageName}")
        return when (permission) {
            MissingPermission.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app) else appSettings(app)
            MissingPermission.EXACT_ALARMS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app) else appSettings(app)
            MissingPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appSettings(app: Uri) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
}
