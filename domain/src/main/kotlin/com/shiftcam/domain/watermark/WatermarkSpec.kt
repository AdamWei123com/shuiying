package com.shiftcam.domain.watermark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 水印字段。标签以文档为准，「备注」「备注信息」锁定不可改名（INV-11 / R25）。
 * 数据里不存任何手工空格，对齐由 [WatermarkLayoutEngine] 按 labelPadTo 补白（ADR-009）。
 */
enum class WatermarkField(
    val label: String,
    val labelEditable: Boolean,
    val defaultOrder: Int,
) {
    MACHINE_NAME("设备名称", true, 0),
    MACHINE_CODE("设备编号", true, 1),
    SITE_NAME("工地", true, 2),
    WORK_TYPE("施工方式", true, 3),
    SHIFT_NO("班次", true, 4),
    CLOCK_IN("上班", true, 5),
    CLOCK_OUT("下班", true, 6),
    WORK_HOURS("工时", true, 7),
    COUNT("计数", true, 8),
    REMARK("备注", false, 9),
    REMARK_INFO("备注信息", false, 10),
    DATE_TIME("日期", true, 11),
    LOCATION("位置", true, 12),
    WEATHER("天气", true, 13),
}

@Serializable
data class WatermarkFieldSpec(
    val field: WatermarkField,
    val visible: Boolean = true,
    val order: Int = field.defaultOrder,
)

/**
 * 水印版式规格：预览与成片共用同一份（INV-06）。
 * 尺寸一律用「比例」而非绝对像素，按长边缩放，保证不同分辨率不漂移。
 */
@Serializable
data class WatermarkLayoutSpec(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val referenceLongEdge: Int = 1080,
    val fontSizeRatio: Float = 0.026f,
    val paddingRatio: Float = 0.028f,
    val lineSpacingRatio: Float = 0.008f,
    /** 标签列最少按 4 个汉字宽补白（仅渲染，数据不补空格）。 */
    val labelPadTo: Int = 4,
    /** 水印块最多占画面高度的比例，超出按优先级降级。 */
    val maxHeightRatio: Float = 0.40f,
    val contentBgAlpha: Float = 0.55f,
    val fields: List<WatermarkFieldSpec> = defaultFieldSpecs(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}

fun defaultFieldSpecs(): List<WatermarkFieldSpec> =
    WatermarkField.entries.map { WatermarkFieldSpec(field = it, visible = true, order = it.defaultOrder) }

/**
 * 模板 JSON 编解码。JSON 只存「是否可见 / 顺序」，不存任何手工空格。
 * 旧版（schemaVersion < 2 或字段名 styleConfigJson/fieldsJson）一律回落默认版式并保留容错。
 */
object WatermarkTemplateCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(spec: WatermarkLayoutSpec): String = json.encodeToString(WatermarkLayoutSpec.serializer(), spec)

    fun decode(raw: String?): WatermarkLayoutSpec {
        if (raw.isNullOrBlank()) return WatermarkLayoutSpec()
        return try {
            val element = json.parseToJsonElement(raw)
            if (element !is JsonObject) return WatermarkLayoutSpec()
            val version = element["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
            if (version < WatermarkLayoutSpec.CURRENT_SCHEMA_VERSION) {
                WatermarkLayoutSpec()
            } else {
                json.decodeFromJsonElement(WatermarkLayoutSpec.serializer(), element)
            }
        } catch (t: Throwable) {
            WatermarkLayoutSpec()
        }
    }
}
