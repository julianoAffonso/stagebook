package br.app.stagebook.data

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** O que está publicado no último release do GitHub (o publicar.sh grava junto com o APK). */
@Serializable
data class PublishedVersion(val versionCode: Long, val versionName: String)

/**
 * Atualização do app pelo último release do GitHub: lê a versão publicada, baixa o APK e abre o
 * instalador do Android (ele sempre pede o toque em "Instalar").
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    // endereços dos arquivos fixos (os testes trocam por um servidor local)
    internal var apkUrl = APK_URL
    internal var versionUrl = VERSION_URL

    // a versão não muda com o app aberto (atualizar reinicia o processo): pergunta ao sistema uma vez só
    val installedCode: Long by lazy {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }

    // última leitura (hora, conteúdo): na mesma abertura as duas atualizações usam a mesma
    private val last = AtomicReference<Pair<Long, PublishedVersion>?>(null)

    /**
     * O que está publicado, ou null sem internet / sem resposta. Reaproveita uma leitura de até [maxAgeMs]
     * (0 = lê agora, como no "Procurar atualização" dos Ajustes).
     */
    suspend fun published(on: CoroutineContext = Dispatchers.IO, maxAgeMs: Long = 10 * 60 * 1000L): PublishedVersion? {
        last.get()?.let { (at, p) -> if (SystemClock.elapsedRealtime() - at < maxAgeMs) return p }
        return withContext(on) {
            runCatching { json.decodeFromString<PublishedVersion>(get(versionUrl) { input, _ -> input.bufferedReader().readText() }) }
                .onFailure { Log.w(TAG, "não li a versão publicada", it) }
                .getOrNull()
                ?.also { last.set(SystemClock.elapsedRealtime() to it) }
        }
    }

    /**
     * Baixa o APK publicado e confere que é o Stagebook e mais novo que o instalado. `progress` vai de
     * 0 a 100. Lança IOException se não baixar ou não conferir.
     */
    suspend fun download(progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "atualizacao").apply { mkdirs() }
        val apk = File(dir, "stagebook.apk")
        val part = File(dir, "stagebook.apk.part")
        get(apkUrl) { input, total ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) progress((done * 100 / total).toInt())
                }
            }
        }
        val info = context.packageManager.getPackageArchiveInfo(part.path, 0)
        if (info?.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(info) <= installedCode) {
            part.delete()
            throw IOException("o arquivo baixado não é uma versão mais nova do Stagebook")
        }
        if (!part.renameTo(apk)) throw IOException("não consegui guardar o arquivo baixado")
        apk
    }

    /** Abre o instalador do Android com o APK baixado. */
    fun installIntent(apk: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** O Android deixa este app instalar o APK, ou ainda precisa liberar "instalar apps desta fonte". */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Apaga o APK de uma atualização já instalada (ou abandonada). */
    fun clean() {
        File(context.cacheDir, "atualizacao").deleteRecursively()
    }

    private fun <T> get(url: String, read: (InputStream, Long) -> T): T {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { read(it, c.contentLengthLong) }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val TAG = "stagebook"
        const val APK_MIME = "application/vnd.android.package-archive"
        // última versão publicada no GitHub Releases: "latest/download" sempre aponta para o release mais novo
        const val RELEASES = "https://github.com/julianoAffonso/stagebook/releases/latest/download/"
        const val APK_URL = RELEASES + "stagebook.apk"
        const val VERSION_URL = RELEASES + "stagebook-version.json"
    }
}
