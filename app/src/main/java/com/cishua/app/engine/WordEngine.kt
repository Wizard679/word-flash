package com.cishua.app.engine

import kotlin.math.min
import kotlin.random.Random

const val STAGE_INTRO = 0
const val STAGE_QUIZ = 1
const val STAGE_SPELL = 2

/** 单词在一个词库中的学习状态 */
data class WordState(
    val stage: Int = STAGE_INTRO,
    val weight: Int = 10,
    val correct: Int = 0,
    val wrong: Int = 0,
    val mastered: Boolean = false,
    val level: Int = 0,
    val dueAt: Long = 0L,
    val seen: Int = 0,
    val firstDay: String = "",
    val lastAt: Long = 0L
)

data class QuizOptions(val options: List<String>, val correctIndex: Int)

/**
 * 单词状态机 + 记忆曲线。
 * 规则：
 *  - 首次推送：展示释义与例句（INTRO）
 *  - 点倒三角（熟）：下次推送变为四选一（QUIZ）
 *  - 四选一连对 3 次：进入拼写（SPELL），听音写词
 *  - 拼写正确：标记“熟”（mastered），基本不再推送
 *  - 任何环节答错 / 点正三角（不熟）：推送权重上升，更快再次出现
 */
object WordEngine {

    /** 四选一连对多少次后进入拼写 */
    const val QUIZ_TO_SPELL = 3
    const val WEIGHT_MIN = 2
    const val WEIGHT_MAX = 100
    const val DEFAULT_WEIGHT = 10

    /** 记忆曲线（分钟）：10分钟、1小时、6小时、1天、2天、4天、7天、15天、30天 */
    val CURVE_MINUTES = longArrayOf(10, 60, 360, 1440, 2880, 5760, 10080, 21600, 43200)

    /** 熟练后仍保留极低概率的抽查间隔（天） */
    const val MASTERED_REVIEW_DAYS = 30

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE

    /** 标记不熟 / 答错后，多久再推一次 */
    fun retryDelayMs(quickMode: Boolean): Long = if (quickMode) 15_000L else 60_000L

    fun intervalMs(level: Int, quickMode: Boolean): Long {
        val idx = level.coerceIn(0, CURVE_MINUTES.size - 1)
        val minutes = CURVE_MINUTES[idx]
        // 速记模式把间隔压缩到 1/20，方便短时间内验证全流程
        val m = if (quickMode) maxOf(1L, minutes / 20) else minutes
        return m * MINUTE
    }

    /** 点“正三角”（不熟）：权重升高，很快重新推送；四选一/拼写阶段回退到释义展示 */
    fun onNotFamiliar(s: WordState, now: Long, quickMode: Boolean = false): WordState {
        val backToIntro = s.stage != STAGE_INTRO
        return s.copy(
            stage = STAGE_INTRO,
            weight = min(WEIGHT_MAX, s.weight + if (backToIntro) 26 else 18),
            correct = 0,
            level = 0,
            dueAt = now + retryDelayMs(quickMode),
            lastAt = now
        )
    }

    /** 点“倒三角”（熟）：降低权重，并把下一次推送切换成四选一 */
    fun onFamiliar(s: WordState, now: Long, quickMode: Boolean = false): WordState {
        return s.copy(
            stage = STAGE_QUIZ,
            weight = maxOf(WEIGHT_MIN, s.weight - 12),
            correct = 0,
            level = 0,
            dueAt = now + 45_000L,
            lastAt = now
        )
    }

    /** 点“熟”：已掌握，基本不再推送 */
    fun onMastered(s: WordState, now: Long, quickMode: Boolean = false): WordState {
        return s.copy(
            stage = STAGE_SPELL,
            mastered = true,
            weight = WEIGHT_MIN,
            correct = 0,
            level = CURVE_MINUTES.size - 1,
            dueAt = now + MASTERED_REVIEW_DAYS * DAY,
            lastAt = now
        )
    }

    /** 四选一作答 */
    fun onQuizAnswer(s: WordState, now: Long, isCorrect: Boolean, quickMode: Boolean = false): WordState {
        return if (isCorrect) {
            val nextCorrect = s.correct + 1
            val toSpell = nextCorrect >= QUIZ_TO_SPELL
            val nextLevel = s.level + 1
            s.copy(
                stage = if (toSpell) STAGE_SPELL else STAGE_QUIZ,
                correct = if (toSpell) 0 else nextCorrect,
                level = nextLevel,
                weight = maxOf(WEIGHT_MIN, s.weight - 6),
                dueAt = now + if (toSpell) intervalMs(1, quickMode) else intervalMs(nextLevel, quickMode),
                lastAt = now
            )
        } else {
            s.copy(
                stage = STAGE_QUIZ,
                correct = 0,
                level = maxOf(0, s.level - 1),
                wrong = s.wrong + 1,
                weight = min(WEIGHT_MAX, s.weight + 22),
                dueAt = now + retryDelayMs(quickMode),
                lastAt = now
            )
        }
    }

