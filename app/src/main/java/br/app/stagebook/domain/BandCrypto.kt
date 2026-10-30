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
 * Band keys: each device has an EC P-256 pair; the device id comes from the public key (nobody uses another's id with a
 * different key). Two band devices reach the same pair key through ECDH, without ever sending it over the network;
 * messages between them are encrypted with it (AES-GCM, the same as [ShareCode]).
 */
object BandCrypto {
    private const val CONTEXT = "stagebook-band-v1"

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun encode(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)

    fun publicKey(encoded: String): PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }.getOrNull()

    /** Device id: the first 16 bytes (hex) of the public key's SHA-256. */
    fun idOf(publicKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(publicKey.toByteArray()).copyOf(16).joinToString("") { "%02x".format(it) }

    /** Pair key (32 bytes): the same on both sides, with only one side's private key and the other's public key. */
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
