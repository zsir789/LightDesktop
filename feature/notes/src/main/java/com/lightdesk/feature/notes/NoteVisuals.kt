package com.lightdesk.feature.notes

import com.lightdesk.core.designsystem.theme.CourseColors

/**
 * 备忘卡片的视觉派生逻辑。
 *
 * 这里只放**纯函数 / 常量**，不碰 Compose 主题与组合，方便 JVM 单测；
 * 真正的颜色由 `CourseColors` 按这里算出的 seed 生成。
 */
object NotesVisuals {

    /** 按压反馈的轻微缩放系数（0.98，避免“弹跳感”）。 */
    const val PRESS_SCALE: Float = 0.98f

    /** 完成态内容的不透明度：保留色相、降低存在感，但仍保证可读。 */
    const val COMPLETED_CONTENT_ALPHA: Float = 0.62f

    /** 备忘卡片使用的色板大小。 */
    fun paletteSize(): Int = CourseColors.size

    /**
     * 卡片色块的稳定 seed：直接取 id 的 hash，跨会话、跨进程一致，不使用随机数。
     *
     * 交给 `CourseColors.container/accent/onContainer` 后由它内部映射到 12 格 pastel 色板。
     */
    fun seedFor(id: String): Int = id.hashCode()

    /**
     * 由事项 id 推导色板下标（[seedFor] 再按色板大小取模，负数安全）。
     *
     * 仅用于需要“第几格颜色 / 第几格强调色”这种下标的场景；
     * 返回 0..<paletteSize，也可直接作为 `CourseColors` 的 seed 传入。
     */
    fun colorIndexFor(id: String, paletteSize: Int = CourseColors.size): Int {
        if (paletteSize <= 0) return 0
        // kotlin Int.mod 对负数返回非负结果，且不会命中 abs(Int.MIN_VALUE) 的坑
        return id.hashCode().mod(paletteSize)
    }

    /** 按压反馈缩放：按下 0.98，松开回到 1。 */
    fun pressScale(pressed: Boolean): Float = if (pressed) PRESS_SCALE else 1f

    /** 完成态内容透明度：完成后弱化，取消完成后恢复。 */
    fun contentAlpha(done: Boolean): Float = if (done) COMPLETED_CONTENT_ALPHA else 1f
}
