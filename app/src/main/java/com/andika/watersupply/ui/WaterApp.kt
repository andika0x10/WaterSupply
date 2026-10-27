package com.andika.watersupply.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andika.watersupply.*
import com.andika.watersupply.data.*
import com.andika.watersupply.ui.theme.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Indonesian: Locale = Locale.forLanguageTag("id-ID")
val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Indonesian)
val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm", Indonesian)
fun clock(minutes: Int) = "%02d.%02d".format(minutes / 60, minutes % 60)
fun timeOf(drink: Drink): String = Instant.ofEpochMilli(drink.timestamp).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable fun WaterApp(vm: WaterViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { permissionRevision++; vm.refresh() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(containerColor = Sky, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf("Beranda", "Riwayat", "Pengaturan").forEachIndexed { index, title ->
                NavigationBarItem(selected = page == index, onClick = { page = index },
                    icon = { Icon(when (index) { 0 -> Icons.Outlined.Home; 1 -> Icons.Outlined.DateRange; else -> Icons.Outlined.Settings }, null) },
                    label = { Text(title) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Sun))
            }
        }
    }) { padding ->
        key(page) {
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.loading) { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)); return@Column }
                when (page) {
                    0 -> Home(state, { units -> vm.add(units) { id -> scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        if (snackbar.showSnackbar("${glasses(units)} gelas dicatat", "Urungkan", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.delete(id)
                    } } }, { page = 2 }, permissionRevision)
                    1 -> History(state, vm)
                    else -> SettingsPage(state, vm, permissionRevision)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable private fun Home(s: WaterState, add: (Int) -> Unit, settings: () -> Unit, permissionRevision: Int) {
    val context = LocalContext.current
    val notificationsAllowed = remember(permissionRevision) { context.waterApp.reminders.notificationsAllowed() }
    Text("Water Supply", style = MaterialTheme.typography.headlineLarge)
    Text(s.date.format(dateFormat), style = MaterialTheme.typography.bodyMedium)
    Surface(onClick = settings, color = Color.White.copy(alpha = .6f), shape = RoundedCornerShape(24.dp)) {
        Text(if (!s.preferences.enabled) "Pengingat belum aktif" else if (!notificationsAllowed) "Aktifkan izin notifikasi" else if (s.total >= s.target) "Target tercapai · pengingat dijeda" else "Pengingat aktif · ${if (s.preferences.mode == ReminderMode.INTERVAL) "setiap ${s.preferences.interval} menit" else "${s.preferences.times.size} kali sehari"}",
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium)
    }
    WaterGlass(s.total, s.target, Modifier.fillMaxWidth().height(240.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${glasses(s.total)} dari ${glasses(s.target)} gelas", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(if (s.total >= s.target) "Target hari ini tercapai. Bagus!" else "Masih ${glasses((s.target - s.total).coerceAtLeast(0))} gelas menuju target", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = { add(2) }, Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink)) {
        Text("+1 gelas", style = MaterialTheme.typography.titleLarge)
    }
    OutlinedButton(onClick = { add(1) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) { Text("+½ gelas") }
    HorizontalDivider(color = Ink.copy(alpha = .18f))
    Text("Terakhir minum", style = MaterialTheme.typography.titleMedium)
    Text(s.today.firstOrNull()?.let { "${glasses(it.units)} gelas pukul ${timeOf(it)}" } ?: "Belum ada catatan hari ini. Mulai dari satu gelas.", style = MaterialTheme.typography.bodyLarge)
}

@Composable fun WaterGlass(total: Int, target: Int, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState((total.toFloat() / target).coerceIn(0f, 1f), label = "Isi gelas")
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Progres minum ${glasses(total)} dari ${glasses(target)} gelas" }) {
        val w = size.width; val h = size.height
        val left = w * .29f; val right = w * .71f; val top = h * .07f; val bottom = h * .94f
        val glass = Path().apply { moveTo(left, top); lineTo(right, top); lineTo(right - 16.dp.toPx(), bottom - 12.dp.toPx()); quadraticTo(right - 17.dp.toPx(), bottom, right - 29.dp.toPx(), bottom); lineTo(left + 29.dp.toPx(), bottom); quadraticTo(left + 17.dp.toPx(), bottom, left + 16.dp.toPx(), bottom - 12.dp.toPx()); close() }
        drawPath(glass, Color.White.copy(alpha = .58f))
        clipPath(glass) {
            val level = bottom - (bottom - top) * fill
            val water = Path().apply { moveTo(left - 5, level); cubicTo(w * .43f, level - 9.dp.toPx(), w * .55f, level + 9.dp.toPx(), right + 5, level); lineTo(right + 5, bottom + 5); lineTo(left - 5, bottom + 5); close() }
            drawPath(water, Water)
            for (i in 1..7) {
                val y = bottom - (bottom - top) * i / 8f
                drawLine(Ink.copy(alpha = .35f), Offset(right - 26.dp.toPx(), y), Offset(right - 12.dp.toPx(), y), 2.dp.toPx())
            }
        }
        drawPath(glass, Ink, style = Stroke(3.dp.toPx()))
        drawLine(Color.White.copy(alpha = .8f), Offset(left + 12.dp.toPx(), top + 15.dp.toPx()), Offset(left + 18.dp.toPx(), h * .46f), 4.dp.toPx())
    }
}
