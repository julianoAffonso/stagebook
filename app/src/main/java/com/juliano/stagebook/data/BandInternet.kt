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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A banda pela internet (sem servidor nosso: o mesmo ntfy.sh das 3 palavras, tudo cifrado):
 * - quem aceita um envio pelas 3 palavras vira "da banda" e devolve um "ok"; quem enviou guarda o "ok" e também vira da
 *   banda do outro (a mesma confiança da rede local);
 * - entre aparelhos da banda, cada par tem uma caixa de entrada própria (canal que sai da chave do par).
 * Gasta o mínimo: confere **uma vez ao abrir o app** (e enquanto o diálogo das palavras está aberto), nunca de tempos em
 * tempos com o app fechado. O serviço guarda as mensagens por 12 h.
 */
@Singleton
class BandInternet @Inject constructor(
    @ApplicationContext context: Context,
    private val cloud: SetlistCloud,
    private val trust: BandTrust,
    private val identity: DeviceIdentity,
    private val inbox: BandInbox,
    private val connectivity: Connectivity,
) {
    private val prefs = context.getSharedPreferences("band_net", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun pairKey(member: BandTrust.Member): ByteArray? =
        BandCrypto.publicKey(member.key)?.let { BandCrypto.pairKey(identity.privateKey, identity.id, it, member.id) }

    /** Um envio meu pelas 3 palavras: guardado por 12 h para pegar os "ok" de quem aceitar. */
    fun remember(code: String) {
        val now = System.currentTimeMillis()
        val codes = myCodes().filter { it.second > now } + (code to now + VALID_MS)
        prefs.edit { putString(KEY_CODES, codes.joinToString(";") { "${it.first},${it.second}" }) }
    }

    private fun myCodes(): List<Pair<String, Long>> = prefs.getString(KEY_CODES, "").orEmpty().split(';').mapNotNull {
        val p = it.split(','); if (p.size == 2) p[0] to (p[1].toLongOrNull() ?: 0L) else null
    }

    /** Aceitei um envio pelas palavras: quem enviou vira da banda e recebe o meu "ok". */
    fun accepted(code: String, from: BandProtocol.Hello?) {
        from ?: return
        trust.add(from.id, from.name, from.key)
        scope.launch { cloud.ack(code) }
    }

    /** Os "ok" de um envio meu: quem aceitou vira da banda. Devolve os nomes novos. */
    suspend fun checkAcks(code: String): List<String> = cloud.acks(code).filterNot { trust.isMember(it.id) }.map { h ->
        trust.add(h.id, h.name, h.key); h.name
    }

    /** Ao abrir o app (com internet e alguém da banda ou envio recente): a caixa de cada par e os "ok" pendentes. */
    fun onAppOpen() {
        scope.launch {
            if (trust.members.value.isEmpty() && myCodes().isEmpty()) return@launch
            if (!connectivity.isOnline()) return@launch
            lock.withLock {
                val now = System.currentTimeMillis()
                myCodes().filter { it.second > now }.forEach { (code, _) -> runCatching { checkAcks(code) } }
                trust.members.value.forEach { m ->
                    val key = pairKey(m) ?: return@forEach
                    val since = prefs.getString("$KEY_SINCE${m.id}", null)
                    val (packages, last) = cloud.fetchPair(key, since) ?: return@forEach
                    packages.forEach { (text, songs) -> inbox.add(m.id, m.name, text, songs) }
                    last?.let { prefs.edit { putString("$KEY_SINCE${m.id}", it) } }
                }
            }
        }
    }

    /** Manda para quem é da banda e não está em [skip] (os que já receberam pelo Wi-Fi); devolve para quantos foi. */
    suspend fun sendToBand(list: ByteArray, songs: List<SetlistCloud.SharedSong>, skip: Set<String>): Int =
        trust.members.value.filterNot { it.id in skip }.count { m ->
            val key = pairKey(m) ?: return@count false
            cloud.sendToPair(key, m.id, list, songs)
        }

    private companion object {
        const val KEY_CODES = "codes"
        const val KEY_SINCE = "since_"
        const val VALID_MS = 12 * 60 * 60 * 1000L
    }
}
