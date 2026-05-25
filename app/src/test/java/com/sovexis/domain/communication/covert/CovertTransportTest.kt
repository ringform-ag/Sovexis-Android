package com.sovexis.domain.communication.covert

import org.junit.Assert.*
import org.junit.Test

/**
 * CovertTransport 单元测试。
 *
 * 测试项目：
 * - 参数协商与序列化
 * - 协商失败策略链
 * - 用户级别配置
 * - 填充比例验证
 */
class CovertTransportTest {

    // ── ParameterNegotiator 测试 ──

    @Test
    fun `test parameter serialization and deserialization`() {
        val negotiator = ParameterNegotiator()
        val params = CovertParameters(
            version = 1,
            padding_ratio = 0.25,
            packet_size = 1024,
            camouflage_level = "firefox",
            fragmentation = true,
            injection_ratio = 0.3
        )

        val serialized = negotiator.serializeParameters(params)
        assertTrue("序列化结果不应为空", serialized.isNotEmpty())

        val deserialized = negotiator.deserializeParameters(serialized)
        assertNotNull("反序列化应成功", deserialized)
        assertEquals(params.version, deserialized!!.version)
        assertEquals(params.padding_ratio, deserialized.padding_ratio, 0.001)
        assertEquals(params.packet_size, deserialized.packet_size)
        assertEquals(params.camouflage_level, deserialized.camouflage_level)
        assertEquals(params.fragmentation, deserialized.fragmentation)
        assertEquals(params.injection_ratio, deserialized.injection_ratio, 0.001)
    }

    @Test
    fun `test default parameters by user level`() {
        val negotiator = ParameterNegotiator()

        val l0Params = negotiator.getDefaultParameters(0)
        assertEquals(0.1, l0Params.padding_ratio, 0.001)
        assertEquals(0.1, l0Params.injection_ratio, 0.001)

        val l1Params = negotiator.getDefaultParameters(1)
        assertEquals(0.2, l1Params.padding_ratio, 0.001)
        assertEquals(0.2, l1Params.injection_ratio, 0.001)

        val l2Params = negotiator.getDefaultParameters(2)
        assertEquals(0.3, l2Params.padding_ratio, 0.001)
        assertEquals(0.3, l2Params.injection_ratio, 0.001)
    }

    @Test
    fun `test conservative parameters`() {
        val negotiator = ParameterNegotiator()
        val conservative = negotiator.getConservativeParameters()

        assertEquals(0.3, conservative.padding_ratio, 0.001)
        assertEquals(512, conservative.packet_size)
        assertEquals("chrome", conservative.camouflage_level)
        assertTrue(conservative.fragmentation)
        assertEquals(0.3, conservative.injection_ratio, 0.001)
    }

    // ── NegotiationFallbackHandler 测试 ──

    @Test
    fun `test L0 fallback strategy chain`() {
        val handler = NegotiationFallbackHandler(0)
        val chain = handler.getStrategyChain()

        assertEquals(4, chain.size)
        assertEquals(FallbackStrategy.C, chain[0])
        assertEquals(FallbackStrategy.A, chain[1])
        assertEquals(FallbackStrategy.D, chain[2])
        assertEquals(FallbackStrategy.B, chain[3])
        assertFalse(handler.requiresDialog())
    }

    @Test
    fun `test L1 fallback strategy chain`() {
        val handler = NegotiationFallbackHandler(1)
        val chain = handler.getStrategyChain()

        assertEquals(3, chain.size)
        assertEquals(FallbackStrategy.A, chain[0])
        assertEquals(FallbackStrategy.D, chain[1])
        assertEquals(FallbackStrategy.B, chain[2])
        assertTrue(handler.requiresDialog())
    }

    @Test
    fun `test L2 fallback strategy chain`() {
        val handler = NegotiationFallbackHandler(2)
        val chain = handler.getStrategyChain()

        assertEquals(2, chain.size)
        assertEquals(FallbackStrategy.D, chain[0])
        assertEquals(FallbackStrategy.B, chain[1])
        assertTrue(handler.requiresDialog())
    }

    @Test
    fun `test timeout fallback by user level`() {
        val l0Handler = NegotiationFallbackHandler(0)
        val l1Handler = NegotiationFallbackHandler(1)
        val l2Handler = NegotiationFallbackHandler(2)

        assertEquals(FallbackStrategy.A, l0Handler.getTimeoutFallback())
        assertEquals(FallbackStrategy.A, l1Handler.getTimeoutFallback())
        assertEquals(FallbackStrategy.B, l2Handler.getTimeoutFallback())
    }

