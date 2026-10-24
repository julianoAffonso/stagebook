package com.juliano.stagebook.data

import android.util.Log
import com.juliano.stagebook.di.ApplicationScope
import com.juliano.stagebook.di.LowPriority
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

/** O que o aviso de versão nova mostra. */
sealed interface UpdateOffer {
    data class Available(val versionName: String) : UpdateOffer
    data class Downloading(val percent: Int) : UpdateOffer
    data class Ready(val apk: File) : UpdateOffer   // baixado: falta o "Instalar" do Android
    data object Failed : UpdateOffer
}

/**
 * Versão nova do app oferecida sozinha: ao abrir o app com internet, procura em segundo plano (baixa
 * prioridade) e, fora do palco, pergunta se quer atualizar. "Agora não" vale até o dia seguinte para a
 * mesma versão. Atualizar baixa e abre o instalador, como em Ajustes → Atualizar o app.
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
    /** null = nada a oferecer agora. */
    val offer: StateFlow<UpdateOffer?> = _offer.asStateFlow()
    private var job: Job? = null
    private var code = 0L   // versão oferecida


    /** O app abriu (ou voltou para a frente): procura uma vez por abertura, se não está procurando nem oferecendo. */
    @Synchronized
    fun onEnter(): Job? {
        if (job?.isActive == true || _offer.value != null) return job
        job = scope.launch {
            val p = updater.published(background) ?: return@launch   // sem internet: na próxima abertura
            if (p.versionCode <= updater.installedCode) return@launch
            if (order.declinedToday(p.versionCode)) return@launch
            stage.onStage.first { !it }   // no palco: espera sair
            code = p.versionCode
            _offer.value = UpdateOffer.Available(p.versionName)
        }
        return job
    }

    /** "Agora não": a mesma versão só volta a ser oferecida amanhã. */
    fun decline() {
        order.defer(code, remember = true)
        _offer.value = null
    }

    /** "Atualizar": baixa (com o progresso no aviso) e deixa pronto para o instalador do Android. */
    fun accept(): Job = scope.launch {
        _offer.value = UpdateOffer.Downloading(0)
        _offer.value = try {
            val apk = updater.download { _offer.value = UpdateOffer.Downloading(it) }
            UpdateOffer.Ready(apk)
        } catch (e: IOException) {
            Log.w("stagebook", "versão nova não baixou", e)
            order.defer(code, remember = false)
            UpdateOffer.Failed
        }
    }

    /** O aviso fechou depois de abrir o instalador ou de falhar. */
    fun dismiss() {
        order.defer(code, remember = false)
        _offer.value = null
    }
}
