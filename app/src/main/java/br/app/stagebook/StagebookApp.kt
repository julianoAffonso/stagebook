package br.app.stagebook

import android.app.Application
import android.content.Context
import br.app.stagebook.data.Languages
import br.app.stagebook.data.backgroundScope
import br.app.stagebook.data.device.DeviceTweaks
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class StagebookApp : Application() {
    // texts read outside the screens (notices, messages) also use the chosen language
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))

    override fun onCreate() {
        super.onCreate()
        // Settings → Advanced: what Android put back by itself is written again (only with the permission)
        backgroundScope(Dispatchers.IO).launch { DeviceTweaks.ensure(this@StagebookApp) }
    }
}
