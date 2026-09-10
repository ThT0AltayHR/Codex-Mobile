package com.codex.mobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.codex.mobile.engine.SamuraiStep
import com.codex.mobile.ui.theme.FaintWhite
import com.codex.mobile.ui.theme.PanelBlack

/**
 * The animated status strip: a small pill showing the current samurai
 * step. While a step is genuinely active (isActive=true, meaning a real
 * Codex event justified it and no completion/failure/cancellation event
 * has arrived yet), a soft white shimmer sweeps across the muted text —
 * this animation runs only while the underlying task is actually in
 * flight, never as decoration once it's done.
 */
@Composable
fun SamuraiStepStrip(step: SamuraiStep, modifier: Modifier = Modifier, isActive: Boolean = true) {
    if (step == SamuraiStep.IDLE) return

    Row(
        modifier = modifier
            .background(PanelBlack.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(animationSpec = tween(220)) { -it / 2 } + fadeOut(tween(220)))
            },
            label = "samurai_step"
        ) { targetStep ->
            if (isActive) {
                ShimmeringText(text = targetStep.label)
            } else {
                Text(
                    text = targetStep.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = FaintWhite
                )
            }
        }
    }
}

/**
 * Renders muted text with a soft white highlight band that sweeps across
 * it in a loop, implemented as a brush gradient whose start offset is
 * animated — a real, lightweight Compose shimmer, not a GIF or bitmap.
 * Deliberately subtle (low peak brightness, ~1.6s sweep) per the request
 * that it not be distracting or performance-heavy.
 */
@Composable
private fun ShimmeringText(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -400f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(FaintWhite, Color.White.copy(alpha = 0.9f), FaintWhite),
        start = Offset(translate - 100f, 0f),
        end = Offset(translate + 100f, 0f)
    )

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontStyle = FontStyle.Italic,
            brush = brush
        )
    )
}
