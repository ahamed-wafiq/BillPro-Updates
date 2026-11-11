package com.billpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.auth.AuthService
import com.billpro.app.auth.SessionManager
import com.billpro.app.auth.UserSession
import com.billpro.app.data.BillProDao
import com.billpro.app.data.BillProDatabase
import com.billpro.app.data.CustomerEntity
import com.billpro.app.data.InvoiceEntity
import com.billpro.app.data.InvoiceLineEntity
import java.util.Calendar
import com.billpro.app.data.ProductEntity
import com.billpro.app.ui.*
import com.billpro.app.ui.auth.AuthScreen
import com.billpro.app.ui.components.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = BillProDatabase.getInstance(applicationContext)
        val sessionManager = SessionManager.getInstance(applicationContext)
        val authService = AuthService(applicationContext, database.dao(), sessionManager)

        setContent {
            BillProTheme {
                var activeSession by remember { mutableStateOf(sessionManager.getSession()) }

                if (activeSession == null) {
                    AuthScreen(
                        authService = authService,
                        onAuthSuccess = { session ->
                            activeSession = session
                        }
                    )
                } else {
                    BillProApp(
                        dao = database.dao(),
                        session = activeSession!!,
                        onLogout = {
                            authService.logout()
                            activeSession = null
                        }
                    )
                }
            }
        }
    }
}

enum class Tab(val title: String) {
    Home("Home"),
    Inventory("Inventory"),
    Invoices("Invoices"),
    Cashbook("Payments"),
    Ledger("Customers"),
    Reports("Reports"),
    More("More")
}

@Composable
private fun BillProApp(
    dao: BillProDao,
    session: UserSession,
    onLogout: () -> Unit
) {
    var selected by remember { mutableStateOf(Tab.Home) }
    var shouldOpenCreateBillDirectly by remember { mutableStateOf(false) }
    var shouldOpenAddProductDirectly by remember { mutableStateOf(false) }
    var shouldOpenAddCustomerDirectly by remember { mutableStateOf(false) }
    var isStoreProfileOpen by remember { mutableStateOf(false) }

    // Map selected Tab to Bottom Navigation index:
    // 0: Home, 1: Inventory, 2: Payments/Cashbook, 3: More (or Customers/Reports)
    val bottomNavIndex = when (selected) {
        Tab.Home -> 0
        Tab.Inventory -> 1
        Tab.Cashbook -> 2
        Tab.More, Tab.Ledger, Tab.Reports, Tab.Invoices -> 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BillProTheme.colors.secondaryBackground)
    ) {
        // Main Screen Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 76.dp) // Leave clearance for floating bottom bar
        ) {
            when (selected) {
                Tab.Home -> Dashboard(
                    dao = dao,
                    session = session,
                    onNavigate = { tab -> selected = tab },
                    onQuickCreateBill = {
                        shouldOpenCreateBillDirectly = true
                        selected = Tab.Invoices
                    },
                    onQuickAddProduct = {
                        shouldOpenAddProductDirectly = true
                        selected = Tab.Inventory
                    },
                    onQuickAddCustomer = {
                        shouldOpenAddCustomerDirectly = true
                        selected = Tab.Ledger
                    },
                    onOpenStoreProfile = { isStoreProfileOpen = true }
                )
                Tab.Inventory -> InventoryScreen(
                    dao = dao,
                    businessId = session.businessId,
                    initialOpenAdd = shouldOpenAddProductDirectly,
                    onBackClick = { selected = Tab.Home },
                    onStoreProfileClick = { isStoreProfileOpen = true }
                ).also {
                    shouldOpenAddProductDirectly = false
                }
                Tab.Invoices -> InvoicesScreen(
                    dao = dao,
                    businessId = session.businessId,
                    initialOpenCreate = shouldOpenCreateBillDirectly,
                    onBackClick = { selected = Tab.Home },
                    onStoreProfileClick = { isStoreProfileOpen = true }
                ).also {
                    shouldOpenCreateBillDirectly = false
                }
                Tab.Cashbook -> CashbookScreen(
                    dao = dao,
                    businessId = session.businessId,
                    onBackClick = { selected = Tab.Home },
                    onStoreProfileClick = { isStoreProfileOpen = true },
                    onNavigateToReports = { selected = Tab.Reports }
                )
                Tab.Ledger -> CustomerLedgerScreen(
                    dao = dao,
                    businessId = session.businessId,
                    initialOpenAdd = shouldOpenAddCustomerDirectly,
                    onBackClick = { selected = Tab.Home },
                    onStoreProfileClick = { isStoreProfileOpen = true }
                ).also {
                    shouldOpenAddCustomerDirectly = false
                }
                Tab.Reports -> ReportsScreen(
                    dao = dao,
                    businessId = session.businessId,
                    onBackClick = { selected = Tab.Home },
                    onStoreProfileClick = { isStoreProfileOpen = true }
                )
                Tab.More -> MoreMenuScreen(
                    session = session,
                    onNavigate = { tab -> selected = tab },
                    onOpenStoreProfile = { isStoreProfileOpen = true },
                    onLogout = onLogout
                )
            }
        }

        // Floating Bottom Navigation (Reference Image Style)
        FloatingBottomNavigation(
            selectedTab = bottomNavIndex,
            onTabSelected = { index ->
                selected = when (index) {
                    0 -> Tab.Home
                    1 -> Tab.Inventory
                    2 -> Tab.Cashbook
                    else -> Tab.More
                }
            },
            onCentralActionClick = {
                // Central yellow elevated flash button opens Invoices & Create Bill
                shouldOpenCreateBillDirectly = true
                selected = Tab.Invoices
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Store Settings & Updates Dialog
        if (isStoreProfileOpen) {
            StoreProfileDialog(
                dao = dao,
                businessId = session.businessId,
                ownerName = session.ownerName,
                ownerEmail = session.ownerEmail,
                onDismiss = { isStoreProfileOpen = false },
                onSaveSuccess = { isStoreProfileOpen = false },
                onLogout = onLogout
            )
        }
    }
}

