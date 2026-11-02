package br.app.stagebook.data

import android.util.Log
import br.app.stagebook.di.ApplicationScope
import br.app.stagebook.di.LowPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What the new version notice shows. */
sealed interface UpdateOffer {
    data class Available(val versionName: String) : UpdateOffer
    data class Downloading(val percent: Int) : UpdateOffer
    data class Ready(val apk: File) : UpdateOffer   // downloaded: Android's "Install" is still needed
    data object Failed : UpdateOffer
}

/**
 * New app version offered on its own: when the app opens with internet, it checks in the background (low priority) and,
 * off stage, asks whether to update. "Not now" holds until the next day for the same version. Update downloads and
 * opens the installer, like Settings → Update the app.
 */
@Singleton
class AppUpdateOffer @Inject constructor(
    private val updater: AppUpdater,
    private val stage: StageGate,
    private val order: UpdateOrder,
    @ApplicationScope private val scope: CoroutineScope,
    @LowPriority private val background: CoroutineDispatcher,
) {
    private val _offer = MutableStateFlow<UpdateOffer?>(null)
    /** null = nothing to offer right now. */
    val offer: StateFlow<UpdateOffer?> = _offer.asStateFlow()
    private var job: Job? = null
    private var code = 0L   // offered version


    /** The app opened (or came back to the front): checks once per launch, if it isn't already checking or offering. */
    @Synchronized
    fun onEnter(): Job? {
        if (job?.isActive == true || _offer.value != null) return job
        job = scope.launch {
            val p = updater.published(background) ?: return@launch   // no internet: next launch
            if (p.versionCode <= updater.installedCode) return@launch
            if (order.declinedToday(p.versionCode)) return@launch
            stage.onStage.first { !it }   // on stage: wait until leaving
            code = p.versionCode
            _offer.value = UpdateOffer.Available(p.versionName)
        }
        return job
    }

    /** "Not now": the same version is only offered again tomorrow. */
    fun decline() {
        order.defer(code, remember = true)
        _offer.value = null
    }

    /** "Update": downloads (with progress in the notice) and gets it ready for the Android installer. */
    fun accept(): Job = scope.launch {
        _offer.value = UpdateOffer.Downloading(0)
        _offer.value = try {
            val apk = updater.download { _offer.value = UpdateOffer.Downloading(it) }
            UpdateOffer.Ready(apk)
        } catch (e: IOException) {
            Log.w("stagebook", "new version download failed", e)
            order.defer(code, remember = false)
            UpdateOffer.Failed
        }
    }

    /** The notice closed after opening the installer or failing. */
    fun dismiss() {
        order.defer(code, remember = false)
        _offer.value = null
    }
}
