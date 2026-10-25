package com.juliano.stagebook

import com.juliano.stagebook.data.BandInternet
import com.juliano.stagebook.data.StageGate
import com.juliano.stagebook.ui.band.BandRequestDialog
import com.juliano.stagebook.data.BandLink
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.AppUpdateOffer
import com.juliano.stagebook.data.AppUpdater
import com.juliano.stagebook.data.Languages
import com.juliano.stagebook.data.MidiPedal
import com.juliano.stagebook.data.UpdateOffer
import com.juliano.stagebook.navigation.AppUpdateActions
import com.juliano.stagebook.ui.installApk
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.juliano.stagebook.navigation.StagebookNavHost
import com.juliano.stagebook.ui.LocalPedalInput
import com.juliano.stagebook.ui.PedalInput
import com.juliano.stagebook.ui.theme.StagebookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: LibraryRepository
    @Inject lateinit var updateOffer: AppUpdateOffer
    @Inject lateinit var updater: AppUpdater
    @Inject lateinit var midi: MidiPedal
    @Inject lateinit var band: BandLink
    @Inject lateinit var stageGate: StageGate
    @Inject lateinit var bandInternet: BandInternet
    @Inject lateinit var conduct: com.juliano.stagebook.data.BandConduct   // criado aqui: quem acompanha já responde
    private val pedal = PedalInput()

    // língua escolhida nos ajustes (trocar recria a Activity)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        hideSystemBars()
        // "manter a tela ligada": vale enquanto o app está na frente; fora dele o Android apaga normalmente
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.library.map { it.look.keepScreenOn }.distinctUntilChanged().collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        // pedal MIDI (USB e Bluetooth): os apertos entram pelo mesmo caminho das teclas, na linha da tela
        midi.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { midi.presses.collect { pedal.dispatch(it) } }
        }
        setContent {
            StagebookTheme {
                val offer by updateOffer.offer.collectAsStateWithLifecycle()
                val updateActions = remember {
                    AppUpdateActions(
                        onUpdate = { updateOffer.accept() },
                        onLater = updateOffer::decline,
                        onInstall = {
                            (updateOffer.offer.value as? UpdateOffer.Ready)?.let { installApk(updater.canInstall(), updater.installIntent(it.apk)) }
                        },
                        onClose = updateOffer::dismiss,
                    )
                }
                CompositionLocalProvider(LocalPedalInput provides pedal) { StagebookNavHost(offer, updateActions, conduct.openSong) }
                // "Fulano quer entrar na banda": em qualquer tela, um de cada vez
                // no palco, nada aparece por cima: o show nunca é interrompido
                val requests by band.requests.collectAsStateWithLifecycle()
                val onStage by stageGate.onStage.collectAsStateWithLifecycle()
                if (!onStage) requests.firstOrNull()?.let { r -> BandRequestDialog(r.name) { ok -> band.answer(r, ok) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateOffer.onEnter()   // versão nova do app: procura em segundo plano e pergunta fora do palco
        band.setForeground(true)   // banda na rede: só com o app na frente
        bandInternet.onAppOpen()   // banda pela internet: confere a caixa uma vez, ao abrir
    }

    override fun onStop() {
        band.setForeground(false)
        super.onStop()
    }

    // app sempre em tela cheia (show): barras do sistema escondidas; um deslize da borda as mostra
    // por um instante. Reaplica ao voltar para o app ou depois de um diálogo/teclado.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // o pedal é um teclado Bluetooth: as teclas vão para o palco (ou para o teste do pedal) antes do resto.
    // RestrictedApi: falso positivo do lint; o método é o Activity.dispatchKeyEvent público do Android.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pedal.dispatch(event) || super.dispatchKeyEvent(event)
}
