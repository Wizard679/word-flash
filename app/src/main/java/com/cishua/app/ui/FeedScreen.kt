package com.cishua.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cishua.app.engine.WordEngine
import com.cishua.app.vm.ANSWER_CORRECT
import com.cishua.app.vm.ANSWER_NONE
import com.cishua.app.vm.ANSWER_REVEAL
import com.cishua.app.vm.ANSWER_WRONG
import com.cishua.app.vm.CardUi
import com.cishua.app.vm.FeedViewModel
import com.cishua.app.vm.MODE_DONE
import com.cishua.app.vm.MODE_INTRO
import com.cishua.app.vm.MODE_QUIZ
import com.cishua.app.vm.MODE_SPELL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ACCENTS = listOf(
    Color(0xFFFF2C55),
    Color(0xFF22E0B4),
    Color(0xFF7A5CFF),
    Color(0xFF1FB6FF)
)

fun accentFor(seed: Int): Color = ACCENTS[abs(seed) % ACCENTS.size]

@Composable
fun AppRoot(vm: FeedViewModel, speaker: Speaker) {
    Box(Modifier.fillMaxSize().background(AppColors.Bg)) {
        when {
            vm.loading -> LoadingScreen()
            vm.error != null -> ErrorScreen(vm.error!!)
            else -> FeedContent(vm, speaker)
        }
        if (vm.settingsVisible) SettingsPanel(vm)
        if (vm.statsVisible) StatsPanel(vm)
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("词刷", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text("刷着刷着，单词就记住了", fontSize = 14.sp, color = AppColors.TextTertiary)
        Spacer(Modifier.height(20.dp))
        Text("正在加载词库…", fontSize = 12.sp, color = AppColors.TextTertiary)
    }
}

@Composable
private fun ErrorScreen(msg: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("加载失败", fontSize = 20.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(msg, fontSize = 13.sp, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedContent(vm: FeedViewModel, speaker: Speaker) {
    key(vm.sessionId) {
        val pageCount = maxOf(1, vm.cards.size)
        val pagerState = rememberPagerState(pageCount = { maxOf(1, vm.cards.size) })
        val scope = rememberCoroutineScope()
        // 点按钮 / 答完题后自动滑到下一张卡
        val advanceTo: (Int) -> Unit = { from ->
            val target = from + 1
            vm.ensureCards(target + 1)
            if (target < vm.cards.size) {
                scope.launch { pagerState.animateScrollToPage(target) }
            }
        }
        LaunchedEffect(pagerState.currentPage, vm.cards.size) {
            vm.ensureCards(pagerState.currentPage + 2)
            vm.onPageShown(pagerState.currentPage)
        }
        // 翻页自动朗读（拼写卡自己会读，避免重复）
        LaunchedEffect(pagerState.settledPage, vm.autoSpeak) {
            val card = vm.cards.getOrNull(pagerState.settledPage)
            if (vm.autoSpeak && card != null && card.mode != MODE_SPELL && card.mode != MODE_DONE) {
                speaker.speak(card.word)
            }
        }
        // 答题后留一点时间看正确释义，再自动翻页
        val currentCard = vm.cards.getOrNull(pagerState.currentPage)
        LaunchedEffect(currentCard?.uid, currentCard?.answerState) {
            val card = currentCard ?: return@LaunchedEffect
            if (card.mode != MODE_QUIZ && card.mode != MODE_SPELL) return@LaunchedEffect
            if (card.answerState == ANSWER_NONE) return@LaunchedEffect
            val from = pagerState.currentPage
            delay(if (card.answerState == ANSWER_CORRECT) 900L else 2100L)
            if (pagerState.currentPage == from) advanceTo(from)
        }
        Box(Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1
            ) { page ->
                val card = vm.cards.getOrNull(page)
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val alpha = (1f - abs(offset) * 0.35f).coerceIn(0f, 1f)
                val scale = (1f - abs(offset) * 0.07f).coerceIn(0.8f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    if (card == null) {
                        EmptyCard()
                    } else {
                        CardPage(
                            card = card,
                            page = page,
                            currentPage = pagerState.currentPage,
                            vm = vm,
                            speaker = speaker
                        )
                    }
                }
            }
            TopBar(vm)
            val current = vm.cards.getOrNull(pagerState.currentPage)
            if (current != null && current.mode != MODE_DONE) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    val actionable = current.hint == null
                    ActionBar(
                        enabled = actionable,
                        showFamiliar = current.mode == MODE_INTRO,
                        onNotFamiliar = {
                            if (actionable) {
                                vm.onNotFamiliar(pagerState.currentPage)
                                advanceTo(pagerState.currentPage)
                            }
                        },
                        onFamiliar = {
                            if (actionable) {
                                vm.onFamiliar(pagerState.currentPage)
                                advanceTo(pagerState.currentPage)
                            }
                        },
                        onMastered = {
                            if (actionable) {
                                vm.onMastered(pagerState.currentPage)
                                advanceTo(pagerState.currentPage)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("准备中…", color = AppColors.TextTertiary, fontSize = 13.sp)
    }
}

@Composable
private fun TopBar(vm: FeedViewModel) {
    val accent = accentFor(vm.cards.getOrNull(0)?.wordId ?: 0)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            vm.banks.forEach { meta ->
                BankChip(meta.name, meta.id == vm.bankId) { vm.switchBank(meta.id) }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            SmallPill("统计", onClick = { vm.openStats() })
            Spacer(Modifier.width(6.dp))
            SmallPill("设置", onClick = { vm.openSettings() })
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinProgress(
                fraction = vm.todayNew.toFloat() / vm.dailyNew.coerceAtLeast(1),
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text("今日 ${vm.todayNew}/${vm.dailyNew}", fontSize = 10.5.sp, color = AppColors.TextTertiary)
            Spacer(Modifier.width(8.dp))
            Text("熟练 ${vm.totalMastered}", fontSize = 10.5.sp, color = AppColors.TextTertiary)
        }
    }
}

@Composable
private fun CardPage(card: CardUi, page: Int, currentPage: Int, vm: FeedViewModel, speaker: Speaker) {
    val accent = accentFor(card.wordId)
    val isActive = page == currentPage
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0C12), accent.copy(alpha = 0.10f), Color(0xFF08080C)),
                    startY = 0f,
                    endY = 1800f
                )
            )
    ) {
        // 顶部柔光
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(1050f, 240f),
                        radius = 1050f
                    )
                )
        )

        when (card.mode) {
            MODE_QUIZ -> QuizCard(card, page, vm, accent)
            MODE_SPELL -> SpellCard(card, page, vm, speaker, accent, isActive)
            MODE_DONE -> DoneCard(vm, accent)
            else -> IntroCard(card, page, vm, speaker, accent)
        }

        if (card.hint != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp, start = 24.dp, end = 24.dp)
            ) {
                HintPill(card.hint, accent)
            }
        }
    }
}

