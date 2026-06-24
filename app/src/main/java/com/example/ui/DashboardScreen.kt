package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.dao.ReceiptWithMerchant
import com.example.data.entity.Product
import com.example.util.ReceiptParser
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: PriceTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val receipts by viewModel.receipts.collectAsState()
    val products by viewModel.products.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val merchants by viewModel.merchants.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showManualEntryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // We use a custom inline header inside the Column for a premium, custom styled "Professional Polish" feel
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Manual Price Entry secondary FAB
                FloatingActionButton(
                    onClick = { showManualEntryDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("add_manual_fab"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Add Price Manually")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Manually", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                // Primary Scan receipt FAB
                FloatingActionButton(
                    onClick = {
                        viewModel.setScanMode(ScanMode.RECEIPT)
                        viewModel.navigateTo(AppScreen.SCANNER)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_receipt_fab"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan receipt", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Premium Custom Polish Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "PRICE INSIGHT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }

                    // Circular settings badge matching the Professional Polish header layout
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { viewModel.navigateTo(AppScreen.SETTINGS) }
                            .testTag("settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                divider = { } // Clean borderless tabs
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Receipts", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("receipts_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Products", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("products_tab")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // Receipts List
                if (receipts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No scanned receipts yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap 'Scan receipt' to begin scanning.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(receipts, key = { it.id }) { receipt ->
                            ReceiptCard(
                                receipt = receipt,
                                onDelete = { viewModel.deleteReceipt(receipt.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) } // margin for FAB
                    }
                }
            } else {
                // Products List
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("product_search_input"),
                        placeholder = { Text("Search products or scan barcode...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.setScanMode(ScanMode.BARCODE)
                                    viewModel.navigateTo(AppScreen.SCANNER)
                                },
                                modifier = Modifier.testTag("barcode_shortcut_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredProducts = remember(products, searchQuery) {
                        products.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    (it.barcode ?: "").contains(searchQuery)
                        }
                    }

                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No products found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val productHistory = priceHistory.filter { it.productName.equals(product.name, ignoreCase = true) }
                                val latestPrice = productHistory.maxByOrNull { it.purchaseDate }?.price ?: 0.0
                                
                                ProductCard(
                                    product = product,
                                    latestPrice = latestPrice,
                                    onClick = { viewModel.selectProductForHistory(product) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) } // margin for FAB
                        }
                    }
                }
            }
        }
    }

    if (showManualEntryDialog) {
        ManualPriceEntryDialog(
            onDismissRequest = { showManualEntryDialog = false },
            onSave = { store, prod, price, bar, uVal, uType, date ->
                viewModel.saveManualPrice(store, prod, price, bar, uVal, uType, date)
                showManualEntryDialog = false
            },
            merchants = merchants,
            products = products
        )
    }
}

