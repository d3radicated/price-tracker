package com.example.util

import com.example.data.entity.Merchant
import com.example.data.entity.Product
import com.example.data.entity.Receipt
import com.example.data.entity.ReceiptItem
import java.util.Locale

object ReceiptParser {

    data class ParsedUnit(val name: String, val value: Double?, val unit: String?)

    // Regex matching numbers followed by common volume/mass/item unit types
    private val unitRegex = Regex("""(?i)(\d+(?:\.\d+)?)\s*(ml|l|g|kg|pcs|pc)\b""")

    /**
     * Parses a line from a receipt to extract name, optional size/volume value, and unit.
     */
    fun parseLineText(line: String): ParsedUnit {
        val cleanLine = line.trim()
        val matchResult = unitRegex.find(cleanLine)
        if (matchResult != null) {
            val fullMatch = matchResult.value
            val valueStr = matchResult.groupValues[1]
            val unitStr = matchResult.groupValues[2]
            val value = valueStr.toDoubleOrNull()
            
            // Extract product name by removing the unit match and cleaning up prices
            var name = cleanLine.replace(fullMatch, "").trim()
            name = cleanReceiptProductName(name)
            
            // Normalize units to clean standard casing
            val normalizedUnit = when (unitStr.lowercase()) {
                "ml" -> "mL"
                "l" -> "L"
                "g" -> "g"
                "kg" -> "kg"
                "pcs", "pc" -> "pcs"
                else -> unitStr
            }
            
            return ParsedUnit(name, value, normalizedUnit)
        }
        
        return ParsedUnit(cleanReceiptProductName(cleanLine), null, null)
    }

    /**
     * Cleans up receipt product names by stripping price indicators, symbols, and excess spacing.
     */
    fun cleanReceiptProductName(raw: String): String {
        var text = raw.trim()
        
        // Remove currency symbols
        text = text.replace("$", "").replace("€", "").replace("£", "")
        
        // Remove common receipt pricing structures like " 2.49" or " @ 1.99" at the end of the line
        text = text.replace(Regex("""\s*(?:@\s*)?\d+\.\d{2}\s*$"""), "")
        
        // Remove simple quantity indicators like "1x " or "2 x " at start or end
        text = text.replace(Regex("""^\d+\s*x\s*"""), "")
        text = text.replace(Regex("""\s+\d+\s*x\s*$"""), "")
        
        // Remove multiple spaces
        text = text.replace(Regex("""\s+"""), " ")
        
        // Clean leading/trailing noise characters
        text = text.trim { it <= ' ' || it == '*' || it == '-' || it == ':' || it == '.' || it == ',' }
        
        // Avoid returning empty strings
        return if (text.isBlank()) "Unknown Item" else text
    }

    /**
     * Standard Levenshtein Distance algorithm to find string difference.
     */
    fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var distance = IntArray(lhsLength + 1) { it }
        var newDistance = IntArray(lhsLength + 1)

