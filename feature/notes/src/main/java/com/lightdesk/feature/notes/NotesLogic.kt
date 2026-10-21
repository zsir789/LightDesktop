package com.lightdesk.feature.notes

import com.lightdesk.core.model.NoteItem
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 备忘页顶部筛选维度。
 *
 * v1.1.0 把备忘录与待办合并为一个页面，用截止时间区分类型：
 * 有 deadline = 待办，无 deadline = 备忘。
 */
enum class NotesFilter(val label: String) {
    ALL("全部"),
    MEMO("备忘"),
    TODO("待办"),
    COMPLETED("已完成"),
}

/**
 * 备忘页纯逻辑：筛选、排序、计数。
 *
 * 全部为无副作用函数，ViewModel 与 JVM 单测复用同一套规则，
 * 避免排序/筛选规则散落在 Compose 代码里无法测试。
 */
object NotesLogic {

    /** 指定筛选条件下的条目数，用于筛选 chip 上的计数。 */
    fun count(items: List<NoteItem>, filter: NotesFilter): Int = when (filter) {
        NotesFilter.ALL -> items.size
        NotesFilter.MEMO -> items.count { !it.isTodo }
        NotesFilter.TODO -> items.count { it.isTodo }
        NotesFilter.COMPLETED -> items.count { it.done }
    }

    /** 四个筛选维度的计数快照。 */
    fun counts(items: List<NoteItem>): Map<NotesFilter, Int> =
        NotesFilter.entries.associateWith { count(items, it) }

    fun filter(items: List<NoteItem>, filter: NotesFilter): List<NoteItem> = when (filter) {
        NotesFilter.ALL -> items
        NotesFilter.MEMO -> items.filter { !it.isTodo }
        NotesFilter.TODO -> items.filter { it.isTodo }
        NotesFilter.COMPLETED -> items.filter { it.done }
    }

    /**
     * 排序规则（与产品约定一致）：
     * 1. 已完成沉底（避免干扰当前要做的事）；
     * 2. 置顶优先；
     * 3. 待办排在备忘之前（“无 deadline 其后”）；
     * 4. 待办按截止时间升序；
     * 5. 同组内按创建时间倒序（新的在前）。
     */
    fun sort(items: List<NoteItem>): List<NoteItem> = items.sortedWith(ORDER)

    /** 先按筛选条件过滤，再排序，得到最终展示顺序。 */
    fun visible(items: List<NoteItem>, filter: NotesFilter): List<NoteItem> =
        sort(filter(items, filter))

    private val ORDER: Comparator<NoteItem> = compareBy<NoteItem> { it.done }
        .thenByDescending { it.pinned }
        .thenBy { if (it.isTodo) 0 else 1 }
        .thenBy { it.deadline ?: Long.MAX_VALUE }
        .thenByDescending { it.createdAt }
        .thenBy { it.id }
}

/**
 * 备忘页展示相关的纯格式化逻辑：倒计时、日期/时间文本、标题校验、时间戳换算。
 *
 * 时区统一取系统默认时区；时间戳统一为毫秒，与 [NoteItem.deadline] 保持一致。
 */
object NotesFormat {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM月dd日 HH:mm", Locale.getDefault())
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.getDefault())
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    private val defaultDeadlineTime: LocalTime = LocalTime.of(23, 59)

    private const val MINUTES_PER_DAY = 24 * 60

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** 列表里的截止时间标签，例如 “05月01日 18:30”。 */
    fun deadlineDateTimeText(deadline: Long): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(deadline).atZone(zone()))

    /** 编辑弹窗里的截止日期文本；未设置时给出占位。 */
    fun editorDateText(deadline: Long?): String = deadline?.let {
        dateFormatter.format(Instant.ofEpochMilli(it).atZone(zone()).toLocalDate())
    } ?: "未设置"

    /** 编辑弹窗里的截止时间文本（24 小时制）；未设置时给出占位。 */
    fun editorTimeText(deadline: Long?): String = deadline?.let {
        timeFormatter.format(Instant.ofEpochMilli(it).atZone(zone()).toLocalTime())
    } ?: "未设置"

    /**
     * 实时倒计时文案：
     * 「剩余 2小时13分」/「剩余 3天」/「剩余 不到1分钟」/「已逾期 1小时」。
     */
    fun countdownText(deadline: Long, now: Long): String {
        val diff = deadline - now
        return if (diff < 0) {
            "已逾期 ${coarseDuration(-diff)}"
        } else {
            "剩余 ${coarseDuration(diff)}"
        }
    }

    /** 毫秒差转粗略中文时长，最多保留“天 + 小时”两级，避免文案过长。 */
    fun coarseDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val days = totalMinutes / MINUTES_PER_DAY
        val hours = (totalMinutes % MINUTES_PER_DAY) / 60
        val minutes = totalMinutes % 60
        return when {
            totalMinutes <= 0L -> "不到1分钟"
            days > 0L -> if (hours > 0L) "${days}天${hours}小时" else "${days}天"
            hours > 0L -> if (minutes > 0L) "${hours}小时${minutes}分" else "${hours}小时"
            else -> "${minutes}分钟"
        }
    }

    /** 标题校验：空白标题返回错误文案，合法返回 null。 */
    fun validateTitle(title: String): String? = if (title.isBlank()) "请输入标题" else null

    /** 新建待办时的默认截止时间：今天 23:59；若今天已过则顺延到明天 23:59。 */
    fun defaultDeadline(now: Long): Long {
        val localZone = zone()
        val today = Instant.ofEpochMilli(now).atZone(localZone).toLocalDate()
        val todayEnd = today.atTime(defaultDeadlineTime).atZone(localZone).toInstant().toEpochMilli()
        return if (todayEnd > now) {
            todayEnd
        } else {
            today.plusDays(1).atTime(defaultDeadlineTime).atZone(localZone).toInstant().toEpochMilli()
        }
    }

    /** 时间戳对应的本地日期，转成 Material3 DatePicker 使用的 UTC 零点毫秒值。 */
    fun toUtcDateMillis(timestamp: Long): Long =
        Instant.ofEpochMilli(timestamp).atZone(zone()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 合并 DatePicker 的 UTC 日期与本地时分，得到本地时区的毫秒时间戳。 */
    fun combineDateAndTime(utcDateMillis: Long, hour: Int, minute: Int): Long {
        val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atTime(hour, minute).atZone(zone()).toInstant().toEpochMilli()
    }

    /** 保留原时间，仅替换日期（DatePicker 选择结果）。 */
    fun withDate(deadline: Long, utcDateMillis: Long): Long {
        val time = Instant.ofEpochMilli(deadline).atZone(zone()).toLocalTime()
        return combineDateAndTime(utcDateMillis, time.hour, time.minute)
    }

    /** 保留原日期，仅替换时间（TimePicker 选择结果）。 */
    fun withTime(deadline: Long, hour: Int, minute: Int): Long {
        val date = Instant.ofEpochMilli(deadline).atZone(zone()).toLocalDate()
        return date.atTime(hour, minute).atZone(zone()).toInstant().toEpochMilli()
    }

    fun hourOf(timestamp: Long): Int = Instant.ofEpochMilli(timestamp).atZone(zone()).hour

    fun minuteOf(timestamp: Long): Int = Instant.ofEpochMilli(timestamp).atZone(zone()).minute
}
