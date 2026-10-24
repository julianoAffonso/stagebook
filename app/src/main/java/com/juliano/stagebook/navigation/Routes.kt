package com.juliano.stagebook.navigation

import kotlinx.serialization.Serializable

// Rotas levam só ids; cada tela busca o resto no repositório.

@Serializable
object Home

@Serializable
data class SongEditor(
    val songId: String? = null,
    val fromImport: Boolean = false,
    val cursor: Int = -1,   // aberta do palco: posição na letra da linha que o usuário segurou
)

@Serializable
data class SetlistDetail(val setId: String)

@Serializable
data class PickSongs(val setId: String)

/** Ler o QR de um setlist, ou buscar pelo [code] da internet, e conferir antes de criar. */
@Serializable
data class ScanSetlist(val code: String = "")

@Serializable
data class Stage(
    val setId: String? = null,
    val songId: String? = null,
    val start: Int = 0,   // setlist: abre o palco nesta música (a que o usuário tocou na lista)
)

@Serializable
data class Importer(val query: String = "", val queued: Boolean = false)   // queued: importando em lote para um setlist
