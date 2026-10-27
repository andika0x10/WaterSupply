package com.andika.watersupply.widget

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.andika.watersupply.MainActivity
import com.andika.watersupply.R
import com.andika.watersupply.data.glasses
import com.andika.watersupply.data.currentDateFlow
import com.andika.watersupply.waterApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private val Ink = Color(0xFF17384D)
internal data class WidgetProgress(val total: Int, val target: Int, val canUndo: Boolean)

class WaterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(
        DpSize(140.dp, 150.dp), DpSize(180.dp, 150.dp),
        DpSize(250.dp, 150.dp), DpSize(250.dp, 180.dp)
    ))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.waterApp.repository
        val progress = context.currentDateFlow().flatMapLatest { date ->
            combine(repository.dao.observeTotal(date.toString()), repository.dao.observeTarget(date.toString())) { total, target ->
                WidgetProgress(total, target ?: 16, total > 0)
            }
        }.distinctUntilChanged()
        // A live Glance session recomposes on update without rerunning provideGlance.
        // Wait for the first Room emission rather than showing an actionable stale snapshot.
        provideContent {
            val value by progress.collectAsState(initial = null)
            val current = value
            if (current == null) {
                Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).padding(12.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Memuat catatan…", style = textStyle(14))
                }
            } else Content(current)
        }
    }

    @Composable internal fun Content(value: WidgetProgress) {
        val narrow = LocalSize.current.width < 180.dp
        val compact = LocalSize.current.width < 250.dp
        val short = LocalSize.current.height < 180.dp
        val fontScale = LocalContext.current.resources.configuration.fontScale
        val largeText = fontScale >= 1.3f
        val bitmap = remember(value.total, value.target) { waterGlassBitmap(value.total, value.target) }
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background))
            .appWidgetBackground().padding(if (narrow) 6.dp else 12.dp)) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = "Progres minum ${glasses(value.total)} dari ${glasses(value.target)} gelas hari ini" },
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!largeText && (!short || fontScale <= 1f)) {
                        Text("Water Supply", style = textStyle(if (compact) 12 else 14, FontWeight.Medium), maxLines = 1)
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(bitmap), contentDescription = null,
                            modifier = GlanceModifier.width(if (narrow) 24.dp else if (compact) 36.dp else 48.dp)
                                .height(if (narrow || short && !compact) 44.dp else if (compact) 54.dp else 72.dp))
                        Spacer(GlanceModifier.width(if (narrow) 3.dp else 8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            if (compact && largeText) {
                                Text(glasses(value.total), maxLines = 1, style = textStyle(if (narrow) 14 else 16, FontWeight.Bold))
                                Text("/ ${glasses(value.target)}${if (narrow) "" else " gelas"}", maxLines = 1,
                                    style = textStyle(12))
                            } else {
                                Text("${glasses(value.total)} / ${glasses(value.target)}", maxLines = 1,
                                    style = textStyle(if (narrow) 14 else if (short && largeText) 18 else if (compact || short || largeText) 20 else 26, FontWeight.Bold))
                                Text("gelas hari ini", maxLines = if (narrow) 2 else 1,
                                    style = textStyle(if (narrow || short && largeText) 10 else 12))
                            }
                            if (!compact && !largeText && !short) {
                                Spacer(GlanceModifier.height(4.dp))
                                Text(if (value.total >= value.target) "Target tercapai" else
                                    "Sisa ${glasses(value.target - value.total)} gelas", style = textStyle(12), maxLines = 1)
                            }
                        }
                    }
                }
                if (narrow) {
                    Spacer(GlanceModifier.width(4.dp))
                    UndoButton(value.canUndo)
                }
            }
            Spacer(GlanceModifier.height(if (narrow) 4.dp else 8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AddButton(if (compact || largeText) "+1" else "+1 gelas", "Tambah 1 gelas", actionRunCallback<AddGlass>(),
                    R.drawable.widget_action_primary, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                AddButton(if (compact || largeText) "+½" else "+½ gelas", "Tambah setengah gelas", actionRunCallback<AddHalf>(),
                    R.drawable.widget_action_secondary, GlanceModifier.defaultWeight())
                if (!narrow) {
                    Spacer(GlanceModifier.width(4.dp))
                    UndoButton(value.canUndo)
                }
            }
        }
    }

    @Composable private fun AddButton(label: String, description: String, action: Action, background: Int, modifier: GlanceModifier) {
        Box(modifier.height(48.dp).background(ImageProvider(background)).clickable(action)
            .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Text(label, style = textStyle(14, FontWeight.Bold), maxLines = 1)
        }
    }

    @Composable private fun UndoButton(enabled: Boolean) {
        val modifier = GlanceModifier.size(48.dp).background(ImageProvider(R.drawable.widget_action_secondary))
            .semantics { contentDescription = if (enabled) "Urungkan catatan terakhir hari ini" else "Belum ada catatan hari ini untuk diurungkan" }
        Box(if (enabled) modifier.clickable(actionRunCallback<UndoLastDrink>()) else modifier,
            contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_widget_undo), contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(ColorProvider(if (enabled) Ink else Color(0xFF95BDD7))))
        }
    }
}

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(Ink), fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif)

class AddGlass : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.waterApp.repository.record(2)
        context.waterApp.refresh()
    }
}
class AddHalf : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.waterApp.repository.record(1)
        context.waterApp.refresh()
    }
}
class UndoLastDrink : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.waterApp.repository.undoLatestToday()
        context.waterApp.refresh()
    }
}
class WaterWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = WaterWidget() }
