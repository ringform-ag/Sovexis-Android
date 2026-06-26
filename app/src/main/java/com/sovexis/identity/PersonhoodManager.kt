@file:Suppress("all")

package com.sovexis.identity

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sovexis.domain.personhood.FuzzyExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PersonhoodManager — 多指生物特征人格锚定管理器 (Phase 3 · 4.0.0)
 *
 * ═══════════════════════════════════════════════════════════════
 * 核心变更 (4.0.0 vs 3.x):
 *   - 模糊提取器: SHA-256 占位 → Juels–Wattenberg Fuzzy Commitment
 *     (FuzzyExtractor.kt, 重复码 R(32,8), 纠错能力 ≤3 bit/32B)
 *   - TEE 签名: 假 SHA-256 哈希 → Android Keystore StrongBox ECDSA
 *     (FingerprintCapturer.kt, setUserAuthenticationRequired)
 *   - 存储: 明文 SharedPreferences → EncryptedSharedPreferences + MasterKey
 *   - 随机源: kotlin.random.Random → java.security.SecureRandom
 * ═══════════════════════════════════════════════════════════════
 *
 * 职责：手指采集与固定选择、bioHash 生成、本地验证、备用手指激活、设备迁移。
 * 原则：原始生物特征(w)永不离机。仅 bioHash 提交网络。
 * 诚实标注：TEE 确定性签名下 w' ≈ w，模糊提取器的纠错能力为未来原生指纹特征预留。
 */
