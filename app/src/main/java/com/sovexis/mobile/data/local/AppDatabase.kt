package com.sovexis.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sovexis.mobile.data.local.dao.AccountDao
import com.sovexis.mobile.data.local.dao.CredentialDao
import com.sovexis.mobile.data.local.dao.SafeBoxDao
import com.sovexis.mobile.data.local.entity.AccountEntity
import com.sovexis.mobile.data.local.entity.CredentialEntity
import com.sovexis.mobile.data.local.entity.SafeBoxItemEntity

@Database(
    entities = [
        AccountEntity::class,
        CredentialEntity::class,
        SafeBoxItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun credentialDao(): CredentialDao
    abstract fun safeBoxDao(): SafeBoxDao
}
