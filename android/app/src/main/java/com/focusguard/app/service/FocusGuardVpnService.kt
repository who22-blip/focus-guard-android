package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusguard.app.MainActivity
import com.focusguard.app.R
import com.focusguard.app.data.FocusGuardPrefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * FocusGuardVpnService establishes a local loopback VPN tunnel that routes ONLY DNS traffic
 * (10.0.0.1 virtual DNS) through AdGuard Family DNS (94.140.14.15) or the user's selected
 * Family DNS provider, while blocking known explicit domains directly on-device.
 * Runs 24/7 in the background via START_STICKY and a specialUse Foreground Service.
 */
class FocusGuardVpnService : VpnService(), Runnable {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile
    private var isRunning = false

    private val localBlockedKeywords = listOf(
        "pornhub", "xvideos", "xnxx", "onlyfans", "chaturbate",
        "xhamster", "redtube", "youporn", "rule34", "hentai", "erome"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectVpn()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        if (!isRunning) {
            isRunning = true
            vpnThread = Thread(this, "FocusGuardVpnThread").apply { start() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system or user.")
        disconnectVpn()
        super.onRevoke()
    }

    override fun run() {
        try {
            val dnsServerIp = FocusGuardPrefs.getFamilyDnsIp(this)
            val builder = Builder()
                .setSession("FOCUS GUARD Family DNS")
                .addAddress("10.0.0.2", 32)
                .addRoute("10.0.0.1", 32) // Intercept only DNS queries routed to virtual DNS 10.0.0.1
                .addDnsServer("10.0.0.1")
                .setBlocking(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            val descriptor = vpnInterface?.fileDescriptor ?: return
            val inputStream = FileInputStream(descriptor)
            val outputStream = FileOutputStream(descriptor)
            val buffer = ByteArray(32767)

            val upstreamAddress = InetAddress.getByName(dnsServerIp)

            while (isRunning) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue

                // Check IPv4 UDP packet to port 53
                val version = (buffer[0].toInt() shr 4) and 0xF
                if (version != 4 || length < 28) continue

                val ihl = (buffer[0].toInt() and 0x0F) * 4
                val protocol = buffer[9].toInt() and 0xFF
                if (protocol != 17 || length < ihl + 8) continue // 17 = UDP

                val destPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
                if (destPort != 53) continue

                val dnsOffset = ihl + 8
                val dnsLength = length - dnsOffset
                val queriedDomain = extractDomainName(buffer, dnsOffset + 12, length)

                if (localBlockedKeywords.any { queriedDomain.contains(it) }) {
                    FocusGuardPrefs.incrementBlockedCount(this)
                    Log.i(TAG, "Sinkholed restricted domain locally: $queriedDomain")
                    // Drop or return NXDOMAIN / 0.0.0.0
                    continue
                }

                // Forward UDP DNS query to Family DNS server over protected socket
                try {
                    val socket = DatagramSocket()
                    protect(socket)
                    socket.soTimeout = 3000

                    val outPacket = DatagramPacket(buffer, dnsOffset, dnsLength, upstreamAddress, 53)
                    socket.send(outPacket)

                    val respBuf = ByteArray(4096)
                    val inPacket = DatagramPacket(respBuf, respBuf.size)
                    socket.receive(inPacket)
                    socket.close()

                    val responseIpPacket = buildUdpIpResponse(
                        requestPacket = buffer,
                        ihl = ihl,
                        dnsPayload = respBuf,
                        dnsLen = inPacket.length
                    )
                    outputStream.write(responseIpPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Upstream Family DNS query failed for $queriedDomain: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VPN loop stopped", e)
        } finally {
            disconnectVpn()
        }
    }

    private fun extractDomainName(packet: ByteArray, start: Int, maxLen: Int): String {
        val sb = StringBuilder()
        var idx = start
        while (idx < maxLen) {
            val len = packet[idx].toInt() and 0xFF
            if (len == 0) break
            idx++
            if (idx + len > maxLen) break
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                sb.append(packet[idx + i].toInt().toChar())
            }
            idx += len
        }
        return sb.toString().lowercase()
    }

    private fun buildUdpIpResponse(
        requestPacket: ByteArray,
        ihl: Int,
        dnsPayload: ByteArray,
        dnsLen: Int
    ): ByteArray {
        val totalLen = 20 + 8 + dnsLen
        val out = ByteBuffer.allocate(totalLen)

        // IPv4 Header (20 bytes)
        out.put(0x45.toByte()) // Version 4, IHL 5
        out.put(0x00.toByte())
        out.putShort(totalLen.toShort())
        out.putShort(0.toShort())
        out.putShort(0x4000.toShort()) // Don't fragment
        out.put(64.toByte()) // TTL
        out.put(17.toByte()) // UDP
        out.putShort(0.toShort()) // Checksum placeholder
        // Swap src and dst IPs
        out.put(requestPacket, 16, 4) // Src = original Dst (10.0.0.1)
        out.put(requestPacket, 12, 4) // Dst = original Src (10.0.0.2)

        // Compute IPv4 header checksum
        val ipHeaderChecksum = computeChecksum(out.array(), 0, 20)
        out.putShort(10, ipHeaderChecksum)

        // UDP Header (8 bytes)
        val origSrcPort = ((requestPacket[ihl].toInt() and 0xFF) shl 8) or (requestPacket[ihl + 1].toInt() and 0xFF)
        val origDstPort = ((requestPacket[ihl + 2].toInt() and 0xFF) shl 8) or (requestPacket[ihl + 3].toInt() and 0xFF)
        out.putShort(origDstPort.toShort()) // Src port = 53
        out.putShort(origSrcPort.toShort()) // Dst port = client port
        out.putShort((8 + dnsLen).toShort())
        out.putShort(0.toShort()) // UDP checksum 0 allowed for IPv4

        out.put(dnsPayload, 0, dnsLen)
        return out.array()
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv().toShort()
    }

    private fun disconnectVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = "focus_guard_vpn_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FOCUS GUARD 24/7 Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val dnsHost = FocusGuardPrefs.getFamilyDnsHost(this)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FOCUS GUARD Active 24/7")
            .setContentText("Family DNS ($dnsHost) blocking adult content in background.")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.focusguard.DISCONNECT"
        const val NOTIFICATION_ID = 1001
        const val TAG = "FocusGuardVpn"
    }
}
