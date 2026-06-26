# 信用持久化方案分析 — 寄生三板斧

> texno · 2026-06-25  
> 状态：方案分析，未实施  
> 核心思想：不新增协议层，利用已有的三种 P2P 自然流量，信用数据"寄生"在其中

---

## 零、意外发现：P2P 已经在做了

读代码时发现一个意外的设计：

```go
// sync.go:209 — HandleTrustSync 对收到的每一个 peer 信任事件，都调了：
m.trustScorer.AddScore(event.NodeDid, event.Delta, event.EventType)
```

这一行意味着**每个 Peer 的本地 PebbleDB 里，已经存着全网节点的信用拷贝**——虽然是异步的、不保证完整的，但种子已经埋下了。Peer A 收到 Peer B 的 `STORE_SUCCESS` 事件后，在自己的 PebbleDB 里也更新了 Peer B 的 profile。

这被称为 **"信用反光"**：你的信用不仅存在于你自己的硬盘上，也映照在曾经和你交互过的每一个 Peer 的硬盘上。每个独立的 Peer 看到的都是同一个 CreditProfile 的**局部快照**，但这些快照合在一起就是完整的信用历史。

**这不是设计目标是代码副作用，但它恰好回答了老张的问题。**

现在沿着你的三个思路往下走。

---

## 一、Node 注册：网络入职即信用副本分发

### 现有机制

```
新 Node 启动
  → mDNS 广播 "_sovexis-node._tcp" (discovery/mdns.go)
  → 局域网内已有节点发现
  → PeerInfo 交换：Did / Address / PublicKey / CreditLevel / CreditStatus
```

`PeerInfo.CreditLevel` 已经在结构体里了——但只是一个 `int`，当前 level 的快照。

### 你的想法：入职时携带完整信用轨迹

当老张的 Node 在网络上注册时，它可以带上自己的完整 `CreditProfile`：

```go
// 扩展 PeerInfo
type PeerInfo struct {
    // ... 现有字段 ...
    CreditProfile  *CreditProfileJson  `json:"creditProfile,omitempty"`   // 完整信用档案
    CreditHistoryHash string           `json:"creditHistoryHash,omitempty"` // 历史链的 Merkle root
}
```

**不是"网络替你存"，是"你入职时顺手交给邻居一份副本"**。邻居验证后存入自己的 PebbleDB。

### 代价分析

| 维度 | 估算 |
|------|------|
| 单份 CreditProfile | ~2KB（JSON，含 score/level/experience/timestamps） |
| 100 个 peer 各存一份 | ~200KB per peer |
| 验证成本 | Ed25519 签名验证一次，<1ms |
| 网络成本 | mDNS TXT record 扩容 ~3KB（当前上限 ~4KB，勉强够） |

### 恢复路径

老张的 Node 没了 → 重装 Sovexis → 用 DID 发起 mDNS 广播 → 邻居 Peer 返回：  
1. 自己本地存的 CreditProfile  
2. 自己的 ScoreHistoryRecords 里与老张相关的条目

新 Node 收集 N 个 peer 的回复后，**取多数一致的 experience 和 history hash** 作为恢复基准。为什么不是信任任意一个 peer 的回复？因为单一 peer 可能没来得及收到最新事件——多数一致性是防"某个邻居掉线了几周"。

---

## 二、交易对手：每一份合约都是双向信用见证

### 现有机制

```
StorageContract {
    ConsumerDid: "did:sovexis:node:laozhang",
    ProviderDid: "did:sovexis:node:provider01",
    Status: "ACTIVE" / "COMPLETED" / "DISPUTED",
    CreatedAt / ExpiresAt,
    ProofSchedule: "daily",
}
```

合约天然是**双向存证**——消费者和提供者各自持有一份。合约履行的记录在合约数据中是**不可否认的**，因为双方都签了名。

### 你的想法：合约结束时，双方保留一份信用见证

当一个 `StorageContract` 变为 `COMPLETED`：

```
Consumer 端的 PebbleDB:                     Provider 端的 PebbleDB:
  contracts/                                contracts/
    {contractID}                            {contractID}
      → status: COMPLETED                   → status: COMPLETED
      → consumerDid: laozhang               → consumerDid: laozhang
      → providerDid: provider01             → providerDid: provider01
  信用记录(本地):                            信用记录(本地):
    laozhang: ScoreStoreSuccess +5          provider01: ScoreSLAFulfilled +15
```

现在只需要加一行：**合约完成时，双方在各自信用 history 里写一条"此合约的另一方信用良好"的锚定记录**。

```go
// storage/contract.go — 在 MarkCompleted 末尾
func (m *ContractManager) MarkCompleted(contractID string) error {
    // ... 现有逻辑 ...
    
    // 双向信用见证（新增）
    contract := m.load(contractID)
    m.trustScorer.AddScore(contract.ConsumerDid, 0, 
        fmt.Sprintf("contract_%s_fulfilled_by_%s", contractID, contract.ProviderDid))
    m.trustScorer.AddScore(contract.ProviderDid, 0,
        fmt.Sprintf("contract_%s_fulfilled_with_%s", contractID, contract.ConsumerDid))
    // 加 0 分——这一行不是加分，是"我证明这段历史真实存在"
}
```

