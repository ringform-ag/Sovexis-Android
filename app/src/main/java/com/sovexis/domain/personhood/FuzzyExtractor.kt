package com.sovexis.domain.personhood

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sovexis 人格锚定 · 模糊提取器 (Fuzzy Extractor)
 *
 * 实现 Juels–Wattenberg 模糊承诺方案 (Fuzzy Commitment Scheme)。
 *
 * ── 核心原理 ──
 * 注册 Gen(w):
 *   1. 三样本取中值 → 模板 w（抑制单次采集噪声）
 *   2. 生成随机密钥 k（32 bytes, SecureRandom）
 *   3. ECC 编码: k → c（重复码 R(32,8): 每比特重复 8 次 → 256 bytes）
 *   4. hd = w ⊕ c（辅助数据，可公开存储）
 *   5. 存储 commit = SHA-256(k) 用于验证
 *   6. 返回 (k, hd, commit)
 *
 * 验证 Rep(w', hd):
 *   1. c' = w' ⊕ hd
 *   2. 重复码解码: c' → k'（每 8-bit 块取多数）
 *   3. 校验 SHA-256(k') == stored_commit
 *   4. 成功 → 返回 k'；失败 → null
 *
 * ── 纠错能力 ──
 * 重复码 R(32,8) 可纠正每 32-byte 密钥中任意 ≤3 比特翻转的错误。
 * TEE 确定性签名场景下 w' ≈ w，纠错能力未被使用，但架构为未来原生指纹特征向量预留。
 *
 * ── 安全性 ──
 * hd = w ⊕ c 中 c 是随机码字，不泄露 k 的信息（需知道 w 才能恢复 k）。
 * commit = SHA-256(k) 不泄露 k。
 *
 * @author Sovexis Architecture Team
 * @since 4.0.0 — 替换 SHA-256 占位实现
 */
object FuzzyExtractor {

    // ── Parameters ──

    /** 密钥字节数。SHA-256 输出长度。 */
    const val KEY_BYTES = 32

    /** 重复码重复次数。每比特重复 8 次 → 码字 256 bytes。 */
    const val REPETITION_FACTOR = 8

    /** 码字字节数 = KEY_BYTES * REPETITION_FACTOR。 */
    val CODEWORD_BYTES: Int get() = KEY_BYTES * REPETITION_FACTOR

    /** 提交哈希长度 = SHA-256 输出。 */
    const val COMMITMENT_BYTES = 32

    // ── Public API ──

    /**
     * Gen(w, samples) — 注册阶段。
     *
     * @param samples 3 次采集的原始特征（每份长度 >= KEY_BYTES）
     * @return ExtractResult 含 k, hd, commit，或 null（样本无效）
     */
    fun generate(samples: List<ByteArray>): ExtractResult? {
        if (samples.size < 3) return null
        if (samples.any { it.size < 1 }) return null

        // 1. 三样本取中值模板（逐字节 median）
        val minLen = samples.minOf { it.size }
        val w = ByteArray(minLen)
        for (i in 0 until minLen) {
            val vals = samples.map { it[i].toInt() and 0xFF }.sorted()
            w[i] = vals[1].toByte() // 三个值的中位数
        }

        // 2. 生成随机密钥
        val k = ByteArray(KEY_BYTES)
        SecureRandom().nextBytes(k)

        // 3. ECC 编码: k → c (重复码)
        val c = encodeKey(k)

        // 4. hd = w ⊕ c（对齐长度）
        val hdLen = minOf(w.size, c.size)
        val hd = ByteArray(hdLen)
        for (i in 0 until hdLen) {
            hd[i] = (w[i].toInt() xor c[i].toInt()).toByte()
        }

        // 5. commit = SHA-256(k)
        val commit = sha256(k)

        return ExtractResult(k = k, hd = hd, commitment = commit)
    }

    /**
     * Rep(w', hd, commitment) — 验证阶段。
     *
     * @param wPrime 新采集的特征向量
     * @param hd 注册时生成的辅助数据
     * @param commitment 注册时存储的 SHA-256(k)
     * @return 恢复的密钥 k 或 null（验证失败）
     */
    fun reproduce(wPrime: ByteArray, hd: ByteArray, commitment: ByteArray): ByteArray? {
        if (commitment.size != COMMITMENT_BYTES) return null

        // 1. c' = w' ⊕ hd
        val cPrimeLen = minOf(wPrime.size, hd.size)
        val cPrime = ByteArray(cPrimeLen)
        for (i in 0 until cPrimeLen) {
            cPrime[i] = (wPrime[i].toInt() xor hd[i].toInt()).toByte()
        }

        // 2. 重复码解码: c' → k'
        val kPrime = decodeToKey(cPrime)
        if (kPrime == null || kPrime.size != KEY_BYTES) return null

        // 3. 校验 commit
        val computedCommit = sha256(kPrime)
        if (!computedCommit.contentEquals(commitment)) {
            // 尝试宽松模式：跳过解码，直接 hash w' 的截断（仅在 w' 本身是确定性值时有效）
            val altKey = sha256(wPrime).copyOf(KEY_BYTES)
            if (sha256(altKey).contentEquals(commitment)) return altKey
            return null
        }

        return kPrime
    }

    // ── ECC: Repetition Code R(32, 8) ──

    /**
     * 编码：每比特重复 REPETITION_FACTOR 次。
     *
     * k = [b0, b1, ..., b255]
     * c = [b0×8, b1×8, ..., b255×8]
     */
    private fun encodeKey(k: ByteArray): ByteArray {
        val c = ByteArray(CODEWORD_BYTES)
        for (i in k.indices) {
            val b = k[i].toInt() and 0xFF
            for (bit in 0..7) {
                val bitVal = ((b shr (7 - bit)) and 1)
                val byteVal = if (bitVal == 1) 0xFF.toByte() else 0x00.toByte()
                c[i * REPETITION_FACTOR + bit] = byteVal
            }
        }
        return c
    }

    /**
     * 解码：每 REPETITION_FACTOR 字节取多数 → 恢复 1 比特。
     */
    private fun decodeToKey(cPrime: ByteArray): ByteArray? {
        if (cPrime.size < CODEWORD_BYTES) return null
        val k = ByteArray(KEY_BYTES)
        for (i in 0 until KEY_BYTES) {
            var byteVal = 0
            for (bit in 0..7) {
                val baseIdx = i * REPETITION_FACTOR + bit
                if (baseIdx + REPETITION_FACTOR > cPrime.size) return null
                var ones = 0
                for (r in 0 until REPETITION_FACTOR) {
                    val v = cPrime[baseIdx + r].toInt() and 0xFF
                    if (v > 127) ones++
                }
                if (ones > REPETITION_FACTOR / 2) {
                    byteVal = byteVal or (1 shl (7 - bit))
                }
            }
            k[i] = byteVal.toByte()
        }
        return k
    }

    // ── HMAC + HKDF ──

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLen: Int = 32): ByteArray {
        val prk = hmacSha256(salt, ikm)
        val result = ByteArray(outputLen)
        var t = ByteArray(0)
        var blockIdx = 1.toByte()
        var offset = 0
        while (offset < outputLen) {
            t = hmacSha256(prk, t + info + blockIdx)
            val copyLen = minOf(t.size, outputLen - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            blockIdx++
        }
        return result
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    // ── Result ──

    data class ExtractResult(
        val k: ByteArray,
        val hd: ByteArray,
        val commitment: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtractResult) return false
            return k.contentEquals(other.k) && hd.contentEquals(other.hd) && commitment.contentEquals(other.commitment)
        }
        override fun hashCode(): Int =
            31 * (31 * k.contentHashCode() + hd.contentHashCode()) + commitment.contentHashCode()
    }
}
