package br.app.stagebook

import android.app.Application
import android.content.Context
import br.app.stagebook.data.Languages
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StagebookApp : Application() {
    // texts read outside the screens (notices, messages) also use the chosen language
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))
}
