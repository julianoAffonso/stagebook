package br.app.stagebook.data.web

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import br.app.stagebook.R
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.Languages
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.essential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The editor on the computer (T37): a small HTTP server on the local network, on port [PORT] (or the next free one).
 * On only with the switch on, outside essential mode and with the app in front (closing the app turns it off).
 */
@Singleton
class WebEditorServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val identity: DeviceIdentity,
    val auth: WebEditorAuth,
    metadata: javax.inject.Provider<br.app.stagebook.data.SongMetadata>,
    importer: javax.inject.Provider<br.app.stagebook.data.PageImporter>,
    pages: javax.inject.Provider<WebPageReader>,
    cloud: javax.inject.Provider<br.app.stagebook.data.SetlistCloud>,
    bandInternet: javax.inject.Provider<br.app.stagebook.data.BandInternet>,
    inbox: javax.inject.Provider<br.app.stagebook.data.BandInbox>,
    backup: javax.inject.Provider<br.app.stagebook.data.Backup>,
    tracks: javax.inject.Provider<br.app.stagebook.data.TrackStore>,
) {
    private val scope = br.app.stagebook.data.backgroundScope(Dispatchers.IO)   // errors logged, never a crash (audit 01/11)
    private var server: ServerSocket? = null
    private var serverJob: Job? = null
    private var foreground = false

    /** "http://192.168.0.10:8090" while on; empty when off or with no local network. */
    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address

    private val idle = IdleTimer(IDLE_MS)
    private var idleJob: Job? = null

    // saving with no artist or year fills them in from MusicBrainz, as in the app's editor
    private val api = WebEditorApi(
        repository, afterSave = { metadata.get().completeLater(it) },
        importer = { importer.get() }, readPage = { url, script -> pages.get().read(url, script ?: br.app.stagebook.data.ExtractScript.SOURCE) },
        receiver = { receiver },
        band = { WebEditorApi.Band({ inbox.get().items.value }, { bandInternet.get().syncNow() }) },
        backup = { backup.get() }, tracks = { tracks.get() },
        workDir = { java.io.File(context.cacheDir, "web").apply { mkdirs() } },
    )
    private val receiver by lazy {
        WebReceive(repository, { cloud.get().receive(it) }, { code, ok -> bandInternet.get().accepted(code, ok.from) }, { inbox.get().remove(it) })
    }

    val routes = WebEditorRoutes(auth, ::page, ::strings, { identity.name.value }, api::handle, onAccess = idle::touch)

    init {
        // every app start begins with the editor off (user's decision of 30/10): it's turned on on purpose, each time
        scope.launch {
            repository.loaded.first { it }
            turnOff()
        }
        scope.launch {
            repository.library.map { it.look.webEditorOn && !it.look.essential }.distinctUntilChanged().collect { update() }
        }

    }

    fun setForeground(on: Boolean) {
        foreground = on
        update()
    }

    @Synchronized
    fun update() {
        val look = repository.library.value.look
        val want = look.webEditorOn && !look.essential && foreground
        if (want && server == null) start() else if (!want && server != null) stop()
        else if (want) _address.value = addressOf(server?.localPort ?: 0)
    }

    private val slots = java.util.concurrent.Semaphore(MAX_CONNECTIONS)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val serveDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONNECTIONS)

    private fun start() {
        // uploads left behind by a closed app (or a cut connection) go away when the editor comes back
        java.io.File(context.cacheDir, "web").listFiles()?.forEach { it.delete() }
        val socket = (PORT until PORT + 10).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
        } ?: run { Log.w(TAG, "no free port"); return }
        server = socket
        _address.value = addressOf(socket.localPort)
        idle.touch()
        idleJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(IDLE_CHECK_MS)
                if (idle.expired()) turnOff()
            }
        }
        serverJob = scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                // a few at a time, on their own threads: a flood of connections can't hold the app's other work
                // (library saves, band); the rest is closed right away (audit 30/10)
                if (!slots.tryAcquire()) { runCatching { client.close() }; continue }
                launch(serveDispatcher) { try { serve(client) } finally { slots.release() } }
            }
        }
    }

    // open connections, closed when the editor turns off: an upload in progress stops instead of restoring a backup or
    // replacing a track while the editor is "off" (audit 30/10)
    private val clients = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())

    private fun stop() {
        runCatching { server?.close() }
        server = null
        synchronized(clients) { clients.toList() }.forEach { runCatching { it.close() } }
        api.clear()
        serverJob?.cancel()
        idleJob?.cancel()
        _address.value = ""
    }

    // switches it off in Settings too: to use it again, turn it on there
    private fun turnOff() {
        if (repository.library.value.look.webEditorOn) repository.updateLook { it.copy(webEditorOn = false) }
    }

    private fun serve(client: Socket) = client.use { s ->
        clients += s
        val response = runCatching {
            s.soTimeout = TIMEOUT_MS
            val req = WebHttp.read(s.getInputStream(), s.inetAddress?.hostAddress.orEmpty(), ::bodyFile, { routes.gate(it, isBig(it.path)) }, ::maxBody)
            try {
                if (server == null) HttpResponse.status(503) else routes.handle(req)   // turned off while it was arriving
            } finally {
                req.file?.delete()   // whoever keeps it (a backup waiting for its parts) moved it away first
            }
        }.getOrElse { e -> HttpResponse.status((e as? HttpError)?.status ?: 400) }
        runCatching { WebHttp.write(s.getOutputStream(), response) }
        clients -= s
    }

    // backups and backing tracks are big: they go to a file; everything else is a small text
    private fun isBig(path: String) = path == "/api/backup/inspect" || (path.startsWith("/api/songs/") && path.endsWith("/track"))

    private fun maxBody(path: String): Long = when {
        path == "/api/backup/inspect" -> MAX_FILE
        isBig(path) -> br.app.stagebook.data.TrackStore.MAX_BYTES   // a backing track: the same limit as in the app
        else -> MAX_BODY.toLong()
    }

    private fun bodyFile(path: String): java.io.File? =
        if (isBig(path)) java.io.File(context.cacheDir, "web").apply { mkdirs() }.let { java.io.File.createTempFile("upload", ".part", it) } else null

    // the address the computer types: the Wi-Fi's (or the hotspot's) IPv4
    private fun addressOf(port: Int): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val fromWifi = cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses.orEmpty()
            .map { it.address }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        val ip = fromWifi ?: runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()
        return ip?.let { "http://${it.hostAddress}:$port" }.orEmpty()
    }

    private fun page(): String = context.assets.open("web/index.html").bufferedReader().use { it.readText() }

    // the page's texts, in the app's language (Settings → Language): translating the app translates the editor
    private fun strings(): Map<String, String> {
        val localized = Languages.wrap(context)
        return WEB_STRINGS.mapValues { localized.getString(it.value) }
    }

    companion object {
        private const val TAG = "WebEditor"
        const val PORT = 8090
        private const val TIMEOUT_MS = 15_000
        const val MAX_BODY = 2 * 1024 * 1024   // a pasted chord sheet or a few ChordPro files
        const val MAX_FILE = 1024L * 1024 * 1024   // a backup with all the backing tracks
        const val MAX_CONNECTIONS = 8
        const val IDLE_MS = 5 * 60_000L
        private const val IDLE_CHECK_MS = 30_000L

        /** Texts the page uses, by the name it knows them. */
        val WEB_STRINGS = mapOf(
            "title" to R.string.web_title,
            "ask" to R.string.web_ask,
            "askingCode" to R.string.web_asking_code,
            "denied" to R.string.web_denied,
            "busy" to R.string.web_busy,
            "connected" to R.string.web_connected,
            "offline" to R.string.web_offline,
            "saved" to R.string.web_saved,
            "pick" to R.string.web_pick,
            "unsaved" to R.string.web_unsaved,
            // the app's own texts, so the page says the same as the tablet (and is translated with it)
            "songs" to R.string.tab_songs,
            "search" to R.string.songs_search,
            "newSong" to R.string.songs_new,
            "noSongs" to R.string.songs_empty_title,
            "notFound" to R.string.songs_not_found,
            "titleField" to R.string.editor_title,
            "artist" to R.string.editor_author,
            "year" to R.string.editor_year,
            "bpm" to R.string.editor_tempo,
            "key" to R.string.editor_key,
            "capo" to R.string.editor_capo,
            "capoFret" to R.string.editor_capo_fret,
            "keyDown" to R.string.editor_transpose_down,
            "keyUp" to R.string.editor_transpose_up,
            "capoDown" to R.string.editor_capo_down,
            "capoUp" to R.string.editor_capo_up,
            "lyrics" to R.string.editor_lyrics,
            "save" to R.string.action_save,
            "delete" to R.string.action_delete,
            "deleteConfirm" to R.string.songs_delete_confirm,
            "titleEmpty" to R.string.editor_error_title_empty,
            "titleTaken" to R.string.editor_error_title_taken,
            "setlists" to R.string.tab_setlists,
            "newSet" to R.string.sets_new,
            "setName" to R.string.sets_name,
            "setNameHint" to R.string.sets_name_hint,
            "noSets" to R.string.sets_empty_title,
            "emptySet" to R.string.set_empty_title,
            "setDeleteConfirm" to R.string.sets_delete_confirm,
            "remove" to R.string.set_remove,
            "addCount" to R.string.pick_add_short,
            "track" to R.string.stage_backing_track,
            "trackNone" to R.string.editor_track_none,
            "trackPick" to R.string.editor_track_pick,
            "trackChange" to R.string.editor_track_change,
            "trackRemove" to R.string.stage_track_remove,
            "trackBad" to R.string.stage_track_bad,
            "fromInternet" to R.string.import_from_sites,
            "go" to R.string.import_go,
            "typeWords" to R.string.sets_type_code,
            "codeNotFound" to R.string.scan_code_not_found,
            "codeOffline" to R.string.scan_code_offline,
            "newer" to R.string.scan_newer,
            "which" to R.string.scan_which,
            "leaveOut" to R.string.scan_leave_out,
            "rowHave" to R.string.scan_song_have,
            "rowNew" to R.string.scan_song_new,
            "rowCame" to R.string.scan_carried_row,
            "rowMissing" to R.string.scan_missing_row,
            "create" to R.string.scan_create,
            "syncBand" to R.string.import_sync_band,
            "syncing" to R.string.import_syncing,
            "received" to R.string.inbox_title,
            "open" to R.string.inbox_open,
            "openFile" to R.string.backup_open_card,
            "merge" to R.string.backup_merge,
            "replace" to R.string.backup_replace,
            "partLibrary" to R.string.backup_part_library,
            "partTracks" to R.string.backup_part_tracks,
            "partSettings" to R.string.backup_part_settings,
            "restored" to R.string.backup_restored,
            "bandNone" to R.string.web_band_none,
            "receiveTitle" to R.string.web_receive_title,
            "backupHint" to R.string.web_backup_hint,
            "receiveDone" to R.string.web_receive_done,
            "restoreNeeds" to R.string.web_restore_needs,
            "notSong" to R.string.not_song,
            "import" to R.string.import_menu,
            "imported" to R.string.editor_imported_new,
            "unreadable" to R.string.import_unreadable,
            "tabTitle" to R.string.import_tab_title,
            "tabYes" to R.string.import_tab_yes,
            "tabNo" to R.string.import_tab_no,
            "pasteTitle" to R.string.web_paste_title,
            "pasteHint" to R.string.web_paste_hint,
            "convert" to R.string.web_convert,
            "urlTitle" to R.string.web_url_title,
            "urlHint" to R.string.web_url_hint,
            "fetch" to R.string.web_fetch,
            "reading" to R.string.web_reading,
            "filesTitle" to R.string.web_files_title,
            "filesHint" to R.string.web_files_hint,
            "filesPick" to R.string.web_files_pick,
            "filesDone" to R.string.web_files_done,
            "notFound" to R.string.web_not_found,
            "noInternet" to R.string.web_no_internet,
            "badAddress" to R.string.web_bad_address,
        )
    }
}
