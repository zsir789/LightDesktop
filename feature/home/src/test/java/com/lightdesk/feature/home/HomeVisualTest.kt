package com.lightdesk.feature.home

import com.lightdesk.core.designsystem.theme.CourseColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.0 视觉层纯逻辑单测：待办色块种子、课程/待办状态文案、已结束课程的淡出系数。
 *
 * 颜色本身由 core:designsystem 的 [CourseColors] 决定（此处只验证 seed 选择与文案），
 * 这样首页的“五颜六色但仍稳定”不依赖 UI 测试即可回归。
 */
class HomeVisualTest {

    @Test
    fun `todo seed is the stable hash of the item id`() {
        assertEquals(97, HomePresentation.todoSeed("a"))
        assertEquals(0, HomePresentation.todoSeed(""))
        // 同一 id 多次调用恒定，不同 id 通常不同
        assertEquals(HomePresentation.todoSeed("todo-42"), HomePresentation.todoSeed("todo-42"))
        assertTrue(HomePresentation.todoSeed("todo-1") != HomePresentation.todoSeed("todo-2"))
    }

    @Test
    fun `todo seed always maps inside the course color palette`() {
        val ids = listOf("", "a", "todo-1", "b7f3-4c2a", "中文待办事项", "x".repeat(64))
        ids.forEach { id ->
            val index = CourseColors.indexForSeed(HomePresentation.todoSeed(id))
            assertTrue("seed for '$id' produced index $index", index in 0 until CourseColors.size)
        }
        assertEquals(12, CourseColors.size)
    }

    @Test
    fun `status label covers every course status`() {
        assertEquals("进行中", HomePresentation.statusLabel(CourseStatus.ONGOING))
        assertEquals("即将开始", HomePresentation.statusLabel(CourseStatus.UPCOMING))
        assertEquals("已结束", HomePresentation.statusLabel(CourseStatus.FINISHED))
    }

    @Test
    fun `todo tag prefers overdue and hides for normal todos`() {
        assertEquals("已逾期", HomePresentation.todoTagLabel(overdue = true, urgent = false))
        assertEquals("已逾期", HomePresentation.todoTagLabel(overdue = true, urgent = true))
        assertEquals("1 小时内", HomePresentation.todoTagLabel(overdue = false, urgent = true))
        assertNull(HomePresentation.todoTagLabel(overdue = false, urgent = false))
    }

    @Test
    fun `finished courses are dimmed in the color blocks`() {
        assertEquals(1f, HomePresentation.courseContentAlpha(CourseStatus.ONGOING), 0.0001f)
        assertEquals(1f, HomePresentation.courseContentAlpha(CourseStatus.UPCOMING), 0.0001f)
        assertEquals(0.55f, HomePresentation.courseContentAlpha(CourseStatus.FINISHED), 0.0001f)
    }
}
