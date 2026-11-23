package br.app.stagebook.ui.stage

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageFont

/** The Android font family for each settings option. */
val StageFont.family: FontFamily
    get() = when (this) {
        StageFont.DEFAULT -> FontFamily.SansSerif
        StageFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        StageFont.SERIF -> FontFamily.Serif
        StageFont.MONO -> FontFamily.Monospace
    }

/**
 * The lyrics as the stage writes them, at [sizeSp]: one definition for the stage, the external screen, the editor's
 * preview and the one in Settings (audit 22/11: written four times, and two had lost "no hyphens"). The line keeps its
 * breathing room at both ends, so a line measured alone is as tall as inside the text.
 */
fun stageTextStyle(look: Look, sizeSp: Float, color: Color = Color(look.fg)) = TextStyle(
    color = color,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * STAGE_LINE).sp,
    fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = look.font.family,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    hyphens = Hyphens.None,
)

/** The chords over lyrics written in [text]: bold, in their colour and font, [scale] times the lyrics' size. */
fun stageChordStyle(text: TextStyle, look: Look, color: Color = Color(look.chordColor), scale: Float = look.chordScale) =
    text.copy(color = color, fontSize = text.fontSize * scale, fontWeight = FontWeight.Bold, fontFamily = look.chordFont.family, lineHeight = TextUnit.Unspecified)

private const val STAGE_LINE = 1.25f
