package com.cishua.app.vm

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cishua.app.data.Bank
import com.cishua.app.data.BankMeta
import com.cishua.app.data.ProgressStore
import com.cishua.app.data.WordRepository
import com.cishua.app.engine.FeedPlanner
import com.cishua.app.engine.STAGE_INTRO
import com.cishua.app.engine.STAGE_QUIZ
import com.cishua.app.engine.STAGE_SPELL
import com.cishua.app.engine.WordEngine
import com.cishua.app.engine.WordState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

const val MODE_INTRO = 0
const val MODE_QUIZ = 1
const val MODE_SPELL = 2
const val MODE_DONE = 3

const val ANSWER_NONE = 0
const val ANSWER_CORRECT = 1
const val ANSWER_WRONG = 2
const val ANSWER_REVEAL = 3

/** 信息流里的一张卡片 */
data class CardUi(
    val uid: Long,
    val wordKey: String,
    val wordId: Int,
    val mode: Int,
    val word: String,
    val phonetic: String,
    val translation: String,
    val example: String,
    val exampleCn: String,
    val example2: String,
    val example2Cn: String,
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val answerState: Int = ANSWER_NONE,
    val chosen: Int = -1,
    val hint: String? = null,
    val isNew: Boolean = false,
    val seenMarked: Boolean = false,
    val rep: Int = 1
)

data class BankStat(val meta: BankMeta, val seen: Int, val mastered: Int)

/**
 * 信息流主逻辑：加载词库、生成推送队列、处理三个按钮与答题。
 * 所有状态都通过 Compose 的 mutableState 暴露，UI 直接观察。
 */
class FeedViewModel(private val app: Context) {

    private val repo = WordRepository(app)
    private val store = ProgressStore(app)
    private val prefs = app.getSharedPreferences("cishua_prefs", Context.MODE_PRIVATE)
    private val rng = Random(System.currentTimeMillis())

    val banks = mutableStateListOf<BankMeta>()
    val cards = mutableStateListOf<CardUi>()

    var bankId by mutableStateOf(prefs.getString("bank", "cet4") ?: "cet4")
        private set
    var bank by mutableStateOf<Bank?>(null)
        private set
    var sessionId by mutableStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var dailyNew by mutableStateOf(prefs.getInt("daily_new", 30))
        private set
    var quickMode by mutableStateOf(prefs.getBoolean("quick", false))

    /** 翻页时自动朗读单词 */
    var autoSpeak by mutableStateOf(prefs.getBoolean("auto_speak", true))
        private set
    var todayNew by mutableStateOf(0)
        private set
    var todayReview by mutableStateOf(0)
        private set
    var todayMastered by mutableStateOf(0)
        private set
    var totalMastered by mutableStateOf(0)
        private set
    var settingsVisible by mutableStateOf(false)
    var statsVisible by mutableStateOf(false)
        private set

    private var scope: CoroutineScope? = null
    private var states: MutableMap<String, WordState> = HashMap()
    private val recentIds = ArrayDeque<Int>()
    private var meaningPool: List<String> = emptyList()
    private var exhausted = false
    private var uidSeq = 0L
    private var today: String = dayString()

    fun attach(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        loadCurrentBank()
    }

    fun loadCurrentBank() {
        val sc = scope ?: return
        loading = true
        error = null
        sc.launch {
            try {
                val metas = withContext(Dispatchers.IO) { repo.loadBankMetas() }
                if (banks.isEmpty()) banks.addAll(metas)
                val id = bankId
                val loaded = withContext(Dispatchers.IO) { repo.loadBank(id) }
                val savedStates = withContext(Dispatchers.IO) { store.loadAll(id) }
                bank = loaded
                states = savedStates
                meaningPool = loaded.words.map { it.translation }
                startSession()
                loading = false
            } catch (t: Throwable) {
                error = "词库加载失败：${t.message}"
                loading = false
            }
        }
    }

    private fun startSession() {
        today = dayString()
        cards.clear()
        recentIds.clear()
        exhausted = false
        uidSeq = 0
        sessionId++
        todayNew = store.countNewToday(bankId, today)
        todayReview = 0
        todayMastered = 0
        totalMastered = store.masteredCount(bankId)
        ensureCards(3)
    }

    fun switchBank(id: String) {
        if (id == bankId) return
        bankId = id
        prefs.edit().putString("bank", id).apply()
        loadCurrentBank()
    }

