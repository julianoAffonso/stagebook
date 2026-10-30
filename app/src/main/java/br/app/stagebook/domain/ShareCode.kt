package br.app.stagebook.domain

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Código de envio do setlist pela internet (`47-violao-lua`): fácil de ditar, digitar ou mandar por mensagem. Dele saem,
 * por PBKDF2, a chave do AES-GCM e o canal no serviço público (ntfy.sh): o servidor só vê bytes cifrados num canal de
 * nome sorteado. Digitar com ou sem acento, em maiúsculas ou com espaços dá o mesmo código.
 */
object ShareCode {
    private const val ITERATIONS = 60_000       // demora um pouco para quem tenta adivinhar; ~0,3 s num tablet fraco
    private const val SALT = "stagebook-setlist-v1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    // palavras curtas, sem acento, fáceis de falar e de digitar (256: 8 bits cada)
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

    /** Como o código aparece para a pessoa: as 3 palavras com hífen ("47-violao-lua"; espaço some ou dobra ao digitar). */
    fun display(code: String) = code

    /** O código dentro de um QR (para quem está do lado tirar foto ou ler pelo "Ler QR"). */
    const val QR_PREFIX = "stagebook:"

    fun qrText(code: String) = QR_PREFIX + code

    /** O código lido de um QR, ou null se o QR não for de código. */
    fun fromQr(text: String): String? = text.takeIf { it.startsWith(QR_PREFIX) }?.removePrefix(QR_PREFIX)?.let(::normalize)

    /** Um código novo: número de 10 a 99 e duas palavras (~22 bits; vale 12 h no servidor). */
    fun generate(): String = "${10 + random.nextInt(90)}-${WORDS[random.nextInt(WORDS.size)]}-${WORDS[random.nextInt(WORDS.size)]}"

    /** O código como o app entende: sem acento, minúsculo, partes separadas por "-". Null se não tiver cara de código. */
    fun normalize(input: String): String? {
        val parts = TextKey.of(input).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (parts.size != 3 || parts[0].toIntOrNull() == null || parts.drop(1).any { it.any(Char::isDigit) }) return null
        return parts.joinToString("-")
    }

    /** Chave do AES (32 bytes) e canal (texto) do código já normalizado. */
    class Keys(val key: ByteArray, val topic: String)

    fun keys(code: String): Keys {
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

    /** Null se não decifrar (código errado ou mensagem de outra coisa no canal). */
    fun decrypt(key: ByteArray, text: String): ByteArray? = runCatching {
        val all = Base64.getDecoder().decode(text.trim())
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES))
        }
        c.doFinal(all, IV_BYTES, all.size - IV_BYTES)
    }.getOrNull()

    internal val wordCount get() = WORDS.size
}
