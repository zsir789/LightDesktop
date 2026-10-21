package com.lightdesk.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdesk.core.common.TimeFormat
import com.lightdesk.core.designsystem.component.CenteredHint
import com.lightdesk.core.designsystem.component.EmptyState
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.LoadingView
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.SectionCard
import com.lightdesk.core.designsystem.component.SectionTitle
import com.lightdesk.core.designsystem.component.TagPill
import com.lightdesk.core.designsystem.component.TextAction
import com.lightdesk.core.designsystem.theme.CourseColors
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette
import com.lightdesk.core.designsystem.theme.LocalStatusColors

/**
 * 首页内容区：欢迎卡 + 今日课程色块 + 今日待办色块 + 快捷入口。
 *
 * v1.2.0 视觉层：课程色块由 `CourseColors`（core:designsystem）按 `course.colorSeed` 上色，
 * 待办色块按 `item.id.hashCode()` 上浅色；页面本身不处理导航，全部通过回调交给 [HomeRoute]。
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenTimetable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        WelcomeCard(state = state, onRefresh = onRefresh)

        state.error?.let { message ->
            ErrorBanner(message = message, onAction = onRefresh)
        }

        if (state.loading) {
            LoadingView()
        } else {
            TodayCoursesSection(state = state, onOpenTimetable = onOpenTimetable)
            TodayTodosSection(state = state, onOpenNotes = onOpenNotes)
            QuickActionsSection(onOpenNotes = onOpenNotes, onOpenTimetable = onOpenTimetable)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 柔和渐变欢迎卡：accentSoft → primaryContainer 的横向渐变，圆角、无重阴影。
 */
@Composable
private fun WelcomeCard(state: HomeUiState, onRefresh: () -> Unit) {
    val palette = LocalLightDeskPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(palette.accentSoft, palette.primaryContainer),
                )
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagPill(text = state.dateLabel, color = palette.primary)
                    state.weekNumber?.let { week ->
                        TagPill(text = "第 $week 周", color = palette.primary)
                    }
                }
            }
            TextAction(text = "刷新", onClick = onRefresh, color = palette.primary)
        }
    }
}

