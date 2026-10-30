package br.app.stagebook.data

import android.content.Context
import androidx.core.content.edit
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup (Ajustes → Biblioteca): um arquivo `.stagebook` (um zip por dentro) com as partes escolhidas (todas marcadas por padrão; o usuário tira o que não quer).
 * [Part.LIBRARY]: músicas e setlists, com o zoom de cada música. [Part.TRACKS]: os arquivos de backing track, ligados às
 * músicas. [Part.SETTINGS]: aparência e ajustes do palco, pedais, player e perfis da interface de áudio USB, banda e língua.
 * A identidade do aparelho (chave guardada no Keystore) não vai: noutro aparelho, a banda reconhece um aparelho novo.
 */
@Singleton
class Backup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val tracks: TrackStore,
) {
    enum class Part { LIBRARY, TRACKS, SETTINGS }

    @Serializable
    private data class Manifest(val format: Int = FORMAT, val app: String = "", val parts: List<Part> = emptyList())

    @Serializable
    private data class Songs(val songs: List<Song> = emptyList(), val sets: List<Setlist> = emptyList())

    /** O que um arquivo de backup traz (para marcar na restauração). [newer]: de uma versão mais nova do app. */
    data class Contents(val parts: Set<Part>, val songs: Int, val newer: Boolean)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun write(out: OutputStream, parts: Set<Part>) = withContext(Dispatchers.IO) {
        val lib = repository.library.value
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            put(MANIFEST, json.encodeToString(Manifest.serializer(), Manifest(app = appVersion(), parts = parts.toList())).toByteArray())
            if (Part.LIBRARY in parts) {
                // sem as faixas no backup, a música não aponta para um arquivo que não vai junto
                val songs = if (Part.TRACKS in parts) lib.songs else lib.songs.map { it.copy(track = "") }
                put(SONGS, json.encodeToString(Songs.serializer(), Songs(songs, lib.sets)).toByteArray())
                if (Part.TRACKS in parts) {
                    lib.songs.map { it.track }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                        val f = tracks.file(name)
                        if (f.isFile) { zip.putNextEntry(ZipEntry(TRACKS_DIR + name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                    }
                }
            }
            if (Part.SETTINGS in parts) {
                val settings = buildJsonObject {
                    put("look", json.encodeToJsonElement(Look.serializer(), lib.look))
                    put("prefs", JsonObject(PREFS.associateWith { prefsToJson(it) }))
                }
                put(SETTINGS, settings.toString().toByteArray())
            }
        }
    }

    suspend fun inspect(input: InputStream): Contents? = withContext(Dispatchers.IO) {
        runCatching {
            var manifest: Manifest? = null
            var songs = 0
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    when (e.name) {
                        MANIFEST -> manifest = json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        SONGS -> songs = json.decodeFromString(Songs.serializer(), zip.readBytes().decodeToString()).songs.size
                    }
                }
            }
            manifest?.let { Contents(it.parts.toSet(), songs, it.format > FORMAT) }
        }.getOrNull()
    }

    /**
     * Restaura as partes escolhidas. Músicas e setlists: [replace] = substituem os daqui; senão somam (só entra o que falta).
     * Ajustes substituem os daqui.
     */
    suspend fun restore(input: InputStream, parts: Set<Part>, replace: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var songs: Songs? = null
            var settings: JsonObject? = null
            val trackFiles = mutableMapOf<String, ByteArray>()
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    when {
                        e.name == SONGS -> songs = json.decodeFromString(Songs.serializer(), zip.readBytes().decodeToString())
                        e.name == SETTINGS -> settings = json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
                        e.name.startsWith(TRACKS_DIR) && Part.TRACKS in parts -> {
                            // só o nome do arquivo (nunca uma pasta de fora: "../")
                            val name = e.name.removePrefix(TRACKS_DIR).substringAfterLast('/')
                            if (name.isNotEmpty() && !name.startsWith(".")) trackFiles[name] = zip.readBytes()
                        }
                    }
                }
            }
            if (Part.LIBRARY in parts) songs?.let { s ->
                trackFiles.forEach { (name, bytes) -> tracks.file(name).writeBytes(bytes) }
                val keepTracks = Part.TRACKS in parts
                val songs = s.songs.map { if (keepTracks && it.track in trackFiles) it else it.copy(track = "") }
                if (replace) repository.restore(songs, s.sets) else repository.mergeBackup(songs, s.sets)
            }
            if (Part.SETTINGS in parts) settings?.let { st ->
                st["look"]?.let { repository.updateLook { _ -> json.decodeFromJsonElement(Look.serializer(), st["look"]!!) } }
                st["prefs"]?.jsonObject?.forEach { (name, values) -> if (name in PREFS) jsonToPrefs(name, values.jsonObject) }
            }
            true
        }.getOrDefault(false)
    }

    // SharedPreferences ↔ JSON, com o tipo de cada valor (texto, sim/não, número inteiro/longo/decimal, lista de textos)
    private fun prefsToJson(name: String): JsonElement = buildJsonObject {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            when (v) {
                is String -> put(k, buildJsonObject { put("s", v) })
                is Boolean -> put(k, buildJsonObject { put("b", v) })
                is Int -> put(k, buildJsonObject { put("i", v) })
                is Long -> put(k, buildJsonObject { put("l", v) })
                is Float -> put(k, buildJsonObject { put("f", v) })
                is Set<*> -> put(k, buildJsonObject { put("ss", kotlinx.serialization.json.JsonArray(v.map { JsonPrimitive(it.toString()) })) })
            }
        }
    }

    private fun jsonToPrefs(name: String, values: JsonObject) = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit(commit = true) {
        clear()
        values.forEach { (k, typed) ->
            val o = typed.jsonObject
            o["s"]?.let { putString(k, it.jsonPrimitive.content) }
            o["b"]?.jsonPrimitive?.booleanOrNull?.let { putBoolean(k, it) }
            o["i"]?.let { putInt(k, it.jsonPrimitive.content.toInt()) }
            o["l"]?.let { putLong(k, it.jsonPrimitive.content.toLong()) }
            o["f"]?.let { putFloat(k, it.jsonPrimitive.content.toFloat()) }
            o["ss"]?.let { putStringSet(k, it.jsonArray.map { e -> e.jsonPrimitive.content }.toSet()) }
        }
    }

    private fun appVersion() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    companion object {
        const val FORMAT = 1
        const val EXTENSION = "stagebook"
        // o seletor do Android não conhece .stagebook: grava como binário e abre qualquer arquivo (o conteúdo é conferido)
        const val MIME = "application/octet-stream"
        private const val MANIFEST = "manifest.json"
        private const val SONGS = "biblioteca.json"
        private const val SETTINGS = "ajustes.json"
        private const val TRACKS_DIR = "faixas/"
        // ajustes que vão no backup: player e perfis da interface USB, normalização, banda (quem é da banda) e língua.
        // Ficam de fora a identidade (Keystore), a caixa de entrada e a fila da internet (passageiras)
        val PREFS = listOf("player", "norm", "band", "lingua")
    }
}
