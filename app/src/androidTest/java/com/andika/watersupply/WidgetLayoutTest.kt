package com.andika.watersupply

import android.content.res.Configuration
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.andika.watersupply.widget.WaterWidget
import com.andika.watersupply.widget.WidgetProgress
import com.andika.watersupply.widget.WaterWidgetReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetLayoutTest {
    @Test fun failureLayoutKeepsRetryAndAppAccessVisible() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        for (fontScale in listOf(1f, 1.3f, 2f)) {
            val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                this.fontScale = fontScale
            })
            for (width in listOf(140, 180, 250, 276)) {
                val result = GlanceRemoteViews().compose(context, DpSize(width.dp, 110.dp)) {
                    WaterWidget().ErrorContent()
                }
                instrumentation.runOnMainSync {
                    val parent = FrameLayout(context)
                    parent.addView(result.remoteViews.apply(context, parent))
                    val density = context.resources.displayMetrics.density
                    val w = (width * density).roundToInt()
                    val h = (110 * density).roundToInt()
                    parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    parent.layout(0, 0, w, h)
                    val nodes = descendants(parent)
                    val retry = nodes.single { it.contentDescription?.toString() == "Coba lagi" }
                    assertTrue(retry.height >= (48 * density).toInt())
                    assertTrue(retry.width >= (48 * density).toInt())
                    assertTrue(nodes.any { it.contentDescription?.toString() == "Catatan belum bisa dimuat. Buka Water Supply" })
                    nodes.filterIsInstance<TextView>().forEach { text ->
                        assertTrue("$width/$fontScale: ${text.text} visible", text.height > 0)
                        for (line in 0 until text.layout.lineCount) {
                            assertEquals("$width/$fontScale: ${text.text} truncated", 0, text.layout.getEllipsisCount(line))
                        }
                        assertTrue("$width/$fontScale: ${text.text} fits vertically",
                            text.layout.getLineBottom(text.layout.lineCount - 1) <= text.height - text.paddingTop - text.paddingBottom)
                    }
                }
            }
        }
    }

    @Test fun pickerProvidesFourColumnWidgetAndThumbnail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == ComponentName(context, WaterWidgetReceiver::class.java)
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(4, provider.targetCellWidth)
            assertEquals(2, provider.targetCellHeight)
        }
        val density = context.resources.displayMetrics.density
        assertEquals((276 * density).roundToInt(), provider.minWidth)
        assertEquals((110 * density).roundToInt(), provider.minHeight)
        assertEquals((250 * density).roundToInt(), provider.minResizeWidth)
        assertEquals((110 * density).roundToInt(), provider.minResizeHeight)
        assertTrue("Picker thumbnail must be set", provider.previewImage != 0)
        val preview = BitmapFactory.decodeResource(context.resources, provider.previewImage)
        assertNotNull("Picker thumbnail must be a valid bitmap", preview)
        preview?.recycle()
    }

    @Test fun responsiveLayoutsKeepActionsReachableAndRenderProgressStates() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        for (fontScale in listOf(1f, 1.3f, 2f)) {
            val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                this.fontScale = fontScale
            })
            val density = context.resources.displayMetrics.density
            for ((width, height) in listOf(140 to 110, 180 to 110, 250 to 110, 276 to 110,
                300 to 110, 140 to 150, 180 to 150, 250 to 150, 300 to 150, 250 to 180)) {
                for (total in listOf(0, 7, 16, 19)) {
                    val name = "widget-${width}x$height-total$total-font$fontScale"
                    val result = GlanceRemoteViews().compose(context, DpSize(width.dp, height.dp)) {
                        WaterWidget().Content(WidgetProgress(total, 16, total > 0))
                    }
                    instrumentation.runOnMainSync {
                        val parent = FrameLayout(context)
                        val view = result.remoteViews.apply(context, parent)
                        parent.addView(view)
                        val w = (width * density).roundToInt()
                        val h = (height * density).roundToInt()
                        parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                        parent.layout(0, 0, w, h)
                        val nodes = descendants(parent)
                        for (description in listOf("Tambah 1 gelas", "Tambah setengah gelas",
                            if (total > 0) "Urungkan catatan terakhir hari ini" else "Belum ada catatan hari ini untuk diurungkan")) {
                            val action = nodes.single { it.contentDescription?.toString() == description }
                            assertTrue("$name: $description width", action.width >= (48 * density).toInt())
                            assertTrue("$name: $description height", action.height >= (48 * density).toInt())
                            val location = IntArray(2)
                            action.getLocationOnScreen(location)
                            assertTrue("$name: $description bounds", location[0] >= 0 && location[1] >= 0 &&
                                location[0] + action.width <= w && location[1] + action.height <= h)
                            // Standalone RemoteViews have no bound widget ID for callbacks.
                            if (total == 0 && !description.startsWith("Tambah")) assertFalse(action.isClickable)
                        }
                        assertTrue("$name: progress accessibility", nodes.any {
                            it.contentDescription?.toString() == "Progres minum ${com.andika.watersupply.data.glasses(total)} dari 8 gelas hari ini"
                        })
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        parent.draw(Canvas(bitmap))
                        File(base.getExternalFilesDir(null), "$name.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                        nodes.filterIsInstance<TextView>().forEach {
                            assertTrue("$name: ${it.text} must be visible", it.height > 0)
                            assertEquals("$name: ${it.text} must not be truncated", 0, it.layout.getEllipsisCount(0))
                            assertTrue("$name: ${it.text} must fit vertically",
                                it.layout.getLineBottom(it.layout.lineCount - 1) <= it.height - it.paddingTop - it.paddingBottom)
                        }
                    }
                }
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