@Composable
private fun IntroCard(card: CardUi, page: Int, vm: FeedViewModel, speaker: Speaker, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 104.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StatusChip(
            text = if (card.isNew) "新词 · 首次推送" else "第 ${card.rep} 次推送 · 复习",
            accent = accent
        )
        Spacer(Modifier.height(20.dp))
        val wordSize = when {
            card.word.length >= 14 -> 30.sp
            card.word.length >= 11 -> 36.sp
            card.word.length >= 8 -> 42.sp
            else -> 48.sp
        }
        Text(
            text = card.word,
            fontSize = wordSize,
            lineHeight = (wordSize.value * 1.2f).sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            letterSpacing = 0.4.sp,
            modifier = Modifier.clickable { speaker.speak(card.word) }
        )
        if (card.phonetic.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "/ ${card.phonetic} /",
                    fontSize = 15.sp,
                    color = AppColors.TextSecondary,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.width(10.dp))
                SpeakButton("发音", accent) { speaker.speak(card.word) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = card.translation,
            fontSize = 19.sp,
            lineHeight = 29.sp,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
        if (card.example.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            ExampleBlock(card.example, card.exampleCn, card.example2, card.example2Cn, accent)
        }
    }
}

@Composable
private fun ExampleBlock(en: String, cn: String, en2: String, cn2: String, accent: Color) {
    GlassCard(Modifier.fillMaxWidth(), alpha = 0.06f) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent.copy(alpha = 0.7f))
            )
            Column(Modifier.padding(14.dp)) {
                Text("例句", fontSize = 10.5.sp, color = accent, letterSpacing = 1.sp)
                Spacer(Modifier.height(7.dp))
                Text(en, fontSize = 14.5.sp, lineHeight = 21.sp, color = AppColors.TextPrimary)
                if (cn.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(cn, fontSize = 12.5.sp, lineHeight = 18.sp, color = AppColors.TextTertiary)
                }
                if (en2.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(en2, fontSize = 13.5.sp, lineHeight = 20.sp, color = AppColors.TextSecondary)
                    if (cn2.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(cn2, fontSize = 12.sp, lineHeight = 17.sp, color = AppColors.TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizCard(card: CardUi, page: Int, vm: FeedViewModel, accent: Color) {
    val answered = card.answerState != ANSWER_NONE
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 100.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StatusChip("四选一 · 选出正确的中文释义", accent)
        Spacer(Modifier.height(18.dp))
        Text(
            card.word,
            fontSize = if (card.word.length >= 11) 34.sp else 42.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            letterSpacing = 0.4.sp
        )
        if (card.phonetic.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("/ ${card.phonetic} /", fontSize = 14.sp, color = AppColors.TextTertiary)
        }
        Spacer(Modifier.height(22.dp))
        card.options.forEachIndexed { i, opt ->
            val state = when {
                !answered -> 0
                i == card.correctIndex -> 1
                i == card.chosen -> 2
                else -> 3
            }
            OptionRow(
                letter = ('A' + i).toString(),
                text = opt,
                state = state,
                accent = accent,
                enabled = !answered
            ) { vm.onQuizAnswer(page, i) }
            Spacer(Modifier.height(10.dp))
        }
        if (answered) {
            Spacer(Modifier.height(6.dp))
            AnswerPanel(card, accent)
        }
    }
}

@Composable
private fun OptionRow(
    letter: String,
    text: String,
    state: Int,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when (state) {
        1 -> AppColors.Right.copy(alpha = 0.20f)
        2 -> AppColors.Wrong.copy(alpha = 0.20f)
        3 -> Color.White.copy(alpha = 0.04f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val border = when (state) {
        1 -> AppColors.Right.copy(alpha = 0.8f)
        2 -> AppColors.Wrong.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val letterColor = when (state) {
        1 -> AppColors.Right
        2 -> AppColors.Wrong
        else -> accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(letterColor.copy(alpha = 0.16f))
                .border(1.dp, letterColor.copy(alpha = 0.4f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, fontSize = 11.sp, color = letterColor, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            color = if (state == 3) AppColors.TextTertiary else AppColors.TextPrimary
        )
    }
}

@Composable
private fun AnswerPanel(card: CardUi, accent: Color) {
    GlassCard(Modifier.fillMaxWidth(), alpha = 0.07f) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (card.answerState == ANSWER_CORRECT) "答对了" else "正确释义",
                fontSize = 11.sp,
                color = if (card.answerState == ANSWER_CORRECT) AppColors.Right else accent,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(card.translation, fontSize = 15.sp, lineHeight = 22.sp, color = AppColors.TextPrimary)
            if (card.example.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(card.example, fontSize = 12.5.sp, lineHeight = 18.sp, color = AppColors.TextSecondary)
                if (card.exampleCn.isNotEmpty()) {
                    Text(card.exampleCn, fontSize = 11.5.sp, lineHeight = 16.sp, color = AppColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun SpellCard(
    card: CardUi,
    page: Int,
    vm: FeedViewModel,
    speaker: Speaker,
    accent: Color,
    isActive: Boolean
) {
    var input by remember(card.uid) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val answered = card.answerState != ANSWER_NONE

    LaunchedEffect(isActive, card.uid) {
        if (isActive && !answered) {
            speaker.speak(card.word)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 100.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StatusChip("拼写挑战 · 听发音写单词", accent)
        Spacer(Modifier.height(16.dp))
        if (card.phonetic.isNotEmpty()) {
            Text(
                "/ ${card.phonetic} /",
                fontSize = 20.sp,
                color = AppColors.TextPrimary,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
        }
        SpeakButton("播放发音", accent) { speaker.speak(card.word) }
        Spacer(Modifier.height(18.dp))
        Text("释义", fontSize = 10.5.sp, color = AppColors.TextTertiary, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(card.translation, fontSize = 16.sp, lineHeight = 24.sp, color = AppColors.TextPrimary)
        val masked = maskExample(card.example, card.word)
        if (masked != null) {
            Spacer(Modifier.height(14.dp))
            Text(masked, fontSize = 13.sp, lineHeight = 19.sp, color = AppColors.TextTertiary)
        }
        Spacer(Modifier.height(26.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, if (answered) Color.White.copy(alpha = 0.12f) else accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (!answered) {
                if (input.isEmpty()) {
                    Text(
                        "输入这个单词 · ${WordEngine.spellHint(card.word)}",
                        fontSize = 15.sp,
                        color = AppColors.TextTertiary
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        letterSpacing = 1.sp
                    ),
                    cursorBrush = if (input.isEmpty()) SolidColor(Color.Transparent) else SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.onSpellSubmit(page, input) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            } else {
                Text(
                    input.ifBlank { "（未填写）" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (input.isBlank()) AppColors.TextTertiary else AppColors.TextSecondary,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (!answered) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.75f))))
                    .clickable { vm.onSpellSubmit(page, input) },
                contentAlignment = Alignment.Center
            ) {
                Text("提交答案", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            GlassCard(Modifier.fillMaxWidth(), alpha = 0.07f) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (card.answerState == ANSWER_CORRECT) "拼写正确 · 已标记「熟」" else "拼写有误 · 正确答案如下",
                        fontSize = 11.sp,
                        color = if (card.answerState == ANSWER_CORRECT) AppColors.Right else AppColors.Wrong,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        card.word,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        textDecoration = if (card.answerState == ANSWER_WRONG) TextDecoration.Underline else TextDecoration.None
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(card.translation, fontSize = 13.sp, lineHeight = 19.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

private fun maskExample(example: String, word: String): String? {
    if (example.isEmpty() || word.isEmpty()) return null
    val regex = Regex("(?i)\\b${Regex.escape(word)}[a-z]*\\b")
    return if (regex.containsMatchIn(example)) regex.replace(example, "____") else null
}

@Composable
private fun HintPill(text: String, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DoneCard(vm: FeedViewModel, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("这组刷完啦", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "今天新学 ${vm.todayNew} 个 · 复习 ${vm.todayReview} 次 · 新增熟练 ${vm.todayMastered} 个",
            fontSize = 13.sp,
            color = AppColors.TextTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        GlassCard(Modifier.fillMaxWidth(), alpha = 0.06f) {
            Column(Modifier.padding(16.dp)) {
                Text("接下来", fontSize = 11.sp, color = accent, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "背过的单词会在 10 分钟 → 1 小时 → 6 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天 的时间点自动回到信息流里。",
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(AppColors.Pink, AppColors.PinkDeep)))
                .clickable { vm.continueMore() },
            contentAlignment = Alignment.Center
        ) {
            Text("再刷 20 个新词", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .clickable { vm.openStats() },
            contentAlignment = Alignment.Center
        ) {
            Text("查看学习统计", color = AppColors.TextPrimary, fontSize = 14.sp)
        }
    }
}
