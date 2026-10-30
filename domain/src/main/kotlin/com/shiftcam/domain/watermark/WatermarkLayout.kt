package com.shiftcam.domain.watermark

/**
 * 文本测量抽象：Android 侧用 TextPaint，测试侧用等宽假实现。
 * 预览（Compose Canvas）与成片（Bitmap Canvas）必须注入同一套测量结果，才能「预览 = 成片」。
 */
interface TextMetrics {
    fun width(text: String, fontSizePx: Float): Float
    fun lineHeight(fontSizePx: Float): Float
}

/** 水印渲染所需的全部业务值，全部是已格式化好的字符串。 */
data class WatermarkInput(
    val machineName: String = "",
    val machineCode: String = "",
    val siteName: String = "",
    val workType: String = "",
    val shiftNo: String = "",
    val clockIn: String = "",
    val clockOut: String = "",
    val workHours: String = "",
    val count: String = "",
    val remark: String = "",
    val remarkInfo: String = "",
    val weather: String = "",
    val location: String = "",
    val dateTime: String = "",
) {
    fun valueOf(field: WatermarkField): String = when (field) {
        WatermarkField.MACHINE_NAME -> machineName
        WatermarkField.MACHINE_CODE -> machineCode
        WatermarkField.SITE_NAME -> siteName
        WatermarkField.WORK_TYPE -> workType
        WatermarkField.SHIFT_NO -> shiftNo
        WatermarkField.CLOCK_IN -> clockIn
        WatermarkField.CLOCK_OUT -> clockOut
        WatermarkField.WORK_HOURS -> workHours
        WatermarkField.COUNT -> count
        WatermarkField.REMARK -> remark
        WatermarkField.REMARK_INFO -> remarkInfo
        WatermarkField.DATE_TIME -> dateTime
        WatermarkField.LOCATION -> location
        WatermarkField.WEATHER -> weather
    }
}

data class WatermarkLine(
    val text: String,
    val x: Float,
    val top: Float,
    val bottom: Float,
)

data class WatermarkRow(
    val field: WatermarkField,
    val label: String,
    val labelX: Float,
    val labelTop: Float,
    val lines: List<WatermarkLine>,
)

data class WatermarkLayout(
    val fontSizePx: Float,
    val paddingPx: Float,
    val labelColumnWidth: Float,
    val rows: List<WatermarkRow>,
    val blockLeft: Float,
    val blockTop: Float,
    val blockRight: Float,
    val blockBottom: Float,
    val bgAlpha: Float,
    val droppedFields: List<WatermarkField>,
) {
    val blockWidth: Float get() = blockRight - blockLeft
    val blockHeight: Float get() = blockBottom - blockTop
}

/**
 * 纯 Kotlin 版式引擎（INV-06）：预览与成片都调用 [layout]，不允许第二套排版。
 *
 * 排版规则：
 * - 长边缩放：fontSize/padding/行距都按 canvas 长边与 referenceLongEdge 的比例计算。
 * - 标签列宽 = max(最宽标签, labelPadTo 个汉字宽)；值列悬挂缩进对齐，长文本按列宽换行。
 * - 空值字段不渲染；超出 maxHeightRatio 时按 [degradePriority] 从低到高丢弃。
 * - 水印块贴左下，背景半透明。
 */
object WatermarkLayoutEngine {

    /** 降级顺序：列表越靠前越先被丢弃（天气最不重要）。 */
    val degradePriority: List<WatermarkField> = listOf(
        WatermarkField.WEATHER,
        WatermarkField.LOCATION,
        WatermarkField.DATE_TIME,
        WatermarkField.SHIFT_NO,
        WatermarkField.CLOCK_OUT,
        WatermarkField.WORK_TYPE,
        WatermarkField.SITE_NAME,
        WatermarkField.REMARK_INFO,
        WatermarkField.REMARK,
        WatermarkField.COUNT,
        WatermarkField.WORK_HOURS,
        WatermarkField.CLOCK_IN,
        WatermarkField.MACHINE_CODE,
    )

