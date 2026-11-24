package cl.gakuto.kizuna.launch

import android.net.Network
import java.util.concurrent.atomic.AtomicReference

/**
 * Mutable route assigned by Android JobScheduler to a running download.
 *
 * Foreground-service/manual flows keep null and use Android's current default network.
 * User-initiated jobs receive the Network that satisfies the JobScheduler constraint,
 * and Android may replace it through JobService.onNetworkChanged().
 */
internal class DownloadNetworkController(initial: Network? = null) {
    private val current = AtomicReference(initial)

    fun current(): Network? = current.get()

    fun update(network: Network?): Boolean {
        val previous = current.getAndSet(network)
        return previous != network
    }
}
