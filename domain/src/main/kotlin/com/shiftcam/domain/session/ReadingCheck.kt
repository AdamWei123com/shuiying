package com.shiftcam.domain.session

enum class ReadingCheckCode {
    OK,
    MISSING,
    ANOMALY,
    INVALID,
}

data class ReadingCheck(
    val code: ReadingCheckCode,
    val message: String?,
) {
    val requiresConfirm: Boolean
        get() = code == ReadingCheckCode.ANOMALY
}
