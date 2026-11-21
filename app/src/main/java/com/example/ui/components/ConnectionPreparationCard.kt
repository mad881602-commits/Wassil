package com.example.ui.components

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalWassilStrings
import com.example.ui.theme.WassilBlueDark
import com.example.ui.theme.WassilBlueLight
import com.example.ui.theme.WassilBluePrimary
import com.example.ui.theme.WassilSuccessBg
import com.example.ui.theme.WassilSuccessGreen
import kotlinx.coroutines.delay

/**
 * Interactive Connection Readiness Bar displayed in both SEND and RECEIVE screens.
 * Shows Wi-Fi, Bluetooth, and Hotspot status and opens the corresponding system toggles with one tap.
 */
@Composable
fun ConnectionPreparationCard(
    isSender: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalWassilStrings.current
    val context = LocalContext.current

    var isWifiEnabled by remember { mutableStateOf(checkWifiEnabled(context)) }
    var isBluetoothEnabled by remember { mutableStateOf(checkBluetoothEnabled(context)) }
    var isHotspotEnabled by remember { mutableStateOf(checkHotspotEnabled(context)) }

    // Periodically refresh state while screen is visible so toggles update live when user enables them
    LaunchedEffect(Unit) {
        while (true) {
            isWifiEnabled = checkWifiEnabled(context)
            isBluetoothEnabled = checkBluetoothEnabled(context)
            isHotspotEnabled = checkHotspotEnabled(context)
            delay(1500L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = strings.connectionPrepTitle,
                style = MaterialTheme.typography.titleMedium,
                color = WassilBluePrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSender) strings.connectionPrepSenderSubtitle else strings.connectionPrepReceiverSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConnectionToggleTile(
                    title = strings.wifiLabel,
                    icon = Icons.Default.Wifi,
                    isEnabled = isWifiEnabled,
                    enabledText = strings.statusEnabled,
                    disabledText = strings.statusOpenSettings,
                    onClick = { openWifiSettingsOrPanel(context) },
                    modifier = Modifier.weight(1f)
                )

                ConnectionToggleTile(
                    title = strings.bluetoothLabel,
                    icon = Icons.Default.Bluetooth,
                    isEnabled = isBluetoothEnabled,
                    enabledText = strings.statusEnabled,
                    disabledText = strings.statusOpenSettings,
                    onClick = { openBluetoothSettings(context) },
                    modifier = Modifier.weight(1f)
                )

                ConnectionToggleTile(
                    title = strings.hotspotLabel,
                    icon = Icons.Default.WifiTethering,
                    isEnabled = isHotspotEnabled,
                    enabledText = strings.statusEnabled,
                    disabledText = strings.statusOpenSettings,
                    onClick = { openHotspotSettings(context) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionToggleTile(
    title: String,
    icon: ImageVector,
    isEnabled: Boolean,
    enabledText: String,
    disabledText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isEnabled) WassilSuccessBg.copy(alpha = 0.7f) else WassilBlueLight.copy(alpha = 0.55f)
    val borderColor = if (isEnabled) WassilSuccessGreen.copy(alpha = 0.4f) else WassilBluePrimary.copy(alpha = 0.2f)
    val iconColor = if (isEnabled) WassilSuccessGreen else WassilBluePrimary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = if (isEnabled) WassilSuccessGreen else WassilBluePrimary,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = if (isEnabled) enabledText else disabledText,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

fun checkWifiEnabled(context: Context): Boolean {
    return try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }
}

fun checkBluetoothEnabled(context: Context): Boolean {
    return try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }
}

fun checkHotspotEnabled(context: Context): Boolean {
    return try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        val method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
        method.isAccessible = true
        method.invoke(wm) as? Boolean ?: false
    } catch (_: Exception) {
        false
    }
}

fun openWifiSettingsOrPanel(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

fun openBluetoothSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

fun openHotspotSettings(context: Context) {
    try {
        val intent = Intent("android.settings.TETHER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

/**
 * Ensures Wi-Fi is awake/ready right when the user scans the sender's QR code so it connects and transfers immediately.
 */
fun ensureWifiReadyOnQrScan(context: Context) {
    try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        if (!wm.isWifiEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = true
            } else {
                openWifiSettingsOrPanel(context)
            }
        }
    } catch (_: Exception) {
    }
}