        for (j in 1..rhsLength) {
            newDistance[0] = j
            for (i in 1..lhsLength) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = distance[i - 1] + match
                val costInsert = distance[i] + 1
                val costDelete = newDistance[i - 1] + 1
                newDistance[i] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = distance
            distance = newDistance
            newDistance = swap
        }
        return distance[lhsLength]
    }

    /**
     * Finds the closest match in a list of existing names using Levenshtein distance.
     * Returns the matching name if the similarity is above the specified threshold (0.0 to 1.0).
     */
    fun findBestMatch(scannedName: String, existingNames: List<String>, threshold: Double = 0.7): String? {
        if (scannedName.isBlank() || existingNames.isEmpty()) return null
        var bestMatch: String? = null
        var bestSimilarity = 0.0

        val s1 = scannedName.lowercase(Locale.ROOT)
        for (existingName in existingNames) {
            val s2 = existingName.lowercase(Locale.ROOT)
            val maxLength = maxOf(s1.length, s2.length)
            if (maxLength == 0) continue
            
            val distance = levenshteinDistance(s1, s2)
            val similarity = 1.0 - (distance.toDouble() / maxLength.toDouble())
            if (similarity > bestSimilarity && similarity >= threshold) {
                bestSimilarity = similarity
                bestMatch = existingName
            }
        }
        return bestMatch
    }

    /**
     * Converts matching unit types to display a normalized "Price per 100mL" or "Price per kg".
     */
    fun getNormalizedPriceString(price: Double, value: Double?, unit: String?): String {
        if (value == null || value <= 0.0 || unit == null) return "N/A"
        return try {
            val u = unit.lowercase(Locale.ROOT).trim()
            when (u) {
                "ml" -> {
                    val pricePer100ml = (price / value) * 100.0
                    String.format(Locale.US, "$%.2f per 100mL", pricePer100ml)
                }
                "l" -> {
                    // 1L = 1000mL -> Price per 100mL
                    val mlValue = value * 1000.0
                    val pricePer100ml = (price / mlValue) * 100.0
                    String.format(Locale.US, "$%.2f per 100mL", pricePer100ml)
                }
                "g" -> {
                    // 1000g = 1kg -> Price per kg
                    val kgValue = value / 1000.0
                    val pricePerKg = price / kgValue
                    String.format(Locale.US, "$%.2f per kg", pricePerKg)
                }
                "kg" -> {
                    val pricePerKg = price / value
                    String.format(Locale.US, "$%.2f per kg", pricePerKg)
                }
                "pcs", "pc" -> {
                    val pricePerPc = price / value
                    String.format(Locale.US, "$%.2f per pc", pricePerPc)
                }
                else -> String.format(Locale.US, "$%.2f per %s", price / value, unit)
            }
        } catch (e: Exception) {
            "N/A"
        }
    }

    /**
     * Matches scanned product name with existing database items.
     * Checks exact names, then uses fuzzy Levenshtein.
     */
    fun findSmartMatch(scannedName: String, existingProducts: List<Product>): Product? {
        if (scannedName.isBlank()) return null
        
        // 1. Exact match (ignore case)
        val exactMatch = existingProducts.find { it.name.equals(scannedName, ignoreCase = true) }
        if (exactMatch != null) return exactMatch
        
        // 2. Fuzzy match
        val existingNames = existingProducts.map { it.name }
        val bestFuzzyName = findBestMatch(scannedName, existingNames, threshold = 0.7)
        if (bestFuzzyName != null) {
            return existingProducts.find { it.name.equals(bestFuzzyName, ignoreCase = true) }
        }
        
        return null
    }

    // --- Minimalist JSON Export / Import parser for local backup ---

    fun exportBackup(
        merchants: List<Merchant>,
        products: List<Product>,
        receipts: List<Receipt>,
        items: List<ReceiptItem>
    ): String {
        val sb = java.lang.StringBuilder()
        sb.append("{\n")
        
        // Merchants
        sb.append("  \"merchants\": [\n")
        merchants.forEachIndexed { i, m ->
            sb.append("    {\"id\": ${m.id}, \"name\": \"${escapeJson(m.name)}\"}")
            if (i < merchants.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // Products
        sb.append("  \"products\": [\n")
        products.forEachIndexed { i, p ->
            val barcodeStr = if (p.barcode == null) "null" else "\"${escapeJson(p.barcode)}\""
            val valStr = p.unitValue?.toString() ?: "null"
            val typeStr = if (p.unitType == null) "null" else "\"${escapeJson(p.unitType)}\""
            sb.append("    {\"id\": ${p.id}, \"barcode\": $barcodeStr, \"name\": \"${escapeJson(p.name)}\", \"unitValue\": $valStr, \"unitType\": $typeStr}")
            if (i < products.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // Receipts
        sb.append("  \"receipts\": [\n")
        receipts.forEachIndexed { i, r ->
            sb.append("    {\"id\": ${r.id}, \"merchantId\": ${r.merchantId}, \"purchaseDate\": ${r.purchaseDate}, \"totalAmount\": ${r.totalAmount}}")
            if (i < receipts.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // ReceiptItems
        sb.append("  \"receipt_items\": [\n")
        items.forEachIndexed { i, ri ->
            sb.append("    {\"id\": ${ri.id}, \"receiptId\": ${ri.receiptId}, \"productId\": ${ri.productId}, \"price\": ${ri.price}, \"quantity\": ${ri.quantity}}")
            if (i < items.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")

        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    // A lightweight parser that doesn't depend on complex external engines, ensuring 100% offline stability
    fun parseBackup(json: String): ParsedBackup {
        val merchants = mutableListOf<Merchant>()
        val products = mutableListOf<Product>()
        val receipts = mutableListOf<Receipt>()
        val items = mutableListOf<ReceiptItem>()

        try {
            // Very basic state-machine parser for simple structures
            val lines = json.lines()
            var currentSection = ""
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("\"merchants\":")) {
                    currentSection = "merchants"
                    continue
                } else if (trimmed.startsWith("\"products\":")) {
                    currentSection = "products"
                    continue
                } else if (trimmed.startsWith("\"receipts\":")) {
                    currentSection = "receipts"
                    continue
                } else if (trimmed.startsWith("\"receipt_items\":")) {
                    currentSection = "receipt_items"
                    continue
                }

                if (trimmed.startsWith("{") && trimmed.endsWith("}") || (trimmed.startsWith("{") && trimmed.endsWith("},"))) {
                    // Parse values
                    val jsonObject = trimmed.removePrefix("{").removeSuffix("}").removeSuffix(",").trim()
                    val pairs = jsonObject.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()) // split by comma not inside quotes
                    val map = mutableMapOf<String, String>()
                    for (pair in pairs) {
                        val parts = pair.split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim().replace("\"", "")
                            val value = parts[1].trim()
                            map[key] = value
                        }
                    }

                    when (currentSection) {
                        "merchants" -> {
                            val id = map["id"]?.toLongOrNull() ?: 0L
                            val name = map["name"]?.removeSurrounding("\"") ?: ""
                            merchants.add(Merchant(id = id, name = name))
                        }
                        "products" -> {
                            val id = map["id"]?.toLongOrNull() ?: 0L
                            val name = map["name"]?.removeSurrounding("\"") ?: ""
                            val barcodeRaw = map["barcode"]
                            val barcode = if (barcodeRaw == "null" || barcodeRaw == null) null else barcodeRaw.removeSurrounding("\"")
                            val valStr = map["unitValue"]
                            val unitValue = if (valStr == "null" || valStr == null) null else valStr.toDoubleOrNull()
                            val typeRaw = map["unitType"]
                            val unitType = if (typeRaw == "null" || typeRaw == null) null else typeRaw.removeSurrounding("\"")
                            products.add(Product(id = id, barcode = barcode, name = name, unitValue = unitValue, unitType = unitType))
                        }
                        "receipts" -> {
                            val id = map["id"]?.toLongOrNull() ?: 0L
                            val merchantId = map["merchantId"]?.toLongOrNull() ?: 0L
                            val purchaseDate = map["purchaseDate"]?.toLongOrNull() ?: System.currentTimeMillis()
                            val totalAmount = map["totalAmount"]?.toDoubleOrNull() ?: 0.0
                            receipts.add(Receipt(id = id, merchantId = merchantId, purchaseDate = purchaseDate, totalAmount = totalAmount))
                        }
                        "receipt_items" -> {
                            val id = map["id"]?.toLongOrNull() ?: 0L
                            val receiptId = map["receiptId"]?.toLongOrNull() ?: 0L
                            val productId = map["productId"]?.toLongOrNull() ?: 0L
                            val price = map["price"]?.toDoubleOrNull() ?: 0.0
                            val quantity = map["quantity"]?.toIntOrNull() ?: 1
                            items.add(ReceiptItem(id = id, receiptId = receiptId, productId = productId, price = price, quantity = quantity))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedBackup(merchants, products, receipts, items)
    }

    data class ParsedBackup(
        val merchants: List<Merchant>,
        val products: List<Product>,
        val receipts: List<Receipt>,
        val items: List<ReceiptItem>
    )
}
