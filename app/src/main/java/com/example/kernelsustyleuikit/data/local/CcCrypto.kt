package com.example.kernelsustyleuikit.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 密码加密存储（Android Keystore + AES-GCM）。
 *
 * 密文格式：`[ivLength(1B)][iv][ciphertext]`，整体 Base64。
 * 密钥永不离开 Keystore，进程内也不缓存明文。
 */
object CcCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "cc_server_password"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(1 + iv.size + cipherText.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(cipherText, 0, packed, 1 + iv.size, cipherText.size)

        Base64.encodeToString(packed, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.isEmpty()) return null
        val ivLength = packed[0].toInt()
        if (ivLength <= 0 || 1 + ivLength > packed.size) return null

        val iv = packed.copyOfRange(1, 1 + ivLength)
        val cipherText = packed.copyOfRange(1 + ivLength, packed.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
