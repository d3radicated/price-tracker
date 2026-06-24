package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceTrackerDao {

    // --- Merchant Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchant(merchant: Merchant): Long

    @Query("SELECT * FROM merchants WHERE name = :name LIMIT 1")
    suspend fun getMerchantByName(name: String): Merchant?

    @Query("SELECT * FROM merchants ORDER BY name ASC")
    fun getAllMerchants(): Flow<List<Merchant>>


    // --- Product Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE name = :name AND unitValue = :unitValue AND unitType = :unitType LIMIT 1")
    suspend fun getProductByDetails(name: String, unitValue: Double?, unitType: String?): Product?

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE barcode IS NULL")
    suspend fun getProductsWithoutBarcode(): List<Product>


    // --- Receipt Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt): Long

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: Long)

    @Transaction
    @Query("""
        SELECT r.id, r.merchantId, m.name AS merchantName, r.purchaseDate, r.totalAmount
        FROM receipts r
        INNER JOIN merchants m ON r.merchantId = m.id
        ORDER BY r.purchaseDate DESC
    """)
    fun getAllReceiptsWithMerchant(): Flow<List<ReceiptWithMerchant>>


    // --- ReceiptItem Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItem(item: ReceiptItem): Long

    @Query("""
        SELECT ri.id, ri.receiptId, ri.productId, p.name AS productName, p.barcode AS productBarcode, p.unitValue, p.unitType, ri.price, ri.quantity
        FROM receipt_items ri
        INNER JOIN products p ON ri.productId = p.id
        WHERE ri.receiptId = :receiptId
    """)
    fun getReceiptItemsWithProduct(receiptId: Long): Flow<List<ReceiptItemWithProduct>>


    // --- Price Evolution/History ---
    @Query("""
        SELECT ri.price, r.purchaseDate, p.name as productName, p.unitValue, p.unitType, m.name as merchantName, r.id as receiptId
        FROM receipt_items ri
        INNER JOIN receipts r ON ri.receiptId = r.id
        INNER JOIN products p ON ri.productId = p.id
        INNER JOIN merchants m ON r.merchantId = m.id
        WHERE ri.productId = :productId
        ORDER BY r.purchaseDate DESC
    """)
    fun getPriceHistory(productId: Long): Flow<List<PriceHistoryEntry>>

    @Query("""
        SELECT ri.price, r.purchaseDate, p.name as productName, p.unitValue, p.unitType, m.name as merchantName, r.id as receiptId
        FROM receipt_items ri
        INNER JOIN receipts r ON ri.receiptId = r.id
        INNER JOIN products p ON ri.productId = p.id
        INNER JOIN merchants m ON r.merchantId = m.id
        ORDER BY r.purchaseDate DESC
    """)
    fun getAllPriceHistory(): Flow<List<PriceHistoryEntry>>
}

data class ReceiptWithMerchant(
    val id: Long,
    val merchantId: Long,
    val merchantName: String,
    val purchaseDate: Long,
    val totalAmount: Double
)

data class ReceiptItemWithProduct(
    val id: Long,
    val receiptId: Long,
    val productId: Long,
    val productName: String,
    val productBarcode: String?,
    val unitValue: Double?,
    val unitType: String?,
    val price: Double,
    val quantity: Int
)
