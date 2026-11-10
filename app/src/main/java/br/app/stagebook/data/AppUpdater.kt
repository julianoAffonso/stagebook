package br.app.stagebook.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * installer: through an installer session on Android 12+ (no tap when the system allows it), the system screen otherwise.
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    // fixed file addresses (tests swap them for a local server)
    internal var apkUrl = APK_URL
    internal var versionUrl = VERSION_URL

    // waits before each new try after a server error or a network failure (tests shorten them)
    internal var retryDelaysMs = longArrayOf(1000, 3000, 6000)

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

    /**
     * Installs through an installer session: on Android 12+ the system applies an update without asking again when this
     * app installed the previous version (the first time it still asks). False if the session couldn't start; the caller
     * then falls back to [installIntent]. The result comes later through [InstallResultReceiver]. Off the main thread:
     * copying the whole APK into the session froze the screen on a slow tablet.
     */
    suspend fun installWithSession(apk: File): Boolean = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        // the sessions let go below answer "aborted": that isn't about the one being started
        InstallResults.session = InstallResults.BETWEEN_SESSIONS
        // sessions left waiting by an earlier try (or by a version whose answer never arrived) hold the APK: let go first
        installer.mySessions.filter { it.appPackageName == context.packageName }
            .forEach { runCatching { installer.abandonSession(it.sessionId) } }
        var id = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            id = installer.createSession(params)
            InstallResults.session = id
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("stagebook.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) }
                }
                session.commit(InstallResultReceiver.pending(context, id).intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "couldn't start the install session", e)
            InstallResults.session = InstallResults.BETWEEN_SESSIONS
            if (id >= 0) runCatching { installer.abandonSession(id) }
            false
        }
    }

    /** Whether Android lets this app install the APK, or it still needs to allow "install apps from this source". */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Deletes the APK of an update already installed (or abandoned). */
    fun clean() {
        File(context.cacheDir, "atualizacao").deleteRecursively()
    }

    /**
     * Reads [url], trying again after a server error (5xx) or a network failure, waiting longer each time
     * ([retryDelaysMs]). A client error (4xx) is final, and a cancelled coroutine stops the wait at once.
     */
    private suspend fun <T> get(url: String, read: (InputStream, Long) -> T): T {
        var attempt = 0
        while (true) {
            try {
                return getOnce(url, read)
            } catch (e: IOException) {
                val wait = retryDelaysMs.getOrNull(attempt)
                if (e is ClientError || wait == null) throw e
                Log.w(TAG, "download of $url failed (${e.message}), trying again in $wait ms")
                attempt++
                delay(wait)
            }
        }
    }

    private class ClientError(code: Int) : IOException("HTTP $code")

    private fun <T> getOnce(url: String, read: (InputStream, Long) -> T): T {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        try {
            val code = c.responseCode
            if (code in 400..499) throw ClientError(code)
            if (code != 200) throw IOException("HTTP $code")
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
