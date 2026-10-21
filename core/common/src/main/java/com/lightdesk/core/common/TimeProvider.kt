package com.lightdesk.core.common

/**
 * 统一的时间源抽象。
 *
 * 所有涉及“当前时间”的逻辑都必须通过该接口获取时间，便于单元测试注入固定时钟，
 * 避免哈希硬编码 [System.currentTimeMillis] 造成不可测。
 */
interface TimeProvider {
    fun now(): Long
}

/** 生产环境实现：跟随系统时钟。 */
class SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
