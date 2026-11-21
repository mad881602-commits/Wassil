package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferHistoryDatabase
import com.example.data.local.TransferHistoryEntity
import com.example.data.local.TransferHistoryRepository
import com.example.data.preferences.DevicePreferences
import com.example.files.FileSelectionManager
import com.example.i18n.AppLanguage
import com.example.model.FileCategory
import com.example.model.InstalledAppInfo
import com.example.model.ShareableFile
import com.example.model.TransferDirection
import com.example.model.TransferSessionState
import com.example.network.DirectP2PTransferEngine
import com.example.network.UdpDiscoveryService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class WassilScreen {
    HOME,
    SELECT_FILES,
    SEND_PAIRING,
    RECEIVE,
    ACTIVE_TRANSFER,
    HISTORY
}

class WassilViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TransferHistoryDatabase.getInstance(application)
    val historyRepository = TransferHistoryRepository(database.historyDao())
    val devicePreferences = DevicePreferences(application)
    val fileSelectionManager = FileSelectionManager(application)
    val transferEngine = DirectP2PTransferEngine(fileSelectionManager, historyRepository)

    val language: StateFlow<AppLanguage> = devicePreferences.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLanguage.ARABIC
    )

    val deviceName: StateFlow<String> = devicePreferences.deviceNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevicePreferences.defaultDeviceName()
    )

    val historyList: StateFlow<List<TransferHistoryEntity>> = historyRepository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val receivedHistoryList: StateFlow<List<TransferHistoryEntity>> = historyRepository.allHistory
        .map { list -> list.filter { it.direction == TransferDirection.RECEIVED.name } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sessionState: StateFlow<TransferSessionState> = transferEngine.sessionState

    private val _currentScreen = MutableStateFlow(WassilScreen.HOME)
    val currentScreen: StateFlow<WassilScreen> = _currentScreen.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
    val selectedFiles: StateFlow<List<ShareableFile>> = _selectedFiles.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingInstalledApps = MutableStateFlow(false)
    val isLoadingInstalledApps: StateFlow<Boolean> = _isLoadingInstalledApps.asStateFlow()

    private val _isBusyLoadingFiles = MutableStateFlow(false)
    val isBusyLoadingFiles: StateFlow<Boolean> = _isBusyLoadingFiles.asStateFlow()

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    init {
        UdpDiscoveryService.startListeningForSessions(viewModelScope)
        viewModelScope.launch {
            transferEngine.sessionState.collect { state ->
                when (state) {
                    is TransferSessionState.Transferring,
                    is TransferSessionState.InterruptedOrCancelled -> {
                        _currentScreen.value = WassilScreen.ACTIVE_TRANSFER
                    }
                    is TransferSessionState.Completed -> {
                        if (state.direction == TransferDirection.SENT) {
                            _selectedFiles.value = emptyList()
                            _currentScreen.value = WassilScreen.ACTIVE_TRANSFER
                        } else {
                            transferEngine.resetToIdle(getApplication())
                            _currentScreen.value = WassilScreen.RECEIVE
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun navigateTo(screen: WassilScreen) {
        _bannerError.value = null
        when (screen) {
            WassilScreen.HOME -> {
                val st = sessionState.value
                if (st !is TransferSessionState.Transferring && st !is TransferSessionState.InterruptedOrCancelled) {
                    transferEngine.resetToIdle(getApplication())
                }
                _currentScreen.value = WassilScreen.HOME
            }
            WassilScreen.RECEIVE -> {
                transferEngine.openReceiverScanner()
                _currentScreen.value = WassilScreen.RECEIVE
            }
            else -> {
                _currentScreen.value = screen
            }
        }
    }

    fun toggleLanguage() {
        val next = if (language.value == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
        viewModelScope.launch {
            devicePreferences.updateLanguage(next)
        }
    }

    fun updateDeviceName(newName: String) {
        viewModelScope.launch {
            devicePreferences.updateDeviceName(newName)
        }
    }

    fun dismissBannerError() {
        _bannerError.value = null
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _isLoadingInstalledApps.value) return
        viewModelScope.launch {
            _isLoadingInstalledApps.value = true
            try {
                _installedApps.value = fileSelectionManager.getInstalledApps()
            } finally {
                _isLoadingInstalledApps.value = false
            }
        }
    }

    fun addInstalledAppsToSelection(apps: List<InstalledAppInfo>) {
        if (apps.isEmpty()) return
        viewModelScope.launch {
            _isBusyLoadingFiles.value = true
            try {
                val resolved = mutableListOf<ShareableFile>()
                for (app in apps) {
                    val shareable = fileSelectionManager.resolveInstalledAppToShareable(app)
                    if (shareable != null) {
                        resolved.add(shareable)
                    }
                }
                mergeSelectedFiles(resolved)
            } finally {
                _isBusyLoadingFiles.value = false
            }
        }
    }

    fun addPickedUris(uris: List<Uri>, overrideCategory: FileCategory? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusyLoadingFiles.value = true
            try {
                val resolved = mutableListOf<ShareableFile>()
                for (uri in uris) {
                    val file = fileSelectionManager.resolveUriToShareableFile(uri, overrideCategory)
                    if (file != null) {
                        resolved.add(file)
                    }
                }
                if (resolved.isEmpty()) {
                    _bannerError.value = "تعذر فتح الملف المحدد."
                } else {
                    mergeSelectedFiles(resolved)
                }
            } finally {
                _isBusyLoadingFiles.value = false
            }
        }
    }

    private fun mergeSelectedFiles(newFiles: List<ShareableFile>) {
        val existing = _selectedFiles.value.toMutableList()
        for (f in newFiles) {
            if (existing.none { it.sha256 == f.sha256 && it.name == f.name }) {
                existing.add(f)
            }
        }
        _selectedFiles.value = existing
    }

    fun removeSelectedFile(id: String) {
        _selectedFiles.value = _selectedFiles.value.filterNot { it.id == id }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun startSendingSelectedFiles() {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            _bannerError.value = "يرجى اختيار تطبيق أو ملف واحد على الأقل للإرسال."
            return
        }
        _selectedFiles.value = emptyList()
        _currentScreen.value = WassilScreen.SEND_PAIRING
        transferEngine.startSenderSession(
            scope = viewModelScope,
            senderDeviceName = deviceName.value,
            files = files,
            appContext = getApplication()
        )
    }

    fun connectToSender(codeOrQr: String, manualIpPort: String = "") {
        transferEngine.connectAsReceiver(
            scope = viewModelScope,
            receiverDeviceName = deviceName.value,
            codeOrQrPayload = codeOrQr,
            manualIpPort = manualIpPort,
            appContext = getApplication()
        )
    }

    fun togglePauseResumeTransfer() {
        transferEngine.togglePauseResume()
    }

    /**
     * When user taps Cancel during an active transfer, pause/stop at the current byte offset
     * and show the Retry & Resume screen so they can resume without starting over!
     */
    fun cancelCurrentTransferWithRetryOption() {
        transferEngine.cancelTransferWithRetryOption()
    }

    /**
     * Resumes the cancelled or interrupted transfer from the exact byte offset where it stopped.
     */
    fun retryAndResumeCurrentTransfer() {
        transferEngine.retryAndResumeTransfer(
            scope = viewModelScope,
            appContext = getApplication()
        )
    }

    /**
     * Completely ends the session and returns to Home.
     */
    fun abandonSessionAndReturnHome() {
        transferEngine.cancelActiveSession(getApplication())
        _currentScreen.value = WassilScreen.HOME
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            historyRepository.removeEntry(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun installReceivedApk(context: Context, entry: TransferHistoryEntity) {
        val path = entry.localFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(context, "ملف التطبيق غير متوفر", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "لم يتم العثور على ملف التطبيق في الذاكرة", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
            if (!canInstall) {
                Toast.makeText(
                    context,
                    "يرجى السماح بتثبيت التطبيقات من هذا المصدر ثم الضغط على تثبيت",
                    Toast.LENGTH_LONG
                ).show()
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(permIntent) }
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر تشغيل مثبت الحزم لهذا التطبيق", Toast.LENGTH_SHORT).show()
        }
    }

    fun openHistoryFile(context: Context, entry: TransferHistoryEntity) {
        val isApk = entry.fileCategory == FileCategory.APK.name ||
            entry.fileName.endsWith(".apk", ignoreCase = true)
        if (isApk) {
            installReceivedApk(context, entry)
            return
        }

        val path = entry.localFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(context, "الملف غير متوفر محلياً", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "تم نقل أو حذف الملف من الذاكرة", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entry.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, entry.fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "لا يوجد تطبيق لفتح هذا النوع من الملفات (${entry.fileName})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shareHistoryFileAgain(context: Context, entry: TransferHistoryEntity) {
        val path = entry.localFilePath
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود في الذاكرة", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = entry.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, entry.fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر مشاركة الملف", Toast.LENGTH_SHORT).show()
        }
    }
}
