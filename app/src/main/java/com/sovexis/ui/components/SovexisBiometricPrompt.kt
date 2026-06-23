package com.sovexis.ui.components

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

/**
 * Sovexis 统一生物认证组件
 *
 * 封装 BiometricPrompt 的完整调用链：构建 → authenticate() → 成功/失败回调。
 * 内置 Crossfade 防抖：Crossfade 销毁并重建 composable 树时不会重新弹出认证对话框。
 *
 * @param cryptoObject       可选的 BiometricPrompt.CryptoObject
 * @param title              认证对话框标题
 * @param subtitle           认证对话框副标题
 * @param negativeButtonText 取消按钮文本
 * @param onSuccess          认证成功回调，返回签名字节数组
 * @param onFailed           认证失败回调，返回错误消息
 */
@Composable
fun SovexisBiometricPrompt(
    cryptoObject: BiometricPrompt.CryptoObject? = null,
    title: String = "验证身份",
    subtitle: String = "请使用指纹或面部识别",
    negativeButtonText: String = "取消",
    onSuccess: (ByteArray) -> Unit,
    onFailed: (String) -> Unit
) {
    val context = LocalContext.current
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnFailed by rememberUpdatedState(onFailed)

    // 启用 Crossfade 后，主题切换会销毁并重建整个 composable 树。
    // DisposableEffect(Unit) 在新旧实例中都触发 → 造成生物认证重复弹出。
    // 解决方案：LaunchedEffect + 150ms 延迟 + cancelled 标志。
    // Crossfade 在销毁旧树时取消协程 → 旧实例认证被跳过；
    // 新实例的 150ms 延迟足够让窗口焦点稳定，避免系统级冲突。
    LaunchedEffect(Unit) {
        delay(150) // Crossfade 过渡稳定期
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity == null) {
            currentOnFailed("生物认证不可用：宿主不是 FragmentActivity")
            return@LaunchedEffect
        }

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .build()

        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val biometricSignature = result.cryptoObject?.signature?.let { sig ->
                        sig.update("SovexisBiometricBinding".toByteArray(Charsets.UTF_8))
                        sig.sign()
                    } ?: ByteArray(32)
                    currentOnSuccess(biometricSignature)
                }

                override fun onAuthenticationFailed() { /* 系统控制重试 */ }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED ->
                            currentOnFailed("用户取消了生物认证")
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                            currentOnFailed("设备不支持生物认证")
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            currentOnFailed("生物认证已被锁定，请稍后重试")
                        else ->
                            currentOnFailed("生物认证失败: $errString (code=$errorCode)")
                    }
                }
            }
        )

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
