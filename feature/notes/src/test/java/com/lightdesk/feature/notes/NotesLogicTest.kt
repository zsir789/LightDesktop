package com.lightdesk.feature.notes

import com.lightdesk.core.model.NoteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [NotesLogic]/[NotesFormat] 的纯逻辑单测：筛选、排序、倒计时文案、标题校验、时间戳换算。
 */
class NotesLogicTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun atLocal(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDate.of(year, month, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun note(
        id: String,
        deadline: Long? = null,
        done: Boolean = false,
        pinned: Boolean = false,
        createdAt: Long = 0L,
    ) = NoteItem(
        id = id,
        title = "title-$id",
        deadline = deadline,
        done = done,
        pinned = pinned,
        createdAt = createdAt,
    )

    // ---- 筛选与计数 ----

    @Test
    fun `counts split memos todos and completed independently`() {
        val items = listOf(
            note("memo"),
            note("todo", deadline = 1_000L),
            note("doneTodo", deadline = 2_000L, done = true),
            note("doneMemo", done = true),
        )

        assertEquals(4, NotesLogic.count(items, NotesFilter.ALL))
        assertEquals(2, NotesLogic.count(items, NotesFilter.MEMO))
        assertEquals(2, NotesLogic.count(items, NotesFilter.TODO))
        assertEquals(2, NotesLogic.count(items, NotesFilter.COMPLETED))
        assertEquals(
            mapOf(
                NotesFilter.ALL to 4,
                NotesFilter.MEMO to 2,
                NotesFilter.TODO to 2,
                NotesFilter.COMPLETED to 2,
            ),
            NotesLogic.counts(items),
        )
    }

    @Test
    fun `filter keeps only matching items in original order`() {
        val items = listOf(
            note("memo"),
            note("todo", deadline = 1_000L),
            note("doneTodo", deadline = 2_000L, done = true),
            note("doneMemo", done = true),
        )

        assertEquals(listOf("memo", "doneMemo"), NotesLogic.filter(items, NotesFilter.MEMO).map { it.id })
        assertEquals(listOf("todo", "doneTodo"), NotesLogic.filter(items, NotesFilter.TODO).map { it.id })
        assertEquals(listOf("doneTodo", "doneMemo"), NotesLogic.filter(items, NotesFilter.COMPLETED).map { it.id })
        assertEquals(items, NotesLogic.filter(items, NotesFilter.ALL))
    }

    @Test
    fun `visible applies filter before sorting`() {
        val items = listOf(
            note("doneLate", deadline = 9_000L, done = true),
            note("doneEarly", deadline = 500L, done = true),
            note("todo", deadline = 1_000L),
        )

        assertEquals(listOf("doneEarly", "doneLate"), NotesLogic.visible(items, NotesFilter.COMPLETED).map { it.id })
    }

    // ---- 排序 ----

    @Test
    fun `sort puts completed last then pinned then todos by deadline`() {
        val items = listOf(
            note("memoA", createdAt = 100L),
            note("memoPinned", pinned = true, createdAt = 50L),
            note("todoLate", deadline = 5_000L, createdAt = 20L),
            note("todoEarly", deadline = 1_000L, createdAt = 10L),
            note("doneEarly", deadline = 500L, done = true, createdAt = 900L),
            note("doneLate", deadline = 9_000L, done = true, createdAt = 100L),
        )

        assertEquals(
            listOf("memoPinned", "todoEarly", "todoLate", "memoA", "doneEarly", "doneLate"),
            NotesLogic.sort(items).map { it.id },
        )
    }

    @Test
    fun `memos without deadline are ordered by newest first`() {
        val items = listOf(
            note("old", createdAt = 1_000L),
            note("new", createdAt = 2_000L),
        )

        assertEquals(listOf("new", "old"), NotesLogic.sort(items).map { it.id })
    }

    // ---- 倒计时文案 ----

    @Test
    fun `countdown text shows hours and minutes`() {
        val now = atLocal(2025, 5, 1, 10, 0)
        val deadline = now + 2 * 3_600_000L + 13 * 60_000L

        assertEquals("剩余 2小时13分", NotesFormat.countdownText(deadline, now))
    }

    @Test
    fun `countdown text shows days only when exact`() {
        val now = atLocal(2025, 5, 1, 10, 0)

        assertEquals("剩余 3天", NotesFormat.countdownText(now + 3 * 86_400_000L, now))
        assertEquals("剩余 1天2小时", NotesFormat.countdownText(now + 26 * 3_600_000L, now))
    }

    @Test
    fun `countdown text shows overdue`() {
        val now = atLocal(2025, 5, 1, 10, 0)

        assertEquals("已逾期 1小时", NotesFormat.countdownText(now - 3_600_000L, now))
        assertEquals("已逾期 2天3小时", NotesFormat.countdownText(now - 51 * 3_600_000L, now))
    }

    @Test
    fun `countdown text under one minute`() {
        val now = atLocal(2025, 5, 1, 10, 0)

        assertEquals("剩余 不到1分钟", NotesFormat.countdownText(now + 30_000L, now))
        assertEquals("不到1分钟", NotesFormat.coarseDuration(0L))
    }

    // ---- 标题校验 ----

    @Test
    fun `blank title is invalid and filled title is valid`() {
        assertEquals("请输入标题", NotesFormat.validateTitle(""))
        assertEquals("请输入标题", NotesFormat.validateTitle("   "))
        assertEquals("请输入标题", NotesFormat.validateTitle("\n\t"))
        assertNull(NotesFormat.validateTitle("买牛奶"))
    }

    // ---- 默认截止时间 ----

    @Test
    fun `default deadline is today at 2359`() {
        val now = atLocal(2025, 5, 1, 10, 0)

        assertEquals(atLocal(2025, 5, 1, 23, 59), NotesFormat.defaultDeadline(now))
    }

    @Test
    fun `default deadline rolls to tomorrow once 2359 is reached`() {
        val now = atLocal(2025, 5, 1, 23, 59)

        assertEquals(atLocal(2025, 5, 2, 23, 59), NotesFormat.defaultDeadline(now))
    }

    // ---- 日期/时间戳换算 ----

    @Test
    fun `toUtcDateMillis maps local date to utc midnight`() {
        val timestamp = atLocal(2025, 5, 1, 18, 30)

        assertEquals(
            Instant.parse("2025-05-01T00:00:00Z").toEpochMilli(),
            NotesFormat.toUtcDateMillis(timestamp),
        )
    }

    @Test
    fun `combineDateAndTime uses local zone`() {
        val utcDateMillis = Instant.parse("2025-05-03T00:00:00Z").toEpochMilli()

        assertEquals(atLocal(2025, 5, 3, 8, 5), NotesFormat.combineDateAndTime(utcDateMillis, 8, 5))
    }

    @Test
    fun `withDate keeps original time`() {
        val deadline = atLocal(2025, 5, 1, 18, 30)
        val utcDateMillis = Instant.parse("2025-05-03T00:00:00Z").toEpochMilli()

        assertEquals(atLocal(2025, 5, 3, 18, 30), NotesFormat.withDate(deadline, utcDateMillis))
    }

    @Test
    fun `withTime keeps original date`() {
        val deadline = atLocal(2025, 5, 1, 18, 30)

        assertEquals(atLocal(2025, 5, 1, 8, 5), NotesFormat.withTime(deadline, 8, 5))
    }

    @Test
    fun `display texts match list and editor formats`() {
        val deadline = atLocal(2025, 5, 1, 18, 30)

        assertEquals("05月01日 18:30", NotesFormat.deadlineDateTimeText(deadline))
        assertEquals("2025年05月01日", NotesFormat.editorDateText(deadline))
        assertEquals("18:30", NotesFormat.editorTimeText(deadline))
        assertEquals("未设置", NotesFormat.editorDateText(null))
        assertEquals("未设置", NotesFormat.editorTimeText(null))
        assertEquals(18, NotesFormat.hourOf(deadline))
        assertEquals(30, NotesFormat.minuteOf(deadline))
    }
}
