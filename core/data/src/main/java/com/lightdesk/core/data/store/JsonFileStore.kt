package com.lightdesk.core.data.store

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppLogger
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.common.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 基于 JSON 文件的通用本地持久化存储。
 *
 * 设计要点：
 * 1. 读：文件损坏/格式不兼容时自动备份损坏文件并回退默认值，绝不崩溃；
 * 2. 写：先写临时文件再原子替换，避免进程被杀导致半截文件；
 * 3. 并发：内部 Mutex 串行化读写，配合内存缓存减少 IO；
 * 4. 迁移：可选 [migrator] 按文件根部的 `schemaVersion` 做结构迁移，迁移失败保留原文件。
 *
 * 后续若迁移到 Room/DataStore，只需替换仓储实现，接口不变。
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val defaultValue: () -> T,
    private val logger: Logger = AppLogger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val migrator: JsonSchemaMigrator? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }
    private val mutex = Mutex()

    @Volatile
    private var cache: T? = null

    @Volatile
    private var loaded: Boolean = false

    /** 本地文件是否已存在（用于区分“从未保存”与“保存了空数据”，迁移逻辑依赖它）。 */
    suspend fun exists(): Boolean = withContext(ioDispatcher) { file.exists() }

    suspend fun read(): T = mutex.withLock {
        if (!loaded) {
            // 首次读取放到 IO 线程，避免小概率的严格模式/主线程磁盘读取问题
            withContext(ioDispatcher) {
                cache = readFromDisk()
                loaded = true
            }
        }
        @Suppress("UNCHECKED_CAST")
        cache as T
    }

    suspend fun write(value: T): AppResult<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
                moveReplacing(temp, file)
                cache = value
                loaded = true
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { throwable ->
                    logger.e(TAG, "写入本地数据失败: ${file.name}", throwable)
                    AppResult.Failure(AppError.fromThrowable(throwable, ErrorCode.STORAGE))
                },
            )
        }
    }

    /** 直接删除本地文件（用于“清空数据”），失败仅记录日志。 */
    suspend fun clear(): AppResult<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            runCatching {
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("删除文件失败: ${file.name}")
                }
                cache = null
                loaded = false
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = { throwable ->
                    logger.e(TAG, "清空本地数据失败: ${file.name}", throwable)
                    AppResult.Failure(AppError.fromThrowable(throwable, ErrorCode.STORAGE))
                },
            )
        }
    }

    private fun readFromDisk(): T {
        if (!file.exists() || file.length() == 0L) return defaultValue()

        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (throwable: Throwable) {
            logger.e(TAG, "读取本地数据失败，已回退默认值: ${file.name}", throwable)
            return defaultValue()
        }

        // 取不到版本号说明不是预期的 JSON 对象，交给反序列化去判定是否损坏。
        val version = runCatching { schemaVersionOf(raw) }.getOrNull()
        val currentVersion = migrator?.currentVersion
        val fromHigherVersion = version != null && currentVersion != null && version > currentVersion
        if (fromHigherVersion) {
            logger.w(TAG, "本地数据版本($version)高于当前支持($currentVersion)，按当前结构尽力读取: ${file.name}")
        }

        val source = if (migrator != null && version != null && currentVersion != null && version < currentVersion) {
            try {
                migrator.migrate(raw, version)
            } catch (throwable: Throwable) {
                logger.e(TAG, "本地数据迁移失败(schemaVersion=$version)，已保留原文件: ${file.name}", throwable)
                return defaultValue()
            }
        } else {
            raw
        }

        return try {
            json.decodeFromString(serializer, source)
        } catch (throwable: Throwable) {
            logger.e(TAG, "读取本地数据失败，已备份并回退默认值: ${file.name}", throwable)
            if (fromHigherVersion) {
                copyFileBackup(INCOMPATIBLE_SUFFIX)
            } else {
                backupCorruptedFile()
            }
            defaultValue()
        }
    }

    /** 只解析根部的 `schemaVersion`，避免为了拿版本号反序列化整个模型。 */
    private fun schemaVersionOf(raw: String): Int? {
        val element = json.parseToJsonElement(raw)
        return (element as? JsonObject)
            ?.get("schemaVersion")
            ?.let { value -> (value as? JsonPrimitive)?.intOrNull }
    }

    private fun backupCorruptedFile() {
        runCatching {
            if (file.exists()) {
                val backup = File(file.parentFile, "${file.name}.corrupt")
                moveReplacing(file, backup)
            }
        }.onFailure { logger.w(TAG, "备份损坏文件失败: ${file.name}", it) }
    }

    /** 保留原文件的备份（用于“数据来自更高版本”等不能覆盖原文件的场景）。 */
    private fun copyFileBackup(suffix: String) {
        runCatching {
            if (!file.exists()) return@runCatching
            val backup = File(file.parentFile, "${file.name}.$suffix")
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { logger.w(TAG, "备份本地数据失败: ${file.name}", it) }
    }

    private fun moveReplacing(from: File, to: File) {
        runCatching {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            // 部分文件系统不支持 ATOMIC_MOVE，降级为普通替换
            if (!from.renameTo(to)) {
                to.delete()
                if (!from.renameTo(to)) throw IllegalStateException("重命名临时文件失败: ${to.name}")
            }
        }
    }

    private companion object {
        const val TAG = "JsonFileStore"

        /** 数据来自更高版本时的原文件备份后缀。 */
        const val INCOMPATIBLE_SUFFIX = "incompatible"
    }
}
