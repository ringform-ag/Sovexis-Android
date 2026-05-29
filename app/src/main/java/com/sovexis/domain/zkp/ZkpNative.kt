/**
 * Sovexis ZKP Native - Kotlin JNI 接口
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: FRAMEWORK - 框架文件，待人工完善
 * 人工补充:
 *   - 具体业务参数的序列化格式定义
 *   - ProvingKey/VerifyingKey 的持久化存储方案
 *   - 线程安全审计（JNI 调用的线程模型）
 *
 * 本文件定义 Kotlin 端的 JNI native 方法声明。
 * 通过 System.loadLibrary("sovexis_zkp") 加载 Rust 编译的动态链接库。
 *
 * 对应的 Rust 实现: zkp-playground/src/jni_bridge.rs
 * JNI 函数命名规范:
 *   Java_<包名>_<类名>_<方法名>
 *   包名中的点号替换为下划线
 *
 * 使用方式:
 *   val (pkBytes, vkBytes) = ZkpNative.setup("bio_auth")
 *   val proofBytes = ZkpNative.prove(pkBytes, privateInputs, publicInputs)
 *   val isValid = ZkpNative.verify(vkBytes, proofBytes, publicInputs)
 *
 * [MANUAL-IMPLEMENTATION-REQUIRED]
 * 原因: 需要与 Rust 端的 JNI 函数签名保持同步
 * 预估工时: 2h
 * 技能要求: JNI、Kotlin、Rust FFI
 */
package com.sovexis.domain.zkp

import android.util.Log
import java.nio.charset.StandardCharsets

/**
 * Sovexis ZKP 原生接口
 *
 * 封装所有通过 JNI 调用 Rust 原生库的方法。
 * 使用 Kotlin object + companion object 模式实现单例加载。
 *
 * ## 线程安全
 * Rust 端使用 Mutex 保证全局状态（参数存储、证明存储）的线程安全。
 * Kotlin 端的 JNI 调用是线程安全的（JNIEnv 在每个线程独立获取）。
 *
 * ## 错误处理
 * Rust 端的错误通过 JNI 异常机制传递到 Kotlin 端。
 * 调用方应使用 try-catch 捕获可能的异常:
 *   - IllegalArgumentException: 参数格式错误
 *   - IOException: 序列化/反序列化错误
 *   - RuntimeException: 证明生成/验证失败
 *
 * ## 库加载
 * System.loadLibrary() 在 companion object init 块中调用，
 * 确保在首次访问 ZkpNative 时自动加载。
 * 如果库加载失败，将抛出 UnsatisfiedLinkError。
 */
object ZkpNative {

    private const val TAG = "ZkpNative"

