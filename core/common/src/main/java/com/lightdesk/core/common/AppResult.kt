package com.lightdesk.core.common

/**
 * 统一错误码：跨模块错误传递时只暴露语义化错误，避免上层直接依赖底层异常类型。
 * 新增能力时在此追加错误码即可，不需要改动已有模块。
 */
enum class ErrorCode {
    NETWORK,
    TIMEOUT,
    PARSE,
    STORAGE,
    PERMISSION,
    INVALID_INPUT,
    NOT_FOUND,
    UNKNOWN,
}

data class AppError(
    val code: ErrorCode,
    val message: String,
    val cause: Throwable? = null,
) {
    companion object {
        fun fromThrowable(throwable: Throwable, code: ErrorCode = ErrorCode.UNKNOWN): AppError {
            return AppError(
                code = code,
                message = throwable.message?.takeIf { it.isNotBlank() } ?: defaultMessage(code),
                cause = throwable,
            )
        }

        fun defaultMessage(code: ErrorCode): String = when (code) {
            ErrorCode.NETWORK -> "网络连接失败，请检查网络后重试"
            ErrorCode.TIMEOUT -> "请求超时，请稍后重试"
            ErrorCode.PARSE -> "数据解析失败"
            ErrorCode.STORAGE -> "本地数据读写失败"
            ErrorCode.PERMISSION -> "缺少必要权限"
            ErrorCode.INVALID_INPUT -> "输入内容不合法"
            ErrorCode.NOT_FOUND -> "数据不存在"
            ErrorCode.UNKNOWN -> "发生未知错误"
        }
    }
}

/** 统一结果类型：功能模块之间通过它传递成功/失败，禁止吞掉异常。 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

val AppResult<*>.isSuccess: Boolean
    get() = this is AppResult.Success

fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> null
}

fun <T> AppResult<T>.getOrElse(fallback: T): T = getOrNull() ?: fallback

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/** 包裹可能抛出异常的代码，统一转换为 [AppResult]，确保异常不会穿透到 UI 层。 */
inline fun <T> attempt(code: ErrorCode = ErrorCode.UNKNOWN, block: () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        AppResult.Failure(AppError.fromThrowable(t, code))
    }
}
