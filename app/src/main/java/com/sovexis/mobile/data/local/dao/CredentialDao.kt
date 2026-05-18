package com.sovexis.mobile.data.local.dao

import androidx.room.*
import com.sovexis.mobile.data.local.entity.CredentialEntity
import com.sovexis.mobile.data.local.entity.CredentialStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials WHERE ownerDid = :ownerDid ORDER BY issuanceDate DESC")
    fun getCredentialsByOwner(ownerDid: String): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE credentialId = :credentialId")
    suspend fun getCredentialById(credentialId: String): CredentialEntity?

    @Query("SELECT * FROM credentials WHERE ownerDid = :ownerDid AND status = :status")
    fun getCredentialsByStatus(ownerDid: String, status: CredentialStatus): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE credentialType = :type AND ownerDid = :ownerDid")
    fun getCredentialsByType(ownerDid: String, type: String): Flow<List<CredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: CredentialEntity)

    @Update
    suspend fun updateCredential(credential: CredentialEntity)

    @Query("UPDATE credentials SET status = :status WHERE credentialId = :credentialId")
    suspend fun updateStatus(credentialId: String, status: CredentialStatus)

    @Delete
    suspend fun deleteCredential(credential: CredentialEntity)
}
