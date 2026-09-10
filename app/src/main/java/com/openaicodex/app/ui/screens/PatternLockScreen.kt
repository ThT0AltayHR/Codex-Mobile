package com.openaicodex.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.openaicodex.app.ui.theme.*

/**
 * A 3x3 dot pattern lock, used to gate a chat's Secret Vault. Two modes:
 * - [isSettingNewPattern] = true: asks the user to draw+confirm a NEW
 *   pattern (used the first time a vault is opened, or from "change
 *   pattern").
 * - false: asks the user to draw the EXISTING pattern to unlock, shows
 *   remaining attempts, and locks out (with a countdown) after too many
 *   wrong tries — see SecretVault.verifyPattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternLockScreen(
    isSettingNewPattern: Boolean,
    remainingAttempts: Int,
    lockoutRemainingSeconds: Long,
    onBack: () -> Unit,
    onPatternDrawn: (List<Int>) -> Unit
) {
    var firstDraw by remember { mutableStateOf<List<Int>?>(null) }
    var mismatchNotice by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PureBlack,
        topBar = {
            TopAppBar(
                title = { Text(if (isSettingNewPattern) "Desen belirle" else "Kasa kilidi", color = PureWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MutedWhite, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))

            if (lockoutRemainingSeconds > 0) {
                Text(
                    "Çok fazla yanlış deneme. Lütfen ${lockoutRemainingSeconds}s bekleyin.",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Text(
                    when {
                        isSettingNewPattern && firstDraw == null -> "Yeni bir desen çizin"
                        isSettingNewPattern -> "Deseni onaylamak için tekrar çizin"
                        else -> "Kasayı açmak için deseninizi çizin"
                    },
                    color = OffWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (!isSettingNewPattern && remainingAttempts in 1..2) {
                    Spacer(Modifier.height(4.dp))
                    Text("Kalan deneme: $remainingAttempts", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
                if (mismatchNotice) {
                    Spacer(Modifier.height(4.dp))
                    Text("Desenler eşleşmedi, tekrar deneyin", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(32.dp))

            PatternGrid(
                enabled = lockoutRemainingSeconds <= 0,
                onPatternComplete = { cells ->
                    if (cells.size < 4) return@PatternGrid
                    if (isSettingNewPattern) {
                        val first = firstDraw
                        if (first == null) {
                            firstDraw = cells
                            mismatchNotice = false
                        } else if (first == cells) {
                            onPatternDrawn(cells)
                        } else {
                            mismatchNotice = true
                            firstDraw = null
                        }
                    } else {
                        onPatternDrawn(cells)
                    }
                }
            )
        }
    }
}

@Composable
private fun PatternGrid(
    enabled: Boolean,
    onPatternComplete: (List<Int>) -> Unit
) {
    val gridSizeDp = 260.dp
    val density = LocalDensity.current
    var selected by remember { mutableStateOf(listOf<Int>()) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }

    val cellCentersPx = remember(gridSizeDp) {
        val sizePx = with(density) { gridSizeDp.toPx() }
        val step = sizePx / 3f
        (0 until 9).map { index ->
            val row = index / 3
            val col = index % 3
            Offset(step * col + step / 2f, step * row + step / 2f)
        }
    }

    fun nearestCell(pos: Offset): Int? {
        val sizePx = with(density) { gridSizeDp.toPx() }
        val touchRadius = sizePx / 3f * 0.42f
        return cellCentersPx.indices.minByOrNull { i -> (cellCentersPx[i] - pos).getDistanceSquared() }
            ?.takeIf { (cellCentersPx[it] - pos).getDistanceSquared() <= touchRadius * touchRadius }
    }

    Canvas(
        modifier = Modifier
            .size(gridSizeDp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        selected = emptyList()
                        nearestCell(offset)?.let { selected = listOf(it) }
                        dragPosition = offset
                    },
                    onDrag = { change, _ ->
                        dragPosition = change.position
                        nearestCell(change.position)?.let { cell ->
                            if (!selected.contains(cell)) selected = selected + cell
                        }
                    },
                    onDragEnd = {
                        onPatternComplete(selected)
                        selected = emptyList()
                        dragPosition = null
                    }
                )
            }
    ) {
        cellCentersPx.forEach { center ->
            drawCircle(color = BorderGrayColor, radius = 8.dp.toPx(), center = center)
        }
        if (selected.size > 1) {
            for (i in 0 until selected.size - 1) {
                drawLine(
                    color = Color.White,
                    start = cellCentersPx[selected[i]],
                    end = cellCentersPx[selected[i + 1]],
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
        val dp = dragPosition
        if (selected.isNotEmpty() && dp != null) {
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = cellCentersPx[selected.last()],
                end = dp,
                strokeWidth = 3.dp.toPx()
            )
        }
        selected.forEach { i ->
            drawCircle(color = Color.White, radius = 12.dp.toPx(), center = cellCentersPx[i], style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private val BorderGrayColor = Color(0xFF3A3A3A)
