package com.lightdesk.core.data.repository

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.map
import com.lightdesk.core.data.store.CourseTableStore
import com.lightdesk.core.data.store.JsonFileStore
import com.lightdesk.core.model.CourseTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsonCourseRepository(
    private val store: JsonFileStore<CourseTableStore>,
) : CourseRepository {

    private val state = MutableStateFlow<CourseTable?>(null)
    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile
    private var loaded = false

    override fun observeTable(): Flow<CourseTable?> = state.onStart { ensureLoaded() }

    override suspend fun replace(table: CourseTable): AppResult<Unit> = writeMutex.withLock {
        ensureLoaded()
        store.write(CourseTableStore(table = table)).map { state.value = table }
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
                state.value = store.read().table
                loaded = true
            }
        }
    }
}
