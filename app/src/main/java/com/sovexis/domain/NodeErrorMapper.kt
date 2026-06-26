package com.sovexis.domain

/**
 * 将 Node 端返回的原始错误信息映射为用户可理解的中文提示。
 * 覆盖 6 种最常见的通信/认证/支付/宪法错误场景。
 *
 * Usage:
 *   val friendly = NodeErrorMapper.translate(exception.message)
 *   或
 *   val friendly = NodeErrorMapper.translate(httpResponseBody)
 */
object NodeErrorMapper {

    /** 错误模式 → 对应的中文语义说明 */
    private val patterns = listOf(
        // 节点不可达 (timeout / connection refused / unreachable)
        Pattern(
            match = { s -> s.contains("timeout", ignoreCase = true) || s.contains("refused", ignoreCase = true)
                || s.contains("unreachable", ignoreCase = true) || s.contains("connect", ignoreCase = true) },
            friendly = "节点不可达，请确认节点已启动且与手机在同一网络。"
        ),
        // 凭证过期
        Pattern(
            match = { s -> s.contains("expired", ignoreCase = true) || s.contains("credential.*expire".toRegex()) },
            friendly = "凭证已过期，请重新授权后再试。"
        ),
        // 余额不足
        Pattern(
            match = { s -> s.contains("insufficient", ignoreCase = true) || s.contains("balance", ignoreCase = true) },
            friendly = "余额不足，无法完成此操作。"
        ),
        // 令牌已使用
        Pattern(
            match = { s -> s.contains("token.*consumed".toRegex()) || s.contains("already used", ignoreCase = true)
                || s.contains("single.use", ignoreCase = true) },
            friendly = "授权令牌已被使用，请重新获取授权。"
        ),
        // 宪法拦截
        Pattern(
            match = { s -> s.contains("constitution", ignoreCase = true) || s.contains("blocked", ignoreCase = true)
                || s.contains("硬性规则") || s.contains("硬性规则".toRegex()) },
            friendly = "操作被安全规则拦截，不符合宪法条款。"
        ),
        // 绑定不存在
        Pattern(
            match = { s -> s.contains("binding", ignoreCase = true) && (s.contains("not found", ignoreCase = true)
                || s.contains("missing", ignoreCase = true)) },
            friendly = "未找到绑定关系，请先绑定节点再操作。"
        ),
        // HTTP 5xx (兜底)
        Pattern(
            match = { s -> s.contains("500") || s.contains("502") || s.contains("503") },
            friendly = "节点内部错误，请稍后重试。"
        ),
        // HTTP 403 (兜底)
        Pattern(
            match = { s -> s.contains("403") },
            friendly = "权限不足，请确认此节点已绑定成功。"
        ),
    )

    /**
     * 翻译一条错误消息为中文。
     * 若未匹配到任何已知模式，返回原始消息（超过 60 字符时截断）。
     */
    fun translate(raw: String?): String {
        if (raw.isNullOrBlank()) return "操作失败，请重试。"
        for (p in patterns) {
            if (p.match(raw)) return p.friendly
        }
        // 未匹配 — 保留原始但限制长度
        return if (raw.length <= 60) raw else raw.take(60) + "…"
    }

    private data class Pattern(
        val match: (String) -> Boolean,
        val friendly: String
    )
}
