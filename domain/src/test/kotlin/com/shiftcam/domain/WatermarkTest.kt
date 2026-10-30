package com.shiftcam.domain

import com.shiftcam.domain.watermark.TextMetrics
import com.shiftcam.domain.watermark.WatermarkField
import com.shiftcam.domain.watermark.WatermarkInput
import com.shiftcam.domain.watermark.WatermarkLayoutEngine
import com.shiftcam.domain.watermark.WatermarkLayoutSpec
import com.shiftcam.domain.watermark.WatermarkTemplateCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeMetrics : TextMetrics {
    override fun width(text: String, fontSizePx: Float): Float = text.length * fontSizePx
    override fun lineHeight(fontSizePx: Float): Float = fontSizePx * 1.35f
}

private val sampleInput = WatermarkInput(
    machineName = "小松 PC200-8",
    machineCode = "KM-001",
    siteName = "滨江工地",
    workType = "挖掘",
    shiftNo = "第1班",
    clockIn = "07:30",
    clockOut = "17:30",
    workHours = "9.5h",
    count = "45",
    remark = "土方外运，路况较差",
    remarkInfo = "联系人：张工 13800000000",
    weather = "晴 26℃",
    location = "杭州市滨江区",
    dateTime = "2026-10-30",
)

class WatermarkLayoutTest {

    private val metrics = FakeMetrics()
    private val spec = WatermarkLayoutSpec()

    @Test
    fun `标签无手工空格且备注锁定`() {
        WatermarkField.entries.forEach { field ->
            assertFalse(field.label.contains(" "), "标签不应含手工空格：${field.label}")
        }
        assertEquals("备注", WatermarkField.REMARK.label)
        assertEquals("备注信息", WatermarkField.REMARK_INFO.label)
        assertFalse(WatermarkField.REMARK.labelEditable)
        assertFalse(WatermarkField.REMARK_INFO.labelEditable)
    }

    @Test
    fun `值列按标签列悬挂对齐`() {
        val layout = WatermarkLayoutEngine.layout(sampleInput, spec, metrics, 1080, 1440)
        assertTrue(layout.rows.isNotEmpty())
        val expectedX = layout.paddingPx + layout.labelColumnWidth
        layout.rows.forEach { row ->
            row.lines.forEach { line -> assertEquals(expectedX, line.x, 0.01f) }
        }
        assertTrue(layout.labelColumnWidth >= metrics.width("测测测测", layout.fontSizePx))
    }

    @Test
    fun `长备注按列宽换行且首行不溢出`() {
        val longRemark = "土方外运，路况较差，".repeat(5)
        val layout = WatermarkLayoutEngine.layout(sampleInput.copy(remark = longRemark), spec, metrics, 1080, 1440)
        val remarkRow = layout.rows.first { it.field == WatermarkField.REMARK }
        assertTrue(remarkRow.lines.size > 1)
        remarkRow.lines.forEach { line ->
            assertTrue(
                metrics.width(line.text, layout.fontSizePx) <= layout.blockRight - line.x + 0.01f,
                "行宽溢出：${line.text}",
            )
        }
    }

    @Test
    fun `分辨率翻倍时字号与内边距同步翻倍`() {
        val hd = WatermarkLayoutEngine.layout(sampleInput, spec, metrics, 1080, 1920)
        val qhd = WatermarkLayoutEngine.layout(sampleInput, spec, metrics, 2160, 3840)
        assertEquals(2f, qhd.fontSizePx / hd.fontSizePx, 0.001f)
        assertEquals(2f, qhd.paddingPx / hd.paddingPx, 0.001f)
    }

    @Test
    fun `高度超限时按优先级丢弃天气`() {
        val crowded = sampleInput.copy(
            remark = "这是一段非常长的备注".repeat(20),
            remarkInfo = "补充说明".repeat(20),
        )
        val layout = WatermarkLayoutEngine.layout(crowded, spec, metrics, 1080, 1080)
        assertTrue(WatermarkField.WEATHER in layout.droppedFields)
        assertTrue(layout.blockHeight <= 1080 * spec.maxHeightRatio + 0.01f)
        assertTrue(layout.rows.none { it.field == WatermarkField.WEATHER })
    }

    @Test
    fun `空字段不渲染`() {
        val layout = WatermarkLayoutEngine.layout(sampleInput.copy(weather = "", location = ""), spec, metrics, 1080, 1440)
        assertTrue(layout.rows.none { it.field == WatermarkField.WEATHER })
        assertTrue(layout.rows.none { it.field == WatermarkField.LOCATION })
    }

    @Test
    fun `关闭可见性后不渲染该字段`() {
        val hidden = spec.copy(
            fields = spec.fields.map {
                if (it.field == WatermarkField.COUNT) it.copy(visible = false) else it
            },
        )
        val layout = WatermarkLayoutEngine.layout(sampleInput, hidden, metrics, 1080, 1440)
        assertTrue(layout.rows.none { it.field == WatermarkField.COUNT })
    }
}

class TemplateCodecTest {

    @Test
    fun `v2 JSON 往返一致`() {
        val spec = WatermarkLayoutSpec(
            maxHeightRatio = 0.35f,
            fields = WatermarkLayoutSpec().fields.map {
                if (it.field == WatermarkField.WEATHER) it.copy(visible = false) else it
            },
        )
        val roundTrip = WatermarkTemplateCodec.decode(WatermarkTemplateCodec.encode(spec))
        assertEquals(2, roundTrip.schemaVersion)
        assertEquals(0.35f, roundTrip.maxHeightRatio)
        assertFalse(roundTrip.fields.first { it.field == WatermarkField.WEATHER }.visible)
    }

    @Test
    fun `旧版 JSON 回落默认版式`() {
        val legacy = """{"styleConfigJson":"{}","fieldsJson":"[]"}"""
        val spec = WatermarkTemplateCodec.decode(legacy)
        assertEquals(WatermarkLayoutSpec.CURRENT_SCHEMA_VERSION, spec.schemaVersion)
        assertTrue(spec.fields.all { it.visible })
    }

    @Test
    fun `空或坏 JSON 不崩溃`() {
        assertEquals(2, WatermarkTemplateCodec.decode(null).schemaVersion)
        assertEquals(2, WatermarkTemplateCodec.decode("{bad json").schemaVersion)
    }
}
