package com.lightdesk.feature.timetable.data

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.model.Course
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * WakeUp / 标准 iCalendar（.ics）课表解析器。
 *
 * 解析流程：先展开行折叠（CRLF + 首空格续行）→ 按 BEGIN/END 收集 VEVENT →
 * 逐个事件解析时间、重复规则与排除日期 → 展开为具体上课日期 → 合并同一门课的多个时段。
 *
 * 支持的 WakeUp 常见写法：
 * - `DTSTART;TZID=Asia/Shanghai:20240902T080000`、`DTSTART:20240902T080000Z`、浮动时间 `DTSTART:20240902T080000`；
 * - `VALUE=DATE` 全天事件、`DTEND` 缺失时按 [DEFAULT_EVENT_DURATION_MINUTES] 分钟；
 * - `RRULE:FREQ=WEEKLY`（INTERVAL / BYDAY / COUNT / UNTIL / WKST）与 `EXDATE`（多值、TZID/UTC/日期）；
 * - `SUMMARY` 为课程名，`DESCRIPTION` / `LOCATION` 中宽松提取教师与地点。
 *
 * 同一门课不同时段（不同 DTSTART / RRULE）会各自成为一条 [Course]，颜色种子由课程信息稳定生成；
 * 全表最早的 DTSTART 所在周的周一推导为学期第 1 周周一（`CourseTable.semesterStart`）。
 *
 * 实现 [TimetableImporter]，异常统一转成 [AppResult.Failure]，不向上抛异常。
 */
