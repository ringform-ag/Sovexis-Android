package com.sovexis.domain.sync

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.sovexis.di.NodePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeSyncClient @Inject constructor(
    @NodePrefs private val nodePrefs: SharedPreferences
) {
    companion object {
        private const val TAG = "NodeSyncClient"
        private const val KEY_PAIRING_SEED = "pairing_seed"
        private const val KEY_NODE_IP = "node_ip"
        private const val KEY_NODE_PORT = "node_port"
        private const val TIMEOUT_MS = 30_000
    }

    data class SyncVaultItem(
        val id: String,
        val titleCipher: String,
        val contentCipher: String,
        val iv: String,
        val createdAt: Long,
        val modifiedAt: Long
    )

    data class SyncCredentialItem(
        val id: String,
        val type: String,
        val content: String,
        val issuedAt: Long
    )

    data class SyncStatus(
        val vaultCount: Int = 0,
        val credentialCount: Int = 0,
        val lastSyncAt: Long = 0,
        val vaultDigest: String = "",
        val credDigest: String = "",
        val conflictFiles: List<String> = emptyList()
    )

    data class ManifestEntry(
        val id: String,
        val sha256: String
    )

    private fun buildSyncHeaders(): Map<String, String> {
        val seedB64 = nodePrefs.getString(KEY_PAIRING_SEED, null) ?: return emptyMap()
        val seed = Base64.decode(seedB64, Base64.NO_WRAP)
        val ts = (System.currentTimeMillis() / 1000).toString()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        val token = mac.doFinal(ts.toByteArray()).joinToString("") { "%02x".format(it) }

        return mapOf(
            "X-Sync-Ts" to ts,
            "X-Sync-Token" to token,
            "Content-Type" to "application/json"
        )
    }

    private fun baseUrl(): String {
        val ip = nodePrefs.getString(KEY_NODE_IP, null) ?: return ""
        val port = nodePrefs.getInt(KEY_NODE_PORT, 8100)
        return "http://$ip:$port"
    }

    // ---- Vault Sync ----

    suspend fun uploadVaultItem(item: SyncVaultItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val headers = buildSyncHeaders()
            if (headers.isEmpty()) return@withContext Result.failure(Exception("pairing seed not configured"))

            val url = URL("${baseUrl()}/sync/vault/receive")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val body = JSONObject().apply {
                put("id", item.id)
                put("titleCipher", item.titleCipher)
                put("contentCipher", item.contentCipher)
                put("iv", item.iv)
                put("createdAt", item.createdAt)
                put("modifiedAt", item.modifiedAt)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            val response = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).readText()
            } else {
                BufferedReader(InputStreamReader(conn.errorStream)).readText()
            }

            if (code in 200..299) {
                Log.d(TAG, "Vault item synced: ${item.id}")
                Result.success(true)
            } else {
                Log.w(TAG, "Sync vault failed ($code): $response")
                Result.failure(Exception("sync failed ($code): $response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync vault error", e)
            Result.failure(e)
        }
    }

    suspend fun getVaultManifest(): Result<List<ManifestEntry>> = withContext(Dispatchers.IO) {
        try {
            val headers = buildSyncHeaders()
            if (headers.isEmpty()) return@withContext Result.failure(Exception("pairing seed not configured"))

            val url = URL("${baseUrl()}/sync/manifest/vault")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).readText()
            } else {
                return@withContext Result.failure(Exception("get manifest failed ($code)"))
            }

            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: JSONArray()
            val entries = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                ManifestEntry(
                    id = item.getString("id"),
                    sha256 = item.optString("sha256", "")
                )
            }
            Result.success(entries)
        } catch (e: Exception) {
            Log.e(TAG, "Get manifest error", e)
            Result.failure(e)
        }
    }

    suspend fun getSyncStatus(): Result<SyncStatus> = withContext(Dispatchers.IO) {
        try {
            val headers = buildSyncHeaders()
            if (headers.isEmpty()) return@withContext Result.failure(Exception("pairing seed not configured"))

            val url = URL("${baseUrl()}/sync/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).readText()
            } else {
                return@withContext Result.failure(Exception("get status failed ($code)"))
            }

            val json = JSONObject(body)
            Result.success(SyncStatus(
                vaultCount = json.optInt("vaultCount", 0),
                credentialCount = json.optInt("credentialCount", 0),
                lastSyncAt = json.optLong("lastSyncAt", 0),
                vaultDigest = json.optString("vaultDigest", ""),
                credDigest = json.optString("credDigest", "")
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Get status error", e)
            Result.failure(e)
        }
    }

    // ---- Credential Sync ----

    suspend fun uploadCredential(item: SyncCredentialItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val headers = buildSyncHeaders()
            if (headers.isEmpty()) return@withContext Result.failure(Exception("pairing seed not configured"))

            val url = URL("${baseUrl()}/sync/credentials/receive")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val body = JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("content", item.content)
                put("issuedAt", item.issuedAt)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                Log.d(TAG, "Credential synced: ${item.id}")
                Result.success(true)
            } else {
                val errBody = BufferedReader(InputStreamReader(conn.errorStream)).readText()
                Result.failure(Exception("sync failed ($code): $errBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync credential error", e)
            Result.failure(e)
        }
    }
}
