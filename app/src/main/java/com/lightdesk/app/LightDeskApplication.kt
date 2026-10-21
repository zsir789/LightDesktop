package com.lightdesk.app

import android.app.Application
import com.lightdesk.app.di.AppContainer
import com.lightdesk.core.common.AppLogger

/**
 * 应用入口：只负责创建依赖容器，不做业务逻辑。
 * 依赖容器可替换（例如接入 Hilt），其余代码不受影响。
 */
class LightDeskApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = try {
            AppContainer(this)
        } catch (throwable: Throwable) {
            AppLogger.e("LightDeskApplication", "依赖容器初始化失败", throwable)
            throw throwable
        }
    }
}
