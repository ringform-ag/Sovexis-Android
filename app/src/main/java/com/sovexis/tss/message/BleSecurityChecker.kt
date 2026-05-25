package com.sovexis.tss.message

import android.os.Build

/**
 * BLE 安全补丁检查器
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-20
 * 实现状态: ✅ 已完成
 * 参考文档: 阈值签名模块 BLE Client 模式重写指令 (陵谦)
 *
 * 在 BluetoothTransceiver 初始化时调用，检查当前设备的
 * Android 安全补丁级别是否覆盖已知蓝牙 RCE 漏洞。
 *
 * 参考源：
 * - Android Security Bulletin (https://source.android.com/docs/security/bulletin)
 * - NVD (https://nvd.nist.gov/)
 * - MITRE CVE (https://cve.mitre.org/)
 *
 * 已分析 CVE（2024-2025）：
 * - CVE-2024-49748: GATT Server 堆溢出，零点击 RCE (CVSS 9.8)
 * - CVE-2025-48539: 蓝牙栈 UAF，野外利用 (CVSS 8.0)
 * - CVE-2025-32876: Legacy Pairing TK 可推算
 * - CVE-2025-32875: 应用层未验证绑定状态
 * - CVE-2025-44557: BLE 芯片状态机绕过
 */
object BleSecurityChecker {

    /**
     * 已知蓝牙漏洞及其修复日期
     * 更新日期：2026-05-20
     */
    private data class BleVulnerability(
        val cveId: String,
        val cvssScore: Float,
        val fixedPatchLevel: String,  // 格式: "yyyy-MM-dd"
        val description: String
    )

    private val KNOWN_VULNS = listOf(
        BleVulnerability(
            "CVE-2024-49748", 9.8f, "2025-01-01",
            "GATT Server 堆缓冲区溢出，邻近网络零点击 RCE"
        ),
        BleVulnerability(
            "CVE-2025-48539", 8.0f, "2025-09-01",
            "蓝牙栈 Use-After-Free，已确认野外利用"
        ),
        BleVulnerability(
            "CVE-2025-0084",  7.5f, "2025-08-01",
            "蓝牙 HFP 协议 Use-After-Free，可远程代码执行"
        ),
        BleVulnerability(
            "CVE-2024-34722", 7.5f, "2024-07-01",
            "Legacy BLE Pairing 认证绕过（smp_proc_rand）"
        ),
        BleVulnerability(
            "CVE-2025-32876", 7.5f, "2025-06-01",
            "BLE Legacy Pairing TK 可推算，导致通信被嗅探"
        ),
        BleVulnerability(
            "CVE-2025-44557", 7.5f, "2025-10-01",
            "Cypress PSoC4 BLE 状态机绕过（芯片级）"
        )
    )

    /**
     * 获取安全警告信息。
     * @return 如果存在未修复漏洞，返回警告字符串；否则返回 null
     */
    fun getSecurityWarning(): String? {
        val patchLevel = Build.VERSION.SECURITY_PATCH  // 格式: "yyyy-MM-dd"
        val unpatched = KNOWN_VULNS.filter { patchLevel < it.fixedPatchLevel }

        return if (unpatched.isNotEmpty()) {
            buildString {
                appendLine("⚠️ 您的设备存在 ${unpatched.size} 个已知蓝牙安全漏洞")
                appendLine("当前安全补丁级别: $patchLevel")
                appendLine("最高 CVSS 评分: ${unpatched.maxOf { it.cvssScore }}")
                appendLine()
                appendLine("受影响漏洞:")
                unpatched.forEach { vuln ->
                    appendLine("  • ${vuln.cveId} (CVSS ${vuln.cvssScore}) - ${vuln.description}")
                }
                appendLine()
                appendLine("建议:")
                appendLine("  1. 检查系统更新并安装最新安全补丁")
                appendLine("  2. 仅在可信的物理环境中使用蓝牙签名功能")
                appendLine("  3. 启用 PSK 预绑定以增加应用层保护")
            }
        } else {
            null
        }
    }

    /**
     * 获取当前设备的安全补丁级别
     */
    fun getPatchLevel(): String = Build.VERSION.SECURITY_PATCH

    /**
     * 检查特定 CVE 是否已修复
     */
    fun isVulnPatched(cveId: String): Boolean {
        val patchLevel = Build.VERSION.SECURITY_PATCH
        val vuln = KNOWN_VULNS.firstOrNull { it.cveId == cveId }
        return vuln == null || patchLevel >= vuln.fixedPatchLevel
    }

    /**
     * 检查是否存在高风险未修复漏洞（CVSS >= 8.0）
     */
    fun hasHighRiskVuln(): Boolean {
        val patchLevel = Build.VERSION.SECURITY_PATCH
        return KNOWN_VULNS.any {
            it.cvssScore >= 8.0f && patchLevel < it.fixedPatchLevel
        }
    }
}