@Composable
private fun Dashboard(
    dao: BillProDao,
    session: UserSession,
    onNavigate: (Tab) -> Unit,
    onQuickCreateBill: () -> Unit,
    onQuickAddProduct: () -> Unit,
    onQuickAddCustomer: () -> Unit,
    onOpenStoreProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayRange = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        start to end
    }

    val todaySalesPaise by dao.observeInvoiceTotalForPeriod(session.businessId, todayRange.first, todayRange.second).collectAsState(initial = 0L)
    val todayInvoicesCount by dao.observeInvoiceCountForPeriod(session.businessId, todayRange.first, todayRange.second).collectAsState(initial = 0)
    val totalSalesPaise by dao.observeTotalSales(session.businessId).collectAsState(initial = 0L)
    val totalInvoicesCount by dao.observeTotalInvoicesCount(session.businessId).collectAsState(initial = 0)
    val pendingPaymentsPaise by dao.observePendingPayments(session.businessId).collectAsState(initial = 0L)
    val lowStockCount by dao.observeLowStockCount(session.businessId).collectAsState(initial = 0)
    val recentInvoices by dao.observeRecentInvoices(session.businessId, limit = 5).collectAsState(initial = emptyList())
    val customerCount by dao.observeCustomerCount(session.businessId).collectAsState(initial = 0)
    val productCount by dao.observeProductCount(session.businessId).collectAsState(initial = 0)

    val totalCashIn by dao.observeTotalCashIn(session.businessId).collectAsState(initial = 0L)
    val totalCashOut by dao.observeTotalCashOut(session.businessId).collectAsState(initial = 0L)
    val netProfitPaise = totalCashIn - totalCashOut

    val coroutineScope = rememberCoroutineScope()
    val isDemoLoaded = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BillProTheme.colors.secondaryBackground)
    ) {
        // 1. Signature Yellow Top Bar inspired by the reference image
        BillProTopBar(
            title = "Dashboard",
            onBackClick = null,
            onStoreProfileClick = onOpenStoreProfile,
            storeInitial = session.businessName.take(1).uppercase(),
            hasNotification = lowStockCount > 0
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 2. White Rounded Dashboard Stat Card with metric rings
            item {
                DashboardStatCard(
                    todaySalesFormatted = formatCurrency(todaySalesPaise),
                    todayInvoicesCount = todayInvoicesCount,
                    lowStockCount = lowStockCount,
                    inventoryCount = productCount,
                    onInventoryClick = { onNavigate(Tab.Inventory) }
                )
            }

            // 3. Categories / Business Snapshot (Reference: Groceries Dark Card & Household White Card)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Categories",
                        badgeCount = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Dark Card: Sales Overview (Groceries style in reference)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = BillProTheme.colors.darkBackground,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { onNavigate(Tab.Invoices) }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(BillProTheme.colors.primaryYellow, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = BillProTheme.colors.primaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = "All Invoices",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    color = Color(0xFF333333),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = BillProTheme.colors.primaryYellow,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                // Subtle progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF333333))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .fillMaxHeight()
                                            .background(BillProTheme.colors.primaryYellow)
                                    )
                                }

                                Text(
                                    text = "$totalInvoicesCount BILLED",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // White Card: Inventory Snapshot (Household Essentials style in reference)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { onNavigate(Tab.Inventory) }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
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

                                Text(
                                    text = "Products",
                                    color = BillProTheme.colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    color = if (lowStockCount > 0) Color(0xFFFDE8E8) else Color(0xFFE6F4EA),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (lowStockCount > 0) "STOCK ALERT" else "HEALTHY",
                                        color = if (lowStockCount > 0) Color(0xFFE34B4B) else Color(0xFF24A36A),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                // Subtle progress bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFEFEFEF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(if (productCount > 0) 0.85f else 0.1f)
                                            .fillMaxHeight()
                                            .background(if (lowStockCount > 0) Color(0xFFE34B4B) else Color(0xFF24A36A))
                                    )
                                }

                                Text(
                                    text = "$productCount IN STOCK",
                                    color = BillProTheme.colors.secondaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Financial Snapshot Card (Revenue, Expenses, Profit)
            item {
                FinancialSummaryCard(
                    revenueFormatted = formatCurrency(totalCashIn),
                    expensesFormatted = formatCurrency(totalCashOut),
                    profitFormatted = formatCurrency(netProfitPaise),
                    periodLabel = "Cashflow Overview"
                )
            }

            // 5. Quick Actions Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Quick Actions",
                        badgeCount = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionButton(
                            label = "New Bill",
                            icon = Icons.Default.AddShoppingCart,
                            modifier = Modifier.weight(1f),
                            onClick = onQuickCreateBill
                        )
                        QuickActionButton(
                            label = "Add Item",
                            icon = Icons.Default.AddBox,
                            modifier = Modifier.weight(1f),
                            onClick = onQuickAddProduct
                        )
                        QuickActionButton(
                            label = "Customer",
                            icon = Icons.Default.PersonAdd,
                            modifier = Modifier.weight(1f),
                            onClick = onQuickAddCustomer
                        )
                        QuickActionButton(
                            label = "Reports",
                            icon = Icons.Default.PieChart,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Tab.Reports) }
                        )
                    }
                }
            }

            // 6. Recent Orders / Invoices Section (Reference Image style)
            item {
                SectionHeader(
                    title = "Recent Orders",
                    badgeCount = recentInvoices.size.takeIf { it > 0 },
                    actionLabel = if (recentInvoices.isNotEmpty()) "SEE ALL" else null,
                    onActionClick = { onNavigate(Tab.Invoices) }
                )
            }

            if (recentInvoices.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No Invoices Yet",
                        subtitle = "Create your first bill or load sample catalog data.",
                        actionLabel = "Create New Bill",
                        onActionClick = onQuickCreateBill,
                        icon = Icons.Default.ReceiptLong
                    )
                }
            } else {
                items(recentInvoices, key = { it.id }) { invoice ->
                    RecentInvoiceRow(
                        invoice = invoice,
                        onClick = { onNavigate(Tab.Invoices) }
                    )
                }
            }

            // 7. Multi-Tenant Isolated Business Status Card
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFAF6E5), CircleShape)
                                .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = BillProTheme.colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Business: ${session.businessName}",
                                color = BillProTheme.colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tenant ID: ${session.businessId} • ${session.ownerEmail}",
                                color = BillProTheme.colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * RecentInvoiceRow: Follows the sleek "Order #U10234 Ali Khan / Completed $56.80" styling in the reference
 */
