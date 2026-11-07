package com.guilherme.hermes.shared

// Shared by the phone app and the Windows app (Compose is the same on both).

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small line icons, drawn by hand so the app stays light. */
enum class Ico { Mark, Video, Audio, Paste, Check, Alert, Close, Retry, Share, Open, Stop, Search, Folder }

@Composable
fun Icon(i: Ico, color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIco(i, color) }
}

fun DrawScope.drawIco(i: Ico, color: Color) {
    val s = size.minDimension
    val w = s * 0.09f
    val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
    fun path(vararg pts: Float, close: Boolean = false) {
        val pa = Path().apply {
            moveTo(pts[0] * s, pts[1] * s)
            var k = 2
            while (k < pts.size) { lineTo(pts[k] * s, pts[k + 1] * s); k += 2 }
            if (close) close()
        }
        drawPath(pa, color, style = st)
    }
    when (i) {
        Ico.Mark -> {
            // Hermes: an arrow coming down onto a line, with a small wing on the shaft
            line(0.5f, 0.12f, 0.5f, 0.66f)
            path(0.3f, 0.47f, 0.5f, 0.67f, 0.7f, 0.47f)
            line(0.2f, 0.86f, 0.8f, 0.86f)
            path(0.5f, 0.3f, 0.66f, 0.2f, 0.8f, 0.2f)
            path(0.5f, 0.4f, 0.64f, 0.32f, 0.74f, 0.32f)
        }
        Ico.Video -> {
            drawRoundRect(color, p(0.12f, 0.22f), Size(0.76f * s, 0.56f * s), CornerRadius(0.12f * s), style = st)
            path(0.43f, 0.38f, 0.62f, 0.5f, 0.43f, 0.62f, close = true)
        }
        Ico.Audio -> {
            line(0.42f, 0.22f, 0.42f, 0.7f)
            path(0.42f, 0.22f, 0.78f, 0.14f, 0.78f, 0.6f)
            drawCircle(color, 0.11f * s, p(0.31f, 0.72f), style = st)
            drawCircle(color, 0.11f * s, p(0.67f, 0.62f), style = st)
        }
        Ico.Paste -> {
            drawRoundRect(color, p(0.2f, 0.2f), Size(0.6f * s, 0.68f * s), CornerRadius(0.1f * s), style = st)
            drawRoundRect(color, p(0.36f, 0.12f), Size(0.28f * s, 0.16f * s), CornerRadius(0.05f * s), style = st)
            line(0.34f, 0.52f, 0.66f, 0.52f); line(0.34f, 0.68f, 0.56f, 0.68f)
        }
        Ico.Check -> path(0.22f, 0.52f, 0.42f, 0.72f, 0.78f, 0.3f)
        Ico.Alert -> {
            drawCircle(color, 0.38f * s, p(0.5f, 0.5f), style = st)
            line(0.5f, 0.3f, 0.5f, 0.55f); drawCircle(color, w * 0.7f, p(0.5f, 0.7f))
        }
        Ico.Close -> { line(0.27f, 0.27f, 0.73f, 0.73f); line(0.73f, 0.27f, 0.27f, 0.73f) }
        Ico.Stop -> drawRoundRect(color, p(0.27f, 0.27f), Size(0.46f * s, 0.46f * s), CornerRadius(0.08f * s))
        Ico.Retry -> {
            drawArc(color, -60f, 290f, false, p(0.2f, 0.2f), Size(0.6f * s, 0.6f * s), style = st)
            path(0.62f, 0.14f, 0.67f, 0.26f, 0.55f, 0.3f)
        }
        Ico.Share -> {
            drawCircle(color, 0.09f * s, p(0.72f, 0.24f), style = st)
            drawCircle(color, 0.09f * s, p(0.28f, 0.5f), style = st)
            drawCircle(color, 0.09f * s, p(0.72f, 0.76f), style = st)
            line(0.36f, 0.45f, 0.64f, 0.29f); line(0.36f, 0.55f, 0.64f, 0.71f)
        }
        Ico.Open -> {
            path(0.44f, 0.22f, 0.22f, 0.22f, 0.22f, 0.78f, 0.78f, 0.78f, 0.78f, 0.56f)
            line(0.5f, 0.5f, 0.8f, 0.2f); path(0.58f, 0.2f, 0.8f, 0.2f, 0.8f, 0.42f)
        }
        Ico.Folder -> {
            path(0.14f, 0.3f, 0.14f, 0.78f, 0.86f, 0.78f, 0.86f, 0.38f, 0.5f, 0.38f, 0.42f, 0.26f, 0.18f, 0.26f, close = true)
        }
        Ico.Search -> {
            drawCircle(color, 0.24f * s, p(0.44f, 0.44f), style = st)
            line(0.62f, 0.62f, 0.82f, 0.82f)
        }
    }
}
