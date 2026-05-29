# PAYMENT_SPEC.md - Sovexis 支付模块规格 v2.3.0

## 模块定位
支付模块负责交易构造、签名、验证，以及模拟账本管理。在 MVP 阶段，我们实现一个基于本地交易历史的模拟账本（MockLedger），同时预留适配器接口，为未来接入真实支付通道网络（如闪电网络）做好准备。

设计原则：所有支付操作必须经过策略引擎的限额检查，并需要用户生物认证（BiometricPrompt）授权签名。

---

## 1. 交易数据模型

### 1.1 未签名交易

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class UnsignedTransaction(
    val fromDid: String,
    val toDid: String,
    val amount: Double,
    val nonce: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val asset: String = "AGT"
)

```

### 1.2 已签名交易

```kotlin
@Serializable
data class SignedTransaction(
    val tx: UnsignedTransaction,
    val signature: ByteArray,
    val signerPublicKeyPem: String
) {
    val txId: String
        get() = computeTxId(tx)

    override fun equals(other: Any?): Boolean {
        // 因 ByteArray 需自定义
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SignedTransaction
        if (tx != other.tx) return false
        if (!signature.contentEquals(other.signature)) return false
        if (signerPublicKeyPem != other.signerPublicKeyPem) return false
        return true
    }

    override fun hashCode(): Int {
        var result = tx.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + signerPublicKeyPem.hashCode()
        return result
    }
}

fun computeTxId(tx: UnsignedTransaction): String {
    val json = canonicalJson.encodeToString(UnsignedTransaction.serializer(), tx)
    val hash = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}

```

### 1.3 交易状态

```kotlin
enum class TxStatus {
    PENDING, CONFIRMED, FAILED
}

data class TransactionRecord(
    val signedTx: SignedTransaction,
    val status: TxStatus,
    val confirmedAt: Long? = null
)

```

## 2. 模拟账本（MockLedger）

MockLedger 是一个本地单例，管理所有账户的余额和交易历史。它不进行任何网络通信，仅根据已确认的交易记录计算状态。

### 2.1 数据存储

交易记录以 JSON 数组形式存储在 EncryptedSharedPreferences 或 Room 数据库中。MVP 采用 EncryptedSharedPreferences，键为 tx_history_{did}。

```kotlin
class MockLedger(private val context: Context) {
    private val prefs: SharedPreferences = // ... EncryptedSharedPreferences

    private fun historyKey(did: String) = "tx_history_$did"
    private fun balanceKey(did: String) = "balance_$did"

    suspend fun submitTransaction(signedTx: SignedTransaction): Result<String> {
        // 1. 验证签名
        if (!verifySignature(signedTx)) return Result.failure(InvalidSignatureException())

        // 2. 检查 nonce 连续性
        val currentNonce = getNonce(signedTx.tx.fromDid)
        if (signedTx.tx.nonce != currentNonce + 1) return Result.failure(NonceException())

        // 3. 检查余额
        val balance = getBalance(signedTx.tx.fromDid)
        if (balance < signedTx.tx.amount) return Result.failure(InsufficientBalanceException())

        // 4. 保存交易并更新余额
        val record = TransactionRecord(signedTx, TxStatus.CONFIRMED, System.currentTimeMillis())
        addToHistory(signedTx.tx.fromDid, record)
        addToHistory(signedTx.tx.toDid, record)

        updateBalance(signedTx.tx.fromDid, balance - signedTx.tx.amount)
        val toBalance = getBalance(signedTx.tx.toDid)
        updateBalance(signedTx.tx.toDid, toBalance + signedTx.tx.amount)

        return Result.success(signedTx.txId)
    }

    suspend fun getBalance(did: String): Double {
        return prefs.getFloat(balanceKey(did), 1000.0f).toDouble() // 默认 1000 AGT
    }

    suspend fun getNonce(did: String): Long {
        val history = getHistory(did)
        return (history.maxOfOrNull { it.signedTx.tx.nonce } ?: 0L)
    }

    private suspend fun updateBalance(did: String, newBalance: Double) {
        prefs.edit().putFloat(balanceKey(did), newBalance.toFloat()).apply()
    }