    fun changeDailyNew(n: Int) {
        dailyNew = n
        prefs.edit().putInt("daily_new", n).apply()
        if (cards.isEmpty() || (cards.isNotEmpty() && cards.last().mode == MODE_DONE)) {
            exhausted = false
            while (cards.isNotEmpty() && cards.last().mode == MODE_DONE) {
                cards.removeAt(cards.size - 1)
            }
            ensureCards(3)
        }
    }

    fun changeAutoSpeak(on: Boolean) {
        autoSpeak = on
        prefs.edit().putBoolean("auto_speak", on).apply()
    }

    fun changeQuickMode(on: Boolean) {
        quickMode = on
        prefs.edit().putBoolean("quick", on).apply()
    }

    fun resetProgress() {
        store.resetAll()
        states = HashMap()
        todayNew = 0
        todayReview = 0
        todayMastered = 0
        totalMastered = 0
        cards.clear()
        recentIds.clear()
        exhausted = false
        sessionId++
        ensureCards(3)
    }

    fun continueMore() {
        dailyNew += 20
        prefs.edit().putInt("daily_new", dailyNew).apply()
        exhausted = false
        while (cards.isNotEmpty() && cards.last().mode == MODE_DONE) {
            cards.removeAt(cards.size - 1)
        }
        ensureCards(cards.size + 2)
    }

    /** 保证队列里至少有 target+1 张卡片 */
    fun ensureCards(target: Int) {
        val b = bank ?: return
        var guard = 0
        while (cards.size <= target && !exhausted && guard++ < 40) {
            val card = buildNextCard(b)
            if (card == null) {
                exhausted = true
                if (cards.isEmpty() || cards.last().mode != MODE_DONE) cards.add(doneCard())
                break
            }
            cards.add(card)
        }
    }

    private fun newLeft(): Int {
        val count = if (today == dayString()) todayNew else store.countNewToday(bankId, dayString())
        return (dailyNew - count).coerceAtLeast(0)
    }

    private fun buildNextCard(b: Bank): CardUi? {
        val now = System.currentTimeMillis()
        val stateList = b.words.map { states[it.word.lowercase()] ?: WordState() }
        val choice = FeedPlanner.choose(
            states = stateList,
            newLeft = newLeft(),
            recent = recentIds.toSet(),
            now = now,
            rng = rng
        ) ?: return null
        val entry = b.words[choice.index]
        val st = stateList[choice.index]
        val mode = when (st.stage) {
            STAGE_QUIZ -> MODE_QUIZ
            STAGE_SPELL -> MODE_SPELL
            else -> MODE_INTRO
        }
        val quiz = if (mode == MODE_QUIZ) {
            WordEngine.buildQuizOptions(entry.translation, meaningPool, rng)
        } else null
        recentIds.addLast(choice.index)
        while (recentIds.size > 16) recentIds.removeFirst()
        uidSeq++
        return CardUi(
            uid = uidSeq,
            wordKey = entry.word.lowercase(),
            wordId = choice.index,
            mode = mode,
            word = entry.word,
            phonetic = entry.phonetic,
            translation = entry.translation,
            example = entry.example,
            exampleCn = entry.exampleCn,
            example2 = entry.example2,
            example2Cn = entry.example2Cn,
            options = quiz?.options ?: emptyList(),
            correctIndex = quiz?.correctIndex ?: -1,
            isNew = st.seen == 0,
            rep = st.seen + 1
        )
    }

    private fun doneCard(): CardUi {
        uidSeq++
        return CardUi(
            uid = uidSeq,
            wordKey = "__done__",
            wordId = -1,
            mode = MODE_DONE,
            word = "",
            phonetic = "",
            translation = "",
            example = "",
            exampleCn = "",
            example2 = "",
            example2Cn = "",
            seenMarked = true
        )
    }

