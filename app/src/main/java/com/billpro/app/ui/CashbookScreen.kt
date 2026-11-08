package com.billpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.data.BillProDao
import com.billpro.app.data.CashbookEntryEntity
import com.billpro.app.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashbookScreen(
    dao: BillProDao,
    businessId: String = "default_biz",
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    onNavigateToReports: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Filter: "ALL", "INCOME" (Paid/In), "EXPENSE" (Out)
    var selectedFilter by remember { mutableStateOf("ALL") }
    var isAddEntryDialogOpen by remember { mutableStateOf(false) }
    var initialDirectionForDialog by remember { mutableStateOf("OUT") }
    var entryToDelete by remember { mutableStateOf<CashbookEntryEntity?>(null) }

    val entries by dao.observeCashbook(businessId).collectAsState(initial = emptyList())
    val totalCashIn by dao.observeTotalCashIn(businessId).collectAsState(initial = 0L)
    val totalCashOut by dao.observeTotalCashOut(businessId).collectAsState(initial = 0L)
    val netBalancePaise = totalCashIn - totalCashOut

    val incomeCount = remember(entries) { entries.count { it.direction == "IN" } }
    val expenseCount = remember(entries) { entries.count { it.direction == "OUT" } }

    val filteredEntries = remember(entries, selectedFilter) {
        when (selectedFilter) {
            "INCOME" -> entries.filter { it.direction == "IN" }
            "EXPENSE" -> entries.filter { it.direction == "OUT" }
            else -> entries
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BillProTheme.colors.secondaryBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BillProTheme.colors.secondaryBackground)
        ) {
            // 1. Signature Top Bar inspired by the reference image
            BillProTopBar(
                title = "Payments",
                onBackClick = onBackClick,
                onStoreProfileClick = onStoreProfileClick
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. Filter Pills row inspired by reference's "Paid [12]" and "Unpaid [5]"
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PaymentFilterPill(
                            label = "Paid (In)",
                            count = incomeCount,
                            isSelected = selectedFilter == "INCOME",
                            onClick = { selectedFilter = if (selectedFilter == "INCOME") "ALL" else "INCOME" },
                            modifier = Modifier.weight(1f)
                        )

                        PaymentFilterPill(
                            label = "Expenses (Out)",
                            count = expenseCount,
                            isSelected = selectedFilter == "EXPENSE",
                            onClick = { selectedFilter = if (selectedFilter == "EXPENSE") "ALL" else "EXPENSE" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. "This Month" Financial Overview Card (Revenue, Expenses, Profit)
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        FinancialSummaryCard(
                            revenueFormatted = formatCurrency(totalCashIn),
                            expensesFormatted = formatCurrency(totalCashOut),
                            profitFormatted = formatCurrency(netBalancePaise),
                            periodLabel = "This Month"
                        )
                    }
                }

                // 4. Reference-Inspired Dark Section (#191919) for Recent Payments & Quick Actions
                item {
                    Surface(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = BillProTheme.colors.darkBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section Header inside dark area
                            SectionHeader(
                                title = "Recent Payments",
                                badgeCount = filteredEntries.size,
                                isDarkTheme = true
                            )

                            if (filteredEntries.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF222222),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = BillProTheme.colors.primaryYellow,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "No Payment Transactions",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Record money in or store utility expenses using the actions below.",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                // Display entries: Top one is highlighted in yellow card like reference!
                                filteredEntries.take(6).forEachIndexed { index, entry ->
                                    if (index == 0) {
                                        // Highlighted Yellow Card (Reference style: Wholesale Supplier)
                                        HighlightedPaymentCard(
                                            entry = entry,
                                            onDelete = { entryToDelete = entry }
                                        )
                                    } else {
                                        // Standard Dark Entry Card (Reference style: Electricity Bill, Rent Payment)
                                        DarkPaymentCard(
                                            entry = entry,
                                            onDelete = { entryToDelete = entry }
                                        )
                                    }
                                }
                            }

                            // 5. Quick Actions Section at the bottom of dark container
                            Spacer(Modifier.height(4.dp))
                            SectionHeader(
                                title = "Quick Actions",
                                badgeCount = 4,
                                isDarkTheme = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QuickActionButton(
                                    label = "Add Expense",
                                    icon = Icons.Default.ArrowDownward,
                                    isDark = true,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        initialDirectionForDialog = "OUT"
                                        isAddEntryDialogOpen = true
                                    }
                                )

                                QuickActionButton(
                                    label = "Add Income",
                                    icon = Icons.Default.ArrowUpward,
                                    isDark = true,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        initialDirectionForDialog = "IN"
                                        isAddEntryDialogOpen = true
                                    }
                                )

                                QuickActionButton(
                                    label = "View Reports",
                                    icon = Icons.Default.PieChart,
                                    isDark = true,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onNavigateToReports?.invoke() }
                                )

                                QuickActionButton(
                                    label = "Payment List",
                                    icon = Icons.Default.ListAlt,
                                    isDark = true,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedFilter = "ALL" }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Payment / Cashbook Entry Dialog
        if (isAddEntryDialogOpen) {
            AddPaymentEntryDialog(
                businessId = businessId,
                initialDirection = initialDirectionForDialog,
                onDismiss = { isAddEntryDialogOpen = false },
                onSave = { entry ->
                    coroutineScope.launch {
                        dao.insertCashbookEntry(entry)
                        isAddEntryDialogOpen = false
                        snackbarHostState.showSnackbar("Payment entry saved successfully")
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        entryToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                title = { Text("Delete Payment Entry", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete this payment record for ${entry.description}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                dao.deleteCashbookEntryById(entry.id, businessId)
                                entryToDelete = null
                                snackbarHostState.showSnackbar("Entry deleted")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE34B4B))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { entryToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * PaymentFilterPill: Modeled after reference's "Paid [12]" and "Unpaid [5]"
 */
@Composable
private fun PaymentFilterPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) BillProTheme.colors.primaryYellow else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFE8B623) else Color(0xFFEBE8E0)
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = BillProTheme.colors.primaryText,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                color = if (isSelected) Color(0xFFE8B623) else Color(0xFFF0EFEA),
                shape = CircleShape
            ) {
                Text(
                    text = count.toString(),
                    color = BillProTheme.colors.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * HighlightedPaymentCard: Signature Yellow Card (Wholesale Supplier style in reference)
 */
@Composable
private fun HighlightedPaymentCard(
    entry: CashbookEntryEntity,
    onDelete: () -> Unit
) {
    val timeAgo = calculateTimeAgo(entry.createdAtEpochMs)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BillProTheme.colors.primaryYellow,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (entry.direction == "IN") Icons.Default.ArrowUpward else Icons.Default.Storefront,
                        contentDescription = null,
                        tint = BillProTheme.colors.primaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.description,
                        color = BillProTheme.colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${entry.category} • ${formatCurrency(entry.amountPaise)}",
                        color = Color(0xFF4A4430),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Timeline countdown ring (e.g. "1 DAY AGO")
            CircularMetricRing(
                value = timeAgo.number,
                label = timeAgo.unit,
                strokeColor = BillProTheme.colors.primaryText,
                size = 54.dp
            )
        }
    }
}

/**
 * DarkPaymentCard: Clean Dark row (Electricity Bill / Rent Payment style in reference)
 */
@Composable
private fun DarkPaymentCard(
    entry: CashbookEntryEntity,
    onDelete: () -> Unit
) {
    val timeAgo = calculateTimeAgo(entry.createdAtEpochMs)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF222222),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(BillProTheme.colors.primaryYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            entry.category.contains("utility", true) -> Icons.Default.FlashOn
                            entry.category.contains("rent", true) -> Icons.Default.Home
                            entry.direction == "IN" -> Icons.Default.ArrowUpward
                            else -> Icons.Default.Payments
                        },
                        contentDescription = null,
                        tint = BillProTheme.colors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.description,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${entry.category} • ${formatCurrency(entry.amountPaise)}",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp
                    )
                }
            }

            // Timeline ring in dark card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2E2E2E), CircleShape)
                        .border(1.5.dp, BillProTheme.colors.primaryYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeAgo.number,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = timeAgo.unit,
                    color = Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

data class TimeAgoResult(val number: String, val unit: String)

private fun calculateTimeAgo(timestampMs: Long): TimeAgoResult {
    val diffMs = System.currentTimeMillis() - timestampMs
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        days <= 0 -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
            if (hours <= 0) TimeAgoResult("1", "HR AGO") else TimeAgoResult(hours.toString(), "HRS AGO")
        }
        days == 1L -> TimeAgoResult("1", "DAY AGO")
        days < 30L -> TimeAgoResult(days.toString(), "DAYS AGO")
        else -> TimeAgoResult("${days / 30}", "MO AGO")
    }
}

