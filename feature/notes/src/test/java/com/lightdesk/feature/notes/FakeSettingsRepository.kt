package com.lightdesk.feature.notes

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.data.repository.SettingsRepository
import com.lightdesk.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 设置仓储测试替身：StatsTracker 需要读取其中的「使用统计」开关。 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    val state = MutableStateFlow(initial)

    override fun observeSettings(): Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit> {
        state.value = transform(state.value)
        return AppResult.Success(Unit)
    }
}
