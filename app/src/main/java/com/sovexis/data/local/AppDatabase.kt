package com.sovexis.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sovexis.data.local.dao.AccountDao
import com.sovexis.data.local.dao.CredentialDao
import com.sovexis.data.local.dao.SafeBoxDao
import com.sovexis.data.local.entity.AccountEntity
import com.sovexis.data.local.entity.CredentialEntity
import com.sovexis.data.local.entity.SafeBoxItemEntity
import com.sovexis.domain.storage.VaultDao
import com.sovexis.domain.storage.VaultItemEntity

@Database(
    entities = [
        AccountEntity::class,
        CredentialEntity::class,
        SafeBoxItemEntity::class,
        VaultItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun credentialDao(): CredentialDao
    abstract fun safeBoxDao(): SafeBoxDao
    abstract fun vaultDao(): VaultDao
}
