package com.lightdesk.feature.weather.data

import com.lightdesk.core.common.AppError
import com.lightdesk.core.common.AppResult
import com.lightdesk.core.common.ErrorCode
import com.lightdesk.core.common.SystemTimeProvider
import com.lightdesk.core.common.TimeProvider
import com.lightdesk.core.model.WeatherSnapshot
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Open-Meteo 网络客户端（无需 API Key）。
 *
 * 所有网络异常（连接失败/超时/HTTP 错误/响应解析失败）都会转换成
 * [AppResult.Failure]，由 ViewModel 决定是否降级到缓存，不会抛到 UI 层。
 */
class OpenMeteoClient(
    private val client: OkHttpClient = defaultHttpClient(),
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {

    suspend fun fetch(
        city: String,
        latitude: Double,
        longitude: Double,
    ): AppResult<WeatherSnapshot> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(OpenMeteoUrl.build(latitude, longitude))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError(
                            code = ErrorCode.NETWORK,
                            message = "天气服务返回异常（HTTP ${response.code}），请稍后重试",
                        ),
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext AppResult.Failure(
                        AppError(code = ErrorCode.PARSE, message = "天气服务返回内容为空"),
                    )
                }
                OpenMeteoResponseMapper.parse(
                    body = body,
                    city = city,
                    latitude = latitude,
                    longitude = longitude,
                    updatedAt = timeProvider.now(),
                )
            }
        } catch (timeout: SocketTimeoutException) {
            AppResult.Failure(
                AppError(ErrorCode.TIMEOUT, "天气请求超时，请检查网络后重试", timeout),
            )
        } catch (io: IOException) {
            AppResult.Failure(
                AppError(ErrorCode.NETWORK, "网络连接失败，请检查网络后重试", io),
            )
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            AppResult.Failure(
                AppError(
                    code = ErrorCode.UNKNOWN,
                    message = throwable.message ?: "获取天气失败，请稍后重试",
                    cause = throwable,
                ),
            )
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 8L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val CALL_TIMEOUT_SECONDS = 15L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
