package com.cishua.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cishua.app.vm.FeedViewModel

@Composable
private fun ChoiceChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) AppColors.Pink.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .border(
                1.dp,
                if (active) AppColors.Pink.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            color = if (active) AppColors.Pink else AppColors.TextSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean) {
    Box(
        Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) AppColors.Pink.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.5.sp, color = AppColors.TextTertiary, letterSpacing = 1.2.sp)
}

@Composable
fun SettingsPanel(vm: FeedViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable { vm.closeSettings() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF121218))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { vm.closeSettings() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("完成", fontSize = 12.sp, color = AppColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("当前词库")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.banks.forEach { meta ->
                    ChoiceChip(meta.name, meta.id == vm.bankId) { vm.switchBank(meta.id) }
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("每日新词上限")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 30, 50, 80).forEach { n ->
                    ChoiceChip("$n 个", vm.dailyNew == n) { vm.changeDailyNew(n) }
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("复习间隔")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("标准（记忆曲线）", !vm.quickMode) { vm.changeQuickMode(false) }
                ChoiceChip("速记（压缩 20 倍）", vm.quickMode) { vm.changeQuickMode(true) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "速记模式把间隔压缩，用于快速验证「释义 → 四选一 → 拼写」的完整流程。",
                fontSize = 11.sp,
                color = AppColors.TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(22.dp))

            SectionLabel("语音")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .clickable { vm.changeAutoSpeak(!vm.autoSpeak) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("翻页时自动朗读单词", fontSize = 13.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "滑到新单词时自动读一遍；拼写挑战始终朗读",
                        fontSize = 10.5.sp,
                        color = AppColors.TextTertiary
                    )
                }
                Spacer(Modifier.width(12.dp))
                ToggleSwitch(vm.autoSpeak)
            }
            Spacer(Modifier.height(22.dp))

            SectionLabel("数据")
            Spacer(Modifier.height(8.dp))
            if (!confirmReset) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .clickable { confirmReset = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("清空所有学习进度", fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Wrong.copy(alpha = 0.22f))
                            .border(1.dp, AppColors.Wrong.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clickable {
                                vm.resetProgress()
                                confirmReset = false
                                vm.closeSettings()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("确认清空", fontSize = 13.sp, color = AppColors.Wrong, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable { confirmReset = false }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))

            SectionLabel("玩法")
            Spacer(Modifier.height(8.dp))
            Text(
                "▲ 不熟：推送权重升高，很快再刷到\n▼ 熟：降权，下次推送变成四选一\n「熟」：标记熟练，基本不再推送\n四选一连对 3 次 → 下次进入拼写挑战（听音写词）\n拼写正确 → 标记熟练",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "词库：ECDICT（MIT 许可）提供词条、音标与中文释义；例句来自 Tatoeba（CC BY 2.0）；专升本使用公开大纲词表。App 内不含任何商业词典的释义、例句或录音，发音由系统离线 TTS 生成。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = AppColors.TextTertiary
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun StatsPanel(vm: FeedViewModel) {
    val snapshot = remember { vm.statsSnapshot() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0E16), Color(0xFF08080C))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("学习统计", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { vm.closeStats() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("返回", fontSize = 12.sp, color = AppColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock("今日新词", "${vm.todayNew}", AppColors.Pink, Modifier.weight(1f))
                StatBlock("今日复习", "${vm.todayReview}", AppColors.Teal, Modifier.weight(1f))
                StatBlock("今日熟练", "${vm.todayMastered}", AppColors.Amber, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))

            GlassCard(Modifier.fillMaxWidth(), alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("各词库进度", fontSize = 11.sp, color = AppColors.TextTertiary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(14.dp))
                    snapshot.forEach { stat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stat.meta.name, fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.width(60.dp))
                            ThinProgress(
                                fraction = if (stat.meta.count > 0) stat.mastered.toFloat() / stat.meta.count else 0f,
                                accent = AppColors.Pink,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${stat.mastered}/${stat.meta.count}",
                                fontSize = 10.5.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已推送 ${stat.seen} 个",
                            fontSize = 10.sp,
                            color = AppColors.TextTertiary
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            GlassCard(Modifier.fillMaxWidth(), alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("记忆曲线", fontSize = 11.sp, color = AppColors.Teal, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "10 分钟 → 1 小时 → 6 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "答错或不熟会把间隔回退并提高推送权重，让这个词更快回到信息流里；连续答对则逐步拉长间隔。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = AppColors.TextTertiary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "上滑继续背单词",
                fontSize = 11.sp,
                color = AppColors.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.5.sp, color = AppColors.TextTertiary)
    }
}
