package tv.arka.app.data.security

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated encryption (AES-256-GCM) for everything sensitive that ArkaTV stores:
 * source credentials, playlist URLs and any stream URL that embeds them.
 *
 * Envelope scheme: a random data key encrypts the values; that data key is itself wrapped by a
 * non-exportable key held in the Android Keystore. Wrapping once keeps per-value encryption fast
 * enough for thousands of playlist rows.
 */
object SecretBox {
    private const val PREFIX = "a1:"
    private const val WRAPPED_KEY_PREF = "data_key_v1"
    private const val KEYSTORE_ALIAS = "arkatv_master_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    @Volatile
    private var dataKey: SecretKey? = null

    /** True when an existing data key could not be unwrapped, so older stored values are unreadable. */
    @Volatile
    var keyWasReset: Boolean = false
        private set

    fun init(prefs: SharedPreferences) {
        if (dataKey != null) return
        synchronized(this) {
            if (dataKey != null) return
            val wrapped = prefs.getString(WRAPPED_KEY_PREF, null)
            if (wrapped != null) {
                val raw = try {
                    unwrap(wrapped)
                } catch (e: Exception) {
                    null
                }
                if (raw != null) {
                    dataKey = SecretKeySpec(raw, "AES")
                    return
                }
                keyWasReset = true
            }
            val raw = ByteArray(32).also { random.nextBytes(it) }
            prefs.edit().putString(WRAPPED_KEY_PREF, wrap(raw)).commit()
            dataKey = SecretKeySpec(raw, "AES")
        }
    }

    /** Unit tests run without an Android Keystore: they install a raw key instead. */
    fun useKeyForTests(raw: ByteArray) {
        dataKey = SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: String): String {
        val key = dataKey ?: error("SecretBox not initialised")
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    /** Returns null when the value is not ours, was tampered with, or the key changed. */
    fun decrypt(stored: String): String? {
        val key = dataKey ?: return null
        if (!stored.startsWith(PREFIX)) return null
        return try {
            val bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length))
            if (bytes.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val encrypted = cipher.doFinal(raw)
        return Base64.getEncoder().encodeToString(cipher.iv + encrypted)
    }

    private fun unwrap(wrapped: String): ByteArray {
        val bytes = Base64.getDecoder().decode(wrapped)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }
}
