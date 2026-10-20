package org.marsopenclient.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.marsopenclient.api.AndroidLanNetworkHelper
import org.marsopenclient.api.ApiCallResult
import org.marsopenclient.api.BatteryStatusResult
import org.marsopenclient.api.BleStatusResult
import org.marsopenclient.api.DeviceInfoResult
import org.marsopenclient.api.DeviceModelProfile
import org.marsopenclient.api.EmStatusResult
import org.marsopenclient.api.EsModeResult
import org.marsopenclient.api.EsOperatingMode
import org.marsopenclient.api.EsSetModeConfig
import org.marsopenclient.api.EsStatusResult
import org.marsopenclient.api.LanDiagnosticsInfo
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint
import org.marsopenclient.api.MarstekJsonCodec
import org.marsopenclient.api.MarstekUdpClient
import org.marsopenclient.api.NoOpLanNetworkBinder
import org.marsopenclient.api.PacketLogEntry
import org.marsopenclient.api.PvStatusResult
import org.marsopenclient.api.WifiStatusResult
import org.marsopenclient.data.AndroidFileAppStorage
import org.marsopenclient.data.InMemoryAppStorage
import org.marsopenclient.data.LocalAppStorage
import org.marsopenclient.data.LogReportFormatter
import org.marsopenclient.data.PersistedAppConfig

enum class AppSection(val titleUk: String) {
  DASHBOARD("Головна"),
  ES_MODE("Режими"),
  DEVICES("Пристрої"),
  SYS_CONTROL("Налаштування"),
  CONSOLE("Журнал"),
}

data class ManualModeFormState(
  val timeNum: Int = 1,
  val startTime: String = "08:30",
  val endTime: String = "20:30",
  val weekSet: Int = 127,
  val powerWatts: String = "800",
  val enable: Boolean = true,
  val includeManualSet: Boolean = false,
  val manualSetCode: Int = 1, // 0: disable, 1: charge, 2: discharge, 3: auto
)

data class PassiveModeFormState(
  val powerWatts: String = "500",
  val cdTimeSeconds: String = "300",
)

data class UpsModeFormState(
  val enable: Boolean = true,
  val useUppercaseWireMode: Boolean = true, // "UPS" vs "Ups"
)

data class MarstekUiState(
  val currentSection: AppSection = AppSection.DASHBOARD,
  val savedDevices: List<MarstekEndpoint> = emptyList(),
  val demoDevices: List<MarstekEndpoint> = emptyList(),
  val demoModeEnabled: Boolean = false,
  val selectedEndpoint: MarstekEndpoint? = null,
  val lanDiagnostics: LanDiagnosticsInfo = LanDiagnosticsInfo(),
  val instanceId: Int = 0,
  val discoveryPort: String = MarstekApiConstants.DEFAULT_UDP_PORT.toString(),
  val discoveryBleMac: String = "0",
  val deepSubnetScanEnabled: Boolean = true,
  val isDiscovering: Boolean = false,
  val discoveryProgressText: String? = null,
  val isLoadingAll: Boolean = false,
  val isConnectedOnline: Boolean = false,
  val lastSuccessfulSyncMillis: Long? = null,
  val autoPollEnabled: Boolean = false,
  val statusBannerMessage: String? = null,
  val isBannerError: Boolean = false,

  // 3.1 - 3.7 Query States
  val deviceInfo: DeviceInfoResult? = null,
  val wifiStatus: WifiStatusResult? = null,
  val bleStatus: BleStatusResult? = null,
  val batteryStatus: BatteryStatusResult? = null,
  val pvStatus: PvStatusResult? = null,
  val esStatus: EsStatusResult? = null,
  val esMode: EsModeResult? = null,
  val emStatus: EmStatusResult? = null,

  // 3.6.2 ES.SetMode Form State
  val selectedEsModeTab: EsOperatingMode = EsOperatingMode.AUTO,
  val autoModeEnable: Boolean = true,
  val aiModeEnable: Boolean = true,
  val manualForm: ManualModeFormState = ManualModeFormState(),
  val passiveForm: PassiveModeFormState = PassiveModeFormState(),
  val upsForm: UpsModeFormState = UpsModeFormState(),

  // 3.8 SYS Control State
  val dodSliderValue: Int = MarstekApiConstants.DEFAULT_DOD_VALUE,
  val customVersionWatts: String = "2500",
  val includeInstanceIdInSys: Boolean = false,

  // Raw UDP Console State
  val consoleSelectedMethod: String = MarstekApiConstants.METHOD_ES_GET_STATUS,
  val consoleRawRequest: String = MarstekJsonCodec.getTemplateForMethod(MarstekApiConstants.METHOD_ES_GET_STATUS),
  val consoleLastResponse: String = "",
) {
  val endpoints: List<MarstekEndpoint>
    get() = if (demoModeEnabled) {
      savedDevices + demoDevices
    } else {
      savedDevices
    }
}

