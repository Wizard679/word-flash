package com.cishua.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 半透明玻璃卡片 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    alpha: Float = 0.08f,
    corner: Int = 18,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(Color.White.copy(alpha = alpha))
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(corner.dp))
    ) {
        content()
    }
}

/** 卡片顶部的状态标签 */
@Composable
fun StatusChip(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = accent, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    }
}

/** 顶部词库 chip */
@Composable
fun BankChip(text: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active)
        Brush.horizontalGradient(listOf(AppColors.Pink, AppColors.PinkDeep))
    else
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (active) Color.White else AppColors.TextSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 顶部小胶囊按钮 */
@Composable
fun SmallPill(text: String, onClick: () -> Unit, accent: Color = AppColors.TextSecondary) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(text, color = accent, fontSize = 11.5.sp)
    }
}

/** 三角按钮（正三角=不熟，倒三角=熟） */
@Composable
fun RailAction(
    up: Boolean,
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) 0.84f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "railScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(if (enabled) scale.value else 1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (up) {
                    moveTo(w / 2f, 2f)
                    lineTo(w - 4f, h - 4f)
                    lineTo(4f, h - 4f)
                } else {
                    moveTo(4f, 4f)
                    lineTo(w - 4f, 4f)
                    lineTo(w / 2f, h - 2f)
                }
                close()
            }
            if (enabled) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w / 2f, h / 2f),
                        radius = w * 0.72f
                    ),
                    radius = w * 0.72f,
                    center = Offset(w / 2f, h / 2f)
                )
            }
            drawPath(path, color = if (enabled) accent else accent.copy(alpha = 0.3f))
            drawPath(path, color = Color.White.copy(alpha = 0.35f), style = Stroke(width = 1f))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 圆形“熟”按钮 */
@Composable
fun MasterAction(enabled: Boolean, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "masterScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(if (enabled) scale.value else 1f)
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            if (enabled) AppColors.Pink.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.12f),
                            if (enabled) AppColors.Amber.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("熟", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "熟练",
            color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 发音按钮 */
@Composable
fun SpeakButton(text: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(Modifier.size(11.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.3f)
                lineTo(size.width * 0.7f, 0f)
                lineTo(size.width * 0.7f, size.height)
                lineTo(0f, size.height * 0.7f)
                close()
            }
            drawPath(path, accent)
        }
        Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** 进度条 */
@Composable
fun ThinProgress(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
        )
    }
}

/**
 * 底部操作条：▲不熟 / ▼熟 / 熟。
 * 放在屏幕底部，单手持机时拇指可直接点到；点击后由页面自动滑到下一个单词。
 */
@Composable
fun ActionBar(
    enabled: Boolean,
    showFamiliar: Boolean,
    onNotFamiliar: () -> Unit,
    onFamiliar: () -> Unit,
    onMastered: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            icon = ICON_UP,
            label = "不熟",
            accent = AppColors.Pink,
            onClick = onNotFamiliar
        )
        if (showFamiliar) {
            ActionButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = ICON_DOWN,
                label = "熟",
                accent = AppColors.Teal,
                onClick = onFamiliar
            )
        }
        ActionButton(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            icon = ICON_CHECK,
            label = "熟练",
            accent = AppColors.Pink,
            filled = true,
            onClick = onMastered
        )
    }
}

private const val ICON_UP = 0
private const val ICON_DOWN = 1
private const val ICON_CHECK = 2

@Composable
private fun ActionButton(
    modifier: Modifier,
    enabled: Boolean,
    icon: Int,
    label: String,
    accent: Color,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = MutableInteractionSource()
    val pressed = interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "actionScale"
    )
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(62.dp)
            .scale(scale.value)
            .clip(shape)
            .background(
                when {
                    filled && enabled -> Brush.horizontalGradient(
                        listOf(AppColors.Pink, AppColors.PinkDeep)
                    )
                    filled -> SolidColor(Color.White.copy(alpha = 0.08f))
                    else -> SolidColor(Color.White.copy(alpha = if (enabled) 0.07f else 0.03f))
                }
            )
            .border(
                1.dp,
                if (enabled) accent.copy(alpha = if (filled) 0.9f else 0.55f) else Color.White.copy(alpha = 0.08f),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(19.dp)) {
            val w = size.width
            val h = size.height
            val color = when {
                !enabled -> accent.copy(alpha = 0.35f)
                filled -> Color.White
                else -> accent
            }
            when (icon) {
                ICON_UP, ICON_DOWN -> {
                    val path = Path().apply {
                        if (icon == ICON_UP) {
                            moveTo(w / 2f, 1f)
                            lineTo(w - 1f, h - 2f)
                            lineTo(1f, h - 2f)
                        } else {
                            moveTo(1f, 2f)
                            lineTo(w - 1f, 2f)
                            lineTo(w / 2f, h - 1f)
                        }
                        close()
                    }
                    drawPath(path, color)
                }
                else -> {
                    drawCircle(
                        color = if (filled) Color.White.copy(alpha = 0.16f) else color.copy(alpha = 0.16f),
                        radius = w / 2f
                    )
                    drawCircle(color = color, radius = w / 2f, style = Stroke(width = 1.4f))
                    val path = Path().apply {
                        moveTo(w * 0.28f, h * 0.52f)
                        lineTo(w * 0.44f, h * 0.68f)
                        lineTo(w * 0.74f, h * 0.34f)
                    }
                    drawPath(path, color, style = Stroke(width = 2.2f))
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.4f)
                filled -> Color.White
                else -> Color.White.copy(alpha = 0.92f)
            }
        )
    }
}
