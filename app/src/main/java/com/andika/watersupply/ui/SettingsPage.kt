package com.andika.watersupply.ui

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andika.watersupply.*
import com.andika.watersupply.data.*
import com.andika.watersupply.widget.WaterWidgetReceiver

@Composable fun SettingsPage(s: WaterState, vm: WaterViewModel, permissionRevision: Int, permissions: ReminderPermissionActions) {
    val context = LocalContext.current
    val controller = context.waterApp.reminders
    val p = s.preferences
    var targetEditor by remember { mutableStateOf(false) }
    var scheduleEditor by remember { mutableStateOf(false) }
    Text("Pengaturan", style = MaterialTheme.typography.headlineLarge)
    Text("Sesuaikan dengan ritme harimu.", style = MaterialTheme.typography.bodyLarge)
    SettingSection("Target harian") {
        Text("${glasses(s.target)} gelas", style = MaterialTheme.typography.headlineSmall)
        Text("Perubahan berlaku mulai hari ini. Target hari sebelumnya tetap tersimpan.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { targetEditor = true }) { Text("Ubah target") }
    }
    SettingSection("Pengingat minum") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (p.enabled) "Aktif" else "Nonaktif", Modifier.weight(1f))
            Switch(checked = p.enabled, onCheckedChange = { enabled ->
                vm.preferences(p.copy(enabled = enabled, reminderSetupComplete = true))
                if (enabled) permissions.notifications()
            }, modifier = Modifier.semantics { contentDescription = "Aktifkan pengingat minum" })
        }
        Text(if (p.mode == ReminderMode.INTERVAL) "Setiap ${p.interval} menit, ${clock(p.start)}–${clock(p.end)}" else p.times.joinToString(", ") { clock(it) }, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { scheduleEditor = true }) { Text("Atur jadwal") }
        Text("Jadwal tetap setelah pencatatan. Pengingat berhenti saat target tercapai.", style = MaterialTheme.typography.bodySmall)
    }
    SettingSection("Izin & ketepatan") {
        key(permissionRevision) {
            Text(if (controller.notificationsAllowed()) "Notifikasi diizinkan" else "Notifikasi belum diizinkan", style = MaterialTheme.typography.titleMedium)
            if (!controller.notificationsAllowed()) TextButton(onClick = permissions.notifications) { Text("Izinkan notifikasi") }
            Text(if (controller.exactAllowed()) "Akses alarm tepat waktu tersedia" else "Pengingat dapat terlambat karena akses alarm belum aktif.", style = MaterialTheme.typography.bodyMedium)
            if (Build.VERSION.SDK_INT >= 31 && !controller.exactAllowed()) TextButton(onClick = permissions.alarms) { Text("Izinkan alarm tepat waktu") }
        }
    }
    SettingSection("Widget layar utama") {
        Text("1. Sentuh dan tahan area kosong di layar utama.\n2. Pilih Widget, lalu Water Supply.\n3. Letakkan widget dan sesuaikan ukurannya.", style = MaterialTheme.typography.bodyMedium)
        Text("Catat 1 atau ½ gelas langsung dari widget. Untuk koreksi, buka Riwayat.", style = MaterialTheme.typography.bodySmall)
        if (Build.VERSION.SDK_INT >= 26) {
            val widgets = context.getSystemService(AppWidgetManager::class.java)
            if (widgets.isRequestPinAppWidgetSupported) OutlinedButton(onClick = {
                widgets.requestPinAppWidget(ComponentName(context, WaterWidgetReceiver::class.java), null, null)
            }) { Text("Tambahkan widget") }
        }
    }
    Text("Water Supply · Semua catatan tersimpan di perangkat ini.", style = MaterialTheme.typography.bodySmall)
    if (targetEditor) TargetEditor(s.target, { targetEditor = false }) { vm.target(it); targetEditor = false }
    if (scheduleEditor) ScheduleEditor(p, { scheduleEditor = false }) { vm.preferences(it); scheduleEditor = false }
}

@Composable private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable private fun TargetEditor(initial: Int, close: () -> Unit, save: (Int) -> Unit) {
    var text by remember { mutableStateOf(glasses(initial)) }
    val units = runCatching { text.replace(',', '.').toBigDecimalOrNull()?.multiply(java.math.BigDecimal(2))?.intValueExact() }.getOrNull()
    AlertDialog(onDismissRequest = close, title = { Text("Target harian") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Jumlah gelas") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text("Minimal 0,5 gelas, dalam kelipatan 0,5.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { units?.let(save) }, enabled = units != null && units > 0) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}

@Composable private fun ScheduleEditor(initial: Preferences, close: () -> Unit, save: (Preferences) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    fun pick(value: Int, changed: (Int) -> Unit) { TimePickerDialog(context, { _, h, m -> changed(h * 60 + m) }, value / 60, value % 60, true).show() }
    AlertDialog(onDismissRequest = close, title = { Text("Jadwal pengingat") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = draft.mode == ReminderMode.INTERVAL, onClick = { draft = draft.copy(mode = ReminderMode.INTERVAL) }, label = { Text("Interval") })
            FilterChip(selected = draft.mode == ReminderMode.CUSTOM, onClick = { draft = draft.copy(mode = ReminderMode.CUSTOM) }, label = { Text("Jam khusus") })
            if (draft.mode == ReminderMode.INTERVAL) {
                Text("Setiap ${draft.interval} menit")
                Slider(value = draft.interval.toFloat(), onValueChange = { draft = draft.copy(interval = ((it / 15).toInt() * 15).coerceIn(15, 240)) }, valueRange = 15f..240f, steps = 14, modifier = Modifier.semantics { contentDescription = "Interval pengingat dalam menit" })
                OutlinedButton(onClick = { pick(draft.start) { draft = draft.copy(start = it) } }, Modifier.fillMaxWidth()) { Text("Mulai ${clock(draft.start)}") }
                OutlinedButton(onClick = { pick(draft.end) { draft = draft.copy(end = it) } }, Modifier.fillMaxWidth()) { Text("Selesai ${clock(draft.end)}") }
            } else {
                Text("Pengingat berulang setiap hari pada jam berikut.")
                draft.times.forEach { minute ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(minute), Modifier.weight(1f))
                        TextButton(onClick = { draft = draft.copy(times = draft.times - minute) }, modifier = Modifier.semantics { contentDescription = "Hapus jadwal ${clock(minute)}" }) { Text("Hapus") }
                    }
                }
                OutlinedButton(onClick = { pick(540) { draft = draft.copy(times = (draft.times + it).distinct().sorted()) } }) { Text("Tambah jam") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        if (draft.start >= draft.end) error = "Jam selesai harus setelah jam mulai."
        else if (draft.mode == ReminderMode.CUSTOM && draft.times.isEmpty()) error = "Tambahkan minimal satu jam."
        else save(draft)
    }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
}
