package com.lightdesk.feature.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lightdesk.core.designsystem.component.ErrorBanner
import com.lightdesk.core.designsystem.component.PageHorizontalPadding
import com.lightdesk.core.designsystem.component.PrimaryButton
import com.lightdesk.core.designsystem.component.SecondaryButton
import com.lightdesk.core.designsystem.component.SettingSwitchRow
import com.lightdesk.core.designsystem.component.SettingValueRow
import com.lightdesk.core.designsystem.theme.LocalLightDeskPalette

/**
 * 新增/编辑事项的底部卡片。
 *
 * 字段：标题（必填）、内容（多行）、设为待办（开启后可选截止日期 + 时间）、置顶。
 * 所有状态与校验结果来自 [NoteEditorState]，本组件只负责展示与回调。
 *
 * 视觉：容器色、圆角、日期/时间选择器容器全部走主题色（不使用硬编码白底），
 * 标题旁用主题 accentSoft 色块做点缀。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorSheet(
    editor: NoteEditorState,
    now: Long,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTodoEnabledChange: (Boolean) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onDeadlineChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
) {
    val palette = LocalLightDeskPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // 打开日期/时间选择器时的基准时间：已有截止时间则沿用，否则用默认截止时间。
    val baseDeadline = editor.deadline ?: NotesFormat.defaultDeadline(now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageHorizontalPadding)
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = palette.accentSoft,
                    border = BorderStroke(width = 1.dp, color = palette.outlineVariant),
                ) {
                    Icon(
                        imageVector = if (editor.isEditing) {
                            Icons.Outlined.EditNote
                        } else {
                            Icons.Outlined.Add
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (editor.isEditing) "编辑事项" else "新建事项",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                placeholder = { Text("要做什么？") },
                singleLine = true,
                isError = editor.titleError != null,
                shape = MaterialTheme.shapes.medium,
                enabled = !editor.saving,
            )
            editor.titleError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = editor.content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容（可选）") },
                placeholder = { Text("补充说明、步骤或备注") },
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                enabled = !editor.saving,
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingSwitchRow(
                title = "设为待办",
                checked = editor.todoEnabled,
                onCheckedChange = onTodoEnabledChange,
                description = "开启后设置截止时间，到期前显示实时倒计时",
                enabled = !editor.saving,
            )
            if (editor.todoEnabled) {
                SettingValueRow(
                    label = "截止日期",
                    value = NotesFormat.editorDateText(editor.deadline),
                    onClick = { showDatePicker = true },
                )
                SettingValueRow(
                    label = "截止时间",
                    value = NotesFormat.editorTimeText(editor.deadline),
                    onClick = { showTimePicker = true },
                )
            }
            SettingSwitchRow(
                title = "置顶",
                checked = editor.pinned,
                onCheckedChange = onPinnedChange,
                description = "置顶事项始终排在列表最前",
                enabled = !editor.saving,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            editor.error?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                ErrorBanner(
                    message = message,
                    actionLabel = "知道了",
                    onAction = onDismissError,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !editor.saving,
                )
                PrimaryButton(
                    text = "保存",
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    loading = editor.saving,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDatePicker) {
        DeadlineDatePickerDialog(
            initialDeadline = baseDeadline,
            onDismiss = { showDatePicker = false },
            onConfirm = { utcDateMillis ->
                showDatePicker = false
                onDeadlineChange(NotesFormat.withDate(baseDeadline, utcDateMillis))
            },
        )
    }

    if (showTimePicker) {
        DeadlineTimePickerDialog(
            initialDeadline = baseDeadline,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                onDeadlineChange(NotesFormat.withTime(baseDeadline, hour, minute))
            },
        )
    }
}

/** Material3 日期选择器：返回 UTC 零点毫秒值，由调用方与既有时间合并。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDatePickerDialog(
    initialDeadline: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = NotesFormat.toUtcDateMillis(initialDeadline),
    )
    // 主题没有定义 surfaceContainer* 角色，显式给容器色，避免落回 M3 基线灰紫。
    val pickerColors = DatePickerDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = pickerColors,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        datePickerState.selectedDateMillis
                            ?: NotesFormat.toUtcDateMillis(initialDeadline),
                    )
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            colors = pickerColors,
        )
    }
}

/**
 * 时间选择器弹窗。
 *
 * 用 [Dialog] 而不是 AlertDialog：Material3 TimePicker 自带固定宽度，
 * AlertDialog 的内容区会把它裁切掉。容器与表盘颜色统一走主题色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineTimePickerDialog(
    initialDeadline: Long,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = NotesFormat.hourOf(initialDeadline),
        initialMinute = NotesFormat.minuteOf(initialDeadline),
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "选择截止时间",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "确定",
                        onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