    @Test
    fun `test snackbar messages`() {
        val handler = NegotiationFallbackHandler(1)

        val messageA = handler.getSnackbarMessage(FallbackStrategy.A)
        assertNotNull(messageA)
        assertTrue(messageA!!.message.contains("安全通信模式"))

        val messageB = handler.getSnackbarMessage(FallbackStrategy.B)
        assertNotNull(messageB)
        assertTrue(messageB!!.message.contains("通信已终止"))
        assertTrue(messageB.showRetryButton)

        val messageC = handler.getSnackbarMessage(FallbackStrategy.C)
        assertNotNull(messageC)
        assertTrue(messageC!!.message.contains("安全风险"))

        val messageD = handler.getSnackbarMessage(FallbackStrategy.D)
        assertNull(messageD)
    }

    // ── PacketPadder 测试 ──

    @Test
    fun `test packet padding`() {
        val padder = PacketPadder(targetPacketSize = 512, paddingRatio = 0.2)
        val original = ByteArray(100) { it.toByte() }

        val padded = padder.pad(original)
        assertEquals(512, padded.size)

        // 验证原始数据保留
        for (i in original.indices) {
            assertEquals(original[i], padded[i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test padding too large message`() {
        val padder = PacketPadder(targetPacketSize = 512)
        val tooLarge = ByteArray(600) { 0x01 }
        padder.pad(tooLarge)
    }

    @Test
    fun `test padding ratio validation`() {
        // 有效比例
        PacketPadder(paddingRatio = 0.1)
        PacketPadder(paddingRatio = 0.2)
        PacketPadder(paddingRatio = 0.3)

        // 无效比例
        assertThrows(IllegalArgumentException::class.java) {
            PacketPadder(paddingRatio = 0.05)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PacketPadder(paddingRatio = 0.35)
        }
    }

    // ── VirtualEventInjector 测试 ──

    @Test
    fun `test default injection ratio by user level`() {
        assertEquals(0.1, VirtualEventInjector.getDefaultRatioForUserLevel(0), 0.001)
        assertEquals(0.2, VirtualEventInjector.getDefaultRatioForUserLevel(1), 0.001)
        assertEquals(0.3, VirtualEventInjector.getDefaultRatioForUserLevel(2), 0.001)
    }

    @Test
    fun `test max injection ratio by user level`() {
        assertEquals(0.1, VirtualEventInjector.getMaxRatioForUserLevel(0), 0.001)
        assertEquals(0.4, VirtualEventInjector.getMaxRatioForUserLevel(1), 0.001)
        assertEquals(0.5, VirtualEventInjector.getMaxRatioForUserLevel(2), 0.001)
    }

    @Test
    fun `test virtual DID generation`() {
        val injector = VirtualEventInjector()
        val did1 = injector.generateVirtualDid()
        val did2 = injector.generateVirtualDid()

        assertTrue(did1.startsWith("did:sovexis:virtual:"))
        assertTrue(did2.startsWith("did:sovexis:virtual:"))
        assertNotEquals(did1, did2)
    }

    @Test
    fun `test virtual payload generation`() {
        val injector = VirtualEventInjector()
        val payload = injector.generateVirtualPayload(256)

        assertEquals(256, payload.size)
    }

    // ── WebTrafficCamouflage 测试 ──

    @Test
    fun `test JA4 fingerprint generation`() {
        val camouflage = WebTrafficCamouflage()

        val chromeFingerprint = camouflage.generateJA4Fingerprint("chrome")
        assertNotNull(chromeFingerprint)
        assertTrue(chromeFingerprint.contains("TLS"))

        val firefoxFingerprint = camouflage.generateJA4Fingerprint("firefox")
        assertNotNull(firefoxFingerprint)
        assertTrue(firefoxFingerprint.contains("TLS"))
    }

    @Test
    fun `test random SNI host selection`() {
        val camouflage = WebTrafficCamouflage()
        val sni = camouflage.randomSniHost()

        assertNotNull(sni)
        assertTrue(sni.contains(".") || sni.contains("google"))
    }

    @Test
    fun `test TLS version`() {
        val camouflage = WebTrafficCamouflage()
        assertEquals("TLS_1.3", camouflage.getTlsVersion())
    }

    private fun <T : Throwable> assertThrows(expectedType: Class<T>, block: () -> Unit) {
        try {
            block()
            fail("Expected ${expectedType.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (!expectedType.isInstance(e)) {
                fail("Expected ${expectedType.simpleName} but got ${e.javaClass.simpleName}")
            }
        }
    }

    private fun fail(message: String) {
        throw AssertionError(message)
    }
}
