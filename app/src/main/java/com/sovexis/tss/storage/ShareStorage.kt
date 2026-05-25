package com.sovexis.tss.storage

/**
 * 密钥份额安全存储接口
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 *
 * 定义密钥份额的安全存储操作，包括加密存储、解密读取和安全擦除。
 * 实现必须使用 Android Keystore (StrongBox 优先) 保护密钥材料。
 */
interface ShareStorage {

    /**
     * 加密存储密钥份额
     *
     * 使用 Android Keystore 生成的 AES-256 密钥加密份额数据。
     * 每次加密使用新的随机 IV。
     *
     * @param shareId 份额唯一标识
     * @param encryptedShare 已加密的份额数据（由 Android Keystore 加密）
     * @return Result<Unit> 存储结果
     */
    suspend fun save(shareId: String, encryptedShare: ByteArray): Result<Unit>

    /**
     * 读取并解密密钥份额
     *
     * 从存储中读取加密的份额数据，使用 Android Keystore 解密。
     *
     * @param shareId 份额唯一标识
     * @return Result<ByteArray> 解密的份额数据
     */
    suspend fun load(shareId: String): Result<ByteArray>

    /**
     * 安全擦除密钥份额
     *
     * 先用全零字节覆写份额文件，再删除，确保无法恢复。
     * 这是高安全模式降级时的关键操作。
     *
     * @param shareId 份额唯一标识
     * @return Result<Unit> 擦除结果
     */
    suspend fun secureDelete(shareId: String): Result<Unit>

    /**
     * 检查份额是否存在
     *
     * @param shareId 份额唯一标识
     * @return Boolean 是否存在
     */
    suspend fun exists(shareId: String): Boolean
}
