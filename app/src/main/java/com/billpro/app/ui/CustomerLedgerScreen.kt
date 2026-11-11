package com.billpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.data.BillProDao
import com.billpro.app.data.CustomerEntity
import com.billpro.app.data.InvoiceEntity
import com.billpro.app.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerScreen(
    dao: BillProDao,
    businessId: String = "default_biz",
    initialOpenAdd: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(initialOpenAdd) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForHistory by remember { mutableStateOf<CustomerEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val customers by if (searchQuery.isBlank()) {
        dao.observeCustomers(businessId).collectAsState(initial = emptyList())
    } else {
        dao.searchCustomers(businessId, searchQuery.trim()).collectAsState(initial = emptyList())
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
            // 1. Signature Yellow Top Bar inspired by reference image
            BillProTopBar(
                title = "Customers & Ledger",
                onBackClick = onBackClick,
                onStoreProfileClick = onStoreProfileClick
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Search and Add Customer Button
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
                                placeholder = "Search name, phone, GSTIN..."
                            )
                        }

                        Button(
                            onClick = { isAddDialogOpen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BillProTheme.colors.primaryYellow,
                                contentColor = BillProTheme.colors.primaryText
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Summary Stats Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "REGISTERED CLIENTS",
                                    color = BillProTheme.colors.secondaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${customers.size} Accounts",
                                    color = BillProTheme.colors.primaryText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFFFAF6E5), CircleShape)
                                    .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.People,
                                    contentDescription = null,
                                    tint = BillProTheme.colors.primaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Customer List
                if (customers.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = if (searchQuery.isNotEmpty()) "No Matching Customers" else "No Customers Registered",
                            subtitle = "Register your business customers to track invoices, credit, and contact details.",
                            actionLabel = "Add Customer",
                            onActionClick = { isAddDialogOpen = true },
                            icon = Icons.Default.People
                        )
                    }
                } else {
                    items(customers, key = { it.id }) { customer ->
                        ModernCustomerCard(
                            customer = customer,
                            onViewHistory = { customerForHistory = customer },
                            onEdit = { customerToEdit = customer },
                            onDelete = { customerToDelete = customer }
                        )
                    }
                }
            }
        }

        // Add / Edit Dialog
        if (isAddDialogOpen || customerToEdit != null) {
            CustomerFormDialog(
                customer = customerToEdit,
                businessId = businessId,
                onDismiss = {
                    isAddDialogOpen = false
                    customerToEdit = null
                },
                onSave = { savedCustomer ->
                    coroutineScope.launch {
                        dao.upsertCustomer(savedCustomer)
                        isAddDialogOpen = false
                        customerToEdit = null
                        snackbarHostState.showSnackbar("Customer saved successfully")
                    }
                }
            )
        }

        // Customer History / Ledger Dialog
        if (customerForHistory != null) {
            CustomerHistoryDialog(
                dao = dao,
                businessId = businessId,
                customer = customerForHistory!!,
                onDismiss = { customerForHistory = null }
            )
        }

        // Delete Confirmation Dialog
        if (customerToDelete != null) {
            AlertDialog(
                onDismissRequest = { customerToDelete = null },
                title = { Text("Delete Customer", color = BillProTheme.colors.primaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to delete '${customerToDelete!!.name}'? Past generated invoices will be retained.",
                        color = BillProTheme.colors.secondaryText
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = customerToDelete!!
                            coroutineScope.launch {
                                dao.deleteCustomerById(target.id, businessId)
                                customerToDelete = null
                                snackbarHostState.showSnackbar("Customer deleted")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE34B4B))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customerToDelete = null }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * ModernCustomerCard: Rounded white card with yellow circular avatar initials
 */
@Composable
private fun ModernCustomerCard(
    customer: CustomerEntity,
    onViewHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val initial = customer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "C"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onViewHistory() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular yellow avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(BillProTheme.colors.primaryYellow, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = BillProTheme.colors.primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = customer.name,
                        color = BillProTheme.colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (customer.phone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = BillProTheme.colors.secondaryText, modifier = Modifier.size(12.dp))
                            Text(customer.phone, color = BillProTheme.colors.secondaryText, fontSize = 12.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFFAF8F0), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Customer",
                            tint = BillProTheme.colors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFFFEBEE), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Customer",
                            tint = Color(0xFFE34B4B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (customer.gstin.isNotBlank() || customer.address.isNotBlank()) {
                HorizontalDivider(color = Color(0xFFF0EFEA))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (customer.gstin.isNotBlank()) {
                        Text(
                            text = "GSTIN: ${customer.gstin}",
                            color = BillProTheme.colors.secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (customer.address.isNotBlank()) {
                        Text(
                            text = customer.address,
                            color = BillProTheme.colors.mutedText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerFormDialog(
    customer: CustomerEntity?,
    businessId: String = "default_biz",
    onDismiss: () -> Unit,
    onSave: (CustomerEntity) -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var gstin by remember { mutableStateOf(customer?.gstin ?: "") }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var openingBalanceText by remember { mutableStateOf(if (customer != null && customer.openingBalancePaise > 0) (customer.openingBalancePaise / 100.0).toString() else "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (customer == null) "Add Customer" else "Edit Customer",
                color = BillProTheme.colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFE34B4B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Customer / Business Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Billing Address (Optional)") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Customer name is required"
                        return@Button
                    }
                    val opening = (openingBalanceText.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                    val entity = CustomerEntity(
                        id = customer?.id ?: 0L,
                        businessId = businessId,
                        name = name.trim(),
                        phone = phone.trim(),
                        gstin = gstin.trim(),
                        address = address.trim(),
                        openingBalancePaise = opening,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.primaryYellow,
                    contentColor = BillProTheme.colors.primaryText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (customer == null) "Create Customer" else "Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun CustomerHistoryDialog(
    dao: BillProDao,
    businessId: String,
    customer: CustomerEntity,
    onDismiss: () -> Unit
) {
    val invoices by dao.observeCustomerInvoices(customer.id, businessId).collectAsState(initial = emptyList())
    val totalBilled = invoices.sumOf { it.totalPaise }
    val totalPaid = invoices.sumOf { it.paidPaise }
    val totalDue = (totalBilled - totalPaid).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = customer.name, color = BillProTheme.colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "Customer Ledger & Invoices", color = BillProTheme.colors.secondaryText, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Stats
                Surface(
                    color = Color(0xFFFAF8F0),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Billed", color = BillProTheme.colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", totalBilled / 100.0), color = BillProTheme.colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pending Due", color = Color(0xFFE34B4B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", totalDue / 100.0), color = if (totalDue > 0) Color(0xFFE34B4B) else Color(0xFF24A36A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (invoices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No invoices billed to this customer yet.", color = BillProTheme.colors.secondaryText, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(invoices, key = { it.id }) { inv ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(inv.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BillProTheme.colors.primaryText)
                                        Text(inv.status, fontSize = 11.sp, color = BillProTheme.colors.secondaryText)
                                    }
                                    Text("₹ " + String.format(Locale.ENGLISH, "%,.2f", inv.totalPaise / 100.0), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BillProTheme.colors.primaryText)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.primaryYellow,
                    contentColor = BillProTheme.colors.primaryText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
