package org.marsopenclient.ui.main

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
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint
import org.marsopenclient.api.MarstekJsonCodec
import org.marsopenclient.api.MarstekUdpClient
import org.marsopenclient.api.PacketLogEntry
import org.marsopenclient.api.PvStatusResult
import org.marsopenclient.api.WifiStatusResult

enum class AppSection(val titleUk: String) {
  DEVICES("Пристрої"),
  DASHBOARD("Моніторинг"),
  ES_MODE("Режими ES"),
  SYS_CONTROL("Система SYS"),
  CONSOLE("Консоль API"),
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
  val endpoints: List<MarstekEndpoint> = emptyList(),
  val selectedEndpoint: MarstekEndpoint? = null,
  val instanceId: Int = 0,
  val discoveryPort: String = MarstekApiConstants.DEFAULT_UDP_PORT.toString(),
  val discoveryBleMac: String = "0",
  val isDiscovering: Boolean = false,
  val isLoadingAll: Boolean = false,
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
)

class MarstekDashboardViewModel(
  private val udpClient: MarstekUdpClient = MarstekUdpClient(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(MarstekUiState())
  val uiState: StateFlow<MarstekUiState> = _uiState.asStateFlow()

  val packetLogs: StateFlow<List<PacketLogEntry>> = udpClient.packetLogs.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  private var autoPollJob: Job? = null

  init {
    val defaultSims = udpClient.getSimulatedEndpoints()
    val initialEndpoint = defaultSims.firstOrNull()
    _uiState.update {
      it.copy(
        endpoints = defaultSims,
        selectedEndpoint = initialEndpoint,
        manualForm = it.manualForm.copy(
          includeManualSet = initialEndpoint?.profile == DeviceModelProfile.VENUS_E_MINI
        ),
      )
    }
    if (initialEndpoint != null) {
      refreshAllStatuses()
    }
  }

  fun selectSection(section: AppSection) {
    _uiState.update { it.copy(currentSection = section) }
  }

  fun dismissBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  fun selectEndpoint(endpoint: MarstekEndpoint) {
    _uiState.update {
      it.copy(
        selectedEndpoint = endpoint,
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
        statusBannerMessage = "Обрано пристрій: ${endpoint.name} (${endpoint.ip}:${endpoint.port})",
        isBannerError = false,
      )
    }
    refreshAllStatuses()
  }

  fun addManualEndpoint(
    name: String,
    ip: String,
    port: Int,
    modelName: String,
    firmwareVersion: Int?,
  ) {
    val cleanIp = ip.trim()
    if (cleanIp.isBlank()) {
      showBanner("Введіть коректну IP-адресу пристрою в локальній мережі", isError = true)
      return
    }
    val validPort = port.coerceIn(1, 65535)
    val id = "$cleanIp:$validPort"
    val newEndpoint = MarstekEndpoint(
      id = id,
      name = name.trim().ifBlank { "$modelName ($cleanIp)" },
      ip = cleanIp,
      port = validPort,
      deviceModel = modelName.ifBlank { "VenusD" },
      firmwareVersion = firmwareVersion,
      isSimulated = false,
    )
    _uiState.update { current ->
      val updatedList = (listOf(newEndpoint) + current.endpoints.filterNot { it.id == id })
      current.copy(
        endpoints = updatedList,
        selectedEndpoint = newEndpoint,
        statusBannerMessage = "Додано пристрій ${newEndpoint.name} ($cleanIp:$validPort)",
        isBannerError = false,
      )
    }
    refreshAllStatuses()
  }

  fun updateDiscoveryConfig(port: String, bleMac: String) {
    _uiState.update {
      it.copy(
        discoveryPort = port,
        discoveryBleMac = bleMac,
      )
    }
  }

  fun updateInstanceId(id: Int) {
    _uiState.update { it.copy(instanceId = id.coerceAtLeast(0)) }
  }

  /**
   * 2.2.2 & 3.1.1 UDP Broadcast Discovery (`Marstek.GetDevice`)
   */
  fun runLanDiscovery() {
    val state = _uiState.value
    val port = state.discoveryPort.toIntOrNull()?.coerceIn(1, 65535)
      ?: MarstekApiConstants.DEFAULT_UDP_PORT
    val bleMac = state.discoveryBleMac.trim().ifBlank { "0" }

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isDiscovering = true,
          statusBannerMessage = "Пошук пристроїв Marstek у LAN через UDP Broadcast (порт $port, ble_mac=\"$bleMac\")...",
          isBannerError = false,
        )
      }
      val found = udpClient.discoverDevicesOnLan(
        port = port,
        bleMacFilter = bleMac,
        timeoutMs = 2200,
        includeSimulatedInResults = false,
      )
      _uiState.update { current ->
        val merged = (found + current.endpoints).distinctBy { it.id }
        val chosen = found.firstOrNull() ?: current.selectedEndpoint
        val message = if (found.isNotEmpty()) {
          "Знайдено пристроїв у локальній мережі: ${found.size} (${found.joinToString { "${it.deviceModel}@${it.ip}" }})"
        } else {
          "У локальній мережі не знайдено відповідей на UDP порт $port. Перевірте Wi-Fi та налаштування Open API в додатку Marstek (або скористайтеся демо-симулятором)."
        }
        current.copy(
          isDiscovering = false,
          endpoints = merged,
          selectedEndpoint = chosen,
          statusBannerMessage = message,
          isBannerError = found.isEmpty(),
        )
      }
      if (found.isNotEmpty()) {
        refreshAllStatuses()
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
   * Queries all status components (3.1 Marstek, 3.2 WiFi, 3.3 BLE, 3.4 Bat, 3.5 PV, 3.6 ES, 3.7 EM)
   */
  fun refreshAllStatuses(silentBanner: Boolean = false) {
    val endpoint = _uiState.value.selectedEndpoint ?: return
    val instanceId = _uiState.value.instanceId

    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingAll = true) }

      val devRes = udpClient.getDevice(endpoint, endpoint.bleMac ?: "0")
      val wifiRes = udpClient.getWifiStatus(endpoint, instanceId)
      val bleRes = udpClient.getBleStatus(endpoint, instanceId)
      val batRes = udpClient.getBatteryStatus(endpoint, instanceId)
      val esStatusRes = udpClient.getEsStatus(endpoint, instanceId)
      val esModeRes = udpClient.getEsMode(endpoint, instanceId)
      val emRes = udpClient.getEmStatus(endpoint, instanceId)
      val pvRes = if (endpoint.profile.supportsPv) {
        udpClient.getPvStatus(endpoint, instanceId)
      } else {
        null
      }

      _uiState.update { cur ->
        var next = cur.copy(isLoadingAll = false)
        if (devRes is ApiCallResult.Success) {
          next = next.copy(deviceInfo = devRes.value)
        }
        if (wifiRes is ApiCallResult.Success) {
          next = next.copy(wifiStatus = wifiRes.value)
        }
        if (bleRes is ApiCallResult.Success) {
          next = next.copy(bleStatus = bleRes.value)
        }
        if (batRes is ApiCallResult.Success) {
          next = next.copy(batteryStatus = batRes.value)
        }
        if (esStatusRes is ApiCallResult.Success) {
          next = next.copy(esStatus = esStatusRes.value)
        }
        if (esModeRes is ApiCallResult.Success) {
          next = next.copy(esMode = esModeRes.value)
        }
        if (emRes is ApiCallResult.Success) {
          next = next.copy(emStatus = emRes.value)
        }
        if (pvRes is ApiCallResult.Success) {
          next = next.copy(pvStatus = pvRes.value)
        }

        if (!silentBanner) {
          val anyError = listOfNotNull(devRes, wifiRes, bleRes, batRes, esStatusRes, esModeRes, emRes)
            .firstOrNull { it !is ApiCallResult.Success }
          next = when (anyError) {
            is ApiCallResult.RpcFailure -> next.copy(
              statusBannerMessage = "Помилка RPC (${anyError.error.code}): ${anyError.error.message} — ${anyError.error.standardMeaning}",
              isBannerError = true,
            )
            is ApiCallResult.TransportFailure -> next.copy(
              statusBannerMessage = anyError.reason,
              isBannerError = true,
            )
            else -> next.copy(
              statusBannerMessage = "Дані пристрою ${endpoint.name} успішно оновлено",
              isBannerError = false,
            )
          }
        }
        next
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
              statusBannerMessage = "Marstek.GetDevice: ${res.value.device} (FW ${res.value.ver ?: "?"}) @ ${res.value.ip}",
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
              statusBannerMessage = "Wifi.GetStatus: SSID=${res.value.ssid}, RSSI=${res.value.rssi} dBm",
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
              statusBannerMessage = "BLE.GetStatus: state=${res.value.state}, mac=${res.value.bleMac}",
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
              statusBannerMessage = "Bat.GetStatus: SOC=${res.value.soc}%, Temp=${res.value.batTemp}°C",
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
              statusBannerMessage = "PV.GetStatus: Загальна потужність PV = ${res.value.totalPvPowerWatts} W (${res.value.channels.size} кан.)",
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
              statusBannerMessage = "ES.GetStatus: SOC=${res.value.batSoc}%, OnGrid=${res.value.ongridPower}W",
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
              statusBannerMessage = "ES.GetMode: Поточний режим = ${res.value.mode}",
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
              statusBannerMessage = "EM.GetStatus: CT=${if (res.value.isCtConnected) "Підключено (1)" else "Не підключено (0)"}, P=${res.value.totalPower}W",
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
            message = "ES.SetMode (${state.selectedEsModeTab.wireName}): set_result = $ok",
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
          showBanner("DOD.SET ($dodValue%): set_result = $ok", isError = !ok)
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
          val desc = if (enableCode == 0) "0 (Увімкнено трансляцію BLE)" else "1 (Заблоковано трансляцію BLE)"
          showBanner("Ble.Adv ($desc): set_result = $ok", isError = !ok)
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
          val desc = if (stateCode == 1) "1 (LED Увімкнено)" else "0 (LED Вимкнено)"
          showBanner("Led.Ctrl ($desc): set_result = $ok", isError = !ok)
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
          showBanner("Set.Ver (${versionWatts}W): set_result = $ok", isError = !ok)
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
          showBanner("Reset.Factory (type=$typeCode): set_result = $ok", isError = !ok)
          refreshAllStatuses(silentBanner = true)
        }
        else -> handleFailureBanner("Reset.Factory", res)
      }
    }
  }

  // ============================================================================
  // Raw UDP API Console
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

  fun clearLogs() {
    udpClient.clearPacketLogs()
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
          message = "$method -> ${result.reason}",
          isError = true,
        )
      }
      else -> Unit
    }
  }
}
