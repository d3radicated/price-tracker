package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PriceHistoryEntry
import com.example.util.ReceiptParser
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHistoryScreen(
    viewModel: PriceTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val product by viewModel.selectedProduct.collectAsState()
    val history by viewModel.selectedProductHistory.collectAsState()

    Scaffold(
        topBar = {
            // Handled as a custom, highly-polished header inside the content Column to match the mockup design
        },
        modifier = modifier
    ) { innerPadding ->
        if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No product selected")
            }
        } else {
            val p = product!!
            val sortedHistory = remember(history) { history.sortedByDescending { it.purchaseDate } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Premium Custom Polish Header for Product History
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        
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
                                "Price History",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                }

                // Product Stats Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("product_info_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val sizeStr = if (p.unitValue != null && p.unitType != null) {
                                "${p.unitValue} ${p.unitType}"
                            } else {
                                "Unitless"
                            }
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    sizeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            if (!p.barcode.isNullOrBlank()) {
                                Text(
                                    "UPC: ${p.barcode}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Display trend info if available
                        if (sortedHistory.size >= 2) {
                            val latest = sortedHistory[0]
                            val previous = sortedHistory[1]
                            val diff = latest.price - previous.price
                            val trendColor = if (diff < 0) Color(0xFF2E7D32) else if (diff > 0) MaterialTheme.colorScheme.error else Color.Gray

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val icon = if (diff < 0) Icons.Default.TrendingDown else if (diff > 0) Icons.Default.TrendingUp else Icons.Default.TrendingFlat
                                Icon(icon, contentDescription = null, tint = trendColor)
                                
                                val trendText = if (diff < 0) {
                                    String.format(Locale.US, "Down $%.2f compared to last scan", -diff)
                                } else if (diff > 0) {
                                    String.format(Locale.US, "Up $%.2f compared to last scan", diff)
                                } else {
                                    "Stable price"
                                }
                                Text(
                                    trendText,
                                    color = trendColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    "Price Evolution Chart",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                PriceEvolutionChart(
                    history = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                        .testTag("price_history_canvas")
                )

                Text(
                    "Historic Price Points",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (sortedHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No scanned entries found for this product.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedHistory) { entry ->
                            HistoricEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceEvolutionChart(
    history: List<PriceHistoryEntry>,
    modifier: Modifier = Modifier
) {
    val sortedHistory = remember(history) { history.sortedBy { it.purchaseDate } }
    if (sortedHistory.size < 2) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Need 2+ price entries to plot trendline",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val prices = sortedHistory.map { it.price }
    val maxPrice = prices.maxOrNull() ?: 1.0
    val minPrice = prices.minOrNull() ?: 0.0
    val minDate = sortedHistory.minOf { it.purchaseDate }
    val maxDate = sortedHistory.maxOf { it.purchaseDate }

    // Dynamic scale bounds
    val priceRange = if (maxPrice == minPrice) 1.0 else (maxPrice - minPrice) * 1.2
    val priceOffset = if (maxPrice == minPrice) 0.5 else (minPrice - (maxPrice - minPrice) * 0.1)

    val dateRange = if (maxDate == minDate) 1.0 else (maxDate - minDate).toDouble()

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.US) }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(16.dp))
    ) {
        val width = size.width
        val height = size.height

        val paddingX = 60f
        val paddingY = 60f

        val graphWidth = width - (2 * paddingX)
        val graphHeight = height - (2 * paddingY)

        val points = sortedHistory.map { entry ->
            val x = paddingX + (((entry.purchaseDate - minDate).toDouble() / dateRange) * graphWidth).toFloat()
            val yValue = entry.price
            val yPercent = if (priceRange == 0.0) 0.5f else ((yValue - priceOffset) / priceRange).toFloat()
            val y = paddingY + (graphHeight - (yPercent * graphHeight))
            Offset(x, y)
        }

        // Draw Connecting trendlines
        for (i in 0 until points.size - 1) {
            drawLine(
                color = primaryColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f
            )
        }

        // Draw Data nodes and price values
        points.forEachIndexed { i, pt ->
            drawCircle(
                color = secondaryColor,
                radius = 12f,
                center = pt
            )
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = pt
            )

            // Dynamic date & price values label printing
            drawContext.canvas.nativeCanvas.apply {
                val pText = String.format(Locale.US, "$%.2f", sortedHistory[i].price)
                val dText = dateFormat.format(Date(sortedHistory[i].purchaseDate))
                
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                
                // Draw price on top of node
                drawText(pText, pt.x, pt.y - 20f, paint)
                
                // Draw date underneath node
                paint.color = android.graphics.Color.GRAY
                paint.textSize = 20f
                paint.isFakeBoldText = false
                drawText(dText, pt.x, pt.y + 36f, paint)
            }
        }
    }
}

@Composable
fun HistoricEntryRow(
    entry: PriceHistoryEntry
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.merchantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    dateFormat.format(Date(entry.purchaseDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (entry.unitValue != null && entry.unitType != null) {
                    val normalizedPrice = ReceiptParser.getNormalizedPriceString(entry.price, entry.unitValue, entry.unitType)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Normalized: $normalizedPrice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                String.format(Locale.US, "$%.2f", entry.price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
