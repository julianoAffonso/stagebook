package com.juliano.stagebook.data.model

import androidx.compose.runtime.Immutable
import com.juliano.stagebook.domain.PedalAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Formato do letras.json (o mesmo da versão em Java: a biblioteca antiga abre sem conversão).

@Immutable
@Serializable
data class Song(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val year: String = "",
    val text: String = "",
    val zoom: Float = 1f,   // zoom desta música no palco (pinça), sobre o tamanho padrão dos ajustes
    // faixa de Backing track copiada para o app (nome em filesDir/tracks); só deste aparelho: fora da exportação/QR
    val track: String = "",
)

@Immutable
@Serializable
data class Setlist(
    val id: String = "",
    val name: String = "",
    val items: List<String> = emptyList(),   // ids de música, na ordem
)

@Serializable
enum class SectionLang {
    @SerialName("pt") PT,
    @SerialName("en") EN,
    @SerialName("orig") ORIGINAL,
}

@Serializable
/** Fonte do palco: só as que vêm no Android (nada para baixar; o Fire não tem o Google Play Services). */
enum class StageFont {
    @SerialName("default") DEFAULT,
    @SerialName("condensed") CONDENSED,
    @SerialName("serif") SERIF,
    @SerialName("mono") MONO,
}

/** Instrumento dos desenhos de acorde. Violão e guitarra têm a mesma afinação (mesmos desenhos). */
enum class Instrument(val chordPro: String) {
    @SerialName("violao") VIOLAO("guitar"),
    @SerialName("guitarra") GUITARRA("guitar"),
    @SerialName("ukulele") UKULELE("ukulele"),
    @SerialName("cavaquinho") CAVAQUINHO("cavaquinho"),
    @SerialName("teclado") TECLADO("keyboard"),
}

/** Onde aparece a lista com os desenhos dos acordes da música. */
enum class ChordListPlace {
    @SerialName("none") NONE,
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
}

/** Site de importação (letra ou cifra). */
@Immutable
@Serializable
data class ImportSite(val name: String, val url: String)

/** Os sites que já vêm no app; o usuário escolhe o padrão, tira e acrescenta outros. */
object ImportSites {
    val LYRICS = listOf(
        ImportSite("Letras", "https://www.letras.mus.br/"),
        ImportSite("Vagalume", "https://www.vagalume.com.br/"),
        ImportSite("Genius", "https://genius.com/"),
    )
    val CHORDS = listOf(
        ImportSite("Cifra Club", "https://www.cifraclub.com.br/"),
        ImportSite("Cifras", "https://www.cifras.com.br/"),
        ImportSite("Ultimate Guitar", "https://www.ultimate-guitar.com/"),
        ImportSite("LaCuerda", "https://acordes.lacuerda.net/"),
    )
}

enum class StageColumns {
    @SerialName("auto") AUTO,   // 2 colunas no tablet deitado, 1 no resto
    @SerialName("one") ONE,
    @SerialName("two") TWO,
}

/** Aparência do palco: vale para todas as músicas. Cores em ARGB. */
@Immutable
@Serializable
data class Look(
    val fontSp: Float = 30f,
    val fg: Int = 0xFFFFFFFF.toInt(),
    val bg: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val sectionLang: SectionLang = SectionLang.PT,   // fica só para ler bibliotecas antigas: as seções são sempre em português
    val sectionColor: Int = 0xFF4AF6CD.toInt(),
    val showSections: Boolean = true,
    val keepScreenOn: Boolean = true,   // tela não apaga com o app aberto (show); o palco fica aceso sempre
    val pedalName: String = "",   // nome do pedal Bluetooth reconhecido pelo último botão apertado
    val columns: StageColumns = StageColumns.AUTO,
    val pedalKeys: Map<Int, PedalAction> = emptyMap(),   // botões ligados a outro comando nos ajustes (tecla → comando)
    val midiBtAddress: String = "",   // pedal MIDI Bluetooth escolhido em Ajustes (o app reconecta sozinho)
    val midiBtName: String = "",
    // pedal MIDI pelo USB: desligado, o app não abre nada no USB (fica livre para a interface de áudio USB)
    val midiUsb: Boolean = true,
    // cifras (etapa 3): perfil "letra e cifra" (false = só letra), tamanho do acorde em relação à letra e cor
    val chords: Boolean = true,
    val chordScale: Float = 0.8f,
    val chordColor: Int = 0xFFFFC107.toInt(),
    // seções reconhecidas na língua do app (a letra guardada não muda); false = como estão escritas
    val translateSections: Boolean = true,
    val font: StageFont = StageFont.DEFAULT,        // fonte da letra
    val chordFont: StageFont = StageFont.DEFAULT,   // fonte dos acordes
    val instrument: Instrument = Instrument.VIOLAO,   // desenhos de acorde (popup e lista)
    val chordList: ChordListPlace = ChordListPlace.NONE,
    val chordTap: Boolean = true,
    // modo player (faixas, saída USB, mixer): desligado, nada do player carrega — nem o motor, nem a USB, nem o serviço
    val playerMode: Boolean = false,
    // importação: os sites de cada tipo; o primeiro de cada lista é o padrão (onde o importador abre)
    val lyricSites: List<ImportSite> = ImportSites.LYRICS,
    val chordSites: List<ImportSite> = ImportSites.CHORDS,   // tocar num acorde no palco mostra o desenho (desligado: o toque vira a página)
) {
    companion object {
        const val MIN_FONT = 16f
        const val MAX_FONT = 72f
        const val MAX_STAGE_FONT = 120f   // com zoom no palco pode passar do máximo dos ajustes
        const val MIN_CHORD_SCALE = 0.5f
        const val MAX_CHORD_SCALE = 1.2f
    }
}

@Immutable
@Serializable
data class Library(
    val version: Int = 1,
    val songs: List<Song> = emptyList(),
    val sets: List<Setlist> = emptyList(),
    val look: Look = Look(),
) {
    fun song(id: String): Song? = songs.firstOrNull { it.id == id }
    fun setlist(id: String): Setlist? = sets.firstOrNull { it.id == id }
}
