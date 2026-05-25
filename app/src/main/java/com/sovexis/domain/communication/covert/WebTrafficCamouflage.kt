package com.sovexis.domain.communication.covert

import java.security.SecureRandom

/**
 * Web 流量伪装器。
 *
 * 将 CovertTransport 的流量伪装为标准 Web 流量（HTTPS/TLS 1.3），
 * 使用 Chrome 浏览器的 TLS 密码套件顺序和 JA4 指纹。
 */
class WebTrafficCamouflage {
    private val random = SecureRandom()

    // Chrome 134 TLS 1.3 标准密码套件顺序（JA4 指纹匹配）
    private val chromeCipherSuites = listOf(
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256"
    )

    // Firefox 124 TLS 1.3 标准密码套件顺序
    private val firefoxCipherSuites = listOf(
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_AES_128_GCM_SHA256"
    )

    // 主流 CDN 域名池（用于 SNI 伪装）
    private val sniPool = listOf(
        "www.googleapis.com",
        "lh3.googleusercontent.com",
        "cdn.jsdelivr.net",
        "cloudflare.com",
        "ajax.googleapis.com",
        "fonts.googleapis.com",
        "cdnjs.cloudflare.com",
        "unpkg.com"
    )

    /**
     * 生成伪装 TLS ClientHello 的 JA4 指纹。
     *
     * @param browserType 浏览器类型（"chrome" 或 "firefox"）
     * @return JA4 指纹字符串
     */
    fun generateJA4Fingerprint(browserType: String = "chrome"): String {
        val suites = when (browserType) {
            "chrome" -> chromeCipherSuites.shuffled(random).take(3)
            "firefox" -> firefoxCipherSuites.shuffled(random).take(3)
            else -> chromeCipherSuites.shuffled(random).take(3)
        }
        return suites.joinToString("_")
    }

    /**
     * 随机选择 SNI 伪装域名。
     *
     * @return 随机选择的 CDN 域名
     */
    fun randomSniHost(): String {
        return sniPool[random.nextInt(sniPool.size)]
    }

    /**
     * 获取 TLS 版本。
     *
     * @return TLS 版本字符串
     */
    fun getTlsVersion(): String = "TLS_1.3"

    /**
     * 获取支持的浏览器类型列表。
     *
     * @return 浏览器类型列表
     */
    fun getSupportedBrowsers(): List<String> = listOf("chrome", "firefox")
}
