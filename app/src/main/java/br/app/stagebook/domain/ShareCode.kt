package br.app.stagebook.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Setlist sharing code over the internet (`47-violao-lua`): easy to read out, type or send in a message. From it,
 * PBKDF2 derives the AES-GCM key and the channel on the public service (ntfy.sh): the server only sees encrypted bytes
 * on a randomly named channel. Typing with or without accents, in capitals or with spaces gives the same code.
 */
object ShareCode {
    private const val ITERATIONS = 60_000       // slows down anyone trying to guess; ~0.3 s on a weak tablet
    private const val SALT = "stagebook-setlist-v1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    // short words, no accents, easy to say and type (256: 8 bits each)
    private val WORDS = (
        "agua alma amor anel ano arco areia asa astro aula azul bala banda barco beijo bico bola bolo bom bossa bote braco " +
        "brasa brisa cabo cacau cafe caixa calma cama campo cana canto capa carta casa cedo ceu chao chave chuva cidade cinza " +
        "clave coco coral corda coro cravo cruz cume dado danca dedo dia disco doce dom duna eco eixo era estrela fada farol " +
        "fato feira festa figo fio flor flauta fogo folha fonte forro frevo fruta fuga gaita galo gato gelo gema giro gota " +
        "grama grilo guia harpa hino hora ilha ima jade jazz jogo junho lago lama lapis lar lata leao leite lenda lima linha " +
        "lira lobo loja lua luz maio mala manga mapa mar massa mate mel mesa meta milho mina moda moeda monte mundo muro " +
        "nave neve ninho noite norte nota nuvem oboe olho onda ouro ovo padre palco palma pao papel par pato pausa paz pedra " +
        "pena pera piano pipa pista placa planta poema polo ponte porta pote praia prato prumo pulo quarto queijo rabo raio " +
        "rama rede reino remo rio ritmo roda rosa rua rumo sabia sal salsa samba sapo sela selo serra sino sitio sol som " +
        "sopro sul surdo taco tamba tarde tecla tela tempo terra tigre toada tom torre trem trevo trigo trilho trio tuba " +
        "uva vale vapor vela verao verso via vida vila vinho viola violao vista voz zebra zinco abelha cometa cristal " +
        "farofa girassol harmonia lanterna melodia orquestra pandeiro ribeira sanfona tambor acorde baixo bateria cantor " +
        "cuica escala estudio fanfarra ganza guitarra jongo lundu metro oitava palheta refrao sax solista tenor timbal xote"
        ).split(' ').distinct().take(256)

    private val random = SecureRandom()

    /**
     * How the code shows to the person: the 3 words with dashes ("47-violao-lua"; a space vanishes or doubles when
     * typing).
     */
    fun display(code: String) = code

    /** The code inside a QR (for someone next to you to photograph or scan with "Scan QR"). */
    const val QR_PREFIX = "stagebook:"

    fun qrText(code: String) = QR_PREFIX + code

    /** The code read from a QR, or null if the QR isn't a code. */
    fun fromQr(text: String): String? = text.takeIf { it.startsWith(QR_PREFIX) }?.removePrefix(QR_PREFIX)?.let(::normalize)

    /**
     * The link every internet send is shared as (setlist, songs or band invite; user, 07/11): it opens the app on the
     * receive screen, or the site when the app isn't installed. The 3 words still work typed.
     */
    const val LINK_PREFIX = "https://stagebook.app.br/r#"

    /** What a link carries, for the site to say what was sent (`t=`): an invite, a setlist or loose songs. */
    enum class LinkKind(val tag: String) { INVITE("i"), SETLIST("l"), SONGS("m") }

    /** What a receive link carries besides the code (for the site): all empty in a bare `#47-violao-lua`. */
    data class LinkInfo(val code: String, val kind: LinkKind? = null, val from: String = "", val setlist: String = "", val lang: String = "")

    /**
     * `https://stagebook.app.br/r#<code>.<details>`: the code first (a bare `#47-violao-lua` still works), then what the
     * site shows in the sender's language, as base64url (no padding) of a compact JSON `{"t","n","s","l"}`. Not a secret
     * (user, 07/11): it only keeps the names out of plain sight and uses nothing but `[A-Za-z0-9_.-]`, which no messenger
     * mangles (a "%" or "&" can break). All in the fragment, which never leaves the phone for the server. Empty parts are
     * left out; [setlist] only goes with a setlist.
     */
    fun link(code: String, kind: LinkKind? = null, from: String = "", setlist: String = "", lang: String = ""): String {
        val details = buildJsonObject {
            kind?.let { put("t", it.tag) }
            from.takeIf { it.isNotBlank() }?.let { put("n", it) }
            setlist.takeIf { it.isNotBlank() && kind == LinkKind.SETLIST }?.let { put("s", it) }
            lang.takeIf { it.isNotBlank() }?.let { put("l", it) }
        }
        if (details.isEmpty()) return LINK_PREFIX + code
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(details.toString().toByteArray(Charsets.UTF_8))
        return "$LINK_PREFIX$code.$payload"
    }