这相当于：**每一次成功的存储交易，都在两个陌生人的信用链上刻下一行"我们有过一次诚实的合作"**。这一行的 value 是 0，但它的存在本身就是信用证据——比平台给你的 720 分重得多。

### 恢复路径

老张换了设备，向 P2P 网络广播"帮我恢复信用"。Provider01 收到请求后：

```
1. 在本地 PebbleDB 搜索 contracts/ 中 consumerDid = laozhang 的条目
2. 搜索 trust/history/laozhang/ 中的记录
3. 打包返回：{contracts: [...], creditEvents: [...]}
4. Ed25519 签名：Sign(provider_prv, hash(打包数据))
```

老张的新 Node 拿到 N 份这样的回复后，交叉验证：
- 不同 provider 的合约记录是否一致
- 信用事件的时间线是否吻合
- 不存在矛盾（如 provider01 说老张履约，provider02 说老张违约，而两个合约时间重叠）

---

## 三、迁移 + 仲裁 = 终极恢复

### 方案 B 扩展：MigrationPackage 携带信用

当前 `MigrationPackage`：

```kotlin
data class MigrationPackage(
    val did, val hdList, val salt, val commitments,
    val fingerConfigs, val authToken
)
```

➡️ 扩展：`val creditProfile: ByteArray? = null`，迁移时一并带上。

**使用场景**：老张换新手机，用迁移功能把 persona + 信用一起迁过去。**解决了 80% 的信用丢失场景**——大部分用户不是"设备损毁"，是"换新手机"。

### 方案 C：仲裁委员会复原（远期）

当 P2P 网络达到 7+ pillar 节点且仲裁不再是 503 占位后：

```
老张 → RequestCreditRecovery(did: laozhang, reason: "设备损毁")
  → 仲裁委员会检查:
      1. N 个 pillar 各自查询本地 PebbleDB 中 laozhang 的 CreditProfile
      2. 多数一致 → 签发 CreditRecoveryCredential
      3. 分歧 → 打开仲裁流程（检查历史链 Merkle root）
  → 老张用 RecoveryCredential 向任意 Peer 请求完整信用历史
  → Peer 验证 RecoveryCredential 的 Ed25519 签名 → 返回数据
```

这需要仲裁委员会不是 503（现在还是），但这层是在上面两层失败后的最终兜底。只要老张和至少一个交易对手有过合作，那个对手就能帮他——不需要整个网络。

---

## 四、三层总览

```
老张的设备
    │
    ├─ 设备正常 ──→ 信用存在自己的 PebbleDB 里
    │
    ├─ 换新手机 ──→ 方案 B：MigrationPackage 带信用一起迁
    │               （覆盖最大比例的用户场景）
    │
    ├─ 设备丢失，                                           
    │  网络正常 ──→ 方案 A：入职时向邻居请求副本               
    │               Peer 从本地 PebbleDB 取出老张的 profile    
    │                                                                 
    ├─ 设备丢失，                                           
    │  有交易史 ──→ 方案 A：交易对手 (=曾经的邻居) 提供合约见证 
    │               合约记录 + 信用 history 返回                
    │                                                                 
    └─ 全丢光了 ──→ 方案 B+C：迁移到新设备，用新设备发起仲裁恢复
                    仲裁委员会多数表决 → 签发复原凭证            
```

**三层不是替代关系，是递进兜底。** 每一层失败了，下一层顶上。

---

## 五、诚实评估

### 现在就能做的（零新协议）

| 改动 | 位置 | 代价 |
|------|------|------|
| ✅ `HandleTrustSync` 已在持久化远程 credit | `sync.go:209` | 零——已经做了 |
| ✅ `MigrationPackage` 加 `creditProfile` 字段 | `PersonhoodManager.kt` | 1 小时 |
| ✅ `PeerInfo` 加完整 `CreditProfile` | `types.go` | 30 分钟 |
| ✅ 合约完成时双向写信用见证 | `contract.go` | 1 小时 |
| ✅ 接收端持久化完整 score history | `sync.go` + `store.go` | 2 小时 |

### 等仲裁激活后才能做的

| 改动 | 依赖 |
|------|------|
| 🔴 仲裁委员会签发 CreditRecoveryCredential | `handler_arbitration.go` 不再 503 |
| 🔴 多节点多数表决恢复 | 7+ pillar 在线 |

### 老张的最终答案

> 老张的信用放在哪里？

**放在每一个他诚实地合作过的人那里。** 他的 Node 没了，但 provider01 记得他履约过；他的手机换了，但 MigrationPackage 带着信用一起走了；他的所有设备都丢了，但 P2P 网络里至少 5 个 peer 的 PebbleDB 里有他的 CreditProfile 碎片。

这不是云备份——因为没有"云"。这是**分布式互证**：你对网络做出的每一份贡献，网络都替你记住了。不需要中心化机构替你保管，因为每一个和你交互过的节点，都是你诚实行为的密码学证人。
