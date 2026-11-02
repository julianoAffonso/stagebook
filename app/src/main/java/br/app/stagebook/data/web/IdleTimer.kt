package br.app.stagebook.data.web

/**
 * The editor on the computer turns itself off after [timeoutMs] with no access (T37, user's decision of 30/10): left on
 * by mistake, it doesn't stay on during a show. The open page pings every minute, so a page left open keeps it on.
 */
class IdleTimer(private val timeoutMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    @Volatile private var last = now()

    fun touch() { last = now() }

    fun expired(): Boolean = now() - last > timeoutMs
}
