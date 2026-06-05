package com.sovexis.domain.zkp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

/**
 * ZkpProverImpl 端到端验证测试
 *
 * [AI-GENERATED] 2026-06-03 | Mopro 集成收尾
 *
 * 验证 ZKP prove → verify 完整链路可走通。
 * 当前使用 multiplier2_wc 测试电路 (c = a × b)。
 *
 * ## 前置条件
 * - 设备需支持 arm64-v8a
 * - jniLibs 中已部署 libsovexis_zkp.so
 * - assets/circuit 中已部署 multiplier2_wc_final.zkey
 */
class ZkpProverImplTest {

    private lateinit var context: Context
    private lateinit var zkpService: ZkpService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        zkpService = ZkpProverImpl(context)
    }

    /**
     * ZKP-001: Mopro 原生库可加载
     */
    @Test
    fun isZkpAvailable_returnsTrue() {
        assertTrue("ZKP 应可用: .so 和 zkey 均已部署", zkpService.isZkpAvailable())
    }

    /**
     * ZKP-002: prove 生成有效证明
     */
    @Test
    fun prove_generatesValidProof() = runBlocking {
        // 构造测试请求
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val request = ZkpProveRequest(
            biometricSignature = ByteArray(8).also { SecureRandom().nextBytes(it) },
            deviceBindingData = ByteArray(8).also { SecureRandom().nextBytes(it) },
            kdfsPatternHash = ByteArray(32).also { SecureRandom().nextBytes(it) },
            sessionNonce = nonce,
            publicKeyPem = "test-public-key-pem",
            expectedCommitmentRoot = ByteArray(32).also { SecureRandom().nextBytes(it) }
        )

        val result = zkpService.prove(request)

        assertTrue("prove 应成功", result.isSuccess)
        val proof = result.getOrNull()
        assertNotNull("证明数据不应为空", proof)
        assertTrue("证明字节不应为空", proof!!.proofBytes.isNotEmpty())
        assertTrue("公开输入不应为空", proof.publicInputs.isNotEmpty())
    }

    /**
     * ZKP-003: verify 验证 prove 生成的证明
     */
    @Test
    fun verify_acceptsGeneratedProof() = runBlocking {
        // 生成证明
        val request = ZkpProveRequest(
            biometricSignature = ByteArray(8).also { SecureRandom().nextBytes(it) },
            deviceBindingData = ByteArray(8).also { SecureRandom().nextBytes(it) },
            kdfsPatternHash = ByteArray(32).also { SecureRandom().nextBytes(it) },
            sessionNonce = ByteArray(32).also { SecureRandom().nextBytes(it) },
            publicKeyPem = "test-public-key-pem-verify",
            expectedCommitmentRoot = ByteArray(32).also { SecureRandom().nextBytes(it) }
        )

        val proveResult = zkpService.prove(request)
        assertTrue("prove 应成功", proveResult.isSuccess)
        val proof = proveResult.getOrNull()!!

        // 验证生成的证明
        val verifyRequest = ZkpVerifyRequest(
            proofBytes = proof.proofBytes,
            publicInputs = proof.publicInputs,
            verificationKey = ByteArray(0) // Mopro 从 zkey 中提取 vk，此参数预留
        )
        val verifyResult = zkpService.verify(verifyRequest)

        assertTrue(
            "验证应返回 Valid，实际: $verifyResult",
            verifyResult is ZkpVerifyResult.Valid
        )
    }
}
