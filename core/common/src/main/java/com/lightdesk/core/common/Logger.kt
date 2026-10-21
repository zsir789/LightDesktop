package com.lightdesk.core.common

import android.util.Log

/** 轻量日志抽象；方便后续接入文件日志或上报，而不改动业务代码。 */
interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

object LogcatLogger : Logger {
    private const val PREFIX = "LightDesk"

    override fun d(tag: String, message: String) {
        Log.d("$PREFIX/$tag", message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w("$PREFIX/$tag", message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e("$PREFIX/$tag", message, throwable)
    }
}

object NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}

/** 业务层统一通过该对象打日志，后续可替换实现。 */
object AppLogger : Logger by LogcatLogger
