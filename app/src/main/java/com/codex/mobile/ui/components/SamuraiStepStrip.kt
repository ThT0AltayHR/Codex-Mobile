package com.codex.mobile.ui.components

/**
 * The "samurai steps" indicator: a small rotating ring with the current
 * step's icon at its centre, next to a shimmering label — shown under the
 * newest assistant message while Codex is actively working. Tapping it
 * expands a real timeline built only from actual completed-step events
 * (see ChatViewModel.handleStdoutLine / SamuraiStepEntry) — nothing here
 * is a fabricated placeholder.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.engine.SamuraiStep
import com.codex.mobile.engine.SamuraiStepEntry
import com.codex.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun iconForStep(step: SamuraiStep): CIcon = when (step) {
    SamuraiStep.THINKING -> CIcon.Sparkle
    SamuraiStep.PLANNING -> CIcon.Workflow
    SamuraiStep.WRITING_CODE -> CIcon.CodeBrackets
    SamuraiStep.EDITING_FILE -> CIcon.Edit
    SamuraiStep.CREATING_FILE -> CIcon.Add
    SamuraiStep.DELETING_FILE -> CIcon.Trash
    SamuraiStep.RUNNING_COMMAND -> CIcon.Shell
    SamuraiStep.INSTALLING_PACKAGE -> CIcon.Artifact
    SamuraiStep.BUILDING -> CIcon.Chip
    SamuraiStep.TESTING -> CIcon.Check
    SamuraiStep.GIT_OPERATION -> CIcon.GitBranch
    SamuraiStep.INSPECTING_ARCHIVE -> CIcon.FileArchive
    SamuraiStep.SCANNING_PROJECT -> CIcon.Folder
    SamuraiStep.SEARCHING_WEB -> CIcon.Globe
    SamuraiStep.PREPARING_SUMMARY -> CIcon.FileGeneric
    SamuraiStep.FINISHING -> CIcon.Check
    SamuraiStep.WORKING_GENERIC -> CIcon.Sparkle
    SamuraiStep.IDLE -> CIcon.Sparkle
}

fun SamuraiStepEntry.describe(): String {
    val base = step.label
    return when {
        filePath != null -> "$base  ·  ${filePath.substringAfterLast('/')}"
        command != null -> "$base  ·  ${command.take(48)}"
        sourceCount != null -> "$base  ·  $sourceCount kaynak"
        else -> base
    }
}

private fun relativeTime(ts: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))

@Composable
fun RotatingStepRing(icon: CIcon, size: androidx.compose.ui.unit.Dp = 30.dp, tint: Color = OffWhite) {
    val infinite = rememberInfiniteTransition(label = "ring")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        ) {
            drawArc(
                color = tint.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = tint.copy(alpha = 0.12f),
                startAngle = -90f + 260f,
                sweepAngle = 100f,
                useCenter = false,
                style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        CodexIcon(icon, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
fun SamuraiStepStrip(
    currentStep: SamuraiStep,
    stepHistory: List<SamuraiStepEntry>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentStep == SamuraiStep.IDLE) return
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(CardBlack)
                .border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(Dimens.radiusMd))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggleExpanded)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotatingStepRing(icon = iconForStep(currentStep))
            Spacer(Modifier.width(10.dp))
            Text(
                currentStep.label,
                color = MutedWhite,
                fontFamily = BodyFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (stepHistory.isNotEmpty()) {
                Text("${stepHistory.size}", color = FaintWhite, fontFamily = BodyFamily, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                CodexIcon(
                    if (expanded) CIcon.ChevronDown else CIcon.ChevronRight,
                    tint = FaintWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && stepHistory.isNotEmpty(),
            enter = fadeIn(tween(Motion.fast)) + expandVertically(tween(Motion.medium)),
            exit = fadeOut(tween(Motion.fast)) + shrinkVertically(tween(Motion.fast))
        ) {
            SamuraiTimeline(stepHistory, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Full step timeline — reused in the compact chat strip above and in the Workflow tab of the tools panel. */
@Composable
fun SamuraiTimeline(entries: List<SamuraiStepEntry>, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 260.dp) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(PanelBlack)
            .border(BorderStroke(1.dp, DividerGray), RoundedCornerShape(Dimens.radiusMd))
    ) {
        LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
            items(entries.reversed()) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(CardBlack),
                        contentAlignment = Alignment.Center
                    ) { CodexIcon(iconForStep(entry.step), tint = MutedWhite, modifier = Modifier.size(12.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        entry.describe(),
                        color = MutedWhite,
                        fontFamily = BodyFamily,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(relativeTime(entry.timestampMillis), color = GhostWhite, fontFamily = TerminalMonoFamily, fontSize = 10.sp)
                }
            }
        }
    }
}