    /**
     * 加载原生库
     *
     * System.loadLibrary("sovexis_zkp") 会查找以下路径:
     * - Android: /data/app/.../lib/arm64/libsovexis_zkp.so
     * - 宿主机: 由 java.library.path 指定
     *
     * 库文件应放置在:
     *   app/src/main/jniLibs/arm64-v8a/libsovexis_zkp.so
     *   app/src/main/jniLibs/armeabi-v7a/libsovexis_zkp.so
     */
    init {
        try {
            System.loadLibrary("sovexis_zkp")
            Log.i(TAG, "sovexis_zkp 原生库加载成功")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "sovexis_zkp 原生库加载失败", e)
            throw e
        }
    }

    // =====================================================================
    // Native 方法声明
    // =====================================================================

    /**
     * 可信设置 (Trusted Setup)
     *
     * 为指定电路类型生成 Groth16 参数（ProvingKey + VerifyingKey）。
     *
     * ## 安全警告
     * 此方法使用随机数生成器进行可信设置。
     * 生产环境应使用 MPC 仪式 (MPC Ceremony) 进行去中心化可信设置。
     *
     * ## 参数
     * @param circuitType 电路类型标识符
     *   - "bio_auth": 生物认证电路
     *   - "did_proof": DID 证明电路
     *   - "age_range": 年龄范围证明电路
     *
     * ## 返回值
     * @return Array<ByteArray> 包含两个元素:
     *   - [0]: ProvingKey 的序列化字节数组（必须保密！）
     *   - [1]: VerifyingKey 的序列化字节数组（可公开）
     *
     * ## 异常
     * @throws RuntimeException 如果可信设置失败
     * @throws IllegalArgumentException 如果 circuitType 无效
     *
     * ## 性能参考
     * - 简单电路: ~1-2 秒
     * - 复杂电路 (10k 约束): ~10-30 秒
     */
    external fun setup(circuitType: String): Array<ByteArray>

    /**
     * 生成零知识证明
     *
     * 使用证明密钥和输入参数生成 Groth16 零知识证明。
     *
     * ## 参数
     * @param pk ProvingKey 的序列化字节数组（由 setup() 返回的 [0]）
     * @param privateInputs 私有输入的序列化数据（JSON 格式的 UTF-8 字节数组）
     *   示例: {"secret_age": 25}
     * @param publicInputs 公共输入的序列化数据（JSON 格式的 UTF-8 字节数组）
     *   示例: {"min_age": 18}
     *
     * ## 返回值
     * @return ByteArray 序列化的 Groth16 证明（约 200 字节）
     *
     * ## 异常
     * @throws RuntimeException 如果证明生成失败
     * @throws IllegalArgumentException 如果输入参数格式错误
     * @throws IOException 如果序列化/反序列化失败
     *
     * ## 证明特性
     * - 零知识: 验证方无法获取私有输入的值
     * - 简洁: 证明体积固定 ~200 字节
     * - 非交互: 生成后无需与验证方交互
     */
    external fun prove(
        pk: ByteArray,
        privateInputs: ByteArray,
        publicInputs: ByteArray
    ): ByteArray

    /**
     * 验证零知识证明
     *
     * 使用验证密钥验证 Groth16 证明的正确性。
     *
     * ## 参数
     * @param vk VerifyingKey 的序列化字节数组（由 setup() 返回的 [1]）
     * @param proof 序列化的 Groth16 证明（由 prove() 返回）
     * @param publicInputs 公共输入的序列化数据（必须与 prove() 时一致）
     *
     * ## 返回值
     * @return Boolean true 表示证明有效，false 表示证明无效
     *
     * ## 异常
     * @throws RuntimeException 如果验证过程出错（非证明无效）
     * @throws IllegalArgumentException 如果输入参数格式错误
     *
     * ## 性能参考
     * - BN254: ~1-5 毫秒
     */
    external fun verify(
        vk: ByteArray,
        proof: ByteArray,
        publicInputs: ByteArray
    ): Boolean

    /**
     * 获取原生库版本信息
     *
     * @return String 版本信息字符串
     *   格式: "sovexis-zkp v0.1.0 | arkworks-rs 0.6.0 | BN254 curve"
     */
    external fun getLibVersion(): String

    /**
     * 清理指定参数
     *
     * 从 Rust 端全局存储中移除指定 ID 的参数和证明数据，释放内存。
     *
     * @param prepareResultId prepare 阶段返回的参数 ID
     */
    external fun cleanup(prepareResultId: String)

    // =====================================================================
    // Kotlin 友好的封装方法
    // =====================================================================

    /**
     * 可信设置 - Kotlin 友好封装
     *
     * 返回类型安全的数据类而非原始字节数组。
     *
     * @param circuitType 电路类型标识符
     * @return SetupResult 包含 ProvingKey 和 VerifyingKey
     * @throws ZkpNativeException 如果设置失败
     */
    fun setupTyped(circuitType: String): SetupResult {
        return try {
            val result = setup(circuitType)
            require(result.size == 2) { "setup() 应返回 2 个字节数组" }
            SetupResult(
                provingKey = result[0],
                verifyingKey = result[1]
            )
        } catch (e: Exception) {
            Log.e(TAG, "可信设置失败", e)
            throw ZkpNativeException("可信设置失败: ${e.message}", e)
        }
    }

    /**
     * 生成证明 - Kotlin 友好封装
     *
     * 接受 Map 参数并自动序列化为 JSON 字节数组。
     *
     * @param pk ProvingKey 字节数组
     * @param privateInputs 私有输入 Map
     * @param publicInputs 公共输入 Map
     * @return ByteArray 序列化的证明
     * @throws ZkpNativeException 如果证明生成失败
     */
    fun proveTyped(
        pk: ByteArray,
        privateInputs: Map<String, Any>,
        publicInputs: Map<String, Any>
    ): ByteArray {
        return try {
            val privateJson = serializeMapToJson(privateInputs)
            val publicJson = serializeMapToJson(publicInputs)
            prove(pk, privateJson, publicJson)
        } catch (e: ZkpNativeException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "证明生成失败", e)
            throw ZkpNativeException("证明生成失败: ${e.message}", e)
        }
    }

    /**
     * 验证证明 - Kotlin 友好封装
     *
     * @param vk VerifyingKey 字节数组
     * @param proof 证明字节数组
     * @param publicInputs 公共输入 Map
     * @return Boolean 验证结果
     * @throws ZkpNativeException 如果验证过程出错
     */
    fun verifyTyped(
        vk: ByteArray,
        proof: ByteArray,
        publicInputs: Map<String, Any>
    ): Boolean {
        return try {
            val publicJson = serializeMapToJson(publicInputs)
            verify(vk, proof, publicJson)
        } catch (e: ZkpNativeException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "证明验证失败", e)
            throw ZkpNativeException("证明验证失败: ${e.message}", e)
        }
    }

    // =====================================================================
    // 内部辅助方法
    // =====================================================================

    /**
     * 将 Map 序列化为 JSON 字节数组
     *
     * [MANUAL-IMPLEMENTATION-REQUIRED]
     * 当前使用简单的字符串拼接，生产环境应使用 Gson 或 kotlinx.serialization
     *
     * @param map 输入参数 Map
     * @return ByteArray JSON 格式的 UTF-8 字节数组
     */
    private fun serializeMapToJson(map: Map<String, Any>): ByteArray {
        // [MANUAL-IMPLEMENTATION-REQUIRED]
        // 替换为 Gson 或 kotlinx.serialization 的序列化
        //
        // Gson 示例:
        //   val gson = Gson()
        //   return gson.toJson(map).toByteArray(StandardCharsets.UTF_8)
        //
        // kotlinx.serialization 示例:
        //   val json = Json.encodeToString(map)
        //   return json.toByteArray(StandardCharsets.UTF_8)

        val sb = StringBuilder("{")
        map.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) sb.append(",")
            sb.append("\"$key\":")
            when (value) {
                is Number -> sb.append(value)
                is String -> sb.append("\"$value\"")
                is Boolean -> sb.append(value)
                else -> sb.append("\"$value\"")
            }
        }
        sb.append("}")
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    // =====================================================================
    // 数据类
    // =====================================================================

    /**
     * 可信设置结果
     *
     * @param provingKey Groth16 证明密钥（必须保密！）
     * @param verifyingKey Groth16 验证密钥（可公开）
     */
    data class SetupResult(
        val provingKey: ByteArray,
        val verifyingKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SetupResult) return false
            return provingKey.contentEquals(other.provingKey) &&
                    verifyingKey.contentEquals(other.verifyingKey)
        }

        override fun hashCode(): Int {
            var result = provingKey.contentHashCode()
            result = 31 * result + verifyingKey.contentHashCode()
            return result
        }
    }
}

/**
 * ZKP 原生操作异常
 *
 * 封装 JNI 层抛出的所有异常，提供统一的错误处理接口。
 *
 * @property message 错误描述
 * @property cause 原始异常（可能为 null）
 */
class ZkpNativeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
