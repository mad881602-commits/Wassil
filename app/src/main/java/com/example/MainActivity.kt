package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ActiveTransferScreen
import com.example.ui.screens.FileSelectionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendPairingScreen
import com.example.ui.screens.TransferHistoryScreen
import com.example.ui.theme.WassilTheme
import com.example.viewmodel.WassilScreen
import com.example.viewmodel.WassilViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val wassilViewModel: WassilViewModel = viewModel()
            val language by wassilViewModel.language.collectAsStateWithLifecycle()

            WassilTheme(language = language) {
                WassilAppContent(viewModel = wassilViewModel)
            }
        }
    }
}

@Composable
fun WassilAppContent(viewModel: WassilViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val isBusyLoading by viewModel.isBusyLoadingFiles.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val receivedHistoryList by viewModel.receivedHistoryList.collectAsStateWithLifecycle()
    val bannerError by viewModel.bannerError.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                WassilScreen.HOME -> {
                    HomeScreen(
                        deviceName = deviceName,
                        historyCount = historyList.size,
                        onSendClick = { viewModel.navigateTo(WassilScreen.SELECT_FILES) },
                        onReceiveClick = { viewModel.navigateTo(WassilScreen.RECEIVE) },
                        onHistoryClick = { viewModel.navigateTo(WassilScreen.HISTORY) },
                        onUpdateDeviceName = { viewModel.updateDeviceName(it) },
                        onToggleLanguage = { viewModel.toggleLanguage() }
                    )
                }

                WassilScreen.SELECT_FILES -> {
                    FileSelectionScreen(
                        viewModel = viewModel,
                        selectedFiles = selectedFiles,
                        isBusyLoading = isBusyLoading,
                        onBack = { viewModel.navigateTo(WassilScreen.HOME) },
                        onStartSend = { viewModel.startSendingSelectedFiles() }
                    )
                }

                WassilScreen.SEND_PAIRING -> {
                    SendPairingScreen(
                        sessionState = sessionState,
                        onRetryAndResume = { viewModel.retryAndResumeCurrentTransfer() },
                        onCancelSession = { viewModel.abandonSessionAndReturnHome() }
                    )
                }

                WassilScreen.RECEIVE -> {
                    ReceiveScreen(
                        viewModel = viewModel,
                        sessionState = sessionState,
                        receivedItems = receivedHistoryList,
                        onConnectWithCodeOrQr = { codeOrQr, ipPort ->
                            viewModel.connectToSender(codeOrQr, ipPort)
                        },
                        onRetryAndResume = { viewModel.retryAndResumeCurrentTransfer() },
                        onBack = { viewModel.navigateTo(WassilScreen.HOME) }
                    )
                }

                WassilScreen.ACTIVE_TRANSFER -> {
                    ActiveTransferScreen(
                        sessionState = sessionState,
                        onTogglePauseResume = { viewModel.togglePauseResumeTransfer() },
                        onCancelTransfer = { viewModel.cancelCurrentTransferWithRetryOption() },
                        onRetryAndResume = { viewModel.retryAndResumeCurrentTransfer() },
                        onDoneReturnHome = { viewModel.abandonSessionAndReturnHome() }
                    )
                }

                WassilScreen.HISTORY -> {
                    TransferHistoryScreen(
                        viewModel = viewModel,
                        historyItems = historyList,
                        onBack = { viewModel.navigateTo(WassilScreen.HOME) }
                    )
                }
            }

            AnimatedVisibility(
                visible = !bannerError.isNullOrBlank(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissBannerError() }) {
                            Text("حسناً")
                        }
                    }
                }
            }
        }
    }
}
