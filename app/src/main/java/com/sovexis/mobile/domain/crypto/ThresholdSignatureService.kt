package com.sovexis.mobile.domain.crypto

/**
 * Sovexis 阈值签名服务接口 (2P-ECDSA)
 *
 * [AI-GENERATED] 基于陵谦指令文档重写
 * 生成时间: 2026-05-20
 * 许可证: Apache 2.0
 * 实现状态: ⚠️ 接口定义完成，等待 bnb-chain/tss-lib AAR 集成
 *
 * 基于 bnb-chain/tss-lib (Go, MIT) 的 2P-ECDSA 阈值签名实现
 * 支持多形态部署：Android 设备 (本地) + 家庭服务器/硬件令牌 (远程)
 *
 * 核心流程：
 * 1. generateKeyShares(): 执行 GG20 Keygen 协议，与远程方交互生成密钥份额
 * 2. partialSign(): 使用本地份额对数据进行部分签名
 * 3. combineSignatures(): 合并本地和远程部分签名，生成完整 ECDSA 签名
 *
 * 通信方式：
 * - 主力：蓝牙 (BLE)
 * - 备选：WiFi Direct / 局域网 TCP (强制 Noise IK 隧道加密)
 *
 * 安全说明：
 * - 密钥份额使用 Android Keystore (StrongBox) 加密存储
 * - 私钥全程不暴露，仅通过协议交互完成签名
 * - 支持安全擦除（高安全模式降级时使用）
 */
interface ThresholdSignatureService {

    /**
     * 生成 2-of-2 密钥份额
     *
     * 执行完整的 GG20 Keygen 协议，需要与远程方通过 MessageTransceiver 交互。
     * 此过程会生成两个密钥份额，一个保存在本地，一个由远程方持有。
     *
     * @param transceiver 通信信道，用于与远程方交换协议消息
     * @return Result<KeyShareInfo> 本地份额信息，包含公钥和份额标识
     */
    suspend fun generateKeyShares(
        transceiver: MessageTransceiver
    ): Result<KeyShareInfo>

    /**
     * 使用本地份额对数据进行部分签名
     *
     * 执行 GG20 Signing 协议的本地部分，生成部分签名并发送给远程方。
     * 需要远程方的配合才能完成完整签名。
     *
     * @param data 待签名数据的 SHA-256 哈希（32字节）
     * @param transceiver 通信信道，用于与远程方交换签名协议消息
     * @return Result<PartialSignature> 本地的部分签名
     */
    suspend fun partialSign(
        data: ByteArray,
        transceiver: MessageTransceiver
    ): Result<PartialSignature>

    /**
     * 合并本地和远程的部分签名，生成完整的 ECDSA 签名
     *
     * 当收集到足够的部分签名后（2-of-2 需要两个），可以合并为完整的 DER 编码 ECDSA 签名。
     * 此操作不需要网络通信，纯本地计算。
     *
     * @param localPartial 本地部分签名
     * @param remotePartial 远程部分签名
     * @return Result<ThresholdSignature> 完整的阈值签名（DER 编码）
     */
    suspend fun combineSignatures(
        localPartial: PartialSignature,
        remotePartial: RemotePartialSignature
    ): Result<ThresholdSignature>

    /**
     * 获取本地份额的元信息
     *
     * 返回本地存储的密钥份额信息，不包含敏感的份额数据本身。
     *
     * @return Result<KeyShareInfo> 本地份额元信息
     */
    fun getLocalShareInfo(): Result<KeyShareInfo>

    /**
     * 删除本地份额（高安全模式降级时使用）
     *
     * 安全擦除本地存储的密钥份额，先覆写后删除，确保无法恢复。
     * 删除后无法再进行阈值签名，除非重新执行密钥生成。
     *
     * @return Result<Unit> 删除结果
     */
    suspend fun deleteLocalShare(): Result<Unit>
}

/**
 * 通信信道抽象接口
 *
 * 用于 TSS 协议消息传输的抽象，支持多种传输方式：
 * - 蓝牙 (BluetoothTransceiver)
 * - WiFi/局域网 (WifiTransceiver，含 Noise 隧道)
 * - 模拟/测试 (MockTransceiver)
 */
interface MessageTransceiver {

    /**
     * 向远程方发送协议消息
     *
     * @param message TSS 协议消息信封
     * @return Result<Unit> 发送结果
     */
    suspend fun send(message: TssMessage): Result<Unit>

    /**
     * 接收来自远程方的协议消息
     *
     * 阻塞等待直到收到消息或超时。
     *
     * @return Result<TssMessage> 接收到的消息
     */
    suspend fun receive(): Result<TssMessage>

    /**
     * 通信信道是否可用
     *
     * 检查当前信道是否可以正常通信（如蓝牙已连接、WiFi 已建立等）。
     *
     * @return Boolean 是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 关闭连接
     *
     * 释放资源，断开连接。
     */
    suspend fun close()
}

