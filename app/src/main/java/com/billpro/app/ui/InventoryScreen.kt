package com.billpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.billpro.app.data.InventoryLedgerEntity
import com.billpro.app.data.ProductEntity
import com.billpro.app.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    dao: BillProDao,
    businessId: String = "default_biz",
    initialOpenAdd: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Catalog, 1: Stock Ledger

    // Catalog state
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, LOW_STOCK, OUT_OF_STOCK
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var isAddProductDialogOpen by remember { mutableStateOf(initialOpenAdd) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    // Ledger state
    var ledgerSearchQuery by remember { mutableStateOf("") }
    var selectedLedgerFilter by remember { mutableStateOf("ALL") } // ALL, RESTOCK, SALE, WASTAGE, ADJUSTMENT, RETURN
    var isAddMovementDialogOpen by remember { mutableStateOf(false) }
    var movementProductTarget by remember { mutableStateOf<ProductEntity?>(null) }
    var productForLedgerHistory by remember { mutableStateOf<ProductEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val products by if (searchQuery.isBlank()) {
        dao.observeProducts(businessId).collectAsState(initial = emptyList())
    } else {
        dao.searchProducts(businessId, searchQuery.trim()).collectAsState(initial = emptyList())
    }

    val ledgerEntries by if (ledgerSearchQuery.isBlank()) {
        dao.observeInventoryLedger(businessId).collectAsState(initial = emptyList())
    } else {
        dao.searchInventoryLedger(businessId, ledgerSearchQuery.trim()).collectAsState(initial = emptyList())
    }

    val filteredProducts = remember(products, selectedFilter) {
        when (selectedFilter) {
            "LOW_STOCK" -> products.filter { it.stockQuantity > 0 && it.stockQuantity <= it.lowStockThreshold }
            "OUT_OF_STOCK" -> products.filter { it.stockQuantity <= 0.0 }
            else -> products
        }
    }

    val filteredLedgerEntries = remember(ledgerEntries, selectedLedgerFilter) {
        if (selectedLedgerFilter == "ALL") {
            ledgerEntries
        } else {
            ledgerEntries.filter { it.movementType.equals(selectedLedgerFilter, ignoreCase = true) }
        }
    }

    val lowStockCount = remember(products) {
        products.count { it.stockQuantity <= it.lowStockThreshold }
    }

    val totalRestockUnits = remember(ledgerEntries) {
        ledgerEntries.filter { it.movementType in listOf("RESTOCK", "RETURN") }.sumOf { it.quantityChange }
    }

    val totalOutflowUnits = remember(ledgerEntries) {
        ledgerEntries.filter { it.movementType in listOf("SALE", "WASTAGE") }.sumOf { Math.abs(it.quantityChange) }
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
                title = "Inventory",
                onBackClick = onBackClick,
                onStoreProfileClick = onStoreProfileClick
            )

            // 2. Modern Rounded Tab Selector inspired by reference
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeTab == 0) BillProTheme.colors.primaryYellow else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeTab == 0) Color(0xFFE8B623) else Color(0xFFEBE8E0)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { activeTab = 0 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = BillProTheme.colors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Catalog (${products.size})",
                            color = BillProTheme.colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 0) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeTab == 1) BillProTheme.colors.primaryYellow else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeTab == 1) Color(0xFFE8B623) else Color(0xFFEBE8E0)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { activeTab = 1 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = BillProTheme.colors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Ledger (${ledgerEntries.size})",
                            color = BillProTheme.colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = if (activeTab == 1) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                }
            }

            // TAB 0: PRODUCT CATALOG
            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Search Bar and Add Button Header
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
                                    placeholder = "Search product, SKU, barcode..."
                                )
                            }

                            Button(
                                onClick = { isAddProductDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BillProTheme.colors.primaryYellow,
                                    contentColor = BillProTheme.colors.primaryText
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Filter Chips
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == "ALL",
                                    onClick = { selectedFilter = "ALL" },
                                    label = { Text("All Products (${products.size})") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "LOW_STOCK",
                                    onClick = { selectedFilter = "LOW_STOCK" },
                                    label = { Text("Low Stock ($lowStockCount)") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "OUT_OF_STOCK",
                                    onClick = { selectedFilter = "OUT_OF_STOCK" },
                                    label = { Text("Out of Stock") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Product List
                    if (filteredProducts.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = if (searchQuery.isNotEmpty()) "No Matching Products" else "Empty Inventory",
                                subtitle = "Add your business items with selling price, stock quantities, and GST rates.",
                                actionLabel = "Add Product",
                                onActionClick = { isAddProductDialogOpen = true },
                                icon = Icons.Default.Inventory2
                            )
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ModernProductCard(
                                product = product,
                                onEdit = { productToEdit = product },
                                onAdjustStock = {
                                    movementProductTarget = product
                                    isAddMovementDialogOpen = true
                                },
                                onViewLedger = { productForLedgerHistory = product },
                                onDelete = { productToDelete = product }
                            )
                        }
                    }
                }
            } else {
                // TAB 1: INVENTORY STOCK LEDGER
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Summary KPIs
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0))
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF24A36A), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restocked In", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "+${formatStock(totalRestockUnits)}",
                                        color = Color(0xFF24A36A),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0))
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFE34B4B), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Outflow / Sold", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "-${formatStock(totalOutflowUnits)}",
                                        color = Color(0xFFE34B4B),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0))
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BillProTheme.colors.primaryText, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Movements", color = BillProTheme.colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "${ledgerEntries.size}",
                                        color = BillProTheme.colors.primaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Ledger Search & Record Button
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SearchBar(
                                    query = ledgerSearchQuery,
                                    onQueryChange = { ledgerSearchQuery = it },
                                    placeholder = "Search movement notes..."
                                )
                            }

                            Button(
                                onClick = {
                                    movementProductTarget = null
                                    isAddMovementDialogOpen = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BillProTheme.colors.primaryYellow,
                                    contentColor = BillProTheme.colors.primaryText
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Record", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Ledger Filter Chips
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val filters = listOf(
                                "ALL" to "All",
                                "RESTOCK" to "Restock (In)",
                                "SALE" to "Sales (Out)",
                                "WASTAGE" to "Wastage",
                                "ADJUSTMENT" to "Adjustments",
                                "RETURN" to "Returns"
                            )
                            items(filters) { (key, label) ->
                                FilterChip(
                                    selected = selectedLedgerFilter == key,
                                    onClick = { selectedLedgerFilter = key },
                                    label = { Text(label, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Ledger Records List
                    if (filteredLedgerEntries.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No Movements Logged",
                                subtitle = "Record inventory purchases, sales deductions, or wastage adjustments.",
                                actionLabel = "Record Stock Movement",
                                onActionClick = {
                                    movementProductTarget = null
                                    isAddMovementDialogOpen = true
                                },
                                icon = Icons.Default.SwapVert
                            )
                        }
                    } else {
                        items(filteredLedgerEntries, key = { it.id }) { entry ->
                            ModernLedgerCard(entry = entry)
                        }
                    }
                }
            }
        }

        // Add / Edit Product Dialog
        if (isAddProductDialogOpen || productToEdit != null) {
            ProductFormDialog(
                product = productToEdit,
                businessId = businessId,
                onDismiss = {
                    isAddProductDialogOpen = false
                    productToEdit = null
                },
                onSave = { savedProduct ->
                    coroutineScope.launch {
                        dao.upsertProduct(savedProduct)
                        isAddProductDialogOpen = false
                        productToEdit = null
                        snackbarHostState.showSnackbar("Product saved successfully")
                    }
                }
            )
        }

        // Add Stock Movement Dialog
        if (isAddMovementDialogOpen) {
            AddStockMovementDialog(
                products = products,
                preselectedProduct = movementProductTarget,
                onDismiss = {
                    isAddMovementDialogOpen = false
                    movementProductTarget = null
                },
                onSave = { productId, movementType, quantityChange, unitCostPaise, reference, notes ->
                    coroutineScope.launch {
                        dao.recordStockMovement(
                            productId = productId,
                            businessId = businessId,
                            movementType = movementType,
                            quantityChange = quantityChange,
                            unitCostPaise = unitCostPaise,
                            reference = reference,
                            notes = notes
                        )
                        isAddMovementDialogOpen = false
                        movementProductTarget = null
                        snackbarHostState.showSnackbar("Stock movement logged")
                    }
                }
            )
        }

        // Product Ledger History Dialog
        productForLedgerHistory?.let { product ->
            ProductLedgerHistoryDialog(
                product = product,
                dao = dao,
                businessId = businessId,
                onDismiss = { productForLedgerHistory = null }
            )
        }

        // Delete Confirmation Dialog
        productToDelete?.let { product ->
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                title = { Text("Delete Product", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove '${product.name}' from your inventory?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                dao.deleteProductById(product.id, businessId)
                                productToDelete = null
                                snackbarHostState.showSnackbar("Product removed")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE34B4B))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * ModernProductCard: Rounded white card with yellow highlights and clear stock level pills
 */
@Composable
private fun ModernProductCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit,
    onViewLedger: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.stockQuantity <= product.lowStockThreshold
    val isOutOfStock = product.stockQuantity <= 0.0

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFAF6E5), CircleShape)
                            .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = BillProTheme.colors.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = product.name,
                            color = BillProTheme.colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "SKU: ${if (product.sku.isNotBlank()) product.sku else "—"} • GST: ${product.gstRatePercent}%",
                            color = BillProTheme.colors.secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }

                // Stock Badge
                val (badgeBg, badgeText, badgeColor) = when {
                    isOutOfStock -> Triple(Color(0xFFFFEBEE), "OUT OF STOCK", Color(0xFFE34B4B))
                    isLowStock -> Triple(Color(0xFFFFF3E0), "LOW STOCK", Color(0xFFE99A28))
                    else -> Triple(Color(0xFFE8F5E9), "IN STOCK", Color(0xFF24A36A))
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Price and Available Stock Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAF8F2), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SALE PRICE", fontSize = 10.sp, color = BillProTheme.colors.secondaryText, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatCurrency(product.salePricePaise),
                        color = BillProTheme.colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("STOCK ON HAND", fontSize = 10.sp, color = BillProTheme.colors.secondaryText, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${formatStock(product.stockQuantity)} ${product.unit}",
                        color = if (isLowStock) Color(0xFFE34B4B) else Color(0xFF24A36A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onViewLedger) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("History", fontSize = 12.sp)
                }

                TextButton(onClick = onAdjustStock) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Movement", fontSize = 12.sp)
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = BillProTheme.colors.primaryText, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE34B4B), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * ModernLedgerCard: Clean movement card
 */
