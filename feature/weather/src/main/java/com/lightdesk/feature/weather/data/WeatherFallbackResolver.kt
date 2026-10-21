package com.lightdesk.feature.weather.data

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.getOrNull
import com.lightdesk.core.model.WeatherSnapshot

/** 一次天气加载的展示结论：在线数据、离线缓存、或彻底失败。 */
data class WeatherLoadOutcome(
    val snapshot: WeatherSnapshot?,
    val isOffline: Boolean,
    val errorMessage: String?,
)

/**
 * 天气降级策略（纯逻辑，便于单元测试）：
 * 1. 联网成功 -> 展示在线数据；
 * 2. 联网失败但有缓存 -> 展示缓存并标记“离线缓存”；
 * 3. 联网失败且无缓存 -> 展示错误信息，由页面给出重试入口。
 */
object WeatherFallbackResolver {

    fun resolve(
        online: AppResult<WeatherSnapshot>?,
        cached: WeatherSnapshot?,
    ): WeatherLoadOutcome {
        val onlineSnapshot = online?.getOrNull()
        return when {
            onlineSnapshot != null -> WeatherLoadOutcome(
                snapshot = onlineSnapshot,
                isOffline = false,
                errorMessage = null,
            )

            cached != null -> WeatherLoadOutcome(
                snapshot = cached,
                isOffline = true,
                errorMessage = null,
            )

            online is AppResult.Failure -> WeatherLoadOutcome(
                snapshot = null,
                isOffline = false,
                errorMessage = online.error.message,
            )

            else -> WeatherLoadOutcome(snapshot = null, isOffline = false, errorMessage = null)
        }
    }
}
