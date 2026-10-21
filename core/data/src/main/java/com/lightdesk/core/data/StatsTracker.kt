package com.lightdesk.core.data

import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.Logger
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.data.repository.StatsRepository
import com.lightdesk.core.model.FeatureKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 统计埋点入口：功能模块只调用这里的方法，不关心持久化细节。
 *
 * 记录失败不应影响主流程，因此统一在协程内兜底捕获（异常不会抛给 UI）。
 * 新增统计类型时在此追加方法即可，调用方（各模块）保持解耦。
 */
class StatsTracker(
    private val repository: StatsRepository,
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider,
    private val logger: Logger = AppLogger,
) {
    fun trackVisit(feature: FeatureKey) = record("visit:$feature") {
        repository.recordVisit(feature, timeProvider.now())
    }

    fun trackNoteCreated() = record("noteCreated") {
        repository.recordNoteCreated(timeProvider.now())
    }

    fun trackNoteCompleted() = record("noteCompleted") {
        repository.recordNoteCompleted(timeProvider.now())
    }

    fun trackWeatherCondition(condition: String) = record("weather:$condition") {
        repository.recordWeatherCondition(condition, timeProvider.now())
    }

    private fun record(name: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure {
                logger.w(TAG, "记录统计失败: $name", it)
            }
        }
    }

    private companion object {
        const val TAG = "StatsTracker"
    }
}
