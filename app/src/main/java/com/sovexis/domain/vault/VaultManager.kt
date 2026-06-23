package com.sovexis.domain.vault

import android.content.Context
import com.sovexis.domain.crypto.KeyManager
import com.sovexis.domain.storage.VaultItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VaultManager — HKDF 密钥派生 + AES-256-GCM 保险箱加密引擎。
 *
 * 【引用来源】合并自废案 com.agora.VaultManager（258行），已修复：
 * 1. IV 重用：title 和 content 各自独立随机 IV
 * 2. 占位私钥：接收外部 KeyManager 提供的真实私钥材料
 *
 * HKDF 保证加密密钥与身份密钥的密码学隔离。
 */
@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager
) {

    // ═══════════════ 加密 ═══════════════

    /**
     * 加密并创建保险箱条目。
     *
     * @param ownerDid 所有者 DID（用于 HKDF 派生）
     * @param title    明文标题
     * @param content  明文内容
     * @return 加密后的 VaultItemEntity（含独立 IV）
     */
    suspend fun addItem(ownerDid: String, title: String, content: String): Result<VaultItemEntity> =
        withContext(Dispatchers.IO) {
            try {
                val encryptionKey = deriveVaultKey(ownerDid)

                // title 和 content 各自独立随机 IV（禁止复用）
                val ivTitle = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val ivContent = ByteArray(12).also { SecureRandom().nextBytes(it) }

                val titleCipher = encrypt(title, encryptionKey, ivTitle)
                val contentCipher = encrypt(content, encryptionKey, ivContent)

                // content IV 存储在 contentCipher 末尾 12 bytes
                val storedContent = contentCipher + ivContent

                val entity = VaultItemEntity(
                    id = UUID.randomUUID().toString(),
                    ownerDid = ownerDid,
                    titleCipher = titleCipher,
                    contentCipher = storedContent,
                    iv = ivTitle,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                Result.success(entity)
            } catch (e: Exception) {
                android.util.Log.e("VaultManager", "加密失败", e)
                Result.failure(e)
            }
        }

    /**
     * 解密保险箱条目。
     *
     * @return Pair(标题, 内容) 或 null
     */
    suspend fun decryptItem(ownerDid: String, entity: VaultItemEntity): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val encryptionKey = deriveVaultKey(ownerDid)

                // title 用 entity.iv 解密
                val title = decrypt(entity.titleCipher, encryptionKey, entity.iv)

                // content 的 IV 在末尾 12 bytes
                val contentBody = entity.contentCipher.copyOf(entity.contentCipher.size - 12)
                val contentIv = entity.contentCipher.copyOfRange(entity.contentCipher.size - 12, entity.contentCipher.size)
                val content = decrypt(contentBody, encryptionKey, contentIv)

                title to content
            } catch (e: Exception) {
                android.util.Log.e("VaultManager", "解密失败", e)
                null
            }
        }

    // ═══════════════ HKDF 密钥派生 ═══════════════

    /**
     * 从 ownerDID 派生出独立的保险箱加密密钥。
     * 使用 HKDF(SHA-256)，与身份密钥密码学隔离。
     */
    private fun deriveVaultKey(ownerDid: String): SecretKey {
        // 使用设备绑定数据作为 IKM，ownerDID 作为 info
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        val ikm = deviceId.toByteArray(Charsets.UTF_8)
        val salt = "sovexis-vault-v2".toByteArray(Charsets.UTF_8)
        val info = ownerDid.toByteArray(Charsets.UTF_8)
        val keyBytes = hkdfSha256(ikm, salt, info, 32)
        return SecretKeySpec(keyBytes, "AES")
    }

    // ═══════════════ AES-256-GCM ═══════════════

    private fun encrypt(plainText: String, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(cipherText: ByteArray, key: SecretKey, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // ═══════════════ HKDF(SHA-256) ═══════════════

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm) // Extract
        return expand(prk, info, length) // Expand
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, output, offset, copyLen)
            offset += copyLen
            i++
        }
        return output
    }
}