/**
 * TSS 协议消息信封
 *
 * 统一的消息格式，与具体 TSS 库解耦。
 * 包含协议会话标识、发送/接收方、轮次和载荷。
 *
 * @property sessionId 协议会话唯一标识
 * @property fromShareId 发送方份额 ID
 * @property toShareId 接收方份额 ID
 * @property round 协议轮次（用于顺序控制）
 * @property payload 库特定的序列化消息数据
 * @property timestamp 消息创建时间戳
 */
data class TssMessage(
    val sessionId: String,
    val fromShareId: String,
    val toShareId: String,
    val round: Int,
    val payload: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TssMessage) return false
        return sessionId == other.sessionId &&
                fromShareId == other.fromShareId &&
                toShareId == other.toShareId &&
                round == other.round &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + fromShareId.hashCode()
        result = 31 * result + toShareId.hashCode()
        result = 31 * result + round
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * 密钥份额信息
 *
 * 包含密钥份额的元信息，不包含敏感的份额数据本身。
 *
 * @property shareId 份额唯一标识
 * @property publicKey 完整的 ECDSA 公钥（P-256, 未压缩, 65 字节）
 * @property threshold 重建阈值（2-of-2 时为 2）
 * @property totalShares 总份额数（2-of-2 时为 2）
 * @property createdAt 创建时间戳
 */
data class KeyShareInfo(
    val shareId: String,
    val publicKey: ByteArray,
    val threshold: Int = 2,
    val totalShares: Int = 2,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyShareInfo) return false
        return shareId == other.shareId &&
                publicKey.contentEquals(other.publicKey) &&
                threshold == other.threshold &&
                totalShares == other.totalShares
    }

    override fun hashCode(): Int {
        var result = shareId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + threshold
        result = 31 * result + totalShares
        return result
    }
}

/**
 * 本地部分签名
 *
 * 由本地份额生成的部分签名数据。
 *
 * @property sessionId 签名会话 ID
 * @property shareId 本地份额 ID
 * @property partialSigData 部分签名数据（库特定格式）
 * @property timestamp 签名时间戳
 */
data class PartialSignature(
    val sessionId: String,
    val shareId: String,
    val partialSigData: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PartialSignature) return false
        return sessionId == other.sessionId &&
                shareId == other.shareId &&
                partialSigData.contentEquals(other.partialSigData)
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + shareId.hashCode()
        result = 31 * result + partialSigData.contentHashCode()
        return result
    }
}

/**
 * 远程部分签名
 *
 * 由远程方生成的部分签名数据。
 *
 * @property sessionId 签名会话 ID
 * @property shareId 远程份额 ID
 * @property partialSigData 部分签名数据（库特定格式）
 * @property timestamp 签名时间戳
 */
data class RemotePartialSignature(
    val sessionId: String,
    val shareId: String,
    val partialSigData: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemotePartialSignature) return false
        return sessionId == other.sessionId &&
                shareId == other.shareId &&
                partialSigData.contentEquals(other.partialSigData)
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + shareId.hashCode()
        result = 31 * result + partialSigData.contentHashCode()
        return result
    }
}

/**
 * 完整阈值签名
 *
 * 合并后的完整 ECDSA 签名，DER 编码。
 *
 * @property signature DER 编码的 ECDSA 签名
 * @property publicKey 对应公钥（用于验证）
 * @property algorithm 签名算法（默认 ECDSA_P256）
 * @property timestamp 签名时间戳
 */
data class ThresholdSignature(
    val signature: ByteArray,
    val publicKey: ByteArray,
    val algorithm: String = "ECDSA_P256",
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThresholdSignature) return false
        return signature.contentEquals(other.signature) &&
                publicKey.contentEquals(other.publicKey) &&
                algorithm == other.algorithm
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * 密钥生成输出
 *
 * 密钥生成协议的完整输出，包含本地份额和公钥。
 *
 * @property shareId 本地份额 ID
 * @property encryptedShare 加密的份额数据（由 Android Keystore 保护）
 * @property publicKey 完整公钥
 * @property threshold 阈值
 * @property totalShares 总份额数
 */
data class KeygenOutput(
    val shareId: String,
    val encryptedShare: ByteArray,
    val publicKey: ByteArray,
    val threshold: Int,
    val totalShares: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeygenOutput) return false
        return shareId == other.shareId &&
                encryptedShare.contentEquals(other.encryptedShare) &&
                publicKey.contentEquals(other.publicKey) &&
                threshold == other.threshold &&
                totalShares == other.totalShares
    }

    override fun hashCode(): Int {
        var result = shareId.hashCode()
        result = 31 * result + encryptedShare.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + threshold
        result = 31 * result + totalShares
        return result
    }
}
