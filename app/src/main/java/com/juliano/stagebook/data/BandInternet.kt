package com.juliano.stagebook.data

import android.content.Context
import androidx.core.content.edit
import com.juliano.stagebook.domain.BandCrypto
import com.juliano.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A banda pela internet (sem servidor nosso: o mesmo ntfy.sh das 3 palavras, tudo cifrado), com **reenviar até chegar**:
 * - quem aceita um envio pelas 3 palavras vira "da banda" e devolve um "ok"; quem enviou guarda o "ok" e passa a confiar
 *   também (a mesma confiança da rede local) e responde "bem-vindo", para o outro parar de repetir;
 * - entre aparelhos da banda, cada par tem uma caixa própria; quem recebe devolve um recibo; sem recibo, quem enviou
 *   reenvia quando o serviço já apagou (12 h);
 * - ao se encontrarem no mesmo Wi-Fi (ensaio, show), o que estiver pendente passa na hora.
 *
 * **Bateria** (pedido do usuário): nada roda com o app fechado; ao abrir, tenta uma vez; a procura no Wi-Fi para acertar
 * dura 30 s e só acontece com algo pendente (nunca no palco); reenvio só depois de 12 h; cada pendência desiste depois de
 * 30 dias ou de 5 reenvios.
 */
@Singleton
class BandInternet @Inject constructor(
    @ApplicationContext context: Context,
    private val cloud: SetlistCloud,
    private val trust: BandTrust,
    private val identity: DeviceIdentity,
    private val inbox: BandInbox,
    private val connectivity: Connectivity,
    private val link: BandLink,
) {
    /** Um envio meu para a caixa de um membro, até chegar o recibo. */
    @Serializable
    private data class Out(val id: String, val to: String, val list: String, val songs: List<SetlistCloud.SharedSong>, val at: Long, val sentAt: Long, val tries: Int = 1)

    /** Envio meu pelas 3 palavras (para pegar o "ok" de quem aceitar). */
    @Serializable
    private data class MyCode(val code: String, val until: Long)

    /** Envio pelas palavras que eu aceitei: o meu "ok" vai sendo repetido até quem enviou dar as boas-vindas. */
    @Serializable
    private data class Accepted(val code: String, val from: String, val until: Long)

    private val prefs = context.getSharedPreferences("band_net", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    init {
        // o "ok" das palavras chegando pela rede local: confere contra os meus envios guardados
        link.codeAckHandler = { hello, body ->
            val h = cloud.checkAckBody(body, myCodes().map { it.code })
            if (h != null && h.id == hello.id) { trust.add(h.id, h.name, h.key); true } else false
        }
        // apareceu alguém da banda no Wi-Fi: entrega o que estiver pendente para ele (sem esperar o serviço)
        scope.launch { link.onlineMembers.collect { peers -> if (peers.isNotEmpty()) settleOnLan(peers) } }
    }

    private fun <T> load(key: String, ser: kotlinx.serialization.KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(ser), prefs.getString(key, null) ?: return emptyList()) }.getOrDefault(emptyList())

    private fun <T> save(key: String, ser: kotlinx.serialization.KSerializer<T>, list: List<T>) =
        prefs.edit { putString(key, json.encodeToString(ListSerializer(ser), list)) }

    private fun now() = System.currentTimeMillis()
    private fun outbox() = load(KEY_OUT, Out.serializer()).filter { now() - it.at < KEEP_MS && it.tries <= MAX_TRIES }
    private fun myCodes() = load(KEY_CODES, MyCode.serializer()).filter { it.until > now() }
    private fun accepted() = load(KEY_ACCEPTED, Accepted.serializer()).filter { it.until > now() }

    private fun pairKey(member: BandTrust.Member): ByteArray? =
        BandCrypto.publicKey(member.key)?.let { BandCrypto.pairKey(identity.privateKey, identity.id, it, member.id) }

    /** Um envio meu pelas 3 palavras: guardado por 30 dias para pegar os "ok" de quem aceitar (também na rede local). */
    fun remember(code: String) = save(KEY_CODES, MyCode.serializer(), myCodes() + MyCode(code, now() + KEEP_MS))

    /** Aceitei um envio pelas palavras: quem enviou vira da banda; o meu "ok" vai agora e é repetido até ele confirmar. */
    fun accepted(code: String, from: BandProtocol.Hello?) {
        from ?: return
        trust.add(from.id, from.name, from.key)
        save(KEY_ACCEPTED, Accepted.serializer(), accepted().filterNot { it.code == code } + Accepted(code, from.id, now() + KEEP_MS))
        scope.launch { cloud.ack(code) }
    }

    /** Os "ok" de um envio meu: quem aceitou vira da banda (e recebe as boas-vindas). Devolve os nomes novos. */
    suspend fun checkAcks(code: String): List<String> = cloud.acks(code).filterNot { trust.isMember(it.id) }.map { h ->
        trust.add(h.id, h.name, h.key)
        trust.member(h.id)?.let(::pairKey)?.let { cloud.welcome(it, h.id) }
        h.name
    }

    /** Ao abrir o app: uma passada só (internet, se houver) e, com algo pendente, 30 s de procura no Wi-Fi para acertar. */
    fun onAppOpen() = sync(force = false)

    /**
     * "Sincronizar com a banda" (menu Importar): a mesma passada, na hora e sem esperar reabrir o app; a procura no Wi-Fi
     * roda mesmo sem nada pendente (pode haver novidade de quem está perto).
     */
    fun syncNow() = sync(force = true)

    private fun sync(force: Boolean) {
        scope.launch {
            lock.withLock {
                val pending = outbox().isNotEmpty() || accepted().isNotEmpty()
                if (trust.members.value.isNotEmpty() && connectivity.isOnline()) runCatching { syncInternet() }
                if ((pending || force) && trust.members.value.isNotEmpty()) link.burst()
            }
        }
    }

    private suspend fun syncInternet() {
        myCodes().forEach { runCatching { checkAcks(it.code) } }
        accepted().forEach { runCatching { cloud.ack(it.code) } }   // repete o "ok" até chegar o bem-vindo
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        var out = outbox()
        var acc = accepted()
        trust.members.value.forEach { m ->
            val key = pairKey(m) ?: return@forEach
            val got = cloud.fetchPair(key, prefs.getString("$KEY_SINCE${m.id}", null)) ?: return@forEach
            // pediram músicas que faltavam no setlist que mandei (T12): responde com as que tenho, num pacote de músicas
            got.requests.forEach { wanted ->
                val ids = link.songsFor(wanted)
                if (ids.isNotEmpty()) cloud.songsPackage(ids)?.let { (list, songs) -> cloud.sendToPair(key, m.id, list, songs, java.util.UUID.randomUUID().toString()) }
            }
            got.packages.forEach { (pid, text, songs) ->
                // repetido (reenvio que chegou duas vezes) não entra de novo; o recibo vai de qualquer jeito
                if (seen.add("${m.id}|$pid")) inbox.add(m.id, m.name, text, songs)
                cloud.receipt(key, m.id, pid)
            }
            out = out.filterNot { it.to == m.id && it.id in got.receipts }
            if (got.welcome) acc = acc.filterNot { it.from == m.id }
            got.last?.let { prefs.edit { putString("$KEY_SINCE${m.id}", it) } }
        }
        // sem recibo e o serviço já apagou (12 h): manda de novo, com o mesmo id
        out = out.map { o ->
            if (now() - o.sentAt < RESEND_AFTER_MS) return@map o
            val m = trust.member(o.to) ?: return@map o
            val key = pairKey(m) ?: return@map o
            val ok = cloud.sendToPair(key, o.to, java.util.Base64.getDecoder().decode(o.list), o.songs, o.id)
            if (ok) o.copy(sentAt = now(), tries = o.tries + 1) else o
        }
        save(KEY_OUT, Out.serializer(), out)
        save(KEY_ACCEPTED, Accepted.serializer(), acc)
        prefs.edit { putStringSet(KEY_SEEN, seen.toList().takeLast(MAX_SEEN).toSet()) }
    }

    // no mesmo Wi-Fi: os envios sem recibo vão direto (e saem da fila); o meu "ok" pendente também
    private suspend fun settleOnLan(peers: List<BandLink.Peer>) = lock.withLock {
        val ids = peers.associateBy { it.id }
        var out = outbox()
        out = out.filterNot { o ->
            val peer = ids[o.to] ?: return@filterNot false
            val body = link.packageFor(o.to, java.util.Base64.getDecoder().decode(o.list), o.songs) ?: return@filterNot false
            link.sendTo(peer, body)
        }
        val acc = accepted().filterNot { a ->
            val peer = ids[a.from] ?: return@filterNot false
            val body = cloud.ackBody(a.code) ?: return@filterNot false
            link.sendTo(peer, body, BandProtocol.Kind.CODE_ACK)
        }
        save(KEY_OUT, Out.serializer(), out)
        save(KEY_ACCEPTED, Accepted.serializer(), acc)
    }

    /** Manda para quem é da banda e não está em [skip] (já recebeu pelo Wi-Fi), guardando até o recibo; para quantos foi. */
    suspend fun sendToBand(list: ByteArray, songs: List<SetlistCloud.SharedSong>, skip: Set<String>): Int = lock.withLock {
        val targets = trust.members.value.filterNot { it.id in skip }
        val listB64 = java.util.Base64.getEncoder().encodeToString(list)
        val sent = targets.mapNotNull { m ->
            val key = pairKey(m) ?: return@mapNotNull null
            val id = UUID.randomUUID().toString().take(8)
            val ok = cloud.sendToPair(key, m.id, list, songs, id)
            // mesmo se não foi agora (sem internet), fica na fila: vai ao abrir o app ou ao se encontrarem no Wi-Fi
            Out(id, m.id, listB64, songs, now(), if (ok) now() else 0L) to ok
        }
        save(KEY_OUT, Out.serializer(), outbox() + sent.map { it.first })
        sent.count { it.second }
    }

    /** Quantos envios esperam recibo (para a tela, se precisar). */
    fun pendingCount() = outbox().size

    /**
     * Pede pela internet a quem mandou o setlist (alguém da banda) as músicas que faltam (T12). Ele responde quando o app dele
     * sincronizar, e as músicas chegam em "Recebidos da banda". false = não é da banda ou sem internet.
     */
    suspend fun requestSongs(memberId: String, wanted: List<BandLink.Wanted>): Boolean {
        val m = trust.member(memberId) ?: return false
        val key = pairKey(m) ?: return false
        return runCatching { cloud.requestFromPair(key, m.id, wanted) }.getOrDefault(false)
    }

    private companion object {
        const val KEY_OUT = "outbox"
        const val KEY_CODES = "codes"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_SINCE = "since_"
        const val KEY_SEEN = "seen"
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000      // desiste depois de 30 dias
        const val RESEND_AFTER_MS = 12L * 60 * 60 * 1000   // o serviço guarda 12 h: antes disso não reenvia
        const val MAX_TRIES = 5
        const val MAX_SEEN = 300
    }
}
