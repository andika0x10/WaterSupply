package com.andika.watersupply.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andika.watersupply.*
import com.andika.watersupply.data.*
import com.andika.watersupply.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun History(s: WaterState, vm: WaterViewModel) {
    var selected by rememberSaveable { mutableStateOf(s.date.toString()) }
    var editing by remember { mutableStateOf<Drink?>(null) }
    var editor by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val date = LocalDate.parse(selected)
    val records = s.drinksByDate[selected].orEmpty()
    Text("Riwayat", style = MaterialTheme.typography.headlineLarge)
    Text("Sedikit demi sedikit, setiap hari.", style = MaterialTheme.typography.bodyLarge)
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tujuh hari terakhir", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                (6 downTo 0).forEach { offset ->
                    val d = s.date.minusDays(offset.toLong())
                    val units = s.totalsByDate[d.toString()] ?: 0
                    val target = targetFor(s.targets, d)
                    Column(Modifier.width(48.dp).clip(RoundedCornerShape(12.dp)).clickable { selected = d.toString() }.padding(vertical = 8.dp).clearAndSetSemantics {
                        contentDescription = "${d.format(dateFormat)}, ${glasses(units)} dari ${glasses(target)} gelas"
                        onClick { selected = d.toString(); true }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(glasses(units), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.padding(vertical = 6.dp).height(76.dp).width(16.dp).clip(RoundedCornerShape(8.dp)).background(Sky)) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight((units.toFloat() / target).coerceIn(0f, 1f)).background(if (units >= target) Sun else Water))
                        }
                        Text(d.format(DateTimeFormatter.ofPattern("EE", Indonesian)), style = MaterialTheme.typography.labelMedium, fontWeight = if (d == date) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, y, m, d -> selected = LocalDate.of(y, m + 1, d).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Indonesian))) }
    Text("${glasses(records.sumOf { it.units })} / ${glasses(targetFor(s.targets, date))} gelas", style = MaterialTheme.typography.headlineSmall)
    Button(onClick = { editing = null; editor = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tambah catatan") }
    if (records.isEmpty()) Text("Belum ada catatan. Tambahkan gelas yang sudah kamu minum.")
    records.forEach { drink ->
        Surface(onClick = { editing = drink; editor = true }, color = Color.White, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("${glasses(drink.units)} gelas", style = MaterialTheme.typography.titleMedium); Text(timeOf(drink), style = MaterialTheme.typography.bodyMedium) }
                Text("Ubah", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (editor) DrinkEditor(editing, date, { editor = false }, { units, timestamp -> vm.save(units, timestamp, editing?.id ?: 0); editor = false }, { editing?.let { vm.delete(it.id) }; editor = false })
}

@Composable private fun DrinkEditor(drink: Drink?, selected: LocalDate, close: () -> Unit, save: (Int, Long) -> Unit, delete: () -> Unit) {
    val context = LocalContext.current
    val initial = drink?.let { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        ?: selected.atTime(LocalTime.now().withSecond(0).withNano(0))
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime()) }
    var units by remember { mutableIntStateOf(drink?.units ?: 2) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text(if (drink == null) "Tambah catatan" else "Ubah catatan") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = units == 2, onClick = { units = 2 }, label = { Text("1 gelas") })
                FilterChip(selected = units == 1, onClick = { units = 1 }, label = { Text("½ gelas") })
            }
            OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() }, Modifier.fillMaxWidth()) { Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Indonesian))) }
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, true).show() }, Modifier.fillMaxWidth()) { Text(time.format(timeFormat)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (drink != null) TextButton(onClick = { confirmDelete = true }) { Text("Hapus catatan", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val timestamp = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (timestamp > System.currentTimeMillis()) error = "Pilih waktu yang sudah berlalu." else save(units, timestamp)
    }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = close) { Text("Batal") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Hapus catatan ini?") }, text = { Text("Jumlah gelas pada hari tersebut akan diperbarui.") }, confirmButton = { TextButton(onClick = delete) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } })
}