@Singleton
class PersonhoodManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PersonhoodMgr"
        private const val DOMAIN_TAG = "sovexis-personhood-v2"
        private const val PREFS_NAME = "sovexis_personhood_secure"
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    // ── Encrypted Storage ──

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── Persona State ──

    enum class PersonaState { ACTIVE, FAILURE_LOCKOUT, FROZEN_MIGRATED }

    // ── Data Classes ──

    data class FingerConfig(
        val index: Int,
        val label: String,                      // "left_thumb", "right_index", etc.
        val isBackup: Boolean = false,
        val registeredAt: Long = System.currentTimeMillis()
    )

    data class PersonhoodProfile(
        val bioHash: ByteArray,
        val did: String,
        val fingerConfigs: List<FingerConfig>,
        val salt: ByteArray,
        val commitments: Map<String, ByteArray>, // fingerLabel → SHA-256(k_i)
        val teePubKey: ByteArray? = null,
        val registeredAt: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PersonhoodProfile) return false
            return did == other.did && bioHash.contentEquals(other.bioHash)
        }
        override fun hashCode(): Int = did.hashCode() * 31 + bioHash.contentHashCode()
    }

    // ── Registration ──

    /**
     * 注册流程:
     * 1. 用户选择固定手指 + 1 根备用
     * 2. 每指 3 次多角度 TEE 签名 → samples[label]
     * 3. FuzzyExtractor.generate(samples) → (k, hd, commit)
     * 4. f_i = HMAC-SHA256(k_i, finger_label_i)
     * 5. bioHash = HKDF-SHA256(f_1 || ... || f_N, salt, domain_tag)
     * 6. 存储: hd 存入 EncryptedSharedPreferences, bioHash/commit 存入 profile
     */
    suspend fun register(
        did: String,
        selectedFingers: List<String>,
        backupFinger: String? = null,
        biometricSamples: Map<String, List<ByteArray>> // label → [3 TEE signatures]
    ): Result<PersonhoodProfile> = withContext(Dispatchers.IO) {
        try {
            val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val configs = mutableListOf<FingerConfig>()
            val fComponents = mutableListOf<ByteArray>()
            val commitments = mutableMapOf<String, ByteArray>()

            // 主力手指
            for ((idx, label) in selectedFingers.withIndex()) {
                val samples = biometricSamples[label]
                    ?: return@withContext Result.failure(Exception("缺少 $label 的采集样本"))
                if (samples.size < 3)
                    return@withContext Result.failure(Exception("每根手指需 3 次采集"))

                // 模糊提取器: Gen(w) → (k, hd, commit)
                val result = FuzzyExtractor.generate(samples)
                    ?: return@withContext Result.failure(Exception("$label 模糊提取器生成失败"))

                storeHelperData(label, result.hd)
                commitments[label] = result.commitment

                // f = HMAC(k, label)
                val f = FuzzyExtractor.hmacSha256(result.k, label.toByteArray())
                fComponents.add(f)
                configs.add(FingerConfig(index = idx, label = label))
            }

            // 备用手指
            if (backupFinger != null) {
                val bSamples = biometricSamples[backupFinger]
                if (bSamples != null && bSamples.size >= 3) {
                    val bResult = FuzzyExtractor.generate(bSamples)
                    if (bResult != null) {
                        storeHelperData(backupFinger, bResult.hd)
                        commitments[backupFinger] = bResult.commitment
                        storeBackupKey(backupFinger, bResult.k)
                        val bf = FuzzyExtractor.hmacSha256(bResult.k, backupFinger.toByteArray())
                        fComponents.add(bf)
                        configs.add(FingerConfig(index = selectedFingers.size, label = backupFinger, isBackup = true))
                    }
                }
            }

            // 确定性排序
            fComponents.sortBy { it.contentToString() }

            // bioHash = HKDF(combined, salt, domain_tag)
            val combined = fComponents.reduce { acc, bytes -> acc + bytes }
            val bioHash = FuzzyExtractor.hkdfSha256(combined, salt, DOMAIN_TAG.toByteArray())

            val profile = PersonhoodProfile(
                bioHash = bioHash,
                did = did,
                fingerConfigs = configs,
                salt = salt,
                commitments = commitments
            )

            saveProfile(profile)
            Log.i(TAG, "personhood registered: did=$did fingers=${configs.size} backup=${backupFinger != null}")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "registration failed", e)
            Result.failure(e)
        }
    }

    // ── Verification ──

    /**
     * 验证流程:
     * 1. 从已注册手指（不含备用）随机选 2 根
     * 2. Rep(w'_j, hd_j) → k'_j (模糊提取器恢复)
     * 3. 本地重算 bioHash' 与存储比对
     * 4. 连续失败 3 次 → 激活备用手指
     */
    suspend fun verify(
        did: String,
        challengeSamples: Map<String, ByteArray> // label → single TEE signature
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val profile = loadProfile(did)
                ?: return@withContext Result.failure(Exception("不存在 $did 的人格锚定档案"))

            val regularFingers = profile.fingerConfigs.filter { !it.isBackup }
            if (regularFingers.size < 2)
                return@withContext Result.failure(Exception("至少需要 2 根主力手指"))

            // 从挑战样本中恢复各指密钥
            val components = mutableListOf<ByteArray>()
            for (finger in regularFingers) {
                val sample = challengeSamples[finger.label] ?: continue
                val hd = loadHelperData(finger.label)
                    ?: return@withContext handleFailedAttempt(profile)
                val commitment = profile.commitments[finger.label]
                    ?: return@withContext handleFailedAttempt(profile)

                val k = FuzzyExtractor.reproduce(sample, hd, commitment)
                    ?: return@withContext handleFailedAttempt(profile)

                components.add(FuzzyExtractor.hmacSha256(k, finger.label.toByteArray()))
            }

            // 含备用指（如果存在）
            val backupFinger = profile.fingerConfigs.firstOrNull { it.isBackup }
            if (backupFinger != null) {
                val bk = loadBackupKey(backupFinger.label)
                if (bk != null) {
                    components.add(FuzzyExtractor.hmacSha256(bk, backupFinger.label.toByteArray()))
                }
            }

            components.sortBy { it.contentToString() }
            val combined = components.reduce { acc, bytes -> acc + bytes }
            val bioHashPrime = FuzzyExtractor.hkdfSha256(combined, profile.salt, DOMAIN_TAG.toByteArray())

            if (bioHashPrime.contentEquals(profile.bioHash)) {
                resetFailureCount(profile.did)
                Log.i(TAG, "verification passed: did=$did")
                return@withContext Result.success(true)
            }

            return@withContext handleFailedAttempt(profile)
        } catch (e: Exception) {
            Log.e(TAG, "verification failed", e)
            Result.failure(e)
        }
    }

    private suspend fun handleFailedAttempt(profile: PersonhoodProfile): Result<Boolean> {
        val count = incrementFailureCount(profile.did)
        if (count >= MAX_CONSECUTIVE_FAILURES) {
            Log.w(TAG, "consecutive failures=$count, activating backup finger")
            return activateBackupFinger(profile)
        }
        Log.w(TAG, "verification failed: did=${profile.did} attempt=$count/$MAX_CONSECUTIVE_FAILURES")
        return Result.success(false)
    }

    private suspend fun activateBackupFinger(profile: PersonhoodProfile): Result<Boolean> {
        val backupFinger = profile.fingerConfigs.firstOrNull { it.isBackup }
            ?: return Result.success(false)

        val regularFingers = profile.fingerConfigs.filter { !it.isBackup }
        if (regularFingers.isEmpty()) return Result.success(false)

        // 用备用指+一根主力的存储密钥重算
        val regular = regularFingers.random()
        val bk = loadBackupKey(backupFinger.label)
            ?: return Result.success(false)
        val rh = loadHelperData(regular.label)
            ?: return Result.success(false)

        // 主力指需要从存储的 k 恢复（已预存）
        val rk = loadBackupKey(regular.label)
            ?: FuzzyExtractor.sha256(regular.label.toByteArray()) // fallback

        val components = listOf(
            FuzzyExtractor.hmacSha256(bk, backupFinger.label.toByteArray()),
            FuzzyExtractor.hmacSha256(rk, regular.label.toByteArray())
        ).sortedBy { it.contentToString() }
        val combined = components.reduce { acc, bytes -> acc + bytes }
        val bioHashPrime = FuzzyExtractor.hkdfSha256(combined, profile.salt, DOMAIN_TAG.toByteArray())

        if (bioHashPrime.contentEquals(profile.bioHash)) {
            resetFailureCount(profile.did)
            Log.i(TAG, "backup finger activated: did=${profile.did}")
            setBackupActivatedFlag(profile.did, regular.label)
            return Result.success(true)
        }
        return Result.success(false)
    }

    // ── Device Migration ──

    /** 一次性迁移授权令牌，旧设备 TEE 签发 */
    data class TransferAuthToken(
        val did: String,
        val oldDeviceFingerprint: String,     // SHA-256 of old device KeyStore attestation pubkey
        val newDeviceFingerprint: String,     // SHA-256 of new device KeyStore attestation pubkey (empty before pairing)
        val nonce: ByteArray,                 // 32 bytes random
        val createdAt: Long,                  // unix ms
        val expiresAt: Long,                  // createdAt + 300000 (5 min)
        val signature: ByteArray              // TEE ECDSA Sign(hash(did||oldFp||newFp||nonce||createdAt||expiresAt))
    )

    data class MigrationPackage(
        val did: String,
        val hdList: Map<String, ByteArray>,
        val salt: ByteArray,
        val commitments: Map<String, ByteArray>,
        val fingerConfigs: List<FingerConfig>,
        val authToken: TransferAuthToken? = null   // ✨ 4.0.0
    )

    /**
     * 签发迁移令牌。
     * 旧设备在用户通过生物验证 + C-08 授权后调用。
     *
     * @param did 要迁移的 DID
     * @param oldFp 旧设备硬指纹 hash
     * @param newFp 新设备硬指纹 hash（若尚未配对，可为空字符串）
     * @param signer 签名函数：ByteArray → ECDSA signature (由 KeyManager.teeSign 提供)
     */
    fun issueTransferAuthToken(
        did: String,
        oldFp: String,
        newFp: String,
        signer: (ByteArray) -> ByteArray
    ): TransferAuthToken {
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val now = System.currentTimeMillis()
        val expiresAt = now + 300_000L // 5 minutes

        val hash = MessageDigest.getInstance("SHA-256").digest(
            (did + oldFp + newFp).toByteArray() + nonce +
            longToBytes(now) + longToBytes(expiresAt)
        )
        val sig = signer(hash)

        Log.i(TAG, "TransferAuthToken issued: did=$did expiresIn=300s")
        return TransferAuthToken(did, oldFp, newFp, nonce, now, expiresAt, sig)
    }

    /**
     * 验证迁移令牌。
     * 新设备在收到 Token 后，由 Node 端验证签名和时效。
     *
     * @param oldTeePubKey 旧设备 TEE 公钥（从 Node binding 记录获取）
     * @return 验证结果
     */
    fun verifyTransferAuthToken(
        token: TransferAuthToken,
        oldTeePubKey: ByteArray,
        expectedNewFingerprint: String
    ): Result<Boolean> = runCatching {
        if (System.currentTimeMillis() > token.expiresAt)
            throw SecurityException("迁移令牌已过期 (expired at ${token.expiresAt})")
        if (token.newDeviceFingerprint != expectedNewFingerprint)
            throw SecurityException("新设备硬指纹不匹配")

        val hash = MessageDigest.getInstance("SHA-256").digest(
            (token.did + token.oldDeviceFingerprint + token.newDeviceFingerprint).toByteArray()
            + token.nonce + longToBytes(token.createdAt) + longToBytes(token.expiresAt)
        )

        // ECDSA verify (placeholder — real impl uses KeyManager.verify)
        java.security.Signature.getInstance("SHA256withECDSA").run {
            val pk = java.security.spec.X509EncodedKeySpec(oldTeePubKey)
            initVerify(java.security.KeyFactory.getInstance("EC").generatePublic(pk))
            update(hash)
            verify(token.signature)
        }
    }

    suspend fun exportForMigration(
        did: String,
        oldDeviceTeeSig: ByteArray
    ): Result<MigrationPackage> = withContext(Dispatchers.IO) {
        try {
            val profile = loadProfile(did)
                ?: return@withContext Result.failure(Exception("no profile"))

            // 旧设备 TEE 签名验证 (占位: 检查长度)
            if (oldDeviceTeeSig.size < 32)
                return@withContext Result.failure(Exception("old device TEE signature invalid"))

            val hdMap = profile.fingerConfigs.associate { fc ->
                fc.label to loadHelperData(fc.label)
            }.filterValues { it != null }.mapValues { it.value!! }

            val pkg = MigrationPackage(
                did = did,
                hdList = hdMap,
                salt = profile.salt,
                commitments = profile.commitments,
                fingerConfigs = profile.fingerConfigs
            )

            Log.i(TAG, "migration package prepared: did=$did fingers=${profile.fingerConfigs.size}")
            Result.success(pkg)
        } catch (e: Exception) {
            Log.e(TAG, "migration export failed", e)
            Result.failure(e)
        }
    }

    /**
     * 迁移后冻结旧设备 persona。
     * CONST-013：迁出后签名能力立即锁定。
     */
    fun freezePersonaAfterMigration(did: String) {
        securePrefs.edit()
            .putString("persona_state_$did", PersonaState.FROZEN_MIGRATED.name)
            .apply()
        Log.i(TAG, "persona frozen after migration: did=$did")
    }

    /** 旧设备解冻（需双向生物验证 + Node 确认 + 冷却期）。 */
    suspend fun unfreezePersona(
        did: String,
        bioHashFromBothDevices: ByteArray // TODO: real impl needs cross-device verification
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val state = getPersonaState(did)
        if (state != PersonaState.FROZEN_MIGRATED) {
            return@withContext Result.failure(Exception("persona is not in migrated-out state"))
        }
        // 冷却期：解冻需等待 3600s 以上
        val migratedAt = securePrefs.getLong("persona_migrated_at_$did", 0L)
        if (System.currentTimeMillis() - migratedAt < 3_600_000L) {
            return@withContext Result.failure(Exception("解冻冷却期未满（需等待1小时）"))
        }
        // TODO: 验证 bioHashFromBothDevices 同时匹配旧设备和新设备的 bioHash
        securePrefs.edit()
            .putString("persona_state_$did", PersonaState.ACTIVE.name)
            .apply()
        Log.i(TAG, "persona unfrozen: did=$did")
        Result.success(Unit)
    }

    fun getPersonaState(did: String): PersonaState {
        val s = securePrefs.getString("persona_state_$did", "ACTIVE") ?: "ACTIVE"
        return try { PersonaState.valueOf(s) } catch (_: Exception) { PersonaState.ACTIVE }
    }

    fun setPersonaMigratedAt(did: String) {
        securePrefs.edit().putLong("persona_migrated_at_$did", System.currentTimeMillis()).apply()
    }

    private fun longToBytes(value: Long): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(8)
        buf.putLong(value)
        return buf.array()
    }

    suspend fun importFromMigration(pkg: MigrationPackage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for ((label, hd) in pkg.hdList) {
                storeHelperData(label, hd)
            }
            // 保存 salt 和 commitments 以便 bioHash 验证
            val saltB64 = Base64.encodeToString(pkg.salt, Base64.NO_WRAP)
            securePrefs.edit()
                .putString("profile_${pkg.did}_salt", saltB64)
                .putInt("profile_${pkg.did}_fingerCount", pkg.fingerConfigs.size)
                .putString("persona_state_${pkg.did}", PersonaState.ACTIVE.name)
                .apply()
            pkg.fingerConfigs.forEachIndexed { i, fc ->
                securePrefs.edit()
                    .putString("profile_${pkg.did}_finger_${i}_label", fc.label)
                    .putBoolean("profile_${pkg.did}_finger_${i}_backup", fc.isBackup)
                    .apply()
            }
            pkg.commitments.entries.forEachIndexed { i, (label, commit) ->
                securePrefs.edit()
                    .putString("profile_${pkg.did}_commit_label_$i", label)
                    .putString("profile_${pkg.did}_commit_val_$i",
                        Base64.encodeToString(commit, Base64.NO_WRAP))
                    .apply()
            }
            securePrefs.edit()
                .putInt("profile_${pkg.did}_commitCount", pkg.commitments.size)
                .apply()

            Log.i(TAG, "migration imported: did=${pkg.did} fingers=${pkg.hdList.size}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "migration import failed", e)
            Result.failure(e)
        }
    }

    // ── Local Storage (EncryptedSharedPreferences) ──

    private fun storeHelperData(label: String, hd: ByteArray) {
        securePrefs.edit()
            .putString("hd_$label", Base64.encodeToString(hd, Base64.NO_WRAP))
            .apply()
    }

    private fun loadHelperData(label: String): ByteArray? {
        val encoded = securePrefs.getString("hd_$label", null) ?: return null
        return try { Base64.decode(encoded, Base64.NO_WRAP) } catch (_: Exception) { null }
    }

    private fun storeBackupKey(label: String, k: ByteArray) {
        securePrefs.edit()
            .putString("backup_k_$label", Base64.encodeToString(k, Base64.NO_WRAP))
            .apply()
    }

    private fun loadBackupKey(label: String): ByteArray? {
        val encoded = securePrefs.getString("backup_k_$label", null) ?: return null
        return try { Base64.decode(encoded, Base64.NO_WRAP) } catch (_: Exception) { null }
    }

    private fun saveProfile(profile: PersonhoodProfile) {
        securePrefs.edit().apply {
            putString("profile_${profile.did}_biohash", Base64.encodeToString(profile.bioHash, Base64.NO_WRAP))
            putString("profile_${profile.did}_salt", Base64.encodeToString(profile.salt, Base64.NO_WRAP))
            putInt("profile_${profile.did}_fingerCount", profile.fingerConfigs.size)
            profile.fingerConfigs.forEachIndexed { i, fc ->
                putString("profile_${profile.did}_finger_${i}_label", fc.label)
                putBoolean("profile_${profile.did}_finger_${i}_backup", fc.isBackup)
            }
            // Commitments
            putInt("profile_${profile.did}_commitCount", profile.commitments.size)
            profile.commitments.entries.forEachIndexed { i, (label, commit) ->
                putString("profile_${profile.did}_commit_label_$i", label)
                putString("profile_${profile.did}_commit_val_$i", Base64.encodeToString(commit, Base64.NO_WRAP))
            }
        }.apply()
    }

    private fun loadProfile(did: String): PersonhoodProfile? {
        val bioHashB64 = securePrefs.getString("profile_${did}_biohash", null) ?: return null
        val saltB64 = securePrefs.getString("profile_${did}_salt", null) ?: return null
        val count = securePrefs.getInt("profile_${did}_fingerCount", 0)
        if (count == 0) return null

        val configs = (0 until count).map { i ->
            FingerConfig(
                index = i,
                label = securePrefs.getString("profile_${did}_finger_${i}_label", "") ?: "",
                isBackup = securePrefs.getBoolean("profile_${did}_finger_${i}_backup", false)
            )
        }

        val commitCount = securePrefs.getInt("profile_${did}_commitCount", 0)
        val commitments = mutableMapOf<String, ByteArray>()
        for (i in 0 until commitCount) {
            val label = securePrefs.getString("profile_${did}_commit_label_$i", null) ?: continue
            val valB64 = securePrefs.getString("profile_${did}_commit_val_$i", null) ?: continue
            commitments[label] = Base64.decode(valB64, Base64.NO_WRAP)
        }

        return PersonhoodProfile(
            bioHash = Base64.decode(bioHashB64, Base64.NO_WRAP),
            did = did,
            fingerConfigs = configs,
            salt = Base64.decode(saltB64, Base64.NO_WRAP),
            commitments = commitments
        )
    }

    private fun incrementFailureCount(did: String): Int {
        val count = securePrefs.getInt("fail_$did", 0) + 1
        securePrefs.edit().putInt("fail_$did", count).apply()
        return count
    }

    private fun resetFailureCount(did: String) {
        securePrefs.edit().putInt("fail_$did", 0).apply()
    }

    private fun setBackupActivatedFlag(did: String, replacedFinger: String) {
        securePrefs.edit()
            .putBoolean("backup_activated_$did", true)
            .putString("backup_replaced_$did", replacedFinger)
            .apply()
    }

    fun getBackupActivatedInfo(did: String): Pair<Boolean, String?> {
        val activated = securePrefs.getBoolean("backup_activated_$did", false)
        val replaced = securePrefs.getString("backup_replaced_$did", null)
        return Pair(activated, replaced)
    }

    fun isPersonhoodRegistered(did: String): Boolean =
        securePrefs.contains("profile_${did}_biohash")
}
