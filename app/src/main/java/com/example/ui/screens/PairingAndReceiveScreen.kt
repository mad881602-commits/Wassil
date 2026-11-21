package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.TransferHistoryEntity
import com.example.i18n.AppLanguage
import com.example.i18n.LocalWassilStrings
import com.example.model.FileCategory
import com.example.model.TransferSessionOffer
import com.example.model.TransferSessionState
import com.example.qr.QrCodeManager
import com.example.security.SecurityUtils
import com.example.ui.components.ConnectionPreparationCard
import com.example.ui.components.ensureWifiReadyOnQrScan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WassilBlueDark
import com.example.ui.theme.WassilBlueLight
import com.example.ui.theme.WassilBluePrimary
import com.example.ui.theme.WassilSuccessBg
import com.example.ui.theme.WassilSuccessGreen
import com.example.viewmodel.WassilViewModel
import java.util.concurrent.Executors

@Composable
fun SendPairingScreen(
    sessionState: TransferSessionState,
    onRetryAndResume: () -> Unit,
    onCancelSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancelSession)
    val strings = LocalWassilStrings.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancelSession,
                    modifier = Modifier.testTag("cancel_send_session_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = WassilBluePrimary
                    )
                }
                Text(
                    text = strings.waitingForReceiverTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        when (sessionState) {
            is TransferSessionState.SenderWaitingForPeer -> {
                val offer = sessionState.offer
                val qrBitmap = remember(sessionState.qrPayload) {
                    QrCodeManager.generateQrBitmap(sessionState.qrPayload, 640)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Connection Setup Checklist (Wi-Fi, Bluetooth, Hotspot)
                    ConnectionPreparationCard(isSender = true)

                    Surface(
                        color = WassilSuccessBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = WassilSuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = strings.senderHotspotEmbeddedNotice,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WassilSuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                if (offer.hotspotSsid.isNotBlank()) {
                                    Text(
                                        text = "${strings.hotspotLabel}: ${offer.hotspotSsid}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WassilSuccessGreen
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = strings.scanQrOrEnterCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // QR Code & Pairing Code Card
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .size(230.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .testTag("sender_qr_code_image")
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = strings.pairingCodeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = WassilBlueLight,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "${offer.pairingCode.take(3)} - ${offer.pairingCode.takeLast(3)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WassilBluePrimary,
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                        .testTag("sender_pairing_code_text")
                                )
                            }
                        }
                    }
                }
            }

            is TransferSessionState.SenderAwaitingReceiverApproval -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = WassilBluePrimary)
                    Text(
                        text = strings.connectionStatusAwaitingApproval,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = sessionState.receiverDeviceName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = WassilBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is TransferSessionState.InterruptedOrCancelled -> {
                RetryOrExitCard(
                    message = if (strings.language == AppLanguage.ARABIC) sessionState.messageAr else sessionState.messageEn,
                    onRetry = onRetryAndResume,
                    onBack = onCancelSession
                )
            }

            is TransferSessionState.Error -> {
                RetryOrExitCard(
                    message = if (strings.language == AppLanguage.ARABIC) sessionState.messageAr else sessionState.messageEn,
                    onRetry = onRetryAndResume,
                    onBack = onCancelSession
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = WassilBluePrimary)
                }
            }
        }
    }
}