    /** 拼写作答 */
    fun onSpellAnswer(s: WordState, now: Long, isCorrect: Boolean, quickMode: Boolean = false): WordState {
        return if (isCorrect) {
            onMastered(s, now, quickMode)
        } else {
            s.copy(
                stage = STAGE_SPELL,
                wrong = s.wrong + 1,
                correct = 0,
                weight = min(WEIGHT_MAX, s.weight + 25),
                dueAt = now + retryDelayMs(quickMode),
                lastAt = now
            )
        }
    }

    /** 释义缩略：用于四选一选项，避免过长 */
    fun shortMeaning(translation: String): String {
        val clean = translation.replace('\u3000', ' ').trim()
        if (clean.isEmpty()) return translation
        val parts = clean.split('；', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return clean
        val joined = if (parts.size >= 2) parts.take(2).joinToString("；") else parts[0]
        return if (joined.length <= 26) joined else joined.substring(0, 25) + "…"
    }

    /**
     * 生成四选一选项：1 个正确释义 + 3 个干扰释义，顺序随机。
     * @param correct 正确释义原文
     * @param pool 其他单词的释义原文
     */
    fun buildQuizOptions(
        correct: String,
        pool: List<String>,
        rng: Random,
        optionCount: Int = 4
    ): QuizOptions {
        val correctText = shortMeaning(correct)
        val used = HashSet<String>()
        used.add(correctText)
        val distractors = ArrayList<String>(optionCount - 1)
        val shuffled = pool.shuffled(rng)
        for (raw in shuffled) {
            val m = shortMeaning(raw)
            if (m.length < 2) continue
            if (!used.add(m)) continue
            // 开头两个字相同容易造成歧义，直接跳过
            if (correctText.length >= 2 && m.length >= 2 && m.take(2) == correctText.take(2)) continue
            distractors.add(m)
            if (distractors.size >= optionCount - 1) break
        }
        // 干扰项不足时允许放宽相似度限制
        if (distractors.size < optionCount - 1) {
            for (raw in shuffled) {
                val m = shortMeaning(raw)
                if (m.length < 2 || m in distractors || m == correctText) continue
                distractors.add(m)
                if (distractors.size >= optionCount - 1) break
            }
        }
        val all = (distractors + correctText).toMutableList()
        // 保证正确答案一定在列表中
        val shuffledAll = all.shuffled(rng)
        return QuizOptions(shuffledAll, shuffledAll.indexOf(correctText))
    }

    /** 拼写比对：忽略大小写、首尾空格与连字符差异 */
    fun isSpellingMatch(input: String, word: String): Boolean {
        fun norm(s: String) = s.trim().lowercase().replace(" ", "").replace("-", "").replace("’", "'")
        return norm(input) == norm(word) && input.trim().isNotEmpty()
    }

    /** 拼写提示：首字母 + 下划线 */
    fun spellHint(word: String): String {
        if (word.length <= 1) return word
        val sb = StringBuilder()
        word.forEachIndexed { i, c ->
            if (i == 0) sb.append(c)
            else if (c == ' ' || c == '-' || c == '\'' || c == '’') sb.append(c)
            else sb.append('_')
        }
        return sb.toString()
    }
}

/** 信息流推送规划：按权重 + 记忆曲线到期时间挑选下一个单词 */
object FeedPlanner {

    data class Choice(val index: Int, val isNew: Boolean)

    fun dueScore(s: WordState, now: Long): Double {
        val overdueHours = ((now - s.dueAt).toDouble() / 3_600_000.0).coerceAtLeast(0.0)
        return 2.0 + s.weight + min(overdueHours, 72.0) * 0.6
    }

    fun choose(
        states: List<WordState>,
        newLeft: Int,
        recent: Set<Int>,
        now: Long,
        rng: Random,
        dueBias: Double = 0.6
    ): Choice? {
        val duePool = ArrayList<Int>()
        val dueWeights = ArrayList<Double>()
        val newPool = ArrayList<Int>()
        for (i in states.indices) {
            if (i in recent) continue
            val s = states[i]
            if (s.mastered) {
                if (s.dueAt <= now) {
                    duePool.add(i)
                    dueWeights.add(dueScore(s, now) * 0.2)
                }
                continue
            }
            if (s.seen == 0) {
                newPool.add(i)
            } else if (s.dueAt <= now) {
                duePool.add(i)
                dueWeights.add(dueScore(s, now))
            }
        }
        if (duePool.isEmpty() && newPool.isEmpty()) return null
        val allowNew = newLeft > 0 && newPool.isNotEmpty()
        val useDue = if (!allowNew) duePool.isNotEmpty()
        else if (duePool.isEmpty()) false
        else rng.nextDouble() < dueBias

        if (useDue) {
            val total = dueWeights.sum()
            var r = rng.nextDouble() * total
            for (i in duePool.indices) {
                r -= dueWeights[i]
                if (r <= 0) return Choice(duePool[i], false)
            }
            return Choice(duePool.last(), false)
        }
        if (allowNew && newPool.isNotEmpty()) {
            return Choice(newPool[rng.nextInt(newPool.size)], true)
        }
        return if (duePool.isNotEmpty()) Choice(duePool[rng.nextInt(duePool.size)], false) else null
    }
}
