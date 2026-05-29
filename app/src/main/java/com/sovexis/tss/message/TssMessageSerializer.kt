package com.sovexis.tss.message

import com.sovexis.domain.crypto.TssMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import android.util.Base64

/**
 * TSS 消息序列化器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: 阈值签名模块 BLE Client 模式重写指令 (陵谦)
 *
 * 负责 TssMessage 与 ByteArray 之间的序列化/反序列化。
 * 使用 kotlinx.serialization JSON 格式，外层由 BLE 链路加密保护。
 *
 * 序列化格式：
 * ```json
 * {
 *   "version": 1,
 *   "sessionId": "...",
 *   "fromShareId": "...",
 *   "toShareId": "...",
 *   "round": 0,
 *   "payload": "base64-encoded-data"
 * }
 * ```
 *
 * 安全特性：
 * - version 字段保证向前兼容
 * - payload 使用 Base64 编码（JSON 不支持原始字节）
 * - 序列化后的数据由 BLE 链路层加密（LE Secure Connections）
 */
object TssMessageSerializer {

    /**
     * 当前序列化版本
     */
    const val CURRENT_VERSION = 1

    /**
     * JSON 配置
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 将 TssMessage 序列化为 ByteArray
     *
     * @param message TSS 消息
     * @return JSON 格式的字节数组
     */
    fun serialize(message: TssMessage): ByteArray {
        val dto = TssMessageDto(
            version = CURRENT_VERSION,
            sessionId = message.sessionId,
            fromShareId = message.fromShareId,
            toShareId = message.toShareId,
            round = message.round,
            payload = Base64.encodeToString(message.payload, Base64.NO_WRAP)
        )
        return json.encodeToString(dto).toByteArray(Charsets.UTF_8)
    }

    /**
     * 将 ByteArray 反序列化为 TssMessage
     *
     * @param data JSON 格式的字节数组
     * @return TssMessage 或 null（如果解析失败）
     */
    fun deserialize(data: ByteArray): TssMessage? {
        return try {
            val jsonStr = String(data, Charsets.UTF_8)
            val dto = json.decodeFromString<TssMessageDto>(jsonStr)

            // 版本检查（向前兼容：支持 version <= CURRENT_VERSION）
            if (dto.version > CURRENT_VERSION) {
                // 未来版本的消息，当前版本可能无法正确处理
                // 这里选择尝试解析，但记录警告
                android.util.Log.w("TssMessageSerializer", "接收到未来版本消息: ${dto.version}")
            }

            TssMessage(
                sessionId = dto.sessionId,
                fromShareId = dto.fromShareId,
                toShareId = dto.toShareId,
                round = dto.round,
                payload = Base64.decode(dto.payload, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            android.util.Log.e("TssMessageSerializer", "反序列化失败", e)
            null
        }
    }

    /**
     * 序列化 DTO（用于 JSON 序列化）
     */
    @Serializable
    private data class TssMessageDto(
        val version: Int,
        val sessionId: String,
        val fromShareId: String,
        val toShareId: String,
        val round: Int,
        val payload: String  // Base64 编码
    )
}
