package com.sovexis.domain.zkp

import android.os.Build
import java.io.File

/**
 * Root 检测器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-21
 * 实现状态: ✅ 已完成
 * 参考文档: Sovexis · ZKP 模块完整实现指令 (陵谦)
 *
 * 检测方法（不依赖单一方法，综合判断）：
 * 1. 检查 su 二进制文件路径
 * 2. 检查 root 管理应用
 * 3. 检查系统构建标签
 *
 * 用途：为 ZKP 证明附加风险标签，触发免责声明弹窗。
 */
object RootDetector {
    fun isDeviceRooted(): Boolean {
        return checkSuBinaries() || checkRootManagers() || checkBuildTags()
    }

    private fun checkSuBinaries(): Boolean {
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/su/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/vendor/bin/su"
        )
        return suPaths.any { File(it).exists() }
    }

    private fun checkRootManagers(): Boolean {
        val managers = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "me.phh.superuser"
        )
        return managers.any { pkg ->
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", pkg))
                process.waitFor()
                process.inputStream.bufferedReader().readText().contains(pkg)
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkBuildTags(): Boolean {
        return Build.TAGS?.contains("test-keys") == true
    }
}
