package com.sovexis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * å¯éªŒè¯å‡­è¯å®žä½? * å¯¹åº” Multipaz/Inji åº“çš„ VC æ•°æ®æ¨¡åž‹
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val credentialId: String,
    val ownerDid: String,
    val credentialType: String,
    val issuerDid: String,
    val issuanceDate: Long,
    val expirationDate: Long? = null,
    val credentialJson: String,       // VC JSON æ•°æ®
    val presentationJson: String? = null,  // VP JSON æ•°æ®
    val status: CredentialStatus = CredentialStatus.ACTIVE,
    val selectiveDisclosureFields: String? = null  // å¯é€‰æ‹©æ€§æŠ«éœ²çš„å­—æ®µåˆ—è¡¨ JSON
)

enum class CredentialStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
