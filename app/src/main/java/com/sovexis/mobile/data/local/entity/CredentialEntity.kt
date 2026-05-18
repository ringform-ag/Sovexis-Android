package com.sovexis.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 可验证凭证实�? * 对应 Multipaz/Inji 库的 VC 数据模型
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val credentialId: String,
    val ownerDid: String,
    val credentialType: String,
    val issuerDid: String,
    val issuanceDate: Long,
    val expirationDate: Long? = null,
    val credentialJson: String,       // VC JSON 数据
    val presentationJson: String? = null,  // VP JSON 数据
    val status: CredentialStatus = CredentialStatus.ACTIVE,
    val selectiveDisclosureFields: String? = null  // 可选择性披露的字段列表 JSON
)

enum class CredentialStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
