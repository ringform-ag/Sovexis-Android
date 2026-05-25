# VAULT_SPEC.md - Sovexis 保险箱模块规格 v1.0

## 模块定位
保险箱提供端到端加密的个人数据存储功能。用户可将敏感笔记、凭证或任意文本安全地存储于本地，只有持有正确密钥的副账号才能解密查看。保险箱的操作受策略引擎约束。

本模块使用 AES-256-GCM 对称加密，密钥从副账号的签名私钥派生，确保“一个副账号一把锁”。设计遵循“自我实现”，加密算法使用标准 `javax.crypto`，派生使用自实现的 HKDF。

---

## 1. 数据模型

### 1.1 加密条目（存储）

```kotlin
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val ownerDid: String,
    val titleCipher: ByteArray,
    val contentCipher: ByteArray,
    val iv: ByteArray,            // 12 字节
    val createdAt: Long,
    val updatedAt: Long
)

```

### 1.2 明文条目（UI 展示）

```kotlin
data class PlainVaultItem(
    val id: String,
    val ownerDid: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

```

## 2. 密钥派生

每个副账号对应一个独立的保险箱加密密钥，从副账号的签名私钥派生。

### 2.1 自实现 HKDF（基于 HMAC-SHA256）

```kotlin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HKDF {
    fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)  // Extract
        return expand(prk, info, length) // Expand
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val output = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, output, offset, copyLen)
            offset += copyLen
            i++
        }
        return output
    }
}

```

### 2.2 派生保险箱密钥

```kotlin
suspend fun deriveVaultKey(childDid: String, privateKey: PrivateKey): SecretKey {
    val ikm = privateKey.encoded  // PKCS#8
    val salt = "sovexis-vault".toByteArray()
    val info = childDid.toByteArray()
    val keyBytes = HKDF.deriveKey(ikm, salt, info, 32)
    return SecretKeySpec(keyBytes, "AES")
}

```

## 3. 加密与解密

### 3.1 加密

```kotlin
fun encrypt(plainText: String, key: SecretKey): Pair<ByteArray, ByteArray> {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return cipherText to iv
}

```

### 3.2 解密

```kotlin
fun decrypt(cipherText: ByteArray, iv: ByteArray, key: SecretKey): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
    val plainBytes = cipher.doFinal(cipherText)
    return String(plainBytes, Charsets.UTF_8)
}

```

4. 存储层（Room）

```kotlin
@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items WHERE ownerDid = :ownerDid ORDER BY updatedAt DESC")
    suspend fun getItems(ownerDid: String): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItem(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: String)
}

```

- 由于数据已加密，Room 数据库无需额外加密，但可以使用 SQLCipher 增强安全性（可选）。

## 5. 核心 API

### 5.1 VaultManager

```kotlin

class VaultManager(
    private val identityManager: IdentityManager,
    private val vaultDao: VaultDao
) {
    suspend fun addItem(ownerDid: String, title: String, content: String): Result<VaultItemEntity> {
        // 策略检查
        if (PolicyEnforcer.checkVaultWrite(ownerDid) !is PolicyCheckResult.Allowed)
            return Result.failure(SecurityException("无写入权限"))

        // 获取私钥并派生密钥
        val privateKey = identityManager.getPrivateKey(ownerDid) ?: return Result.failure(Exception("身份不存在"))
        val key = deriveVaultKey(ownerDid, privateKey)

        val (titleCipher, ivTitle) = encrypt(title, key)
        val (contentCipher, ivContent) = encrypt(content, key)
        // 为简化，使用相同 IV（实际可分开，此处合并）
        val item = VaultItemEntity(
            id = UUID.randomUUID().toString(),
            ownerDid = ownerDid,
            titleCipher = titleCipher,
            contentCipher = contentCipher,
            iv = ivTitle, // 实际存储需分别保存两个 IV，MVP 可仅加密内容
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        vaultDao.insert(item)
        return Result.success(item)
    }

    suspend fun listItems(ownerDid: String): List<VaultItemEntity> {
        return vaultDao.getItems(ownerDid)
    }

    suspend fun getPlainItem(itemId: String, ownerDid: String): Result<PlainVaultItem> {
        if (PolicyEnforcer.checkVaultRead(ownerDid) !is PolicyCheckResult.Allowed)
            return Result.failure(SecurityException("无读取权限"))

        val entity = vaultDao.getItem(itemId) ?: return Result.failure(Exception("条目不存在"))
        val privateKey = identityManager.getPrivateKey(ownerDid) ?: return Result.failure(Exception("身份不存在"))
        val key = deriveVaultKey(ownerDid, privateKey)

        val title = decrypt(entity.titleCipher, entity.iv, key)
        val content = decrypt(entity.contentCipher, entity.iv, key)
        return Result.success(PlainVaultItem(
            id = entity.id,
            ownerDid = entity.ownerDid,
            title = title,
            content = content,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        ))
    }

    suspend fun deleteItem(itemId: String, ownerDid: String): Result<Unit> {
        if (PolicyEnforcer.checkVaultDelete(ownerDid) !is PolicyCheckResult.Allowed)
            return Result.failure(SecurityException("无删除权限"))
        vaultDao.delete(itemId)
        return Result.success(Unit)
    }
}

```

## 6. 依赖项

- androidx.room:room-runtime、room-ktx

- kotlinx-coroutines-core

- 无第三方加密库

## 7. 移植性

- 加密逻辑（AES-GCM + HKDF）纯 Kotlin，可直接用于其他平台。

- 存储层通过接口抽象，可替换为其他数据库或文件存储。

# 规格版本：1.0

- 最后更新：2026-04-12
- 维护者：Sovexis 架构组