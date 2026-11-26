package com.example.bizzly.data.repository

import com.example.bizzly.data.local.InventoryDao
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.Product
import kotlinx.coroutines.flow.Flow

data class StockAlertEvent(
    val item: InventoryItemEntity,
    val becameOutOfStock: Boolean,
    val crossedBelowThreshold: Boolean
)

class InventoryRepository(
    private val dao: InventoryDao
) {
    fun observeItems(businessId: String): Flow<List<InventoryItemEntity>> =
        dao.observeByBusiness(businessId)

    fun observeLowStockItems(businessId: String): Flow<List<InventoryItemEntity>> =
        dao.observeLowStockByBusiness(businessId)

    suspend fun getItems(businessId: String): List<InventoryItemEntity> =
        dao.getByBusiness(businessId)

    suspend fun upsertItem(item: InventoryItemEntity): Long =
        dao.upsert(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun adjustQuantity(id: Long, delta: Double): InventoryItemEntity? {
        val current = dao.getById(id) ?: return null
        val updatedQty = (current.quantity + delta).coerceAtLeast(0.0)
        val now = System.currentTimeMillis()
        dao.updateQuantity(id, updatedQty, now)
        return current.copy(quantity = updatedQty, updatedAt = now)
    }

    suspend fun setQuantity(id: Long, newQuantity: Double): InventoryItemEntity? {
        val current = dao.getById(id) ?: return null
        val cleanQty = newQuantity.coerceAtLeast(0.0)
        val now = System.currentTimeMillis()
        dao.updateQuantity(id, cleanQty, now)
        return current.copy(quantity = cleanQty, updatedAt = now)
    }

    suspend fun deleteItem(id: Long) = dao.deleteById(id)

    suspend fun deleteAllForBusiness(businessId: String) = dao.deleteAllForBusiness(businessId)

    suspend fun seedFromCatalogIfNeeded(businessId: String, data: BusinessData): Int {
        if (data.prod.isEmpty()) return 0
        if (dao.countForBusiness(businessId) > 0) return 0
        return syncMissingFromCatalog(businessId, data)
    }

    suspend fun syncMissingFromCatalog(businessId: String, data: BusinessData): Int {
        if (data.prod.isEmpty()) return 0
        val existing = dao.getByBusiness(businessId)
        val existingProductIds = existing.mapNotNull { it.productId }.toSet()
        val existingNames = existing.map { it.name.trim().lowercase() }.toSet()

        val now = System.currentTimeMillis()
        val toInsert = data.prod.mapNotNull { prod ->
            val keyName = prod.n.trim().lowercase()
            if (prod.id in existingProductIds || keyName in existingNames) {
                null
            } else {
                val info = BizzlyCalculator.stockInfo(data, prod)
                InventoryItemEntity(
                    businessId = businessId,
                    productId = prod.id,
                    name = prod.n.trim(),
                    category = "Catalogue",
                    unit = "unité",
                    quantity = info.current.coerceAtLeast(0.0),
                    minThreshold = data.seuil.toDouble().coerceAtLeast(1.0),
                    unitPrice = prod.pv.coerceAtLeast(0.0),
                    updatedAt = now
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert)
        }
        return toInsert.size
    }

    /**
     * Déduit automatiquement les quantités vendues (delta > 0) ou réintègre en cas d'annulation (delta < 0),
     * en plafonnant toujours la quantité à 0.0 sans bloquer la vente.
     */
    suspend fun deductForSoldQuantities(
        businessId: String,
        products: List<Product>,
        soldDeltaByProductId: Map<Long, Double>
    ): List<StockAlertEvent> {
        if (soldDeltaByProductId.isEmpty()) return emptyList()
        val productMap = products.associateBy { it.id }
        val existing = dao.getByBusiness(businessId)
        if (existing.isEmpty()) return emptyList()

        val byPid = existing.filter { it.productId != null }.associateBy { it.productId!! }
        val byName = existing.associateBy { it.name.trim().lowercase() }
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<StockAlertEvent>()

        for ((pid, soldDelta) in soldDeltaByProductId) {
            if (soldDelta == 0.0) continue
            val prod = productMap[pid]
            val item = byPid[pid] ?: prod?.let { byName[it.n.trim().lowercase()] } ?: continue
            val nextQty = (item.quantity - soldDelta).coerceAtLeast(0.0)
            if (nextQty == item.quantity) continue
            dao.updateQuantity(item.id, nextQty, now)
            val updated = item.copy(quantity = nextQty, updatedAt = now)
            if (soldDelta > 0.0) {
                val becameOut = nextQty <= 0.0 && item.quantity > 0.0
                val crossedLow = nextQty < item.minThreshold && item.quantity >= item.minThreshold
                if (becameOut || crossedLow) {
                    alerts += StockAlertEvent(
                        item = updated,
                        becameOutOfStock = becameOut,
                        crossedBelowThreshold = crossedLow
                    )
                }
            }
        }
        return alerts
    }

    /**
     * Synchronisation Comptage Bar -> Inventaire Room.
     */
    suspend fun syncFromBarCounts(
        businessId: String,
        data: BusinessData
    ): Pair<Int, List<StockAlertEvent>> {
        if (data.prod.isEmpty()) return 0 to emptyList()
        val existing = dao.getByBusiness(businessId)
        val byPid = existing.filter { it.productId != null }.associateBy { it.productId!! }
        val byName = existing.associateBy { it.name.trim().lowercase() }
        val now = System.currentTimeMillis()
        val toInsert = mutableListOf<InventoryItemEntity>()
        val alerts = mutableListOf<StockAlertEvent>()
        var updatedCount = 0

        for (prod in data.prod) {
            val info = BizzlyCalculator.stockInfo(data, prod)
            val barQty = (prod.f ?: info.current).coerceAtLeast(0.0)
            val match = byPid[prod.id] ?: byName[prod.n.trim().lowercase()]
            if (match != null) {
                dao.updateQuantityAndPrice(match.id, barQty, prod.pv.coerceAtLeast(0.0), now)
                val updated = match.copy(
                    quantity = barQty,
                    unitPrice = prod.pv.coerceAtLeast(0.0),
                    updatedAt = now
                )
                updatedCount++
                val becameOut = barQty <= 0.0 && match.quantity > 0.0
                val crossedLow = barQty < match.minThreshold && match.quantity >= match.minThreshold
                if (becameOut || crossedLow) {
                    alerts += StockAlertEvent(updated, becameOut, crossedLow)
                }
            } else {
                toInsert += InventoryItemEntity(
                    businessId = businessId,
                    productId = prod.id,
                    name = prod.n.trim(),
                    category = "Catalogue",
                    unit = "unité",
                    quantity = barQty,
                    minThreshold = data.seuil.toDouble().coerceAtLeast(1.0),
                    unitPrice = prod.pv.coerceAtLeast(0.0),
                    updatedAt = now
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert)
        }
        return (updatedCount + toInsert.size) to alerts
    }

    /**
     * Retourne une map productId -> quantité actuelle en inventaire Room pour synchroniser vers Comptage Bar.
     */
    suspend fun getBarCountsFromInventory(
        businessId: String,
        data: BusinessData
    ): Map<Long, Double> {
        val existing = dao.getByBusiness(businessId)
        if (existing.isEmpty()) return emptyMap()
        val byPid = existing.filter { it.productId != null }.associateBy { it.productId!! }
        val byName = existing.associateBy { it.name.trim().lowercase() }
        val out = LinkedHashMap<Long, Double>()
        for (prod in data.prod) {
            val match = byPid[prod.id] ?: byName[prod.n.trim().lowercase()] ?: continue
            out[prod.id] = match.quantity.coerceAtLeast(0.0)
        }
        return out
    }
}
