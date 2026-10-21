package com.lightdesk.feature.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightdesk.core.designsystem.component.TagPill
import com.lightdesk.core.designsystem.theme.CourseColors
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette
import com.lightdesk.core.designsystem.theme.LocalStatusColors
import com.lightdesk.core.model.NoteItem

/**
 * 单条事项卡片（v1.2 视觉升级）。
 *
 * 视觉规则：
 * - 每张卡按 [NoteItem.id] 的稳定 hash 取一个 pastel 色块（`CourseColors`），左侧强调条同色相；
 * - 完成态保留色相语义但降饱和：底色渐变为 `surfaceVariant`，文字弱化并加删除线；
 * - 置顶/逾期仍用 `StatusColors` 的 warning/danger 标识，避免和色块抢视觉；
 * - 按压时整卡轻微缩放到 0.98；点击进入编辑，长按弹出操作菜单。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteCard(
    item: NoteItem,
    now: Long,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val palette = LocalLightDeskPalette.current

    // 稳定 seed：直接取 id.hashCode()，同一事项每次进入列表颜色一致，不使用随机数。
    val seed = NotesVisuals.seedFor(item.id)
    val blockContainer = CourseColors.container(seed)
    val blockContent = CourseColors.onContainer(seed)
    val blockAccent = CourseColors.accent(seed)

    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = NotesVisuals.pressScale(pressed),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "noteCardPressScale",
    )

    val containerColor by animateColorAsState(
        targetValue = if (item.done) MaterialTheme.colorScheme.surfaceVariant else blockContainer,
        animationSpec = tween(durationMillis = 260),
        label = "noteCardContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else blockContent,
        animationSpec = tween(durationMillis = 260),
        label = "noteCardContent",
    )
    val accentColor by animateColorAsState(
        targetValue = if (item.done) MaterialTheme.colorScheme.outlineVariant else blockAccent,
        animationSpec = tween(durationMillis = 260),
        label = "noteCardAccent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (item.done) MaterialTheme.colorScheme.outlineVariant else palette.outline,
        animationSpec = tween(durationMillis = 260),
        label = "noteCardBorder",
    )

    val contentAlpha = NotesVisuals.contentAlpha(item.done)
    val titleColor = contentColor.copy(alpha = contentAlpha)
    val secondaryColor = contentColor.copy(alpha = contentAlpha * 0.78f)

    val deadline = item.deadline
    val deadlineColor = when {
        item.done -> status.success
        deadline != null && item.isOverdue(now) -> status.danger
        else -> secondaryColor
    }
    val deadlineText = when {
        item.done -> "已完成"
        deadline != null -> NotesFormat.countdownText(deadline, now)
        else -> ""
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(pressScale),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧同色相强调条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = "编辑事项",
                        onLongClickLabel = "更多操作",
                        onLongClick = { menuExpanded = true },
                        onClick = onEdit,
                    )
                    .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CompletionCheckbox(
                    checked = item.done,
                    onClick = onToggleDone,
                    uncheckedBorderColor = titleColor.copy(alpha = 0.55f),
                    checkTint = MaterialTheme.colorScheme.onPrimary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.pinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "已置顶",
                                tint = status.warning,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            textDecoration = if (item.done) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val preview = item.preview
                    if (preview.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TagPill(
                            text = if (item.isTodo) "待办" else "备忘",
                            color = contentColor.copy(alpha = contentAlpha),
                        )
                        if (deadline != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = deadlineColor,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${NotesFormat.deadlineDateTimeText(deadline)} · $deadlineText",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = deadlineColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多操作",
                            tint = titleColor.copy(alpha = 0.8f),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.pinned) "取消置顶" else "置顶") },
                            onClick = {
                                menuExpanded = false
                                onTogglePinned()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = status.danger) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 完成勾选控件：填充色渐变 + 对勾弹性缩放渐显。
 *
 * 勾选后的填充沿用语义成功色，对勾颜色取卡片文字色（onPrimary），
 * 因而与 pastel 色块、深色主题都能保持对比度。
 */
@Composable
private fun CompletionCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    uncheckedBorderColor: Color,
    checkTint: Color,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val fillColor by animateColorAsState(
        targetValue = if (checked) status.success else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "checkFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) status.success else uncheckedBorderColor,
        animationSpec = tween(durationMillis = 180),
        label = "checkBorder",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "checkScale",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "checkAlpha",
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics {
                contentDescription = if (checked) "取消完成" else "标记完成"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = checkTint,
                modifier = Modifier
                    .size(16.dp)
                    .scale(checkScale)
                    .alpha(checkAlpha),
            )
        }
    }
}