@Composable
fun ReceiveScreen(
    viewModel: WassilViewModel,
    sessionState: TransferSessionState,
    receivedItems: List<TransferHistoryEntity>,
    onConnectWithCodeOrQr: (codeOrQr: String, manualIpPort: String) -> Unit,
    onRetryAndResume: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val strings = LocalWassilStrings.current
    val context = LocalContext.current

    val hasCameraHardware = remember(context) {
        val pm = context.packageManager
        pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) ||
            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    var selectedTab by remember { mutableIntStateOf(if (hasCameraHardware) 0 else 1) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var cameraRuntimeUnavailable by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraPermissionDeniedMessage by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val camGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = camGranted
        cameraPermissionDeniedMessage = !camGranted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("receive_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = WassilBluePrimary
                    )
                }
                Text(
                    text = strings.receiveScreenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        when (sessionState) {
            is TransferSessionState.ReceiverApprovalRequired -> {
                IncomingTransferApprovalCard(
                    offer = sessionState.offer,
                    onAccept = sessionState.onApprove,
                    onReject = sessionState.onReject
                )
            }

            is TransferSessionState.ReceiverConnecting -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = WassilBluePrimary)
                    Text(
                        text = strings.connectionStatusConnecting,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            is TransferSessionState.InterruptedOrCancelled -> {
                RetryOrExitCard(
                    message = if (strings.language == AppLanguage.ARABIC) sessionState.messageAr else sessionState.messageEn,
                    onRetry = onRetryAndResume,
                    onBack = { viewModel.navigateTo(com.example.viewmodel.WassilScreen.RECEIVE) }
                )
            }

            is TransferSessionState.Error -> {
                RetryOrExitCard(
                    message = if (strings.language == AppLanguage.ARABIC) sessionState.messageAr else sessionState.messageEn,
                    onRetry = onRetryAndResume,
                    onBack = { viewModel.navigateTo(com.example.viewmodel.WassilScreen.RECEIVE) }
                )
            }

            else -> {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = WassilBluePrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.scanQrTab, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        modifier = Modifier.testTag("receive_qr_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.manualCodeTab, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Key, contentDescription = null) },
                        modifier = Modifier.testTag("receive_code_tab")
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Connection Setup Checklist (Wi-Fi, Bluetooth, Hotspot)
                    ConnectionPreparationCard(isSender = false)

                    if (selectedTab == 0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            if (!hasCameraHardware || cameraRuntimeUnavailable) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = strings.cameraUnavailableMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { selectedTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = WassilBluePrimary),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("switch_to_pairing_code_button")
                                    ) {
                                        Text(strings.usePairingCodeInsteadButton, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (hasCameraPermission) {
                                LiveCameraQrScanner(
                                    onQrCodeScanned = { scannedPayload ->
                                        ensureWifiReadyOnQrScan(context)
                                        onConnectWithCodeOrQr(scannedPayload, "")
                                    },
                                    onCameraUnavailable = {
                                        cameraRuntimeUnavailable = true
                                    }
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (cameraPermissionDeniedMessage) {
                                            strings.errCameraPermissionDenied
                                        } else {
                                            strings.cameraPermissionRequired
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            val perms = mutableListOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                            }
                                            permissionsLauncher.launch(perms.toTypedArray())
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WassilBluePrimary),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("grant_camera_permission_button")
                                    ) {
                                        Text(strings.grantCameraPermission, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = strings.manualCodeTab,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = pairingCodeInput,
                                    onValueChange = { pairingCodeInput = it },
                                    label = { Text(strings.enterPairingCodeHint) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pairing_code_input")
                                )
                                Button(
                                    onClick = {
                                        ensureWifiReadyOnQrScan(context)
                                        onConnectWithCodeOrQr(pairingCodeInput, "")
                                    },
                                    enabled = pairingCodeInput.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WassilBluePrimary),
                                    contentPadding = PaddingValues(vertical = 14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("connect_to_sender_button")
                                ) {
                                    Text(
                                        text = strings.connectToSenderButton,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Directly Display Received Files & Apps on the Receive Screen
                    ReceivedFilesDirectSection(
                        receivedItems = receivedItems,
                        onInstallApp = { entry -> viewModel.installReceivedApk(context, entry) },
                        onOpenFile = { entry -> viewModel.openHistoryFile(context, entry) },
                        onShareFile = { entry -> viewModel.shareHistoryFileAgain(context, entry) },
                        onDeleteEntry = { entry -> viewModel.deleteHistoryEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedFilesDirectSection(
    receivedItems: List<TransferHistoryEntity>,
    onInstallApp: (TransferHistoryEntity) -> Unit,
    onOpenFile: (TransferHistoryEntity) -> Unit,
    onShareFile: (TransferHistoryEntity) -> Unit,
    onDeleteEntry: (TransferHistoryEntity) -> Unit
) {
    val strings = LocalWassilStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WassilSuccessBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = WassilSuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.receivedFilesDirectlyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (receivedItems.isNotEmpty()) {
                    Surface(
                        color = WassilSuccessGreen,
                        shape = CircleShape
                    ) {
                        Text(
                            text = receivedItems.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (receivedItems.isEmpty()) {
                Text(
                    text = strings.noReceivedFilesYetHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                receivedItems.forEach { entry ->
                    val isApk = entry.fileCategory == FileCategory.APK.name ||
                        entry.fileName.endsWith(".apk", ignoreCase = true)
                    val category = runCatching { FileCategory.valueOf(entry.fileCategory) }
                        .getOrDefault(FileCategory.OTHER)

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isApk) WassilSuccessBg else WassilBlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isApk) Icons.Default.Android else categoryIcon(category),
                                        contentDescription = null,
                                        tint = if (isApk) WassilSuccessGreen else WassilBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${SecurityUtils.formatBytes(entry.fileSizeBytes)} • ${entry.peerDeviceName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteEntry(entry) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = strings.deleteEntryButton,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isApk) {
                                    Button(
                                        onClick = { onInstallApp(entry) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("install_received_apk_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.installAppButton,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { onOpenFile(entry) },
                                        colors = ButtonDefaults.buttonColors(containerColor = WassilBluePrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.openFileButton, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onShareFile(entry) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.shareFileAgainButton)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCameraQrScanner(
    onQrCodeScanned: (String) -> Unit,
    onCameraUnavailable: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraExecutor.shutdown() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val bound = runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val selector = when {
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                                CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else -> null
                        }
                        if (selector == null) {
                            false
                        } else {
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(
                                        cameraExecutor,
                                        QrCodeManager.QrImageAnalyzer { qrText ->
                                            onQrCodeScanned(qrText)
                                        }
                                    )
                                }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageAnalysis
                            )
                            true
                        }
                    }.getOrDefault(false)
                    if (!bound) {
                        onCameraUnavailable()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .border(3.dp, WassilBluePrimary, RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun IncomingTransferApprovalCard(
    offer: TransferSessionOffer,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val strings = LocalWassilStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.incomingTransferTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${strings.fromDeviceLabel}: ${offer.senderDeviceName}",
                style = MaterialTheme.typography.titleMedium,
                color = WassilBluePrimary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.rejectTransferButton, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.acceptTransferButton, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RetryOrExitCard(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalWassilStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("retry_resume_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.retryAndResumeButton, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.back)
            }
        }
    }
}
