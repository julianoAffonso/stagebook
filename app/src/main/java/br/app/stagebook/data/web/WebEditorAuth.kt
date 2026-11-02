package br.app.stagebook.data.web

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who may use the editor on the computer (T37). Android doesn't see the computer's MAC address (Android 10+, and the
 * browser doesn't send it), so an approved browser gets a random key in a cookie: next time it
 * doesn't ask. Only the key's hash is saved; removing a computer applies right away.
 */
@Singleton
class WebEditorAuth(private val store: Store, private val now: () -> Long = System::currentTimeMillis) {
    @Inject constructor(@ApplicationContext context: Context) : this(PrefsStore(context))

    @Immutable
    @Serializable
    data class Device(val id: String, val name: String, val hash: String, val since: Long)

    /**
     * A browser asking to come in, waiting for the answer on the tablet: its network address and a short [code] the
     * page shows too, so a stranger can't pass for the musician's computer just by sending the same name.
     */
    @Immutable
    data class Request(val id: String, val name: String, val at: Long, val ip: String = "", val code: String = "")

    private sealed interface Answer {
        data class Approved(val key: String) : Answer
        data object Denied : Answer
    }

    /** Where the authorized computers are saved (prefs in the app, memory in the tests). */
    interface Store {
        fun load(): String?
        fun save(json: String)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<Device>> = _devices

    private val _requests = MutableStateFlow<List<Request>>(emptyList())
    val requests: StateFlow<List<Request>> = _requests
    private val answers = mutableMapOf<String, Answer>()
    private val deniedUntil = mutableMapOf<String, Long>()   // address → when it may ask again

    /** The computer for this cookie key, or null (unknown or removed). */
    fun device(key: String?): Device? {
        if (key.isNullOrEmpty()) return null
        val hash = hash(key)
        return _devices.value.firstOrNull { MessageDigest.isEqual(it.hash.toByteArray(), hash.toByteArray()) }
    }

    /**
     * A browser asks to come in: a new request on the tablet (at most [MAX_WAITING] at a time, one per address; an
     * address that was denied waits [DENIED_MS] before asking again). Null = refused for now.
     */
    @Synchronized
    fun ask(name: String, ip: String = ""): Request? {
        expire()
        if ((deniedUntil[ip] ?: 0L) > now()) return null
        _requests.value.firstOrNull { it.ip == ip && ip.isNotEmpty() }?.let { return it }   // asking again: the same request
        if (_requests.value.size >= MAX_WAITING) return null
        val r = Request(token(12), name.take(MAX_NAME).ifBlank { "?" }, now(), ip, (1000 + random.nextInt(9000)).toString())
        _requests.update { it + r }
        return r
    }

    /** Answer on the tablet. Approved saves the computer and leaves its key for the browser to pick up. */
    @Synchronized
    fun answer(id: String, ok: Boolean) {
        val r = _requests.value.firstOrNull { it.id == id } ?: return
        _requests.update { list -> list.filterNot { it.id == id } }
        answers[id] = if (ok) {
            val key = token(32)
            _devices.update { it + Device(token(8), r.name, hash(key), now()) }
            save()
            Answer.Approved(key)
        } else {
            if (r.ip.isNotEmpty()) deniedUntil[r.ip] = now() + DENIED_MS
            Answer.Denied
        }
    }

    /** What the waiting browser gets: the key (once), "denied", or null while nobody answered. */
    sealed interface Poll {
        data object Waiting : Poll
        data object Denied : Poll
        data object Unknown : Poll
        data class Approved(val key: String) : Poll
    }

    @Synchronized
    fun poll(id: String): Poll {
        expire()
        return when (val a = answers.remove(id)) {
            is Answer.Approved -> Poll.Approved(a.key)
            Answer.Denied -> Poll.Denied
            null -> if (_requests.value.any { it.id == id }) Poll.Waiting else Poll.Unknown
        }
    }

    fun remove(id: String) {
        _devices.update { list -> list.filterNot { it.id == id } }
        save()
    }

    // a request nobody answered goes away after a while (the browser asks again)
    private fun expire() {
        val limit = now() - REQUEST_TTL_MS
        _requests.update { list -> list.filter { it.at >= limit } }
        deniedUntil.values.removeAll { it <= now() }
    }

    private fun token(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun load(): List<Device> = runCatching { json.decodeFromString<List<Device>>(store.load() ?: return emptyList()) }.getOrDefault(emptyList())

    private fun save() = store.save(json.encodeToString(_devices.value))

    private class PrefsStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences("web_editor", Context.MODE_PRIVATE)
        override fun load(): String? = prefs.getString("devices", null)
        override fun save(json: String) = prefs.edit { putString("devices", json) }
    }

    companion object {
        const val MAX_WAITING = 3
        const val MAX_NAME = 60
        const val REQUEST_TTL_MS = 2 * 60_000L
        const val DENIED_MS = 5 * 60_000L

        fun hash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
