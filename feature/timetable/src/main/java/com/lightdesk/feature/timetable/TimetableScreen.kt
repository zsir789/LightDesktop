package com.lightdesk.feature.timetable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdesk.core.common.TimeFormat
import com.lightdesk.core.designsystem.component.CenteredHint
import com.lightdesk.core.designsystem.component.ConfirmDialog
import com.lightdesk.core.designsystem.component.EmptyState
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.InfoBanner
import com.lightdesk.core.designsystem.component.LoadingView
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.ScreenHeader
import com.lightdesk.core.designsystem.component.SectionCard
import com.lightdesk.core.designsystem.component.SectionTitle
import com.lightdesk.core.designsystem.component.TagPill
import com.lightdesk.core.designsystem.component.TextAction
import com.lightdesk.core.designsystem.theme.CourseColors
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette
import com.lightdesk.core.model.Course
import com.lightdesk.core.model.CourseTable
import com.lightdesk.feature.timetable.data.WeekText
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private val MonthCellHeight = 84.dp
private val MinCourseCardHeight = 44.dp
private val CompactCourseCardHeight = 34.dp
private const val MinutesPerHour = 60
private const val MaxMonthCourseLines = 2
private const val GridBgAlpha = 0.35f

/**
 * 课表页：周视图 / 月视图切换、课程详情、导入与空态引导。
 *
 * 字体修复：本文件所有文字统一使用 `MaterialTheme.typography` 中的样式，
 * 不再在局部覆盖 `fontFamily` / `fontWeight` / `fontSize`，保证深浅色与大小屏下
 * 中文排版跟随主题，避免系统字体回退不一致。
 */
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
    onDismissInfo: () -> Unit,
    onDismissError: () -> Unit,
    onViewModeChange: (TimetableViewMode) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCourseClick: (Course) -> Unit,
    onDismissCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val table = state.table
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "课表",
            subtitle = if (table == null) {
                "从 WakeUp 导出 .ics 后导入"
            } else {
                "${table.name} · ${table.courseCount} 门课程"
            },
            actions = {
                if (state.importing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = "导入课表")
                }
                if (table != null) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空课表")
                    }
                }
            },
        )
        MessageBanners(
            state = state,
            onDismissInfo = onDismissInfo,
            onDismissError = onDismissError,
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingView(text = "正在读取课表…")
            }
            table == null || table.courses.isEmpty() -> EmptyTimetableContent(
                onImportClick = onImportClick,
                modifier = Modifier.weight(1f),
            )
            else -> CourseScheduleContent(
                state = state,
                table = table,
                onViewModeChange = onViewModeChange,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onCurrentWeek = onCurrentWeek,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCurrentMonth = onCurrentMonth,
                onSelectDate = onSelectDate,
                onCourseClick = onCourseClick,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (state.clearConfirmVisible) {
        ConfirmDialog(
            title = "清空课表",
            message = "将删除本机保存的课表数据，重新导入后可恢复。确定清空吗？",
            confirmText = "清空",
            destructive = true,
            onConfirm = onConfirmClear,
            onDismiss = onDismissClear,
        )
    }

    state.selectedCourse?.let { course ->
        CourseDetailDialog(course = course, onDismiss = onDismissCourse)
    }
}

// ----------------------------------------------------------------------
// 顶部提示条
// ----------------------------------------------------------------------

@Composable
private fun MessageBanners(
    state: TimetableUiState,
    onDismissInfo: () -> Unit,
    onDismissError: () -> Unit,
) {
    val modifier = Modifier.padding(horizontal = PageHorizontalPadding, vertical = 2.dp)
    state.errorMessage?.let { message ->
        ErrorBanner(
            message = message,
            modifier = modifier,
            actionLabel = "知道了",
            onAction = onDismissError,
        )
    }
    state.infoMessage?.let { message ->
        InfoBanner(
            message = message,
            modifier = modifier,
            actionLabel = "知道了",
            onAction = onDismissInfo,
        )
    }
}

