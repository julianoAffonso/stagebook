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

/** What's new in a version, in the app's two languages. */
@Serializable
data class VersionNotes(val versionCode: Long, val versionName: String, val en: List<String> = emptyList(), val pt: List<String> = emptyList())

/** What's published in the latest GitHub release (the Release workflow saves it with the APK). */
@Serializable
data class PublishedVersion(val versionCode: Long, val versionName: String, val notes: List<VersionNotes> = emptyList()) {
    /** What someone on [installed] hasn't seen yet: every version skipped, newest first. */
    fun notesSince(installed: Long): List<VersionNotes> = notes.filter { it.versionCode > installed }
}

/**
 * App update from the latest GitHub release: reads the published version, downloads the APK and opens the Android
 * installer (it always asks for the "Install" tap).
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    // fixed file addresses (tests swap them for a local server)
    internal var apkUrl = APK_URL
    internal var versionUrl = VERSION_URL

    // the version doesn't change with the app open (updating restarts the process): ask the system only once
    val installedCode: Long by lazy {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }

    // last read (time, content): in the same launch both updates use the same one
    private val last = AtomicReference<Pair<Long, PublishedVersion>?>(null)

    /**
     * What's published, or null with no internet / no answer. Reuses a read up to [maxAgeMs] old (0 = read now, like
     * "Check for updates" in Settings).
     */
    suspend fun published(on: CoroutineContext = Dispatchers.IO, maxAgeMs: Long = 10 * 60 * 1000L): PublishedVersion? {
        last.get()?.let { (at, p) -> if (SystemClock.elapsedRealtime() - at < maxAgeMs) return p }
        return withContext(on) {
            runCatching { json.decodeFromString<PublishedVersion>(get(versionUrl) { input, _ -> input.bufferedReader().readText() }) }
                .onFailure { Log.w(TAG, "couldn't read the published version", it) }
                .getOrNull()
                ?.also { last.set(SystemClock.elapsedRealtime() to it) }
        }
    }

    /**
     * Downloads the published APK and checks it's Stagebook and newer than the installed one. `progress` goes from 0 to
     * 100. Throws IOException if it doesn't download or doesn't check out.
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
            throw IOException("the downloaded file isn't a newer Stagebook version")
        }
        if (!part.renameTo(apk)) throw IOException("couldn't save the downloaded file")
        apk
    }

    /** Opens the Android installer with the downloaded APK. */
    fun installIntent(apk: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Whether Android lets this app install the APK, or it still needs to allow "install apps from this source". */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Deletes the APK of an update already installed (or abandoned). */
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
        // latest version published on GitHub Releases: "latest/download" always points to the newest release
        const val RELEASES = "https://github.com/julianoAffonso/stagebook/releases/latest/download/"
        const val APK_URL = RELEASES + "stagebook.apk"
        const val VERSION_URL = RELEASES + "stagebook-version.json"
    }
}
