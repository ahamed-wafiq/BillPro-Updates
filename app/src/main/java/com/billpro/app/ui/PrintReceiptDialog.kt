package com.billpro.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.billpro.app.data.CustomerEntity
import com.billpro.app.data.InvoiceEntity
import com.billpro.app.data.InvoiceLineEntity
import com.billpro.app.data.StoreProfile
import com.billpro.app.printing.BluetoothPrinterDevice
import com.billpro.app.printing.BluetoothThermalPrinter
import com.billpro.app.printing.EscPosFormatter
import com.billpro.app.printing.InvoicePdfGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PrintMode {
    BLUETOOTH,
    SYSTEM
}

@Composable
fun PrintReceiptDialog(
    invoice: InvoiceEntity,
    lines: List<InvoiceLineEntity>,
    customer: CustomerEntity?,
    store: StoreProfile,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var printMode by remember { mutableStateOf(PrintMode.BLUETOOTH) }
    var bondedPrinters by remember { mutableStateOf(listOf<BluetoothPrinterDevice>()) }
    var selectedPrinter by remember { mutableStateOf<BluetoothPrinterDevice?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasBtPermission by remember { mutableStateOf(BluetoothThermalPrinter.hasBluetoothPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBtPermission = isGranted
        if (isGranted) {
            val list = BluetoothThermalPrinter.getBondedPrinters(context)
            bondedPrinters = list
            val last = BluetoothThermalPrinter.getLastPrinterAddress(context)
            selectedPrinter = list.firstOrNull { it.address == last } ?: list.firstOrNull()
        } else {
            errorMessage = "Bluetooth permission is required to connect to your 80mm thermal printer."
        }
    }

    fun refreshPrinters() {
        if (!hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val list = BluetoothThermalPrinter.getBondedPrinters(context)
        bondedPrinters = list
        val last = BluetoothThermalPrinter.getLastPrinterAddress(context)
        selectedPrinter = list.firstOrNull { it.address == last } ?: list.firstOrNull()
    }

    LaunchedEffect(Unit) {
        if (hasBtPermission) {
            refreshPrinters()
        }
    }

    val dateFormatted = remember(invoice.createdAtEpochMs) {
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(invoice.createdAtEpochMs))
    }

    val customerNameDisplay = customer?.name?.takeIf { it.isNotBlank() } ?: "Walk-in Customer"

    Dialog(
        onDismissRequest = { if (!isPrinting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFFF7F6F2),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFFC928), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = Color(0xFF191919),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "80mm Receipt Print",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191919)
                            )
                            Text(
                                text = "In-App Preview & Printer Selection",
                                fontSize = 11.sp,
                                color = Color(0xFF777777)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isPrinting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF191919)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Scrollable Content: Receipt Preview + Printer Options
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Visual Receipt Card (Simulating standard 80mm roll)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2DFD6)),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Store Header
                            Text(
                                text = store.storeName.ifBlank { "BillPro Store" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191919),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (store.address.isNotBlank()) {
                                Text(
                                    text = store.address,
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (store.phone.isNotBlank()) {
                                Text(
                                    text = "Tel: ${store.phone}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            ReceiptDashedDivider()

                            // Invoice & Customer Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Inv: ${invoice.invoiceNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF191919)
                                )
                                Text(
                                    text = dateFormatted,
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                            Text(
                                text = "Customer: $customerNameDisplay",
                                fontSize = 11.sp,
                                color = Color(0xFF191919)
                            )

                            ReceiptDashedDivider()

                            // Column Headers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ITEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191919), modifier = Modifier.weight(2f))
                                Text("QTY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191919), textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                                Text("RATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191919), textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                Text("AMOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191919), textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                            }

                            ReceiptDashedDivider()

                            // Line Items
                            lines.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = line.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFF191919),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(2f)
                                    )
                                    val qtyStr = if (line.quantity % 1.0 == 0.0) line.quantity.toInt().toString() else "%.1f".format(line.quantity)
                                    Text(
                                        text = qtyStr,
                                        fontSize = 11.sp,
                                        color = Color(0xFF555555),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    val ratePaise = if (line.quantity > 0) (line.lineTotalPaise / line.quantity).toLong() else line.lineTotalPaise
                                    Text(
                                        text = "₹${formatRupees(ratePaise)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF555555),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        text = "₹${formatRupees(line.lineTotalPaise)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF191919),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                }
                            }

                            ReceiptDashedDivider()

                            // Financial Summary
                            ReceiptSummaryRow(label = "Subtotal", value = "₹${formatRupees(invoice.subtotalPaise)}")
                            if (invoice.discountPaise > 0) {
                                ReceiptSummaryRow(label = "Discount", value = "- ₹${formatRupees(invoice.discountPaise)}", isDiscount = true)
                            }
                            if (invoice.previousBalancePaise > 0) {
                                ReceiptSummaryRow(label = "Previous Balance", value = "+ ₹${formatRupees(invoice.previousBalancePaise)}")
                            }
                            ReceiptSummaryRow(
                                label = "NET TOTAL",
                                value = "₹${formatRupees(invoice.totalPaise)}",
                                isBold = true,
                                isTotal = true
                            )
                            ReceiptSummaryRow(label = "Amount Paid", value = "₹${formatRupees(invoice.paidPaise)}")
                            val balanceDue = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
                            if (balanceDue > 0) {
                                ReceiptSummaryRow(label = "Balance Due", value = "₹${formatRupees(balanceDue)}", isDue = true)
                            }

                            ReceiptDashedDivider()

                            Text(
                                text = "Thank you for your business!",
                                fontSize = 11.sp,
                                color = Color(0xFF777777),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Print Mode Selector Tabs
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2DFD6))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "PRINTER SETTINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF777777),
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1EFE9), RoundedCornerShape(8.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SegmentedTab(
                                    title = "Bluetooth POS (80mm)",
                                    selected = printMode == PrintMode.BLUETOOTH,
                                    onClick = {
                                        printMode = PrintMode.BLUETOOTH
                                        refreshPrinters()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SegmentedTab(
                                    title = "System / WiFi Print",
                                    selected = printMode == PrintMode.SYSTEM,
                                    onClick = { printMode = PrintMode.SYSTEM },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (printMode == PrintMode.BLUETOOTH) {
                                if (!hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC928), contentColor = Color(0xFF191919)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Grant Bluetooth Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (!BluetoothThermalPrinter.isBluetoothEnabled(context)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE99A28), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Bluetooth is turned OFF. Please turn on Bluetooth in phone settings.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFB26A00)
                                        )
                                    }
                                } else if (bondedPrinters.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "No paired Bluetooth printers found.",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8D6E63)
                                        )
                                        Text(
                                            "Pair your 80mm thermal printer in Phone Settings > Bluetooth first, then tap Refresh.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6D4C41)
                                        )
                                        OutlinedButton(
                                            onClick = { refreshPrinters() },
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Refresh List", fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    // Device selection list
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Select Paired Printer:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF191919)
                                        )
                                        TextButton(onClick = { refreshPrinters() }) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Refresh", fontSize = 11.sp)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        bondedPrinters.forEach { device ->
                                            val isSelected = selectedPrinter?.address == device.address
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) Color(0xFFFFF9E6) else Color(0xFFF9F9F8),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFFFC928) else Color(0xFFE5E2D9)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedPrinter = device }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Print,
                                                            contentDescription = null,
                                                            tint = if (isSelected) Color(0xFF191919) else Color(0xFF777777),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = device.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                color = Color(0xFF191919)
                                                            )
                                                            Text(
                                                                text = device.address,
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF888888)
                                                            )
                                                        }
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = Color(0xFF24A36A),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // System Print explanation
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF4F6F8), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "System Print Spooler (80mm Roll)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF191919)
                                    )
                                    Text(
                                        text = "Prints the 80mm receipt document using Android system print services (USB, WiFi, Network printers, or Save as PDF).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }
                        }
                    }

                    // Error banner if any
                    if (errorMessage != null) {
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
                                Text(
                                    text = errorMessage!!,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB71C1C),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Action Buttons: Cancel and Final "PRINT RECEIPT"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isPrinting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = Color(0xFF191919))
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            if (printMode == PrintMode.BLUETOOTH) {
                                val printer = selectedPrinter
                                if (printer == null) {
                                    errorMessage = "Please select a paired Bluetooth printer above."
                                    return@Button
                                }
                                isPrinting = true
                                coroutineScope.launch {
                                    val formattedLines = lines.map {
                                        Triple(it.description, it.quantity, it.lineTotalPaise)
                                    }
                                    val escBytes = EscPosFormatter.receipt80mm(
                                        storeName = store.storeName.ifBlank { "BillPro Store" },
                                        storeAddress = store.address,
                                        storePhone = store.phone,
                                        invoiceNo = invoice.invoiceNumber,
                                        dateStr = dateFormatted,
                                        customerName = customerNameDisplay,
                                        lines = formattedLines,
                                        subtotalPaise = invoice.subtotalPaise,
                                        discountPaise = invoice.discountPaise,
                                        previousBalancePaise = invoice.previousBalancePaise,
                                        totalPaise = invoice.totalPaise,
                                        paidPaise = invoice.paidPaise
                                    )

                                    val result = BluetoothThermalPrinter.printEscPos(
                                        context = context,
                                        address = printer.address,
                                        bytes = escBytes
                                    )

                                    isPrinting = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Receipt sent to ${printer.name} successfully!", Toast.LENGTH_LONG).show()
                                        onPrintSuccess()
                                    } else {
                                        val ex = result.exceptionOrNull()
                                        errorMessage = "Printing failed: ${ex?.message ?: "Could not connect to printer"}. Make sure printer is ON and within range."
                                    }
                                }
                            } else {
                                // System Print Spooler
                                InvoicePdfGenerator.printInvoice80mm(
                                    context = context,
                                    invoice = invoice,
                                    lines = lines,
                                    customer = customer,
                                    store = store
                                )
                                onPrintSuccess()
                            }
                        },
                        enabled = !isPrinting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFC928),
                            contentColor = Color(0xFF191919)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(48.dp)
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF191919),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Printing...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("PRINT RECEIPT", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF191919) else Color(0xFF666666)
            )
        }
    }
}

@Composable
private fun ReceiptDashedDivider() {
    Text(
        text = "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
        fontSize = 10.sp,
        color = Color(0xFFB0AAA0),
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ReceiptSummaryRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isTotal: Boolean = false,
    isDiscount: Boolean = false,
    isDue: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 13.sp else 11.sp,
            fontWeight = if (isBold || isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isDue) Color(0xFFE99A28) else Color(0xFF191919)
        )
        Text(
            text = value,
            fontSize = if (isTotal) 14.sp else 11.sp,
            fontWeight = if (isBold || isTotal) FontWeight.ExtraBold else FontWeight.Normal,
            color = when {
                isDiscount -> Color(0xFF24A36A)
                isDue -> Color(0xFFE99A28)
                isTotal -> Color(0xFF191919)
                else -> Color(0xFF191919)
            }
        )
    }
}

private fun formatRupees(paise: Long): String {
    return String.format(Locale.ENGLISH, "%,.2f", paise / 100.0)
}

