package com.lightdesk.feature.notes

import com.lightdesk.core.common.TimeProvider

/** 可手动拨动的时钟，便于断言与截止时间/倒计时相关的行为。 */
class FakeTimeProvider(
    var current: Long = DEFAULT_NOW,
) : TimeProvider {

    override fun now(): Long = current

    fun advanceBy(millis: Long) {
        current += millis
    }

    companion object {
        /** 2023-11-14T22:13:20Z，取整秒方便断言。 */
        const val DEFAULT_NOW: Long = 1_700_000_000_000L
    }
}
