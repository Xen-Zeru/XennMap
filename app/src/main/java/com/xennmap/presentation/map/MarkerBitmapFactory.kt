package com.xennmap.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.xennmap.domain.model.PlaceCategory
import com.xennmap.ui.theme.Primary
import com.xennmap.ui.theme.Secondary
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws all map marker/label images with Canvas so no bundled image assets are needed. */
object MarkerBitmapFactory {

    private fun density(context: Context): Float = context.resources.displayMetrics.density

    /** Returns device pixels for a dp value (MapLibre icon bitmaps are in device pixels). */
    private fun dp(context: Context, value: Float): Float = value * density(context)

    fun placeIcon(context: Context, category: PlaceCategory): Bitmap {
        val sizeDp = 30f
        val sizePx = dp(context, sizeDp).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = sizePx / 2f
        val color = categoryColorArgb(category)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 2f)
        }
        canvas.drawCircle(c, c, c - dp(context, 2f), fill)
        canvas.drawCircle(c, c, c - dp(context, 2f), ring)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 1.8f)
            strokeCap = Paint.Cap.ROUND
        }

        when (category) {
            PlaceCategory.FISHING_SPOT -> {
                // Simple fish: body + tail.
                val body = RectF(dp(context, 9f), dp(context, 13f), dp(context, 20f), dp(context, 19f))
                canvas.drawOval(body, white)
                val tail = Path().apply {
                    moveTo(dp(context, 19f), dp(context, 16f))
                    lineTo(dp(context, 23.5f), dp(context, 12.5f))
                    lineTo(dp(context, 23.5f), dp(context, 19.5f))
                    close()
                }
                canvas.drawPath(tail, white)
                val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(dp(context, 11.5f), dp(context, 15.5f), dp(context, 1f), eye)
            }
            PlaceCategory.HOME_PORT -> {
                val house = Path().apply {
                    moveTo(dp(context, 10f), dp(context, 16f))
                    lineTo(dp(context, 10f), dp(context, 22f))
                    lineTo(dp(context, 22f), dp(context, 22f))
                    lineTo(dp(context, 22f), dp(context, 16f))
                }
                canvas.drawPath(house, stroke)
                val roof = Path().apply {
                    moveTo(dp(context, 8.5f), dp(context, 16f))
                    lineTo(dp(context, 16f), dp(context, 10f))
                    lineTo(dp(context, 23.5f), dp(context, 16f))
                }
                canvas.drawPath(roof, stroke)
            }
            PlaceCategory.DOCK -> {
                // Anchor: ring + shank + arms.
                canvas.drawCircle(dp(context, 16f), dp(context, 11f), dp(context, 2.2f), stroke)
                canvas.drawLine(dp(context, 16f), dp(context, 13.5f), dp(context, 16f), dp(context, 22f), stroke)
                canvas.drawLine(dp(context, 12f), dp(context, 17f), dp(context, 20f), dp(context, 17f), stroke)
                val arc = RectF(dp(context, 10.5f), dp(context, 16f), dp(context, 21.5f), dp(context, 26.5f))
                canvas.drawArc(arc, 20f, 140f, false, stroke)
            }
            PlaceCategory.DANGER -> {
                val triangle = Path().apply {
                    moveTo(dp(context, 16f), dp(context, 9.5f))
                    lineTo(dp(context, 23.5f), dp(context, 22.5f))
                    lineTo(dp(context, 8.5f), dp(context, 22.5f))
                    close()
                }
                canvas.drawPath(triangle, stroke)
                canvas.drawLine(dp(context, 16f), dp(context, 13.5f), dp(context, 16f), dp(context, 18f), stroke)
                canvas.drawCircle(dp(context, 16f), dp(context, 20.8f), dp(context, 1.1f), white)
            }
            PlaceCategory.WAYPOINT -> {
                val diamond = Path().apply {
                    moveTo(dp(context, 16f), dp(context, 10f))
                    lineTo(dp(context, 22f), dp(context, 16f))
                    lineTo(dp(context, 16f), dp(context, 22f))
                    lineTo(dp(context, 10f), dp(context, 16f))
                    close()
                }
                canvas.drawPath(diamond, white)
            }
            PlaceCategory.FAVORITE -> {
                val star = starPath(c, dp(context, 1f) + (sizeDp / 2f) * 0.52f, (sizeDp / 2f) * 0.24f + dp(context, 1f), 5)
                canvas.drawPath(star, white)
            }
            PlaceCategory.OTHER -> {
                canvas.drawCircle(c, c, dp(context, 2.5f), white)
            }
        }
        return bitmap
    }

    private fun starPath(centerX: Float, outerRadius: Float, innerRadius: Float, points: Int): Path {
        val path = Path()
        val startAngle = -Math.PI / 2
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = startAngle + i * Math.PI / points
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerX + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /**
     * Crosshair marker for a point the user selected on the map:
     * cyan ring + center dot + four tick marks extending outward.
     */
    fun crosshair(context: Context): Bitmap {
        val sizeDp = 44f
        val sizePx = dp(context, sizeDp).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = sizePx / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Secondary.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 3f)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(c, c, dp(context, 10f), ring)
        canvas.drawCircle(c, c, dp(context, 3.5f), dot)
        val tick = dp(context, 5f)
        val inner = dp(context, 15f)
        canvas.drawLine(c, c - inner, c, c - inner - tick, ring)
        canvas.drawLine(c, c + inner, c, c + inner + tick, ring)
        canvas.drawLine(c - inner, c, c - inner - tick, c, ring)
        canvas.drawLine(c + inner, c, c + inner + tick, c, ring)
        return bitmap
    }

    /** Cyan heading arrow (▲ with notched tail) for the current-position marker. */
    fun headingArrow(context: Context): Bitmap {        val sizeDp = 26f
        val sizePx = dp(context, sizeDp).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = sizePx / 2f
        val arrow = Path().apply {
            moveTo(c, dp(context, 2.5f))
            lineTo(dp(context, 21f), dp(context, 21.5f))
            lineTo(c, dp(context, 17.5f))
            lineTo(dp(context, 5f), dp(context, 21.5f))
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Secondary.toArgb()
            style = Paint.Style.FILL
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(context, 1.6f)
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
        return bitmap
    }

    /** Rounded depth label rendered as text (works fully offline, no glyph fonts needed). */
    fun depthLabel(context: Context, text: String, dark: Boolean): Bitmap {
        val textSize = dp(context, 10f)
        val padH = dp(context, 5f)
        val padV = dp(context, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            color = if (dark) 0xFFEAF6FF.toInt() else 0xFF06283A.toInt()
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xB30B1E2A.toInt() else 0xCCFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text)
        val width = (textWidth + padH * 2).roundToInt()
        val height = ((metrics.bottom - metrics.top) + padV * 2).roundToInt()
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            height / 2f, height / 2f, bg,
        )
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, width / 2f, baseline, paint)
        return bitmap
    }

    private fun categoryColorArgb(category: PlaceCategory): Int = when (category) {
        PlaceCategory.FISHING_SPOT -> Secondary.toArgb()
        PlaceCategory.HOME_PORT -> Primary.toArgb()
        PlaceCategory.DOCK -> 0xFF7FA8FF.toInt()
        PlaceCategory.DANGER -> 0xFFFF5C5C.toInt()
        PlaceCategory.WAYPOINT -> 0xFFFFB547.toInt()
        PlaceCategory.FAVORITE -> 0xFFFF7EB3.toInt()
        PlaceCategory.OTHER -> 0xFF91A7B5.toInt()
    }
}
