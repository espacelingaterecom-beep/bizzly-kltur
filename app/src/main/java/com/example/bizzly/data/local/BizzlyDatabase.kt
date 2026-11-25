package com.example.bizzly.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "business_accounts")
data class BusinessAccountEntity(
    @PrimaryKey val id: String,
    val ident: String,
    val name: String,
    val jsonPayload: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_data")
data class BusinessDataEntity(
    @PrimaryKey val businessId: String,
    val jsonPayload: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_credentials")
data class SyncStateEntity(
    @PrimaryKey val businessId: String,
    val syncKey: String,
    val seq: Long,
    val snapJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface BizzlyDao {
    @Query("SELECT * FROM business_accounts ORDER BY updatedAt ASC")
    fun observeAccounts(): Flow<List<BusinessAccountEntity>>

    @Query("SELECT * FROM business_accounts ORDER BY updatedAt ASC")
    suspend fun getAllAccounts(): List<BusinessAccountEntity>

    @Query("SELECT * FROM business_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): BusinessAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: BusinessAccountEntity)

    @Query("DELETE FROM business_accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("SELECT * FROM business_data WHERE businessId = :businessId LIMIT 1")
    fun observeBusinessData(businessId: String): Flow<BusinessDataEntity?>

    @Query("SELECT * FROM business_data WHERE businessId = :businessId LIMIT 1")
    suspend fun getBusinessData(businessId: String): BusinessDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessData(data: BusinessDataEntity)

    @Query("DELETE FROM business_data WHERE businessId = :businessId")
    suspend fun deleteBusinessData(businessId: String)

    @Query("SELECT * FROM sync_credentials WHERE businessId = :businessId LIMIT 1")
    suspend fun getSyncState(businessId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_credentials")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_credentials WHERE businessId = :businessId")
    suspend fun deleteSyncState(businessId: String)
}

@Database(
    entities = [
        BusinessAccountEntity::class,
        BusinessDataEntity::class,
        SyncStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BizzlyDatabase : RoomDatabase() {
    abstract fun bizzlyDao(): BizzlyDao

    companion object {
        @Volatile
        private var instance: BizzlyDatabase? = null

        fun getInstance(context: Context): BizzlyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BizzlyDatabase::class.java,
                    "bizzly_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
