package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransferHistoryEntity
import com.example.i18n.LocalWassilStrings
import com.example.model.FileCategory
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.security.SecurityUtils
import com.example.ui.theme.WassilBlueLight
import com.example.ui.theme.WassilBluePrimary
import com.example.ui.theme.WassilErrorBg
import com.example.ui.theme.WassilErrorRed
import com.example.ui.theme.WassilSuccessBg
import com.example.ui.theme.WassilSuccessGreen
import com.example.viewmodel.WassilViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransferHistoryScreen(
    viewModel: WassilViewModel,
    historyItems: List<TransferHistoryEntity>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val strings = LocalWassilStrings.current
    val context = LocalContext.current
    var filterTab by remember { mutableIntStateOf(0) }

    val filteredItems = remember(historyItems, filterTab) {
        when (filterTab) {
            1 -> historyItems.filter { it.direction == TransferDirection.SENT.name }
            2 -> historyItems.filter { it.direction == TransferDirection.RECEIVED.name }
            else -> historyItems
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = WassilBluePrimary
                        )
                    }
                    Text(
                        text = strings.transferHistory,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (historyItems.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearAllHistory() },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.clearHistoryButton, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = filterTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = WassilBluePrimary
        ) {
            Tab(
                selected = filterTab == 0,
                onClick = { filterTab = 0 },
                text = { Text("${strings.historyAllTab} (${historyItems.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = filterTab == 1,
                onClick = { filterTab = 1 },
                text = {
                    val sentCount = historyItems.count { it.direction == TransferDirection.SENT.name }
                    Text("${strings.historySentTab} ($sentCount)", fontWeight = FontWeight.Bold)
                }
            )
            Tab(
                selected = filterTab == 2,
                onClick = { filterTab = 2 },
                text = {
                    val recCount = historyItems.count { it.direction == TransferDirection.RECEIVED.name }
                    Text("${strings.historyReceivedTab} ($recCount)", fontWeight = FontWeight.Bold)
                }
            )
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = WassilBluePrimary.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.emptyHistoryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.emptyHistorySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onInstallApp = { viewModel.installReceivedApk(context, entry) },
                        onOpen = { viewModel.openHistoryFile(context, entry) },
                        onShareAgain = { viewModel.shareHistoryFileAgain(context, entry) },
                        onDelete = { viewModel.deleteHistoryEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: TransferHistoryEntity,
    onInstallApp: () -> Unit,
    onOpen: () -> Unit,
    onShareAgain: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalWassilStrings.current
    val isSent = entry.direction == TransferDirection.SENT.name
    val isApk = entry.fileCategory == FileCategory.APK.name ||
        entry.fileName.endsWith(".apk", ignoreCase = true)
    val dateStr = remember(entry.timestampMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(entry.timestampMillis))
    }
    val isCompleted = entry.status == TransferStatus.COMPLETED.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSent) WassilBlueLight else WassilSuccessBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.Upload else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (isSent) WassilBluePrimary else WassilSuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${SecurityUtils.formatBytes(entry.fileSizeBytes)} • ${entry.peerDeviceName} • $dateStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (isCompleted) WassilSuccessBg else WassilErrorBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Verified else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isCompleted) WassilSuccessGreen else WassilErrorRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCompleted) strings.statusCompleted else strings.statusCorrupted,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCompleted) WassilSuccessGreen else WassilErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!entry.localFilePath.isNullOrBlank()) {
                    if (isApk) {
                        Button(
                            onClick = onInstallApp,
                            colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.installAppButton, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.openFileButton)
                        }
                    }

                    OutlinedButton(
                        onClick = onShareAgain,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.shareFileAgainButton)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = strings.deleteEntryButton,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