@Composable
private fun RecentInvoiceRow(
    invoice: InvoiceEntity,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular icon with yellow background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFAF6E5), CircleShape)
                        .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = BillProTheme.colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Order #${invoice.invoiceNumber}",
                        color = BillProTheme.colors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (invoice.notes.isNotBlank()) invoice.notes else "Standard Bill",
                        color = BillProTheme.colors.secondaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.wrapContentWidth(Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status pill
                val (statusColor, containerColor) = when (invoice.status) {
                    "PAID" -> Color(0xFF24A36A) to Color(0xFFE8F5E9)
                    "PARTIAL" -> Color(0xFFE99A28) to Color(0xFFFFF3E0)
                    else -> Color(0xFFE34B4B) to Color(0xFFFFEBEE)
                }

                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = invoice.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = formatCurrency(invoice.totalPaise),
                    color = BillProTheme.colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * MoreMenuScreen: Accessible from bottom navigation item 4.
 * Provides direct access to Invoices, Customers, GST Reports, Business Profile & Updater.
 */
@Composable
private fun MoreMenuScreen(
    session: UserSession,
    onNavigate: (Tab) -> Unit,
    onOpenStoreProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BillProTheme.colors.secondaryBackground)
    ) {
        BillProTopBar(
            title = "Business Hub",
            onBackClick = { onNavigate(Tab.Home) },
            onStoreProfileClick = onOpenStoreProfile,
            storeInitial = session.businessName.take(1).uppercase()
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(BillProTheme.colors.primaryYellow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = session.businessName.take(1).uppercase(),
                                color = BillProTheme.colors.primaryText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.businessName,
                                color = BillProTheme.colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = session.ownerEmail,
                                color = BillProTheme.colors.secondaryText,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = onOpenStoreProfile) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = BillProTheme.colors.primaryText)
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Navigation Hub")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF))
                ) {
                    Column {
                        HubMenuItem(
                            icon = Icons.Default.ReceiptLong,
                            title = "Invoices & Billing History",
                            subtitle = "View, download and share customer invoices",
                            onClick = { onNavigate(Tab.Invoices) }
                        )
                        HorizontalDivider(color = Color(0xFFF0EFEA))
                        HubMenuItem(
                            icon = Icons.Default.People,
                            title = "Customer Ledger",
                            subtitle = "Customer balances, contact info and dues",
                            onClick = { onNavigate(Tab.Ledger) }
                        )
                        HorizontalDivider(color = Color(0xFFF0EFEA))
                        HubMenuItem(
                            icon = Icons.Default.BarChart,
                            title = "Reports & Analytics",
                            subtitle = "Daily, monthly sales and business breakdown",
                            onClick = { onNavigate(Tab.Reports) }
                        )
                        HorizontalDivider(color = Color(0xFFF0EFEA))
                        HubMenuItem(
                            icon = Icons.Default.SystemUpdate,
                            title = "Check for Updates",
                            subtitle = "GitHub In-App Release updates & settings",
                            onClick = onOpenStoreProfile
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFE34B4B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out of BillPro", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun HubMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFFAF6E5), CircleShape)
                .border(1.dp, BillProTheme.colors.primaryYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BillProTheme.colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = BillProTheme.colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = BillProTheme.colors.secondaryText,
                fontSize = 11.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = BillProTheme.colors.secondaryText,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun formatCurrency(paise: Long): String {
    val rupees = paise / 100.0
    return "₹" + String.format(Locale.ENGLISH, "%,.2f", rupees)
}
