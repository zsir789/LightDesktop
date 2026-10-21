package com.lightdesk.feature.home

import com.lightdesk.core.common.TimeFormat
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.core.model.NoteItem
import com.lightdesk.core.model.TimetableCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 今日课程相对“当前时刻”的状态，用于首页高亮与标签展示。 */
enum class CourseStatus {
    /** 正在上课。 */
    ONGOING,

    /** 尚未开始（下一节或更晚）。 */
    UPCOMING,

    /** 已经下课。 */
    FINISHED,
}

/** 首页“今日课程”条目：课程本身 + 当前状态。 */
data class TodayCourse(
    val course: Course,
    val status: CourseStatus,
)

/** 首页“今日待办”条目：事项 + 实时倒计时展示信息。 */
data class TodayTodo(
    val item: NoteItem,
    val countdownText: String,
    val overdue: Boolean,
    val urgent: Boolean,
)

/**
 * 首页展示逻辑集合（纯函数，不依赖 Android 框架，便于单元测试）。
 *
 * 所有“今天”的判断都以传入的 [ZoneId] 为准，时间戳统一为毫秒。
 */
object HomePresentation {

    /** 距离截止不足该时长视为“紧急”，倒计时用警示色展示。 */
    const val WARNING_MILLIS: Long = 60L * 60L * 1000L

    private const val MINUTES_PER_HOUR = 60

    /** 按小时返回问候语。 */
    fun greeting(hourOfDay: Int): String = when (hourOfDay) {
        in 5..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }

    fun hourOfDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(timestamp).atZone(zone).hour

    fun minutesOfDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(timestamp).atZone(zone)
        return time.hour * MINUTES_PER_HOUR + time.minute
    }

    fun localDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        TimetableCalendar.toLocalDate(timestamp, zone)

    /** 例：「10月19日 周一」。 */
    fun dateLabel(date: LocalDate): String =
        "${date.monthValue}月${date.dayOfMonth}日 ${TimeFormat.weekdayLabel(date.dayOfWeek.value)}"

    /**
     * 今日待办：未完成、有截止时间、且截止日期不晚于今天（已逾期也算今天要处理）。
     * 结果按截止时间升序，同一时间按创建时间升序，保证展示稳定。
     */
    fun todayTodos(
        items: List<NoteItem>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NoteItem> {
        val today = localDate(now, zone)
        return items
            .asSequence()
            .filter { item -> !item.done }
            .filter { item ->
                val deadline = item.deadline ?: return@filter false
                !localDate(deadline, zone).isAfter(today)
            }
            .sortedWith(compareBy({ it.deadline ?: Long.MAX_VALUE }, { it.createdAt }))
            .toList()
    }

    /** 全部未完成待办数量（不限今天）。 */
    fun pendingTodoCount(items: List<NoteItem>): Int =
        items.count { it.isTodo && !it.done }

    fun todayTodosUi(
        items: List<NoteItem>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TodayTodo> = todayTodos(items, now, zone).map { item -> toTodoUi(item, now) }

    /** 单条待办的倒计时展示信息。 */
    fun toTodoUi(item: NoteItem, now: Long): TodayTodo {
        val deadline = item.deadline
        if (deadline == null) {
            return TodayTodo(item = item, countdownText = "", overdue = false, urgent = false)
        }
        val remaining = deadline - now
        return TodayTodo(
            item = item,
            countdownText = TimeFormat.countdownText(remaining),
            overdue = remaining < 0,
            urgent = remaining >= 0 && remaining < WARNING_MILLIS,
        )
    }

    /** 课程在某个“当天分钟数”时刻的状态。 */
    fun courseStatus(course: Course, nowMinutes: Int): CourseStatus = when {
        nowMinutes < course.startMinutes -> CourseStatus.UPCOMING
        nowMinutes < course.endMinutes -> CourseStatus.ONGOING
        else -> CourseStatus.FINISHED
    }

    /** 今日课程（已按开始时间排序，并附带当前状态）。 */
    fun todayCourses(
        table: CourseTable?,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TodayCourse> {
        val nowMinutes = minutesOfDay(now, zone)
        return TimetableCalendar.coursesOn(table, localDate(now, zone))
            .map { course -> TodayCourse(course = course, status = courseStatus(course, nowMinutes)) }
    }

    /** 是否已经导入过课表（有课程数据）。 */
    fun hasTimetable(table: CourseTable?): Boolean =
        table != null && table.courses.isNotEmpty()

    /** 需要高亮的课程：正在上的课优先，否则取下一节；当天全上完则为 null。 */
    fun highlighted(courses: List<TodayCourse>): TodayCourse? =
        courses.firstOrNull { it.status != CourseStatus.FINISHED }

    fun highlightedCourseId(courses: List<TodayCourse>): String? =
        highlighted(courses)?.course?.id

    // ---- v1.2.0 视觉层（色块/标签）相关的可测纯逻辑 ----

    /**
     * 待办色块种子：由事项 id 稳定推导，保证同一条待办每次进入首页颜色一致。
     *
     * 直接复用 [String.hashCode]（JVM 规范固定），配合 designsystem 的
     * `CourseColors.indexForSeed` 取 pastel 底色，无需额外存储。
     */
    fun todoSeed(itemId: String): Int = itemId.hashCode()

    /** 课程状态标签文案（进行中/即将开始/已结束）。 */
    fun statusLabel(status: CourseStatus): String = when (status) {
        CourseStatus.ONGOING -> "进行中"
        CourseStatus.UPCOMING -> "即将开始"
        CourseStatus.FINISHED -> "已结束"
    }

    /** 待办紧急标签：已逾期优先，其次 1 小时内截止；普通待办不显示标签。 */
    fun todoTagLabel(overdue: Boolean, urgent: Boolean): String? = when {
        overdue -> "已逾期"
        urgent -> "1 小时内"
        else -> null
    }

    /** 色块内容不透明度：已结束的课程整体淡出，进行中/即将开始的保持清晰。 */
    fun courseContentAlpha(status: CourseStatus): Float =
        if (status == CourseStatus.FINISHED) 0.55f else 1f
}