@Composable
private fun ModernLedgerCard(entry: InventoryLedgerEntity) {
    val dateString = remember(entry.createdAtEpochMs) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(entry.createdAtEpochMs))
    }

    val isPositive = entry.quantityChange >= 0
    val color = when (entry.movementType) {
        "RESTOCK", "RETURN" -> Color(0xFF24A36A)
        "WASTAGE" -> Color(0xFFE34B4B)
        else -> BillProTheme.colors.primaryText
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
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
                        .size(40.dp)
                        .background(
                            if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (isPositive) Color(0xFF24A36A) else Color(0xFFE34B4B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.productName,
                        color = BillProTheme.colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${entry.movementType} • $dateString",
                        color = BillProTheme.colors.secondaryText,
                        fontSize = 11.sp
                    )
                    if (entry.reference.isNotBlank()) {
                        Text(
                            text = "Ref: ${entry.reference}",
                            color = BillProTheme.colors.mutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isPositive) "+" else ""}${formatStock(entry.quantityChange)}",
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Bal: ${formatStock(entry.balanceAfter)}",
                    color = BillProTheme.colors.secondaryText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ProductFormDialog(
    product: ProductEntity?,
    businessId: String,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }
    var salePriceText by remember { mutableStateOf(product?.let { (it.salePricePaise / 100.0).toString() } ?: "") }
    var purchasePriceText by remember { mutableStateOf(product?.let { (it.purchasePricePaise / 100.0).toString() } ?: "") }
    var stockQuantityText by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "0.0") }
    var lowStockThresholdText by remember { mutableStateOf(product?.lowStockThreshold?.toString() ?: "5.0") }
    var gstRateText by remember { mutableStateOf(product?.gstRatePercent?.toString() ?: "18.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add New Product" else "Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salePriceText,
                        onValueChange = { salePriceText = it },
                        label = { Text("Sale Price (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = purchasePriceText,
                        onValueChange = { purchasePriceText = it },
                        label = { Text("Cost Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockQuantityText,
                        onValueChange = { stockQuantityText = it },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (pcs/kg/box)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gstRateText,
                        onValueChange = { gstRateText = it },
                        label = { Text("GST %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = lowStockThresholdText,
                        onValueChange = { lowStockThresholdText = it },
                        label = { Text("Low Stock Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU / Code (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val salePrice = ((salePriceText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        val purchasePrice = ((purchasePriceText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        val stock = stockQuantityText.toDoubleOrNull() ?: 0.0
                        val threshold = lowStockThresholdText.toDoubleOrNull() ?: 5.0
                        val gst = gstRateText.toDoubleOrNull() ?: 18.0

                        val toSave = (product ?: ProductEntity(businessId = businessId, name = name, salePricePaise = salePrice)).copy(
                            businessId = businessId,
                            name = name,
                            sku = sku,
                            barcode = barcode,
                            unit = unit,
                            salePricePaise = salePrice,
                            purchasePricePaise = purchasePrice,
                            stockQuantity = stock,
                            lowStockThreshold = threshold,
                            gstRatePercent = gst,
                            updatedAtEpochMs = System.currentTimeMillis()
                        )
                        onSave(toSave)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.primaryYellow,
                    contentColor = BillProTheme.colors.primaryText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Product", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStockMovementDialog(
    products: List<ProductEntity>,
    preselectedProduct: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (productId: Long, movementType: String, quantityChange: Double, unitCostPaise: Long, reference: String, notes: String) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(preselectedProduct ?: products.firstOrNull()) }
    var movementType by remember { mutableStateOf("RESTOCK") } // RESTOCK, SALE, WASTAGE, ADJUSTMENT, RETURN
    var quantityText by remember { mutableStateOf("") }
    var unitCostText by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isProductDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Stock Movement", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Product Selection
                ExposedDropdownMenuBox(
                    expanded = isProductDropdownExpanded,
                    onExpandedChange = { isProductDropdownExpanded = !isProductDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: "Select Product",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProductDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isProductDropdownExpanded,
                        onDismissRequest = { isProductDropdownExpanded = false }
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} (Stock: ${formatStock(prod.stockQuantity)})") },
                                onClick = {
                                    selectedProduct = prod
                                    isProductDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Movement Type Tabs
                Text("Movement Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BillProTheme.colors.secondaryText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("RESTOCK" to "+ In", "WASTAGE" to "- Waste", "ADJUSTMENT" to "Adj").forEach { (type, label) ->
                        FilterChip(
                            selected = movementType == type,
                            onClick = { movementType = type },
                            label = { Text(label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity (${selectedProduct?.unit ?: "units"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { unitCostText = it },
                    label = { Text("Unit Cost (₹) (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference / Supplier Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = selectedProduct
                    val qty = quantityText.toDoubleOrNull() ?: 0.0
                    if (prod != null && qty > 0) {
                        val change = if (movementType in listOf("SALE", "WASTAGE")) -qty else qty
                        val unitCost = ((unitCostText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        onSave(prod.id, movementType, change, unitCost, reference, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BillProTheme.colors.primaryYellow,
                    contentColor = BillProTheme.colors.primaryText
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProductLedgerHistoryDialog(
    product: ProductEntity,
    dao: BillProDao,
    businessId: String,
    onDismiss: () -> Unit
) {
    val ledgerHistory by dao.observeProductInventoryLedger(product.id, businessId).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Stock History", fontWeight = FontWeight.Bold)
                Text(product.name, fontSize = 12.sp, color = BillProTheme.colors.secondaryText)
            }
        },
        text = {
            if (ledgerHistory.isEmpty()) {
                Text("No stock movement history recorded for this product yet.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ledgerHistory) { entry ->
                        ModernLedgerCard(entry = entry)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.primaryYellow, contentColor = BillProTheme.colors.primaryText),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

private fun formatCurrency(paise: Long): String {
    val rupees = paise / 100.0
    return "₹ " + String.format(Locale.ENGLISH, "%,.2f", rupees)
}

private fun formatStock(quantity: Double): String {
    return if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", quantity)
    }
}
