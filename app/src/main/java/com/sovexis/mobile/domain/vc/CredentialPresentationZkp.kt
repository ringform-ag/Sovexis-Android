package com.sovexis.mobile.domain.vc

import com.sovexis.mobile.domain.zkp.*

/**
 * 凭证出示 ZKP 包装器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 封装凭证出示场景的 ZKP 证明生成，支持缓存策略：
 * - 先查缓存（1 小时有效期）
 * - 缓存未命中或 requireFresh=true 时生成新证明
 * - 异步生成，不阻塞 UI
 */
class CredentialPresentationZkp(
    private val zkpService: ZkpService,
    private val cacheManager: ZkpCacheManager
) {
    /**
     * 出示凭证（带 ZKP 证明）
     *
     * @param credentialId 凭证 ID
     * @param challenge 服务商提供的挑战
     * @param request ZKP 证明请求（包含生物认证、设备绑定、KDFS 等）
     * @param requireFresh 是否强制生成新证明（忽略缓存）
     * @return 包含 ZKP 证明的凭证出示结果
     */
    suspend fun present(
        credentialId: String,
        challenge: ByteArray,
        request: ZkpProveRequest,
        requireFresh: Boolean = false
    ): Result<Presentation> {
        return runCatching {
            val cacheKey = cacheManager.generateCacheKey(credentialId, challenge)

            // 先查缓存
            val proof = cacheManager.get(cacheKey, requireFresh)
                ?: zkpService.prove(request).getOrThrow().also {
                    // 缓存新证明
                    cacheManager.put(cacheKey, it)
                }

            Presentation(
                credentialId = credentialId,
                proof = proof
            )
        }
    }

    /**
     * 使凭证出示缓存失效
     */
    suspend fun invalidateCache(credentialId: String, challenge: ByteArray) {
        val cacheKey = cacheManager.generateCacheKey(credentialId, challenge)
        cacheManager.invalidate(cacheKey)
    }
}

/**
 * 凭证出示结果
 */
data class Presentation(
    val credentialId: String,
    val proof: ZkpProofData
)
