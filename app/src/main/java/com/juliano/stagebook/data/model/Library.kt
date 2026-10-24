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
    // pedal MIDI pelo USB: desligado, o app não abre nada no USB (fica livre para a interface de áudio da FLOW)
    val midiUsb: Boolean = true,
) {
    companion object {
        const val MIN_FONT = 16f
        const val MAX_FONT = 72f
        const val MAX_STAGE_FONT = 120f   // com zoom no palco pode passar do máximo dos ajustes
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
