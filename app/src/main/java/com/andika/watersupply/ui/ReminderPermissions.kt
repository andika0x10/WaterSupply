package com.andika.watersupply.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.andika.watersupply.reminders.ReminderController
import com.andika.watersupply.waterApp

class ReminderPermissionActions(val notifications: () -> Unit, val alarms: () -> Unit)

@Composable
fun rememberReminderPermissions(onChanged: () -> Unit): ReminderPermissionActions {
    val context = LocalContext.current
    val controller = context.waterApp.reminders
    val updatedOnChanged by rememberUpdatedState(onChanged)
    val history = remember(context) { context.getSharedPreferences("reminder_permissions", Context.MODE_PRIVATE) }
    var explainAlarm by rememberSaveable { mutableStateOf(false) }
    val alarmSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { updatedOnChanged() }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updatedOnChanged()
        if (controller.notificationsAllowed() && !controller.exactAllowed()) explainAlarm = true
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        updatedOnChanged()
        if (granted && !controller.exactAllowed()) explainAlarm = true
    }
    if (explainAlarm) AlertDialog(
        onDismissRequest = { explainAlarm = false },
        title = { Text("Izinkan alarm tepat waktu") },
        text = { Text("Aktifkan Water Supply pada halaman Alarm & pengingat agar pengingat minum berbunyi sesuai jadwal, termasuk saat layar terkunci.") },
        confirmButton = { TextButton(onClick = {
            explainAlarm = false
            if (Build.VERSION.SDK_INT >= 31) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                try { alarmSettings.launch(intent) }
                catch (_: ActivityNotFoundException) {
                    alarmSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }
            }
        }) { Text("Lanjutkan") } },
        dismissButton = { TextButton(onClick = { explainAlarm = false }) { Text("Nanti") } }
    )
    return ReminderPermissionActions(notifications = {
        controller.createChannel()
        val activity = context.activity()
        val runtimeMissing = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val permanentlyDenied = Build.VERSION.SDK_INT >= 33 && history.getBoolean("notification_requested", false) && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        when {
            Build.VERSION.SDK_INT >= 33 && runtimeMissing && !permanentlyDenied -> {
                history.edit { putBoolean("notification_requested", true) }
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            !controller.notificationsAllowed() -> {
                val intent = if (Build.VERSION.SDK_INT >= 26) {
                    if (controller.channelBlocked()) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderController.CHANNEL_ID)
                    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                if (Build.VERSION.SDK_INT >= 26) intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                notificationSettings.launch(intent)
            }
            !controller.exactAllowed() -> explainAlarm = true
            else -> updatedOnChanged()
        }
    }, alarms = { if (!controller.exactAllowed()) explainAlarm = true })
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
