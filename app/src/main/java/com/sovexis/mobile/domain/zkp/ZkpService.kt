package com.sovexis.mobile.domain.zkp

/**
 * ZKP 服务接口
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 基于 Mopro 实现 Groth16 证明，通过 JitPack 发布 Kotlin 原生包。
 */
interface ZkpService {
    /**
     * 生成 ZKP 证明。
     *
     * @param request 包含四个私有输入的证明请求
     * @param isHighRisk 是否为高风险操作（控制是否启用真假混淆）
     * @return ZkpProof 或失败原因
     */
    suspend fun prove(request: ZkpProveRequest, isHighRisk: Boolean = false): Result<ZkpProofData>

    /**
     * 验证 ZKP 证明。
     */
    suspend fun verify(request: ZkpVerifyRequest): ZkpVerifyResult

    /**
     * 检查设备是否支持 ZKP（Mopro 是否可用）。
     */
    fun isZkpAvailable(): Boolean
}
