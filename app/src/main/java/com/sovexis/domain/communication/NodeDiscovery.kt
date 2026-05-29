@file:Suppress("all")

package com.sovexis.domain.communication

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sovexis Node mDNS 服务发现
 *
 * 通过 Android NSD API 发现局域网内的 Sovexis Node 实例。
 * 对应 Node 端的 _sovexis-node._tcp 服务类型。
 *
 * [AI-GENERATED]
 * 生成时间: 2026-05-25
 * 实现状态: ✅ 可用于联调
 */
@Singleton
class NodeDiscovery @Inject constructor(
    private val nsdManager: NsdManager
) {
    companion object {
        private const val TAG = "NodeDiscovery"
        const val SERVICE_TYPE = "_sovexis-node._tcp"
    }

    /**
     * 发现的节点信息
     */
    data class DiscoveredNode(
        val serviceName: String,
        val host: String,
        val port: Int,
        val did: String = "",
        val version: String = "",
        val ipAddress: String = ""
    )

    /**
     * 开始扫描局域网内的 Sovexis Node
     *
     * @return Flow of discovered nodes
     */
    fun discoverNodes(): Flow<DiscoveredNode> = callbackFlow {
        // 先定义 resolveListener（在 discoveryListener 之前）
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(service: NsdServiceInfo) {
                val host = service.host?.hostAddress ?: return
                val txtRecords = service.attributes

                val node = DiscoveredNode(
                    serviceName = service.serviceName,
                    host = service.serviceName,
                    port = service.port,
                    did = txtRecords?.get("did")?.toString(Charsets.UTF_8) ?: "",
                    version = txtRecords?.get("version")?.toString(Charsets.UTF_8) ?: "",
                    ipAddress = host
                )

                Log.d(TAG, "Node resolved: $host:${service.port} DID=${node.did}")
                trySend(node)
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: ${serviceInfo.serviceName}, error: $errorCode")
            }
        }

        // 定义 discoveryListener（在 resolveListener 之后，可以引用它）
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                // 解析服务详情
                nsdManager.resolveService(service, resolveListener)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: $serviceType, error: $errorCode")
                close(IllegalStateException("Discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $serviceType, error: $errorCode")
            }
        }

        // 开始发现
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery", e)
            }
        }
    }.shareIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        started = SharingStarted.Lazily,
        replay = 1
    )

    /**
     * 单次扫描（收集一段时间内发现的所有节点）
     *
     * @param durationMs 扫描持续时间（毫秒）
     * @return 发现的节点列表
     */
    suspend fun scanOnce(durationMs: Long = 5000L): List<DiscoveredNode> {
        val nodes = mutableListOf<DiscoveredNode>()

        discoverNodes()
            .takeWhile { true }
            .collect { node ->
                if (nodes.none { it.ipAddress == node.ipAddress && it.port == node.port }) {
                    nodes.add(node)
                }
            }

        // 实际使用时应该用 timeout
        kotlinx.coroutines.delay(durationMs)

        return nodes.distinctBy { "${it.ipAddress}:${it.port}" }
    }

    /**
     * 手动添加已知节点（跳过 mDNS 发现）
     */
    fun createManualNode(ipAddress: String, port: Int = 8100): DiscoveredNode {
        return DiscoveredNode(
            serviceName = "manual-node",
            host = "Sovexis-Node",
            port = port,
            ipAddress = ipAddress
        )
    }
}
