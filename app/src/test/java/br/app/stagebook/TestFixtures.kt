package br.app.stagebook

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppUpdater
import br.app.stagebook.data.Backup
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.DeezerBpm
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LastStage
import br.app.stagebook.data.LibraryEnricher
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MetronomeSound
import br.app.stagebook.data.MidiOut
import br.app.stagebook.data.MidiPedal
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.TrackStore
import br.app.stagebook.player.StagePlayer
import br.app.stagebook.ui.settings.SettingsViewModel
import br.app.stagebook.ui.stage.StageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The one place the tests build the app's pieces (audit 22/11: the same setup was copied in dozens of files, and every
 * new constructor parameter meant editing all of them). A constructor that changes is fixed here.
 */

/** Robolectric's application: a new one for each test. */
internal fun appContext(): Application = ApplicationProvider.getApplicationContext()

private val liveScopes = mutableListOf<CoroutineScope>()
private var scopesOf: Application? = null

/**
 * A background scope for a test's repository and services. The scopes of the test before are cancelled when the next
 * test asks for its first one (Robolectric gives each test a new application): their writers and collectors used to live
 * on in the same JVM, under the tests that followed.
 */
internal fun testScope(): CoroutineScope = synchronized(liveScopes) {
    val app = appContext()
    if (scopesOf !== app) {
        liveScopes.forEach { it.cancel() }
        liveScopes.clear()
        scopesOf = app
    }
    CoroutineScope(SupervisorJob() + Dispatchers.IO).also { liveScopes += it }
}

/** How long a test waits for something the app does in the background. */
// The longest a test waits for something another thread does, everywhere (it is only a ceiling: a passing test never
// waits it out). GitHub's runner, on a slow day, went past the 5 s many tests had each fixed for themselves: four
// different old tests failed the Check of one PR in a row (23/11)
internal const val TEST_WAIT_MS = 30_000L

/** Waits until [what] holds, or fails with [TEST_WAIT_MS] (or [ms]) up. */
internal fun awaitTrue(ms: Long = TEST_WAIT_MS, what: () -> Boolean) = runBlocking { withTimeout(ms) { while (!what()) delay(10) } }

/**
 * A real library, already read from the disk. [empty]: without the sample songs of a first run; [bandOn]: with the band
 * on the local network turned on.
 */
internal fun testRepo(context: android.content.Context = appContext(), scope: CoroutineScope = testScope(), empty: Boolean = false, bandOn: Boolean = false): LibraryRepository =
    LibraryRepository(context, scope).also { r ->
        runBlocking {
            withTimeout(TEST_WAIT_MS) { r.loaded.first { it } }
            if (empty) r.restore(emptyList(), emptyList())
        }
        if (bandOn) {
            r.updateLook { it.copy(bandOn = true) }
            awaitTrue { r.library.value.look.bandOn }
        }
    }

internal fun testLink(
    context: android.content.Context = appContext(),
    repo: LibraryRepository,
    identity: DeviceIdentity = DeviceIdentity(context),
    trust: BandTrust = BandTrust(context),
    stage: StageGate = StageGate(),
    inbox: BandInbox = BandInbox(context),
    pdfs: PdfStore = PdfStore(context),
    inspector: PdfInspector = PdfInspector(),
): BandLink = BandLink(context, identity, trust, stage, inbox, repo, pdfs, inspector)

internal fun testConduct(
    context: android.content.Context = appContext(),
    repo: LibraryRepository,
    link: BandLink = testLink(context, repo),
    identity: DeviceIdentity = DeviceIdentity(context),
): BandConduct = BandConduct(link, repo, identity)

internal fun testEnricher(repo: LibraryRepository, scope: CoroutineScope = testScope(), context: android.content.Context = appContext()): LibraryEnricher =
    LibraryEnricher(repo, MusicBrainz(), DeezerBpm(), Connectivity(context), scope, TrackStore(context), context)

internal fun testSettingsVm(
    repo: LibraryRepository,
    context: android.content.Context = appContext(),
    scope: CoroutineScope = testScope(),
    updater: AppUpdater = AppUpdater(context),
    backup: Backup = Backup(context, repo, TrackStore(context)),
    midi: MidiPedal = MidiPedal(context, repo, scope),
): SettingsViewModel = SettingsViewModel(
    repo, context, updater, midi, backup, MetronomeSound(context), MidiOut(context, repo, scope), StagePlayer(context, repo), testEnricher(repo, scope, context),
    br.app.stagebook.data.ExternalImageStore(context),
)

/** A stage with the real pieces around it (no audio plays in a test); [show] is where the setlist's marks go. */
internal fun testStage(
    context: Application,
    repo: LibraryRepository,
    saved: SavedStateHandle,
    show: SetlistShowStore = SetlistShowStore(context, repo, testScope()),
    bandTrust: BandTrust = BandTrust(context),
    lastStage: LastStage = LastStage(context),
    link: BandLink = testLink(context, repo),
    conduct: BandConduct = testConduct(context, repo, link),
) = StageViewModel(
    repo, saved, StageGate(), TrackStore(context), StagePlayer(context, repo), conduct, MetronomeSound(context), br.app.stagebook.data.ChordPlayer(),
    br.app.stagebook.data.StageAnnouncer(link), MidiOut(context, repo, testScope()), show, bandTrust, lastStage,
)

/** The band member the conduct tests hear from. */
internal val VOICE = br.app.stagebook.domain.BandProtocol.Hello("voz", "Voz", "key")