    // anywhere in a text (a whole message pasted, a link with or without https), but not inside another host's name; the
    // code is the fragment up to the first "." or "&" (the rest is for the site): the details in base64 (the standard
    // alphabet and padding too, in case something rewrote them) or the older "&t=..&n=.." parameters. A ".", "," or ")"
    // a messenger glues to the end stays out: none is in the base64 alphabet
    private val LINK = Regex(
        """(?<![A-Za-z0-9.-])(?:www\.)?stagebook\.app\.br/r/?#([A-Za-z0-9-]+)(?:\.([A-Za-z0-9_+/=-]+)|((?:&[A-Za-z]=[^&\s]*)+))?""",
        RegexOption.IGNORE_CASE,
    )

    /** The code of the first receive link in [text], or null if there is none. */
    fun fromLink(text: String?): String? = text?.let { LINK.find(it) }?.groupValues?.get(1)?.let(::normalizeWords)

    /** The first receive link in [text] with what it says, in either format; null if there is none. */
    fun linkInfo(text: String?): LinkInfo? {
        val match = text?.let { LINK.find(it) } ?: return null
        val code = normalizeWords(match.groupValues[1]) ?: return null
        val fields = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::readDetails)
            ?: match.groupValues[3].takeIf { it.isNotEmpty() }?.let(::readParams).orEmpty()
        return LinkInfo(code, LinkKind.entries.firstOrNull { it.tag == fields["t"] }, fields["n"].orEmpty(), fields["s"].orEmpty(), fields["l"].orEmpty())
    }

    // the base64 details; a "-" or "_" glued to the end (both are in the alphabet) breaks the JSON, so up to two
    // characters are dropped before giving up
    private fun readDetails(raw: String): Map<String, String>? {
        val plain = raw.trimEnd('=').replace('-', '+').replace('_', '/')
        return (0..2).asSequence().mapNotNull { cut ->
            val body = plain.dropLast(cut).takeIf { it.isNotEmpty() && it.length % 4 != 1 } ?: return@mapNotNull null
            runCatching {
                val text = Base64.getDecoder().decode(body + "=".repeat((4 - body.length % 4) % 4)).toString(Charsets.UTF_8)
                Json.parseToJsonElement(text).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content.take(MAX_DETAIL) }
            }.getOrNull()
        }.firstOrNull()
    }

    // the older format, "&t=l&n=Ana%20Lu", read like the browser's decodeURIComponent (a "+" stays a "+")
    private fun readParams(raw: String): Map<String, String> = raw.split('&').filter { '=' in it }.associate { kv ->
        val value = runCatching { java.net.URLDecoder.decode(kv.substringAfter('=').replace("+", "%2B"), "UTF-8") }.getOrDefault("")
        kv.substringBefore('=').lowercase() to value.take(MAX_DETAIL)
    }

    private const val MAX_DETAIL = 80   // what the site shows of each part

    /** A new code: a number from 10 to 99 and two words (~22 bits; valid 12 h on the server). */
    fun generate(): String = "${10 + random.nextInt(90)}-${WORDS[random.nextInt(WORDS.size)]}-${WORDS[random.nextInt(WORDS.size)]}"

    /**
     * The code as the app understands it: no accents, lowercase, parts separated by "-". Null if it doesn't look like a
     * code.
     */
    fun normalize(input: String): String? = fromLink(input) ?: normalizeWords(input)   // a pasted link also counts

    private fun normalizeWords(input: String): String? {
        val parts = TextKey.of(input).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (parts.size != 3 || parts[0].toIntOrNull() == null || parts.drop(1).any { it.any(Char::isDigit) }) return null
        return parts.joinToString("-")
    }

    /** AES key (32 bytes) and channel (text) of the already normalized code. */
    class Keys(val key: ByteArray, val topic: String)

    // the latest codes' keys: 60 thousand PBKDF2 rounds each, and one call per saved code on every "ok" over Wi-Fi
    private const val KEYS_KEPT = 16
    private val derived = object : LinkedHashMap<String, Keys>(KEYS_KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Keys>) = size > KEYS_KEPT
    }

    fun keys(code: String): Keys = synchronized(derived) { derived[code] } ?: derive(code).also { synchronized(derived) { derived[code] = it } }

    private fun derive(code: String): Keys {
        val spec = PBEKeySpec(code.toCharArray(), SALT.toByteArray(), ITERATIONS, 48 * 8)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val topic = "sb" + raw.copyOfRange(32, 48).joinToString("") { "%02x".format(it) }
        return Keys(raw.copyOf(32), topic)
    }

    fun encrypt(key: ByteArray, plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv)) }
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }

    /** Null if it doesn't decrypt (wrong code or a message about something else on the channel). */
    fun decrypt(key: ByteArray, text: String): ByteArray? = runCatching {
        val all = Base64.getDecoder().decode(text.trim())
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES))
        }
        c.doFinal(all, IV_BYTES, all.size - IV_BYTES)
    }.getOrNull()

    internal val wordCount get() = WORDS.size
}
