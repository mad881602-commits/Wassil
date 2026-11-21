package com.example.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Handles direct Sender Hotspot / Wi-Fi Direct Group creation and automatic Receiver connection
 * to the Sender's Hotspot right when the Receiver scans the QR code.
 */
object HotspotConnectionManager {

    data class SenderHotspotCredentials(
        val ssid: String,
        val password: String,
        val hostIp: String
    )

    private var localOnlyHotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Attempts to start a direct Wi-Fi P2P group or LocalOnlyHotspot on the Sender phone so the
     * Receiver can auto-connect to the Sender's access point immediately after scanning the QR code.
     */
    @SuppressLint("MissingPermission")
    suspend fun startSenderDirectHotspot(context: Context): SenderHotspotCredentials? = withContext(Dispatchers.Main) {
        // 1. Try Wi-Fi Direct autonomous group (creates DIRECT-xx-PhoneName AP with WPA2 passphrase and IP 192.168.49.1)
        val p2pCreds = tryStartWifiDirectGroup(context)
        if (p2pCreds != null) {
            return@withContext p2pCreds
        }

        // 2. Fallback: Try Android LocalOnlyHotspot (creates local Wi-Fi AP with SSID & password)
        val lohsCreds = tryStartLocalOnlyHotspot(context)
        if (lohsCreds != null) {
            return@withContext lohsCreds
        }

        null
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryStartWifiDirectGroup(context: Context): SenderHotspotCredentials? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            return null
        }
        return withTimeoutOrNull(3500L) {
            val deferred = CompletableDeferred<SenderHotspotCredentials?>()
            try {
                val p2pManager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
                val channel = p2pManager?.initialize(context.applicationContext, Looper.getMainLooper(), null)
                if (p2pManager == null || channel == null) {
                    deferred.complete(null)
                } else {
                    p2pManager.requestGroupInfo(channel) { existingGroup: WifiP2pGroup? ->
                        if (existingGroup != null && existingGroup.isGroupOwner && !existingGroup.networkName.isNullOrBlank()) {
                            deferred.complete(
                                SenderHotspotCredentials(
                                    ssid = existingGroup.networkName,
                                    password = existingGroup.passphrase.orEmpty(),
                                    hostIp = "192.168.49.1"
                                )
                            )
                        } else {
                            p2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        p2pManager.requestGroupInfo(channel) { group ->
                                            if (group != null && !group.networkName.isNullOrBlank()) {
                                                deferred.complete(
                                                    SenderHotspotCredentials(
                                                        ssid = group.networkName,
                                                        password = group.passphrase.orEmpty(),
                                                        hostIp = "192.168.49.1"
                                                    )
                                                )
                                            } else {
                                                deferred.complete(null)
                                            }
                                        }
                                    }, 600L)
                                }

                                override fun onFailure(reason: Int) {
                                    deferred.complete(null)
                                }
                            })
                        }
                    }
                }
            } catch (_: Exception) {
                deferred.complete(null)
            }
            deferred.await()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryStartLocalOnlyHotspot(context: Context): SenderHotspotCredentials? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return withTimeoutOrNull(3500L) {
            val deferred = CompletableDeferred<SenderHotspotCredentials?>()
            try {
                stopLocalOnlyHotspot()
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wm == null) {
                    deferred.complete(null)
                } else {
                    wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                            localOnlyHotspotReservation = reservation
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val softApConfig = reservation?.softApConfiguration
                                val ssid = softApConfig?.ssid.orEmpty()
                                val pass = softApConfig?.passphrase.orEmpty()
                                if (ssid.isNotBlank()) {
                                    deferred.complete(SenderHotspotCredentials(ssid, pass, "192.168.43.1"))
                                    return
                                }
                            }
                            @Suppress("DEPRECATION")
                            val config = reservation?.wifiConfiguration
                            @Suppress("DEPRECATION")
                            val ssid = config?.SSID?.trim('"').orEmpty()
                            @Suppress("DEPRECATION")
                            val pass = config?.preSharedKey?.trim('"').orEmpty()
                            if (ssid.isNotBlank()) {
                                deferred.complete(SenderHotspotCredentials(ssid, pass, "192.168.43.1"))
                            } else {
                                deferred.complete(null)
                            }
                        }

                        override fun onFailed(reason: Int) {
                            deferred.complete(null)
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            } catch (_: Exception) {
                deferred.complete(null)
            }
            deferred.await()
        }
    }

    fun stopSenderDirectHotspot(context: Context) {
        stopLocalOnlyHotspot()
        try {
            val p2pManager = context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            val channel = p2pManager?.initialize(context.applicationContext, Looper.getMainLooper(), null)
            if (p2pManager != null && channel != null) {
                p2pManager.removeGroup(channel, null)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopLocalOnlyHotspot() {
        runCatching { localOnlyHotspotReservation?.close() }
        localOnlyHotspotReservation = null
    }

    /**
     * When the Receiver scans the Sender's QR code:
     * 1. First checks if any candidate IP (or the current Wi-Fi DHCP gateway IP, e.g. when connected to Sender's hotspot)
     *    is already reachable on `tcpPort`.
     * 2. If not yet connected and the QR code contains the Sender's hotspot SSID & password,
     *    automatically connects to that Wi-Fi hotspot using WifiNetworkSpecifier / WifiManager and binds the process to it!
     * 3. Returns the reachable IP address of the Sender.
     */
    suspend fun connectToSenderHotspotAndResolveIp(
        context: Context,
        candidateIps: List<String>,
        tcpPort: Int,
        wifiSsid: String,
        wifiPassword: String
    ): String = withContext(Dispatchers.IO) {
        val allCandidates = buildCandidateIpList(context, candidateIps)

        // Step 1: Fast check if we are already on the same network/hotspot and can reach tcpPort immediately
        for (ip in allCandidates) {
            if (isEndpointReachable(ip, tcpPort, timeoutMs = 1200)) {
                return@withContext ip
            }
        }

        // Step 2: If the QR code has the Sender's Hotspot SSID, connect to that Wi-Fi network directly!
        if (wifiSsid.isNotBlank()) {
            connectToWifiHotspotBySsid(context, wifiSsid, wifiPassword)
        }

        // Step 3: Re-evaluate candidate IPs (including updated DHCP gateway IP from the newly joined hotspot)
        val refreshedCandidates = buildCandidateIpList(context, candidateIps)
        for (ip in refreshedCandidates) {
            if (isEndpointReachable(ip, tcpPort, timeoutMs = 2500)) {
                return@withContext ip
            }
        }

        refreshedCandidates.firstOrNull() ?: "127.0.0.1"
    }

    private fun buildCandidateIpList(context: Context, qrIps: List<String>): List<String> {
        val list = mutableListOf<String>()
        list.addAll(qrIps.filter { it.isNotBlank() })

        // Add current Wi-Fi DHCP gateway IP (this is the Sender's IP when Receiver joins Sender's Hotspot!)
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val gatewayInt = wm?.dhcpInfo?.gateway ?: 0
            if (gatewayInt != 0) {
                val gatewayIp = String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    gatewayInt and 0xff,
                    gatewayInt shr 8 and 0xff,
                    gatewayInt shr 16 and 0xff,
                    gatewayInt shr 24 and 0xff
                )
                if (gatewayIp != "0.0.0.0" && !list.contains(gatewayIp)) {
                    list.add(0, gatewayIp) // Prioritize hotspot gateway IP
                }
            }
        } catch (_: Exception) {
        }

        // Standard Android Hotspot & Wi-Fi Direct group owner IPs
        for (standardHotspotIp in listOf("192.168.49.1", "192.168.43.1")) {
            if (!list.contains(standardHotspotIp)) {
                list.add(standardHotspotIp)
            }
        }
        return list
    }

    private fun isEndpointReachable(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun connectToWifiHotspotBySsid(
        context: Context,
        ssid: String,
        password: String
    ): Boolean = withContext(Dispatchers.Main) {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@withContext false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return@withContext withTimeoutOrNull(12_000L) {
                val deferred = CompletableDeferred<Boolean>()
                try {
                    activeNetworkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
                    val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
                    if (password.isNotBlank()) {
                        specBuilder.setWpa2Passphrase(password)
                    }
                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specBuilder.build())
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            cm.bindProcessToNetwork(network)
                            if (!deferred.isCompleted) deferred.complete(true)
                        }

                        override fun onUnavailable() {
                            if (!deferred.isCompleted) deferred.complete(false)
                        }
                    }
                    activeNetworkCallback = callback
                    cm.requestNetwork(request, callback)
                } catch (_: Exception) {
                    if (!deferred.isCompleted) deferred.complete(false)
                }
                deferred.await()
            } ?: false
        } else {
            return@withContext try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return@withContext false
                if (!wm.isWifiEnabled) wm.isWifiEnabled = true
                val conf = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (password.isNotBlank()) {
                        preSharedKey = "\"$password\""
                    } else {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                val netId = wm.addNetwork(conf)
                if (netId != -1) {
                    wm.disconnect()
                    wm.enableNetwork(netId, true)
                    wm.reconnect()
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun unbindHotspotNetwork(context: Context) {
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.bindProcessToNetwork(null)
            activeNetworkCallback?.let { cm?.unregisterNetworkCallback(it) }
            activeNetworkCallback = null
        } catch (_: Exception) {
        }
    }
}
