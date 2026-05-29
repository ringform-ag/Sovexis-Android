package com.sovexis.ui.components

import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Sovexis 统一生物认证组件
 *
 * 封装 BiometricPrompt 的完整调用链：
 * 构建 → authenticate() → 成功/失败回调
 *
 * 支持 CryptoObject 参数，供签名场景使用（支付、凭证出示等）。
 *
 * @param cryptoObject    可选的 BiometricPrompt.CryptoObject，签名场景使用
 * @param title           认证对话框标题
 * @param subtitle        认证对话框副标题
 * @param negativeButtonText  取消按钮文本
 * @param onSuccess       认证成功回调，返回签名字节数组
 * @param onFailed        认证失败回调，返回错误消息
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

    DisposableEffect(context) {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity == null) {
            currentOnFailed("生物认证不可用：宿主不是 FragmentActivity")
            return@DisposableEffect onDispose { }
        }

        val executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .apply {
                // 允许设备凭据（PIN/图案）作为回退
                // 安全约束：Sovexis 仅使用生物认证，不启用设备凭据回退
                // setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }
            .build()

        val biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // 从 CryptoObject 提取生物绑定签名，传给上层 ViewModel
                    val biometricSignature = result.cryptoObject?.signature?.let { sig ->
                        sig.update("SovexisBiometricBinding".toByteArray(Charsets.UTF_8))
                        sig.sign()
                    } ?: ByteArray(32)
                    currentOnSuccess(biometricSignature)
                }

                override fun onAuthenticationFailed() {
                    // 指纹/面部已识别但未通过验证
                    // 此回调多次调用表明攻击可能正在发生
                    // 暂不处理——由系统控制重试次数
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            currentOnFailed("用户取消了生物认证")
                        }
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            currentOnFailed("设备不支持生物认证")
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            currentOnFailed("生物认证已被锁定，请稍后重试")
                        }
                        else -> {
                            currentOnFailed("生物认证失败: $errString (code=$errorCode)")
                        }
                    }
                }
            }
        )

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }

        onDispose {
            try {
                biometricPrompt.cancelAuthentication()
            } catch (_: Exception) {
                // 认证已完成或未启动
            }
        }
    }
}
