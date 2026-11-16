package br.app.stagebook

import android.app.Application
import android.content.Context
import br.app.stagebook.data.Languages
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.backgroundScope
import br.app.stagebook.data.device.DeviceTweaks
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StagebookApp : Application() {
    @Inject lateinit var repository: LibraryRepository
    @Inject lateinit var tracks: TrackStore
    @Inject lateinit var pdfs: br.app.stagebook.data.PdfStore
    @Inject lateinit var inbox: br.app.stagebook.data.BandInbox
    // made at start so it follows the USB cable from the first moment (the interface shows as soon as it's plugged in)
    @Inject lateinit var player: br.app.stagebook.player.StagePlayer

    // texts read outside the screens (notices, messages) also use the chosen language
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // Settings → Advanced: what Android put back by itself is written again (only with the permission, and the
        // player's tweaks only with the player on)
        backgroundScope(Dispatchers.IO).launch {
            repository.loaded.first { it }
            DeviceTweaks.ensure(this@StagebookApp, repository.library.value.look.playerMode)
        }
        // the app was closed with the stage open (killed, crash): its Do not disturb doesn't stay on
        backgroundScope(Dispatchers.IO).launch { br.app.stagebook.data.device.StageQuiet.clearStale(this@StagebookApp) }
        // backing tracks no song points to any more (deleted songs) leave the disk
        backgroundScope(Dispatchers.IO).launch {
            repository.loaded.first { it }
            tracks.sweep(repository.library.value.songs.mapTo(HashSet()) { it.track })
            // the PDFs a band member sent and the inbox still holds are in use too (a PDF song nobody accepted yet)
            pdfs.sweep(repository.library.value.songs.mapTo(HashSet()) { it.pdf } + inbox.items.value.flatMap { it.songs }.mapTo(HashSet()) { it.pdfFile })
        }
    }
}
