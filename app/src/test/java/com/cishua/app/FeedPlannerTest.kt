package com.cishua.app

import com.cishua.app.engine.FeedPlanner
import com.cishua.app.engine.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 信息流推送规划测试 */
class FeedPlannerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun returnsNullWhenNothingAvailable() {
        val states = listOf(WordState(seen = 3, dueAt = now + 3_600_000L))
        assertNull(FeedPlanner.choose(states, 10, emptySet(), now, Random(0)))
    }

    @Test
    fun picksNewWordWhenNothingDue() {
        val states = listOf(WordState(), WordState(), WordState())
        val choice = FeedPlanner.choose(states, 10, emptySet(), now, Random(1))
        assertNotNull(choice)
        assertTrue(choice!!.isNew)
    }

    @Test
    fun respectsDailyNewQuota() {
        val states = listOf(WordState())
        assertNull("新词额度用完时不应再推新词", FeedPlanner.choose(states, 0, emptySet(), now, Random(0)))
    }

    @Test
    fun excludesRecentlyShownWords() {
        val states = listOf(WordState(), WordState())
        repeat(30) { seed ->
            val choice = FeedPlanner.choose(states, 10, setOf(0), now, Random(seed))
            assertEquals(1, choice?.index)
        }
    }

    @Test
    fun neverPicksMasteredWordsBeforeTheirReviewTime() {
        val states = listOf(
            WordState(mastered = true, seen = 4, dueAt = now + 86_400_000L),
            WordState()
        )
        repeat(20) { seed ->
            assertEquals(1, FeedPlanner.choose(states, 10, emptySet(), now, Random(seed))?.index)
        }
    }

    @Test
    fun highWeightDueWordIsPushedMoreOften() {
        val states = listOf(
            WordState(seen = 5, weight = 92, dueAt = now - 3_600_000L),
            WordState(seen = 5, weight = 4, dueAt = now - 3_600_000L),
            WordState(seen = 5, weight = 10, dueAt = now + 60_000L)
        )
        var high = 0
        var low = 0
        repeat(400) { seed ->
            when (FeedPlanner.choose(states, 0, emptySet(), now, Random(seed), dueBias = 1.0)?.index) {
                0 -> high++
                1 -> low++
            }
        }
        assertTrue("权重高的词应明显更常出现：high=$high low=$low", high > low * 2)
    }

    @Test
    fun overdueWordBeatsLowWeightWord() {
        val states = listOf(
            WordState(seen = 2, weight = 10, dueAt = now - 86_400_000L),
            WordState(seen = 2, weight = 10, dueAt = now - 60_000L)
        )
        var first = 0
        var second = 0
        repeat(400) { seed ->
            when (FeedPlanner.choose(states, 0, emptySet(), now, Random(seed), dueBias = 1.0)?.index) {
                0 -> first++
                1 -> second++
            }
        }
        assertTrue("逾期更久的词应更常出现：first=$first second=$second", first > second)
    }
}
