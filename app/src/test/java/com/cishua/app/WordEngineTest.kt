package com.cishua.app

import com.cishua.app.engine.STAGE_INTRO
import com.cishua.app.engine.STAGE_QUIZ
import com.cishua.app.engine.STAGE_SPELL
import com.cishua.app.engine.WordEngine
import com.cishua.app.engine.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 引擎单元测试：推送权重、阶段流转、四选一选项、拼写校验 */
class WordEngineTest {

    private val now = 1_700_000_000_000L

    @Test
    fun newWordStartsAsIntro() {
        val s = WordState()
        assertEquals(STAGE_INTRO, s.stage)
        assertEquals(WordEngine.DEFAULT_WEIGHT, s.weight)
        assertFalse(s.mastered)
    }

    @Test
    fun notFamiliarRaisesWeightAndComesBackQuickly() {
        val s = WordState()
        val n = WordEngine.onNotFamiliar(s, now)
        assertTrue("权重应上升: ${s.weight} -> ${n.weight}", n.weight > s.weight)
        assertTrue("应很快再次到期", n.dueAt - now <= WordEngine.intervalMs(0, false) / 2)
        assertEquals(STAGE_INTRO, n.stage)
    }

    @Test
    fun notFamiliarFromQuizReturnsToIntro() {
        val s = WordState(stage = STAGE_QUIZ, correct = 2)
        val n = WordEngine.onNotFamiliar(s, now)
        assertEquals(STAGE_INTRO, n.stage)
        assertEquals(0, n.correct)
    }

    @Test
    fun weightIsCapped() {
        var s = WordState(weight = 95)
        repeat(6) { s = WordEngine.onNotFamiliar(s, now) }
        assertEquals(WordEngine.WEIGHT_MAX, s.weight)
    }

    @Test
    fun familiarSwitchesToQuizAndLowersWeight() {
        val s = WordState(weight = 30)
        val n = WordEngine.onFamiliar(s, now)
        assertEquals(STAGE_QUIZ, n.stage)
        assertTrue(n.weight < s.weight)
        assertTrue(n.dueAt > now)
    }

    @Test
    fun masteredIsPushedFarAway() {
        val n = WordEngine.onMastered(WordState(), now)
        assertTrue(n.mastered)
        assertEquals(WordEngine.WEIGHT_MIN, n.weight)
        assertTrue("至少 20 天后才可能再出现", n.dueAt - now >= 20L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun threeCorrectQuizAnswersEnterSpellStage() {
        var s = WordState(stage = STAGE_QUIZ)
        s = WordEngine.onQuizAnswer(s, now, true)
        assertEquals(STAGE_QUIZ, s.stage)
        assertEquals(1, s.correct)
        s = WordEngine.onQuizAnswer(s, now, true)
        assertEquals(2, s.correct)
        s = WordEngine.onQuizAnswer(s, now, true)
        assertEquals("连对三次进入拼写", STAGE_SPELL, s.stage)
        assertEquals(0, s.correct)
    }

    @Test
    fun wrongQuizAnswerRaisesWeightAndResetsProgress() {
        var s = WordState(stage = STAGE_QUIZ, correct = 2, level = 2, weight = 10)
        s = WordEngine.onQuizAnswer(s, now, false)
        assertEquals(STAGE_QUIZ, s.stage)
        assertEquals(0, s.correct)
        assertEquals(1, s.wrong)
        assertTrue(s.weight >= 30)
        assertTrue("答错后很快再推", s.dueAt - now <= WordEngine.intervalMs(0, false))
    }

    @Test
    fun correctSpellingMastersWord() {
        val s = WordEngine.onSpellAnswer(WordState(stage = STAGE_SPELL), now, true)
        assertTrue(s.mastered)
    }

    @Test
    fun wrongSpellingKeepsSpellStageAndRaisesWeight() {
        val s = WordEngine.onSpellAnswer(WordState(stage = STAGE_SPELL, weight = 20), now, false)
        assertEquals(STAGE_SPELL, s.stage)
        assertTrue(s.weight > 20)
        assertEquals(1, s.wrong)
        assertFalse(s.mastered)
    }

    @Test
    fun memoryCurveIntervalsGrow() {
        val intervals = (0..8).map { WordEngine.intervalMs(it, false) }
        for (i in 1 until intervals.size) {
            assertTrue("间隔应递增: $intervals", intervals[i] > intervals[i - 1])
        }
        assertEquals("第一档是 10 分钟", 10 * 60_000L, intervals[0])
        assertEquals("最后一档是 30 天", 30L * 24 * 60 * 60_000L, intervals[8])
    }

    @Test
    fun quickModeCompressesInterval() {
        assertTrue(WordEngine.intervalMs(4, true) < WordEngine.intervalMs(4, false))
    }

    @Test
    fun quizOptionsAreFourUniqueWithCorrectAnswer() {
        val pool = listOf(
            "n. 苹果", "v. 取消", "adj. 长的", "n. 香蕉", "v. 跑",
            "n. 桌子", "adv. 快速地", "n. 电脑", "v. 记住", "n. 天气"
        )
        val correct = "v. 取消，撤销"
        repeat(60) { seed ->
            val opts = WordEngine.buildQuizOptions(correct, pool, Random(seed))
            assertEquals(4, opts.options.size)
            assertEquals("选项不能重复：${opts.options}", 4, opts.options.distinct().size)
            assertEquals(WordEngine.shortMeaning(correct), opts.options[opts.correctIndex])
        }
    }

    @Test
    fun shortMeaningTruncatesLongText() {
        val long = "n. 一个非常非常长的释义内容用来测试截断逻辑是否正常工作，并且还要继续更长一些"
        val short = WordEngine.shortMeaning(long)
        assertTrue(short.length <= 26)
    }

    @Test
    fun spellingMatchIgnoresCaseAndSpaces() {
        assertTrue(WordEngine.isSpellingMatch(" Cancel ", "cancel"))
        assertTrue(WordEngine.isSpellingMatch("CANCEL", "cancel"))
        assertFalse(WordEngine.isSpellingMatch("cancell", "cancel"))
        assertFalse(WordEngine.isSpellingMatch("", "cancel"))
    }

    @Test
    fun spellHintRevealsOnlyFirstLetter() {
        assertEquals("c_______", WordEngine.spellHint("computer"))
        assertNotEquals("computer", WordEngine.spellHint("computer"))
    }
}
