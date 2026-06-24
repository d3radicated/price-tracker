package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merchants",
    indices = [Index(value = ["name"], unique = true)]
)
data class Merchant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["name", "unitValue", "unitType"], unique = true)
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val unitValue: Double?,
    val unitType: String?
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = Merchant::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["merchantId"])]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantId: Long,
    val purchaseDate: Long,
    val totalAmount: Double
)

@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["productId"])
    ]
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val productId: Long,
    val price: Double,
    val quantity: Int
)

data class PriceHistoryEntry(
    val price: Double,
    val purchaseDate: Long,
    val productName: String,
    val unitValue: Double?,
    val unitType: String?,
    val merchantName: String,
    val receiptId: Long
)
