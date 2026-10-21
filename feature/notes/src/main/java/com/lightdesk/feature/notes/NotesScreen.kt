package com.lightdesk.feature.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lightdesk.core.designsystem.component.ConfirmDialog
import com.lightdesk.core.designsystem.component.EmptyState
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.LoadingView
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.ScreenHeader
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette
import com.lightdesk.core.designsystem.theme.LocalStatusColors
import com.lightdesk.core.model.NoteItem

/**
 * 备忘页界面：头部 + 概览色块 + 筛选 chip + 列表 + FAB + 编辑弹窗 + 删除确认。
 *
 * 该 Composable 只负责渲染 [NotesUiState] 与转发用户操作，不包含任何业务逻辑。
 */
@Composable
internal fun NotesScreen(
    uiState: NotesUiState,
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "备忘",
                subtitle = "备忘录与待办统一管理",
            )
            NotesOverviewStrip(
                counts = uiState.counts,
                modifier = Modifier.padding(
                    start = PageHorizontalPadding,
                    end = PageHorizontalPadding,
                    top = 2.dp,
                    bottom = 2.dp,
                ),
            )
            NotesFilterRow(
                selected = uiState.filter,
                counts = uiState.counts,
                onFilterChange = viewModel::setFilter,
            )
            uiState.error?.let { message ->
                ErrorBanner(
                    message = message,
                    modifier = Modifier.padding(
                        horizontal = PageHorizontalPadding,
                        vertical = 6.dp,
                    ),
                    onAction = viewModel::loadItems,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    uiState.loading -> LoadingView(modifier = Modifier.align(Alignment.TopCenter))
                    uiState.items.isEmpty() -> NotesEmptyState(
                        filter = uiState.filter,
                        onCreate = viewModel::openCreate,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PageHorizontalPadding,
                            end = PageHorizontalPadding,
                            top = 4.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items = uiState.items, key = { it.id }) { item ->
                            NoteCard(
                                item = item,
                                now = uiState.now,
                                onToggleDone = { viewModel.toggleDone(item) },
                                onEdit = { viewModel.openEdit(item) },
                                onTogglePinned = { viewModel.togglePinned(item) },
                                onDelete = { viewModel.requestDelete(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = viewModel::openCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PageHorizontalPadding, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = "新建事项")
        }
    }

    uiState.editor?.let { editor ->
        NoteEditorSheet(
            editor = editor,
            now = uiState.now,
            onTitleChange = viewModel::onEditorTitleChange,
            onContentChange = viewModel::onEditorContentChange,
            onTodoEnabledChange = viewModel::onEditorTodoEnabledChange,
            onPinnedChange = viewModel::onEditorPinnedChange,
            onDeadlineChange = viewModel::onEditorDeadlineChange,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveEditor,
            onDismissError = viewModel::dismissEditorError,
        )
    }

    uiState.pendingDelete?.let { item ->
        ConfirmDialog(
            title = "删除事项",
            message = "确定删除「${item.title}」吗？删除后不可恢复。",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
            confirmText = "删除",
            destructive = true,
        )
    }
}

/**
 * 页头下方的概览色块：用主题的 accentSoft 底 + 细边框，
 * 三个圆点分别对应备忘/待办/已完成的语义色。
 */
@Composable
private fun NotesOverviewStrip(
    counts: Map<NotesFilter, Int>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLightDeskPalette.current
    val status = LocalStatusColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = palette.accentSoft,
        border = BorderStroke(width = 1.dp, color = palette.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverviewStat(
                label = "备忘",
                count = counts[NotesFilter.MEMO] ?: 0,
                dotColor = palette.primary,
            )
            OverviewStat(
                label = "待办",
                count = counts[NotesFilter.TODO] ?: 0,
                dotColor = palette.secondary,
            )
            OverviewStat(
                label = "已完成",
                count = counts[NotesFilter.COMPLETED] ?: 0,
                dotColor = status.success,
            )
        }
    }
}

@Composable
private fun OverviewStat(label: String, count: Int, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 顶部筛选 chip 行：横向可滚动，label 带计数，选中态用主题浅色块。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesFilterRow(
    selected: NotesFilter,
    counts: Map<NotesFilter, Int>,
    onFilterChange: (NotesFilter) -> Unit,
) {
    val palette = LocalLightDeskPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotesFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = palette.accentSoft,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = palette.primaryContainer,
                    selectedLabelColor = palette.onPrimaryContainer,
                ),
            )
        }
    }
}

/** 空状态：外层 accentSoft 色块 + 细边框，与卡片色块保持同一套浅色视觉。 */
@Composable
private fun NotesEmptyState(
    filter: NotesFilter,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLightDeskPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding, vertical = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = palette.accentSoft,
        border = BorderStroke(width = 1.dp, color = palette.outlineVariant),
    ) {
        EmptyState(
            title = emptyStateTitle(filter),
            description = emptyStateDescription(filter),
            icon = emptyStateIcon(filter),
            actionLabel = if (filter == NotesFilter.COMPLETED) null else "新建事项",
            onAction = if (filter == NotesFilter.COMPLETED) null else onCreate,
        )
    }
}

private fun emptyStateTitle(filter: NotesFilter): String = when (filter) {
    NotesFilter.ALL -> "还没有任何事项"
    NotesFilter.MEMO -> "还没有备忘"
    NotesFilter.TODO -> "还没有待办"
    NotesFilter.COMPLETED -> "还没有已完成事项"
}

private fun emptyStateDescription(filter: NotesFilter): String = when (filter) {
    NotesFilter.ALL -> "点右下角 + 新建第一条备忘或待办"
    NotesFilter.MEMO -> "新建时关闭「设为待办」即可保存为备忘"
    NotesFilter.TODO -> "新建时打开「设为待办」并选择截止时间"
    NotesFilter.COMPLETED -> "勾选完成的事项会出现在这里"
}

private fun emptyStateIcon(filter: NotesFilter): ImageVector = when (filter) {
    NotesFilter.ALL -> Icons.Outlined.EditNote
    NotesFilter.MEMO -> Icons.Outlined.EditNote
    NotesFilter.TODO -> Icons.AutoMirrored.Outlined.EventNote
    NotesFilter.COMPLETED -> Icons.Outlined.CheckCircle
}
