package br.app.stagebook

import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.StageGate
import br.app.stagebook.ui.band.BandRequestDialog
import br.app.stagebook.data.BandLink
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.receiveAsFlow
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
import br.app.stagebook.data.interfaceKinds
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.ui.platform.ComposeView
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import br.app.stagebook.navigation.StagebookNavHost
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.PedalKeyRoot
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
    @Inject lateinit var webEditor: br.app.stagebook.data.web.WebEditorServer
    @Inject lateinit var conduct: br.app.stagebook.data.BandConduct   // created here so a follower answers right away
    @Inject lateinit var trust: br.app.stagebook.data.BandTrust
    @Inject lateinit var receiveLinks: br.app.stagebook.data.ReceiveLinks
    @Inject lateinit var incomingFiles: br.app.stagebook.data.IncomingFiles
    private val pedal = PedalInput()

    // receive links (user, 07/11): tapped in a message or left in the clipboard by the site; the screens open the
    // receive screen with the code. Buffered: a link that opened the app arrives before the screens are drawn.
    private val receiveCodes = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    private var clipboardPending = false
    // a .stagebook file tapped outside the app (file manager, download, chat): checked, then Open file in Settings
    private val openedFiles = kotlinx.coroutines.channels.Channel<br.app.stagebook.data.IncomingFiles.Result>(kotlinx.coroutines.channels.Channel.BUFFERED)

    // already in a band: the band is used as soon as the app opens, so the local network permission is asked on open
    // (otherwise Android shows its device chooser instead); without a band it's asked in Settings → Band (user, 31/10)
    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) band.update() }

    // recording permission, asked with a word first when a USB audio interface is plugged in: without it Android greys
    // out "Always" for an interface that also records (a mixer), so every time the cable came back it asked for the
    // USB permission again and opened another app on top, mid-song (device test, 02/11)
    private val recordAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val askRecord = androidx.compose.runtime.mutableStateOf(false)

    private fun checkUsbRecord() {
        if (recordAsked || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val usb = getSystemService(android.hardware.usb.UsbManager::class.java) ?: return
        if (usb.deviceList.values.any { br.app.stagebook.data.isUsbAudio(it.interfaceKinds()) }) askRecord.value = true
    }

    // language chosen in settings (changing it recreates the Activity)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Languages.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // just updated from inside the app: where it was (a fresh start only; a recreation keeps its own screen)
        val resume = if (savedInstanceState == null) br.app.stagebook.data.ResumeAfterUpdate.take(this) else null
        hideSystemBars()
        // recreated (font size, language): the system's hide animation of the status bar stays stuck half way and the bar
        // stays drawn over the app even though it reports hidden; showing and hiding it again a moment later frees it
        // (device test B-19: only leaving and coming back cleared it)
        if (savedInstanceState != null) {
            window.decorView.postDelayed({ WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars()) }, BARS_RESET_MS)
            window.decorView.postDelayed({ hideSystemBars() }, BARS_RESET_MS * 2)
        }
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
        if (repository.library.value.look.bandOn && trust.members.value.isNotEmpty() && needsLocalNetwork(this)) localNetwork.launch(LOCAL_NETWORK)
        if (savedInstanceState == null) openLink(intent)   // recreated: the link was already opened
        val codes = receiveCodes.receiveAsFlow()
        val files = openedFiles.receiveAsFlow()
        // pedal keys go through a root that sees them before Android drops the first one after a touch (PedalKeyRoot)
        setContentView(PedalKeyRoot(this, pedal).apply { addView(ComposeView(context).apply { setContent {
            StagebookTheme {
                val offer by updateOffer.offer.collectAsStateWithLifecycle()
                val updateActions = remember {
                    AppUpdateActions(
                        onUpdate = { updateOffer.accept() },
                        onLater = updateOffer::decline,
                        onInstall = {
                            (updateOffer.offer.value as? UpdateOffer.Ready)?.let {
                                // the screen it was on comes back after the update (user, 09/11)
                                val route = br.app.stagebook.data.ResumeAfterUpdate.currentRoute ?: br.app.stagebook.data.ResumeAfterUpdate.HOME
                                // a session started: the window closes so the "installing" message shows (it sat behind it, 09/11)
                                lifecycleScope.launch { if (installApk(updater, it.apk, route)) updateOffer.dismiss() }
                            }
                        },
                        onClose = updateOffer::dismiss,
                    )
                }
                CompositionLocalProvider(LocalPedalInput provides pedal) { StagebookNavHost(offer, updateActions, conduct.openSong, conduct.followingSong, codes, stageGate.onStage, br.app.stagebook.data.InstallResults.notices, files, resume) }
                if (askRecord.value) {
                    br.app.stagebook.ui.components.ConfirmDialog(
                        androidx.compose.ui.res.stringResource(R.string.usb_record_why),
                        androidx.compose.ui.res.stringResource(R.string.usb_record_allow),
                        { recordAudio.launch(android.Manifest.permission.RECORD_AUDIO) },
                        { askRecord.value = false; recordAsked = true },
                        destructive = false,
                    )
                }
                // "So-and-so wants to join the band" on any screen, one at a time. On stage nothing shows on top: the
                // show is never interrupted.
                val requests by band.requests.collectAsStateWithLifecycle()
                val onStage by stageGate.onStage.collectAsStateWithLifecycle()
                if (!onStage) requests.firstOrNull()?.let { r -> BandRequestDialog(r.name) { ok -> band.answer(r, ok) } }
                // the songs that just lost their audio (a file opened without the tracks, Android's own copy), named once
                val lostAudio by repository.lostAudio.collectAsStateWithLifecycle()
                if (!onStage && lostAudio.isNotEmpty()) br.app.stagebook.ui.components.LostAudioDialog(lostAudio, repository::lostAudioSeen)
                // "Chrome · Windows wants to edit" (editor on the computer, T37): also never on stage; the browser waits
                val webRequests by webEditor.auth.requests.collectAsStateWithLifecycle()
                if (!onStage && requests.isEmpty()) webRequests.firstOrNull()?.let { r ->
                    br.app.stagebook.ui.web.WebRequestDialog(r.name, r.ip, r.code) { ok -> webEditor.auth.answer(r.id, ok) }
                }
            }
        } }) })
    }

    override fun onStart() {
        super.onStart()
        // also here, not only on focus: a recreated screen (font size, language) already has focus and the status bar
        // stayed over the app (device test B-19). After the window is attached: called right away it did nothing
        window.decorView.post { hideSystemBars() }
        // new app version: checks in the background and asks off stage (not in essential mode without internet, T19)
        if (repository.library.value.look.internetOn) updateOffer.onEnter()
        band.setForeground(true)   // band on the network: only with the app in front
        webEditor.setForeground(true)   // editor on the computer: only with the app open (T37)
        midi.setForeground(true)   // the saved Bluetooth pedal is looked for only with the app in front
        bandInternet.onAppOpen()   // band over the internet: checks the inbox once, on open
        clipboardPending = true   // a link the site copied: looked at once, when the window gets focus
        br.app.stagebook.player.PlayerService.setAppVisible(true)   // out of sight and idle, the player turns off
    }

    override fun onResume() {
        super.onResume()
        br.app.stagebook.data.ResumeAfterUpdate.opened(this)
        checkUsbRecord()   // also when the app was opened by plugging in the interface
        window.decorView.post { hideSystemBars() }
    }

    override fun onStop() {
        band.setForeground(false)
        webEditor.setForeground(false)
        midi.setForeground(false)
        br.app.stagebook.player.PlayerService.setAppVisible(false)
        super.onStop()
    }

    // the app is always full screen (show): system bars hidden; a swipe from the edge shows them for a moment.
    // Reapplied when coming back to the app or after a dialog or keyboard.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
        if (hasFocus && clipboardPending) { clipboardPending = false; checkClipboard() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)   // singleTask: a link tapped with the app already open comes here
    }

    private fun openLink(intent: Intent?) {
        br.app.stagebook.data.IncomingFiles.fileUri(intent)?.let { uri ->
            // read now, while the sender's permission lasts; copied and checked off the main thread
            lifecycleScope.launch { openedFiles.send(incomingFiles.receive(uri)) }
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        receiveLinks.fromIntent(intent.dataString)?.let { receiveCodes.trySend(it) }
    }

    // the site couldn't open the app (not installed yet) and copied the link: opened once per code. Not with the
    // internet off (essential mode, T19: receiving by code is off) nor on stage (the show is never interrupted)
    private fun checkClipboard() {
        if (!repository.library.value.look.internetOn || stageGate.onStage.value) return
        receiveLinks.fromClipboard()?.let { receiveCodes.trySend(it) }
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

private const val BARS_RESET_MS = 250L

// asked once per app run: "Not now" isn't asked again until the app opens again
private var recordAsked = false


// the local network permission (a string: the constant isn't in older SDK stubs)
const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * The device has the local network permission and it isn't given yet. Checked on the permission itself, not on a version
 * number: where Android doesn't have it, the local network works without it and there is nothing to ask (user, 31/10).
 * Asked where it's used, as Android recommends: on open when already in a band, else in Settings → Band (31/10).
 */
fun needsLocalNetwork(c: Context): Boolean =
    runCatching { c.packageManager.getPermissionInfo(LOCAL_NETWORK, 0) }.isSuccess &&
        c.checkSelfPermission(LOCAL_NETWORK) != android.content.pm.PackageManager.PERMISSION_GRANTED
