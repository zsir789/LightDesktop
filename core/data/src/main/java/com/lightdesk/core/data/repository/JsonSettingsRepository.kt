package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.map
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.SettingsStore
import com.lightdesk.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsonSettingsRepository(
    private val store: JsonFileStore<SettingsStore>,
) : SettingsRepository {

    private val state = MutableStateFlow(AppSettings())
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false

    override fun observeSettings(): Flow<AppSettings> = state.onStart { ensureLoaded() }

    override suspend fun current(): AppSettings {
        ensureLoaded()
        return state.value
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit> =
        writeMutex.withLock {
            ensureLoaded()
            val updated = transform(state.value)
            store.write(SettingsStore(settings = updated)).map { state.value = updated }
        }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (!loaded) {
                state.value = store.read().settings
                loaded = true
            }
        }
    }
}
