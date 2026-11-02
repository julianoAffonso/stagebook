package br.app.stagebook.ui.stage

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import br.app.stagebook.data.model.StageFont

/** The Android font family for each settings option. */
val StageFont.family: FontFamily
    get() = when (this) {
        StageFont.DEFAULT -> FontFamily.SansSerif
        StageFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        StageFont.SERIF -> FontFamily.Serif
        StageFont.MONO -> FontFamily.Monospace
    }
