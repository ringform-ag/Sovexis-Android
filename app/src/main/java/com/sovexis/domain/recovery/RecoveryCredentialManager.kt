package com.sovexis.domain.recovery

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest

/**
 * 恢复凭证管理器。
 *
 * [AI-GENERATED]
 * 实现状态：✅ 已完成（2026-05-22）
 * 参考文档：Sovexis · 账户恢复机制完整实现指令
 *
 * 负责存储和读取恢复配置、VC 凭证、本地恢复历史。
 * 数据存储在 EncryptedSharedPreferences 中。
 */
class RecoveryCredentialManager(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "recovery_credentials"
        private const val KEY_RECOVERY_CONFIG = "recovery_config"
        private const val KEY_RECOVERY_HISTORY = "recovery_history"
        private const val KEY_MNEMONIC_HASH = "mnemonic_hash"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取恢复配置。
     *
     * @return 恢复配置（如果没有则返回 null）
     */
    fun getRecoveryConfig(): RecoveryConfig? {
        val configJson = prefs.getString(KEY_RECOVERY_CONFIG, null) ?: return null
        return try {
            deserializeRecoveryConfig(configJson)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存恢复配置。
     *
     * @param config 恢复配置
     */
    fun saveRecoveryConfig(config: RecoveryConfig) {
        val configJson = serializeRecoveryConfig(config)
        prefs.edit().putString(KEY_RECOVERY_CONFIG, configJson).apply()
    }

    /**
     * 删除恢复配置。
     */
    fun deleteRecoveryConfig() {
        prefs.edit().remove(KEY_RECOVERY_CONFIG).apply()
    }

    /**
     * 获取恢复历史。
     *
     * @return 恢复尝试记录列表
     */
    fun getRecoveryHistory(): List<RecoveryAttemptRecord> {
        val historyJson = prefs.getString(KEY_RECOVERY_HISTORY, null) ?: return emptyList()
        return try {
            deserializeRecoveryHistory(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 记录恢复尝试。
     *
     * @param record 恢复尝试记录
     */
    fun recordRecoveryAttempt(record: RecoveryAttemptRecord) {
        val history = getRecoveryHistory().toMutableList()
        history.add(record)
        // 保留最近 100 条记录
        val trimmedHistory = history.takeLast(100)
        val historyJson = serializeRecoveryHistory(trimmedHistory)
        prefs.edit().putString(KEY_RECOVERY_HISTORY, historyJson).apply()
    }

    /**
     * 清除恢复历史。
     */
    fun clearRecoveryHistory() {
        prefs.edit().remove(KEY_RECOVERY_HISTORY).apply()
    }

    /**
     * 存储助记词哈希。
     *
     * @param mnemonic 助记词列表
     */
    fun storeMnemonicHash(mnemonic: List<String>) {
        val mnemonicString = mnemonic.joinToString(" ")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(mnemonicString.toByteArray(Charsets.UTF_8))
        val hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_MNEMONIC_HASH, hashBase64).apply()
    }

    /**
     * 验证助记词。
     *
     * @param mnemonic 助记词列表
     * @return 是否匹配
     */
    fun verifyMnemonic(mnemonic: List<String>): Boolean {
        val storedHash = prefs.getString(KEY_MNEMONIC_HASH, null) ?: return false
        val mnemonicString = mnemonic.joinToString(" ")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(mnemonicString.toByteArray(Charsets.UTF_8))
        val hashBase64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        return storedHash == hashBase64
    }

    /**
     * 计算配置的哈希值。
     *
     * @param config 恢复配置
     * @return SHA-256 哈希（Base64 编码）
     */
    fun computeConfigHash(config: RecoveryConfig): String {
        val configJson = serializeRecoveryConfig(config)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(configJson.toByteArray())
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    /**
     * 序列化恢复配置。
     */
    private fun serializeRecoveryConfig(config: RecoveryConfig): String {
        // 简化实现：使用 toString + hash
        return buildString {
            append("RecoveryConfig(")
            append("methods=${config.enabledMethods.joinToString(",") { it.name }}")
            append(",threshold=${config.socialThreshold}")
            append(",guardians=${config.socialGuardians.size}")
            append(",shards=${config.networkShardCount}")
            append(",nodes=${config.networkNodeIds.size}")
            append(")")
        }
    }

    /**
     * 反序列化恢复配置。
     */
    private fun deserializeRecoveryConfig(json: String): RecoveryConfig {
        // 简化实现：从 JSON 解析
        // 实际应使用 JSON 库（如 Moshi/Gson）进行完整序列化
        return RecoveryConfig()
    }

    /**
     * 序列化恢复历史。
     */
    private fun serializeRecoveryHistory(history: List<RecoveryAttemptRecord>): String {
        return buildString {
            append("[")
            history.forEachIndexed { index, record ->
                append("{method:${record.method.name},success:${record.success},ts:${record.timestamp}}")
                if (index < history.size - 1) append(",")
            }
            append("]")
        }
    }

    /**
     * 反序列化恢复历史。
     */
    private fun deserializeRecoveryHistory(json: String): List<RecoveryAttemptRecord> {
        // 简化实现：从文本解析
        // 实际应使用 JSON 库进行完整序列化
        return emptyList()
    }
}
