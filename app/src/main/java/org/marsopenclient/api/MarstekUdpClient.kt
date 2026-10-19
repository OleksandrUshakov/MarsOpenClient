package org.marsopenclient.api

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * UDP LAN Transport & RPC Client for Marstek Device Open API (Rev 3.1).
 * Supports both real UDP sockets (Broadcast discovery + Unicast commands)
 * and local Simulated endpoints for offline / demo verification.
 */
class MarstekUdpClient(
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val requestIdCounter = AtomicInteger(1)
  private val logIdCounter = AtomicLong(1L)

  private val simulatedDevices = mutableMapOf<String, MarstekSimulatedDevice>().apply {
    MarstekSimulatedDevice.createDefaultSimulatedEndpoints().forEach { ep ->
      put(ep.id, MarstekSimulatedDevice(ep))
    }
  }

  private val _packetLogs = MutableStateFlow<List<PacketLogEntry>>(emptyList())
  val packetLogs: StateFlow<List<PacketLogEntry>> = _packetLogs.asStateFlow()

  fun nextRequestId(): Int = requestIdCounter.getAndIncrement()

  fun clearPacketLogs() {
    _packetLogs.value = emptyList()
  }

  fun getSimulatedEndpoints(): List<MarstekEndpoint> =
    simulatedDevices.values.map { it.endpoint }

  /**
   * 2.2.2 Discovering Devices via UDP Broadcast (`Marstek.GetDevice`).
   * Sends broadcast packet to 255.255.255.255 and all active IPv4 subnet broadcast addresses.
   */
  suspend fun discoverDevicesOnLan(
    port: Int = MarstekApiConstants.DEFAULT_UDP_PORT,
    bleMacFilter: String = "0",
    timeoutMs: Int = 2200,
    includeSimulatedInResults: Boolean = false,
  ): List<MarstekEndpoint> = withContext(ioDispatcher) {
    val payload = MarstekJsonCodec.buildGetDeviceRequest(requestId = 0, bleMac = bleMacFilter)
    val discovered = linkedMapOf<String, MarstekEndpoint>()

    val broadcastTargets = resolveBroadcastAddresses()
    appendLog(
      direction = PacketLogEntry.Direction.SENT,
      endpointAddress = "BROADCAST:${broadcastTargets.joinToString(",") { it.hostAddress ?: "255.255.255.255" }}:$port",
      method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
      payload = payload,
    )

    val startTime = System.currentTimeMillis()
    try {
      DatagramSocket(null).use { socket ->
        socket.reuseAddress = true
        socket.broadcast = true
        socket.bind(InetSocketAddress(0))

        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        for (target in broadcastTargets) {
          try {
            val packet = DatagramPacket(bytes, bytes.size, target, port)
            socket.send(packet)
          } catch (_: Exception) {
            // Ignore unreachable interface broadcast
          }
        }

        val receiveBuffer = ByteArray(8192)
        while (true) {
          val elapsed = (System.currentTimeMillis() - startTime).toInt()
          val remaining = timeoutMs - elapsed
          if (remaining <= 50) break

          socket.soTimeout = remaining
          val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
          try {
            socket.receive(receivePacket)
            val latency = System.currentTimeMillis() - startTime
            val rawText = String(
              receivePacket.data,
              receivePacket.offset,
              receivePacket.length,
              StandardCharsets.UTF_8,
            ).trim()
            val senderIp = receivePacket.address?.hostAddress ?: ""

            appendLog(
              direction = PacketLogEntry.Direction.RECEIVED,
              endpointAddress = "$senderIp:${receivePacket.port}",
              method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
              payload = rawText,
              latencyMs = latency,
            )

            when (val parsed = MarstekJsonCodec.parseDeviceInfo(rawText, latency)) {
              is ApiCallResult.Success -> {
                val info = parsed.value
                val resolvedIp = info.ip?.takeIf { it.isNotBlank() } ?: senderIp
                if (resolvedIp.isNotBlank()) {
                  val key = "${resolvedIp}:$port"
                  discovered[key] = MarstekEndpoint(
                    id = key,
                    name = parsed.src ?: "${info.device} ($resolvedIp)",
                    ip = resolvedIp,
                    port = port,
                    deviceModel = info.device,
                    firmwareVersion = info.ver,
                    bleMac = info.bleMac,
                    wifiMac = info.wifiMac,
                    wifiName = info.wifiName,
                    isSimulated = false,
                  )
                }
              }
              else -> Unit
            }
          } catch (_: SocketTimeoutException) {
            break
          }
        }
      }
    } catch (e: Exception) {
      appendLog(
        direction = PacketLogEntry.Direction.ERROR,
        endpointAddress = "255.255.255.255:$port",
        method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
        payload = "UDP Broadcast Error: ${e.message}",
        isError = true,
      )
    }

    if (includeSimulatedInResults) {
      simulatedDevices.values.forEach { sim ->
        discovered[sim.endpoint.id] = sim.endpoint
      }
    }

    discovered.values.toList()
  }

  /**
   * Sends a raw JSON-RPC request over UDP to the given endpoint and returns the raw response string + latency.
   */
  suspend fun sendRawCommand(
    endpoint: MarstekEndpoint,
    methodLabel: String,
    rawJsonRequest: String,
    timeoutMs: Int = 2500,
  ): Pair<Result<String>, Long> = withContext(ioDispatcher) {
    val addressLabel = if (endpoint.isSimulated) {
      "SIM://${endpoint.deviceModel} (${endpoint.ip}:${endpoint.port})"
    } else {
      "${endpoint.ip}:${endpoint.port}"
    }

    appendLog(
      direction = PacketLogEntry.Direction.SENT,
      endpointAddress = addressLabel,
      method = methodLabel,
      payload = rawJsonRequest,
    )

    val startTime = System.currentTimeMillis()

    if (endpoint.isSimulated) {
      val sim = simulatedDevices.getOrPut(endpoint.id) { MarstekSimulatedDevice(endpoint) }
      val response = sim.handleRawRequest(rawJsonRequest)
      val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
      val hasError = response.contains("\"error\"")
      appendLog(
        direction = if (hasError) PacketLogEntry.Direction.ERROR else PacketLogEntry.Direction.RECEIVED,
        endpointAddress = addressLabel,
        method = methodLabel,
        payload = response,
        latencyMs = latency,
        isError = hasError,
      )
      return@withContext Pair(Result.success(response), latency)
    }

    try {
      DatagramSocket().use { socket ->
        socket.soTimeout = timeoutMs
        val targetAddress = InetAddress.getByName(endpoint.ip)
        val requestBytes = rawJsonRequest.toByteArray(StandardCharsets.UTF_8)
        val sendPacket = DatagramPacket(requestBytes, requestBytes.size, targetAddress, endpoint.port)
        socket.send(sendPacket)

        val receiveBuffer = ByteArray(8192)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
        socket.receive(receivePacket)

        val latency = System.currentTimeMillis() - startTime
        val responseText = String(
          receivePacket.data,
          receivePacket.offset,
          receivePacket.length,
          StandardCharsets.UTF_8,
        ).trim()

        val hasError = responseText.contains("\"error\"")
        appendLog(
          direction = if (hasError) PacketLogEntry.Direction.ERROR else PacketLogEntry.Direction.RECEIVED,
          endpointAddress = addressLabel,
          method = methodLabel,
          payload = responseText,
          latencyMs = latency,
          isError = hasError,
        )
        Pair(Result.success(responseText), latency)
      }
    } catch (e: SocketTimeoutException) {
      val latency = System.currentTimeMillis() - startTime
      val msg = "Таймаут очікування UDP відповіді (${timeoutMs} мс) від ${endpoint.ip}:${endpoint.port}. Перевірте, що пристрій у мережі та Open API увімкнено в додатку Marstek."
      appendLog(
        direction = PacketLogEntry.Direction.ERROR,
        endpointAddress = addressLabel,
        method = methodLabel,
        payload = msg,
        latencyMs = latency,
        isError = true,
      )
      Pair(Result.failure(SocketTimeoutException(msg)), latency)
    } catch (e: Exception) {
      val latency = System.currentTimeMillis() - startTime
      val msg = "Помилка UDP з'єднання з ${endpoint.ip}:${endpoint.port}: ${e.localizedMessage ?: e.javaClass.simpleName}"
      appendLog(
        direction = PacketLogEntry.Direction.ERROR,
        endpointAddress = addressLabel,
        method = methodLabel,
        payload = msg,
        latencyMs = latency,
        isError = true,
      )
      Pair(Result.failure(e), latency)
    }
  }

  // ============================================================================
  // Typed API Calls for all 14 Marstek Open API Rev 3.1 Methods
  // ============================================================================

  /** 3.1.1 Marstek.GetDevice */
  suspend fun getDevice(
    endpoint: MarstekEndpoint,
    bleMac: String = "0",
  ): ApiCallResult<DeviceInfoResult> {
    val req = MarstekJsonCodec.buildGetDeviceRequest(nextRequestId(), bleMac)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseDeviceInfo(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.2.1 Wifi.GetStatus */
  suspend fun getWifiStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<WifiStatusResult> {
    val req = MarstekJsonCodec.buildWifiGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_WIFI_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseWifiStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.3.1 BLE.GetStatus */
  suspend fun getBleStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<BleStatusResult> {
    val req = MarstekJsonCodec.buildBleGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_BLE_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseBleStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.4.1 Bat.GetStatus */
  suspend fun getBatteryStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<BatteryStatusResult> {
    val req = MarstekJsonCodec.buildBatGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_BAT_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseBatteryStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.5.1 PV.GetStatus */
  suspend fun getPvStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<PvStatusResult> {
    val req = MarstekJsonCodec.buildPvGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_PV_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parsePvStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.6.1 ES.GetStatus */
  suspend fun getEsStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<EsStatusResult> {
    val req = MarstekJsonCodec.buildEsGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_ES_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseEsStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.6.2 ES.SetMode */
  suspend fun setEsMode(
    endpoint: MarstekEndpoint,
    config: EsSetModeConfig,
    instanceId: Int = 0,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildEsSetModeRequest(config, nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_ES_SET_MODE, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.6.3 ES.GetMode */
  suspend fun getEsMode(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<EsModeResult> {
    val req = MarstekJsonCodec.buildEsGetModeRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_ES_GET_MODE, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseEsMode(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.7.1 EM.GetStatus */
  suspend fun getEmStatus(
    endpoint: MarstekEndpoint,
    instanceId: Int = 0,
  ): ApiCallResult<EmStatusResult> {
    val req = MarstekJsonCodec.buildEmGetStatusRequest(nextRequestId(), instanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_EM_GET_STATUS, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseEmStatus(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.8.1 DOD.SET */
  suspend fun setDod(
    endpoint: MarstekEndpoint,
    value: Int,
    includeInstanceId: Int? = null,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildDodSetRequest(value, 0, includeInstanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_DOD_SET, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.8.2 Ble.Adv */
  suspend fun setBleAdv(
    endpoint: MarstekEndpoint,
    enableCode: Int, // 0: enable, 1: disable
    includeInstanceId: Int? = null,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildBleAdvRequest(enableCode, 0, includeInstanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_BLE_ADV, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.8.3 Led.Ctrl */
  suspend fun setLedCtrl(
    endpoint: MarstekEndpoint,
    stateCode: Int, // 1: on, 0: off
    includeInstanceId: Int? = null,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildLedCtrlRequest(stateCode, 0, includeInstanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_LED_CTRL, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.8.4 Set.Ver */
  suspend fun setPowerVersion(
    endpoint: MarstekEndpoint,
    versionWatts: Int,
    includeInstanceId: Int? = null,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildSetVerRequest(versionWatts, 0, includeInstanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_SET_VER, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  /** 3.8.5 Reset.Factory */
  suspend fun resetFactory(
    endpoint: MarstekEndpoint,
    typeCode: Int, // 1: clear data, 2: keep data
    includeInstanceId: Int? = null,
  ): ApiCallResult<SetOperationResult> {
    val req = MarstekJsonCodec.buildResetFactoryRequest(typeCode, 0, includeInstanceId)
    val (res, latency) = sendRawCommand(endpoint, MarstekApiConstants.METHOD_RESET_FACTORY, req)
    return res.fold(
      onSuccess = { MarstekJsonCodec.parseSetOperationResult(it, latency) },
      onFailure = { ApiCallResult.TransportFailure(it.message ?: "Transport error", it) },
    )
  }

  private fun resolveBroadcastAddresses(): List<InetAddress> {
    val result = linkedSetOf<InetAddress>()
    try {
      result.add(InetAddress.getByName("255.255.255.255"))
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        if (!networkInterface.isUp || networkInterface.isLoopback) continue
        for (interfaceAddress in networkInterface.interfaceAddresses) {
          val broadcast = interfaceAddress.broadcast
          if (broadcast is Inet4Address) {
            result.add(broadcast)
          }
        }
      }
    } catch (_: Exception) {
      // Fallback to global broadcast
    }
    return result.toList()
  }

  private fun appendLog(
    direction: PacketLogEntry.Direction,
    endpointAddress: String,
    method: String,
    payload: String,
    latencyMs: Long? = null,
    isError: Boolean = false,
  ) {
    val entry = PacketLogEntry(
      id = logIdCounter.getAndIncrement(),
      timestampMillis = System.currentTimeMillis(),
      direction = direction,
      endpointAddress = endpointAddress,
      method = method,
      payload = payload,
      latencyMs = latencyMs,
      isError = isError,
    )
    _packetLogs.update { existing ->
      (listOf(entry) + existing).take(150)
    }
  }
}
