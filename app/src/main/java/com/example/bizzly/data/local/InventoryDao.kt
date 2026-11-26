package com.example.bizzly.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class InventoryStockStatus {
    HEALTHY,
    LOW_STOCK,
    OUT_OF_STOCK
}

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["businessId"]),
        Index(value = ["businessId", "quantity"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val businessId: String,
    val productId: Long? = null,
    val name: String,
    val sku: String = "",
    val category: String = "",
    val unit: String = "unité",
    val quantity: Double = 0.0,
    val minThreshold: Double = 5.0,
    val unitPrice: Double = 0.0,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isBelowThreshold: Boolean
        get() = quantity < minThreshold

    fun stockStatus(): InventoryStockStatus = when {
        quantity <= 0.0 -> InventoryStockStatus.OUT_OF_STOCK
        quantity < minThreshold -> InventoryStockStatus.LOW_STOCK
        else -> InventoryStockStatus.HEALTHY
    }
}

@Dao
interface InventoryDao {
    @Query(
        """
        SELECT * FROM inventory_items
        WHERE businessId = :businessId
        ORDER BY CASE
            WHEN quantity <= 0 THEN 0
            WHEN quantity < minThreshold THEN 1
            ELSE 2
        END, name COLLATE NOCASE ASC
        """
    )
    fun observeByBusiness(businessId: String): Flow<List<InventoryItemEntity>>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE businessId = :businessId AND quantity < minThreshold
        ORDER BY quantity ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeLowStockByBusiness(businessId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE businessId = :businessId")
    suspend fun getByBusiness(businessId: String): List<InventoryItemEntity>

    @Query("SELECT COUNT(*) FROM inventory_items WHERE businessId = :businessId")
    suspend fun countForBusiness(businessId: String): Int

    @Query("SELECT * FROM inventory_items WHERE businessId = :businessId AND productId = :productId LIMIT 1")
    suspend fun getByProductId(businessId: String, productId: Long): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE businessId = :businessId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getByName(businessId: String, name: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Query("UPDATE inventory_items SET quantity = :newQty, updatedAt = :now WHERE id = :id")
    suspend fun updateQuantity(id: Long, newQty: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET quantity = :newQty, unitPrice = :unitPrice, updatedAt = :now WHERE id = :id")
    suspend fun updateQuantityAndPrice(id: Long, newQty: Double, unitPrice: Double, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM inventory_items WHERE businessId = :businessId")
    suspend fun deleteAllForBusiness(businessId: String)
}
