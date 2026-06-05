package com.sovexis.domain.zkp

import android.util.Log

/**
 * ZKP 验证器实现 (Mopro v2)
 *
 * [AI-GENERATED]
 * 生成时间: 2026-06-01
 * 参考文档: Mopro 集成 · 补充指令 (ringform)
 *
 * 委托 MoproLib.verifyCircomProof 进行 Groth16 证明验证。
 * 验证方侧无需 Root 检测——Root 检测仅在证明生成侧有意义。
 */
class ZkpVerifierImpl : ZkpVerifier {

    companion object {
        private const val TAG = "ZkpVerifierImpl"
    }

    override suspend fun verify(request: ZkpVerifyRequest): ZkpVerifyResult {
        return try {
            val proofString = String(request.proofBytes, Charsets.UTF_8)
            val verificationKeyPath = String(request.verificationKey, Charsets.UTF_8)

            val isValid = MoproLib.verifyCircomProof(
                zkeyPath = verificationKeyPath,
                proof = proofString,
                proofLib = MoproLib.ProofLib.ARKWORKS
            )

            if (isValid) ZkpVerifyResult.Valid
            else ZkpVerifyResult.Invalid("Mopro 验证失败: 证明无效")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Mopro 原生库未加载", e)
            ZkpVerifyResult.Invalid("Mopro 不可用: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "验证异常", e)
            ZkpVerifyResult.Invalid("验证异常: ${e.message}")
        }
    }
}

/**
 * ZKP 验证器接口
 */
interface ZkpVerifier {
    suspend fun verify(request: ZkpVerifyRequest): ZkpVerifyResult
}