    // 签名验证
    private fun verifySignature(signedTx: SignedTransaction): Boolean {
        val json = canonicalJson.encodeToString(UnsignedTransaction.serializer(), signedTx.tx)
        val hash = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
        val publicKey = getPublicKeyFromPem(signedTx.signerPublicKeyPem)
        val signature = signedTx.signature
        return Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(hash)
        }.verify(signature)
    }
}

```

### 2.2 规范化 JSON

为避免签名延展性，必须对交易对象按 key 字典序排序生成 JSON。使用 kotlinx.serialization 默认按字段声明顺序，但可通过配置 Json { prettyPrint = false; encodeDefaults = true } 并确保 UnsignedTransaction 字段顺序固定。

## 3. 签名与生物认证

### 3.1 签名流程

```kotlin
class PaymentManager(
    private val identityManager: IdentityManager,
    private val ledger: MockLedger,
    private val context: Context
) {
    suspend fun preparePayment(fromDid: String, toDid: String, amount: Double, note: String?): PrepareResult {
        // 1. 策略检查
        val policyCheck = PolicyEnforcer.checkPayment(fromDid, toDid, amount, "AGT")
        if (policyCheck !is PolicyCheckResult.Allowed) {
            return PrepareResult.Denied((policyCheck as PolicyCheckResult.Denied).reason, policyCheck.policyField)
        }

        // 2. 获取 nonce
        val nonce = ledger.getNonce(fromDid) + 1

        // 3. 构造未签名交易
        val unsigned = UnsignedTransaction(
            fromDid = fromDid,
            toDid = toDid,
            amount = amount,
            nonce = nonce,
            note = note
        )

        return PrepareResult.Ready(unsigned)
    }

    suspend fun signAndSubmit(unsignedTx: UnsignedTransaction): Result<SignedTransaction> {
        // 1. 触发生物认证并签名
        val signature = signWithBiometric(unsignedTx)

        // 2. 获取公钥
        val child = identityManager.getChildIdentity(unsignedTx.fromDid) ?: return Result.failure(Exception("身份不存在"))
        val publicKeyPem = child.publicKeyPem

        val signed = SignedTransaction(unsignedTx, signature, publicKeyPem)

        // 3. 提交到账本
        return ledger.submitTransaction(signed).map { signed }
    }

    private suspend fun signWithBiometric(unsignedTx: UnsignedTransaction): ByteArray {
        // 使用 BiometricPrompt 结合 CryptoObject
        // 实际实现需在 Activity/Fragment 中调用，此处简化
        val json = canonicalJson.encodeToString(UnsignedTransaction.serializer(), unsignedTx)
        val hash = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())

        // 从 Keystore 获取副账号签名密钥
        val privateKey = identityManager.getPrivateKey(unsignedTx.fromDid) // 内部处理 Keystore 加载
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(hash)
        }.sign()
        return signature
    }
}

sealed class PrepareResult {
    data class Ready(val unsignedTx: UnsignedTransaction) : PrepareResult()
    data class Denied(val reason: String, val policyField: String) : PrepareResult()
}

```

### 3.2 BiometricPrompt 集成要点

- 由于签名需要用户在场证明，必须绑定 BiometricPrompt.CryptoObject。在 Android 上，可通过在 Keystore 生成密钥时设置 setUserAuthenticationRequired(true)，这样每次使用该密钥都会强制要求生物认证。

## 4. 适配器接口（预留）

```kotlin
interface PaymentAdapter {
    val name: String
    suspend fun estimateFee(unsignedTx: UnsignedTransaction): Double
    suspend fun submit(signedTx: SignedTransaction): Result<String>
    suspend fun getBalance(did: String): Double
}

class MockLedgerAdapter(private val ledger: MockLedger) : PaymentAdapter {
    override val name = "MockLedger"
    override suspend fun estimateFee(unsignedTx: UnsignedTransaction) = 0.0
    override suspend fun submit(signedTx: SignedTransaction) = ledger.submitTransaction(signedTx)
    override suspend fun getBalance(did: String) = ledger.getBalance(did)
}

```

- 未来可通过 PaymentRouter 动态选择费用最低的适配器。

## 5. 依赖项

- androidx.biometric:biometric

- kotlinx-serialization-json

- androidx.security:security-crypto

## 6. 移植性

- 交易模型和签名逻辑（除 Android Biometric 部分）可移植。

- 适配器接口抽象了支付通道，便于未来扩展。

# 规格版本：1.0
- 最后更新：2026-04-12
- 维护者：Sovexis 架构组