package com.sovexis.ui.feature.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.sovexis.ui.components.SovexisScaffold
import com.sovexis.ui.navigation.SovexisRoute
import com.sovexis.ui.theme.SovexisPrimary

@Composable
fun AboutScreen(
    navController: NavHostController? = null
) {
    SovexisScaffold(
        accounts = emptyList(),
        activeDid = null,
        currentRoute = "about",
        onAccountSelected = { },
        onNavigate = { route ->
            navController?.navigate(route) {
                popUpTo(SovexisRoute.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
        onAddSubAccount = { },
        onStewardAccount = { },
        topBarTitle = "关于 Sovexis"
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Logo 区域
            Text(
                "Sovexis",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = SovexisPrimary
            )
            Text(
                "个人数字主权基座",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Android MVP v2.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 白皮书摘要
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "白皮书",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Sovexis 是一个个人数字主权基座系统，致力于让每一位用户在数字世界拥有完全的数据自主权。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "核心技术体系：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val features = listOf(
                        "did:self — 自注册去中心化身份（DID），基于 ECDSA P-256 密钥对，无需中心化注册机构",
                        "ZKP 选择性披露 — 零知识证明技术在保护凭证完整性的同时，实现最小化隐私披露",
                        "KDFS 图案认证 — 自定义 4×4 网格图案作为认知密钥，与生物认证构成双因素安全体系",
                        "TSS 阈值签名 — 2P-ECDSA 阈值签名协议，将私钥份额分散存储，防止单点泄露",
                        "Path ORAM 存储混淆 — 访问模式隐藏技术，防止通过 I/O 模式推断用户行为",
                        "Noise IK 加密通信 — 端到端加密传输协议，配合虚拟事件注入实现流量混淆"
                    )
                    features.forEach { feature ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text("•  ", style = MaterialTheme.typography.bodyMedium,
                                color = SovexisPrimary)
                            Text(feature, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "架构设计：陵谦 · UI 工程：Texno · 项目创始人：ringform",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 技术栈
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("技术栈", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Kotlin + Jetpack Compose + Material 3", style = MaterialTheme.typography.bodySmall)
                    Text("• Dagger Hilt DI · Room Database · Navigation Compose", style = MaterialTheme.typography.bodySmall)
                    Text("• Bouncy Castle (ECDSA) · BiometricPrompt (Keystore)", style = MaterialTheme.typography.bodySmall)
                    Text("• BnB TSS Library (GG20 2P-ECDSA) · Mopro (Groth16 ZKP)", style = MaterialTheme.typography.bodySmall)
                    Text("• EncryptedSharedPreferences (AES256-GCM)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
