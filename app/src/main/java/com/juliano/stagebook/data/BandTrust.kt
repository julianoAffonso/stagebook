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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Os aparelhos "da banda": quem foi autorizado na rede local (ou aceitou pelas 3 palavras, parte 3). A mesma confiança vale
 * nos dois caminhos e fica guardada até alguém remover; remover vale na hora (a chave do par deixa de ser usada).
 */
@Singleton
class BandTrust @Inject constructor(@ApplicationContext context: Context) {
    @Immutable
    @Serializable
    data class Member(val id: String, val name: String, val key: String, val since: Long)

    private val prefs = context.getSharedPreferences("band", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _members = MutableStateFlow(load())
    val members: StateFlow<List<Member>> = _members

    fun isMember(id: String) = _members.value.any { it.id == id }

    fun member(id: String) = _members.value.firstOrNull { it.id == id }

    /** Grava (ou atualiza o nome de) um aparelho da banda. */
    fun add(id: String, name: String, key: String) {
        _members.update { list ->
            val old = list.firstOrNull { it.id == id }
            (list.filterNot { it.id == id } + Member(id, name, key, old?.since ?: System.currentTimeMillis())).sortedBy { it.name.lowercase() }
        }
        save()
    }

    fun remove(id: String) {
        _members.update { list -> list.filterNot { it.id == id } }
        save()
    }

    private fun load(): List<Member> = runCatching { json.decodeFromString<List<Member>>(prefs.getString(KEY, null) ?: return emptyList()) }.getOrDefault(emptyList())

    private fun save() = prefs.edit { putString(KEY, json.encodeToString(_members.value)) }

    private companion object {
        const val KEY = "members"
    }
}
