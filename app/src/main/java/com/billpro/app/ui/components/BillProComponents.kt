package com.billpro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.billpro.app.ui.BillProTheme

/**
 * 1. BillProTopBar: Signature yellow header inspired by the reference image.
 */
@Composable
fun BillProTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    onStoreProfileClick: (() -> Unit)? = null,
    storeInitial: String = "B",
    hasNotification: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BillProTheme.colors.yellowHeaderGradient)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action (Back arrow or placeholder)
            if (onBackClick != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Back",
                        tint = BillProTheme.colors.primaryText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            // Title
            Text(
                text = title.uppercase(),
                color = BillProTheme.colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                textAlign = TextAlign.Center
            )

            // Right action (Store / Profile icon with notification badge)
            if (onStoreProfileClick != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFE8B623), CircleShape)
                        .clickable { onStoreProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Profile",
                        tint = BillProTheme.colors.primaryText,
                        modifier = Modifier.size(22.dp)
                    )

                    if (hasNotification) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(10.dp)
                                .background(Color(0xFFE34B4B), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(42.dp))
            }
        }
    }
}

/**
 * 2. CircularMetricRing: Circular indicator ring inspired by the reference's "HRS / MIN / SEC" / "1 DAY AGO"
 */
@Composable
fun CircularMetricRing(
    value: String,
    label: String,
    progressFraction: Float = 0.75f,
    strokeColor: Color = BillProTheme.colors.primaryYellow,
    trackColor: Color = Color(0xFFF0EFE9),
    modifier: Modifier = Modifier,
    size: Dp = 68.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.White, CircleShape)
                .border(2.5.dp, strokeColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                color = BillProTheme.colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Text(
            text = label.uppercase(),
            color = BillProTheme.colors.secondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 3. DashboardStatCard: Top rounded white card with Today stats and metric rings
 */
@Composable
fun DashboardStatCard(
    todaySalesFormatted: String,
    todayInvoicesCount: Int,
    lowStockCount: Int,
    inventoryCount: Int,
    onInventoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header pills row: Stats [TODAY] and Inventory [156]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Stats",
                        color = BillProTheme.colors.primaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0xFFF7F5EC),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "TODAY",
                            color = BillProTheme.colors.secondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFFF7F5EC),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { onInventoryClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Inventory",
                            color = BillProTheme.colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = BillProTheme.colors.primaryYellow,
                            shape = CircleShape
                        ) {
                            Text(
                                text = inventoryCount.toString(),
                                color = BillProTheme.colors.primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Metric Rings (e.g. Sales, Bills, Alert)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularMetricRing(
                    value = todayInvoicesCount.toString(),
                    label = "BILLS TODAY",
                    strokeColor = BillProTheme.colors.primaryYellow
                )

                CircularMetricRing(
                    value = if (lowStockCount > 0) lowStockCount.toString() else "0",
                    label = if (lowStockCount > 0) "LOW STOCK" else "ALERTS",
                    strokeColor = if (lowStockCount > 0) Color(0xFFE34B4B) else Color(0xFF24A36A)
                )

                CircularMetricRing(
                    value = inventoryCount.toString(),
                    label = "PRODUCTS",
                    strokeColor = Color(0xFF191919)
                )
            }

            // Big Sales banner inside stats card
            Surface(
                color = Color(0xFFFAF9F5),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "TODAY'S REVENUE",
                            color = BillProTheme.colors.secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = todaySalesFormatted,
                            color = BillProTheme.colors.primaryText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(BillProTheme.colors.primaryYellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = BillProTheme.colors.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. FinancialSummaryCard: 3-column financial snapshot (Revenue, Expenses, Profit)
 */
@Composable
fun FinancialSummaryCard(
    revenueFormatted: String,
    expensesFormatted: String,
    profitFormatted: String,
    periodLabel: String = "This Month",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = periodLabel,
                    color = BillProTheme.colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = BillProTheme.colors.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REVENUE",
                        color = BillProTheme.colors.secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = revenueFormatted,
                        color = BillProTheme.colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EXPENSES",
                        color = Color(0xFFE34B4B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = expensesFormatted,
                        color = Color(0xFFE34B4B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PROFIT",
                        color = Color(0xFF24A36A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = profitFormatted,
                        color = Color(0xFF24A36A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 5. SectionHeader: Title with count badge and optional action link
 */
@Composable
fun SectionHeader(
    title: String,
    badgeCount: Int? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = if (isDarkTheme) Color.White else BillProTheme.colors.primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )

            if (badgeCount != null) {
                Surface(
                    color = if (isDarkTheme) Color(0xFF333333) else Color(0xFFEFECE2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = if (isDarkTheme) Color.White else BillProTheme.colors.primaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel.uppercase(),
                color = BillProTheme.colors.primaryYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onActionClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 6. QuickActionButton: Squircle action button with circular icon
 */
@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) Color(0xFF222222) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF333333) else Color(0xFFEBE8E0)
        ),
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isDark) Color(0xFF2C2C2C) else BillProTheme.colors.primaryYellow,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isDark) BillProTheme.colors.primaryYellow else BillProTheme.colors.primaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                color = if (isDark) Color.White else BillProTheme.colors.primaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 7. SearchBar: Clean rounded search input
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8DF)),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = BillProTheme.colors.secondaryText,
                modifier = Modifier.size(20.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = BillProTheme.colors.mutedText,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = BillProTheme.colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(BillProTheme.colors.primaryYellow),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = BillProTheme.colors.secondaryText,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

/**
 * 8. FloatingBottomNavigation: Floating navigation bar inspired by the reference image
 * Features 5 items with a central elevated circular yellow action.
 */
@Composable
fun FloatingBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCentralActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Floating pill bar
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFECE5)),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item 0: Home
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isSelected = selectedTab == 0,
                    onClick = { onTabSelected(0) }
                )

                // Item 1: Inventory
                BottomNavItem(
                    icon = Icons.Default.Inventory2,
                    label = "Inventory",
                    isSelected = selectedTab == 1,
                    onClick = { onTabSelected(1) }
                )

                // Placeholder gap for center elevated action
                Spacer(modifier = Modifier.width(56.dp))

                // Item 2: Cashbook / Payments
                BottomNavItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "Payments",
                    isSelected = selectedTab == 2,
                    onClick = { onTabSelected(2) }
                )

                // Item 3: Customers & More
                BottomNavItem(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    isSelected = selectedTab == 3,
                    onClick = { onTabSelected(3) }
                )
            }
        }

        // Central elevated yellow circular button (lightning / plus action)
        Box(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .background(BillProTheme.colors.primaryYellow, CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .clickable { onCentralActionClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = "New Bill",
                tint = BillProTheme.colors.primaryText,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFFFAF6E5) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) BillProTheme.colors.primaryText else Color(0xFFA0A0A0),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 9. EmptyStateView: Clean empty state
 */
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.FolderOpen,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE8E0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFFAF8F0), CircleShape)
                    .border(1.5.dp, BillProTheme.colors.primaryYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BillProTheme.colors.primaryText,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = BillProTheme.colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    color = BillProTheme.colors.secondaryText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (actionLabel != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BillProTheme.colors.primaryYellow,
                        contentColor = BillProTheme.colors.primaryText
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 10. LoadingView
 */
@Composable
fun LoadingView(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                color = BillProTheme.colors.primaryYellow,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                color = BillProTheme.colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

