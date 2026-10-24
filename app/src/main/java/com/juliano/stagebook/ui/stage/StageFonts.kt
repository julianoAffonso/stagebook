package com.juliano.stagebook.ui.stage

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import com.juliano.stagebook.data.model.StageFont

/** A família de fonte do Android para cada opção dos ajustes. */
val StageFont.family: FontFamily
    get() = when (this) {
        StageFont.DEFAULT -> FontFamily.SansSerif
        StageFont.CONDENSED -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        StageFont.SERIF -> FontFamily.Serif
        StageFont.MONO -> FontFamily.Monospace
    }
