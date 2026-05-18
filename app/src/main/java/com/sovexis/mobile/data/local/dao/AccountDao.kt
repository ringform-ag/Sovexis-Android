package com.sovexis.mobile.data.local.dao

import androidx.room.*
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.AccountRole
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccountOnce(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE role = :role")
    fun getAccountsByRole(role: AccountRole): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE did = :did")
    suspend fun getAccountByDid(did: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE accounts SET isActive = 1 WHERE did = :did")
    suspend fun setActive(did: String)

    @Query("UPDATE accounts SET lastUsedAt = :timestamp WHERE did = :did")
    suspend fun updateLastUsed(did: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteAccount(account: AccountEntity)
}
