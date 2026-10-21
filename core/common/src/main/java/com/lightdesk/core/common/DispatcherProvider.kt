package com.lightdesk.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 线程调度抽象。业务代码不直接使用 [Dispatchers]，方便测试替换。
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val computation: CoroutineDispatcher
    val main: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val computation: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}
