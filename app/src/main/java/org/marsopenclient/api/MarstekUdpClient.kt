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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * UDP LAN Transport & RPC Client for Marstek Device Open API (Rev 3.1).
 *
 * Key reliability features for real Marstek hardware on Android / GrapheneOS:
 * 1. Binds UDP sockets explicitly to the active Wi-Fi/Ethernet `Network` so an active VPN
 *    or cellular connection never hijacks local LAN UDP packets.
 * 2. Acquires `WifiManager.MulticastLock` during discovery and receive windows.
 * 3. Binds the local UDP socket port to the configured Marstek UDP port (e.g. 30000) with
 *    `reuseAddress = true` (falling back to ephemeral port if busy), because many Marstek
 *    ESP32 firmware builds send UDP replies to destination port 30000 rather than ephemeral ports.
 * 4. Serializes UDP transactions with a Mutex + short pacing delay so the ESP32 UDP listener
 *    is never overwhelmed by parallel requests, and retries automatically if a packet is lost
 *    while the station wakes from Wi-Fi power-save (DTIM).
 * 5. Supports both UDP Broadcast AND direct `/24` Subnet Unicast sweep so stations are found
 *    even on routers that filter broadcast packets.
 */
class MarstekUdpClient(
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val networkBinder: LanNetworkBinder = NoOpLanNetworkBinder,
  initialLogs: List<PacketLogEntry> = emptyList(),
  private val onLogsUpdated: ((List<PacketLogEntry>) -> Unit)? = null,
) {
  private val requestIdCounter = AtomicInteger(1)
  private val logIdCounter = AtomicLong(
    (initialLogs.maxOfOrNull { it.id } ?: 0L) + 1L
  )
  private val udpMutex = Mutex()
  private var lastUdpCallFinishedAt = 0L

  private val simulatedDevices = mutableMapOf<String, MarstekSimulatedDevice>().apply {
    MarstekSimulatedDevice.createDefaultSimulatedEndpoints().forEach { ep ->
      put(ep.id, MarstekSimulatedDevice(ep))
    }
  }

  private val _packetLogs = MutableStateFlow<List<PacketLogEntry>>(initialLogs)
  val packetLogs: StateFlow<List<PacketLogEntry>> = _packetLogs.asStateFlow()

  fun inspectLan(): LanDiagnosticsInfo = networkBinder.inspectLan()

  fun nextRequestId(): Int = requestIdCounter.getAndIncrement()

  fun clearPacketLogs() {
    _packetLogs.value = emptyList()
    onLogsUpdated?.invoke(emptyList())
  }

  fun getSimulatedEndpoints(): List<MarstekEndpoint> =
    simulatedDevices.values.map { it.endpoint }

  /**
   * Creates a DatagramSocket bound to the local Wi-Fi network and (when possible)
   * bound to the target Marstek UDP port `preferredLocalPort` (e.g. 30000), because
   * Marstek firmware often sends replies back to port 30000 on the sender IP.
   */
  private fun createBoundUdpSocket(preferredLocalPort: Int): Pair<DatagramSocket, Boolean> {
    val socket = DatagramSocket(null)
    socket.reuseAddress = true
    socket.broadcast = true
    try {
      socket.bind(InetSocketAddress(preferredLocalPort))
    } catch (_: Exception) {
      // Fallback to any available ephemeral port if preferredLocalPort is already bound
      socket.bind(InetSocketAddress(0))
    }
    val boundToWifi = networkBinder.bindSocketToLan(socket)
    return Pair(socket, boundToWifi)
  }

  /**
   * 2.2.2 Discovering Devices via UDP Broadcast + Optional Unicast `/24` Subnet Sweep.
   * Sends both `Marstek.GetDevice` and `ES.GetStatus` probes so devices respond regardless
   * of firmware revision quirks.
   */
  suspend fun discoverDevicesOnLan(
    port: Int = MarstekApiConstants.DEFAULT_UDP_PORT,
    bleMacFilter: String = "0",
    timeoutMs: Int = 3600,
    deepSubnetScan: Boolean = true,
    includeSimulatedInResults: Boolean = false,
    onProgress: ((String) -> Unit)? = null,
  ): List<MarstekEndpoint> = withContext(ioDispatcher) {
    udpMutex.withLock {
      val lanInfo = networkBinder.inspectLan()
      val getDevicePayload = MarstekJsonCodec.buildGetDeviceRequest(requestId = 0, bleMac = bleMacFilter)
      val esStatusProbePayload = MarstekJsonCodec.buildEsGetStatusRequest(requestId = 1, instanceId = 0)
      val discovered = linkedMapOf<String, MarstekEndpoint>()

      val broadcastTargets = resolveBroadcastAddresses(lanInfo)
      val bcastDesc = broadcastTargets.joinToString(",") { it.hostAddress ?: "255.255.255.255" }

      appendLog(
        direction = PacketLogEntry.Direction.SENT,
        endpointAddress = "DISCOVERY(Broadcast=$bcastDesc, Subnet=${lanInfo.subnetPrefix ?: "none"}.*):$port [Wi-FiIP=${lanInfo.localIpv4 ?: "none"}]",
        method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
        payload = getDevicePayload,
      )

      networkBinder.withMulticastLock {
        val startTime = System.currentTimeMillis()
        try {
          val (socket, boundToWifi) = createBoundUdpSocket(port)
          socket.use {
            val getDevBytes = getDevicePayload.toByteArray(StandardCharsets.UTF_8)
            val esProbeBytes = esStatusProbePayload.toByteArray(StandardCharsets.UTF_8)

            // Step 1: Send UDP Broadcast bursts (2 rounds) to wake up ESP32 Wi-Fi
            onProgress?.invoke(
              "Надсилання Broadcast на порт $port (${if (boundToWifi) "Wi-Fi інтерфейс" else "стандартний маршрут"})..."
            )
            repeat(2) {
              for (target in broadcastTargets) {
                try {
                  socket.send(DatagramPacket(getDevBytes, getDevBytes.size, target, port))
                  socket.send(DatagramPacket(esProbeBytes, esProbeBytes.size, target, port))
                } catch (_: Exception) {
                  // Ignore unreachable broadcast target
                }
              }
              drainIncomingDiscoveryPackets(socket, 250, startTime, port, discovered)
            }

            // Step 2: If deepSubnetScan is enabled and we know the phone's /24 Wi-Fi subnet,
            // sweep unicast UDP across .1..254 to bypass router broadcast isolation!
            val subnetPrefix = lanInfo.subnetPrefix
            if (deepSubnetScan && !subnetPrefix.isNullOrBlank()) {
              onProgress?.invoke("Сканування підмережі $subnetPrefix.1–254:$port...")
              for (hostIdx in 1..254) {
                val targetIp = "$subnetPrefix.$hostIdx"
                if (targetIp == lanInfo.localIpv4) continue
                try {
                  val addr = InetAddress.getByName(targetIp)
                  socket.send(DatagramPacket(getDevBytes, getDevBytes.size, addr, port))
                } catch (_: Exception) {
                  // Ignore individual send error
                }
                // Every 32 hosts, drain any incoming UDP replies so receive buffer never overflows
                if (hostIdx % 32 == 0) {
                  onProgress?.invoke("Перевірка адрес $subnetPrefix.${hostIdx - 31}..$hostIdx:$port (знайдено: ${discovered.size})...")
                  drainIncomingDiscoveryPackets(socket, 45, startTime, port, discovered)
                }
              }
            }

            // Step 3: Final broadcast burst + listen for remaining timeout window
            for (target in broadcastTargets) {
              try {
                socket.send(DatagramPacket(getDevBytes, getDevBytes.size, target, port))
              } catch (_: Exception) {
                // Ignore
              }
            }
            onProgress?.invoke("Очікування відповідей від станцій на порту $port...")
            val elapsed = (System.currentTimeMillis() - startTime).toInt()
            val remaining = (timeoutMs - elapsed).coerceAtLeast(600)
            drainIncomingDiscoveryPackets(socket, remaining, startTime, port, discovered)
          }
        } catch (e: Exception) {
          appendLog(
            direction = PacketLogEntry.Direction.ERROR,
            endpointAddress = "255.255.255.255:$port",
            method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
            payload = "Помилка сокета при пошуку в LAN: ${e.message}",
            isError = true,
          )
        }
      }

      if (includeSimulatedInResults) {
        simulatedDevices.values.forEach { sim ->
          discovered[sim.endpoint.id] = sim.endpoint
        }
      }

      lastUdpCallFinishedAt = System.currentTimeMillis()
      discovered.values.toList()
    }
  }

  private fun drainIncomingDiscoveryPackets(
    socket: DatagramSocket,
    listenWindowMs: Int,
    startTime: Long,
    port: Int,
    discovered: LinkedHashMap<String, MarstekEndpoint>,
  ) {
    val windowStart = System.currentTimeMillis()
    val receiveBuffer = ByteArray(8192)
    while (true) {
      val elapsedInWindow = (System.currentTimeMillis() - windowStart).toInt()
      val remaining = listenWindowMs - elapsedInWindow
      if (remaining <= 15) break

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

        // Ignore our own echoed broadcast packets (which lack "result" and "src")
        if (rawText.contains("\"method\"") && !rawText.contains("\"result\"") && !rawText.contains("\"src\"")) {
          continue
        }

        appendLog(
          direction = PacketLogEntry.Direction.RECEIVED,
          endpointAddress = "$senderIp:${receivePacket.port}",
          method = MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE,
          payload = rawText,
          latencyMs = latency,
        )

        val endpoint = parseDiscoveredEndpointFromPacket(rawText, senderIp, port, latency)
        if (endpoint != null) {
          val existing = discovered[endpoint.id]
          // Prefer fuller metadata (from Marstek.GetDevice) over partial ES.GetStatus probe
          if (existing == null || endpoint.firmwareVersion != null) {
            discovered[endpoint.id] = endpoint
          }
        }
      } catch (_: SocketTimeoutException) {
        break
      } catch (_: Exception) {
        break
      }
    }
  }

  private fun parseDiscoveredEndpointFromPacket(
    rawText: String,
    senderIp: String,
    port: Int,
    latency: Long,
  ): MarstekEndpoint? {
    // 1. Try parsing as Marstek.GetDevice
    when (val parsed = MarstekJsonCodec.parseDeviceInfo(rawText, latency)) {
      is ApiCallResult.Success -> {
        val info = parsed.value
        val resolvedIp = info.ip?.takeIf { it.isNotBlank() } ?: senderIp
        if (resolvedIp.isNotBlank() && (info.ver != null || info.bleMac != null || info.wifiMac != null || parsed.src != null)) {
          val key = "$resolvedIp:$port"
          val model = info.device.takeIf { it.isNotBlank() && it != "Unknown" }
            ?: parsed.src?.substringBefore("-")
            ?: "Venus"
          return MarstekEndpoint(
            id = key,
            name = parsed.src ?: "$model ($resolvedIp)",
            ip = resolvedIp,
            port = port,
            deviceModel = model,
            firmwareVersion = info.ver,
            bleMac = info.bleMac ?: parsed.src?.substringAfter("-", "")?.takeIf { it.isNotBlank() },
            wifiMac = info.wifiMac,
            wifiName = info.wifiName,
            isSimulated = false,
            lastSeenMillis = System.currentTimeMillis(),
          )
        }
      }
      else -> Unit
    }

    // 2. Fallback: If device responded to ES.GetStatus probe or returned an RPC response with "src"
    if (senderIp.isNotBlank() && rawText.contains("\"src\"")) {
      when (val esParsed = MarstekJsonCodec.parseEsStatus(rawText, latency)) {
        is ApiCallResult.Success -> {
          val src = esParsed.src
          val model = src?.substringBefore("-")?.takeIf { it.isNotBlank() } ?: "Venus"
          val macFromSrc = src?.substringAfter("-", "")?.takeIf { it.isNotBlank() }
          val key = "$senderIp:$port"
          return MarstekEndpoint(
            id = key,
            name = src ?: "$model ($senderIp)",
            ip = senderIp,
            port = port,
            deviceModel = model,
            bleMac = macFromSrc,
            isSimulated = false,
            lastSeenMillis = System.currentTimeMillis(),
            lastKnownSoc = esParsed.value.batSoc?.toInt(),
          )
        }
        else -> Unit
      }
    }
    return null
  }

  /**
   * Sends a raw JSON-RPC request over UDP to the given endpoint and returns the raw response string + latency.
   * Automatically serializes access, binds to local Wi-Fi + target UDP port, and retries up to [maxAttempts]
   * if the device does not respond on the first packet.
   */
  suspend fun sendRawCommand(
    endpoint: MarstekEndpoint,
    methodLabel: String,
    rawJsonRequest: String,
    timeoutMs: Int = 2800,
    maxAttempts: Int = 2,
  ): Pair<Result<String>, Long> = withContext(ioDispatcher) {
    val addressLabel = if (endpoint.isSimulated) {
      "SIM://${endpoint.deviceModel} (${endpoint.ip}:${endpoint.port})"
    } else {
      "${endpoint.ip}:${endpoint.port}"
    }

    if (endpoint.isSimulated) {
      appendLog(
        direction = PacketLogEntry.Direction.SENT,
        endpointAddress = addressLabel,
        method = methodLabel,
        payload = rawJsonRequest,
      )
      val startTime = System.currentTimeMillis()
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

    udpMutex.withLock {
      // Pace consecutive UDP calls so the ESP32 in the Marstek battery has time to process
      val sinceLast = System.currentTimeMillis() - lastUdpCallFinishedAt
      if (sinceLast in 0..75) {
        delay(80 - sinceLast)
      }

      val overallStartTime = System.currentTimeMillis()
      val perAttemptTimeout = (timeoutMs / maxAttempts.coerceAtLeast(1)).coerceAtLeast(1200)
      var lastException: Exception? = null

      networkBinder.withMulticastLock {
        try {
          val (socket, boundToWifi) = createBoundUdpSocket(endpoint.port)
          socket.use {
            val targetAddress = InetAddress.getByName(endpoint.ip)
            val requestBytes = rawJsonRequest.toByteArray(StandardCharsets.UTF_8)
            val sendPacket = DatagramPacket(requestBytes, requestBytes.size, targetAddress, endpoint.port)

            for (attempt in 1..maxAttempts) {
              val attemptLabel = if (maxAttempts > 1 && attempt > 1) {
                "$methodLabel (спроба $attempt/$maxAttempts)"
              } else {
                methodLabel
              }
              appendLog(
                direction = PacketLogEntry.Direction.SENT,
                endpointAddress = "$addressLabel [localPort=${socket.localPort}, wifiBound=$boundToWifi]",
                method = attemptLabel,
                payload = rawJsonRequest,
              )

              try {
                socket.send(sendPacket)
                val receiveBuffer = ByteArray(8192)
                val waitStart = System.currentTimeMillis()

                while (true) {
                  val elapsedAttempt = (System.currentTimeMillis() - waitStart).toInt()
                  val remaining = perAttemptTimeout - elapsedAttempt
                  if (remaining <= 20) {
                    throw SocketTimeoutException("Timeout after ${perAttemptTimeout}ms")
                  }
                  socket.soTimeout = remaining
                  val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                  socket.receive(receivePacket)

                  val responseText = String(
                    receivePacket.data,
                    receivePacket.offset,
                    receivePacket.length,
                    StandardCharsets.UTF_8,
                  ).trim()

                  // Ignore self-echo if local port equals target port and loopback occurred
                  if (responseText.contains("\"method\"") && !responseText.contains("\"result\"") && !responseText.contains("\"error\"")) {
                    continue
                  }

                  val latency = System.currentTimeMillis() - overallStartTime
                  val hasError = responseText.contains("\"error\"")
                  appendLog(
                    direction = if (hasError) PacketLogEntry.Direction.ERROR else PacketLogEntry.Direction.RECEIVED,
                    endpointAddress = "${receivePacket.address?.hostAddress ?: endpoint.ip}:${receivePacket.port}",
                    method = methodLabel,
                    payload = responseText,
                    latencyMs = latency,
                    isError = hasError,
                  )
                  lastUdpCallFinishedAt = System.currentTimeMillis()
                  return@withMulticastLock Pair(Result.success(responseText), latency)
                }
              } catch (timeoutEx: SocketTimeoutException) {
                lastException = timeoutEx
                // Proceed to next attempt if available
              }
            }
          }
        } catch (e: Exception) {
          lastException = e
        }
      }

      lastUdpCallFinishedAt = System.currentTimeMillis()
      val totalLatency = System.currentTimeMillis() - overallStartTime
      val lanInfo = networkBinder.inspectLan()
      val msg = if (lastException is SocketTimeoutException) {
        "Таймаут відповіді (${totalLatency} мс) від ${endpoint.ip}:${endpoint.port}. " +
          "Ваш Wi-Fi IP: ${lanInfo.localIpv4 ?: "не визначено"}. " +
          "Перевірте: 1) чи станція підключена до тієї ж Wi-Fi мережі; 2) чи увімкнено Open API у додатку Marstek та чи збігається UDP порт (${endpoint.port})."
      } else {
        "Помилка UDP з'єднання з ${endpoint.ip}:${endpoint.port}: ${lastException?.localizedMessage ?: "невідома помилка"}"
      }
      appendLog(
        direction = PacketLogEntry.Direction.ERROR,
        endpointAddress = addressLabel,
        method = methodLabel,
        payload = msg,
        latencyMs = totalLatency,
        isError = true,
      )
      Pair(Result.failure(lastException ?: SocketTimeoutException(msg)), totalLatency)
    }
  }

  // ============================================================================
  // Typed API Calls for all 14 Marstek Open API Rev 3.1 Methods
  // ============================================================================

  /** 3.1.1 Marstek.GetDevice (uses requestId = 0 and ble_mac = "0" by default per spec) */
  suspend fun getDevice(
    endpoint: MarstekEndpoint,
    bleMac: String = "0",
  ): ApiCallResult<DeviceInfoResult> {
    val req = MarstekJsonCodec.buildGetDeviceRequest(0, bleMac)
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

  private fun resolveBroadcastAddresses(lanInfo: LanDiagnosticsInfo): List<InetAddress> {
    val result = linkedSetOf<InetAddress>()
    try {
      lanInfo.broadcastAddresses.forEach { addrStr ->
        try {
          result.add(InetAddress.getByName(addrStr))
        } catch (_: Exception) {
          // Ignore
        }
      }
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
      val updated = (listOf(entry) + existing).take(250)
      onLogsUpdated?.invoke(updated)
      updated
    }
  }
}