class IcsParser(
    private val displayZone: ZoneId = ZoneId.systemDefault(),
) : TimetableImporter {

    override val sourceType: String = SOURCE_TYPE

    // ------------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------------

    /**
     * 解析 ICS 文本。
     *
     * @return [IcsParseResult.Success] 含课程列表、事件数、可恢复提示、学期第 1 周周一与日历名称；
     *         [IcsParseResult.Failure] 含可直接展示的中文错误提示。
     */
    fun parse(content: String): IcsParseResult {
        val lines = unfoldLines(content)
        if (lines.isEmpty()) {
            return IcsParseResult.Failure(EMPTY_FILE_MESSAGE)
        }
        val document = collectDocument(lines)
        if (document.events.isEmpty()) {
            return IcsParseResult.Failure("这个文件里没有任何课程事件，请确认导出的是含课程的日历（空日历无法导入）")
        }

        val warnings = mutableListOf<String>()
        val events = document.events.mapNotNull { parseEvent(it, warnings) }
        if (events.isEmpty()) {
            return IcsParseResult.Failure("没有可导入的课程：缺少有效的开始时间（DTSTART）")
        }

        val occurrences = mutableListOf<Occurrence>()
        events.forEach { event ->
            val eventOccurrences = expandOccurrences(event, warnings)
            if (eventOccurrences.isEmpty()) {
                warnings += "「${event.title}」没有可展开的上课日期，已跳过"
            }
            occurrences += eventOccurrences
        }
        if (occurrences.isEmpty()) {
            return IcsParseResult.Failure("没有可导入的课程：所有课程事件都没有有效的上课时间")
        }

        // 学期第 1 周周一 = 全表最小 DTSTART 所在周的周一
        val semesterStartDate = events
            .map { displayOf(it.start).toLocalDate() }
            .min()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val courses = mergeCourses(occurrences, semesterStartDate)
        if (courses.isEmpty()) {
            return IcsParseResult.Failure("没有可导入的课程：展开后没有任何上课时间")
        }

        return IcsParseResult.Success(
            courses = courses,
            eventCount = document.events.size,
            warnings = warnings.distinct(),
            semesterStart = semesterStartDate.toString(),
            calendarName = document.calendarName,
        )
    }

    override fun importContent(content: String): AppResult<ImportedTable> {
        val result = try {
            parse(content)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCode.PARSE,
                    message = "课表解析失败：${throwable.message?.takeIf { it.isNotBlank() } ?: "未知错误"}",
                    cause = throwable,
                ),
            )
        }
        return when (result) {
            is IcsParseResult.Success -> AppResult.Success(result.toImportedTable(sourceType))
            is IcsParseResult.Failure -> AppResult.Failure(AppError(ErrorCode.PARSE, result.message))
        }
    }

    // ------------------------------------------------------------------
    // 行展开与组件收集
    // ------------------------------------------------------------------

    /** 去掉 BOM、统一换行、展开折行（续行以空格或 Tab 开头）。 */
    private fun unfoldLines(content: String): List<String> {
        val normalized = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (normalized.isBlank()) return emptyList()
        val unfolded = mutableListOf<StringBuilder>()
        normalized.split('\n').forEach { rawLine ->
            val isContinuation = rawLine.isNotEmpty() && (rawLine.first() == ' ' || rawLine.first() == '\t')
            if (isContinuation && unfolded.isNotEmpty()) {
                unfolded.last().append(rawLine.drop(1))
            } else {
                unfolded += StringBuilder(rawLine)
            }
        }
        return unfolded.map { it.toString() }.filter { it.isNotBlank() }
    }

    /** 收集所有 VEVENT 属性（忽略 VALARM 等嵌套组件）与日历名称。 */
    private fun collectDocument(lines: List<String>): ParsedDocument {
        val events = mutableListOf<List<IcsProperty>>()
        var currentEvent: MutableList<IcsProperty>? = null
        var depth = 0
        var eventDepth = -1
        var calendarName: String? = null

        lines.forEach { line ->
            val property = parsePropertyLine(line)
            if (property != null && property.name == "BEGIN") {
                depth++
                val component = property.value.trim().uppercase()
                if (component == "VEVENT") {
                    currentEvent = mutableListOf()
                    eventDepth = depth
                }
                return@forEach
            }
            if (property != null && property.name == "END") {
                val component = property.value.trim().uppercase()
                if (component == "VEVENT" && currentEvent != null) {
                    events += currentEvent.orEmpty()
                    currentEvent = null
                    eventDepth = -1
                }
                depth = (depth - 1).coerceAtLeast(0)
                return@forEach
            }
            if (property == null) return@forEach
            if (currentEvent != null && eventDepth >= 0 && depth == eventDepth) {
                currentEvent?.add(property)
            } else if (depth == 1 && eventDepth < 0 && property.name == "X-WR-CALNAME") {
                calendarName = unescapeText(property.value).trim().takeIf { it.isNotEmpty() }
            }
        }
        return ParsedDocument(events = events, calendarName = calendarName)
    }

    /** 解析一行属性：`NAME;PARAM=VALUE:VALUE`，值中冒号不转义，参数值可带引号。 */
    private fun parsePropertyLine(line: String): IcsProperty? {
        val separator = findValueSeparator(line)
        if (separator <= 0) return null
        val head = line.substring(0, separator)
        val value = line.substring(separator + 1)
        val segments = splitOutsideQuotes(head, ';')
        val name = segments.firstOrNull()?.trim()?.uppercase().orEmpty()
        if (name.isEmpty()) return null
        val params = LinkedHashMap<String, List<String>>()
        segments.drop(1).forEach { segment ->
            val equalsIndex = segment.indexOf('=')
            if (equalsIndex <= 0) return@forEach
            val paramName = segment.substring(0, equalsIndex).trim().uppercase()
            val paramValue = segment.substring(equalsIndex + 1)
            val values = splitOutsideQuotes(paramValue, ',').map { it.trim().trim('"') }
            params[paramName] = params[paramName].orEmpty() + values
        }
        return IcsProperty(name = name, params = params, value = value)
    }

    /** 找到属性名/参数与属性值之间的冒号（跳过双引号内的冒号）。 */
    private fun findValueSeparator(line: String): Int {
        var inQuotes = false
        line.forEachIndexed { index, char ->
            when (char) {
                '"' -> inQuotes = !inQuotes
                ':' -> if (!inQuotes) return index
            }
        }
        return -1
    }

    private fun splitOutsideQuotes(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        text.forEach { char ->
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }
                char == delimiter && !inQuotes -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    // ------------------------------------------------------------------
    // 事件解析
    // ------------------------------------------------------------------

    private fun parseEvent(properties: List<IcsProperty>, warnings: MutableList<String>): CourseEvent? {
        val rawSummary = unescapeText(properties.firstOrNull { it.name == "SUMMARY" }?.value.orEmpty()).trim()
        val description = unescapeText(properties.firstOrNull { it.name == "DESCRIPTION" }?.value.orEmpty()).trim()
        val summaryForMessage = rawSummary.ifBlank { "未命名课程" }

        val startProperty = properties.firstOrNull { it.name == "DTSTART" }
        if (startProperty == null) {
            warnings += "「$summaryForMessage」缺少开始时间（DTSTART），已跳过"
            return null
        }
        val start = parseDateTimeValue(startProperty)
        if (start == null) {
            warnings += "「$summaryForMessage」的开始时间无法识别，已跳过"
            return null
        }
        val end = parseDateTimeValue(properties.firstOrNull { it.name == "DTEND" })
        val durationMinutes = resolveDurationMinutes(startProperty, start, end)

        // 教师 / 地点：WakeUp 常写在 DESCRIPTION 的若干行里，如 "教师: 张三\n地点: 教101"
        val teacherMatch = TEACHER_PATTERN.find(description)
        val locationMatch = LOCATION_PATTERN.find(description)
        val descriptionTeacher = teacherMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val descriptionLocation = locationMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

        var note = description
        listOfNotNull(teacherMatch, locationMatch)
            .sortedByDescending { it.range.first }
            .forEach { match ->
                if (match.range.last < note.length) {
                    note = note.removeRange(match.range)
                }
            }
        note = note.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        // 课程名与地点：LOCATION 属性优先，其次 DESCRIPTION，最后 "课程@教室" 写法的 SUMMARY
        var title = rawSummary
        var location = unescapeText(properties.firstOrNull { it.name == "LOCATION" }?.value.orEmpty()).trim()
        if (location.isBlank()) location = descriptionLocation
        if (location.isBlank()) {
            val atIndex = rawSummary.indexOf('@')
            if (atIndex > 0 && atIndex < rawSummary.lastIndex) {
                title = rawSummary.substring(0, atIndex).trim()
                location = rawSummary.substring(atIndex + 1).trim()
            }
        }
        if (title.isBlank()) title = summaryForMessage

        return CourseEvent(
            title = title,
            teacher = descriptionTeacher,
            location = location,
            note = note,
            start = start,
            durationMinutes = durationMinutes,
            rule = parseRecurrenceRule(properties.firstOrNull { it.name == "RRULE" }?.value, start.zone),
            exDates = parseExcludedDates(properties, warnings),
        )
    }

    /** 解析 DTSTART / DTEND：TZID、UTC（Z）、浮动时间、VALUE=DATE。 */
    private fun parseDateTimeValue(property: IcsProperty?): WallTime? {
        if (property == null) return null
        val raw = property.value.trim()
        if (raw.isEmpty()) return null
        val valueType = property.params["VALUE"]?.firstOrNull()?.uppercase()
        val isDateOnly = valueType == "DATE" || (!raw.contains('T') && raw.length == 8)
        val zone = if (raw.endsWith("Z", ignoreCase = true)) {
            ZoneOffset.UTC
        } else {
            resolveZone(property.params["TZID"]?.firstOrNull()) ?: displayZone
        }
        val body = raw.removeSuffix("Z").removeSuffix("z")
        if (isDateOnly) {
            val date = runCatching { LocalDate.parse(body, BASIC_DATE_FORMATTER) }.getOrNull() ?: return null
            return WallTime(date.atStartOfDay(), zone)
        }
        val wall = parseBasicDateTime(body) ?: return null
        return WallTime(wall, zone)
    }

    private fun resolveZone(raw: String?): ZoneId? {
        val tzid = raw?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        val candidates = listOf(tzid, tzid.removePrefix("/"))
        candidates.forEach { candidate ->
            val zone = runCatching { ZoneId.of(candidate) }.getOrNull()
            if (zone != null) return zone
        }
        val aliasZone = runCatching { ZoneId.of(tzid, ZoneId.SHORT_IDS) }.getOrNull()
        if (aliasZone != null) return aliasZone
        // 兼容 GMT+8 / UTC+08:00 这类偏移写法
        val offsetText = tzid.removePrefix("UTC").removePrefix("GMT").trim()
        if (offsetText.startsWith("+") || offsetText.startsWith("-")) {
            return runCatching { ZoneOffset.of(offsetText) }.getOrNull()
        }
        return null
    }

    private fun parseBasicDateTime(raw: String): LocalDateTime? {
        for (formatter in DATE_TIME_FORMATTERS) {
            val parsed = runCatching { LocalDateTime.parse(raw, formatter) }.getOrNull()
            if (parsed != null) return parsed
        }
        val date = runCatching { LocalDate.parse(raw, BASIC_DATE_FORMATTER) }.getOrNull()
        return date?.atStartOfDay()
    }

    private fun resolveDurationMinutes(startProperty: IcsProperty, start: WallTime, end: WallTime?): Int {
        val valueType = startProperty.params["VALUE"]?.firstOrNull()?.uppercase()
        if (valueType == "DATE") return MINUTES_PER_DAY
        if (end == null) return DEFAULT_EVENT_DURATION_MINUTES
        val minutes = ChronoUnit.MINUTES.between(instantOf(start), instantOf(end)).toInt()
        if (minutes > 0) return minutes
        // 结束时间早于开始时间：视为跨天，按当天剩余时间处理
        val startLocal = displayOf(start)
        val minutesFromMidnight = startLocal.hour * 60 + startLocal.minute
        return (MINUTES_PER_DAY - minutesFromMidnight).coerceAtLeast(1)
    }

    // ------------------------------------------------------------------
    // 重复规则与排除日期
    // ------------------------------------------------------------------

    private fun parseRecurrenceRule(raw: String?, fallbackZone: ZoneId): RecurrenceRule? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val parts = value.split(';').mapNotNull { segment ->
            val index = segment.indexOf('=')
            if (index <= 0) {
                null
            } else {
                segment.substring(0, index).trim().uppercase() to segment.substring(index + 1).trim()
            }
        }.toMap()
        val freq = when (parts["FREQ"]?.uppercase()) {
            "WEEKLY" -> Freq.WEEKLY
            "DAILY" -> Freq.DAILY
            "MONTHLY" -> Freq.MONTHLY
            "YEARLY" -> Freq.YEARLY
            null -> return null
            else -> Freq.OTHER
        }
        val intervalWeeks = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val byDays = parts["BYDAY"].orEmpty()
            .split(',')
            .mapNotNull { token -> parseWeekday(token) }
            .toSet()
        val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
        val untilInstant = parts["UNTIL"]?.let { parseUntil(it, fallbackZone) }
        return RecurrenceRule(
            freq = freq,
            intervalWeeks = intervalWeeks,
            byDays = byDays,
            count = count,
            untilInstant = untilInstant,
        )
    }

    /** BYDAY 里的 "MO" / "2MO" / "+1MO" 都归到星期几（1=周一）。 */
    private fun parseWeekday(token: String): Int? {
        val code = token.trim().uppercase().takeLast(2)
        return WEEKDAY_CODES[code]
    }

    private fun parseUntil(raw: String, fallbackZone: ZoneId): Instant? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val endsWithZ = value.endsWith("Z", ignoreCase = true)
        val body = if (endsWithZ) value.dropLast(1) else value
        val zone = if (endsWithZ) ZoneOffset.UTC else fallbackZone
        if (body.length == 8 && body.all { it.isDigit() }) {
            val date = runCatching { LocalDate.parse(body, BASIC_DATE_FORMATTER) }.getOrNull() ?: return null
            // UNTIL=20240930 表示含当天，按当天 23:59:59.999 处理
            return date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
        }
        val wall = parseBasicDateTime(body) ?: return null
        return wall.atZone(zone).toInstant()
    }

    private fun parseExcludedDates(properties: List<IcsProperty>, warnings: MutableList<String>): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        properties.filter { it.name == "EXDATE" }.forEach { property ->
            val rawValues = property.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            rawValues.forEach { rawValue ->
                val wallTime = parseDateTimeValue(property.copy(value = rawValue))
                if (wallTime != null) {
                    result += displayOf(wallTime).toLocalDate()
                } else {
                    warnings += "有一条排除日期（EXDATE）无法识别，已忽略"
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // 展开
    // ------------------------------------------------------------------

    private fun expandOccurrences(event: CourseEvent, warnings: MutableList<String>): List<Occurrence> {
        val rule = event.rule
        if (rule == null) {
            return listOf(Occurrence(event, displayOf(event.start).toLocalDate()))
        }
        if (rule.freq != Freq.WEEKLY) {
            warnings += "「${event.title}」使用了暂不支持的重复频率（${rule.freq}），仅导入第一次课"
            return listOf(Occurrence(event, displayOf(event.start).toLocalDate()))
        }
        return expandWeekly(event, rule, warnings)
    }

    private fun expandWeekly(event: CourseEvent, rule: RecurrenceRule, warnings: MutableList<String>): List<Occurrence> {
        val startWall = event.start.wall
        val baseMonday = startWall.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekdays = rule.byDays.ifEmpty { setOf(startWall.dayOfWeek.value) }.sorted()
        val intervalWeeks = rule.intervalWeeks.coerceAtLeast(1)
        val maxCount = rule.count?.coerceAtMost(MAX_OCCURRENCES_PER_EVENT) ?: MAX_OCCURRENCES_PER_EVENT
        val occurrences = mutableListOf<Occurrence>()
        // RRULE 的 COUNT 统计的是“重复实例数”，被 EXDATE 排除的实例同样计入次数。
        var produced = 0
        var week = 0
        while (week < MAX_WEEKS && produced < maxCount) {
            val weekMonday = baseMonday.plusWeeks((week.toLong() * intervalWeeks))
            var stop = false
            for (weekday in weekdays) {
                if (produced >= maxCount) break
                val wallTime = weekMonday.plusDays((weekday - 1).toLong()).atTime(startWall.toLocalTime())
                if (wallTime.isBefore(startWall)) continue
                val eventInstant = instantOf(event.start.zone, wallTime)
                if (rule.untilInstant != null && eventInstant.isAfter(rule.untilInstant)) {
                    stop = true
                    break
                }
                produced++
                val localDate = displayOf(event.start.zone, wallTime).toLocalDate()
                if (event.exDates.contains(localDate)) continue
                occurrences += Occurrence(event, localDate)
            }
            if (stop) break
            week++
        }
        if (rule.count != null && produced < rule.count) {
            warnings += "「${event.title}」的重复次数超出可展开范围，仅展开前 $produced 次"
        }
        return occurrences
    }

    // ------------------------------------------------------------------
    // 合并为课程
    // ------------------------------------------------------------------

    private fun mergeCourses(occurrences: List<Occurrence>, semesterStart: LocalDate): List<Course> {
        val grouped = LinkedHashMap<CourseKey, MutableList<LocalDate>>()
        val notes = LinkedHashMap<CourseKey, String>()
        occurrences.forEach { occurrence ->
            val event = occurrence.event
            val startMinutes = displayMinuteOfDay(event.start)
            val key = CourseKey(
                title = event.title,
                teacher = event.teacher,
                location = event.location,
                weekday = occurrence.date.dayOfWeek.value,
                startMinutes = startMinutes,
                endMinutes = (startMinutes + event.durationMinutes).coerceAtMost(MINUTES_PER_DAY),
            )
            grouped.getOrPut(key) { mutableListOf() }.add(occurrence.date)
            if (event.note.isNotBlank()) notes.putIfAbsent(key, event.note)
        }

        val idCounters = mutableMapOf<Int, Int>()
        return grouped.map { (key, dates) ->
            val weeks = dates
                .map { date -> (ChronoUnit.DAYS.between(semesterStart, date) / 7L + 1L).toInt() }
                .filter { it in 1..MAX_WEEK_NUMBER }
                .distinct()
                .sorted()
            val baseSeed = colorSeedFor(key)
            val used = idCounters.getOrDefault(baseSeed, 0)
            idCounters[baseSeed] = used + 1
            val id = if (used == 0) "ics-$baseSeed" else "ics-$baseSeed-$used"
            Course(
                id = id,
                title = key.title,
                teacher = key.teacher,
                location = key.location,
                weekday = key.weekday,
                startMinutes = key.startMinutes,
                endMinutes = key.endMinutes,
                weeks = weeks,
                note = notes[key].orEmpty(),
                colorSeed = baseSeed,
            )
        }
    }

    private fun colorSeedFor(key: CourseKey): Int =
        (key.title + KEY_SEPARATOR + key.teacher + KEY_SEPARATOR + key.location)
            .hashCode()
            .and(Int.MAX_VALUE)

    // ------------------------------------------------------------------
    // 时间工具
    // ------------------------------------------------------------------

    private fun instantOf(start: WallTime): Instant = instantOf(start.zone, start.wall)

    private fun instantOf(zone: ZoneId, wall: LocalDateTime): Instant = wall.atZone(zone).toInstant()

    private fun displayOf(start: WallTime): LocalDateTime = displayOf(start.zone, start.wall)

    private fun displayOf(zone: ZoneId, wall: LocalDateTime): LocalDateTime =
        wall.atZone(zone).withZoneSameInstant(displayZone).toLocalDateTime()

    private fun displayMinuteOfDay(start: WallTime): Int {
        val localTime = displayOf(start).toLocalTime()
        return localTime.hour * 60 + localTime.minute
    }

    private fun unescapeText(raw: String): String {
        if (raw.isEmpty()) return raw
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                when (val next = raw[index + 1]) {
                    'n', 'N' -> builder.append('\n')
                    ',' -> builder.append(',')
                    ';' -> builder.append(';')
                    '\\' -> builder.append('\\')
                    else -> {
                        builder.append(char)
                        builder.append(next)
                    }
                }
                index += 2
            } else {
                builder.append(char)
                index++
            }
        }
        return builder.toString()
    }

    // ------------------------------------------------------------------
    // 内部模型
    // ------------------------------------------------------------------

    private data class IcsProperty(
        val name: String,
        val params: Map<String, List<String>>,
        val value: String,
    )

    private data class WallTime(
        val wall: LocalDateTime,
        val zone: ZoneId,
    )

    private data class ParsedDocument(
        val events: List<List<IcsProperty>>,
        val calendarName: String?,
    )

    private data class CourseEvent(
        val title: String,
        val teacher: String,
        val location: String,
        val note: String,
        val start: WallTime,
        val durationMinutes: Int,
        val rule: RecurrenceRule?,
        val exDates: Set<LocalDate>,
    )

    private data class Occurrence(
        val event: CourseEvent,
        val date: LocalDate,
    )

    private data class RecurrenceRule(
        val freq: Freq,
        val intervalWeeks: Int,
        val byDays: Set<Int>,
        val count: Int?,
        val untilInstant: Instant?,
    )

    private data class CourseKey(
        val title: String,
        val teacher: String,
        val location: String,
        val weekday: Int,
        val startMinutes: Int,
        val endMinutes: Int,
    )

    private enum class Freq {
        WEEKLY,
        DAILY,
        MONTHLY,
        YEARLY,
        OTHER,
    }

    companion object {
        /** 写入 `CourseTable.sourceType` 的来源标识。 */
        const val SOURCE_TYPE = "ICS"

        /** DTEND 缺失时默认的课程时长（分钟）。 */
        const val DEFAULT_EVENT_DURATION_MINUTES = 45

        /** 单个事件最多展开的次数。 */
        const val MAX_OCCURRENCES_PER_EVENT = 400

        /** 单个事件最多向后展开的周数。 */
        const val MAX_WEEKS = 60

        /** 一周内的教学周数上限。 */
        const val MAX_WEEK_NUMBER = 60

        /** 一天分钟数。 */
        const val MINUTES_PER_DAY = 24 * 60

        private const val EMPTY_FILE_MESSAGE = "文件内容为空，无法导入课表"
        private const val KEY_SEPARATOR = "\u0001"

        private val TEACHER_PATTERN =
            Regex("(?:授课教师|任课教师|主讲教师|教师|老师)\\s*[:：]\\s*([^\\n\\r]+)")
        private val LOCATION_PATTERN =
            Regex("(?:上课地点|授课地点|地点|教室)\\s*[:：]\\s*([^\\n\\r]+)")

        private val WEEKDAY_CODES = mapOf(
            "MO" to 1,
            "TU" to 2,
            "WE" to 3,
            "TH" to 4,
            "FR" to 5,
            "SA" to 6,
            "SU" to 7,
        )

        private val BASIC_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DATE_TIME_FORMATTERS: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm"),
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS"),
        )
    }
}

/** ICS 解析结果：成功给出课程与元信息，失败给出可直接展示的中文提示。 */
sealed interface IcsParseResult {

    data class Success(
        val courses: List<Course>,
        val eventCount: Int,
        val warnings: List<String>,
        /** 学期第 1 周周一的 ISO 日期（yyyy-MM-dd），无法推导时为 null。 */
        val semesterStart: String? = null,
        /** ICS 的 `X-WR-CALNAME`，没有时为 null。 */
        val calendarName: String? = null,
    ) : IcsParseResult

    data class Failure(val message: String) : IcsParseResult
}