    fun layout(
        input: WatermarkInput,
        spec: WatermarkLayoutSpec,
        metrics: TextMetrics,
        canvasWidth: Int,
        canvasHeight: Int,
    ): WatermarkLayout {
        require(canvasWidth > 0 && canvasHeight > 0) { "canvas 尺寸必须为正" }

        val longEdge = maxOf(canvasWidth, canvasHeight).toFloat()
        val scale = longEdge / spec.referenceLongEdge.toFloat()
        val fontSize = (longEdge * spec.fontSizeRatio).coerceAtLeast(8f)
        val padding = longEdge * spec.paddingRatio
        val rowGap = longEdge * spec.lineSpacingRatio
        val lineHeight = metrics.lineHeight(fontSize)
        val contentWidth = canvasWidth - padding * 2
        val maxHeight = canvasHeight * spec.maxHeightRatio

        var fields = spec.fields
            .asSequence()
            .filter { it.visible }
            .sortedBy { it.order }
            .map { it.field }
            .filter { input.valueOf(it).isNotBlank() }
            .toList()

        val labelColumnWidth = computeLabelColumnWidth(fields, spec, metrics, fontSize, scale)
        val valueX = padding + labelColumnWidth
        val valueWidth = (contentWidth - labelColumnWidth).coerceAtLeast(fontSize * 2f)

        var totalHeight = 0f
        val dropped = mutableListOf<WatermarkField>()
        while (true) {
            totalHeight = measureHeight(fields, input, metrics, fontSize, lineHeight, rowGap, valueWidth)
            if (totalHeight <= maxHeight) break
            val candidate = degradePriority.firstOrNull { it in fields }
                ?: fields.lastOrNull { it != WatermarkField.MACHINE_NAME }
                ?: break
            if (candidate == WatermarkField.MACHINE_NAME) break
            fields = fields - candidate
            dropped += candidate
        }

        var top = canvasHeight - padding - totalHeight
        val rows = mutableListOf<WatermarkRow>()
        for (field in fields) {
            val label = field.label
            val value = input.valueOf(field)
            val wrapped = wrap(value, valueWidth, fontSize, metrics)
            val lines = mutableListOf<WatermarkLine>()
            var lineTop = top
            for (text in wrapped) {
                lines += WatermarkLine(
                    text = text,
                    x = valueX,
                    top = lineTop,
                    bottom = lineTop + lineHeight,
                )
                lineTop += lineHeight
            }
            rows += WatermarkRow(
                field = field,
                label = label,
                labelX = padding,
                labelTop = top,
                lines = lines,
            )
            top += wrapped.size * lineHeight + rowGap
        }

        return WatermarkLayout(
            fontSizePx = fontSize,
            paddingPx = padding,
            labelColumnWidth = labelColumnWidth,
            rows = rows,
            blockLeft = padding,
            blockTop = canvasHeight - padding - totalHeight,
            blockRight = canvasWidth - padding,
            blockBottom = canvasHeight - padding,
            bgAlpha = spec.contentBgAlpha,
            droppedFields = dropped,
        )
    }

    private fun computeLabelColumnWidth(
        fields: List<WatermarkField>,
        spec: WatermarkLayoutSpec,
        metrics: TextMetrics,
        fontSize: Float,
        scale: Float,
    ): Float {
        val widest = fields.maxOfOrNull { metrics.width(it.label, fontSize) } ?: 0f
        val probe = "测".repeat(spec.labelPadTo.coerceIn(1, 8))
        val minColumn = metrics.width(probe, fontSize)
        return maxOf(widest, minColumn) + fontSize * 0.35f * scale
    }

    private fun measureHeight(
        fields: List<WatermarkField>,
        input: WatermarkInput,
        metrics: TextMetrics,
        fontSize: Float,
        lineHeight: Float,
        rowGap: Float,
        valueWidth: Float,
    ): Float {
        return fields.sumOf { field ->
            val lines = wrap(input.valueOf(field), valueWidth, fontSize, metrics).size
            (lines * lineHeight + rowGap).toDouble()
        }.toFloat()
    }

    private fun wrap(
        text: String,
        maxWidth: Float,
        fontSize: Float,
        metrics: TextMetrics,
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        if (maxWidth <= 1f) return listOf(text)
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            val candidate = current.toString() + ch
            if (current.isNotEmpty() && metrics.width(candidate, fontSize) > maxWidth) {
                lines += current.toString()
                current.clear()
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf("") }
    }
}