    /** 卡片真正进入视野时调用：记一次推送，统计新词/复习 */
    fun onPageShown(index: Int) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode == MODE_DONE || c.seenMarked) return
        val key = c.wordKey
        val now = System.currentTimeMillis()
        val day = dayString()
        if (day != today) {
            today = day
            todayNew = store.countNewToday(bankId, day)
            todayReview = 0
            todayMastered = 0
        }
        val s = states[key] ?: WordState()
        val wasNew = s.seen == 0
        val ns = s.copy(
            seen = s.seen + 1,
            firstDay = if (s.firstDay.isEmpty()) day else s.firstDay,
            lastAt = now,
            dueAt = if (s.dueAt == 0L) now + WordEngine.intervalMs(0, quickMode) else s.dueAt
        )
        states[key] = ns
        store.save(bankId, key, ns)
        if (wasNew) todayNew++ else todayReview++
        cards[index] = c.copy(seenMarked = true, isNew = wasNew, rep = ns.seen)
    }

    private fun stateOf(c: CardUi): WordState = states[c.wordKey] ?: WordState()

    // ---- 以下为单元测试入口（internal，不对外暴露）----
    internal fun testState(key: String): WordState = states[key] ?: WordState()

    internal fun testMakeDue(key: String) {
        val s = states[key] ?: return
        states[key] = s.copy(dueAt = 0L)
    }

    internal fun testRebuildQueue() {
        cards.clear()
        recentIds.clear()
        exhausted = false
        sessionId++
        ensureCards(3)
    }

    internal fun testStore(): ProgressStore = store

    private fun applyState(c: CardUi, ns: WordState) {
        states[c.wordKey] = ns
        store.save(bankId, c.wordKey, ns)
    }

    fun onNotFamiliar(index: Int) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode == MODE_DONE) return
        if (c.answerState == ANSWER_CORRECT || c.answerState == ANSWER_WRONG) return
        val s = stateOf(c)
        val ns = WordEngine.onNotFamiliar(s, System.currentTimeMillis(), quickMode)
        applyState(c, ns)
        val hint = when (c.mode) {
            MODE_INTRO -> "已标记「不熟」，推送权重 +，很快再刷到"
            MODE_QUIZ -> "已标记「不熟」，下次回到释义学习"
            else -> "已标记「不熟」，稍后再来一次"
        }
        cards[index] = c.copy(
            answerState = if (c.mode == MODE_INTRO) c.answerState else ANSWER_REVEAL,
            hint = hint
        )
    }

    fun onFamiliar(index: Int) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode != MODE_INTRO) return
        val s = stateOf(c)
        val ns = WordEngine.onFamiliar(s, System.currentTimeMillis(), quickMode)
        applyState(c, ns)
        cards[index] = c.copy(hint = "记住了！下次推送改为「四选一」检验")
    }

    fun onMastered(index: Int) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode == MODE_DONE) return
        val s = stateOf(c)
        if (s.mastered) return
        val ns = WordEngine.onMastered(s, System.currentTimeMillis(), quickMode)
        applyState(c, ns)
        todayMastered++
        totalMastered = store.masteredCount(bankId)
        cards[index] = c.copy(hint = "已标记「熟」，基本不再推送", answerState = ANSWER_REVEAL)
    }

    fun onQuizAnswer(index: Int, choice: Int) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode != MODE_QUIZ || c.answerState != ANSWER_NONE) return
        val correct = choice == c.correctIndex
        val s = stateOf(c)
        val ns = WordEngine.onQuizAnswer(s, System.currentTimeMillis(), correct, quickMode)
        applyState(c, ns)
        val hint = when {
            !correct -> "答错了，正确释义已在下方展开，权重 +"
            ns.stage == STAGE_SPELL -> "连对 3 次！下次推送改为拼写挑战"
            else -> "答对了，记忆曲线已更新"
        }
        cards[index] = c.copy(
            chosen = choice,
            answerState = if (correct) ANSWER_CORRECT else ANSWER_WRONG,
            hint = hint
        )
    }

    fun onSpellSubmit(index: Int, input: String) {
        val c = cards.getOrNull(index) ?: return
        if (c.mode != MODE_SPELL || c.answerState != ANSWER_NONE) return
        val correct = WordEngine.isSpellingMatch(input, c.word)
        val s = stateOf(c)
        val ns = WordEngine.onSpellAnswer(s, System.currentTimeMillis(), correct, quickMode)
        applyState(c, ns)
        if (correct) {
            todayMastered++
            totalMastered = store.masteredCount(bankId)
        }
        cards[index] = c.copy(
            answerState = if (correct) ANSWER_CORRECT else ANSWER_WRONG,
            hint = if (correct) "拼写正确，已标记「熟」" else "拼写有误，正确写法已显示，权重 +"
        )
    }

    fun openSettings() {
        settingsVisible = true
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun openStats() {
        statsVisible = true
    }

    fun closeStats() {
        statsVisible = false
    }

    fun statsSnapshot(): List<BankStat> {
        return banks.map { meta ->
            BankStat(meta, store.seenCount(meta.id), store.masteredByBank()[meta.id] ?: 0)
        }
    }

    fun bankMetaFor(id: String): BankMeta? = banks.firstOrNull { it.id == id }

    private fun dayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
