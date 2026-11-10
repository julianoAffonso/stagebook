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
 * Backup (Settings → Library): a `.stagebook` file (a zip inside) with the chosen parts (all checked by default; the
 * user unchecks what they don't want).
 * [Part.LIBRARY]: songs and setlists, with each song's zoom. [Part.TRACKS]: the backing track files, linked to the
 * songs. [Part.SETTINGS]: stage look and settings, pedals, player and USB audio interface profiles, band and language.
 * The device identity (key kept in the Keystore) doesn't go: on another device, the band sees a new device.
 */
@Singleton
class Backup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val tracks: TrackStore,
    // the band in memory: restoring its prefs without telling it, the next change saved the old list over them (audit
    // 01/11). Optional only for tests
    private val trust: BandTrust? = null,
) {
    enum class Part { LIBRARY, TRACKS, SETTINGS }

    @Serializable
    private data class Manifest(
        val format: Int = FORMAT, val app: String = "", val parts: List<Part> = emptyList(),
        // the device that saved the band in this file ("" = no settings in it, or a file from before 10/11)
        val device: String = "",
    )

    @Serializable
    private data class Songs(val songs: List<Song> = emptyList(), val sets: List<Setlist> = emptyList())

    /** What a backup file has (to check on restore). [newer]: from a newer app version. */
    data class Contents(val parts: Set<Part>, val songs: Int, val newer: Boolean)

    // coerceInputValues: a value from a newer version (a new enum entry) falls to the default instead of failing the whole restore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    suspend fun write(out: OutputStream, parts: Set<Part>) = withContext(Dispatchers.IO) {
        val lib = repository.library.value
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            put(MANIFEST, json.encodeToString(Manifest.serializer(), Manifest(app = appVersion(), parts = parts.toList(), device = if (Part.SETTINGS in parts) DeviceIdentity.storedId(context) else "")).toByteArray())
            if (Part.LIBRARY in parts) {
                // without the tracks in the backup, the song doesn't point to a file that isn't included
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

    /** Songs left out by the last [restore] because they weren't song text. */
    @Volatile var skipped = 0
        private set

    /**
     * The last [restore] brought a band saved by another device: the members are back here, but for them this device is
     * one they never approved. On the device that saved the file nothing changes, and a file from before the device
     * was written in it says nothing either way.
     */
    @Volatile var bandAgain = false
        private set

    /** The songs the last [restore] brought without their audio (the editor on the computer says which). */
    @Volatile var lost: List<br.app.stagebook.data.model.LostAudio> = emptyList()
        private set

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
     * Restores the chosen parts. Songs and setlists: [replace] = they replace the ones here; otherwise they add (only
     * what's missing comes in). Settings replace the ones here.
     */
    suspend fun restore(input: InputStream, parts: Set<Part>, replace: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        skipped = 0
        bandAgain = false
        lost = emptyList()
        // each track goes straight to a .part file next to the others, never all of them in memory (a backup with 30
        // tracks of 15 MB used to need ~450 MB of heap and closed the app on the Fire; audit 30/10)
        val partFiles = mutableMapOf<String, java.io.File>()
        val ok = runCatching {
            var songs: Songs? = null
            var settings: JsonObject? = null
            var savedBy = ""
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    when {
                        e.name == MANIFEST -> savedBy = json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString()).device
                        e.name == SONGS -> songs = json.decodeFromString(Songs.serializer(), zip.readBytes().decodeToString())
                        e.name == SETTINGS -> settings = json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
                        e.name.startsWith(TRACKS_DIR) && Part.TRACKS in parts -> {
                            // only the file name (never an outside folder: "../")
                            val name = e.name.removePrefix(TRACKS_DIR).substringAfterLast('/')
                            if (name.isNotEmpty() && !name.startsWith(".")) streamTrack(zip, name)?.let { partFiles[name] = it }
                        }
                    }
                }
            }
            if (Part.LIBRARY in parts) songs?.let { s ->
                // the tracks take their place only now, each one swapped in by a rename
                partFiles.forEach { (name, part) -> tracks.file(name).delete(); part.renameTo(tracks.file(name)) }
                val keepTracks = Part.TRACKS in parts
                // the same filter as the band: a "song" that isn't song text stays out (user's request of 30/10)
                val allowed = s.songs.filter { br.app.stagebook.domain.BandContent.songOk(it.title, it.author, it.year, it.text) }
                skipped = s.songs.size - allowed.size
                val songs = allowed.map { if (keepTracks && it.track in partFiles) it else it.copy(track = "") }
                val here = repository.library.value.songs.mapTo(HashSet()) { it.id }
                if (replace) repository.restore(songs, s.sets) else repository.mergeBackup(songs, s.sets)
                // what came in without its audio is named in a window; joining, only the songs that are new here
                repository.reportLostAudio(repository.library.value.songs.filter { it.audioLost && (replace || it.id !in here) })
                lost = repository.lostAudio.value
                // the replaced library's tracks don't stay on the disk
                tracks.sweep(repository.library.value.songs.mapTo(HashSet()) { it.track })
            }
            if (Part.SETTINGS in parts) settings?.let { st ->
                st["look"]?.let { repository.updateLook { _ -> json.decodeFromJsonElement(Look.serializer(), st["look"]!!) } }
                st["prefs"]?.jsonObject?.forEach { (name, values) -> if (name in PREFS) jsonToPrefs(name, values.jsonObject) }
                trust?.reload()
                bandAgain = savedBy.isNotEmpty() && savedBy != DeviceIdentity.storedId(context) && hasMembers()
            }
            true
        }.getOrDefault(false)
        partFiles.values.forEach { it.delete() }   // what wasn't used (or a failed restore) leaves nothing behind
        ok
    }

    /**
     * One track of the backup to `<name>.part` in the tracks folder, in chunks: only real audio (its first bytes) of a
     * sane size, checked like a track picked by hand. Null (and nothing left on disk) otherwise.
     */
    private fun streamTrack(zip: ZipInputStream, name: String): java.io.File? {
        val part = java.io.File(tracks.file(name).parentFile, "$name.part")
        val good = runCatching { TrackStore.copyAudio(zip, part) != null }.getOrDefault(false)
        if (!good) part.delete()
        return part.takeIf { good }
    }

    private fun hasMembers(): Boolean = runCatching {
        json.parseToJsonElement(context.getSharedPreferences("band", Context.MODE_PRIVATE).getString(BandTrust.KEY, null) ?: return false).jsonArray.isNotEmpty()
    }.getOrDefault(false)

    // SharedPreferences ↔ JSON, with each value's type (text, yes/no, int/long/float, list of texts)
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
        // Android's picker doesn't know .stagebook: saves as binary and opens any file (the content is checked)
        const val MIME = "application/octet-stream"
        private const val MANIFEST = "manifest.json"
        private const val SONGS = "biblioteca.json"
        private const val SETTINGS = "ajustes.json"
        private const val TRACKS_DIR = "faixas/"
        // settings that go in the backup: player and USB interface profiles, normalization, band (who's in it) and
        // language. Left out: the identity (Keystore), the inbox and the internet queue (temporary)
        val PREFS = listOf("player", "norm", "band", "lingua")
    }
}