// ----------------------------------------------------------------------
// 空态与导入引导
// ----------------------------------------------------------------------

@Composable
private fun EmptyTimetableContent(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding),
    ) {
        Spacer(Modifier.height(16.dp))
        EmptyState(
            title = "还没有导入课表",
            icon = Icons.Outlined.CalendarMonth,
            description = "从 WakeUp 导出 .ics 文件后导入，即可查看周视图与月视图。",
            actionLabel = "导入 .ics 文件",
            onAction = onImportClick,
        )
        Spacer(Modifier.height(16.dp))
        ImportGuideCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ImportGuideCard() {
    SectionCard {
        SectionTitle(text = "如何从 WakeUp 导出")
        Spacer(Modifier.height(10.dp))
        listOf(
            "打开 WakeUp 课程表，进入「课表」页",
            "右上角菜单选择「导出课表 / 分享」",
            "选择「导出为 .ics 文件」",
            "回到这里点击右上角导入按钮，选择该文件",
        ).forEachIndexed { index, step ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TagPill(text = "${index + 1}")
                Spacer(Modifier.width(10.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// 周 / 月视图容器
// ----------------------------------------------------------------------

@Composable
private fun CourseScheduleContent(
    state: TimetableUiState,
    table: CourseTable,
    onViewModeChange: (TimetableViewMode) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        ViewModeSwitcher(
            mode = state.viewMode,
            onModeChange = onViewModeChange,
            modifier = Modifier.padding(horizontal = PageHorizontalPadding, vertical = 4.dp),
        )
        when (state.viewMode) {
            TimetableViewMode.WEEK -> WeekView(
                table = table,
                today = state.today,
                weekOffset = state.weekOffset,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onCurrentWeek = onCurrentWeek,
                onCourseClick = onCourseClick,
                modifier = Modifier.weight(1f),
            )
            TimetableViewMode.MONTH -> MonthView(
                table = table,
                today = state.today,
                monthOffset = state.monthOffset,
                selectedDateKey = state.selectedDateKey,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCurrentMonth = onCurrentMonth,
                onSelectDate = onSelectDate,
                onCourseClick = onCourseClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 周 / 月视图切换：两个可点击的 [TagPill]，选中态用主题色标识。 */
@Composable
private fun ViewModeSwitcher(
    mode: TimetableViewMode,
    onModeChange: (TimetableViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ModePill(
            text = "周视图",
            selected = mode == TimetableViewMode.WEEK,
            onClick = { onModeChange(TimetableViewMode.WEEK) },
        )
        Spacer(Modifier.width(8.dp))
        ModePill(
            text = "月视图",
            selected = mode == TimetableViewMode.MONTH,
            onClick = { onModeChange(TimetableViewMode.MONTH) },
        )
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    TagPill(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        color = color,
    )
}

// ----------------------------------------------------------------------
// 周期导航
// ----------------------------------------------------------------------

@Composable
private fun PeriodNavigation(
    title: String,
    subtitle: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一个")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一个")
        }
        if (actionLabel != null) {
            TextAction(text = actionLabel, onClick = onAction)
        }
    }
}

// ----------------------------------------------------------------------
// 周视图
// ----------------------------------------------------------------------

@Composable
private fun WeekView(
    table: CourseTable,
    today: LocalDate,
    weekOffset: Int,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekStart = remember(today, weekOffset) { TimetableQuery.weekStart(today, weekOffset) }
    val dates = remember(weekStart) { TimetableQuery.weekDates(weekStart) }
    val coursesByWeekday = remember(table, weekStart) { TimetableQuery.weekCourses(table, weekStart) }
    val weekNumber = remember(table, weekStart) { TimetableQuery.weekNumber(table, weekStart) }
    val isCurrentWeek = weekOffset == 0

    // 缩放状态：捏合手势与 +/− 按钮共享同一个目标值，动画在两者之间平滑过渡。
    val zoomState = rememberSaveable { mutableStateOf(TimetableZoom.DEFAULT_SCALE) }
    var zoomTarget by zoomState
    val scale by animateFloatAsState(
        targetValue = zoomTarget,
        animationSpec = tween(durationMillis = 220),
        label = "weekZoom",
    )
    val verticalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val onPinchZoom = remember(zoomState) {
        { factor: Float -> zoomState.value = TimetableZoom.zoomBy(zoomState.value, factor) }
    }

    val title = if (weekNumber != null) "第 $weekNumber 周" else dateRangeText(weekStart)
    val subtitle = if (weekNumber != null) dateRangeText(weekStart) else null

    Column(modifier.fillMaxWidth()) {
        PeriodNavigation(
            title = title,
            subtitle = subtitle,
            onPrevious = onPreviousWeek,
            onNext = onNextWeek,
            actionLabel = if (isCurrentWeek) null else "本周",
            onAction = onCurrentWeek,
        )
        if (isCurrentWeek) {
            TodaySummary(table = table, today = today)
        }
        WeekZoomControls(
            scale = scale,
            onZoomIn = { zoomTarget = TimetableZoom.zoomIn(zoomTarget) },
            onZoomOut = { zoomTarget = TimetableZoom.zoomOut(zoomTarget) },
            onOverview = {
                zoomTarget = TimetableZoom.overviewScale()
                scope.launch { verticalScroll.animateScrollTo(0) }
            },
        )
        if (coursesByWeekday.isEmpty()) {
            CenteredHint(
                text = "这一周没有课程，可切换到其他周查看",
                modifier = Modifier.padding(horizontal = PageHorizontalPadding, vertical = 6.dp),
            )
        }
        WeeklyGrid(
            coursesByWeekday = coursesByWeekday,
            dates = dates,
            today = today,
            scale = scale,
            verticalScroll = verticalScroll,
            onPinchZoom = onPinchZoom,
            onCourseClick = onCourseClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 周视图缩放工具条：「－ / 状态 / ＋ / 全览」。
 *
 * 状态胶囊跟随动画中的实际缩放值，缩放过程中显示百分比，处于全览/标准时显示对应文案。
 */
@Composable
private fun WeekZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOverview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        ZoomStepButton(
            icon = Icons.Outlined.Remove,
            description = "缩小课表",
            enabled = TimetableZoom.canZoomOut(scale),
            onClick = onZoomOut,
        )
        TagPill(text = TimetableZoom.scaleLabel(scale))
        ZoomStepButton(
            icon = Icons.Outlined.Add,
            description = "放大课表",
            enabled = TimetableZoom.canZoomIn(scale),
            onClick = onZoomIn,
        )
        Spacer(Modifier.width(8.dp))
        TextAction(
            text = "全览",
            onClick = onOverview,
            enabled = !TimetableZoom.isOverview(scale),
        )
    }
}

@Composable
private fun ZoomStepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** 本周概览：今天第几周、几节课、第一节时间。 */
@Composable
private fun TodaySummary(table: CourseTable, today: LocalDate) {
    val todayCourses = remember(table, today) { TimetableQuery.coursesOn(table, today) }
    if (todayCourses.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagPill(text = "今天 ${TimeFormat.weekdayLabel(today.dayOfWeek.value)}")
        Spacer(Modifier.width(8.dp))
        Text(
            text = "共 ${todayCourses.size} 节 · 首节 ${TimeFormat.minutesToClock(todayCourses.first().startMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeeklyGrid(
    coursesByWeekday: Map<Int, List<Course>>,
    dates: List<LocalDate>,
    today: LocalDate,
    scale: Float,
    verticalScroll: ScrollState,
    onPinchZoom: (Float) -> Unit,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allCourses = remember(coursesByWeekday) { coursesByWeekday.values.flatten() }
    val horizontalScroll = rememberScrollState()
    val gridStart = remember(allCourses) { computeGridStart(allCourses) }
    val gridEnd = remember(allCourses, gridStart) { computeGridEnd(allCourses, gridStart) }
    val hourHeightDp = TimetableZoom.hourHeightDp(scale)
    // v1.2.1：横向（列宽 / 时间轴）与文字字号跟随缩放，避免“只有竖直方向缩放”。
    val dayColumnWidthDp = TimetableZoom.dayColumnWidthDp(scale)
    val timeAxisWidthDp = TimetableZoom.timeAxisWidthDp(scale)
    val textScale = TimetableZoom.textScale(scale)
    val compact = TimetableZoom.isCompact(scale)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll),
        ) {
            Spacer(Modifier.width(timeAxisWidthDp.dp))
            dates.forEachIndexed { index, date ->
                DayHeader(
                    weekday = index + 1,
                    date = date,
                    isToday = date == today,
                    widthDp = dayColumnWidthDp,
                    textScale = textScale,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
                    .pinchToZoom(onPinchZoom),
            ) {
                TimeAxis(
                    gridStart = gridStart,
                    gridEnd = gridEnd,
                    hourHeightDp = hourHeightDp,
                    widthDp = timeAxisWidthDp,
                    textScale = textScale,
                )
                dates.forEachIndexed { index, date ->
                    DayColumn(
                        courses = coursesByWeekday[index + 1].orEmpty(),
                        gridStart = gridStart,
                        gridEnd = gridEnd,
                        hourHeightDp = hourHeightDp,
                        widthDp = dayColumnWidthDp,
                        textScale = textScale,
                        compact = compact,
                        isToday = date == today,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
    }
}

/**
 * 双指捏合缩放：只有两根及以上手指按下时才消费事件，单指拖动仍然交给滚动容器，
 * 因此“缩放后仍可滚动”。缩放因子交给调用方与按钮档位一起收敛到 [TimetableZoom] 范围内。
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(onZoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
private fun DayHeader(
    weekday: Int,
    date: LocalDate,
    isToday: Boolean,
    widthDp: Float,
    textScale: Float,
) {
    val labelColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = TimeFormat.weekdayLabel(weekday),
            style = MaterialTheme.typography.labelLarge.scaled(textScale),
            color = labelColor,
            maxLines = 1,
        )
        Text(
            text = "${date.monthValue}/${date.dayOfMonth}",
            style = MaterialTheme.typography.labelSmall.scaled(textScale),
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeAxis(
    gridStart: Int,
    gridEnd: Int,
    hourHeightDp: Float,
    widthDp: Float,
    textScale: Float,
) {
    val totalMinutes = gridEnd - gridStart
    val minuteHeightDp = hourHeightDp / MinutesPerHour
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height((totalMinutes * minuteHeightDp).dp),
    ) {
        var minute = gridStart
        while (minute <= gridEnd) {
            Text(
                text = TimeFormat.minutesToClock(minute),
                style = MaterialTheme.typography.labelSmall.scaled(textScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = ((minute - gridStart) * minuteHeightDp - 6f).coerceAtLeast(0f).dp)
                    .padding(end = 4.dp),
            )
            minute += MinutesPerHour
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun DayColumn(
    courses: List<Course>,
    gridStart: Int,
    gridEnd: Int,
    hourHeightDp: Float,
    widthDp: Float,
    textScale: Float,
    compact: Boolean,
    isToday: Boolean,
    onCourseClick: (Course) -> Unit,
) {
    val totalMinutes = gridEnd - gridStart
    val minuteHeightDp = hourHeightDp / MinutesPerHour
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (isToday) 0.7f else GridBgAlpha,
    )
    val minCardHeightDp = (
        (if (compact) CompactCourseCardHeight else MinCourseCardHeight).value * textScale
        ).coerceAtLeast(24f)
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height((totalMinutes * minuteHeightDp).dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.dp.toPx()
            val minuteHeightPx = minuteHeightDp.dp.toPx()
            var minute = gridStart
            while (minute <= gridEnd) {
                val y = (minute - gridStart) * minuteHeightPx
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth,
                )
                minute += MinutesPerHour
            }
        }
        courses.forEach { course ->
            val topDp = (course.startMinutes - gridStart).coerceAtLeast(0) * minuteHeightDp
            val bottomDp = (course.endMinutes - gridStart).coerceAtMost(totalMinutes) * minuteHeightDp
            val cardHeightDp = (bottomDp - topDp).coerceAtLeast(minCardHeightDp)
            CourseCard(
                course = course,
                compact = compact,
                textScale = textScale,
                onClick = { onCourseClick(course) },
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .offset(y = topDp.dp)
                    .height(cardHeightDp.dp),
            )
        }
    }
}

/**
 * 周视图课程块：颜色完全由 `Course.colorSeed` 决定（底色 / 文字色 / 左侧强调条），
 * 同一门课每次进入颜色一致，不同课程分散在 12 色 pastel 色板里，保持简约不花哨。
 */
@Composable
private fun CourseCard(
    course: Course,
    compact: Boolean,
    textScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = course.colorSeed
    val containerColor = CourseColors.container(seed)
    val contentColor = CourseColors.onContainer(seed)
    val accentColor = CourseColors.accent(seed)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = accentColor),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = (6f * textScale).dp,
                        vertical = (4f * textScale).dp,
                    ),
            ) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.labelMedium.scaled(textScale),
                    color = contentColor,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && course.location.isNotBlank()) {
                    Text(
                        text = course.location,
                        style = MaterialTheme.typography.labelSmall.scaled(textScale),
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// 月视图
// ----------------------------------------------------------------------

@Composable
private fun MonthView(
    table: CourseTable,
    today: LocalDate,
    monthOffset: Int,
    selectedDateKey: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = remember(today, monthOffset) { TimetableQuery.monthOf(today, monthOffset) }
    val monthCourses = remember(table, month) { TimetableQuery.monthCourses(table, month) }
    val gridDates = remember(month) { TimetableQuery.monthGridDates(month) }
    val gridCourses = remember(table, month) { TimetableQuery.monthGridCourses(table, month) }
    val selectedDate = remember(today, month, selectedDateKey) {
        TimetableQuery.selectedDate(today, month, selectedDateKey)
    }
    val selectedCourses = remember(table, selectedDate) { TimetableQuery.coursesOn(table, selectedDate) }
    val isCurrentMonth = month == YearMonth.from(today)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        PeriodNavigation(
            title = "${month.year}年${month.monthValue}月",
            subtitle = "${monthCourses.size} 天有课",
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            actionLabel = if (isCurrentMonth) null else "本月",
            onAction = onCurrentMonth,
        )
        MonthWeekdayHeader()
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            gridDates.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        MonthDayCell(
                            date = date,
                            inMonth = YearMonth.from(date) == month,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            courses = gridCourses[date].orEmpty(),
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SelectedDayCourses(
            date = selectedDate,
            courses = selectedCourses,
            isToday = selectedDate == today,
            onCourseClick = onCourseClick,
            modifier = Modifier.padding(horizontal = PageHorizontalPadding),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MonthWeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        (1..7).forEach { weekday ->
            Text(
                text = TimeFormat.weekdayLabel(weekday).removePrefix("周"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    courses: List<Course>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val palette = LocalLightDeskPalette.current
    val cellColor = when {
        isSelected -> palette.accentSoft
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .height(MonthCellHeight)
            .padding(1.5.dp)
            .clip(shape)
            .background(cellColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DayNumber(
            day = date.dayOfMonth,
            inMonth = inMonth,
            isToday = isToday,
            isSelected = isSelected,
        )
        courses.take(MaxMonthCourseLines).forEach { course ->
            MonthCourseChip(course = course, inMonth = inMonth)
        }
        val extra = courses.size - MaxMonthCourseLines
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 月视图课程条：与周视图课程块共用同一套课程色（底色 + 文字色 + 左侧色点）。 */
@Composable
private fun MonthCourseChip(course: Course, inMonth: Boolean) {
    val seed = course.colorSeed
    val containerColor = CourseColors.container(seed)
    val contentColor = CourseColors.onContainer(seed)
    val accentColor = CourseColors.accent(seed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (inMonth) 1f else 0.55f)
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(accentColor),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = course.title,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayNumber(day: Int, inMonth: Boolean, isToday: Boolean, isSelected: Boolean) {
    val background = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val textColor = when {
        isToday -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectedDayCourses(
    date: LocalDate,
    courses: List<Course>,
    isToday: Boolean,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            text = buildString {
                append("${date.monthValue}月${date.dayOfMonth}日 ")
                append(TimeFormat.weekdayLabel(date.dayOfWeek.value))
                if (isToday) append(" · 今天")
            },
            trailing = {
                if (courses.isNotEmpty()) {
                    TagPill(text = "${courses.size} 节课")
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        if (courses.isEmpty()) {
            CenteredHint(text = "这一天没有课程")
        } else {
            courses.forEachIndexed { index, course ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DayCourseRow(course = course, onClick = { onCourseClick(course) })
            }
        }
    }
}

@Composable
private fun DayCourseRow(course: Course, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accentColor = CourseColors.accent(course.colorSeed)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.width(58.dp)) {
            Text(
                text = TimeFormat.minutesToClock(course.startMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = TimeFormat.minutesToClock(course.endMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// 课程详情
// ----------------------------------------------------------------------

@Composable
private fun CourseDetailDialog(course: Course, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextAction(text = "关闭", onClick = onDismiss)
        },
        title = {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                DetailRow(label = "时间", value = "${TimeFormat.weekdayLabel(course.weekday)} " +
                    "${TimeFormat.minutesToClock(course.startMinutes)} - " +
                    TimeFormat.minutesToClock(course.endMinutes))
                DetailRow(label = "周次", value = WeekText.format(course.weeks))
                if (course.teacher.isNotBlank()) {
                    DetailRow(label = "教师", value = course.teacher)
                }
                if (course.location.isNotBlank()) {
                    DetailRow(label = "地点", value = course.location)
                }
                if (course.note.isNotBlank()) {
                    DetailRow(label = "备注", value = course.note)
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// ----------------------------------------------------------------------
// 纯工具函数
// ----------------------------------------------------------------------

/** 8:00 - 20:00 之类的网格范围：取最早开始时间的整点，限制在 6:00 之后、14:00 之前。 */
private fun computeGridStart(courses: List<Course>): Int {
    if (courses.isEmpty()) return 8 * 60
    val earliest = courses.minOf { it.startMinutes }.coerceIn(0, 24 * 60)
    val hour = (earliest / MinutesPerHour) * MinutesPerHour
    return hour.coerceIn(6 * MinutesPerHour, 14 * MinutesPerHour)
}

/** 取最晚结束时间的整点再向下多留一小时，限制在 23:00 之内。 */
private fun computeGridEnd(courses: List<Course>, gridStart: Int): Int {
    val latest = if (courses.isEmpty()) 18 * 60 else courses.maxOf { it.endMinutes }
    val hour = ((latest + MinutesPerHour - 1) / MinutesPerHour) * MinutesPerHour
    val paddingEnd = (hour + MinutesPerHour).coerceAtMost(23 * MinutesPerHour)
    return paddingEnd.coerceAtLeast(gridStart + 4 * MinutesPerHour)
}

private fun dateRangeText(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    return "${weekStart.monthValue}月${weekStart.dayOfMonth}日 - ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
}

/**
 * 按缩放比例同步放大/缩小字号与行高，让“横向缩放”时文字与格子一起变化。
 * 只复制 fontSize / lineHeight，其余字体属性（字重、字族）仍由主题统一控制。
 */
private fun TextStyle.scaled(factor: Float): TextStyle = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor,
)
