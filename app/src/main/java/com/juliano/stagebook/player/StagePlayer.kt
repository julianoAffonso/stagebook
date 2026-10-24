package com.juliano.stagebook.player

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.edit
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.juliano.stagebook.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** O que o palco mostra do player: tocando, esperando a saída USB para tocar, e o aviso atual (vazio = nenhum). */
@Immutable
data class PlayerUi(val playing: Boolean = false, val waiting: Boolean = false, val message: String = "")

/**
 * Ponte do palco com o motor. Nada do player sobe antes do primeiro play: nem o serviço, nem a biblioteca nativa.
 * A música aberta só anota o backing track; ele é carregado no play (ou ao trocar de música com o player já ligado).
 * No fim do backing track, para e volta ao início, esperando o próximo play.
 */
@Singleton
class StagePlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = Mutex()   // load e seek leem o disco: fora da tela e um de cada vez
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui

    @Volatile private var wanted: File? = null    // backing track da música aberta
    @Volatile private var loaded: File? = null    // o que está no motor
    private var durationMs = -1L
    private var endWatch: Job? = null

    init { instance = this }

    /** Música aberta no palco (null = sem backing track): para o que estiver tocando e anota a nova. */
    fun open(track: File?) {
        if (track == wanted) return
        wanted = track
        if (_ui.value.playing || _ui.value.waiting) pause()
        if (PlayerService.running) scope.launch { load() }
    }

    /** Pedir para sair da economia de bateria (tocar com a tela apagada)? Só uma vez, e só se ainda não saiu. */
    fun needsBatteryAsk(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return !prefs.getBoolean(KEY_BATTERY_ASKED, false) && !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryAsked() = prefs.edit { putBoolean(KEY_BATTERY_ASKED, true) }

    private val prefs get() = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    fun toggle() = if (_ui.value.playing || _ui.value.waiting) pause() else play()

    fun play() {
        if (wanted == null) return
        if (!PlayerService.running) {
            ContextCompat.startForegroundService(context, Intent(context, PlayerService::class.java))
        }
        _ui.update { it.copy(waiting = true) }
        scope.launch {
            if (!load()) {
                _ui.value = PlayerUi(message = context.getString(R.string.player_no_track))
                return@launch
            }
            startIfConnected()
        }
    }

    fun pause() {
        endWatch?.cancel()
        _ui.update { it.copy(playing = false, waiting = false) }
        if (PlayerService.running) {
            Engine.pause()
            PlayerService.playing(false)
        }
    }

    /** Desliga o player (fechar o modo player): libera USB, motor e serviço. */
    fun shutdown() {
        pause()
        loaded = null
        if (PlayerService.running) {
            context.startService(Intent(context, PlayerService::class.java).setAction(PlayerService.ACTION_STOP))
        }
    }

    private suspend fun load(): Boolean = engine.withLock {
        val f = wanted ?: return@withLock false
        if (f == loaded) return@withLock durationMs >= 0
        durationMs = Engine.load(f.absolutePath)
        loaded = f
        durationMs >= 0
    }

    // Toca só com a saída pronta (USB ou do aparelho); sem ela, o play fica esperando (o serviço avisa quando
    // conectar). Também retoma depois de uma reconexão: parar o motor zera o play dele, mas não a posição.
    private fun startIfConnected() {
        val ui = _ui.value
        if (!(ui.waiting || ui.playing) || !PlayerService.connected || loaded == null || loaded != wanted) return
        Engine.play()
        PlayerService.playing(true)
        _ui.update { it.copy(playing = true, waiting = false) }
        endWatch?.cancel()
        endWatch = scope.launch {
            while (isActive) {
                delay(END_POLL_MS)
                if (durationMs > 0 && Engine.position() >= durationMs - END_MARGIN_MS) {
                    pause()
                    engine.withLock { Engine.seek(0) }
                }
            }
        }
    }

    private fun serviceChanged() {
        _ui.update { it.copy(message = PlayerService.status) }
        if (PlayerService.connected) {
            startIfConnected()
        } else if (_ui.value.playing) {
            // a saída caiu tocando: o motor segura a posição e o serviço reconecta sozinho em até 5 s
            _ui.update { it.copy(playing = false, waiting = true) }
        }
    }

    companion object {
        const val END_POLL_MS = 250L
        private const val KEY_BATTERY_ASKED = "batteryAsked"
        const val END_MARGIN_MS = 300L   // o fim real chega quando o último pacote sai; antes disso, já é fim

        @Volatile private var instance: StagePlayer? = null

        /** Modo player desligado nos Ajustes: se o player chegou a subir, desliga tudo. */
        fun turnOff() { instance?.shutdown() }

        /** O serviço mudou de estado (conectou, caiu, desligou): chamado na thread principal. */
        @JvmStatic
        fun onServiceChanged() { instance?.serviceChanged() }
    }
}
