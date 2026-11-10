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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.billpro.app.data.*
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
    var invoiceForPrintPreview by remember { mutableStateOf<InvoiceEntity?>(null) }
    var printLines by remember { mutableStateOf<List<InvoiceLineEntity>>(emptyList()) }
    var storeProfileState by remember { mutableStateOf<StoreProfile?>(null) }

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
                                text = "Click 'New Bill' to create your first invoice.",
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
                        onPrintReceipt = {
                            coroutineScope.launch {
                                printLines = dao.getInvoiceLines(invoice.id, businessId)
                                storeProfileState = StoreProfile.load(context, businessId)
                                invoiceForPrintPreview = invoice
                            }
                        },
                        onSharePdf = {
                            coroutineScope.launch {
                                val lines = dao.getInvoiceLines(invoice.id, businessId)
                                val store = StoreProfile.load(context, businessId)
                                val pdf = InvoicePdfGenerator.generateInvoicePdf(context, invoice, lines, customer, store)
                                InvoicePdfGenerator.shareInvoicePdf(context, pdf)
                            }
                        },
                        onDownloadPdf = {
                            coroutineScope.launch {
                                val lines = dao.getInvoiceLines(invoice.id, businessId)
                                val store = StoreProfile.load(context, businessId)
                                val pdf = InvoicePdfGenerator.generateInvoicePdf(context, invoice, lines, customer, store)
                                val savedFile = InvoicePdfGenerator.savePdfToDownloads(context, pdf)
                                if (savedFile != null) {
                                    Toast.makeText(context, "Invoice saved to Downloads/BillPro/${savedFile.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save PDF to Downloads", Toast.LENGTH_SHORT).show()
                                }
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
            onPrintReceipt = { inv ->
                selectedInvoiceForDetail = null
                coroutineScope.launch {
                    printLines = dao.getInvoiceLines(inv.id, businessId)
                    storeProfileState = StoreProfile.load(context, businessId)
                    invoiceForPrintPreview = inv
                }
            },
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

    // In-App 80mm Print Receipt Dialog
    if (invoiceForPrintPreview != null && storeProfileState != null) {
        val invoice = invoiceForPrintPreview!!
        val customer = invoice.customerId?.let { customerMap[it] }
        PrintReceiptDialog(
            invoice = invoice,
            lines = printLines,
            customer = customer,
            store = storeProfileState!!,
            onDismiss = {
                invoiceForPrintPreview = null
            },
            onPrintSuccess = {
                invoiceForPrintPreview = null
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
    onPrintReceipt: () -> Unit,
    onSharePdf: () -> Unit,
    onDownloadPdf: () -> Unit
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = invoice.invoiceNumber,
                        color = BillProTheme.colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(color = statusContainerColor, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = invoice.status,
                            color = statusBadgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = dateText,
                    color = BillProTheme.colors.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            HorizontalDivider(color = BillProTheme.colors.cardBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "₹" + String.format(Locale.ENGLISH, "%,.2f", invoice.totalPaise / 100.0),
                        color = BillProTheme.colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )

                    // 80mm Print receipt button
                    IconButton(
                        onClick = onPrintReceipt,
                        modifier = Modifier
                            .size(32.dp)
                            .background(BillProTheme.colors.accent, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Print 80mm Receipt",
                            tint = Color(0xFF191919),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Download button
                    IconButton(
                        onClick = onDownloadPdf,
                        modifier = Modifier
                            .size(32.dp)
                            .background(BillProTheme.colors.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download PDF",
                            tint = BillProTheme.colors.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = onSharePdf,
                        modifier = Modifier
                            .size(32.dp)
                            .background(BillProTheme.colors.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share PDF",
                            tint = BillProTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CREATE INVOICE DIALOG (NO GST, PREVIOUS BALANCE OPTION, LIVE STOCK DECREMENT)
// -----------------------------------------------------------------------------------------
private data class DraftLineItem(
    val product: ProductEntity?,
    val description: String,
    val quantity: Double,
    val unitPricePaise: Long,
    val quantityText: String = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else "%.1f".format(quantity)
) {
    val lineTotalPaise: Long
        get() = (quantity * unitPricePaise).toLong()
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
    var customerSearchQuery by remember { mutableStateOf("") }
    var productSearchQuery by remember { mutableStateOf("") }
    var isBrowseCatalogOpen by remember { mutableStateOf(false) }

    var discountText by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var notes by remember { mutableStateOf("") }

    // Previous Balance states: fully automatic from customer account
    var includePreviousBalance by remember { mutableStateOf(false) }
    var customerPastBalancePaise by remember { mutableStateOf(0L) }

    val selectedCustomer = remember(selectedCustomerId, customers) {
        selectedCustomerId?.let { id -> customers.firstOrNull { it.id == id } }
    }

    LaunchedEffect(selectedCustomerId) {
        val cid = selectedCustomerId
        if (cid != null) {
            val unpaid = dao.getCustomerUnpaidInvoiceBalance(cid, businessId)
            val cust = customers.firstOrNull { it.id == cid }
            val opening = cust?.openingBalancePaise ?: 0L
            val pastDue = (opening + unpaid).coerceAtLeast(0L)
            customerPastBalancePaise = pastDue
            includePreviousBalance = pastDue > 0
        } else {
            customerPastBalancePaise = 0L
            includePreviousBalance = false
        }
    }

    val lineItems = remember { mutableStateListOf<DraftLineItem>() }

    // Filter customers based on search query
    val filteredCustomers = remember(customers, customerSearchQuery) {
        if (customerSearchQuery.isBlank()) {
            customers.take(8)
        } else {
            val q = customerSearchQuery.trim().lowercase()
            customers.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
        }
    }

    // Filter products based on search query
    val filteredProducts = remember(availableProducts, productSearchQuery) {
        if (productSearchQuery.isBlank()) {
            emptyList()
        } else {
            val q = productSearchQuery.trim().lowercase()
            availableProducts.filter {
                it.name.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                it.barcode.lowercase().contains(q)
            }
        }
    }

    // Totals calculation (No GST)
    val subtotalPaise = remember(lineItems.toList()) {
        lineItems.sumOf { it.lineTotalPaise }
    }

    val discountPaise = remember(discountText) {
        val discountRupees = discountText.toDoubleOrNull() ?: 0.0
        (discountRupees * 100).toLong().coerceAtLeast(0)
    }

    val appliedPreviousBalancePaise = if (selectedCustomerId != null && includePreviousBalance) {
        customerPastBalancePaise
    } else 0L

    val totalAmountPaise = remember(subtotalPaise, discountPaise, appliedPreviousBalancePaise) {
        (subtotalPaise - discountPaise + appliedPreviousBalancePaise).coerceAtLeast(0)
    }

    var paidAmountText by remember { mutableStateOf("") }
    LaunchedEffect(totalAmountPaise) {
        paidAmountText = String.format(Locale.ENGLISH, "%.2f", totalAmountPaise / 100.0)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BillProTheme.colors.secondaryBackground
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = BillProTheme.colors.primaryText
                                    )
                                }

                                Text(
                                    text = "Create New Bill",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BillProTheme.colors.primaryText
                                )
                            }

                            Surface(
                                color = BillProTheme.colors.primaryYellow,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = invoiceNumber,
                                    color = Color(0xFF191919),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Payable",
                                    fontSize = 11.sp,
                                    color = BillProTheme.colors.secondaryText
                                )
                                Text(
                                    text = "₹" + String.format(Locale.ENGLISH, "%,.2f", totalAmountPaise / 100.0),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BillProTheme.colors.primaryText
                                )
                            }

                            Button(
                                onClick = {
                                    if (lineItems.isEmpty()) {
                                        errorMessage = "Please add at least one item to the invoice"
                                        return@Button
                                    }
                                    if (lineItems.any { it.quantity <= 0.0 }) {
                                        errorMessage = "Item quantity must be greater than zero"
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
                                        isInterState = false,
                                        subtotalPaise = subtotalPaise,
                                        cgstPaise = 0L,
                                        sgstPaise = 0L,
                                        igstPaise = 0L,
                                        discountPaise = discountPaise,
                                        previousBalancePaise = appliedPreviousBalancePaise,
                                        totalPaise = totalAmountPaise,
                                        paidPaise = paidPaise,
                                        status = status,
                                        notes = notes.trim()
                                    )

                                    val dbLines = lineItems.map { item ->
                                        InvoiceLineEntity(
                                            invoiceId = 0L,
                                            businessId = businessId,
                                            productId = item.product?.id,
                                            description = item.description,
                                            quantity = item.quantity,
                                            unitPricePaise = item.unitPricePaise,
                                            gstRatePercent = 0.0,
                                            taxableValuePaise = item.lineTotalPaise,
                                            taxPaise = 0L,
                                            lineTotalPaise = item.lineTotalPaise
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
                                    containerColor = BillProTheme.colors.primaryYellow,
                                    contentColor = Color(0xFF191919)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .padding(start = 16.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate Bill & Save", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (errorMessage != null) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(errorMessage!!, color = Color(0xFFB71C1C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 1. Customer Section with Search Bar
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "CUSTOMER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BillProTheme.colors.secondaryText,
                                    letterSpacing = 1.sp
                                )

                                if (selectedCustomer != null) {
                                    // Selected Customer Card
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFFF9E6),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.primaryYellow),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color(0xFFFFC928), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color(0xFF191919),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = selectedCustomer.name,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF191919)
                                                    )
                                                    if (selectedCustomer.phone.isNotBlank()) {
                                                        Text(
                                                            text = selectedCustomer.phone,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF666666)
                                                        )
                                                    }
                                                    if (customerPastBalancePaise > 0) {
                                                        Text(
                                                            text = "Past Dues: ₹" + String.format(Locale.ENGLISH, "%,.2f", customerPastBalancePaise / 100.0),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFFE99A28)
                                                        )
                                                    }
                                                }
                                            }

                                            TextButton(onClick = { selectedCustomerId = null }) {
                                                Text("Change", color = Color(0xFF191919), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else {
                                    // Customer Search Bar
                                    OutlinedTextField(
                                        value = customerSearchQuery,
                                        onValueChange = { customerSearchQuery = it },
                                        placeholder = { Text("Search customer by name or phone...", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF888888)) },
                                        trailingIcon = {
                                            if (customerSearchQuery.isNotBlank()) {
                                                IconButton(onClick = { customerSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF888888))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Quick Customer Options Chips
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = selectedCustomerId == null,
                                                onClick = { selectedCustomerId = null },
                                                label = { Text("Walk-in / Cash", fontWeight = FontWeight.SemiBold) },
                                                leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                        }

                                        items(filteredCustomers) { cust ->
                                            FilterChip(
                                                selected = selectedCustomerId == cust.id,
                                                onClick = {
                                                    selectedCustomerId = cust.id
                                                    customerSearchQuery = ""
                                                },
                                                label = { Text(cust.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Product Search & Add Section
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ADD ITEMS / PRODUCTS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BillProTheme.colors.secondaryText,
                                        letterSpacing = 1.sp
                                    )

                                    TextButton(
                                        onClick = { isBrowseCatalogOpen = !isBrowseCatalogOpen },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            if (isBrowseCatalogOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isBrowseCatalogOpen) "Hide Catalog" else "Browse Catalog (${availableProducts.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Search Bar for Items
                                OutlinedTextField(
                                    value = productSearchQuery,
                                    onValueChange = { productSearchQuery = it },
                                    placeholder = { Text("Search item by name, barcode or SKU...", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF888888)) },
                                    trailingIcon = {
                                        if (productSearchQuery.isNotBlank()) {
                                            IconButton(onClick = { productSearchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF888888))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Product search results or browse list
                                val productsToShow = if (productSearchQuery.isNotBlank()) {
                                    filteredProducts
                                } else if (isBrowseCatalogOpen) {
                                    availableProducts
                                } else {
                                    emptyList()
                                }

                                if (productsToShow.isNotEmpty()) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        productsToShow.take(8).forEach { prod ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF9F9F8),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val existingIndex = lineItems.indexOfFirst { it.product?.id == prod.id }
                                                            if (existingIndex >= 0) {
                                                                val existing = lineItems[existingIndex]
                                                                val newQ = existing.quantity + 1.0
                                                                lineItems[existingIndex] = existing.copy(
                                                                    quantity = newQ,
                                                                    quantityText = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else "%.1f".format(newQ)
                                                                )
                                                            } else {
                                                                lineItems.add(
                                                                    DraftLineItem(
                                                                        product = prod,
                                                                        description = prod.name,
                                                                        quantity = 1.0,
                                                                        unitPricePaise = prod.salePricePaise,
                                                                        quantityText = "1"
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191919))
                                                        Text(
                                                            "Stock: ${prod.stockQuantity} ${prod.unit} • ₹${String.format(Locale.ENGLISH, "%.2f", prod.salePricePaise / 100.0)}",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF666666)
                                                        )
                                                    }

                                                    FilledTonalButton(
                                                        onClick = {
                                                            val existingIndex = lineItems.indexOfFirst { it.product?.id == prod.id }
                                                            if (existingIndex >= 0) {
                                                                val existing = lineItems[existingIndex]
                                                                val newQ = existing.quantity + 1.0
                                                                lineItems[existingIndex] = existing.copy(
                                                                    quantity = newQ,
                                                                    quantityText = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else "%.1f".format(newQ)
                                                                )
                                                            } else {
                                                                lineItems.add(
                                                                    DraftLineItem(
                                                                        product = prod,
                                                                        description = prod.name,
                                                                        quantity = 1.0,
                                                                        unitPricePaise = prod.salePricePaise,
                                                                        quantityText = "1"
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Bill Items Section with Direct Unit Input Box
                    item {
                        Text(
                            text = "BILL ITEMS (${lineItems.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BillProTheme.colors.secondaryText,
                            letterSpacing = 1.sp
                        )
                    }

                    if (lineItems.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No items added yet. Search items above to add to bill.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }
                        }
                    } else {
                        items(lineItems.size) { index ->
                            val item = lineItems[index]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.description,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF191919)
                                            )
                                            Text(
                                                text = "Unit Rate: ₹" + String.format(Locale.ENGLISH, "%.2f", item.unitPricePaise / 100.0),
                                                fontSize = 11.sp,
                                                color = Color(0xFF777777)
                                            )
                                        }

                                        IconButton(
                                            onClick = { lineItems.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Item",
                                                tint = Color(0xFFE34B4B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quantity Direct Text Area / Input Box
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Units:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF555555)
                                            )

                                            IconButton(
                                                onClick = {
                                                    val current = item.quantity
                                                    if (current > 1.0) {
                                                        val newQ = current - 1.0
                                                        lineItems[index] = item.copy(
                                                            quantity = newQ,
                                                            quantityText = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else "%.1f".format(newQ)
                                                        )
                                                    } else {
                                                        lineItems.removeAt(index)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color(0xFFF1EFE9), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFF191919), modifier = Modifier.size(14.dp))
                                            }

                                            // Direct Text Area where user can enter any unit / quantity
                                            OutlinedTextField(
                                                value = item.quantityText,
                                                onValueChange = { newText ->
                                                    val filtered = newText.filter { it.isDigit() || it == '.' }
                                                    val parsed = filtered.toDoubleOrNull() ?: 0.0
                                                    lineItems[index] = item.copy(
                                                        quantity = parsed,
                                                        quantityText = filtered
                                                    )
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                modifier = Modifier.width(68.dp),
                                                textStyle = LocalTextStyle.current.copy(
                                                    textAlign = TextAlign.Center,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            IconButton(
                                                onClick = {
                                                    val newQ = item.quantity + 1.0
                                                    lineItems[index] = item.copy(
                                                        quantity = newQ,
                                                        quantityText = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else "%.1f".format(newQ)
                                                    )
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(Color(0xFFF1EFE9), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color(0xFF191919), modifier = Modifier.size(14.dp))
                                            }
                                        }

                                        // Item total amount
                                        Text(
                                            text = "₹" + String.format(Locale.ENGLISH, "%,.2f", item.lineTotalPaise / 100.0),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF191919)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Automatic Customer Previous Balance (Include / Exclude)
                    if (selectedCustomerId != null) {
                        item {
                            Surface(
                                color = BillProTheme.colors.cardBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Previous Balance", color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            if (customerPastBalancePaise > 0) {
                                                Text(
                                                    "Past outstanding: ₹" + String.format(Locale.ENGLISH, "%,.2f", customerPastBalancePaise / 100.0),
                                                    color = BillProTheme.colors.warning,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Text("No past outstanding balance (₹0.00)", color = BillProTheme.colors.muted, fontSize = 11.sp)
                                            }
                                        }

                                        if (customerPastBalancePaise > 0) {
                                            Switch(
                                                checked = includePreviousBalance,
                                                onCheckedChange = { includePreviousBalance = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color(0xFF191919),
                                                    checkedTrackColor = BillProTheme.colors.accent
                                                )
                                            )
                                        }
                                    }

                                    if (customerPastBalancePaise > 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF1EFE9), RoundedCornerShape(8.dp))
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (includePreviousBalance) BillProTheme.colors.accent else Color.Transparent,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { includePreviousBalance = true }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Include (+₹" + String.format(Locale.ENGLISH, "%,.2f", customerPastBalancePaise / 100.0) + ")",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (includePreviousBalance) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color(0xFF191919)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (!includePreviousBalance) Color.White else Color.Transparent,
                                            shadowElevation = if (!includePreviousBalance) 1.dp else 0.dp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { includePreviousBalance = false }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Exclude (₹0.00)",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (!includePreviousBalance) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (!includePreviousBalance) Color(0xFF191919) else Color(0xFF777777)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Discount & Notes
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("DISCOUNT & BILL NOTES", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = discountText,
                                        onValueChange = { discountText = it },
                                        label = { Text("Discount (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = { notes = it },
                                        label = { Text("Notes (Optional)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Summary Breakdown
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FINANCIAL BREAKDOWN", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Items Subtotal:", color = BillProTheme.colors.secondaryText, fontSize = 12.sp)
                                    Text("₹" + String.format(Locale.ENGLISH, "%,.2f", subtotalPaise / 100.0), color = BillProTheme.colors.primaryText, fontSize = 12.sp)
                                }

                                if (discountPaise > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Discount:", color = BillProTheme.colors.secondaryText, fontSize = 12.sp)
                                        Text("- ₹" + String.format(Locale.ENGLISH, "%,.2f", discountPaise / 100.0), color = Color(0xFF24A36A), fontSize = 12.sp)
                                    }
                                }

                                if (appliedPreviousBalancePaise > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Previous Balance:", color = Color(0xFFE99A28), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("+ ₹" + String.format(Locale.ENGLISH, "%,.2f", appliedPreviousBalancePaise / 100.0), color = Color(0xFFE99A28), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                HorizontalDivider(color = Color(0xFFEBE8E0))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("NET PAYABLE:", color = BillProTheme.colors.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "₹" + String.format(Locale.ENGLISH, "%,.2f", totalAmountPaise / 100.0),
                                        color = BillProTheme.colors.primaryText,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // 7. Payment Mode & Collection
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("PAYMENT COLLECTION", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = paidAmountText,
                                        onValueChange = { paidAmountText = it },
                                        label = { Text("Amount Paid (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Mode:", color = Color(0xFF777777), fontSize = 10.sp)
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

                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
}

// -----------------------------------------------------------------------------------------
// INVOICE DETAIL & PDF SHARING / DOWNLOAD DIALOG
// -----------------------------------------------------------------------------------------
@Composable
private fun InvoiceDetailDialog(
    dao: BillProDao,
    businessId: String = "default_biz",
    invoice: InvoiceEntity,
    customer: CustomerEntity?,
    onDismiss: () -> Unit,
    onPrintReceipt: (InvoiceEntity) -> Unit = {},
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
                            Text("$qtyText x ₹" + String.format(Locale.ENGLISH, "%.2f", line.unitPricePaise / 100.0), color = BillProTheme.colors.muted, fontSize = 11.sp)
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
                        if (invoice.discountPaise > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Discount", color = BillProTheme.colors.muted, fontSize = 12.sp)
                                Text("- ₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.discountPaise / 100.0), color = BillProTheme.colors.success, fontSize = 12.sp)
                            }
                        }
                        if (invoice.previousBalancePaise > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Previous Balance", color = BillProTheme.colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("+ ₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.previousBalancePaise / 100.0), color = BillProTheme.colors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Amount", color = BillProTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", invoice.totalPaise / 100.0), color = BillProTheme.colors.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
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

                // 80mm Thermal Receipt Print Button (Opens In-App Print Dialog)
                item {
                    Button(
                        onClick = {
                            onPrintReceipt(invoice)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent, contentColor = Color(0xFF191919)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Print Receipt (80mm Thermal)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Action Buttons: Download PDF & Share PDF
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
                                    val savedFile = InvoicePdfGenerator.savePdfToDownloads(context, pdf)
                                    if (savedFile != null) {
                                        Toast.makeText(context, "Invoice saved to Downloads/BillPro/${savedFile.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save PDF to Downloads", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

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
                            Text("Share PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Close", color = BillProTheme.colors.text, fontWeight = FontWeight.Bold)
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
