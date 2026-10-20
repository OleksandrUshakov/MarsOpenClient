package org.marsopenclient.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Diagnostics and socket-binding helper for Android / GrapheneOS LAN communication.
 * Ensures UDP packets are routed over the local Wi-Fi / Ethernet network even when
 * an always-on VPN or mobile data connection is active, and holds a MulticastLock
 * so incoming UDP broadcast replies from Marstek devices are not dropped by the Wi-Fi chip.
 */
data class LanDiagnosticsInfo(
  val localIpv4: String? = null,
  val subnetPrefix: String? = null, // e.g. "192.168.1"
  val broadcastAddresses: List<String> = listOf("255.255.255.255"),
  val isWifiTransportActive: Boolean = false,
  val isVpnActive: Boolean = false,
  val networkInterfaceName: String? = null,
) {
  val summaryText: String
    get() = buildString {
      if (localIpv4 != null) {
        append("Wi-Fi IP: $localIpv4")
        if (networkInterfaceName != null) append(" ($networkInterfaceName)")
      } else {
        append("Локальний Wi-Fi IP не визначено")
      }
      if (isVpnActive) {
        append(" • VPN активний (трафік примусово скеровано через Wi-Fi)")
      }
    }
}

interface LanNetworkBinder {
  fun inspectLan(): LanDiagnosticsInfo
  fun bindSocketToLan(socket: DatagramSocket): Boolean
  fun <T> withMulticastLock(block: () -> T): T
}

class AndroidLanNetworkHelper(
  context: Context,
) : LanNetworkBinder {

  private val appContext = context.applicationContext
  private val connectivityManager =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  private val wifiManager =
    appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

  @Suppress("DEPRECATION")
  private fun findLanNetwork(): Network? {
    val cm = connectivityManager ?: return null
    val networks = try {
      cm.allNetworks.toList()
    } catch (_: Exception) {
      emptyList()
    }
    // Prefer WIFI, then ETHERNET
    for (net in networks) {
      val caps = try {
        cm.getNetworkCapabilities(net)
      } catch (_: Exception) {
        null
      } ?: continue
      if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        return net
      }
    }
    for (net in networks) {
      val caps = try {
        cm.getNetworkCapabilities(net)
      } catch (_: Exception) {
        null
      } ?: continue
      if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
        return net
      }
    }
    return null
  }

  @Suppress("DEPRECATION")
  override fun inspectLan(): LanDiagnosticsInfo {
    val cm = connectivityManager
    var localIp: String? = null
    var ifaceName: String? = null
    val broadcasts = linkedSetOf("255.255.255.255")
    var wifiActive = false
    var vpnActive = false

    if (cm != null) {
      try {
        for (net in cm.allNetworks) {
          val caps = cm.getNetworkCapabilities(net) ?: continue
          if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            vpnActive = true
          }
          if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
          ) {
            wifiActive = true
            val linkProps = cm.getLinkProperties(net)
            if (linkProps != null) {
              ifaceName = linkProps.interfaceName
              for (linkAddr in linkProps.linkAddresses) {
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                  val host = addr.hostAddress
                  if (!host.isNullOrBlank()) {
                    localIp = host
                    val parts = host.split(".")
                    if (parts.size == 4) {
                      broadcasts.add("${parts[0]}.${parts[1]}.${parts[2]}.255")
                    }
                  }
                }
              }
            }
          }
        }
      } catch (_: Exception) {
        // Ignore security/runtime exceptions
      }
    }

    // Fallback/supplement via NetworkInterface
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val ni = interfaces.nextElement()
        if (!ni.isUp || ni.isLoopback) continue
        val name = ni.name.lowercase()
        // Skip VPN / cellular interfaces
        if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet") || name.startsWith("ccmni")) {
          continue
        }
        for (ia in ni.interfaceAddresses) {
          val addr = ia.address
          if (addr is Inet4Address && !addr.isLoopbackAddress) {
            val host = addr.hostAddress
            if (!host.isNullOrBlank() && localIp == null) {
              localIp = host
              ifaceName = ni.name
            }
            val bcast = ia.broadcast
            if (bcast is Inet4Address && !bcast.hostAddress.isNullOrBlank()) {
              broadcasts.add(bcast.hostAddress!!)
            } else if (!host.isNullOrBlank()) {
              val parts = host.split(".")
              if (parts.size == 4) {
                broadcasts.add("${parts[0]}.${parts[1]}.${parts[2]}.255")
              }
            }
          }
        }
      }
    } catch (_: Exception) {
      // Ignore
    }

    val prefix = localIp?.split(".")?.takeIf { it.size == 4 }?.let { "${it[0]}.${it[1]}.${it[2]}" }
    return LanDiagnosticsInfo(
      localIpv4 = localIp,
      subnetPrefix = prefix,
      broadcastAddresses = broadcasts.toList(),
      isWifiTransportActive = wifiActive || localIp != null,
      isVpnActive = vpnActive,
      networkInterfaceName = ifaceName,
    )
  }

  override fun bindSocketToLan(socket: DatagramSocket): Boolean {
    val lanNet = findLanNetwork() ?: return false
    return try {
      lanNet.bindSocket(socket)
      true
    } catch (_: Exception) {
      false
    }
  }

  override fun <T> withMulticastLock(block: () -> T): T {
    val wm = wifiManager ?: return block()
    val lock = try {
      wm.createMulticastLock("MarsOpenClientUdpLock").apply {
        setReferenceCounted(false)
        acquire()
      }
    } catch (_: Exception) {
      null
    }
    return try {
      block()
    } finally {
      try {
        if (lock?.isHeld == true) {
          lock.release()
        }
      } catch (_: Exception) {
        // Ignore
      }
    }
  }
}

object NoOpLanNetworkBinder : LanNetworkBinder {
  override fun inspectLan(): LanDiagnosticsInfo {
    val broadcasts = linkedSetOf("255.255.255.255")
    var localIp: String? = null
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val ni = interfaces.nextElement()
        if (!ni.isUp || ni.isLoopback) continue
        for (ia in ni.interfaceAddresses) {
          val addr = ia.address
          if (addr is Inet4Address && !addr.isLoopbackAddress) {
            if (localIp == null) localIp = addr.hostAddress
            val bcast = ia.broadcast
            if (bcast is Inet4Address && bcast.hostAddress != null) {
              broadcasts.add(bcast.hostAddress!!)
            }
          }
        }
      }
    } catch (_: Exception) {
      // Ignore
    }
    val prefix = localIp?.split(".")?.takeIf { it.size == 4 }?.let { "${it[0]}.${it[1]}.${it[2]}" }
    return LanDiagnosticsInfo(
      localIpv4 = localIp,
      subnetPrefix = prefix,
      broadcastAddresses = broadcasts.toList(),
      isWifiTransportActive = localIp != null,
    )
  }

  override fun bindSocketToLan(socket: DatagramSocket): Boolean = false

  override fun <T> withMulticastLock(block: () -> T): T = block()
}
