package com.codex.mobile.ui.theme

/**
 * CodexMobile's own icon system.
 *
 * Every glyph here is drawn by hand with Canvas primitives — no
 * androidx.compose.material.icons.* (those are Google's default Material
 * icon set and are intentionally never used anywhere in this app). This
 * keeps the whole interface visually distinct and consistent: one stroke
 * weight, one corner language, one hand.
 *
 * Usage: CodexIcon(CIcon.Send, tint = OffWhite, modifier = Modifier.size(20.dp))
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class CIcon {
    Menu, Back, Close, Adjust, Add, Search, ChevronRight, ChevronDown, More,
    Send, Stop, Attach, Copy, Check, CodeBrackets, Globe, Sparkle, Person,
    Appearance, Connectors, Shield, Key, Shell, StorageBox, Bell, Info, Logout,
    GitBranch, Pin, Trash, Edit, FileGeneric, Folder, FileImage, FileArchive,
    FileData, Workflow, Artifact, Refresh, Warning, Motion, Haptics,
    ExternalLink, Scale, Chip, PanelRight, RestartEngine
}

@Composable
fun CodexIcon(
    icon: CIcon,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
    strokeWidth: Float = 1.7f
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        val sw = strokeWidth * s
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, pt(x1, y1), pt(x2, y2), sw, StrokeCap.Round)
        fun circleStroke(cx: Float, cy: Float, r: Float) =
            drawCircle(tint, r * s, pt(cx, cy), style = stroke)
        fun circleFill(cx: Float, cy: Float, r: Float) =
            drawCircle(tint, r * s, pt(cx, cy), style = Fill)
        fun rrectStroke(x: Float, y: Float, w: Float, h: Float, rad: Float) =
            drawRoundRect(tint, pt(x, y), androidx.compose.ui.geometry.Size(w * s, h * s), CornerRadius(rad * s, rad * s), style = stroke)
        fun rrectFill(x: Float, y: Float, w: Float, h: Float, rad: Float) =
            drawRoundRect(tint, pt(x, y), androidx.compose.ui.geometry.Size(w * s, h * s), CornerRadius(rad * s, rad * s), style = Fill)
        fun poly(vararg p: Float, close: Boolean = true) {
            val path = Path()
            path.moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) { path.lineTo(p[i] * s, p[i + 1] * s); i += 2 }
            if (close) path.close()
            drawPath(path, tint, style = stroke)
        }

        when (icon) {
            CIcon.Menu -> { ln(4f, 7f, 20f, 7f); ln(4f, 12f, 20f, 12f); ln(4f, 17f, 20f, 17f) }
            CIcon.Back -> { ln(15f, 5f, 8f, 12f); ln(8f, 12f, 15f, 19f) }
            CIcon.Close -> { ln(6f, 6f, 18f, 18f); ln(18f, 6f, 6f, 18f) }
            CIcon.Adjust -> {
                ln(4f, 6f, 20f, 6f); circleFill(9f, 6f, 1.9f)
                ln(4f, 12f, 20f, 12f); circleFill(15f, 12f, 1.9f)
                ln(4f, 18f, 20f, 18f); circleFill(7f, 18f, 1.9f)
            }
            CIcon.Add -> { ln(12f, 5f, 12f, 19f); ln(5f, 12f, 19f, 12f) }
            CIcon.Search -> { circleStroke(10.5f, 10.5f, 6.5f); ln(15.3f, 15.3f, 20f, 20f) }
            CIcon.ChevronRight -> { ln(9f, 5f, 16f, 12f); ln(16f, 12f, 9f, 19f) }
            CIcon.ChevronDown -> { ln(5f, 9f, 12f, 16f); ln(12f, 16f, 19f, 9f) }
            CIcon.More -> { circleFill(12f, 6f, 1.5f); circleFill(12f, 12f, 1.5f); circleFill(12f, 18f, 1.5f) }
            CIcon.Send -> { ln(12f, 19f, 12f, 6f); ln(12f, 6f, 6f, 12f); ln(12f, 6f, 18f, 12f) }
            CIcon.Stop -> rrectFill(7f, 7f, 10f, 10f, 2.5f)
            CIcon.Attach -> {
                val p = Path().apply {
                    moveTo(17f * s, 8.5f * s); lineTo(17f * s, 15.5f * s)
                    cubicTo(17f * s, 18f * s, 15f * s, 20f * s, 12.5f * s, 20f * s)
                    cubicTo(10f * s, 20f * s, 8f * s, 18f * s, 8f * s, 15.5f * s)
                    lineTo(8f * s, 7f * s)
                    cubicTo(8f * s, 5.3f * s, 9.3f * s, 4f * s, 11f * s, 4f * s)
                    cubicTo(12.7f * s, 4f * s, 14f * s, 5.3f * s, 14f * s, 7f * s)
                    lineTo(14f * s, 14.5f * s)
                    cubicTo(14f * s, 15.6f * s, 13.1f * s, 16.5f * s, 12f * s, 16.5f * s)
                    cubicTo(10.9f * s, 16.5f * s, 10f * s, 15.6f * s, 10f * s, 14.5f * s)
                    lineTo(10f * s, 8f * s)
                }
                drawPath(p, tint, style = stroke)
            }
            CIcon.Copy -> {
                rrectStroke(4f, 4f, 12f, 12f, 2.3f)
                val p = Path().apply {
                    moveTo(8f * s, 4f * s); lineTo(8f * s, 1.8f * s); lineTo(20.2f * s, 1.8f * s)
                    lineTo(20.2f * s, 13.5f * s); lineTo(18f * s, 13.5f * s)
                }
                drawPath(p, tint, style = stroke)
            }
            CIcon.Check -> { ln(5f, 13f, 10f, 18f); ln(10f, 18f, 19f, 7f) }
            CIcon.CodeBrackets -> {
                ln(8f, 9f, 4.5f, 12f); ln(4.5f, 12f, 8f, 15f)
                ln(16f, 9f, 19.5f, 12f); ln(19.5f, 12f, 16f, 15f)
                ln(13.5f, 7f, 10.5f, 17f)
            }
            CIcon.Globe -> {
                circleStroke(12f, 12f, 8f)
                drawOval(tint, pt(8f, 4f), androidx.compose.ui.geometry.Size(8f * s, 16f * s), style = stroke)
                ln(4.3f, 12f, 19.7f, 12f)
            }
            CIcon.Sparkle -> {
                val p = Path().apply {
                    moveTo(12f * s, 3f * s); lineTo(13.8f * s, 9.2f * s); lineTo(20f * s, 11f * s)
                    lineTo(13.8f * s, 12.8f * s); lineTo(12f * s, 19f * s); lineTo(10.2f * s, 12.8f * s)
                    lineTo(4f * s, 11f * s); lineTo(10.2f * s, 9.2f * s); close()
                }
                drawPath(p, tint, style = Fill)
            }
            CIcon.Person -> {
                circleStroke(12f, 8f, 3.2f)
                val p = Path().apply {
                    moveTo(5f * s, 20f * s)
                    cubicTo(5f * s, 16f * s, 8f * s, 14f * s, 12f * s, 14f * s)
                    cubicTo(16f * s, 14f * s, 19f * s, 16f * s, 19f * s, 20f * s)
                }
                drawPath(p, tint, style = stroke)
            }
            CIcon.Appearance -> {
                circleStroke(12f, 12f, 8f)
                drawArc(tint, 90f, 180f, useCenter = true, topLeft = pt(4f, 4f), size = androidx.compose.ui.geometry.Size(16f * s, 16f * s))
            }
            CIcon.Connectors -> { circleStroke(9f, 12f, 5f); circleStroke(15f, 12f, 5f) }
            CIcon.Shield -> {
                val p = Path().apply {
                    moveTo(12f * s, 3f * s); lineTo(19f * s, 6f * s); lineTo(19f * s, 11f * s)
                    cubicTo(19f * s, 16f * s, 16f * s, 19.5f * s, 12f * s, 21f * s)
                    cubicTo(8f * s, 19.5f * s, 5f * s, 16f * s, 5f * s, 11f * s)
                    lineTo(5f * s, 6f * s); lineTo(12f * s, 3f * s); close()
                }
                drawPath(p, tint, style = stroke)
                ln(9f, 11.5f, 11f, 13.5f); ln(11f, 13.5f, 15.5f, 9f)
            }
            CIcon.Key -> {
                circleStroke(7.5f, 7.5f, 3.5f)
                ln(10f, 10f, 19f, 19f); ln(16f, 16f, 18.2f, 13.8f); ln(18f, 18f, 20.2f, 15.8f)
            }
            CIcon.Shell -> {
                rrectStroke(3f, 4f, 18f, 16f, 2.5f)
                ln(7f, 9.5f, 10.5f, 12.5f); ln(10.5f, 12.5f, 7f, 15.5f)
                ln(12.5f, 15.5f, 17f, 15.5f)
            }
            CIcon.StorageBox -> {
                rrectStroke(3f, 7f, 18f, 13f, 2f)
                ln(3f, 11f, 21f, 11f); ln(10f, 14.2f, 14f, 14.2f)
            }
            CIcon.Bell -> {
                val p = Path().apply {
                    moveTo(6f * s, 10f * s)
                    cubicTo(6f * s, 6.5f * s, 8.5f * s, 4f * s, 12f * s, 4f * s)
                    cubicTo(15.5f * s, 4f * s, 18f * s, 6.5f * s, 18f * s, 10f * s)
                    lineTo(18f * s, 14f * s); lineTo(20f * s, 17f * s); lineTo(4f * s, 17f * s)
                    lineTo(6f * s, 14f * s); close()
                }
                drawPath(p, tint, style = stroke)
                drawArc(tint, 10f, 160f, useCenter = false, topLeft = pt(9.6f, 17.6f), size = androidx.compose.ui.geometry.Size(4.8f * s, 4.4f * s), style = stroke)
            }
            CIcon.Info -> { circleStroke(12f, 12f, 8.5f); circleFill(12f, 7.7f, 1.1f); ln(12f, 11f, 12f, 16.5f) }
            CIcon.Logout -> {
                val p = Path().apply {
                    moveTo(14f * s, 4f * s); lineTo(7f * s, 4f * s)
                    lineTo(5f * s, 6f * s); lineTo(5f * s, 18f * s)
                    lineTo(7f * s, 20f * s); lineTo(14f * s, 20f * s)
                }
                drawPath(p, tint, style = stroke)
                ln(10f, 12f, 21f, 12f); ln(21f, 12f, 17f, 8f); ln(21f, 12f, 17f, 16f)
            }
            CIcon.GitBranch -> {
                circleStroke(6f, 6f, 2.1f); circleStroke(6f, 18f, 2.1f); circleStroke(18f, 6f, 2.1f)
                ln(6f, 8.1f, 6f, 15.9f)
                val p = Path().apply {
                    moveTo(6f * s, 10.5f * s)
                    cubicTo(6f * s, 13f * s, 9f * s, 13.3f * s, 12f * s, 13.3f * s)
                    cubicTo(15.3f * s, 13.3f * s, 15.9f * s, 10f * s, 16.7f * s, 8.2f * s)
                }
                drawPath(p, tint, style = stroke)
            }
            CIcon.Pin -> {
                val p = Path().apply {
                    moveTo(12f * s, 21f * s)
                    cubicTo(12f * s, 21f * s, 18f * s, 14.5f * s, 18f * s, 10f * s)
                    cubicTo(18f * s, 6.1f * s, 15.3f * s, 3f * s, 12f * s, 3f * s)
                    cubicTo(8.7f * s, 3f * s, 6f * s, 6.1f * s, 6f * s, 10f * s)
                    cubicTo(6f * s, 14.5f * s, 12f * s, 21f * s, 12f * s, 21f * s); close()
                }
                drawPath(p, tint, style = stroke)
                circleStroke(12f, 10f, 2.3f)
            }
            CIcon.Trash -> {
                ln(5f, 7f, 19f, 7f)
                poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f, close = false)
                val p = Path().apply {
                    moveTo(7f * s, 7f * s); lineTo(8f * s, 20f * s); lineTo(16f * s, 20f * s); lineTo(17f * s, 7f * s)
                }
                drawPath(p, tint, style = stroke)
                ln(10f, 11f, 10f, 17f); ln(14f, 11f, 14f, 17f)
            }
            CIcon.Edit -> {
                poly(4f, 20f, 8f, 20f, 18.5f, 9.5f, close = false)
                val p = Path().apply {
                    moveTo(18.5f * s, 9.5f * s)
                    lineTo(14.5f * s, 5.5f * s)
                    lineTo(4f * s, 16f * s); lineTo(4f * s, 20f * s)
                }
                drawPath(p, tint, style = stroke)
                ln(13f, 7f, 17f, 11f)
            }
            CIcon.FileGeneric -> {
                poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                ln(14f, 3f, 14f, 8f); ln(14f, 8f, 19f, 8f)
                ln(9f, 13f, 16f, 13f); ln(9f, 17f, 14f, 17f)
            }
            CIcon.Folder -> poly(3f, 6f, 9f, 6f, 11f, 8f, 21f, 8f, 21f, 18f, 3f, 18f)
            CIcon.FileImage -> {
                poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                ln(14f, 3f, 14f, 8f); ln(14f, 8f, 19f, 8f)
                circleFill(9.3f, 12.3f, 1.1f)
                poly(8f, 18f, 11.5f, 13.5f, 14f, 16.5f, 17f, 12.5f, 17f, 18f)
            }
            CIcon.FileArchive -> {
                poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                ln(14f, 3f, 14f, 8f); ln(14f, 8f, 19f, 8f)
                ln(12f, 10f, 12f, 12f); ln(12f, 14f, 12f, 16f); ln(12f, 18f, 12f, 19.5f)
            }
            CIcon.FileData -> {
                poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                ln(14f, 3f, 14f, 8f); ln(14f, 8f, 19f, 8f)
                rrectStroke(8.3f, 11f, 3f, 3f, 0.6f); rrectStroke(12.7f, 11f, 3f, 3f, 0.6f)
                rrectStroke(8.3f, 15f, 3f, 3f, 0.6f); rrectStroke(12.7f, 15f, 3f, 3f, 0.6f)
            }
            CIcon.Workflow -> {
                rrectStroke(3f, 3f, 6f, 6f, 1.4f); rrectStroke(9f, 9f, 6f, 6f, 1.4f); rrectStroke(15f, 15f, 6f, 6f, 1.4f)
                ln(9.3f, 9.3f, 8.2f, 8.2f); ln(15.3f, 14.7f, 14.2f, 15.8f)
            }
            CIcon.Artifact -> {
                poly(12f, 3f, 20f, 7.5f, 20f, 16.5f, 12f, 21f, 4f, 16.5f, 4f, 7.5f)
                ln(12f, 12f, 12f, 21f); ln(12f, 12f, 20f, 7.5f); ln(12f, 12f, 4f, 7.5f)
            }
            CIcon.Refresh, CIcon.RestartEngine -> {
                drawArc(tint, -160f, 140f, useCenter = false, topLeft = pt(4f, 4f), size = androidx.compose.ui.geometry.Size(16f * s, 16f * s), style = stroke)
                drawArc(tint, 20f, 140f, useCenter = false, topLeft = pt(4f, 4f), size = androidx.compose.ui.geometry.Size(16f * s, 16f * s), style = stroke)
                ln(4f, 8f, 4f, 12f); ln(4f, 12f, 8f, 12f)
                ln(20f, 16f, 20f, 12f); ln(20f, 12f, 16f, 12f)
            }
            CIcon.Warning -> {
                poly(12f, 3f, 22f, 20f, 2f, 20f)
                ln(12f, 9.5f, 12f, 14.5f); circleFill(12f, 17.3f, 1f)
            }
            CIcon.Motion -> {
                val p = Path().apply {
                    moveTo(3f * s, 12f * s)
                    cubicTo(5f * s, 12f * s, 5f * s, 8f * s, 8f * s, 8f * s)
                    cubicTo(11f * s, 8f * s, 11f * s, 16f * s, 14f * s, 16f * s)
                    cubicTo(17f * s, 16f * s, 17f * s, 12f * s, 21f * s, 12f * s)
                }
                drawPath(p, tint, style = stroke)
            }
            CIcon.Haptics -> {
                rrectStroke(8f, 3f, 8f, 18f, 2.3f)
                ln(4f, 9f, 4f, 15f); ln(20f, 9f, 20f, 15f)
            }
            CIcon.ExternalLink -> {
                poly(9f, 5f, 5f, 5f, 3f, 7f, 3f, 19f, 5f, 21f, 17f, 21f, 19f, 19f, 19f, 15f, close = false)
                poly(14f, 4f, 20f, 4f, 20f, 10f, close = false)
                ln(20f, 4f, 11f, 13f)
            }
            CIcon.Scale -> { rrectFill(4f, 15f, 3.2f, 5f, 0.8f); rrectFill(10.4f, 10f, 3.2f, 10f, 0.8f); rrectFill(16.8f, 5f, 3.2f, 15f, 0.8f) }
            CIcon.Chip -> {
                rrectStroke(7f, 7f, 10f, 10f, 1.5f)
                ln(9f, 3f, 9f, 7f); ln(12f, 3f, 12f, 7f); ln(15f, 3f, 15f, 7f)
                ln(9f, 17f, 9f, 21f); ln(12f, 17f, 12f, 21f); ln(15f, 17f, 15f, 21f)
                ln(3f, 9f, 7f, 9f); ln(3f, 12f, 7f, 12f); ln(3f, 15f, 7f, 15f)
                ln(17f, 9f, 21f, 9f); ln(17f, 12f, 21f, 12f); ln(17f, 15f, 21f, 15f)
            }
            CIcon.PanelRight -> { rrectStroke(3f, 4f, 18f, 16f, 2.3f); ln(15f, 4.3f, 15f, 19.7f) }
        }
    }
}
