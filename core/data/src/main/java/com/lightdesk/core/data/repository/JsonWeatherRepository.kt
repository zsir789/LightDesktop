package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.map
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.data.store.WeatherCacheStore
import com.lightdesk.core.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsonWeatherRepository(
    private val store: JsonFileStore<WeatherCacheStore>,
) : WeatherRepository {

    private val state = MutableStateFlow<WeatherSnapshot?>(null)
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false

    override fun observeCache(): Flow<WeatherSnapshot?> = state.onStart { ensureLoaded() }

    override suspend fun cached(): WeatherSnapshot? {
        ensureLoaded()
        return state.value
    }

    override suspend fun save(snapshot: WeatherSnapshot): AppResult<Unit> = writeMutex.withLock {
        ensureLoaded()
        store.write(WeatherCacheStore(snapshot = snapshot)).map { state.value = snapshot }
    }

    override suspend fun clear(): AppResult<Unit> = writeMutex.withLock {
        store.clear().map {
            state.value = null
            loaded = true
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (!loaded) {
                state.value = store.read().snapshot
                loaded = true
            }
        }
    }
}
