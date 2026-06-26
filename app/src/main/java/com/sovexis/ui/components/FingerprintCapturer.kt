package com.sovexis.ui.components

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sovexis 多指指纹采集器 (TEE-backed)
 *
 * 每根手指在 Android Keystore 中创建一个独立的 ECDSA P-256 密钥对，
 * 密钥使用要求用户生物认证（setUserAuthenticationRequired）。
 * 签名操作由 TEE/StrongBox 硬件执行，签名结果用作模糊提取器的输入 w_i。
 *
 * ── 采集流程 ──
 * 1. 为每根手指创建 Keystore 密钥（仅首次）
 * 2. BiometricPrompt 弹出，用户按指定角度放置手指
 * 3. TEE 签名挑战 nonce → 获取 sig_i
 * 4. 作为 w_i 送入 FuzzyExtractor
 *
 * ── 手指标签 ──
 * "left_thumb", "left_index", "left_middle",
 * "right_thumb", "right_index", "right_middle"
 *
 * @author Sovexis Architecture Team
 * @since 4.0.0
 */
object FingerprintCapturer {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "sovexis_finger_"
    private const val EC_CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /** 采集超时（秒）。 */
    private const val CAPTURE_TIMEOUT_SECONDS = 30L

    /** 注册时每指采集次数。 */
    const val SAMPLES_PER_FINGER = 3

    /** 采集角度标签。 */
    val ANGLE_LABELS = listOf("平放", "左侧边缘", "右侧边缘")

    /**
     * 采集一根手指的多角度样本。
     *
     * @param context Android Context（需为 FragmentActivity）
     * @param fingerLabel 手指标签，如 "left_thumb"
     * @return 3 个样本的 TEE 签名（对应平放/左侧/右侧），或 null
     */
    suspend fun captureFinger(
        context: Context,
        fingerLabel: String
    ): List<ByteArray>? {
        val activity = context as? FragmentActivity ?: return null

        // 确保密钥已创建
        ensureKeyExists(fingerLabel)

        val samples = mutableListOf<ByteArray>()
        for (i in ANGLE_LABELS.indices) {
            val angleLabel = ANGLE_LABELS[i]
            val challenge = buildChallenge(fingerLabel, i)

            val sample = withTimeoutOrNull(CAPTURE_TIMEOUT_SECONDS * 1000L) {
                authenticateAndSign(activity, fingerLabel, angleLabel, challenge)
            }

            if (sample == null || sample.isEmpty()) {
                return null // 任何一次采集失败 → 整体失败
            }
            samples.add(sample)
            // 采集间短暂延迟，避免系统 BiometricPrompt 冲突
            kotlinx.coroutines.delay(300)
        }

        return if (samples.size == SAMPLES_PER_FINGER) samples else null
    }

    /**
     * 验证时采集单根手指的单个样本。
     *
     * @param context Context
     * @param fingerLabel 手指标签
     * @param angleIndex 角度索引（0/1/2，随机指定）
     * @return TEE 签名 或 null
     */
    suspend fun captureSingle(
        context: Context,
        fingerLabel: String,
        angleIndex: Int
    ): ByteArray? {
        val activity = context as? FragmentActivity ?: return null
        val angleLabel = ANGLE_LABELS.getOrElse(angleIndex) { "标准" }
        val challenge = buildChallenge(fingerLabel, angleIndex)

        return withTimeoutOrNull(CAPTURE_TIMEOUT_SECONDS * 1000L) {
            authenticateAndSign(activity, fingerLabel, angleLabel, challenge)
        }
    }

    /**
     * 删除手指对应的 Keystore 密钥（用户重新注册时调用）。
     */
    fun deleteFingerKey(fingerLabel: String) {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            val alias = KEY_ALIAS_PREFIX + fingerLabel
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
        } catch (_: Exception) { }
    }

    /**
     * 检查手指密钥是否已创建。
     */
    fun hasFingerKey(fingerLabel: String): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            ks.containsAlias(KEY_ALIAS_PREFIX + fingerLabel)
        } catch (_: Exception) { false }
    }

    // ── Internal ──

    private fun buildChallenge(fingerLabel: String, angleIndex: Int): ByteArray {
        return "sovexis:finger:$fingerLabel:angle:$angleIndex:${System.currentTimeMillis()}".toByteArray()
    }

    private fun ensureKeyExists(fingerLabel: String) {
        if (hasFingerKey(fingerLabel)) return
        val alias = KEY_ALIAS_PREFIX + fingerLabel
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
        kpg.initialize(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1) // 每次签名都需认证
                .setIsStrongBoxBacked(true) // 优先 StrongBox
                .build()
        )
        kpg.generateKeyPair()
    }

    private suspend fun authenticateAndSign(
        activity: FragmentActivity,
        fingerLabel: String,
        angleLabel: String,
        challenge: ByteArray
    ): ByteArray? = suspendCancellableCoroutine { cont ->
        try {
            val alias = KEY_ALIAS_PREFIX + fingerLabel
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            val privateKey = ks.getKey(alias, null) as? java.security.PrivateKey
                ?: run { cont.resume(null); return@suspendCancellableCoroutine }

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("指纹采集: $fingerLabel")
                .setSubtitle("请以「$angleLabel」角度放置手指")
                .setNegativeButtonText("取消")
                .build()

            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val crypto = result.cryptoObject?.signature ?: signature
                            crypto.update(challenge)
                            val sig = crypto.sign()
                            // 擦除签名中的 challenge 敏感数据
                            challenge.fill(0)
                            cont.resume(sig)
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onAuthenticationFailed() { /* 系统自动重试 */ }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        cont.resume(null) // 用户取消或硬件错误 → 采集失败
                    }
                })

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}
