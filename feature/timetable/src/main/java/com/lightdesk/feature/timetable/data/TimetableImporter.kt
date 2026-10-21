package com.lightdesk.feature.timetable.data

import com.lightdesk.core.common.AppResult
import com.lightdesk.core.model.Course

/**
 * 课表导入器扩展点。
 *
 * 目前只实现了 WakeUp 导出的 `.ics`（[IcsParser]）；以后要支持学校教务系统导出的
 * CSV/JSON 时，新增一个实现类即可，ViewModel 与 UI 不需要改动。
 *
 * 约定：任何解析失败都必须转成 [AppResult.Failure]（带可直接展示的中文提示），
 * 不允许把异常抛给调用方。
 */
interface TimetableImporter {

    /** 来源标识，写入 `CourseTable.sourceType`，例如 "ICS"。 */
    val sourceType: String

    /** 解析文件文本；失败返回 [AppResult.Failure]，成功返回与格式无关的 [ImportedTable]。 */
    fun importContent(content: String): AppResult<ImportedTable>
}

/**
 * 与具体文件格式无关的导入结果，供 ViewModel 组装 `CourseTable`。
 *
 * @property semesterStart 第 1 周周一的 ISO 日期（yyyy-MM-dd），无法推导时为 null；
 * @property calendarName  课表原始名称（如 ICS 的 `X-WR-CALNAME`），用于课表展示名；
 * @property warnings      可恢复的解析提示，UI 只展示数量，不阻塞导入。
 */
data class ImportedTable(
    val courses: List<Course>,
    val semesterStart: String?,
    val calendarName: String?,
    val eventCount: Int,
    val warnings: List<String>,
    val sourceType: String,
)

/** 把 ICS 解析成功结果转换成通用导入结果。 */
fun IcsParseResult.Success.toImportedTable(sourceType: String = IcsParser.SOURCE_TYPE): ImportedTable =
    ImportedTable(
        courses = courses,
        semesterStart = semesterStart,
        calendarName = calendarName,
        eventCount = eventCount,
        warnings = warnings,
        sourceType = sourceType,
    )
