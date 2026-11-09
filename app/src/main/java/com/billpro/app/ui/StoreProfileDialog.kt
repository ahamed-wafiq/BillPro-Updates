package com.billpro.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.billpro.app.data.BillProDao
import com.billpro.app.data.DataBackupManager
import com.billpro.app.data.StoreProfile
import com.billpro.app.sync.BusinessSyncManager
import com.billpro.app.sync.SyncStatus
import com.billpro.app.updater.AppUpdateInfo
import com.billpro.app.updater.UpdateManager
import com.billpro.app.updater.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileDialog(
    dao: BillProDao,
    businessId: String = "default_biz",
    ownerName: String = "Business Owner",
    ownerEmail: String = "owner@billpro.local",
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit,
    onLogout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentProfile = remember(businessId) { StoreProfile.load(context, businessId) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Profile, 1: Cloud & Backup, 2: Account

    var storeName by remember { mutableStateOf(currentProfile.storeName) }
    var phone by remember { mutableStateOf(currentProfile.phone) }
    var email by remember { mutableStateOf(currentProfile.email) }
    var address by remember { mutableStateOf(currentProfile.address) }
    var gstin by remember { mutableStateOf(currentProfile.gstin) }
    var terms by remember { mutableStateOf(currentProfile.termsAndConditions) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var currentVerName by remember { mutableStateOf("1.0.0") }
    var currentVerCode by remember { mutableLongStateOf(1L) }

    LaunchedEffect(Unit) {
        currentVerName = UpdateManager.getCurrentVersionName(context)
        currentVerCode = UpdateManager.getCurrentVersionCode(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Business Hub",
                            color = BillProTheme.colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tenant: $businessId",
                            color = BillProTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BillProTheme.colors.surfaceVariant)
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Business ID", businessId))
                                Toast.makeText(context, "Business ID copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BillProTheme.colors.text, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy ID", fontSize = 11.sp, color = BillProTheme.colors.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    color = BillProTheme.colors.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf("Profile", "Sync", "Account", "Updates")
                        tabs.forEachIndexed { index, tabTitle ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) BillProTheme.colors.accent else Color.Transparent)
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) Color(0xFF17130A) else Color(0xFF555555),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            when (selectedTab) {
                0 -> {
                    // TAB 1: STORE PROFILE
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = BillProTheme.colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it; errorMessage = null },
                            label = { Text("Store / Business Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Contact Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = gstin,
                            onValueChange = { gstin = it.uppercase() },
                            label = { Text("GSTIN (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Contact Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Business Address") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = terms,
                            onValueChange = { terms = it },
                            label = { Text("Invoice Terms & Conditions") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                1 -> {
                    // TAB 2: DATA BACKUP & CLOUD SYNC
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cloud Sync Banner & Button
                        Surface(
                            color = BillProTheme.colors.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = BillProTheme.colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cloud Firestore Sync", color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "Synchronize this business's products, customers, and invoices to Firestore cloud storage securely.",
                                    color = BillProTheme.colors.muted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Button(
                                    onClick = {
                                        syncStatus = SyncStatus.Syncing
                                        coroutineScope.launch {
                                            val mgr = BusinessSyncManager(context, dao)
                                            val res = mgr.syncBusinessData(businessId)
                                            syncStatus = res
                                            when (res) {
                                                is SyncStatus.Success -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                                is SyncStatus.Offline -> Toast.makeText(context, res.reason, Toast.LENGTH_LONG).show()
                                                is SyncStatus.Error -> Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
                                                else -> {}
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (syncStatus is SyncStatus.Syncing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Syncing...", fontSize = 12.sp)
                                    } else {
                                        Text("Sync Now with Cloud Firestore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // 1. Export JSON Backup Button
                        Button(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val backupFile = DataBackupManager.exportBackupJson(context, dao, businessId)
                                        DataBackupManager.shareFile(
                                            context = context,
                                            file = backupFile,
                                            mimeType = "application/json",
                                            title = "Share $businessId Backup"
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BillProTheme.colors.cardBackground,
                                contentColor = BillProTheme.colors.accent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Export Business Backup (JSON)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // 2. Export Sales Register CSV
                        Button(
                            onClick = {
                                isExporting = true
                                coroutineScope.launch {
                                    try {
                                        val csvFile = DataBackupManager.exportSalesCsv(context, dao, businessId)
                                        DataBackupManager.shareFile(
                                            context = context,
                                            file = csvFile,
                                            mimeType = "text/csv",
                                            title = "Export $businessId Sales Register"
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "CSV export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BillProTheme.colors.cardBackground,
                                contentColor = BillProTheme.colors.success
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.success),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Export Sales Register (CSV)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (isExporting) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = BillProTheme.colors.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generating export...", color = BillProTheme.colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 3: ACCOUNT & LOGOUT
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = BillProTheme.colors.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Active Business Session", color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Owner: $ownerName", color = BillProTheme.colors.textSecondary, fontSize = 12.sp)
                                Text("Email: $ownerEmail", color = BillProTheme.colors.textSecondary, fontSize = 12.sp)
                                Text("Business ID: $businessId", color = BillProTheme.colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (onLogout != null) {
                            Button(
                                onClick = { showLogoutConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BillProTheme.colors.errorContainer,
                                    contentColor = BillProTheme.colors.error
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.error),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Log Out of Business Account", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // TAB 4: APP UPDATES (GitHub Releases)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current Version Banner
                        Surface(
                            color = BillProTheme.colors.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = BillProTheme.colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Installed BillPro Version", color = BillProTheme.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Version: v$currentVerName (Build $currentVerCode)", color = BillProTheme.colors.textSecondary, fontSize = 12.sp)
                                Text("Release Channel: GitHub Releases (ahamed-wafiq/BillPro-Updates)", color = BillProTheme.colors.muted, fontSize = 11.sp)
                            }
                        }

                        // State-driven Update Flow
                        when (val state = updateState) {
                            is UpdateState.Idle -> {
                                Button(
                                    onClick = {
                                        updateState = UpdateState.Checking
                                        coroutineScope.launch {
                                            val res = UpdateManager.checkForUpdates(context)
                                            res.fold(
                                                onSuccess = { info ->
                                                    updateState = if (info != null) {
                                                        UpdateState.UpdateAvailable(info)
                                                    } else {
                                                        UpdateState.UpToDate(currentVerName)
                                                    }
                                                },
                                                onFailure = { err ->
                                                    updateState = UpdateState.Error(err.message ?: "Failed to check updates")
                                                }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent, contentColor = Color(0xFF17130A)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Check for Updates", fontWeight = FontWeight.Bold)
                                }
                            }
                            is UpdateState.Checking -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(color = BillProTheme.colors.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Checking GitHub for updates...", color = BillProTheme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                            is UpdateState.UpToDate -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BillProTheme.colors.success, modifier = Modifier.size(32.dp))
                                        Text("BillPro is up to date!", color = BillProTheme.colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("You have the latest version (v${state.currentVersion}).", color = BillProTheme.colors.muted, fontSize = 11.sp)
                                        OutlinedButton(
                                            onClick = {
                                                updateState = UpdateState.Checking
                                                coroutineScope.launch {
                                                    val res = UpdateManager.checkForUpdates(context)
                                                    res.fold(
                                                        onSuccess = { info ->
                                                            updateState = if (info != null) UpdateState.UpdateAvailable(info) else UpdateState.UpToDate(currentVerName)
                                                        },
                                                        onFailure = { err ->
                                                            updateState = UpdateState.Error(err.message ?: "Failed to check updates")
                                                        }
                                                    )
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Check Again", fontSize = 11.sp, color = BillProTheme.colors.accent)
                                        }
                                    }
                                }
                            }
                            is UpdateState.UpdateAvailable -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Update Available!", color = BillProTheme.colors.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                            Surface(color = BillProTheme.colors.accent, shape = RoundedCornerShape(6.dp)) { Text(state.info.tagName, color = Color(0xFF17130A), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                        }
                                        Text(state.info.releaseName, color = BillProTheme.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                                        if (state.info.apkSize > 0) {
                                            Text(
                                                "Download Size: %.1f MB".format(state.info.apkSize / (1024.0 * 1024.0)),
                                                color = BillProTheme.colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (!state.info.expectedSha256.isNullOrBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Security, contentDescription = null, tint = BillProTheme.colors.success, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("SHA-256 Verified Release", color = BillProTheme.colors.success, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        Text("Release Notes:", color = BillProTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Surface(
                                            color = BillProTheme.colors.surface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = state.info.releaseNotes,
                                                color = BillProTheme.colors.textSecondary,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    updateState = UpdateState.Downloading(0f, 0L, state.info.apkSize)
                                                    val res = UpdateManager.downloadApk(context, state.info) { progress, downloaded, total ->
                                                        updateState = UpdateState.Downloading(progress, downloaded, total)
                                                    }
                                                    res.fold(
                                                        onSuccess = { apkFile ->
                                                            updateState = UpdateState.ReadyToInstall(apkFile, state.info)
                                                        },
                                                        onFailure = { err ->
                                                            updateState = UpdateState.Error(err.message ?: "Download failed")
                                                        }
                                                    )
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.accent, contentColor = Color(0xFF17130A)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Download & Update", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            is UpdateState.Downloading -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Downloading BillPro.apk...", color = BillProTheme.colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        LinearProgressIndicator(
                                            progress = state.progress,
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                            color = BillProTheme.colors.accent
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${(state.progress * 100).toInt()}%", color = BillProTheme.colors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            if (state.totalBytes > 0) {
                                                Text(
                                                    "%.1f MB / %.1f MB".format(state.downloadedBytes / (1024.0 * 1024.0), state.totalBytes / (1024.0 * 1024.0)),
                                                    color = BillProTheme.colors.muted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is UpdateState.VerifyingChecksum -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(color = BillProTheme.colors.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Verifying SHA-256 checksum...", color = BillProTheme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                            is UpdateState.ReadyToInstall -> {
                                Surface(
                                    color = BillProTheme.colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BillProTheme.colors.success),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BillProTheme.colors.success, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Update Ready to Install!", color = BillProTheme.colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text("Version: ${state.info.tagName} • Verified SHA-256", color = BillProTheme.colors.textSecondary, fontSize = 11.sp)

                                        if (!UpdateManager.canRequestPackageInstalls(context)) {
                                            Surface(
                                                color = BillProTheme.colors.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        "Android permission required to install updates from BillPro.",
                                                        color = BillProTheme.colors.textSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                    Button(
                                                        onClick = { UpdateManager.openInstallPermissionSettings(context) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.surfaceVariant, contentColor = BillProTheme.colors.accent),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("Grant Install Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                UpdateManager.installApk(context, state.apkFile)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.success, contentColor = Color.White),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(46.dp)
                                        ) {
                                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Install Update Now", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            is UpdateState.Error -> {
                                Surface(
                                    color = BillProTheme.colors.errorContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BillProTheme.colors.error, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Update Check Failed", color = BillProTheme.colors.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Text(state.message, color = BillProTheme.colors.error, fontSize = 11.sp)
                                        if (state.canRetry) {
                                            OutlinedButton(
                                                onClick = {
                                                    updateState = UpdateState.Checking
                                                    coroutineScope.launch {
                                                        val res = UpdateManager.checkForUpdates(context)
                                                        res.fold(
                                                            onSuccess = { info ->
                                                                updateState = if (info != null) UpdateState.UpdateAvailable(info) else UpdateState.UpToDate(currentVerName)
                                                            },
                                                            onFailure = { err ->
                                                                updateState = UpdateState.Error(err.message ?: "Failed to check updates")
                                                            }
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Retry", fontSize = 11.sp, color = BillProTheme.colors.error, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        if (storeName.isBlank()) {
                            errorMessage = "Business name cannot be empty"
                            return@Button
                        }
                        val updated = StoreProfile(
                            storeName = storeName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            gstin = gstin.trim(),
                            termsAndConditions = terms.trim()
                        )
                        StoreProfile.save(context, updated, businessId)
                        onSaveSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BillProTheme.colors.accent,
                        contentColor = Color(0xFF17130A)
                    )
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = BillProTheme.colors.text, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BillProTheme.colors.text, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = BillProTheme.colors.surface,
        shape = RoundedCornerShape(18.dp)
    )

    if (showLogoutConfirm && onLogout != null) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log Out Confirmation", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to log out of $storeName ($businessId)? Your business data is safely stored.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onDismiss()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BillProTheme.colors.error)
                ) {
                    Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = BillProTheme.colors.text, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
