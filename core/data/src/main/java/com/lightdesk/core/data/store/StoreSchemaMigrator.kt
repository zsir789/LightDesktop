package com.lightdesk.core.data.store

/**
 * 本地 JSON 结构迁移器。
 *
 * [JsonFileStore] 在反序列化前读取文件根部的 `schemaVersion`：
 * - 低于 [currentVersion]：先调用 [migrate] 把旧 JSON 改写成新结构，再反序列化；
 * - 高于 [currentVersion]：不做迁移，按当前结构尽力读取；读取失败时保留原文件并回退默认值；
 * - 不是合法 JSON 对象（取不到版本号）：按损坏文件处理（备份 `.corrupt` 后回退默认值）。
 *
 * 迁移失败时不会丢弃原文件，确保用户数据始终可回溯。
 */
interface JsonSchemaMigrator {

    /** 当前代码支持的 `schemaVersion`（与 [SCHEMA_VERSION] 保持一致）。 */
    val currentVersion: Int

    /** 把 [from] 版本的 JSON 文本迁移到 [currentVersion]；无法迁移时抛出异常。 */
    fun migrate(raw: String, from: Int): String
}

/**
 * 当前应用的 store 迁移链。
 *
 * v1 → v2 只新增了带序列化默认值的字段（如 `AppSettings.themePalette`），
 * 旧 JSON 原样交给 kotlinx.serialization 的默认值即可，因此这里保持幂等。
 * 未来出现结构性变更（字段重命名、删除、语义变化）时，在此按版本递进追加改写逻辑，
 * 并补 `JsonFileStoreTest` 的迁移用例。
 */
object StoreSchemaMigrator : JsonSchemaMigrator {

    override val currentVersion: Int = SCHEMA_VERSION

    override fun migrate(raw: String, from: Int): String {
        require(from in 1 until currentVersion) { "不支持的 schemaVersion: $from" }
        return raw
    }
}
