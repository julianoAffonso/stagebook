package com.juliano.stagebook

import android.app.Application
import android.content.Context
import com.juliano.stagebook.data.Languages
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StagebookApp : Application() {
    // textos lidos fora das telas (avisos, mensagens) também na língua escolhida
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Languages.wrap(base))
}
