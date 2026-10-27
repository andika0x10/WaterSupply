package com.andika.watersupply.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

/** Fixed, small bitmap keeps RemoteViews payload bounded at every launcher size. */
internal fun waterGlassBitmap(total: Int, target: Int): Bitmap {
    val bitmap = createBitmap(144, 216)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val glass = Path().apply {
        moveTo(15f, 12f); lineTo(129f, 12f); lineTo(113f, 192f)
        quadTo(112f, 204f, 100f, 204f); lineTo(44f, 204f)
        quadTo(32f, 204f, 31f, 192f); close()
    }
    paint.color = 0x99FFFFFF.toInt()
    canvas.drawPath(glass, paint)
    canvas.withClip(glass) {
        val fraction = if (target > 0) (total.toFloat() / target).coerceIn(0f, 1f) else 0f
        if (fraction > 0f) {
            val level = 204f - 192f * fraction
            val water = Path().apply {
                moveTo(0f, level)
                if (fraction < 1f) cubicTo(48f, level - 12f, 84f, level + 12f, 144f, level)
                else lineTo(144f, level)
                lineTo(144f, 216f); lineTo(0f, 216f); close()
            }
            paint.color = 0xFF78A4CB.toInt()
            canvas.drawPath(water, paint)
        }
        paint.color = 0x6617384D
        paint.strokeWidth = 2.5f
        for (mark in 1..7) {
            val y = 204f - 192f * mark / 8f
            val edge = 113f + (204f - y) / 192f * 16f
            canvas.drawLine(edge - 20f, y, edge - 8f, y, paint)
        }
        paint.color = 0xCCFFFFFF.toInt()
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(29f, 30f, 35f, 100f, paint)
    }
    paint.color = Color.rgb(23, 56, 77)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    paint.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(glass, paint)
    return bitmap
}
