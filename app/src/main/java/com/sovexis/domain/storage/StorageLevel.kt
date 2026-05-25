package com.sovexis.domain.storage

import com.sovexis.domain.communication.CommunicationLevel
import com.sovexis.domain.recovery.RecoveryConfig

/**
 * Sovexis 存储安全级别
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: Path ORAM 详细指南 · Sovexis 存储混淆模块 Level 2 (陵谦)
 *
 * 定义三层存储安全级别：
 * - L0: 标准存储，无混淆
 * - L1: 虚假读取混淆（Level1Obfuscator）
 * - L2: Path ORAM 全混淆，可证明安全
 */

/**
 * 存储安全级别
 */
enum class StorageLevel {
    /** L0: 无混淆，直接 Room 读写 */
    STANDARD,

    /** L1: 虚假读取混淆（Level1Obfuscator） */
    OBFUSCATED,

    /** L2: Path ORAM 全混淆，可证明安全 */
    SOVEREIGN
}

/**
 * L2 映射表备份策略
 */
enum class MapBackupStrategy {
    /** 不备份（默认），最高安全性 */
    NONE,

    /** 加密备份到服务商 */
    SERVICE,

    /** 自托管备份（导出文件或二维码） */
    SELF_HOSTED
}

/**
 * 存储配置
 *
 * @param level 存储安全级别
 * @param communicationLevel 通信安全级别（C0/C1/C2）
 * @param covertUserLevel 隐蔽传输用户级别（0=公开, 1=普通, 2=严格）
 * @param recoveryConfig 账户恢复配置
 * @param mapBackupStrategy L2 映射表备份策略
 * @param backupServiceDid 备份服务商的 DID（用于 SERVICE 策略）
 */
data class StorageConfig(
    val level: StorageLevel = StorageLevel.OBFUSCATED,
    val communicationLevel: CommunicationLevel = CommunicationLevel.STANDARD,
    val covertUserLevel: Int = 1,  // 默认普通用户（L1）
    val recoveryConfig: RecoveryConfig = RecoveryConfig(),
    val mapBackupStrategy: MapBackupStrategy = MapBackupStrategy.NONE,
    val backupServiceDid: String? = null
)
