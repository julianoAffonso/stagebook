package com.juliano.stagebook.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O que a banda mandou e ainda não foi conferido (setlist ou músicas avulsas). Fica guardado até conferir ou descartar —
 * também se o app fechar. As abas avisam (como e-mail novo); no palco nada aparece.
 */
@Singleton
class BandInbox @Inject constructor(@ApplicationContext context: Context) {
    /** [text]: a lista no formato do QR (sem nome = músicas avulsas); [seen]: o aviso já foi dispensado ("Agora não"). */
    @Immutable
    @Serializable
    data class Item(
        val id: String,
        val fromId: String,
        val fromName: String,
        val text: String,
        val songs: List<SetlistCloud.SharedSong> = emptyList(),
        val at: Long = System.currentTimeMillis(),
        val seen: Boolean = false,
    ) {
        /** Nome do setlist; vazio = músicas avulsas. */
        val setName: String get() = text.split('\n').getOrNull(2).orEmpty()
        val songCount: Int get() = text.split('\n').getOrNull(1)?.trim()?.toIntOrNull() ?: songs.size
        /** Veio de uma versão mais nova do Stagebook: só dá para conferir depois de atualizar. */
        val newer: Boolean get() = text == SetlistCloud.NEWER
    }

    private val prefs = context.getSharedPreferences("inbox", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Item>> = _items

    /**
     * Guarda o que chegou. O mesmo envio chegando duas vezes (pelo Wi-Fi e pela internet) não repete; o mesmo setlist da
     * mesma pessoa substitui o antigo (ela corrigiu e mandou de novo: não vale conferir a versão velha).
     */
    fun add(fromId: String, fromName: String, text: String, songs: List<SetlistCloud.SharedSong>) {
        // tudo o que vem da banda (rede local ou internet) passa aqui: só setlist e músicas em texto puro
        if (!com.juliano.stagebook.domain.BandContent.listOk(text)) return
        val item = Item(UUID.randomUUID().toString(), fromId, fromName, text, songs.filter { it.allowed() }.take(com.juliano.stagebook.domain.BandContent.MAX_SONGS))
        _items.update { list ->
            if (list.any { it.fromId == fromId && it.text == text && it.songs == item.songs }) return@update list
            val kept = if (item.setName.isEmpty()) list else list.filterNot { it.fromId == fromId && it.setName == item.setName }
            (kept + item).takeLast(MAX)
        }
        save()
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    fun markSeen(id: String) { _items.update { l -> l.map { if (it.id == id) it.copy(seen = true) else it } }; save() }

    fun remove(id: String) { _items.update { l -> l.filterNot { it.id == id } }; save() }

    private fun load(): List<Item> = runCatching { json.decodeFromString<List<Item>>(prefs.getString(KEY, null) ?: return emptyList()) }.getOrDefault(emptyList())

    private fun save() = prefs.edit { putString(KEY, json.encodeToString(_items.value)) }

    private companion object {
        const val KEY = "items"
        const val MAX = 30   // a caixa não cresce sem limite
    }
}

/**
 * Recebidos agrupados (T11): o mesmo setlist (pelo nome) mandado por mais de um membro fica junto, o mais novo primeiro,
 * para a pessoa escolher qual usar ou comparar; setlists diferentes ficam cada um no seu grupo, sem um apagar o outro.
 * Músicas avulsas ficam sozinhas. Grupos na ordem do mais recente.
 */
fun groupInbox(items: List<BandInbox.Item>): List<List<BandInbox.Item>> =
    items.groupBy { if (it.setName.isEmpty()) "#" + it.id else it.setName.trim().lowercase() }
        .values.map { g -> g.sortedByDescending { it.at } }
        .sortedByDescending { g -> g.first().at }

