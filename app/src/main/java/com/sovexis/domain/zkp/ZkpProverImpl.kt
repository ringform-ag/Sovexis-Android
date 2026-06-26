package com.sovexis.domain.zkp

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Mopro 集成实现 - ZKP 证明器 (v2)
 *
 * ═══════════════════════════════════════════════════════════
 * ⚠️  安全警告 — 当前仅集成测试电路（multiplier）
 * ═══════════════════════════════════════════════════════════
 *
 * 当前电路: Groth16 multiplier (a * b = c)
 * 目的: 验证 Mopro FFI 管线（prove → verify → Solidity 链上验证）
 * 安全状态: 不提供任何身份/生物特征断言能力
 *
 * 密码学专家交付四元承诺电路后，替换以下内容:
 *   - buildMultiplierCircuitInputs() → buildQuadCommitmentCircuitInputs()
 *   - 电路文件: multiplier.zkey → quad_commitment.zkey
 *
 * 在此之前，ZkpVerifierImpl 验证的"证明"仅证明:
 *   截断(biometricSig[0:8]) × 截断(deviceBinding[0:8]) = 截断(kdfs[0:16])
 * 这个关系在密码学上没有实际安全含义。
 * ═══════════════════════════════════════════════════════════
 *
 * [AI-GENERATED]
 * 生成时间: 2026-06-01
 * 实现状态: ✅ 已集成 Mopro FFI — 真实 prove/verify 调用
 * 参考文档: Mopro 集成 · 补充指令 (ringform)
 *
 * ## Mopro 调用流程（prove）
 *
 * 1. 通过 CircuitPathProvider 获取 zkey 路径
 * 2. 将 ZkpProveRequest 转为电路输入 JSON
 *    - 当前测试电路（multiplier）: {"a": [...], "b": [...], "c": [...]}
 *    - 未来四元承诺电路: {"biometricCommitment": ..., "deviceBindingCommitment": ..., "kdfsPatternHash": ...}
 * 3. 调用 MoproLib.generateCircomProof(zkeyPath, jsonInputs, ProofLib.ARKWORKS)
 * 4. 检查 Root 状态，附加风险标签
 * 5. 封装 ZkpProofData 返回
 *
 * ## Mopro 调��流程（verify）
 *
 * 1. 调用 MoproLib.verifyCircomProof(zkeyPath, proofString, ProofLib.ARKWORKS)
 * 2. 返回 ZkpVerifyResult.Valid 或 Invalid
 *
 * ## 降级策略
 *
 * 如果 Mopro 原生库未加载或电路文件缺失，prove() 返回 Result.failure，
 * verify() 返回 Invalid。isZkpAvailable() 反映真实状态。
 */
class ZkpProverImpl(
    private val context: Context
) : ZkpService {

    companion object {
        private const val TAG = "ZkpProverImpl"
    }

    private val rootDetector = RootDetector
    private var moproAvailable: Boolean? = null

    override suspend fun prove(
        request: ZkpProveRequest,
        isHighRisk: Boolean
    ): Result<ZkpProofData> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 获取 zkey 路径
            val zkeyPath = CircuitPathProvider.getZkeyPath(context)
                ?: throw IllegalStateException("电路文件未就绪: zkey 不存在")

            // 2. 将 ZkpProveRequest 转为电路输入 JSON
            //    —— 当前使用测试电路 (multiplier) 的格式
            //    —— 密码学专家交付四元承诺电路后，替换此段 JSON 构建逻辑
            val circuitInputs = buildMultiplierCircuitInputs(request)

            // 3. 调用 Mopro 原生库生成 Circom Groth16 证明
            val proofString = MoproLib.generateCircomProof(
                zkeyPath = zkeyPath,
                circuitInputs = circuitInputs,
                proofLib = MoproLib.ProofLib.ARKWORKS
            )

            // 4. 检查设备 Root 状态，附加风险标签
            val riskLabel = if (rootDetector.isDeviceRooted()) "RISK_ROOTED" else "CLEAN"

            // 5. 构造 ZkpProofData
            ZkpProofData(
                proofBytes = proofString.toByteArray(Charsets.UTF_8),
                publicInputs = listOf(
                    Base64.encodeToString(request.sessionNonce, Base64.NO_WRAP),
                    request.publicKeyPem,
                    Base64.encodeToString(request.expectedCommitmentRoot, Base64.NO_WRAP)
                ),
                riskLabel = riskLabel
            )
        }
    }

    override suspend fun verify(request: ZkpVerifyRequest): ZkpVerifyResult {
        return try {
            // 获取 zkey 路径（验证也需要 zkey 来重建验证密钥）
            val zkeyPath = CircuitPathProvider.getZkeyPath(context)
                ?: return ZkpVerifyResult.Invalid("电路文件未就绪: zkey 不存在")

            val proofString = String(request.proofBytes, Charsets.UTF_8)
            val isValid = MoproLib.verifyCircomProof(
                zkeyPath = zkeyPath,
                proof = proofString,
                proofLib = MoproLib.ProofLib.ARKWORKS
            )

            if (isValid) ZkpVerifyResult.Valid
            else ZkpVerifyResult.Invalid("Mopro 验证失败: 证明无效")
        } catch (e: Throwable) {
            Log.e(TAG, "verify 异常", e)
            ZkpVerifyResult.Invalid("验证异常: ${e.message}")
        }
    }

    override fun isZkpAvailable(): Boolean {
        moproAvailable?.let { return it }

        moproAvailable = try {
            // 1. 检查电路文件
            if (!CircuitPathProvider.isAvailable(context)) {
                Log.w(TAG, "ZKP 不可用: 电路文件缺失")
                false
            } else {
                // 2. 检查 Mopro 原生库
                val version = MoproLib.getVersion()
                Log.i(TAG, "Mopro 已加载, 版本: $version")
                true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ZKP 不可用: ${e.message}")
            false
        }
        return moproAvailable!!
    }

    // ====== 电路输入构建 ======

    /**
     * 构建乘法器测试电路的输入 JSON。
     *
     * 当前测试电路是一个简单的 Circom 乘法器：
     *   signal input a;
     *   signal input b;
     *   signal output c;
     *   c <== a * b;
     *
     * 输入格式: {"a": <正整数>, "b": <正整数>}
     * 从输入数据的前 8 字节解析为 Long（无符号），确保可用作 BN254 域元素。
     *
     * 密码学专家交付四元承诺电路后，替换为:
     *   {"biometricCommitment": [...], "deviceBindingCommitment": [...], "kdfsPatternHash": [...]}
     */
    private fun buildMultiplierCircuitInputs(request: ZkpProveRequest): String {
        // 从字节数组中提取无符号整数作为电路输入
        val a = bytesToULong(request.biometricSignature)
        val b = bytesToULong(request.deviceBindingData)

        // 构建 JSON: {"a": 正整数, "b": 正整数}
        val json = JSONObject()
        json.put("a", a.toString())
        json.put("b", b.toString())

        return json.toString()
    }

    /**
     * 将 ByteArray 的前 8 字节转为无符号 Long 的十进制字符串表示。
     */
    private fun bytesToULong(bytes: ByteArray): java.math.BigInteger {
        return java.math.BigInteger(1, bytes.copyOf(8.coerceAtMost(bytes.size)))
    }
}
