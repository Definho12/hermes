package com.guilherme.hermes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Neutral dark base; the soft blue accent only marks the main button, what is selected and what is downloading. */
object C {
    val Bg = Color(0xFF101113)
    val Surface = Color(0xFF16171A)
    val Field = Color(0xFF121315)
    val Raised = Color(0xFF1C1E22)
    val Line = Color(0xFF26282C)
    val Border = Color(0xFF32353A)
    val Text = Color(0xFFE8E9EC)
    val Sub = Color(0xFF9A9EA6)
    val Faint = Color(0xFF62666D)
    val Accent = Color(0xFF8FD9AE)
    val AccentDim = Color(0xFF8FD9AE).copy(alpha = 0.14f)
    val OnAccent = Color(0xFF0B1F14)
    val Bad = Color(0xFFF08A7E)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

@Composable
fun SwallowTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.OnAccent, background = C.Bg, surface = C.Surface,
            onSurface = C.Text, onBackground = C.Text, surfaceVariant = C.Raised, outline = C.Border,
            surfaceContainerHigh = C.Raised, surfaceContainer = C.Surface,
        ),
        typography = base.copy(
            bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
            labelLarge = base.labelLarge.inter(), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
            titleLarge = base.titleLarge.inter(), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
            headlineSmall = base.headlineSmall.inter(),
        ),
        content = content,
    )
}
