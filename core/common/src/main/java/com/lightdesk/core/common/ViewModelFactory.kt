package com.lightdesk.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 简单的 ViewModel 工厂：功能模块自行组装依赖，不依赖 DI 框架，
 * 后续可无痛替换为 Hilt/Koin 等方案。
 */
inline fun <reified VM : ViewModel> simpleViewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(VM::class.java)) {
            "ViewModel $modelClass 与工厂创建的 ${VM::class.java.simpleName} 不匹配"
        }
        return create() as T
    }
}