@Composable
fun ReceiptCard(
    receipt: ReceiptWithMerchant,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_card_${receipt.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        receipt.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        dateFormat.format(Date(receipt.purchaseDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(Locale.US, "$%.2f", receipt.totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_receipt_${receipt.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Receipt",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Receipt?") },
            text = { Text("This will permanently remove this receipt and its price entries from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProductCard(
    product: Product,
    latestPrice: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_card_${product.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Unit Display tag
                    val unitString = if (product.unitValue != null && product.unitType != null) {
                        "${product.unitValue} ${product.unitType}"
                    } else {
                        "Unitless"
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            unitString,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (!product.barcode.isNullOrBlank()) {
                        Text(
                            "UPC: ${product.barcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Normalized Unit price
                if (product.unitValue != null && product.unitType != null && latestPrice > 0) {
                    val normStr = ReceiptParser.getNormalizedPriceString(latestPrice, product.unitValue, product.unitType)
                    Text(
                        "Normalized: $normStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (latestPrice > 0.0) String.format(Locale.US, "$%.2f", latestPrice) else "N/A",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Latest Price",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPriceEntryDialog(
    onDismissRequest: () -> Unit,
    onSave: (
        merchantName: String,
        productName: String,
        price: Double,
        barcode: String?,
        unitValue: Double?,
        unitType: String?,
        purchaseDate: Long
    ) -> Unit,
    merchants: List<com.example.data.entity.Merchant>,
    products: List<com.example.data.entity.Product>
) {
    var storeName by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var priceString by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var unitValueString by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("") }
    var dateSelected by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }

    // Store autocomplete list
    val storeSuggestions = remember(storeName, merchants) {
        if (storeName.isBlank()) emptyList()
        else merchants.filter {
            it.name.contains(storeName, ignoreCase = true) && 
            !it.name.equals(storeName, ignoreCase = true)
        }.take(3)
    }

    // Product autocomplete list
    val productSuggestions = remember(productName, products) {
        if (productName.isBlank()) emptyList()
        else products.filter {
            it.name.contains(productName, ignoreCase = true) && 
            !it.name.equals(productName, ignoreCase = true)
        }.take(3)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Record Price Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Store Name Field
                Column {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { 
                            storeName = it
                            validationError = null
                        },
                        label = { Text("Store / Merchant Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("manual_store_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) }
                    )
                    if (storeSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            storeSuggestions.forEach { merchant ->
                                SuggestionChip(
                                    onClick = { storeName = merchant.name },
                                    label = { Text(merchant.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }

                // Product Name Field
                Column {
                    OutlinedTextField(
                        value = productName,
                        onValueChange = { 
                            productName = it
                            validationError = null
                        },
                        label = { Text("Product Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("manual_product_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) }
                    )
                    if (productSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            productSuggestions.forEach { product ->
                                SuggestionChip(
                                    onClick = { 
                                        productName = product.name
                                        // Auto-fill existing details!
                                        if (!product.barcode.isNullOrBlank()) barcode = product.barcode
                                        if (product.unitValue != null) unitValueString = product.unitValue.toString()
                                        if (!product.unitType.isNullOrBlank()) unitType = product.unitType
                                    },
                                    label = { Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }

                // Price and Optional Barcode Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priceString,
                        onValueChange = { 
                            priceString = it
                            validationError = null
                        },
                        label = { Text("Price ($) *") },
                        modifier = Modifier.weight(1f).testTag("manual_price_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.weight(1.2f).testTag("manual_barcode_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) }
                    )
                }

                // Unit inputs
                Text(
                    text = "Package Unit Details (Optional)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = unitValueString,
                        onValueChange = { unitValueString = it },
                        label = { Text("Unit Value") },
                        placeholder = { Text("e.g. 500") },
                        modifier = Modifier.weight(1f).testTag("manual_unit_value_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )

                    OutlinedTextField(
                        value = unitType,
                        onValueChange = { unitType = it },
                        label = { Text("Unit Type") },
                        placeholder = { Text("e.g. g, L") },
                        modifier = Modifier.weight(1f).testTag("manual_unit_type_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Quick unit suggestions chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("g", "kg", "L", "ml", "pcs").forEach { unit ->
                        FilterChip(
                            selected = unitType == unit,
                            onClick = { unitType = unit },
                            label = { Text(unit) }
                        )
                    }
                }

                // Date Selection Button
                Surface(
                    onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = dateSelected }
                        val datePickerDialog = android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                dateSelected = newCal.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )
                        datePickerDialog.show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Date: ${dateFormat.format(Date(dateSelected))}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (storeName.isBlank()) {
                        validationError = "Store / Merchant Name is required."
                        return@Button
                    }
                    if (productName.isBlank()) {
                        validationError = "Product Name is required."
                        return@Button
                    }
                    val price = priceString.toDoubleOrNull()
                    if (price == null || price <= 0) {
                        validationError = "Please enter a valid positive price."
                        return@Button
                    }
                    val unitValue = unitValueString.toDoubleOrNull()
                    if (unitValueString.isNotBlank() && (unitValue == null || unitValue <= 0)) {
                        validationError = "Please enter a valid positive unit value (or leave blank)."
                        return@Button
                    }

                    onSave(
                        storeName.trim(),
                        productName.trim(),
                        price,
                        barcode.trim().ifBlank { null },
                        unitValue,
                        unitType.trim().ifBlank { null },
                        dateSelected
                    )
                },
                modifier = Modifier.testTag("manual_save_button")
            ) {
                Text("Save Price")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("manual_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
