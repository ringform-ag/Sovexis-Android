package com.sovexis.domain.zkp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Mopro 电路文件路径管理 (witnesscalc 方案)
 *
 * [AI-GENERATED] 2026-06-01 | witnesscalc adapter — .dat 已编入 .so，仅需 .zkey
 *
 * 文件来源：app/src/main/assets/circuit/
 * 运行时位置：context.filesDir/circuit/
 *
 * ## 电路文件
 * - multiplier2_wc_final.zkey: Groth16 证明/验证密钥
 */
object CircuitPathProvider {

    private const val TAG = "CircuitPathProvider"

    private const val ASSETS_CIRCUIT_DIR = "circuit"

    const val ZKEY_FILE = "multiplier2_wc_final.zkey"

    private var circuitDir: File? = null

    @Synchronized
    fun init(context: Context): File? {
        circuitDir?.let { return it }

        val dir = File(context.filesDir, ASSETS_CIRCUIT_DIR)
        if (!dir.exists()) dir.mkdirs()

        val zkeyFile = File(dir, ZKEY_FILE)
        if (zkeyFile.exists()) {
            circuitDir = dir
            Log.i(TAG, "电路文件已就绪: ${dir.absolutePath}")
            return dir
        }

        return try {
            copyAssetIfNeeded(context, ZKEY_FILE, zkeyFile)
            circuitDir = dir
            Log.i(TAG, "电路文件复制完成: ${dir.absolutePath}")
            dir
        } catch (e: Exception) {
            Log.e(TAG, "电路文件复制失败", e)
            null
        }
    }

    fun getZkeyPath(context: Context): String? {
        val dir = circuitDir ?: init(context) ?: return null
        return File(dir, ZKEY_FILE).absolutePath
    }

    fun isAvailable(context: Context): Boolean {
        val dir = circuitDir ?: init(context) ?: return false
        return File(dir, ZKEY_FILE).exists()
    }

    private fun copyAssetIfNeeded(context: Context, assetName: String, destFile: File) {
        if (destFile.exists()) return
        try {
            context.assets.open("$ASSETS_CIRCUIT_DIR/$assetName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "已复制: $assetName -> ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "复制 $assetName 失败: ${e.message}")
            throw e
        }
    }
}
