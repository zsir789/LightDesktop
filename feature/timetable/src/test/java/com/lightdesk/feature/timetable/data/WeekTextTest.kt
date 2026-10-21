package com.lightdesk.feature.timetable.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTextTest {

    @Test
    fun `empty weeks means every week`() {
        assertEquals("每周", WeekText.format(emptyList()))
        assertEquals("每周", WeekText.format(listOf(0, -1)))
    }

    @Test
    fun `formats single week`() {
        assertEquals("第4周", WeekText.format(listOf(4)))
    }

    @Test
    fun `compresses consecutive weeks into ranges`() {
        assertEquals("第1-3、5周", WeekText.format(listOf(1, 2, 3, 5)))
        assertEquals("第1、3-5、8周", WeekText.format(listOf(1, 3, 4, 5, 8)))
    }

    @Test
    fun `sorts and removes duplicates before formatting`() {
        assertEquals("第1-2周", WeekText.format(listOf(2, 1, 2)))
    }
}
