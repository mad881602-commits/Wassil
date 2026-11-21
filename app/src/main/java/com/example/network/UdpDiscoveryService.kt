package com.example.network

import com.example.security.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides zero-configuration LAN peer discovery via UDP broadcast + local signaling registry
 * so a 6-digit pairing code resolves directly to the peer's IP, port, and session token.
 */
object UdpDiscoveryService {
    private const val DISCOVERY_UDP_PORT = 49877
    private val discoveredPeers = ConcurrentHashMap<String, SecurityUtils.ParsedQrPayload>()

    fun registerLocalSession(payload: SecurityUtils.ParsedQrPayload) {
        discoveredPeers[payload.pairingCode] = payload
    }

    fun unregisterLocalSession(pairingCode: String) {
        discoveredPeers.remove(pairingCode)
    }

    fun lookupByPairingCode(pairingCode: String): SecurityUtils.ParsedQrPayload? {
        val clean = pairingCode.replace("-", "").replace(" ", "").trim()
        return discoveredPeers[clean]
    }

    fun startBroadcastingSession(
        scope: CoroutineScope,
        qrPayloadString: String,
        parsed: SecurityUtils.ParsedQrPayload
    ): Job {
        registerLocalSession(parsed)
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val bytes = qrPayloadString.toByteArray(Charsets.UTF_8)
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                while (isActive) {
                    runCatching {
                        val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, DISCOVERY_UDP_PORT)
                        socket.send(packet)
                    }
                    delay(1500L)
                }
            } catch (_: Exception) {
                // Ignore if broadcast socket restricted on container interface
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun startListeningForSessions(scope: CoroutineScope): Job {
        return scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_UDP_PORT).apply {
                    broadcast = true
                    soTimeout = 2000
                }
                val buffer = ByteArray(1024)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        SecurityUtils.parseQrPayload(msg)?.let { parsed ->
                            val senderHost = packet.address?.hostAddress
                            val resolved = if (parsed.ip == "127.0.0.1" && !senderHost.isNullOrBlank()) {
                                parsed.copy(ip = senderHost)
                            } else {
                                parsed
                            }
                            discoveredPeers[resolved.pairingCode] = resolved
                        }
                    } catch (_: Exception) {
                        // Timeout tick; continue listening
                    }
                }
            } catch (_: Exception) {
                // Socket port already bound in same process
            } finally {
                runCatching { socket?.close() }
            }
        }
    }
}
