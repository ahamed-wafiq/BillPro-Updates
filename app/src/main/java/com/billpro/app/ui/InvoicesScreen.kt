package com.billpro.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.data.*
import com.billpro.app.domain.GstCalculator
import com.billpro.app.printing.EscPosFormatter
import com.billpro.app.printing.InvoicePdfGenerator
import com.billpro.app.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    dao: BillProDao,
    businessId: String = "default_biz",
    initialOpenCreate: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("ALL") } // ALL, PAID, PARTIAL, UNPAID, CANCELLED

    var isCreateDialogOpen by remember { mutableStateOf(initialOpenCreate) }
    var selectedInvoiceForDetail by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceToCancel by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceForPayment by remember { mutableStateOf<InvoiceEntity?>(null) }

    val allInvoices by if (searchQuery.isBlank()) {
        dao.observeInvoices(businessId).collectAsState(initial = emptyList())
    } else {
        dao.searchInvoices(businessId, searchQuery.trim()).collectAsState(initial = emptyList())
    }

    val filteredInvoices = remember(allInvoices, selectedStatus) {
        if (selectedStatus == "ALL") allInvoices else allInvoices.filter { it.status == selectedStatus }
    }

    val customers by dao.observeCustomers(businessId).collectAsState(initial = emptyList())
    val customerMap = remember(customers) { customers.associateBy { it.id } }

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
            BillProTopBar(
                title = "Invoices & Billing",
                onBackClick = onBackClick,
                onStoreProfileClick = onStoreProfileClick
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Search and New Bill Action
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "Search invoice # or notes..."
                            )
                        }

                        Button(
                            onClick = { isCreateDialogOpen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BillProTheme.colors.primaryYellow,
                                contentColor = BillProTheme.colors.primaryText
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Bill", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }


            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "PAID", "PARTIAL", "UNPAID", "CANCELLED").forEach { status ->
                        val count = if (status == "ALL") allInvoices.size else allInvoices.count { it.status == status }
                        item {
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text("$status ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BillProTheme.colors.accent,
                                    selectedLabelColor = Color(0xFF17130A),
                                    containerColor = BillProTheme.colors.surface,
                                    labelColor = BillProTheme.colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedStatus == status,
                                    borderColor = BillProTheme.colors.cardBorder,
                                    selectedBorderColor = BillProTheme.colors.accent
                                )
                            )
                        }
                    }
                }
            }

            // Invoices List
            if (filteredInvoices.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = BillProTheme.colors.cardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = BillProTheme.colors.muted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching invoices found" else "No invoices recorded",
                                color = BillProTheme.colors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Click 'New Bill' to create your first tax invoice with live GST calculation.",
                                color = BillProTheme.colors.muted,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { isCreateDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BillProTheme.colors.accent,
                                    contentColor = Color(0xFF17130A)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Create First Bill", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(filteredInvoices, key = { it.id }) { invoice ->
                    val customer = invoice.customerId?.let { customerMap[it] }
                    InvoiceCard(
                        invoice = invoice,
                        customerName = customer?.name ?: "Walk-in Customer",
                        onClick = { selectedInvoiceForDetail = invoice },
                        onSharePdf = {
                            coroutineScope.launch {
                                val lines = dao.getInvoiceLines(invoice.id, businessId)
                                val store = StoreProfile.load(context, businessId)
                                val pdf = InvoicePdfGenerator.generateInvoicePdf(context, invoice, lines, customer, store)
                                InvoicePdfGenerator.shareInvoicePdf(context, pdf)
                            }
                        }
                    )
                }
            }
        }
    }
}

    // Create New Invoice Wizard / Dialog
    if (isCreateDialogOpen) {
        CreateInvoiceDialog(
            dao = dao,
            businessId = businessId,
            customers = customers,
            onDismiss = { isCreateDialogOpen = false },
            onInvoiceCreated = { invoiceId ->
                isCreateDialogOpen = false
                coroutineScope.launch {
                    val created = dao.getInvoiceById(invoiceId, businessId)
                    selectedInvoiceForDetail = created
                    snackbarHostState.showSnackbar("Invoice generated successfully")
                }
            }
        )
    }

    // Invoice Detail Dialog
    if (selectedInvoiceForDetail != null) {
        val invoice = selectedInvoiceForDetail!!
        val customer = invoice.customerId?.let { customerMap[it] }

        InvoiceDetailDialog(
            dao = dao,
            businessId = businessId,
            invoice = invoice,
            customer = customer,
            onDismiss = { selectedInvoiceForDetail = null },
            onRecordPayment = {
                invoiceForPayment = invoice
                selectedInvoiceForDetail = null
            },
            onCancelInvoice = {
                invoiceToCancel = invoice
                selectedInvoiceForDetail = null
            }
        )
    }

    // Record Payment Dialog
    if (invoiceForPayment != null) {
        RecordPaymentDialog(
            invoice = invoiceForPayment!!,
            onDismiss = { invoiceForPayment = null },
            onConfirmPayment = { amountPaise, paymentMode ->
                coroutineScope.launch {
                    dao.recordPaymentForBusiness(invoiceForPayment!!.id, businessId, amountPaise, paymentMode)
                    val updated = dao.getInvoiceById(invoiceForPayment!!.id, businessId)
                    selectedInvoiceForDetail = updated
                    invoiceForPayment = null
                    snackbarHostState.showSnackbar("Payment recorded successfully")
                }
            }
        )
    }

    // Cancel Invoice Confirmation Dialog
    if (invoiceToCancel != null) {
        AlertDialog(
            onDismissRequest = { invoiceToCancel = null },
            title = { Text("Cancel Invoice", color = BillProTheme.colors.text) },
            text = {
                Text(
                    "Are you sure you want to cancel invoice \"${invoiceToCancel!!.invoiceNumber}\"? Sold items will be automatically restored to your inventory stock.",
                    color = BillProTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toCancel = invoiceToCancel!!
                        coroutineScope.launch {
                            dao.cancelInvoiceWithStockRestorationForBusiness(toCancel.id, businessId)
                            invoiceToCancel = null
                            snackbarHostState.showSnackbar("Invoice cancelled & stock restored")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.error)
                ) {
                    Text("Cancel Invoice", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToCancel = null }) {
                    Text("Dismiss", color = BillProTheme.colors.muted)
                }
            },
            containerColor = BillProTheme.colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun InvoiceCard(
    invoice: InvoiceEntity,
    customerName: String,
    onClick: () -> Unit,
    onSharePdf: () -> Unit
) {
    val dateText = remember(invoice.createdAtEpochMs) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(invoice.createdAtEpochMs))
    }

    val isPaid = invoice.status == "PAID"
    val isPartial = invoice.status == "PARTIAL"
    val isCancelled = invoice.status == "CANCELLED"

    val statusBadgeColor = when {
        isCancelled -> BillProTheme.colors.muted
        isPaid -> BillProTheme.colors.success
        isPartial -> BillProTheme.colors.warning
        else -> BillProTheme.colors.error
    }
    val statusContainerColor = when {
        isCancelled -> BillProTheme.colors.surfaceVariant
        isPaid -> BillProTheme.colors.successContainer
        isPartial -> BillProTheme.colors.warningContainer
        else -> BillProTheme.colors.errorContainer
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BillProTheme.colors.cardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = invoice.invoiceNumber,
                        color = BillProTheme.colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(color = statusContainerColor, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = invoice.status,
                            color = statusBadgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = dateText,
                    color = BillProTheme.colors.muted,
                    fontSize = 11.sp
                )
            }

            Divider(color = BillProTheme.colors.cardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = customerName,
                        color = BillProTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val balance = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
                    if (balance > 0 && !isCancelled) {
                        Text(
                            text = "Due: ₹" + String.format(Locale.ENGLISH, "%,.2f", balance / 100.0),
                            color = BillProTheme.colors.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.totalPaise / 100.0),
                        color = BillProTheme.colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onSharePdf,
                        modifier = Modifier
                            .size(32.dp)
                            .background(BillProTheme.colors.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share PDF",
                            tint = BillProTheme.colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CREATE INVOICE DIALOG WITH LIVE GST & STOCK DECREMENT
// -----------------------------------------------------------------------------------------
private data class DraftLineItem(
    val product: ProductEntity?,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val gstRatePercent: Double
) {
    val taxableValuePaise: Long
        get() = (quantity * unitPricePaise).toLong()

    fun calculateTax(isInterState: Boolean): Long {
        val breakdown = GstCalculator.calculate(taxableValuePaise, gstRatePercent, isInterState)
        return breakdown.cgstPaise + breakdown.sgstPaise + breakdown.igstPaise
    }

    fun calculateTotal(isInterState: Boolean): Long {
        return taxableValuePaise + calculateTax(isInterState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateInvoiceDialog(
    dao: BillProDao,
    businessId: String = "default_biz",
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onInvoiceCreated: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val availableProducts by dao.observeProducts(businessId).collectAsState(initial = emptyList())

    val autoInvoiceNo = remember {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
        "INV-${sdf.format(Date())}"
    }

    var invoiceNumber by remember { mutableStateOf(autoInvoiceNo) }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var isInterState by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var notes by remember { mutableStateOf("") }

    val lineItems = remember { mutableStateListOf<DraftLineItem>() }

    // Dialog state to add item from inventory
    var isAddProductDropdownOpen by remember { mutableStateOf(false) }
    var customItemDesc by remember { mutableStateOf("") }
    var customItemPriceText by remember { mutableStateOf("") }
    var customItemGst by remember { mutableStateOf(18.0) }
    var isAddingCustomItem by remember { mutableStateOf(false) }

    // Totals calculation
    val subtotalPaise = remember(lineItems.toList()) {
        lineItems.sumOf { it.taxableValuePaise }
    }

    val totalTaxBreakdown = remember(lineItems.toList(), isInterState) {
        var totalCgst = 0L
        var totalSgst = 0L
        var totalIgst = 0L

        for (item in lineItems) {
            val breakdown = GstCalculator.calculate(item.taxableValuePaise, item.gstRatePercent, isInterState)
            totalCgst += breakdown.cgstPaise
            totalSgst += breakdown.sgstPaise
            totalIgst += breakdown.igstPaise
        }
        Triple(totalCgst, totalSgst, totalIgst)
    }

    val discountPaise = remember(discountText) {
        val discountRupees = discountText.toDoubleOrNull() ?: 0.0
        (discountRupees * 100).toLong().coerceAtLeast(0)
    }

    val totalAmountPaise = remember(subtotalPaise, totalTaxBreakdown, discountPaise) {
        val tax = totalTaxBreakdown.first + totalTaxBreakdown.second + totalTaxBreakdown.third
        (subtotalPaise + tax - discountPaise).coerceAtLeast(0)
    }

    var paidAmountText by remember { mutableStateOf("") }
    LaunchedEffect(totalAmountPaise) {
        if (paidAmountText.isBlank() || paidAmountText == "0") {
            paidAmountText = String.format(Locale.ENGLISH, "%.2f", totalAmountPaise / 100.0)
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Create New Bill", color = BillProTheme.colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Surface(color = BillProTheme.colors.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(invoiceNumber, color = BillProTheme.colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (errorMessage != null) {
                    item {
                        Text(errorMessage!!, color = BillProTheme.colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 1. Customer Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Customer", color = BillProTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Walk-in option
                            FilterChip(
                                selected = selectedCustomerId == null,
                                onClick = { selectedCustomerId = null },
                                label = { Text("Walk-in / Cash") }
                            )

                            // Registered customers
                            if (customers.isNotEmpty()) {
                                customers.take(3).forEach { cust ->
                                    FilterChip(
                                        selected = selectedCustomerId == cust.id,
                                        onClick = { selectedCustomerId = cust.id },
                                        label = { Text(cust.name.take(12)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Intra-state vs Inter-state Tax Switch
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BillProTheme.colors.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isInterState) "Inter-State (IGST)" else "Intra-State (CGST + SGST)",
                                color = BillProTheme.colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isInterState) "Applies single Integrated GST" else "Splits 50/50 Central & State GST",
                                color = BillProTheme.colors.muted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isInterState,
                            onCheckedChange = { isInterState = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = BillProTheme.colors.accent)
                        )
                    }
                }

                // 3. Products List Header & Add buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BILL ITEMS (${lineItems.size})",
                            color = BillProTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { isAddProductDropdownOpen = !isAddProductDropdownOpen },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("From Catalog", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Product catalog picker dropdown
                if (isAddProductDropdownOpen) {
                    item {
                        Surface(
                            color = BillProTheme.colors.cardBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Select Product:", color = BillProTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (availableProducts.isEmpty()) {
                                    Text("No products in inventory. Add products in the Inventory tab.", color = BillProTheme.colors.muted, fontSize = 11.sp)
                                } else {
                                    availableProducts.take(6).forEach { prod ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    lineItems.add(
                                                        DraftLineItem(
                                                            product = prod,
                                                            description = prod.name,
                                                            quantity = 1.0,
                                                            unitPricePaise = prod.salePricePaise,
                                                            gstRatePercent = prod.gstRatePercent
                                                        )
                                                    )
                                                    isAddProductDropdownOpen = false
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(prod.name, color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Stock: ${prod.stockQuantity} ${prod.unit}", color = BillProTheme.colors.muted, fontSize = 10.sp)
                                            }
                                            Text(
                                                "₹ " + String.format(Locale.ENGLISH, "%,.2f", prod.salePricePaise / 100.0),
                                                color = BillProTheme.colors.accent,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Added Line Items
                if (lineItems.isEmpty()) {
                    item {
                        Text(
                            "No items added yet. Click 'From Catalog' above to add products.",
                            color = BillProTheme.colors.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(lineItems.size) { index ->
                        val item = lineItems[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BillProTheme.colors.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.description, color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Rate: ₹${item.unitPricePaise / 100.0} | GST: ${item.gstRatePercent.toInt()}%",
                                        color = BillProTheme.colors.muted,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Quantity dec/inc
                                    IconButton(
                                        onClick = {
                                            if (item.quantity > 1.0) {
                                                lineItems[index] = item.copy(quantity = item.quantity - 1.0)
                                            } else {
                                                lineItems.removeAt(index)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp).background(BillProTheme.colors.cardBackground, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = BillProTheme.colors.text, modifier = Modifier.size(14.dp))
                                    }

                                    Text(
                                        text = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else "%.1f".format(item.quantity),
                                        color = BillProTheme.colors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    IconButton(
                                        onClick = {
                                            lineItems[index] = item.copy(quantity = item.quantity + 1.0)
                                        },
                                        modifier = Modifier.size(28.dp).background(BillProTheme.colors.cardBackground, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", tint = BillProTheme.colors.text, modifier = Modifier.size(14.dp))
                                    }

                                    Text(
                                        text = "₹ " + String.format(Locale.ENGLISH, "%,.2f", item.calculateTotal(isInterState) / 100.0),
                                        color = BillProTheme.colors.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.widthIn(min = 60.dp)
                                    )

                                    IconButton(
                                        onClick = { lineItems.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = BillProTheme.colors.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Financial Calculation Summary
                item {
                    Surface(
                        color = BillProTheme.colors.cardBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal:", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", subtotalPaise / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                            }
                            if (isInterState) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("IGST:", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                    Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", totalTaxBreakdown.third / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CGST:", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                    Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", totalTaxBreakdown.first / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SGST:", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                    Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", totalTaxBreakdown.second / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(color = BillProTheme.colors.cardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NET PAYABLE:", color = BillProTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "₹ " + String.format(Locale.ENGLISH, "%,.2f", totalAmountPaise / 100.0),
                                    color = BillProTheme.colors.accent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // 5. Payment Details
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Payment Collection", color = BillProTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = paidAmountText,
                                onValueChange = { paidAmountText = it },
                                label = { Text("Amount Paid (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )

                            // Quick Payment Mode chips
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Payment Mode", color = BillProTheme.colors.muted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Cash", "UPI", "Credit").forEach { mode ->
                                        FilterChip(
                                            selected = paymentMode == mode,
                                            onClick = { paymentMode = mode },
                                            label = { Text(mode, fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (lineItems.isEmpty()) {
                        errorMessage = "Please add at least one item to the invoice"
                        return@Button
                    }
                    val paidRupees = paidAmountText.toDoubleOrNull() ?: 0.0
                    val paidPaise = (paidRupees * 100).toLong().coerceIn(0, totalAmountPaise)

                    val status = when {
                        paidPaise >= totalAmountPaise && totalAmountPaise > 0 -> "PAID"
                        paidPaise > 0 -> "PARTIAL"
                        else -> "UNPAID"
                    }

                    val now = System.currentTimeMillis()
                    val invoice = InvoiceEntity(
                        businessId = businessId,
                        invoiceNumber = invoiceNumber.trim(),
                        customerId = selectedCustomerId,
                        createdAtEpochMs = now,
                        placeOfSupplyStateCode = "",
                        isInterState = isInterState,
                        subtotalPaise = subtotalPaise,
                        cgstPaise = totalTaxBreakdown.first,
                        sgstPaise = totalTaxBreakdown.second,
                        igstPaise = totalTaxBreakdown.third,
                        discountPaise = discountPaise,
                        totalPaise = totalAmountPaise,
                        paidPaise = paidPaise,
                        status = status,
                        notes = notes.trim()
                    )

                    val dbLines = lineItems.map { item ->
                        val lineTax = item.calculateTax(isInterState)
                        InvoiceLineEntity(
                            invoiceId = 0L,
                            businessId = businessId,
                            productId = item.product?.id,
                            description = item.description,
                            quantity = item.quantity,
                            unitPricePaise = item.unitPricePaise,
                            gstRatePercent = item.gstRatePercent,
                            taxableValuePaise = item.taxableValuePaise,
                            taxPaise = lineTax,
                            lineTotalPaise = item.taxableValuePaise + lineTax
                        )
                    }

                    coroutineScope.launch {
                        val newInvoiceId = dao.createInvoiceWithLines(
                            invoice = invoice,
                            lines = dbLines,
                            decrementStock = true,
                            recordCashbook = true
                        )
                        onInvoiceCreated(newInvoiceId)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.accent,
                    contentColor = Color(0xFF17130A)
                )
            ) {
                Text("Generate Bill & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BillProTheme.colors.muted)
            }
        },
        containerColor = BillProTheme.colors.surface,
        shape = RoundedCornerShape(18.dp)
    )
}

// -----------------------------------------------------------------------------------------
// INVOICE DETAIL & PDF SHARING / PRINTING DIALOG
// -----------------------------------------------------------------------------------------
@Composable
private fun InvoiceDetailDialog(
    dao: BillProDao,
    businessId: String = "default_biz",
    invoice: InvoiceEntity,
    customer: CustomerEntity?,
    onDismiss: () -> Unit,
    onRecordPayment: () -> Unit,
    onCancelInvoice: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<InvoiceLineEntity>>(emptyList()) }

    LaunchedEffect(invoice.id) {
        lines = dao.getInvoiceLines(invoice.id, businessId)
    }

    val dateStr = remember(invoice.createdAtEpochMs) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
    }

    val balanceDue = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(invoice.invoiceNumber, color = BillProTheme.colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(dateStr, color = BillProTheme.colors.muted, fontSize = 11.sp)
                }
                Surface(
                    color = if (invoice.status == "PAID") BillProTheme.colors.successContainer else BillProTheme.colors.warningContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        invoice.status,
                        color = if (invoice.status == "PAID") BillProTheme.colors.success else BillProTheme.colors.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Details
                item {
                    Surface(
                        color = BillProTheme.colors.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Billed To:", color = BillProTheme.colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(customer?.name ?: "Walk-in / Cash Customer", color = BillProTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (customer != null && customer.phone.isNotBlank()) {
                                Text("Phone: ${customer.phone}", color = BillProTheme.colors.textSecondary, fontSize = 11.sp)
                            }
                            if (customer != null && customer.gstin.isNotBlank()) {
                                Text("GSTIN: ${customer.gstin}", color = BillProTheme.colors.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Line items
                item {
                    Text("ITEMS BREAKDOWN", color = BillProTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }

                items(lines) { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.description, color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val qtyText = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.1f".format(line.quantity)
                            Text("$qtyText x ₹${line.unitPricePaise / 100.0} (GST ${line.gstRatePercent.toInt()}%)", color = BillProTheme.colors.muted, fontSize = 11.sp)
                        }
                        Text(
                            "₹ " + String.format(Locale.ENGLISH, "%,.2f", line.lineTotalPaise / 100.0),
                            color = BillProTheme.colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    HorizontalDivider(color = BillProTheme.colors.cardBorder)
                }

                // Financials
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", color = BillProTheme.colors.muted, fontSize = 12.sp)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.subtotalPaise / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                        }
                        if (invoice.cgstPaise > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CGST", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.cgstPaise / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                            }
                        }
                        if (invoice.sgstPaise > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SGST", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.sgstPaise / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                            }
                        }
                        if (invoice.igstPaise > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("IGST", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.igstPaise / 100.0), color = BillProTheme.colors.text, fontSize = 12.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Amount", color = BillProTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.totalPaise / 100.0), color = BillProTheme.colors.accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount Paid", color = BillProTheme.colors.muted, fontSize = 12.sp)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.paidPaise / 100.0), color = BillProTheme.colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (balanceDue > 0 && invoice.status != "CANCELLED") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Balance Due", color = BillProTheme.colors.warning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", balanceDue / 100.0), color = BillProTheme.colors.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val store = StoreProfile.load(context, businessId)
                                    val pdf = InvoicePdfGenerator.generateInvoicePdf(context, invoice, lines, customer, store)
                                    InvoicePdfGenerator.shareInvoicePdf(context, pdf)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share PDF", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val store = StoreProfile.load(context, businessId)
                                val receiptBytes = EscPosFormatter.receipt(
                                    storeName = store.storeName,
                                    invoiceNo = invoice.invoiceNumber,
                                    lines = lines.map { it.description to it.lineTotalPaise },
                                    totalPaise = invoice.totalPaise
                                )
                                Toast.makeText(context, "Receipt formatted (${receiptBytes.size} bytes ready for printer)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print", fontSize = 12.sp)
                        }
                    }
                }

                if (balanceDue > 0 && invoice.status != "CANCELLED") {
                    item {
                        Button(
                            onClick = onRecordPayment,
                            colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent, contentColor = Color(0xFF17130A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Collect Payment (₹${balanceDue / 100.0})", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (invoice.status != "CANCELLED") {
                    item {
                        TextButton(
                            onClick = onCancelInvoice,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel This Invoice", color = BillProTheme.colors.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BillProTheme.colors.accent)
            }
        },
        containerColor = BillProTheme.colors.surface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun RecordPaymentDialog(
    invoice: InvoiceEntity,
    onDismiss: () -> Unit,
    onConfirmPayment: (Long, String) -> Unit
) {
    val remainingPaise = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
    var amountText by remember { mutableStateOf(String.format(Locale.ENGLISH, "%.2f", remainingPaise / 100.0)) }
    var paymentMode by remember { mutableStateOf("Cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment", color = BillProTheme.colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invoice: ${invoice.invoiceNumber}", color = BillProTheme.colors.muted, fontSize = 13.sp)
                Text(
                    "Remaining Balance: ₹" + String.format(Locale.ENGLISH, "%,.2f", remainingPaise / 100.0),
                    color = BillProTheme.colors.warning,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Payment Method", color = BillProTheme.colors.textSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cash", "UPI", "Bank Transfer").forEach { mode ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(mode, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountRupees = amountText.toDoubleOrNull() ?: 0.0
                    val paise = (amountRupees * 100).toLong()
                    if (paise > 0) {
                        onConfirmPayment(paise, paymentMode)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent, contentColor = Color(0xFF17130A))
            ) {
                Text("Confirm Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BillProTheme.colors.muted)
            }
        },
        containerColor = BillProTheme.colors.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