@Composable
private fun TodayCoursesSection(state: HomeUiState, onOpenTimetable: () -> Unit) {
    val highlightedId = state.highlightedCourseId
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(
            text = "今日课程",
            trailing = {
                if (state.hasTimetable) {
                    TextAction(text = "查看课表", onClick = onOpenTimetable)
                }
            },
        )
        when {
            !state.hasTimetable -> SectionCard {
                EmptyState(
                    title = "还没有导入课表",
                    description = "导入课表后，这里会显示今天的课程安排",
                    icon = Icons.Outlined.CalendarMonth,
                    actionLabel = "去导入课表",
                    onAction = onOpenTimetable,
                )
            }

            state.courses.isEmpty() -> SectionCard {
                CenteredHint(text = "今天没有课")
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.courses.forEachIndexed { index, course ->
                    StaggeredFadeIn(index = index) {
                        CourseBlock(
                            course = course,
                            highlighted = course.course.id == highlightedId,
                            onClick = onOpenTimetable,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseBlock(course: TodayCourse, highlighted: Boolean, onClick: () -> Unit) {
    val item = course.course
    val seed = item.colorSeed
    val containerColor = CourseColors.container(seed)
    val contentColor = CourseColors.onContainer(seed)
    val accentColor = CourseColors.accent(seed)
    val statusAlpha = HomePresentation.courseContentAlpha(course.status)
    val titleColor = contentColor.copy(alpha = contentColor.alpha * statusAlpha)
    val subColor = contentColor.copy(alpha = contentColor.alpha * statusAlpha * 0.72f)
    val meta = listOfNotNull(
        item.location.takeIf { it.isNotBlank() },
        item.teacher.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    ColorBlockCard(
        containerColor = containerColor,
        accentColor = accentColor,
        highlighted = highlighted,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = TimeFormat.minutesToClock(item.startMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = titleColor,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = TimeFormat.minutesToClock(item.endMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = subColor,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (course.status) {
            CourseStatus.ONGOING -> TagPill(
                text = HomePresentation.statusLabel(course.status),
                color = MaterialTheme.colorScheme.primary,
            )

            CourseStatus.UPCOMING -> TagPill(
                text = HomePresentation.statusLabel(course.status),
                color = LocalStatusColors.current.info,
            )

            CourseStatus.FINISHED -> Text(
                text = HomePresentation.statusLabel(course.status),
                style = MaterialTheme.typography.labelMedium,
                color = subColor,
            )
        }
    }
}

@Composable
private fun TodayTodosSection(state: HomeUiState, onOpenNotes: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(
            text = "今日待办",
            trailing = {
                if (state.todos.isNotEmpty()) {
                    TextAction(text = "查看全部", onClick = onOpenNotes)
                }
            },
        )
        if (state.todos.isEmpty()) {
            SectionCard {
                EmptyState(
                    title = "今天没有待办",
                    description = "把今天要做的事记下来，到点前不会漏",
                    icon = Icons.Outlined.Checklist,
                    actionLabel = "去添加",
                    onAction = onOpenNotes,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.todos.forEachIndexed { index, todo ->
                    StaggeredFadeIn(index = index) {
                        TodoBlock(todo = todo, now = state.now, onClick = onOpenNotes)
                    }
                }
            }
            if (state.pendingTodoCount > 0) {
                PendingTodoFooter(count = state.pendingTodoCount, onClick = onOpenNotes)
            }
        }
    }
}

@Composable
private fun TodoBlock(todo: TodayTodo, now: Long, onClick: () -> Unit) {
    val item = todo.item
    val seed = HomePresentation.todoSeed(item.id)
    val containerColor = CourseColors.container(seed)
    val contentColor = CourseColors.onContainer(seed)
    val accentColor = CourseColors.accent(seed)
    val statusColors = LocalStatusColors.current
    val countdownColor = when {
        todo.overdue -> statusColors.danger
        todo.urgent -> statusColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val metaColor = contentColor.copy(alpha = contentColor.alpha * 0.7f)
    val tagLabel = HomePresentation.todoTagLabel(overdue = todo.overdue, urgent = todo.urgent)
    val tagColor = if (todo.overdue) statusColors.danger else statusColors.warning

    ColorBlockCard(
        containerColor = containerColor,
        accentColor = accentColor,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = countdownColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { "未命名待办" },
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontWeight = if (todo.overdue) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.deadline?.let { deadline ->
                    Text(
                        text = "截止 ${TimeFormat.timestampToDateTimeText(deadline)} · " +
                            TimeFormat.deadlineHint(deadline, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (tagLabel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TagPill(text = tagLabel, color = tagColor)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = todo.countdownText,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            color = countdownColor,
        )
    }
}

@Composable
private fun PendingTodoFooter(count: Int, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "全部未完成待办 $count 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "去查看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun QuickActionsSection(onOpenNotes: () -> Unit, onOpenTimetable: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = "快捷入口")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PressableActionButton(
                text = "打开备忘",
                icon = Icons.Outlined.EditNote,
                onClick = onOpenNotes,
                filled = true,
                modifier = Modifier.weight(1f),
            )
            PressableActionButton(
                text = "查看课表",
                icon = Icons.Outlined.CalendarMonth,
                onClick = onOpenTimetable,
                filled = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 主题色快捷入口按钮：按下时缩放到 0.98，松开回弹，给点击一个轻量反馈。
 */
@Composable
private fun PressableActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "quickActionScale",
    )
    val background = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 色块卡：左侧强调条 + 圆角底色；[highlighted] 时加一圈 accent 描边强调。
 * 课程色块与待办色块共用该结构，保持首页视觉统一。
 */
@Composable
private fun ColorBlockCard(
    containerColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .then(
                if (highlighted) {
                    Modifier.border(width = 1.5.dp, color = accentColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(if (highlighted) 5.dp else 4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** 列表淡入：按序号轻微错峰，避免整屏内容同时闪现。 */
@Composable
private fun StaggeredFadeIn(index: Int, content: @Composable () -> Unit) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 240,
                delayMillis = (index.coerceAtMost(6)) * 40,
            )
        ),
        exit = fadeOut(),
    ) {
        content()
    }
}
