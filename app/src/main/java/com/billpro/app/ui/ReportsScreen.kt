package com.billpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.data.BillProDao
import com.billpro.app.ui.components.*
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    dao: BillProDao,
    businessId: String = "default_biz",
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf("THIS_MONTH") } // TODAY, THIS_WEEK, THIS_MONTH, ALL_TIME

    val totalSalesPaise by dao.observeTotalSales(businessId).collectAsState(initial = 0L)
    val totalInvoicesCount by dao.observeTotalInvoicesCount(businessId).collectAsState(initial = 0)
    val pendingPaymentsPaise by dao.observePendingPayments(businessId).collectAsState(initial = 0L)
    val totalCgst by dao.observeTotalCgst(businessId).collectAsState(initial = 0L)
    val totalSgst by dao.observeTotalSgst(businessId).collectAsState(initial = 0L)
    val totalIgst by dao.observeTotalIgst(businessId).collectAsState(initial = 0L)
    val totalTaxable by dao.observeTotalTaxableValue(businessId).collectAsState(initial = 0L)
    val totalGstPaise = totalCgst + totalSgst + totalIgst

    val totalCashIn by dao.observeTotalCashIn(businessId).collectAsState(initial = 0L)
    val totalCashOut by dao.observeTotalCashOut(businessId).collectAsState(initial = 0L)
    val netProfitPaise = totalCashIn - totalCashOut

    val lowStockCount by dao.observeLowStockCount(businessId).collectAsState(initial = 0)
    val productCount by dao.observeProductCount(businessId).collectAsState(initial = 0)
    val customerCount by dao.observeCustomerCount(businessId).collectAsState(initial = 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BillProTheme.colors.secondaryBackground)
    ) {
        // 1. Signature Yellow Top Bar inspired by reference image
        BillProTopBar(
            title = "Reports & GST",
            onBackClick = onBackClick,
            onStoreProfileClick = onStoreProfileClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "TODAY" to "Today",
                        "THIS_WEEK" to "This Week",
                        "THIS_MONTH" to "This Month",
                        "ALL_TIME" to "All Time"
                    ).forEach { (id, label) ->
                        item {
                            FilterChip(
                                selected = selectedPeriod == id,
                                onClick = { selectedPeriod = id },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (selectedPeriod == id) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Financial Snapshot Card (Revenue, Expenses, Net Profit)
            item {
                FinancialSummaryCard(
                    revenueFormatted = formatCurrency(totalCashIn),
                    expensesFormatted = formatCurrency(totalCashOut),
                    profitFormatted = formatCurrency(netProfitPaise),
                    periodLabel = "P&L Summary (${selectedPeriod.replace("_", " ")})"
                )
            }

            // Sales & Collection Metrics
            item {
                SectionHeader(title = "Sales & Dues Overview")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReportStatCard(
                        title = "TOTAL INVOICES",
                        value = "$totalInvoicesCount Bills",
                        subtitle = formatCurrency(totalSalesPaise),
                        icon = Icons.Default.ReceiptLong,
                        accentColor = BillProTheme.colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )

                    ReportStatCard(
                        title = "OUTSTANDING DUES",
                        value = formatCurrency(pendingPaymentsPaise),
                        subtitle = "To collect",
                        icon = Icons.Default.WarningAmber,
                        accentColor = Color(0xFFE99A28),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // GST Breakdown Card
            item {
                SectionHeader(title = "GST Tax Analysis")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL GST COLLECTED",
                                color = BillProTheme.colors.secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = formatCurrency(totalGstPaise),
                                color = BillProTheme.colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Divider(color = Color(0xFFF0EFEA))

                        GstRow(label = "Taxable Turnover", amount = formatCurrency(totalTaxable))
                        GstRow(label = "Central GST (CGST)", amount = formatCurrency(totalCgst))
                        GstRow(label = "State GST (SGST)", amount = formatCurrency(totalSgst))
                        GstRow(label = "Integrated GST (IGST)", amount = formatCurrency(totalIgst))
                    }
                }
            }

            // Inventory & Customer Volume
            item {
                SectionHeader(title = "Catalog & Customer Counts")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReportStatCard(
                        title = "REGISTERED CLIENTS",
                        value = "$customerCount",
                        subtitle = "Accounts",
                        icon = Icons.Default.People,
                        accentColor = BillProTheme.colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )

                    ReportStatCard(
                        title = "INVENTORY ITEMS",
                        value = "$productCount Products",
                        subtitle = if (lowStockCount > 0) "$lowStockCount low stock" else "Healthy",
                        icon = Icons.Default.Inventory2,
                        accentColor = if (lowStockCount > 0) Color(0xFFE34B4B) else Color(0xFF24A36A),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GstRow(label: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = BillProTheme.colors.secondaryText, fontSize = 13.sp)
        Text(text = amount, color = BillProTheme.colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReportStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFAF6E5), CircleShape)
                    .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }

            Text(text = title, color = BillProTheme.colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(text = value, color = BillProTheme.colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = subtitle, color = BillProTheme.colors.mutedText, fontSize = 11.sp)
        }
    }
}

private fun formatCurrency(paise: Long): String {
    val rupees = paise / 100.0
    return "₹ " + String.format(Locale.ENGLISH, "%,.2f", rupees)
}
