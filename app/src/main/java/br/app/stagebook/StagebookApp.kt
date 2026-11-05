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

    // texts read outside the screens (notices, messages) also use the chosen language
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // Settings → Advanced: what Android put back by itself is written again (only with the permission)
        br.app.stagebook.data.device.AutoBlocker.afterInstall(this)
        backgroundScope(Dispatchers.IO).launch { DeviceTweaks.ensure(this@StagebookApp) }
        // backing tracks no song points to any more (deleted songs) leave the disk
        backgroundScope(Dispatchers.IO).launch {
            repository.loaded.first { it }
            tracks.sweep(repository.library.value.songs.mapTo(HashSet()) { it.track })
        }
    }
}
