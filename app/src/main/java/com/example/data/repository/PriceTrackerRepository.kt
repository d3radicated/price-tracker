package com.example.data.repository

import com.example.data.dao.PriceTrackerDao
import com.example.data.dao.ReceiptItemWithProduct
import com.example.data.dao.ReceiptWithMerchant
import com.example.data.entity.*
import com.example.util.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class PriceTrackerRepository(private val dao: PriceTrackerDao) {

    val allMerchants: Flow<List<Merchant>> = dao.getAllMerchants()
    val allProducts: Flow<List<Product>> = dao.getAllProducts()
    val allReceipts: Flow<List<ReceiptWithMerchant>> = dao.getAllReceiptsWithMerchant()
    val allPriceHistory: Flow<List<PriceHistoryEntry>> = dao.getAllPriceHistory()

    fun getReceiptItems(receiptId: Long): Flow<List<ReceiptItemWithProduct>> {
        return dao.getReceiptItemsWithProduct(receiptId)
    }

    fun getPriceHistoryForProduct(productId: Long): Flow<List<PriceHistoryEntry>> {
        return dao.getPriceHistory(productId)
    }

    suspend fun getProductByBarcode(barcode: String): Product? = withContext(Dispatchers.IO) {
        dao.getProductByBarcode(barcode)
    }

    /**
     * Saves a complete receipt along with all reviewed products and receipt items.
     * Integrates database smart matches and prevents duplicates.
     */
    suspend fun saveReceipt(
        merchantName: String,
        purchaseDate: Long,
        items: List<ReviewItem>
    ): Long = withContext(Dispatchers.IO) {
        // 1. Get or create Merchant
        val trimmedMerchant = merchantName.trim()
        var merchant = dao.getMerchantByName(trimmedMerchant)
        val merchantId = if (merchant != null) {
            merchant.id
        } else {
            dao.insertMerchant(Merchant(name = trimmedMerchant))
        }

        // 2. Insert receipt with temporary total
        val totalAmount = items.sumOf { it.price * it.quantity }
        val receiptId = dao.insertReceipt(
            Receipt(
                merchantId = merchantId,
                purchaseDate = purchaseDate,
                totalAmount = totalAmount
            )
        )

        // 3. Process products and items
        for (item in items) {
            var productId = 0L

            // Try barcode lookups first if present
            if (!item.barcode.isNullOrBlank()) {
                val existingByBarcode = dao.getProductByBarcode(item.barcode)
                if (existingByBarcode != null) {
                    productId = existingByBarcode.id
                    // Update units if they were blank but are now supplied
                    if (existingByBarcode.unitValue == null && item.unitValue != null) {
                        dao.updateProduct(
                            existingByBarcode.copy(
                                unitValue = item.unitValue,
                                unitType = item.unitType
                            )
                        )
                    }
                }
            }

            // If not found by barcode, try details or fuzzy matching name
            if (productId == 0L) {
                val existingList = dao.getAllProducts().firstOrNull() ?: emptyList()
                val matched = ReceiptParser.findSmartMatch(item.name, existingList)
                if (matched != null) {
                    productId = matched.id
                    
                    // Fallback database lookup: if unit is null, copy from previously saved item
                    val finalUnitValue = item.unitValue ?: matched.unitValue
                    val finalUnitType = item.unitType ?: matched.unitType
                    
                    // Update barcode or unit if newly available
                    val updatedBarcode = item.barcode ?: matched.barcode
                    if (updatedBarcode != matched.barcode || finalUnitValue != matched.unitValue || finalUnitType != matched.unitType) {
                        dao.updateProduct(
                            matched.copy(
                                barcode = updatedBarcode,
                                unitValue = finalUnitValue,
                                unitType = finalUnitType
                            )
                        )
                    }
                } else {
                    // Create new product
                    productId = dao.insertProduct(
                        Product(
                            barcode = item.barcode,
                            name = item.name.trim(),
                            unitValue = item.unitValue,
                            unitType = item.unitType
                        )
                    )
                }
            }

            // 4. Save Receipt Item
            dao.insertReceiptItem(
                ReceiptItem(
                    receiptId = receiptId,
                    productId = productId,
                    price = item.price,
                    quantity = item.quantity
                )
            )
        }

        receiptId
    }

    suspend fun deleteReceipt(receiptId: Long) = withContext(Dispatchers.IO) {
        dao.deleteReceiptById(receiptId)
    }

    // --- Backup & Restore Logic ---

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val merchants = dao.getAllMerchants().firstOrNull() ?: emptyList()
        val products = dao.getAllProducts().firstOrNull() ?: emptyList()
        val receipts = dao.getAllReceiptsWithMerchant().firstOrNull() ?: emptyList()
        
        // Fetch items for each receipt
        val allItems = mutableListOf<ReceiptItem>()
        val rawReceipts = receipts.map { r ->
            Receipt(id = r.id, merchantId = r.merchantId, purchaseDate = r.purchaseDate, totalAmount = r.totalAmount)
        }
        for (r in rawReceipts) {
            val dbItems = dao.getReceiptItemsWithProduct(r.id).firstOrNull() ?: emptyList()
            dbItems.forEach { item ->
                allItems.add(
                    ReceiptItem(
                        id = item.id,
                        receiptId = item.receiptId,
                        productId = item.productId,
                        price = item.price,
                        quantity = item.quantity
                    )
                )
            }
        }

        ReceiptParser.exportBackup(merchants, products, rawReceipts, allItems)
    }

    suspend fun importFromJson(json: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parsed = ReceiptParser.parseBackup(json)
            if (parsed.merchants.isEmpty() && parsed.products.isEmpty()) return@withContext false

            // Re-insert everything, mapping keys to avoid collisions if necessary
            // In a simple backup system, we can clear the database first or merge them. Let's merge!
            for (m in parsed.merchants) {
                dao.insertMerchant(m)
            }
            for (p in parsed.products) {
                val existing = if (p.barcode != null) dao.getProductByBarcode(p.barcode) else null
                if (existing == null) {
                    dao.insertProduct(p)
                }
            }
            for (r in parsed.receipts) {
                dao.insertReceipt(r)
            }
            for (item in parsed.items) {
                dao.insertReceiptItem(item)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Injects highly realistic dummy products and purchase history to provide an immediate immersive demo
     */
    suspend fun injectDemoData() = withContext(Dispatchers.IO) {
        val m1 = dao.insertMerchant(Merchant(name = "Costco - Seattle Downtown"))
        val m2 = dao.insertMerchant(Merchant(name = "Trader Joe's - Capitol Hill"))
        val m3 = dao.insertMerchant(Merchant(name = "Safeway - Broadway"))

        val p1 = dao.insertProduct(Product(name = "Organic Whole Milk", barcode = "074238321041", unitValue = 1.0, unitType = "L"))
        val p2 = dao.insertProduct(Product(name = "Premium Coffee Beans", barcode = "011110038472", unitValue = 500.0, unitType = "g"))
        val p3 = dao.insertProduct(Product(name = "Sparkling Water", barcode = "082938102391", unitValue = 12.0, unitType = "pcs"))
        val p4 = dao.insertProduct(Product(name = "Greek Yogurt", barcode = "041283023412", unitValue = 907.0, unitType = "g"))
        val p5 = dao.insertProduct(Product(name = "Coca-Cola Classic", barcode = "049000028913", unitValue = 1.5, unitType = "L"))

        // Create historic transactions showing price changes over the past 3 months
        val now = System.currentTimeMillis()
        val oneDay = 86400000L

        // Month 1
        val r1 = dao.insertReceipt(Receipt(merchantId = m1, purchaseDate = now - 90 * oneDay, totalAmount = 26.96))
        dao.insertReceiptItem(ReceiptItem(receiptId = r1, productId = p1, price = 3.49, quantity = 2))
        dao.insertReceiptItem(ReceiptItem(receiptId = r1, productId = p2, price = 12.99, quantity = 1))
        dao.insertReceiptItem(ReceiptItem(receiptId = r1, productId = p5, price = 2.49, quantity = 2))

        // Month 2
        val r2 = dao.insertReceipt(Receipt(merchantId = m2, purchaseDate = now - 45 * oneDay, totalAmount = 35.45))
        dao.insertReceiptItem(ReceiptItem(receiptId = r2, productId = p1, price = 3.69, quantity = 1))
        dao.insertReceiptItem(ReceiptItem(receiptId = r2, productId = p2, price = 13.49, quantity = 1))
        dao.insertReceiptItem(ReceiptItem(receiptId = r2, productId = p3, price = 5.99, quantity = 2))
        dao.insertReceiptItem(ReceiptItem(receiptId = r2, productId = p4, price = 6.29, quantity = 1))

        // Current Month
        val r3 = dao.insertReceipt(Receipt(merchantId = m3, purchaseDate = now - 5 * oneDay, totalAmount = 43.14))
        dao.insertReceiptItem(ReceiptItem(receiptId = r3, productId = p1, price = 3.99, quantity = 2))
        dao.insertReceiptItem(ReceiptItem(receiptId = r3, productId = p2, price = 13.99, quantity = 1))
        dao.insertReceiptItem(ReceiptItem(receiptId = r3, productId = p4, price = 6.79, quantity = 1))
        dao.insertReceiptItem(ReceiptItem(receiptId = r3, productId = p5, price = 2.89, quantity = 2))
    }
}

/**
 * Representation of a product item inside a newly scanned or manually reviewed receipt
 */
data class ReviewItem(
    val id: Long = 0, // temp UI identification
    val name: String,
    val barcode: String?,
    val price: Double,
    val quantity: Int,
    val unitValue: Double?,
    val unitType: String?
)
