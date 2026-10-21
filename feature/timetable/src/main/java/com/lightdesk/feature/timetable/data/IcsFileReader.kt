package com.lightdesk.feature.timetable.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import java.io.IOException

/** 从系统文件选择器返回的 Uri 读出的课表文件内容。 */
data class IcsFileContent(
    val fileName: String,
    val content: String,
)

/**
 * 读取用户选择的 .ics 文件。
 *
 * 所有失败（无权限、文件被删除、内容为空、文件过大）都会被转换成带中文提示的
 * [AppResult.Failure]，调用方直接展示即可，不会把异常抛到 UI 层。
 */
object IcsFileReader {

    private const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val DEFAULT_FILE_NAME = "导入的课表.ics"

    fun read(context: Context, uri: Uri): AppResult<IcsFileContent> {
        return try {
            val resolver = context.contentResolver
            val fileName = queryDisplayName(context, uri)?.trim()?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_FILE_NAME

            val bytes = resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                ?: return AppResult.Failure(
                    AppError(ErrorCode.NOT_FOUND, "无法打开所选文件，文件可能已被移动或删除"),
                )

            when {
                bytes.isEmpty() -> AppResult.Failure(
                    AppError(ErrorCode.INVALID_INPUT, "文件内容为空，无法导入课表"),
                )

                bytes.size > MAX_FILE_BYTES -> AppResult.Failure(
                    AppError(ErrorCode.INVALID_INPUT, "文件过大（超过 5 MB），无法导入课表"),
                )

                else -> {
                    val content = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
                    if (content.isBlank()) {
                        AppResult.Failure(
                            AppError(ErrorCode.INVALID_INPUT, "文件内容为空，无法导入课表"),
                        )
                    } else {
                        AppResult.Success(IcsFileContent(fileName = fileName, content = content))
                    }
                }
            }
        } catch (security: SecurityException) {
            AppResult.Failure(
                AppError(ErrorCode.PERMISSION, "没有读取该文件的权限，请重新选择文件", security),
            )
        } catch (io: IOException) {
            AppResult.Failure(
                AppError(ErrorCode.STORAGE, "读取文件失败，请确认文件仍然存在后重试", io),
            )
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.fromThrowable(throwable, ErrorCode.UNKNOWN))
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex < 0) null else cursor.getString(columnIndex)
            }
    }.getOrNull()
}
