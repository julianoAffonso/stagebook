package br.app.stagebook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paleta "dark/modern": verde-água neon sobre grafite.
private val Mint = Color(0xFF4AF6CD)
private val MintDeep = Color(0xFF00A884)
private val Graphite = Color(0xFF121212)
private val GraphiteRaised = Color(0xFF1E1E1E)

internal val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0E4A3D),
    onPrimaryContainer = Color(0xFFB9FFEA),
    secondary = Color(0xFF9DB7FF),
    onSecondary = Color(0xFF0E2559),
    secondaryContainer = Color(0xFF26344F),
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF452800),
    background = Graphite,
    onBackground = Color(0xFFEAEAEA),
    surface = GraphiteRaised,
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = GraphiteRaised,
    surfaceContainerHigh = Color(0xFF252525),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2E2E2E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF5C1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
)

internal val LightColors = lightColorScheme(
    primary = MintDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9FFEA),
    onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF3F5AA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF0E2559),
    tertiary = Color(0xFF8A5200),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F7),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFE6E8E8),
    onSurfaceVariant = Color(0xFF5A5F5F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF1F1),
    surfaceContainerHighest = Color(0xFFE8EAEA),
    outline = Color(0xFFB4B9B9),
    outlineVariant = Color(0xFFE0E3E3),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Opções de cor do palco que o vocalista escolhe (são dados da biblioteca, em ARGB). */
/** Teclas do teclado no desenho do acorde (como num piano, nos dois temas). */
val PianoWhite = Color(0xFFF5F5F5)
val PianoBlack = Color(0xFF1A1A1A)

object StagePalette {
    val text = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFE0E0E0, 0xFF80DEEA, 0xFFA5D6A7, 0xFF000000).map { it.toInt() }
    val background = listOf(0xFF000000, 0xFF121212, 0xFF0D1B2A, 0xFF102A1A, 0xFFFFFFFF, 0xFFFFF8E1).map { it.toInt() }
    val section = listOf(0xFF4AF6CD, 0xFF42A5F5, 0xFFFFA726, 0xFFFFEB3B, 0xFFEF5350, 0xFFBDBDBD).map { it.toInt() }
    // brilho do metrônomo na borda do palco
    val glow = listOf(0xFF4AF6CD, 0xFF42A5F5, 0xFFFFA726, 0xFFEF5350, 0xFFAB47BC, 0xFFFFFFFF).map { it.toInt() }
    val chord = listOf(0xFFFFC107, 0xFFFFFFFF, 0xFF80DEEA, 0xFFA5D6A7, 0xFFFF8A65, 0xFF000000).map { it.toInt() }
}
