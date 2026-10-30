package br.app.stagebook

import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.StageGate
import br.app.stagebook.ui.band.BandRequestDialog
import br.app.stagebook.data.BandLink
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.AppUpdateOffer
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.Languages
import br.app.stagebook.data.MidiPedal
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.navigation.AppUpdateActions
import br.app.stagebook.ui.installApk
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
import br.app.stagebook.navigation.StagebookNavHost
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.theme.StagebookTheme
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
    @Inject lateinit var conduct: br.app.stagebook.data.BandConduct   // created here so a follower answers right away
    private val pedal = PedalInput()

    // language chosen in settings (changing it recreates the Activity)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        hideSystemBars()
        // "keep the screen on" applies while the app is in front; outside it Android dims as usual
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.library.map { it.look.keepScreenOn }.distinctUntilChanged().collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        // MIDI pedal (USB and Bluetooth): presses take the same path as keys, on the screen's line
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
                // "So-and-so wants to join the band" on any screen, one at a time. On stage nothing shows on top: the
                // show is never interrupted.
                val requests by band.requests.collectAsStateWithLifecycle()
                val onStage by stageGate.onStage.collectAsStateWithLifecycle()
                if (!onStage) requests.firstOrNull()?.let { r -> BandRequestDialog(r.name) { ok -> band.answer(r, ok) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // new app version: checks in the background and asks off stage (not in essential mode without internet, T19)
        if (repository.library.value.look.internetOn) updateOffer.onEnter()
        band.setForeground(true)   // band on the network: only with the app in front
        bandInternet.onAppOpen()   // band over the internet: checks the inbox once, on open
    }

    override fun onStop() {
        band.setForeground(false)
        super.onStop()
    }

    // the app is always full screen (show): system bars hidden; a swipe from the edge shows them for a moment.
    // Reapplied when coming back to the app or after a dialog or keyboard.
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

    // the pedal is a Bluetooth keyboard: keys go to the stage (or the pedal test) before anything else. RestrictedApi
    // is a lint false positive; this is Android's public Activity.dispatchKeyEvent.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = pedal.dispatch(event) || super.dispatchKeyEvent(event)
}