@Composable
private fun AddPaymentEntryDialog(
    businessId: String,
    initialDirection: String,
    onDismiss: () -> Unit,
    onSave: (CashbookEntryEntity) -> Unit
) {
    var direction by remember { mutableStateOf(initialDirection) } // "IN" or "OUT"
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (direction == "IN") "SALES" else "EXPENSE") }
    var description by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") }

    val categories = if (direction == "IN") {
        listOf("SALES", "CUSTOMER_PAYMENT", "CAPITAL", "OTHER_INCOME")
    } else {
        listOf("EXPENSE", "PURCHASE", "RENT", "UTILITY", "SALARY", "OTHER_EXPENSE")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (direction == "IN") "Record Income" else "Record Expense",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Direction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            direction = "IN"
                            category = "SALES"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (direction == "IN") BillProTheme.colors.primaryYellow else Color(0xFFF0EFEA),
                            contentColor = BillProTheme.colors.primaryText
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Income (In)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            direction = "OUT"
                            category = "EXPENSE"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (direction == "OUT") Color(0xFFE34B4B) else Color(0xFFF0EFEA),
                            contentColor = if (direction == "OUT") Color.White else BillProTheme.colors.primaryText
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Expense (Out)", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Party Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BillProTheme.colors.secondaryText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replace("_", " "), fontSize = 11.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0 && description.isNotBlank()) {
                        val entry = CashbookEntryEntity(
                            businessId = businessId,
                            createdAtEpochMs = System.currentTimeMillis(),
                            direction = direction,
                            amountPaise = (amount * 100).toLong(),
                            category = category,
                            description = description
                        )
                        onSave(entry)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.primaryYellow,
                    contentColor = BillProTheme.colors.primaryText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Entry", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatCurrency(paise: Long): String {
    val rupees = paise / 100.0
    return "₹ " + String.format(Locale.ENGLISH, "%,.2f", rupees)
}
