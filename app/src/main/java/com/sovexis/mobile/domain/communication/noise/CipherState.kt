package com.sovexis.mobile.domain.communication.noise

/**
 * Noise CipherState - 加密状态管理
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: Phase 1 + Phase 2 完整实现
 * 规范参考: Noise Protocol Framework Rev 34, Section 5.1
 * (https://noiseprotocol.org/noise.html#cipher-state)
 *
 * CipherState 管理一个加密密钥 k 和一个计数器 nonce n。
 * 它提供了使用 AEAD 加密/解密数据的能力，并确保 nonce 单调递增。
 *
 * Noise 规范 Section 5.1 伪代码:
 * ```
 * CipherState:
 *   k: ByteArray       // 加密密钥 (KEYLEN 字节或空)
 *   n: U64             // nonce 计数器
 *
 *   Initialize(key):
 *     k = key
 *     n = 0
 *
 *   HasKey():
 *     return k is not empty
 *
 *   EncryptWithAd(ad, plaintext):
 *     if k is empty: return plaintext
 *     ciphertext = Encrypt(k, n, ad, plaintext)
 *     n++
 *     return ciphertext
 *
 *   DecryptWithAd(ad, ciphertext):
 *     if k is empty: return ciphertext
 *     plaintext = Decrypt(k, n, ad, ciphertext)
 *     n++
 *     return plaintext
 *
 *   Rekey():
 *     k = Encrypt(k, MAX_NONCE, zero(0))
 *     n = 0
 * ```
 *
 * 安全注意事项:
 * - Nonce 必须严格单调递增，不可重复使用
 * - 当 nonce 溢出时必须调用 rekey() 或停止使用
 * - 密钥 k 为空时，加密/解密操作为恒等函数（传输明文）
 *
 * [IMPLEMENTATION-STATUS: COMPLETE]
 * [REVIEW-REQUIRED: SECURITY]
 *
 * @property cipher Noise Cipher 函数实现
 * @property k 加密密钥 (KEYLEN 字节或 null)
 * @property n nonce 计数器 (64 位无符号整数)
 */
class CipherState(
    private val cipher: NoiseCipher
) {
    /**
     * 加密密钥
     *
     * - 非空时: 使用 AEAD 加密/解密
     * - 空时: 加密/解密为恒等函数（传输明文）
     */
    var k: ByteArray? = null
        private set

    /**
     * Nonce 计数器
     *
     * 64 位无符号整数，每次加密/解密后递增。
     * 当 nonce 达到上限时必须调用 rekey()。
     */
    var n: Long = 0L
        private set

    /**
     * Noise 规范定义的最大 nonce 值
     * 2^63 - 1 (有符号 Long 的最大值)
     */
    private val maxNonce: Long = (1L shl 63) - 1

    /**
     * 使用给定密钥初始化 CipherState
     *
     * 根据 Noise 规范:
     * ```
     * Initialize(key):
     *   k = key
     *   n = 0
     * ```
     *
     * @param key 加密密钥 (KEYLEN 字节)，可为 null 表示未初始化
     */
    fun initialize(key: ByteArray?) {
        this.k = key?.copyOf()
        this.n = 0L
    }

    /**
     * 检查是否已设置加密密钥
     *
     * 根据 Noise 规范:
     * ```
     * HasKey():
     *   return k is not empty
     * ```
     *
     * @return Boolean true 表示已设置密钥
     */
    fun hasKey(): Boolean {
        return k != null
    }

    /**
     * 使用附加认证数据加密明文
     *
     * 根据 Noise 规范:
     * ```
     * EncryptWithAd(ad, plaintext):
     *   if k is empty: return plaintext
     *   ciphertext = Encrypt(k, n, ad, plaintext)
     *   n++
     *   return ciphertext
     * ```
     *
     * 当密钥 k 为空时，返回明文原文（恒等函数）。
     * 当密钥 k 非空时，使用 AEAD 加密并递增 nonce。
     *
     * @param ad 附加认证数据 (Associated Data)
     * @param plaintext 明文数据
     * @return Result<ByteArray> 加密结果（密文 + tag）或错误
     */
    fun encryptWithAd(ad: ByteArray, plaintext: ByteArray): Result<ByteArray> {
        return try {
            if (k == null) {
                // 密钥为空，返回明文（恒等函数）
                return Result.success(plaintext)
            }

            // 检查 nonce 是否溢出
            if (n > maxNonce - 1) {
                return Result.failure(
                    NoiseException("Nonce overflow: n=$n exceeds safe limit. Call rekey() first.")
                )
            }

            val ciphertext = cipher.encrypt(k!!, n, ad, plaintext)
            n++

            Result.success(ciphertext)
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("EncryptWithAd failed", e))
        }
    }

    /**
     * 使用附加认证数据解密密文
     *
     * 根据 Noise 规范:
     * ```
     * DecryptWithAd(ad, ciphertext):
     *   if k is empty: return ciphertext
     *   plaintext = Decrypt(k, n, ad, ciphertext)
     *   n++
     *   return plaintext
     * ```
     *
     * 当密钥 k 为空时，返回密文原文（恒等函数）。
     * 当密钥 k 非空时，使用 AEAD 解密并递增 nonce。
     *
     * @param ad 附加认证数据 (Associated Data)
     * @param ciphertext 密文数据 (含 GCM tag)
     * @return Result<ByteArray> 解密结果（明文）或错误
     */
    fun decryptWithAd(ad: ByteArray, ciphertext: ByteArray): Result<ByteArray> {
        return try {
            if (k == null) {
                // 密钥为空，返回密文（恒等函数）
                return Result.success(ciphertext)
            }

            // 检查 nonce 是否溢出
            if (n > maxNonce - 1) {
                return Result.failure(
                    NoiseException("Nonce overflow: n=$n exceeds safe limit. Call rekey() first.")
                )
            }

            val plaintext = cipher.decrypt(k!!, n, ad, ciphertext)
            n++

            Result.success(plaintext)
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("DecryptWithAd failed", e))
        }
    }

    /**
     * 密钥重生成
     *
     * 根据 Noise 规范:
     * ```
     * Rekey():
     *   k = Encrypt(k, MAX_NONCE, zero(0))
     *   n = 0
     * ```
     *
     * 使用当前密钥和最大 nonce 值加密空数据，
     * 将结果作为新密钥，并重置 nonce 计数器。
     *
     * 当 nonce 即将溢出时，应调用此方法更新密钥。
     *
     * @return Result<Unit> 操作结果
     */
    fun rekey(): Result<Unit> {
        return try {
            if (k == null) {
                return Result.failure(NoiseException("Cannot rekey: no key set"))
            }

            k = cipher.rekey(k!!)
            n = 0L

            Result.success(Unit)
        } catch (e: NoiseException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NoiseException("Rekey failed", e))
        }
    }

    /**
     * 获取当前 nonce 值（只读）
     *
     * @return Long 当前 nonce 值
     */
    fun getNonce(): Long = n

    /**
     * 安全清除密钥
     *
     * 将密钥置为 null 并擦除内存中的密钥数据。
     * 用于会话结束时清理敏感数据。
     */
    fun clear() {
        k?.let { NoiseUtils.secureWipe(it) }
        k = null
        n = 0L
    }

    /**
     * 创建 CipherState 的深拷贝
     *
     * @return CipherState 独立的副本
     */
    fun copy(): CipherState {
        val clone = CipherState(cipher)
        clone.initialize(k?.copyOf())
        clone.n = this.n
        return clone
    }
}
