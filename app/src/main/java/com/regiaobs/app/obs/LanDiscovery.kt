package com.regiaobs.app.obs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class DiscoveredObsHost(
    val host: String,
    val port: Int,
    val label: String = "OBS Studio ($host)"
)

data class LanScanProgress(
    val scanned: Int,
    val total: Int,
    val found: List<DiscoveredObsHost>,
    val isFinished: Boolean
)

object LanDiscovery {
    fun scanLocalSubnet(port: Int = 4455): Flow<LanScanProgress> = flow {
        val prefixes = localIpv4Prefixes()
        if (prefixes.isEmpty()) {
            emit(LanScanProgress(0, 0, emptyList(), isFinished = true))
            return@flow
        }

        val candidates = prefixes.flatMap { prefix ->
            (1..254).map { lastOctet -> "$prefix.$lastOctet" }
        }.distinct()

        val found = mutableListOf<DiscoveredObsHost>()
        var scanned = 0
        val total = candidates.size
        val semaphore = Semaphore(40)

        emit(LanScanProgress(0, total, emptyList(), isFinished = false))

        // Process in chunks so UI receives incremental progress cleanly
        val chunkSize = 32
        for (chunk in candidates.chunked(chunkSize)) {
            val batchMatches = coroutineScope {
                chunk.map { ip ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (probePort(ip, port, timeoutMs = 350)) {
                                DiscoveredObsHost(host = ip, port = port)
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            scanned += chunk.size
            if (batchMatches.isNotEmpty()) {
                found.addAll(batchMatches)
            }
            emit(
                LanScanProgress(
                    scanned = scanned,
                    total = total,
                    found = found.toList(),
                    isFinished = scanned >= total
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun probePort(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun localIpv4Prefixes(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("tun") || name.startsWith("dummy")) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val parts = addr.hostAddress?.split('.') ?: continue
                        if (parts.size == 4) {
                            result += "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
        }
        return result.distinct()
    }
}
