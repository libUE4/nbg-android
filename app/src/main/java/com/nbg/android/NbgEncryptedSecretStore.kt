package com.nbg.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class NbgEncryptedPreferenceSecretStore(
  context: Context,
  private val prefsName: String,
  private val keyAlias: String,
  private val keyPrefix: String,
) {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  fun loadSecret(id: String): String? {
    val raw = prefs.getString(prefKey(id), null) ?: return null
    return decrypt(raw).trim().takeIf { it.isNotBlank() }
  }

  fun saveSecret(id: String, secret: String) {
    val normalized = secret.trim()
    if (normalized.isBlank()) {
      deleteSecret(id)
      return
    }
    prefs.edit().putString(prefKey(id), encrypt(normalized)).apply()
  }

  fun deleteSecret(id: String) {
    prefs.edit().remove(prefKey(id)).apply()
  }

  private fun encrypt(plainText: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return JSONObject()
      .put("v", 1)
      .put("iv", Base64.getEncoder().encodeToString(cipher.iv))
      .put("ct", Base64.getEncoder().encodeToString(cipherText))
      .toString()
  }

  private fun decrypt(raw: String): String {
    val root = JSONObject(raw)
    val iv = Base64.getDecoder().decode(root.optString("iv"))
    val cipherText = Base64.getDecoder().decode(root.optString("ct"))
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(cipherText), Charsets.UTF_8)
  }

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true)
      .build()
    generator.init(spec)
    return generator.generateKey()
  }

  private fun prefKey(id: String): String =
    keyPrefix + id.trim()

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
  }
}

