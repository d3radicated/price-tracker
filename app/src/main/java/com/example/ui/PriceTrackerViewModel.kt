package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.dao.ReceiptItemWithProduct
import com.example.data.db.PriceTrackerDatabase
import com.example.data.entity.Merchant
import com.example.data.entity.PriceHistoryEntry
import com.example.data.entity.Product
import com.example.data.repository.PriceTrackerRepository
import com.example.data.repository.ReviewItem
import com.example.data.dao.ReceiptWithMerchant
import com.example.util.ReceiptParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class AppScreen {
    DASHBOARD,
    SCANNER,
    REVIEW,
    HISTORY,
    SETTINGS
}

enum class ScanMode {
    RECEIPT,
    BARCODE
}

@OptIn(ExperimentalCoroutinesApi::class)
class PriceTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PriceTrackerDatabase.getDatabase(application)
    private val repository = PriceTrackerRepository(db.priceTrackerDao())

    // --- Persisted Dynamic Theme (Monet) Settings ---
    private val prefs = application.getSharedPreferences("price_tracker_prefs", android.content.Context.MODE_PRIVATE)
    private val _useDynamicColors = MutableStateFlow(prefs.getBoolean("use_dynamic_colors", true))
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()

    fun setUseDynamicColors(enabled: Boolean) {
        _useDynamicColors.value = enabled
        prefs.edit().putBoolean("use_dynamic_colors", enabled).apply()
    }

    // --- Navigation & Mode UI State ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.RECEIPT)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    // --- DB Data Flows ---
    val merchants: StateFlow<List<Merchant>> = repository.allMerchants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receipts: StateFlow<List<ReceiptWithMerchant>> = repository.allReceipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val priceHistory: StateFlow<List<PriceHistoryEntry>> = repository.allPriceHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- Active Scanner States ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()


    // --- Receipt Review Flow States ---
    private val _reviewMerchantName = MutableStateFlow("")
    val reviewMerchantName: StateFlow<String> = _reviewMerchantName.asStateFlow()

    private val _reviewPurchaseDate = MutableStateFlow(System.currentTimeMillis())
    val reviewPurchaseDate: StateFlow<Long> = _reviewPurchaseDate.asStateFlow()

    private val _reviewItems = MutableStateFlow<List<ReviewItem>>(emptyList())
    val reviewItems: StateFlow<List<ReviewItem>> = _reviewItems.asStateFlow()


    // --- Product Price History Drill-Down ---
    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

    val selectedProductHistory: StateFlow<List<PriceHistoryEntry>> = _selectedProduct
        .flatMapLatest { product ->
            if (product != null) {
                repository.getPriceHistoryForProduct(product.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- Backup & Settings Messages ---
    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()


    init {
        // Auto-inject demo data if database is empty on start to ensure delightful UX
        viewModelScope.launch {
            val existingReceipts = repository.allReceipts.firstOrNull() ?: emptyList()
            if (existingReceipts.isEmpty()) {
                repository.injectDemoData()
            }
        }
    }

    // --- Screen Control Navigation ---
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
    }

    fun selectProductForHistory(product: Product) {
        _selectedProduct.value = product
        navigateTo(AppScreen.HISTORY)
    }

    // --- Database Operations ---
    fun deleteReceipt(receiptId: Long) {
        viewModelScope.launch {
            repository.deleteReceipt(receiptId)
            _statusMessage.emit("Receipt deleted.")
        }
    }

    fun injectDemoData() {
        viewModelScope.launch {
            repository.injectDemoData()
            _statusMessage.emit("Demo data loaded.")
        }
    }


    // --- OCR & Barcode Scan Orchestration ---

    /**
     * Simulates scanning or accepts direct text from ML Kit OCR process.
     * Extracts values using regex, uses fuzzy matching on products, handles database unit fallbacks.
     */
    fun processOcrResult(merchantName: String, lines: List<String>) {
        viewModelScope.launch {
            _reviewMerchantName.value = merchantName.ifBlank { "Scanned Merchant" }
            _reviewPurchaseDate.value = System.currentTimeMillis()

            val allProductsList = repository.allProducts.firstOrNull() ?: emptyList()
            val parsedReviewItems = mutableListOf<ReviewItem>()

            lines.forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed
                
                // 1. Parse line with Regex
                val parsed = ReceiptParser.parseLineText(line)
                
                // 2. Perform fuzzy smart matching in database
                val matchedProduct = ReceiptParser.findSmartMatch(parsed.name, allProductsList)
                
                // 3. Fallback unit values from matched product if the scanned item lacks unit details
                val finalUnitValue = parsed.value ?: matchedProduct?.unitValue
                val finalUnitType = parsed.unit ?: matchedProduct?.unitType

                // Standard placeholder price extraction or safe random price generator to prevent zeroed fields
                val price = extractPriceFromLine(line)

                parsedReviewItems.add(
                    ReviewItem(
                        id = index.toLong() + 1, // temporary ID
                        name = matchedProduct?.name ?: parsed.name,
                        barcode = matchedProduct?.barcode,
                        price = price,
                        quantity = 1,
                        unitValue = finalUnitValue,
                        unitType = finalUnitType
                    )
                )
            }

            _reviewItems.value = parsedReviewItems
            navigateTo(AppScreen.REVIEW)
        }
    }

    /**
     * Processes barcode lookup result. If product is found, opens price history.
     * If not found, prompts to create or review a product with that barcode.
     */
    fun processBarcodeResult(barcode: String) {
        viewModelScope.launch {
            _isScanning.value = true
            val product = repository.getProductByBarcode(barcode)
            _isScanning.value = false

            if (product != null) {
                // Instantly view product history
                _selectedProduct.value = product
                _statusMessage.emit("Found barcode product: ${product.name}")
                navigateTo(AppScreen.HISTORY)
            } else {
                // Prompt user to add details for this barcode via review view
                _reviewMerchantName.value = "New Item Barcode Lookup"
                _reviewItems.value = listOf(
                    ReviewItem(
                        id = 1,
                        name = "New Barcode Item",
                        barcode = barcode,
                        price = 0.0,
                        quantity = 1,
                        unitValue = null,
                        unitType = null
                    )
                )
                _statusMessage.emit("New barcode scanned. Add product details.")
                navigateTo(AppScreen.REVIEW)
            }
        }
    }

    private fun extractPriceFromLine(line: String): Double {
        // Look for dollar values or simple numbers at the end
        val priceRegex = Regex("""\d+\.\d{2}\s*$""")
        val match = priceRegex.find(line)
        return match?.value?.toDoubleOrNull() ?: 2.99 // safe fallback
    }


    // --- Receipt Review Screen Editing Actions ---

    fun updateReviewMerchant(name: String) {
        _reviewMerchantName.value = name
    }

    fun updateReviewPurchaseDate(timestamp: Long) {
        _reviewPurchaseDate.value = timestamp
    }

    fun updateReviewItem(updatedItem: ReviewItem) {
        _reviewItems.value = _reviewItems.value.map { item ->
            if (item.id == updatedItem.id) updatedItem else item
        }
    }

    fun addReviewItem() {
        val current = _reviewItems.value
        val nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1
        _reviewItems.value = current + ReviewItem(
            id = nextId,
            name = "New Item",
            barcode = null,
            price = 1.0,
            quantity = 1,
            unitValue = null,
            unitType = null
        )
    }

    fun removeReviewItem(id: Long) {
        _reviewItems.value = _reviewItems.value.filter { it.id != id }
    }

    /**
     * Finalizes and saves the reviewed items as a structured SQLite receipt transaction.
     */
    fun saveReviewedReceipt() {
        viewModelScope.launch {
            if (_reviewItems.value.isEmpty()) {
                _statusMessage.emit("Review list is empty. Add at least one item.")
                return@launch
            }
            try {
                repository.saveReceipt(
                    merchantName = _reviewMerchantName.value,
                    purchaseDate = _reviewPurchaseDate.value,
                    items = _reviewItems.value
                )
                _statusMessage.emit("Receipt saved successfully!")
                navigateTo(AppScreen.DASHBOARD)
            } catch (e: Exception) {
                _statusMessage.emit("Error saving receipt: ${e.localizedMessage}")
            }
        }
    }


    // --- Local Backup Actions ---

    fun exportDatabase() {
        viewModelScope.launch {
            try {
                val json = repository.exportToJson()
                // Copy to clipboard or print state
                _statusMessage.emit("EXPORT_JSON_START\n$json\nEXPORT_JSON_END")
            } catch (e: Exception) {
                _statusMessage.emit("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun importDatabase(json: String) {
        viewModelScope.launch {
            try {
                val success = repository.importFromJson(json)
                if (success) {
                    _statusMessage.emit("Database imported successfully!")
                    navigateTo(AppScreen.DASHBOARD)
                } else {
                    _statusMessage.emit("Import failed. Invalid file structure.")
                }
            } catch (e: Exception) {
                _statusMessage.emit("Import failed: ${e.localizedMessage}")
            }
        }
    }
}
