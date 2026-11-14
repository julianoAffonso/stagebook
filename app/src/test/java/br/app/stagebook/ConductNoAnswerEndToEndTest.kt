package br.app.stagebook

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.ConductViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The leader's "N didn't answer", from the real [BandConduct] to the strip's state ([ConductViewModel.ui]): who is
 * online is a fake the test moves. It used to be a number worked out when the message went and kept on the strip after
 * the person answered, left the network or the leader searched again (cd33907c made it follow who is online now).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ConductNoAnswerEndToEndTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val songs = listOf(Conduct.Item("Primeira"), Conduct.Item("Segunda"))

    private fun peer(id: String) = BandLink.Peer(id, id.uppercase(), java.net.InetAddress.getLoopbackAddress(), 1)

    private val repo = runBlocking {
        LibraryRepository(context, CoroutineScope(Dispatchers.IO)).also { r ->
            withTimeout(15_000) { r.loaded.first { it } }
            r.updateLook { it.copy(bandOn = true) }
            withTimeout(15_000) { r.library.first { it.look.bandOn } }
        }
    }
    private val link = BandLink(
        context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), BandInbox(context), repo,
    )

    /** Who is on the network now, as the leader sees it. */
    private val online = MutableStateFlow(listOf(peer("a"), peer("b")))
    private var answering = setOf("a")   // who answers the next message
    private val leader = BandConduct(link, repo, DeviceIdentity(context), online).also { c ->
        c.deliver = { _ -> answering.associateWith { Conduct.Lack(it.uppercase(), emptyList()) } }
    }
    private val vm = ConductViewModel(leader, repo, DeviceIdentity(context))

    init { CoroutineScope(Dispatchers.Unconfined).launch { vm.ui.collect {} } }

    private fun noAnswer(): Int? = (vm.ui.value as? ConductUi.Conducting)?.noAnswer

    /** Waits (letting the main looper run, where the view model works) until the strip says [want]. */
    private fun strip(want: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (noAnswer() != want && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { delay(20) }
        }
        assertEquals(want, noAnswer())
    }

    private fun lead() { leader.start("s1", "Show", songs, 0) }

    @Test fun `one of two online didn't answer`() {
        lead()
        strip(1)
    }

    @Test fun `the person answers the next message and the strip stops saying it`() {
        lead()
        strip(1)
        answering = setOf("a", "b")
        leader.open("s1", 1)
        strip(0)
    }

    @Test fun `the person leaves the network and the strip stops saying it`() {
        lead()
        strip(1)
        online.value = listOf(peer("a"))
        strip(0)
    }
}
