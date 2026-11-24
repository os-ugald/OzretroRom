package cl.gakuto.kizuna.hub

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredLanHub(
    val serviceName: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
    val version: String = "2",
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * Discovers Game Vault PC Hub instances advertised on the local network via mDNS (_ozvault._tcp.).
 */
class LanHubDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? =
        runCatching { appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
    private val wifiManager: WifiManager? =
        runCatching { appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()

    private val _discoveredHubs = MutableStateFlow<List<DiscoveredLanHub>>(emptyList())
    val discoveredHubs: StateFlow<List<DiscoveredLanHub>> = _discoveredHubs

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)

    @Synchronized
    fun startDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return

        runCatching {
            val lock = wifiManager?.createMulticastLock("ozvault_mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.offer(serviceInfo)
                drainResolveQueue()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val lostName = serviceInfo.serviceName
                _discoveredHubs.value = _discoveredHubs.value.filterNot { it.serviceName == lostName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }
        }

        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            discoveryListener = null
            _isScanning.value = false
            releaseMulticastLock()
        }
    }

    @Synchronized
    fun stopDiscovery() {
        resolveQueue.clear()
        resolving.set(false)
        val listener = discoveryListener
        discoveryListener = null
        _isScanning.value = false
        val manager = nsdManager
        if (listener != null && manager != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        releaseMulticastLock()
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    @Suppress("DEPRECATION")
    private fun drainResolveQueue() {
        val manager = nsdManager ?: return
        if (!resolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            resolving.set(false)
            return
        }
        runCatching {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false)
                    drainResolveQueue()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    handleResolved(serviceInfo)
                    resolving.set(false)
                    drainResolveQueue()
                }
            })
        }.onFailure {
            resolving.set(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleResolved(info: NsdServiceInfo) {
        val attrs = parseTxtAttributes(info.attributes)
        val hostAddress: String? = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                ?: info.hostAddresses.firstOrNull()?.hostAddress
                ?: info.host?.hostAddress
        } else {
            info.host?.hostAddress
        }
        val candidateHost = hostAddress?.takeIf { it.isNotBlank() } ?: return
        val baseUrl = buildLanBaseUrl(candidateHost, info.port) ?: return
        val hub = DiscoveredLanHub(
            serviceName = info.serviceName ?: "Game Vault PC",
            host = candidateHost,
            port = info.port,
            baseUrl = baseUrl,
            version = attrs["version"] ?: "2",
            attributes = attrs,
        )
        val current = _discoveredHubs.value.filterNot {
            it.serviceName == hub.serviceName || it.baseUrl == hub.baseUrl
        }
        _discoveredHubs.value = listOf(hub) + current
    }

    companion object {
        const val SERVICE_TYPE = "_ozvault._tcp."

        fun buildLanBaseUrl(host: String, port: Int): String? {
            val cleanHost = host.trim().trimEnd('.')
            if (cleanHost.isBlank() || port !in 1..65535) return null
            if (!GameVaultHubClient.isAllowedLanHost(cleanHost)) return null
            return "http://$cleanHost:$port"
        }

        fun parseTxtAttributes(raw: Map<String, ByteArray?>?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>(raw.size)
            for ((k, v) in raw) {
                val cleanKey = k.trim()
                if (cleanKey.isNotEmpty() && v != null) {
                    out[cleanKey] = String(v, Charsets.UTF_8).trim()
                }
            }
            return out
        }
    }
}
