package com.sovexis.domain.zkp

import android.util.Log

/**
 * Mopro 原生库 JNI 桥接
 *
 * [AI-GENERATED]
 * 生成时间: 2026-06-01
 * 参考文档: Mopro 集成 · 补充指令 (ringform)
 *
 * 封装 Mopro UniFFI 生成的 JNI 调用。
 * Mopro Kotlin bindings 通过 UniFFI + JNA 生成，本文件提供类型安全的 Kotlin 封装。
 *
 * ## 原生库加载
 *
 * Mopro JitPack 包包含预编译的 .so 文件，通过 JNA 自动加载。
 * 如果没有通过 JitPack 获取，则需要:
 *   1. 将 libsovexis_zkp.so 放入 app/src/main/jniLibs/arm64-v8a/
 *   2. System.loadLibrary("sovexis_zkp") 加载
 *
 * ## API 说明
 *
 * - generateCircomProof(zkeyPath, circuitInputs, proofLib): String
 *   生成 Circom Groth16 证明，返回序列化的证明字符串
 *
 * - verifyCircomProof(zkeyPath, proof, proofLib): Boolean
 *   验证 Circom Groth16 证明
 *
 * - getVersion(): String
 *   返回 Mopro 原生库版本
 */
object MoproLib {

    private const val TAG = "MoproLib"

    /**
     * 证明库类型
     */
    enum class ProofLib(val value: Int) {
        ARKWORKS(0),   // arkworks-rs (Circom compat)
        RAPIDSNARK(1)  // rapidsnark
    }

    private var libLoaded = false

    /**
     * 加载 Mopro 原生库。
     * 线程安全：幂等加载，多次调用不会重复加载。
     */
    @Synchronized
    private fun ensureLoaded() {
        if (libLoaded) return
        try {
            System.loadLibrary("sovexis_zkp")
            libLoaded = true
            Log.i(TAG, "Mopro 原生库加载成功")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Mopro 原生库加载失败: ${e.message}")
            throw e
        }
    }

    /**
     * 生成 Circom Groth16 证明。
     *
     * @param zkeyPath .zkey 文件的绝对路径
     * @param circuitInputs JSON 格式的电路输入
     * @param proofLib 证明库类型（ARKWORKS 或 RAPIDSNARK）
     * @return 序列化的 Groth16 证明字符串
     */
    @JvmStatic
    fun generateCircomProof(
        zkeyPath: String,
        circuitInputs: String,
        proofLib: ProofLib
    ): String {
        ensureLoaded()
        return generateCircomProofNative(zkeyPath, circuitInputs, proofLib.value)
    }

    /**
     * 验证 Circom Groth16 证明。
     *
     * @param zkeyPath .zkey 文件的绝对路径
     * @param proof 由 generateCircomProof 返回的证明字符串
     * @param proofLib 证明库类型
     * @return true 如果证明有效
     */
    @JvmStatic
    fun verifyCircomProof(
        zkeyPath: String,
        proof: String,
        proofLib: ProofLib
    ): Boolean {
        ensureLoaded()
        return verifyCircomProofNative(zkeyPath, proof, proofLib.value)
    }

    /**
     * 获取 Mopro 原生库版本。
     *
     * @return 版本字符串，如 "mopro-ffi v0.3.6"
     */
    @JvmStatic
    fun getVersion(): String {
        ensureLoaded()
        return getVersionNative()
    }

    // ====== Native 声明（由 UniFFI JNA / JNI 桥接调用）
    // 注意: external 方法不能有函数体，实现位于原生 .so 库中

    @JvmStatic
    private external fun generateCircomProofNative(
        zkeyPath: String,
        circuitInputs: String,
        proofLib: Int
    ): String

    @JvmStatic
    private external fun verifyCircomProofNative(
        zkeyPath: String,
        proof: String,
        proofLib: Int
    ): Boolean

    @JvmStatic
    private external fun getVersionNative(): String
}
