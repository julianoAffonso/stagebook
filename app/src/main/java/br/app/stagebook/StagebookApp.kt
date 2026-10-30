package br.app.stagebook

import android.app.Application
import android.content.Context
import br.app.stagebook.data.Languages
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StagebookApp : Application() {
    // textos lidos fora das telas (avisos, mensagens) também na língua escolhida
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))
}