class MarstekDashboardViewModel(
  private val localStorage: LocalAppStorage = InMemoryAppStorage(),
  private val udpClient: MarstekUdpClient = MarstekUdpClient(
    networkBinder = NoOpLanNetworkBinder,
    initialLogs = localStorage.loadLogs(),
    onLogsUpdated = { localStorage.saveLogs(it) },
  ),
) : ViewModel() {

  companion object {
    fun createWithContext(context: Context): MarstekDashboardViewModel {
      val storage = AndroidFileAppStorage(context)
      val binder = AndroidLanNetworkHelper(context)
      val client = MarstekUdpClient(
        networkBinder = binder,
        initialLogs = storage.loadLogs(),
        onLogsUpdated = { storage.saveLogs(it) },
      )
      return MarstekDashboardViewModel(
        localStorage = storage,
        udpClient = client,
      )
    }
  }

  private val _uiState = MutableStateFlow(MarstekUiState())
  val uiState: StateFlow<MarstekUiState> = _uiState.asStateFlow()

  val packetLogs: StateFlow<List<PacketLogEntry>> = udpClient.packetLogs.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = localStorage.loadLogs(),
  )

  private var autoPollJob: Job? = null

  init {
    val persisted = localStorage.loadConfig()
    val defaultSims = udpClient.getSimulatedEndpoints()
    val lanInfo = udpClient.inspectLan()
    val availableList = if (persisted.demoModeEnabled) {
      persisted.savedDevices + defaultSims
    } else {
      persisted.savedDevices
    }
    val initialEndpoint = availableList.firstOrNull { it.id == persisted.selectedDeviceId }
      ?: persisted.savedDevices.firstOrNull()
      ?: (if (persisted.demoModeEnabled) defaultSims.firstOrNull() else null)

    _uiState.update {
      it.copy(
        savedDevices = persisted.savedDevices,
        demoDevices = defaultSims,
        demoModeEnabled = persisted.demoModeEnabled,
        selectedEndpoint = initialEndpoint,
        lanDiagnostics = lanInfo,
        discoveryPort = persisted.defaultUdpPort.toString(),
        manualForm = it.manualForm.copy(
          includeManualSet = initialEndpoint?.profile == DeviceModelProfile.VENUS_E_MINI
        ),
      )
    }
    if (initialEndpoint != null) {
      refreshAllStatuses(silentBanner = true)
    }
  }

  fun refreshLanDiagnostics() {
    val info = udpClient.inspectLan()
    _uiState.update { it.copy(lanDiagnostics = info) }
  }

  private fun persistCurrentConfig() {
    val s = _uiState.value
    localStorage.saveConfig(
      PersistedAppConfig(
        savedDevices = s.savedDevices,
        selectedDeviceId = s.selectedEndpoint?.id,
        demoModeEnabled = s.demoModeEnabled,
        defaultUdpPort = s.discoveryPort.toIntOrNull() ?: MarstekApiConstants.DEFAULT_UDP_PORT,
      )
    )
  }

  fun selectSection(section: AppSection) {
    refreshLanDiagnostics()
    _uiState.update { it.copy(currentSection = section) }
  }

  fun dismissBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  fun toggleDemoMode(enabled: Boolean) {
    _uiState.update { cur ->
      val nextSelected = if (enabled) {
        cur.selectedEndpoint ?: cur.savedDevices.firstOrNull() ?: cur.demoDevices.firstOrNull()
      } else {
        cur.selectedEndpoint?.takeIf { !it.isSimulated } ?: cur.savedDevices.firstOrNull()
      }
      cur.copy(
        demoModeEnabled = enabled,
        selectedEndpoint = nextSelected,
        statusBannerMessage = if (enabled) {
          "Увімкнено режим демо-симулятора (віртуальні станції додані до списку)"
        } else {
          "Режим демо-симулятора вимкнено (показані лише реальні станції)"
        },
        isBannerError = false,
      )
    }
    persistCurrentConfig()
    if (_uiState.value.selectedEndpoint != null) {
      refreshAllStatuses(silentBanner = true)
    }
  }

  fun selectEndpoint(endpoint: MarstekEndpoint) {
    _uiState.update {
      it.copy(
        selectedEndpoint = endpoint,
        isConnectedOnline = false,
        deviceInfo = null,
        wifiStatus = null,
        bleStatus = null,
        batteryStatus = null,
        pvStatus = null,
        esStatus = null,
        esMode = null,
        emStatus = null,
        manualForm = it.manualForm.copy(
          includeManualSet = endpoint.profile == DeviceModelProfile.VENUS_E_MINI,
          timeNum = it.manualForm.timeNum.coerceAtMost(endpoint.profile.maxManualTimeNum),
        ),
        statusBannerMessage = "Обрано станцію: ${endpoint.name} (${endpoint.ip}:${endpoint.port})",
        isBannerError = false,
      )
    }
    persistCurrentConfig()
    refreshAllStatuses()
  }

  fun saveOrUpdateDevice(
    existingId: String? = null,
    name: String,
    ip: String,
    port: Int,
    modelName: String,
    firmwareVersion: Int?,
  ) {
    val cleanIp = ip.trim()
    if (cleanIp.isBlank()) {
      showBanner("Введіть коректну IP-адресу станції у локальній мережі (напр. 192.168.1.50)", isError = true)
      return
    }
    val validPort = port.coerceIn(1, 65535)
    val id = "$cleanIp:$validPort"
    val previous = _uiState.value.savedDevices.firstOrNull { it.id == existingId || it.id == id }
    val newEndpoint = MarstekEndpoint(
      id = id,
      name = name.trim().ifBlank { "$modelName ($cleanIp)" },
      ip = cleanIp,
      port = validPort,
      deviceModel = modelName.ifBlank { "VenusD" },
      firmwareVersion = firmwareVersion ?: previous?.firmwareVersion,
      bleMac = previous?.bleMac,
      wifiMac = previous?.wifiMac,
      wifiName = previous?.wifiName,
      isSimulated = false,
      lastSeenMillis = previous?.lastSeenMillis,
      lastKnownSoc = previous?.lastKnownSoc,
      lastKnownMode = previous?.lastKnownMode,
    )
    _uiState.update { current ->
      val filtered = current.savedDevices.filterNot { it.id == existingId || it.id == id }
      val updatedList = listOf(newEndpoint) + filtered
      current.copy(
        savedDevices = updatedList,
        selectedEndpoint = newEndpoint,
        discoveryPort = validPort.toString(),
        statusBannerMessage = "Станцію «${newEndpoint.name}» ($cleanIp:$validPort) збережено локально",
        isBannerError = false,
      )
    }
    persistCurrentConfig()
    refreshAllStatuses()
  }

  fun addManualEndpoint(
    name: String,
    ip: String,
    port: Int,
    modelName: String,
    firmwareVersion: Int?,
  ) = saveOrUpdateDevice(
    existingId = null,
    name = name,
    ip = ip,
    port = port,
    modelName = modelName,
    firmwareVersion = firmwareVersion,
  )

  fun deleteSavedDevice(deviceId: String) {
    _uiState.update { cur ->
      val remaining = cur.savedDevices.filterNot { it.id == deviceId }
      val nextSelected = if (cur.selectedEndpoint?.id == deviceId) {
        remaining.firstOrNull() ?: (if (cur.demoModeEnabled) cur.demoDevices.firstOrNull() else null)
      } else {
        cur.selectedEndpoint
      }
      cur.copy(
        savedDevices = remaining,
        selectedEndpoint = nextSelected,
        statusBannerMessage = "Станцію видалено зі збережених",
        isBannerError = false,
      )
    }
    persistCurrentConfig()
  }

  fun updateDiscoveryConfig(port: String, bleMac: String) {
    _uiState.update {
      it.copy(
        discoveryPort = port,
        discoveryBleMac = bleMac,
      )
    }
    persistCurrentConfig()
  }

  fun toggleDeepSubnetScan(enabled: Boolean) {
    _uiState.update { it.copy(deepSubnetScanEnabled = enabled) }
  }

  fun updateInstanceId(id: Int) {
    _uiState.update { it.copy(instanceId = id.coerceAtLeast(0)) }
  }

  /**
   * 2.2.2 & 3.1.1 UDP Broadcast + Subnet Sweep Discovery (`Marstek.GetDevice` + `ES.GetStatus` probe)
   * Automatically saves all discovered stations locally!
   */
  fun runLanDiscovery() {
    val state = _uiState.value
    val port = state.discoveryPort.toIntOrNull()?.coerceIn(1, 65535)
      ?: MarstekApiConstants.DEFAULT_UDP_PORT
    val bleMac = state.discoveryBleMac.trim().ifBlank { "0" }
    val deepScan = state.deepSubnetScanEnabled

    viewModelScope.launch {
      val lanInfo = udpClient.inspectLan()
      _uiState.update {
        it.copy(
          lanDiagnostics = lanInfo,
          isDiscovering = true,
          discoveryProgressText = "Підготовка пошуку в мережі ${lanInfo.localIpv4 ?: "Wi-Fi"} (порт $port)...",
          statusBannerMessage = null,
          isBannerError = false,
        )
      }

      val found = udpClient.discoverDevicesOnLan(
        port = port,
        bleMacFilter = bleMac,
        timeoutMs = if (deepScan) 4200 else 2800,
        deepSubnetScan = deepScan,
        includeSimulatedInResults = false,
        onProgress = { progressMsg ->
          _uiState.update { it.copy(discoveryProgressText = progressMsg) }
        },
      )

      _uiState.update { current ->
        // Merge discovered devices with existing saved devices, preserving custom names if already saved
        val updatedSaved = current.savedDevices.toMutableList()
        for (disc in found) {
          val existingIdx = updatedSaved.indexOfFirst { it.id == disc.id || it.ip == disc.ip }
          if (existingIdx >= 0) {
            val prev = updatedSaved[existingIdx]
            updatedSaved[existingIdx] = disc.copy(
              name = prev.name.ifBlank { disc.name },
              firmwareVersion = disc.firmwareVersion ?: prev.firmwareVersion,
              bleMac = disc.bleMac ?: prev.bleMac,
              wifiMac = disc.wifiMac ?: prev.wifiMac,
              wifiName = disc.wifiName ?: prev.wifiName,
              lastSeenMillis = System.currentTimeMillis(),
              lastKnownSoc = disc.lastKnownSoc ?: prev.lastKnownSoc,
            )
          } else {
            updatedSaved.add(0, disc.copy(lastSeenMillis = System.currentTimeMillis()))
          }
        }

        val chosen = found.firstOrNull()?.let { f ->
          updatedSaved.firstOrNull { it.ip == f.ip } ?: f
        } ?: current.selectedEndpoint

        val message = if (found.isNotEmpty()) {
          "Знайдено та збережено станцій у мережі: ${found.size} (${found.joinToString { "${it.deviceModel} @ ${it.ip}" }})"
        } else {
          "На порту UDP $port станцій не виявлено (Ваш Wi-Fi IP: ${lanInfo.localIpv4 ?: "не визначено"}). " +
            "Перевірте, чи увімкнено Open API у офіційному додатку Marstek і який порт там задано, або додайте IP станції вручну."
        }
        current.copy(
          isDiscovering = false,
          discoveryProgressText = null,
          savedDevices = updatedSaved,
          selectedEndpoint = chosen,
          statusBannerMessage = message,
          isBannerError = found.isEmpty(),
        )
      }
      persistCurrentConfig()
      if (found.isNotEmpty()) {
        refreshAllStatuses(silentBanner = true)
      }
    }
  }

  fun toggleAutoPoll(enabled: Boolean) {
    _uiState.update { it.copy(autoPollEnabled = enabled) }
    autoPollJob?.cancel()
    if (enabled) {
      autoPollJob = viewModelScope.launch {
        while (isActive) {
          refreshAllStatuses(silentBanner = true)
          delay(5000L)
        }
      }
    }
  }

  /**
   * Queries status components sequentially (serialized by MarstekUdpClient mutex)
   * and updates local cached device metadata whenever the station responds.
   */
  fun refreshAllStatuses(silentBanner: Boolean = false) {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instanceId = _uiState.value.instanceId

    viewModelScope.launch {
      refreshLanDiagnostics()
      _uiState.update { it.copy(isLoadingAll = true) }

      // Query primary ES.GetStatus and Bat.GetStatus first for fast dashboard response
      val esStatusRes = udpClient.getEsStatus(endpoint, instanceId)
      val batRes = udpClient.getBatteryStatus(endpoint, instanceId)
      val esModeRes = udpClient.getEsMode(endpoint, instanceId)
      val devRes = udpClient.getDevice(endpoint, "0")
      val wifiRes = udpClient.getWifiStatus(endpoint, instanceId)
      val bleRes = udpClient.getBleStatus(endpoint, instanceId)
      val emRes = udpClient.getEmStatus(endpoint, instanceId)
      val pvRes = if (endpoint.profile.supportsPv) {
        udpClient.getPvStatus(endpoint, instanceId)
      } else {
        null
      }

      val anySuccess = listOfNotNull(esStatusRes, batRes, esModeRes, devRes, wifiRes, bleRes, emRes, pvRes)
        .any { it is ApiCallResult.Success }

      _uiState.update { cur ->
        var next = cur.copy(
          isLoadingAll = false,
          isConnectedOnline = anySuccess,
          lastSuccessfulSyncMillis = if (anySuccess) System.currentTimeMillis() else cur.lastSuccessfulSyncMillis,
        )

        var resolvedModel = endpoint.deviceModel
        var resolvedFw = endpoint.firmwareVersion
        var resolvedBleMac = endpoint.bleMac
        var resolvedWifiMac = endpoint.wifiMac
        var resolvedWifiName = endpoint.wifiName
        var resolvedSoc = endpoint.lastKnownSoc
        var resolvedMode = endpoint.lastKnownMode

        if (devRes is ApiCallResult.Success) {
          next = next.copy(deviceInfo = devRes.value)
          if (devRes.value.device.isNotBlank() && devRes.value.device != "Unknown") {
            resolvedModel = devRes.value.device
          }
          if (devRes.value.ver != null) resolvedFw = devRes.value.ver
          if (!devRes.value.bleMac.isNullOrBlank()) resolvedBleMac = devRes.value.bleMac
          if (!devRes.value.wifiMac.isNullOrBlank()) resolvedWifiMac = devRes.value.wifiMac
          if (!devRes.value.wifiName.isNullOrBlank()) resolvedWifiName = devRes.value.wifiName
        }
        if (wifiRes is ApiCallResult.Success) {
          next = next.copy(wifiStatus = wifiRes.value)
          if (!wifiRes.value.wifiMac.isNullOrBlank()) resolvedWifiMac = wifiRes.value.wifiMac
          if (!wifiRes.value.ssid.isNullOrBlank()) resolvedWifiName = wifiRes.value.ssid
        }
        if (bleRes is ApiCallResult.Success) {
          next = next.copy(bleStatus = bleRes.value)
          if (!bleRes.value.bleMac.isNullOrBlank()) resolvedBleMac = bleRes.value.bleMac
        }
        if (batRes is ApiCallResult.Success) {
          next = next.copy(batteryStatus = batRes.value)
          if (batRes.value.soc != null) resolvedSoc = batRes.value.soc.toInt()
        }
        if (esStatusRes is ApiCallResult.Success) {
          next = next.copy(esStatus = esStatusRes.value)
          if (esStatusRes.value.batSoc != null) resolvedSoc = esStatusRes.value.batSoc.toInt()
        }
        if (esModeRes is ApiCallResult.Success) {
          next = next.copy(esMode = esModeRes.value)
          if (!esModeRes.value.mode.isNullOrBlank()) resolvedMode = esModeRes.value.mode
          if (esModeRes.value.batSoc != null && resolvedSoc == null) {
            resolvedSoc = esModeRes.value.batSoc.toInt()
          }
        }
        if (emRes is ApiCallResult.Success) {
          next = next.copy(emStatus = emRes.value)
        }
        if (pvRes is ApiCallResult.Success) {
          next = next.copy(pvStatus = pvRes.value)
        }

        // Update cached station metadata in savedDevices if online
        if (anySuccess && !endpoint.isSimulated) {
          val updatedEndpoint = endpoint.copy(
            deviceModel = resolvedModel,
            firmwareVersion = resolvedFw,
            bleMac = resolvedBleMac,
            wifiMac = resolvedWifiMac,
            wifiName = resolvedWifiName,
            lastSeenMillis = System.currentTimeMillis(),
            lastKnownSoc = resolvedSoc,
            lastKnownMode = resolvedMode,
          )
          val updatedList = next.savedDevices.map {
            if (it.id == endpoint.id) updatedEndpoint else it
          }
          next = next.copy(
            savedDevices = updatedList,
            selectedEndpoint = if (next.selectedEndpoint?.id == endpoint.id) updatedEndpoint else next.selectedEndpoint,
          )
        }

        if (!silentBanner) {
          if (anySuccess) {
            next = next.copy(
              statusBannerMessage = "Зв'язок зі станцією «${endpoint.name}» встановлено, дані оновлено",
              isBannerError = false,
            )
          } else {
            val firstFailure = listOfNotNull(esStatusRes, batRes, devRes)
              .firstOrNull { it !is ApiCallResult.Success }
            next = when (firstFailure) {
              is ApiCallResult.RpcFailure -> next.copy(
                statusBannerMessage = "Помилка протоколу (${firstFailure.error.code}): ${firstFailure.error.message} — ${firstFailure.error.standardMeaning}",
                isBannerError = true,
              )
              is ApiCallResult.TransportFailure -> next.copy(
                statusBannerMessage = firstFailure.reason,
                isBannerError = true,
              )
              else -> next
            }
          }
        }
        next
      }
      if (anySuccess && !endpoint.isSimulated) {
        persistCurrentConfig()
      }
    }
  }

  // Individual Component Queries (3.1 - 3.7)
  fun queryDeviceInfo(customBleMac: String? = null) {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val mac = customBleMac ?: _uiState.value.discoveryBleMac.ifBlank { "0" }
    viewModelScope.launch {
      when (val res = udpClient.getDevice(endpoint, mac)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              deviceInfo = res.value,
              isConnectedOnline = true,
              lastSuccessfulSyncMillis = System.currentTimeMillis(),
              statusBannerMessage = "Отримано дані станції: ${res.value.device} (FW ${res.value.ver ?: "?"}) @ ${res.value.ip}",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("Marstek.GetDevice", res)
      }
    }
  }

  fun queryWifiStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getWifiStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              wifiStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Wi-Fi станції: мережа «${res.value.ssid}», сигнал ${res.value.rssi} dBm",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("Wifi.GetStatus", res)
      }
    }
  }

  fun queryBleStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getBleStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              bleStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Bluetooth станції: статус=${res.value.state}, MAC=${res.value.bleMac}",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("BLE.GetStatus", res)
      }
    }
  }

  fun queryBatteryStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getBatteryStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              batteryStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Батарея: заряд ${res.value.soc}%, температура ${res.value.batTemp}°C",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("Bat.GetStatus", res)
      }
    }
  }

  fun queryPvStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getPvStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              pvStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Сонячні панелі: загальна потужність ${res.value.totalPvPowerWatts} Вт (${res.value.channels.size} MPPT кан.)",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("PV.GetStatus", res)
      }
    }
  }

  fun queryEsStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getEsStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              esStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Енергосистема: заряд ${res.value.batSoc}%, потужність мережі ${res.value.ongridPower} Вт",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("ES.GetStatus", res)
      }
    }
  }

  fun queryEsMode() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getEsMode(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              esMode = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Поточний режим роботи: ${res.value.mode}",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("ES.GetMode", res)
      }
    }
  }

  fun queryEmStatus() {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instId = _uiState.value.instanceId
    viewModelScope.launch {
      when (val res = udpClient.getEmStatus(endpoint, instId)) {
        is ApiCallResult.Success -> {
          _uiState.update {
            it.copy(
              emStatus = res.value,
              isConnectedOnline = true,
              statusBannerMessage = "Лічильник CT: ${if (res.value.isCtConnected) "Підключено" else "Не підключено"}, сумарна потужність ${res.value.totalPower} Вт",
              isBannerError = false,
            )
          }
        }
        else -> handleFailureBanner("EM.GetStatus", res)
      }
    }
  }

  // ============================================================================
  // 3.6.2 ES.SetMode Form & Execution
  // ============================================================================

  fun selectEsModeTab(mode: EsOperatingMode) {
    _uiState.update { it.copy(selectedEsModeTab = mode) }
  }

  fun updateAutoModeEnable(enable: Boolean) {
    _uiState.update { it.copy(autoModeEnable = enable) }
  }

  fun updateAiModeEnable(enable: Boolean) {
    _uiState.update { it.copy(aiModeEnable = enable) }
  }

  fun updateManualForm(transform: (ManualModeFormState) -> ManualModeFormState) {
    _uiState.update { it.copy(manualForm = transform(it.manualForm)) }
  }

  fun updatePassiveForm(transform: (PassiveModeFormState) -> PassiveModeFormState) {
    _uiState.update { it.copy(passiveForm = transform(it.passiveForm)) }
  }

  fun updateUpsForm(transform: (UpsModeFormState) -> UpsModeFormState) {
    _uiState.update { it.copy(upsForm = transform(it.upsForm)) }
  }

  fun applySelectedEsMode() {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = state.instanceId

    val config: EsSetModeConfig = when (state.selectedEsModeTab) {
      EsOperatingMode.AUTO -> EsSetModeConfig.Auto(enable = if (state.autoModeEnable) 1 else 0)
      EsOperatingMode.AI -> EsSetModeConfig.Ai(enable = if (state.aiModeEnable) 1 else 0)
      EsOperatingMode.MANUAL -> {
        val mf = state.manualForm
        val pwr = mf.powerWatts.toIntOrNull() ?: 100
        EsSetModeConfig.Manual(
          timeNum = mf.timeNum.coerceIn(0, 9),
          startTime = mf.startTime.trim().ifBlank { "08:30" },
          endTime = mf.endTime.trim().ifBlank { "20:30" },
          weekSet = mf.weekSet and 0x7F,
          power = pwr,
          enable = if (mf.enable) 1 else 0,
          manualSet = if (mf.includeManualSet) mf.manualSetCode else null,
        )
      }
      EsOperatingMode.PASSIVE -> {
        val pf = state.passiveForm
        EsSetModeConfig.Passive(
          power = pf.powerWatts.toIntOrNull() ?: 100,
          cdTime = pf.cdTimeSeconds.toIntOrNull() ?: 300,
        )
      }
      EsOperatingMode.UPS -> {
        val uf = state.upsForm
        EsSetModeConfig.Ups(
          enable = if (uf.enable) 1 else 0,
          useUppercaseWireMode = uf.useUppercaseWireMode,
        )
      }
    }

    viewModelScope.launch {
      when (val res = udpClient.setEsMode(endpoint, config, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          showBanner(
            message = if (ok) {
              "Режим «${state.selectedEsModeTab.displayName}» успішно застосовано!"
            } else {
              "Станція повернула відмову (set_result = false) при зміні режиму"
            },
            isError = !ok,
          )
          queryEsMode()
          queryEsStatus()
        }
        else -> handleFailureBanner("ES.SetMode", res)
      }
    }
  }

  // ============================================================================
  // 3.8 SYS Control Commands (DOD.SET, Ble.Adv, Led.Ctrl, Set.Ver, Reset.Factory)
  // ============================================================================

  fun updateDodSlider(value: Int) {
    _uiState.update {
      it.copy(dodSliderValue = value.coerceIn(MarstekApiConstants.MIN_DOD_VALUE, MarstekApiConstants.MAX_DOD_VALUE))
    }
  }

  fun updateCustomVersionWatts(watts: String) {
    _uiState.update { it.copy(customVersionWatts = watts) }
  }

  fun toggleIncludeInstanceIdInSys(include: Boolean) {
    _uiState.update { it.copy(includeInstanceIdInSys = include) }
  }

  /** 3.8.1 DOD.SET */
  fun sendDodSet(dodValue: Int = _uiState.value.dodSliderValue) {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = if (state.includeInstanceIdInSys) state.instanceId else null
    viewModelScope.launch {
      when (val res = udpClient.setDod(endpoint, dodValue, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          showBanner(
            message = if (ok) "Глибину розряду (DOD) встановлено на $dodValue% (резерв ${100 - dodValue}%)" else "Не вдалося змінити DOD (set_result=false)",
            isError = !ok,
          )
          queryBatteryStatus()
        }
        else -> handleFailureBanner("DOD.SET", res)
      }
    }
  }

  /** 3.8.2 Ble.Adv (0: enable broadcasting, 1: disable/lock broadcasting) */
  fun sendBleAdv(enableCode: Int) {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = if (state.includeInstanceIdInSys) state.instanceId else null
    viewModelScope.launch {
      when (val res = udpClient.setBleAdv(endpoint, enableCode, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          val desc = if (enableCode == 0) "Bluetooth-трансляцію увімкнено" else "Bluetooth-трансляцію заблоковано"
          showBanner("$desc (set_result = $ok)", isError = !ok)
          queryBleStatus()
        }
        else -> handleFailureBanner("Ble.Adv", res)
      }
    }
  }

  /** 3.8.3 Led.Ctrl (1: on, 0: off) */
  fun sendLedCtrl(stateCode: Int) {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = if (state.includeInstanceIdInSys) state.instanceId else null
    viewModelScope.launch {
      when (val res = udpClient.setLedCtrl(endpoint, stateCode, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          val desc = if (stateCode == 1) "LED-індикацію панелі увімкнено" else "LED-індикацію панелі вимкнено"
          showBanner("$desc (set_result = $ok)", isError = !ok)
        }
        else -> handleFailureBanner("Led.Ctrl", res)
      }
    }
  }

  /** 3.8.4 Set.Ver (800, 1200, 1500, 2200, 2500) */
  fun sendSetVersion(versionWatts: Int) {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = if (state.includeInstanceIdInSys) state.instanceId else null
    _uiState.update { it.copy(customVersionWatts = versionWatts.toString()) }
    viewModelScope.launch {
      when (val res = udpClient.setPowerVersion(endpoint, versionWatts, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          showBanner("Ліміт потужності інвертора встановлено на ${versionWatts} Вт (set_result = $ok)", isError = !ok)
        }
        else -> handleFailureBanner("Set.Ver", res)
      }
    }
  }

  /** 3.8.5 Reset.Factory (1: clear accumulated data, 2: keep accumulated data) */
  fun sendFactoryReset(typeCode: Int) {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val instId = if (state.includeInstanceIdInSys) state.instanceId else null
    viewModelScope.launch {
      when (val res = udpClient.resetFactory(endpoint, typeCode, instId)) {
        is ApiCallResult.Success -> {
          val ok = res.value.setResult
          showBanner("Команду скидання налаштувань виконано (type=$typeCode, set_result=$ok)", isError = !ok)
          refreshAllStatuses(silentBanner = true)
        }
        else -> handleFailureBanner("Reset.Factory", res)
      }
    }
  }

  // ============================================================================
  // Raw UDP API Console & Log Export
  // ============================================================================

  fun selectConsoleMethodTemplate(method: String) {
    _uiState.update {
      it.copy(
        consoleSelectedMethod = method,
        consoleRawRequest = MarstekJsonCodec.getTemplateForMethod(method),
      )
    }
  }

  fun updateConsoleRawRequest(rawJson: String) {
    _uiState.update { it.copy(consoleRawRequest = rawJson) }
  }

  fun sendConsoleRawRequest() {
    val state = _uiState.value
    val endpoint = state.selectedEndpoint ?: return
    val rawReq = state.consoleRawRequest.trim()
    if (rawReq.isBlank()) return

    viewModelScope.launch {
      val (result, latency) = udpClient.sendRawCommand(
        endpoint = endpoint,
        methodLabel = state.consoleSelectedMethod,
        rawJsonRequest = rawReq,
      )
      result.fold(
        onSuccess = { rawResp ->
          val pretty = MarstekJsonCodec.formatPretty(rawResp)
          _uiState.update {
            it.copy(
              consoleLastResponse = pretty,
              isConnectedOnline = true,
              statusBannerMessage = "Отримано UDP відповідь за $latency мс",
              isBannerError = rawResp.contains("\"error\""),
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              consoleLastResponse = "// Помилка UDP запиту:\n${err.message}",
              statusBannerMessage = err.message ?: "Помилка відправки",
              isBannerError = true,
            )
          }
        },
      )
    }
  }

  fun buildDiagnosticLogText(): String {
    val state = _uiState.value
    val lanInfo = udpClient.inspectLan()
    return LogReportFormatter.buildDiagnosticReport(
      lanInfo = lanInfo,
      selectedEndpoint = state.selectedEndpoint,
      savedDevices = state.savedDevices,
      logs = packetLogs.value,
    )
  }

  fun notifyBanner(message: String, isError: Boolean = false) {
    showBanner(message, isError)
  }

  fun clearLogs() {
    udpClient.clearPacketLogs()
    showBanner("Журнал пакетів очищено", isError = false)
  }

  private fun showBanner(message: String, isError: Boolean) {
    _uiState.update {
      it.copy(
        statusBannerMessage = message,
        isBannerError = isError,
      )
    }
  }

  private fun handleFailureBanner(method: String, result: ApiCallResult<*>) {
    when (result) {
      is ApiCallResult.RpcFailure -> {
        showBanner(
          message = "$method -> Помилка [${result.error.code}] ${result.error.message}: ${result.error.standardMeaning}",
          isError = true,
        )
      }
      is ApiCallResult.TransportFailure -> {
        showBanner(
          message = result.reason,
          isError = true,
        )
      }
      else -> Unit
    }
  }
}
