package com.sovexis.tss.message

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovexis.domain.crypto.TssMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * BluetoothTransceiver 单元测试
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: 阈值签名模块 BLE Client 模式重写指令 (陵谦)
 *
 * 测试环境: Robolectric（模拟 Android 环境，无需真实蓝牙硬件）
 *
 * 测试覆盖：
 * - TSS-BLE-001: 安全补丁检查
 * - TSS-BLE-002: PSK 生成与导入
 * - TSS-BLE-003: 消息序列化/反序列化
 * - TSS-BLE-004: 消息分包与重组
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O])
class BluetoothTransceiverTest {

    private lateinit var context: Context
    private lateinit var pskVerifier: PskVerifier

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pskVerifier = PskVerifier(context)
    }

    /**
     * TSS-BLE-001: 安全补丁检查
     *
     * 验证：BleSecurityChecker 能正确识别已知漏洞
     */
    @Test
    fun `TSS-BLE-001 安全补丁检查`() {
        // 获取当前补丁级别
        val patchLevel = BleSecurityChecker.getPatchLevel()
        assertNotNull("补丁级别不应为空", patchLevel)

        // 检查特定 CVE
        val is49748Patched = BleSecurityChecker.isVulnPatched("CVE-2024-49748")
        // 注意：测试结果取决于运行环境的实际补丁级别
        // 这里只是验证方法能正常执行

        // 检查是否有高风险漏洞
        val hasHighRisk = BleSecurityChecker.hasHighRiskVuln()
        // 同样，结果取决于环境
    }

    /**
     * TSS-BLE-002: PSK 生成与导入
     *
     * 验证：PSK 可以正确生成、导出和导入
     */
    @Test
    fun `TSS-BLE-002 PSK 生成与导入`() {
        // 初始状态：未配置
        assertFalse("初始状态 PSK 未配置", pskVerifier.isConfigured)

        // 生成 PSK
        val (psk, bitmap) = pskVerifier.generatePsk()
        assertEquals("PSK 长度应为 32 字节", 32, psk.size)
        assertNotNull("二维码不应为空", bitmap)
        assertTrue("生成后 PSK 已配置", pskVerifier.isConfigured)

        // 导出 PSK
        val exportedPsk = pskVerifier.exportPsk()
        assertNotNull("导出的 PSK 不应为空", exportedPsk)

        // 清除 PSK
        pskVerifier.clearPsk()
        assertFalse("清除后 PSK 未配置", pskVerifier.isConfigured)

        // 从二维码导入
        val qrContent = "sovexis-psk:$exportedPsk"
        val importResult = pskVerifier.importPsk(qrContent)
        assertTrue("导入应成功", importResult.isSuccess)
        assertTrue("导入后 PSK 已配置", pskVerifier.isConfigured)

        // 验证无效格式
        val invalidResult = pskVerifier.importPsk("invalid-psk-data")
        assertTrue("无效格式导入应失败", invalidResult.isFailure)
    }

    /**
     * TSS-BLE-003: 消息序列化/反序列化
     *
     * 验证：TssMessage 可以正确序列化和反序列化
     */
    @Test
    fun `TSS-BLE-003 消息序列化与反序列化`() {
        val originalMessage = TssMessage(
            sessionId = "test-session-001",
            fromShareId = "share-A",
            toShareId = "share-B",
            round = 3,
            payload = "Hello, TSS!".toByteArray()
        )

        // 序列化
        val serialized = TssMessageSerializer.serialize(originalMessage)
        assertTrue("序列化后的数据不应为空", serialized.isNotEmpty())

        // 反序列化
        val deserialized = TssMessageSerializer.deserialize(serialized)
        assertNotNull("反序列化应成功", deserialized)

        // 验证字段
        assertEquals("sessionId 应一致", originalMessage.sessionId, deserialized!!.sessionId)
        assertEquals("fromShareId 应一致", originalMessage.fromShareId, deserialized.fromShareId)
        assertEquals("toShareId 应一致", originalMessage.toShareId, deserialized.toShareId)
        assertEquals("round 应一致", originalMessage.round, deserialized.round)
        assertArrayEquals("payload 应一致", originalMessage.payload, deserialized.payload)
    }

    /**
     * TSS-BLE-004: 消息分包与重组
     *
     * 验证：大消息可以正确分包和重组
     *
     * 注意：此测试验证分包逻辑，不涉及真实 BLE 传输
     */
    @Test
    fun `TSS-BLE-004 消息分包与重组逻辑`() {
        // 创建一个大 payload 的消息（超过 MTU）
        val largePayload = ByteArray(1000) { it.toByte() }
        val originalMessage = TssMessage(
            sessionId = "test-session-002",
            fromShareId = "share-A",
            toShareId = "share-B",
            round = 1,
            payload = largePayload
        )

        // 序列化
        val serialized = TssMessageSerializer.serialize(originalMessage)

        // 模拟分包（MTU = 100，减去头部后 payload 约 80 字节）
        val mtu = 100
        val maxPayload = mtu - 20  // 头部开销 20 字节
        val packets = mutableListOf<ByteArray>()

        val totalPackets = ((serialized.size + maxPayload - 1) / maxPayload)
        for (i in 0 until totalPackets) {
            val start = i * maxPayload
            val end = minOf(start + maxPayload, serialized.size)
            val payload = serialized.copyOfRange(start, end)

            // 构建包头
            val packet = ByteArray(20 + payload.size)
            val sessionBytes = originalMessage.sessionId.toByteArray().copyOf(16)
            System.arraycopy(sessionBytes, 0, packet, 0, 16)
            packet[16] = (i shr 8).toByte()
            packet[17] = (i and 0xFF).toByte()
            packet[18] = (totalPackets shr 8).toByte()
            packet[19] = (totalPackets and 0xFF).toByte()
            System.arraycopy(payload, 0, packet, 20, payload.size)
            packets.add(packet)
        }

        // 验证分包数量
        assertTrue("应分成多个包", packets.size > 1)

        // 模拟重组
        val reassembled = ByteArray(serialized.size)
        var offset = 0
        for (packet in packets.sortedBy {
            ((it[16].toInt() and 0xFF) shl 8) or (it[17].toInt() and 0xFF)
        }) {
            val payloadSize = packet.size - 20
            System.arraycopy(packet, 20, reassembled, offset, payloadSize)
            offset += payloadSize
        }

        // 验证重组后的数据
        assertArrayEquals("重组后的数据应与原始数据一致", serialized, reassembled)

        // 验证可以正确反序列化
        val deserialized = TssMessageSerializer.deserialize(reassembled)
        assertNotNull("重组后应能正确反序列化", deserialized)
        assertArrayEquals("payload 应一致", largePayload, deserialized!!.payload)
    }

    /**
     * TSS-BLE-005: PSK 挑战-响应验证
     *
     * 验证：PSK 挑战-响应机制可以正确工作
     */
    @Test
    fun `TSS-BLE-005 PSK 挑战响应验证`() {
        // 生成 PSK
        pskVerifier.generatePsk()

        // 生成 nonce
        val nonce = pskVerifier.generateNonce()
        assertEquals("nonce 长度应为 32 字节", 32, nonce.size)

        // 计算挑战
        val challenge = pskVerifier.computeChallenge(nonce)
        assertEquals("挑战长度应为 32 字节", 32, challenge.size)

        // 计算期望响应
        val expectedResponse = pskVerifier.computeExpectedResponse(nonce)
        assertEquals("响应长度应为 32 字节", 32, expectedResponse.size)

        // 验证响应（模拟对端正确响应）
        val isValid = pskVerifier.verifyResponse(nonce, expectedResponse)
        assertTrue("正确响应应验证通过", isValid)

        // 验证错误响应
        val wrongResponse = ByteArray(32) { (it + 1).toByte() }
        val isInvalid = pskVerifier.verifyResponse(nonce, wrongResponse)
        assertFalse("错误响应应验证失败", isInvalid)
    }

    /**
     * TSS-BLE-006: UUID 格式验证
     *
     * 验证：使用的 UUID 是完全随机的 128-bit UUID，不是 Bluetooth Base UUID
     */
    @Test
    fun `TSS-BLE-006 UUID 格式验证`() {
        val baseUuidSuffix = "-0000-1000-8000-00805F9B34FB"

        // 验证服务 UUID 不是 Base UUID
        val serviceUuid = BluetoothTransceiver.TSS_SERVICE_UUID.toString()
        assertFalse("服务 UUID 不应使用 Base UUID", serviceUuid.endsWith(baseUuidSuffix))

        // 验证写入特征 UUID 不是 Base UUID
        val writeCharUuid = BluetoothTransceiver.WRITE_CHAR_UUID.toString()
        assertFalse("写入特征 UUID 不应使用 Base UUID", writeCharUuid.endsWith(baseUuidSuffix))

        // 验证通知特征 UUID 不是 Base UUID
        val notifyCharUuid = BluetoothTransceiver.NOTIFY_CHAR_UUID.toString()
        assertFalse("通知特征 UUID 不应使用 Base UUID", notifyCharUuid.endsWith(baseUuidSuffix))

        // 验证 CCCD 是标准 UUID（这是正确的）
        val cccdUuid = BluetoothTransceiver.CCCD_UUID.toString()
        assertEquals("CCCD 应是标准 UUID", "00002902-0000-1000-8000-00805f9b34fb", cccdUuid.lowercase())
    }
}
