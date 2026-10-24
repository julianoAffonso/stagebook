package com.juliano.stagebook.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.juliano.stagebook.domain.BandCrypto
import com.juliano.stagebook.domain.BandProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quem é este aparelho para a banda: o par de chaves (criado na primeira vez), o id que sai da chave pública e o nome que
 * os outros veem. A chave privada fica cifrada por uma chave do Android Keystore (que não sai do aparelho); sem Keystore
 * (testes), fica nas preferências do app, que só ele lê.
 */
@Singleton
class DeviceIdentity @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)

    private val keys: Pair<String, PrivateKey> by lazy { load() ?: create() }

    /** Chave pública (Base64) e id. */
    val publicKey: String get() = keys.first
    val id: String get() = BandCrypto.idOf(publicKey)
    val privateKey: PrivateKey get() = keys.second

    private val _name = MutableStateFlow(prefs.getString(KEY_NAME, null) ?: defaultName())
    val name: StateFlow<String> = _name

    fun setName(name: String) {
        val n = name.trim().take(BandProtocol.MAX_NAME).ifEmpty { defaultName() }
        prefs.edit { putString(KEY_NAME, n) }
        _name.value = n
    }

    fun hello() = BandProtocol.Hello(id, _name.value, publicKey)

    /** Já foi confirmado pelo usuário (Ajustes → Banda pergunta uma vez "Como a banda vai ver este aparelho?"). */
    val nameConfirmed: Boolean get() = prefs.contains(KEY_NAME)

    /**
     * Sugestão de nome: o nome do aparelho que a pessoa deu nas configurações do Android (em muitos vem "Galaxy de Fulano"),
     * lido sem permissão; sem ele, marca e modelo.
     */
    fun defaultName(): String {
        val device = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        val model = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.distinct().joinToString(" ").replaceFirstChar { it.uppercase() }
        return (device?.takeIf { it.isNotBlank() } ?: model).trim().take(BandProtocol.MAX_NAME).ifEmpty { "Stagebook" }
    }

    private fun load(): Pair<String, PrivateKey>? = runCatching {
        val pub = prefs.getString(KEY_PUB, null) ?: return null
        val stored = Base64.getDecoder().decode(prefs.getString(KEY_PRIV, null) ?: return null)
        val raw = if (prefs.getBoolean(KEY_WRAPPED, false)) unwrap(stored) else stored
        pub to KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(raw))
    }.getOrNull()

    private fun create(): Pair<String, PrivateKey> {
        val pair = BandCrypto.newKeyPair()
        val pub = BandCrypto.encode(pair.public)
        val wrapped = runCatching { wrap(pair.private.encoded) }.getOrNull()
        prefs.edit {
            putString(KEY_PUB, pub)
            putString(KEY_PRIV, Base64.getEncoder().encodeToString(wrapped ?: pair.private.encoded))
            putBoolean(KEY_WRAPPED, wrapped != null)
        }
        return pub to pair.private
    }

    // AES-GCM com a chave do Keystore: o arquivo das preferências sozinho não abre a chave privada
    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return c.iv + c.doFinal(raw)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, data, 0, 12)) }
        return c.doFinal(data, 12, data.size - 12)
    }

    private companion object {
        const val ALIAS = "stagebook_identity"
        const val KEY_PUB = "pub"
        const val KEY_PRIV = "priv"
        const val KEY_WRAPPED = "wrapped"
        const val KEY_NAME = "name"
    }
}
