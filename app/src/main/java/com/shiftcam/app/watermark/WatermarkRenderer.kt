package com.shiftcam.app.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.shiftcam.domain.watermark.TextMetrics
import com.shiftcam.domain.watermark.WatermarkInput
import com.shiftcam.domain.watermark.WatermarkLayoutEngine
import com.shiftcam.domain.watermark.WatermarkLayoutSpec

/** Android 文本测量：预览与成片的排版都走这一套（INV-06）。 */
class AndroidTextMetrics(private val paint: Paint) : TextMetrics {
    override fun width(text: String, fontSizePx: Float): Float {
        paint.textSize = fontSizePx
        return paint.measureText(text)
    }

    override fun lineHeight(fontSizePx: Float): Float = fontSizePx * 1.32f
}

/**
 * 水印渲染唯一实现（owner D）。
 * 预览（Compose Canvas 的 nativeCanvas）与成片（Bitmap Canvas）都调用 [draw]，
 * 因此版式、字号、换行、降级结果完全一致。
 */
object WatermarkRenderer {

    fun render(src: Bitmap, spec: WatermarkLayoutSpec, input: WatermarkInput): Bitmap {
        val mutable = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) {
            src
        } else {
            src.copy(Bitmap.Config.ARGB_8888, true) ?: src
        }
        val canvas = Canvas(mutable)
        draw(canvas, mutable.width, mutable.height, spec, input)
        return mutable
    }

    fun draw(canvas: Canvas, width: Int, height: Int, spec: WatermarkLayoutSpec, input: WatermarkInput) {
        if (width <= 0 || height <= 0) return
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 255, 255, 255)
            typeface = Typeface.DEFAULT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
        val metrics = AndroidTextMetrics(labelPaint)
        val layout = WatermarkLayoutEngine.layout(input, spec, metrics, width, height)

        val bgPaint = Paint().apply {
            color = Color.argb((layout.bgAlpha * 255).toInt().coerceIn(0, 255), 0, 0, 0)
        }
        canvas.drawRect(layout.blockLeft, layout.blockTop, layout.blockRight, layout.blockBottom, bgPaint)

        val fontMetrics = labelPaint.fontMetrics
        layout.rows.forEach { row ->
            labelPaint.textSize = layout.fontSizePx
            valuePaint.textSize = layout.fontSizePx
            canvas.drawText(row.label, row.labelX, row.labelTop - fontMetrics.ascent, labelPaint)
            row.lines.forEach { line ->
                canvas.drawText(line.text, line.x, line.top - fontMetrics.ascent, valuePaint)
            }
        }
    }
}
