package com.juliano.stagebook.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.edit
import com.juliano.stagebook.di.ApplicationScope
import com.juliano.stagebook.di.LowPriority
import com.juliano.stagebook.domain.LyricsCompare
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Músicas do Drive depois de instalar ou atualizar o app, sem atrapalhar o show nem estragar a
 * biblioteca:
 * 1. baixa em silêncio, mesmo com o palco aberto, numa linha de baixa prioridade (o palco do Fire
 *    não perde processador); sem internet, espera ela voltar. O arquivo conferido fica pendente;
 * 2. aplica com o palco fechado: na hora, se ele já está fechado, ou ao sair dele. A gravação só vale
 *    se conferir (ver [LibraryRepository.commitSync]), senão o pendente fica e tenta de novo depois;
 * 3. o aviso do que mudou ([notice]) aparece assim que aplica (fora do palco), ao sair do palco ou na
 *    próxima abertura do app, nunca por cima do palco. A primeira carga num app vazio não avisa.
 */
@Singleton
class LibrarySync @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: LibraryRepository,
    private val updater: AppUpdater,
    private val stage: StageGate,
    private val order: UpdateOrder,
    @ApplicationScope private val scope: CoroutineScope,
    @LowPriority private val background: CoroutineDispatcher,   // download e conferência sem roubar o palco
) {
    private val prefs = context.getSharedPreferences("sincronia", Context.MODE_PRIVATE)
    private val pending = File(context.filesDir, "sincronia/biblioteca-do-drive.json")
    private val detailsFile = File(context.filesDir, "sincronia/ultima.json")
    private val before = File(context.filesDir, "stagebook.antes-da-sincronia.json")   // gravado pelo commitSync
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _notice = MutableStateFlow<SyncNotice?>(null)
    /** O que a sincronia mudou, para avisar (a tela chama [dismissNotice]). */
    val notice: StateFlow<SyncNotice?> = _notice.asStateFlow()
    private val _updating = MutableStateFlow(false)
    /** Há uma atualização nova a caminho (baixando ou esperando o palco/a decisão do app): a revisão é a anterior. */
    val updating: StateFlow<Boolean> = _updating.asStateFlow()
    private var job: Job? = null
    private var watching = false
    @Volatile private var wasOnline = false


    // internet de verdade agora (os testes trocam)
    internal var online: () -> Boolean = {
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    // só a passagem sem internet → com internet dispara: a mudança de sinal do Wi-Fi não baixa de novo
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            onNetwork(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        override fun onLost(network: Network) = onNetwork(false)
    }

    init {
        // saiu do palco: mostra o aviso que estava esperando e retoma uma gravação adiada
        scope.launch { stage.exits.drop(1).collect { showSaved(); start() } }
        _updating.value = hasPending()
    }

    // a biblioteca é a que o arquivo de versão anuncia (marca sha-256); publicada antes da marca: uma por versão do app
    private fun target(p: PublishedVersion) = p.library.ifEmpty { "app:${updater.installedCode}" }
    private fun hasPending() = pending.exists() && !prefs.getString(KEY_PENDING, null).isNullOrEmpty()

    /**
     * Toda vez que o app confere a biblioteca publicada fica registrado, mude algo ou não: sem isso, uma
     * atualização que não achou nada não deixava sinal nenhum (a revisão só guarda as que mudaram algo).
     */
    private fun checked(result: LastCheck.Result, count: Int = 0) {
        prefs.edit { putString(KEY_CHECK, "${System.currentTimeMillis()},$result,$count") }
    }

    /** A última vez que o app conferiu a biblioteca publicada, ou null se nunca. */
    fun lastCheck(): LastCheck? {
        val p = prefs.getString(KEY_CHECK, null)?.split(',') ?: return null
        val at = p.getOrNull(0)?.toLongOrNull() ?: return null
        val result = runCatching { LastCheck.Result.valueOf(p.getOrElse(1) { "" }) }.getOrNull() ?: return null
        return LastCheck(at, result, p.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    fun dismissNotice() {
        _notice.value = null
        prefs.edit { remove(KEY_NOTICE) }
    }

    /** O app abriu (ou voltou para a frente): mostra o aviso guardado, fora do palco, e segue a sincronia. */
    fun onEnter(): Job? {
        showSaved()
        return start()
    }

    /**
     * Baixa (se a biblioteca publicada mudou desde a última aplicada) e aplica com o palco fechado. Não depende
     * da versão do app: músicas publicadas chegam mesmo sem app novo.
     */
    @Synchronized
    fun start(): Job? {
        if (job?.isActive == true) return job
        if (!hasPending() && !online()) { watch(); return null }
        job = scope.launch {
            if (!hasPending()) {
                val p = updater.published(background) ?: run { watch(); return@launch }   // sem resposta: quando a internet voltar
                if (prefs.getString(KEY_SYNCED, null) == target(p)) {
                    checked(LastCheck.Result.UP_TO_DATE)
                    unwatch()
                    return@launch
                }
                // versão nova do app primeiro: as músicas esperam o usuário decidir (instalar reinicia o app)
                _updating.value = true
                if (p.versionCode > updater.installedCode) order.awaitAppDecision(p.versionCode)
                if (!download(p)) { watch(); return@launch }
            }
            _updating.value = true
            stage.onStage.first { !it }   // palco aberto: aplica ao sair dele
            order.syncBusy(true)
            val applied = try { apply() } finally { order.syncBusy(false) }
            if (applied) showSaved()   // fora do palco: avisa na hora
        }.also { j -> j.invokeOnCompletion { finished(j) } }
        return job
    }


    /**
     * Baixa e guarda como pendente só se for a biblioteca anunciada (mesma marca: o Drive pode estar no meio
     * da troca do arquivo) e de verdade (com músicas).
     */
    private suspend fun download(p: PublishedVersion): Boolean = withContext(background) {
        val got = updater.publishedLibrary(background) ?: return@withContext false
        if (p.library.isNotEmpty() && got.sha256 != p.library) {
            Log.w(TAG, "biblioteca baixada não é a anunciada (o Drive ainda está trocando?): fica para depois")
            return@withContext false
        }
        val json = got.text
        val songs = runCatching { LibraryCodec.decodeSongs(json) }.getOrNull()
        if (songs.isNullOrEmpty() || songs.none { it.title.isNotBlank() }) {
            // o Drive devolveu outra coisa (página de aviso, arquivo cortado): fica para a próxima
            Log.w(TAG, "biblioteca do Drive ilegível ou vazia")
            return@withContext false
        }
        try {
            pending.parentFile?.mkdirs()
            pending.replaceAtomically(json.toByteArray())
            prefs.edit(commit = true) { putString(KEY_PENDING, target(p)) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "não guardei a biblioteca do Drive", e)
            false
        }
    }

    /** Junta e grava com conferência; true se gravou. */
    private suspend fun apply(): Boolean {
        if (stage.onStage.value) return false
        val incoming = withContext(background) { runCatching { LibraryCodec.decodeSongs(pending.readText()) }.getOrNull() }
        if (incoming.isNullOrEmpty()) { discardPending(); return false }
        // instalação do zero (biblioteca vazia): é a carga inicial, não uma atualização para avisar ou revisar
        repository.loaded.first { it }
        val firstLoad = repository.library.value.songs.isEmpty()
        // juntar e gravar também em baixa prioridade; se o palco abriu nesse meio tempo, não troca
        val r = repository.commitSync(incoming, background) { !stage.onStage.value } ?: return false   // o pendente fica para depois
        // os detalhes antes de dar por concluída: o app encerrado logo depois (ex.: instalar versão nova) não os perde
        // ("última atualização" = a última que mudou alguma música: uma sem mudança não apaga os detalhes)
        if (r.changes.isNotEmpty() && !firstLoad) {
            val d = SyncDetails(System.currentTimeMillis(), r.changes)
            detailsLock.withLock {
                cached = d
                withContext(background) {
                    runCatching { saveDetails(d) }.onFailure { Log.w(TAG, "não guardei os detalhes da sincronia", it) }
                }
            }
        }
        prefs.edit(commit = true) {
            putString(KEY_SYNCED, prefs.getString(KEY_PENDING, null))
            val now = SyncNotice(r.added, r.updated, r.sectionsAdded, r.protectedCount, r.metaCount)
            if (now.any && !firstLoad) {
                // soma com um aviso ainda não visto
                val seen = savedNotice() ?: SyncNotice()
                putString(KEY_NOTICE, (seen + now).encode())
            }
        }
        discardPending()
        unwatch()
        checked(if (r.changes.isEmpty()) LastCheck.Result.NOTHING_CHANGED else LastCheck.Result.CHANGED, r.changes.size)
        Log.i(TAG, "sincronia com o Drive: ${r.copy(changes = emptyList())}")
        return true
    }

    // um job que acabou só mexe no aviso "chegando" se ainda é o atual (um novo pode já ter começado)
    @Synchronized
    private fun finished(j: Job) {
        if (job === j) _updating.value = hasPending()
    }

    // ---- detalhes da última sincronia (Ajustes e "Ver detalhes" do aviso) ----

    private val detailsJson = Json { ignoreUnknownKeys = true }
    // na memória enquanto o app está aberto (o arquivo tem as letras de antes e depois, até ~2 MB) e um
    // desfazer de cada vez: dois seguidos não apagam a marca um do outro
    private val detailsLock = Mutex()
    private var cached: SyncDetails? = null

    private fun saveDetails(d: SyncDetails) {
        detailsFile.parentFile?.mkdirs()
        detailsFile.replaceAtomically(detailsJson.encodeToString(SyncDetails.serializer(), d).toByteArray())
    }

    /**
     * O que a última sincronia mudou, ou null se nunca houve. Uma sincronia anterior a este registro é
     * refeita da cópia de antes: só conta a música cuja letra ainda é exatamente a que veio do Drive (uma
     * edição do usuário nunca aparece como mudança da sincronia).
     */
    suspend fun details(): SyncDetails? = detailsLock.withLock { detailsLocked() }

    private suspend fun detailsLocked(): SyncDetails? = cached ?: withContext(background) {
        (runCatching { detailsJson.decodeFromString(SyncDetails.serializer(), detailsFile.readText()) }.getOrNull()
            ?: rebuildDetails()?.also { d -> runCatching { saveDetails(d) } })
            // detalhes gravados antes da regra das seções do aparelho: tira as que não têm o que decidir
            ?.let { d -> d.copy(changes = d.changes.filterNot { it.kind == SyncKind.PROTECTED && !it.decided && onlyLocalSections(it.before, it.after) }) }
    }.also { cached = it }

    private suspend fun rebuildDetails(): SyncDetails? {
        if (!before.exists()) return null
        val old = runCatching { LibraryCodec.decode(before.readText()) { "" } }.getOrNull() ?: return null
        if (old.songs.isEmpty()) return null   // antes era o app vazio: foi a carga inicial, nada para revisar
        repository.loaded.first { it }
        val changes = repository.library.value.songs.mapNotNull { now ->
            val fromDrive = now.driveHash.isNotEmpty() && lyricsHash(now.text) == now.driveHash
            val was = old.song(now.id)
            when {
                was == null && now.driveHash.isNotEmpty() -> SyncChange(now.id, now.title, SyncKind.ADDED)
                was != null && fromDrive && was.text != now.text && LyricsCompare.canonical(was.text) != LyricsCompare.canonical(now.text) -> {
                    val kind = if (LyricsCompare.words(was.text) == LyricsCompare.words(now.text)) SyncKind.SECTIONS else SyncKind.UPDATED
                    SyncChange(now.id, now.title, kind, before = was.text, after = now.text)
                }
                else -> null
            }
        }
        return SyncDetails(before.lastModified(), changes, rebuilt = true)
    }

    /** "Manter a atual" (ver [LibraryRepository.revertSync]). false = a letra mudou depois. */
    suspend fun revert(key: String): Boolean = decide(key, ::keepMine)

    /** "Aceitar a atualizada": registra a escolha; na música em que o usuário tinha a versão dele, troca pela nova. */
    suspend fun keep(key: String): Boolean = decide(key, ::acceptNew)

    /** Música nova que o usuário não quis: sai da biblioteca (e dos setlists). */
    suspend fun removeNew(key: String): Boolean = decide(key) { c ->
        if (c.kind != SyncKind.ADDED) null else c.copy(removed = true).also { repository.deleteSong(c.songId) }
    }

    /**
     * A mesma escolha para todas as pendentes destes tipos (muitas músicas de uma vez): [accept] = aceitar a
     * atualizada (nas novas: ficar), senão manter a atual (nas novas: remover). Grava os detalhes uma vez só.
     * Devolve quantas não deram (letra editada depois da atualização).
     */
    suspend fun decideAll(kinds: Set<SyncKind>, accept: Boolean): Int = detailsLock.withLock {
        val d = detailsLocked() ?: return@withLock 0
        var refused = 0
        val done = d.copy(changes = d.changes.map { c ->
            if (c.decided || c.kind !in kinds) c
            else (when {
                c.kind == SyncKind.ADDED && !accept -> c.copy(removed = true).also { repository.deleteSong(c.songId) }
                accept -> acceptNew(c)
                else -> keepMine(c)
            }) ?: c.also { refused++ }
        })
        cached = done
        withContext(background) { runCatching { saveDetails(done) } }
        refused
    }

    private fun keepMine(c: SyncChange): SyncChange? = when (c.kind) {
        SyncKind.ADDED -> c.copy(kept = true)
        SyncKind.PROTECTED -> c.copy(reverted = true).also { repository.declineSync(c) }
        SyncKind.META_PROTECTED -> c.copy(reverted = true).also { repository.declineMeta(c) }
        SyncKind.META -> if (repository.revertMeta(c)) c.copy(reverted = true) else null
        SyncKind.UPDATED, SyncKind.SECTIONS -> if (repository.revertSync(c)) c.copy(reverted = true) else null
    }

    private fun acceptNew(c: SyncChange): SyncChange? = when (c.kind) {
        SyncKind.PROTECTED -> if (repository.acceptSync(c)) c.copy(kept = true) else null
        SyncKind.META_PROTECTED -> if (repository.acceptMeta(c)) c.copy(kept = true) else null
        else -> c.copy(kept = true)
    }

    /** Uma escolha de cada vez (dois toques seguidos não apagam a marca um do outro); null = não deu. */
    private suspend fun decide(key: String, choose: (SyncChange) -> SyncChange?): Boolean = detailsLock.withLock {
        val d = detailsLocked() ?: return@withLock false
        val c = d.changes.firstOrNull { it.key == key && !it.decided } ?: return@withLock false
        val chosen = choose(c) ?: return@withLock false
        val done = d.copy(changes = d.changes.map { if (it === c) chosen else it })
        cached = done
        withContext(background) { runCatching { saveDetails(done) } }
        true
    }

    private fun showSaved() {
        if (!stage.onStage.value) savedNotice()?.let { _notice.value = it }
    }

    private fun savedNotice(): SyncNotice? = SyncNotice.decode(prefs.getString(KEY_NOTICE, null))

    private fun discardPending() {
        pending.delete()
        prefs.edit { remove(KEY_PENDING) }
    }

    /** A internet mudou (callback do Android; os testes chamam direto). */
    internal fun onNetwork(validated: Boolean) {
        val back = validated && !wasOnline
        wasOnline = validated
        if (back) start()
    }

    @Synchronized
    private fun watch() {
        if (watching || connectivity == null) return
        wasOnline = false
        watching = runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure { Log.w(TAG, "não consegui acompanhar a internet", it) }.isSuccess
    }

    @Synchronized
    private fun unwatch() {
        if (!watching) return
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        watching = false
    }

    private companion object {
        const val TAG = "stagebook"
        const val KEY_SYNCED = "biblioteca_aplicada"   // marca da última biblioteca aplicada
        const val KEY_PENDING = "biblioteca_pendente"   // marca da baixada que espera ser aplicada
        const val KEY_NOTICE = "aviso"
        const val KEY_CHECK = "ultima_verificacao"   // "hora,resultado,quantas"
    }
}

/** Última conferência da biblioteca publicada: quando e o que deu. */
data class LastCheck(val at: Long, val result: Result, val count: Int = 0) {
    enum class Result { UP_TO_DATE, NOTHING_CHANGED, CHANGED }
}
