package br.app.stagebook.domain

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/**
 * Chaves da banda: cada aparelho tem um par EC P-256; o id do aparelho sai da chave pública (ninguém usa o id de outro
 * com outra chave). Dois aparelhos da banda chegam à mesma chave do par por ECDH, sem nunca mandá-la pela rede; as
 * mensagens entre eles vão cifradas com ela (AES-GCM, o mesmo de [ShareCode]).
 */
object BandCrypto {
    private const val CONTEXT = "stagebook-band-v1"

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun encode(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun publicKey(encoded: String): PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }.getOrNull()

    /** Id do aparelho: os primeiros 16 bytes (hex) do SHA-256 da chave pública. */
    fun idOf(publicKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray()).copyOf(16).joinToString("") { "%02x".format(it) }

    /** Chave do par (32 bytes): a mesma dos dois lados, só com a privada de um e a pública do outro. */
    fun pairKey(mine: PrivateKey, myId: String, theirs: PublicKey, theirId: String): ByteArray {
        val secret = KeyAgreement.getInstance("ECDH").apply { init(mine); doPhase(theirs, true) }.generateSecret()
        val ids = listOf(myId, theirId).sorted()
        return MessageDigest.getInstance("SHA-256").apply {
            update(CONTEXT.toByteArray())
            ids.forEach { update(it.toByteArray()) }
            update(secret)
        }.digest()
    }
}
