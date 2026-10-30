package com.shiftcam.app.data.repo

/** 领域异常：UI 捕获后给出可读提示，不把 SQLite 异常直接抛给用户。 */
sealed class DomainException(message: String) : Exception(message) {
    class AlreadyRunning : DomainException("已有进行中的班次，请先下班打卡")
    class NoRunningSession : DomainException("当前没有进行中的班次")
    class SessionNotFound : DomainException("班次不存在")
    class MachineNotFound : DomainException("设备不存在，请先在基础资料中添加")
    class ReadingRejected(val code: String, message: String) : DomainException(message)
    class ConfirmRequired(message: String) : DomainException(message)
    class UndoExpired : DomainException("撤销窗口已过（超过 3 秒）")
    class NothingToUndo : DomainException("没有可撤销的切换")
    class SameWorkType : DomainException("已经是该施工方式")
}
