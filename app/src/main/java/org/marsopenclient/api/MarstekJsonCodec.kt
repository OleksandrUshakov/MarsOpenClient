package org.marsopenclient.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Pure Kotlin JSON-RPC codec for Marstek Device Open API (Rev 3.1).
 * Handles all request builders, response parsers, and Marstek firmware quirks
 * (such as `"set_result": ture`, string/numeric `soc` and `mode`, and 1-ch vs 4-ch PV).
 */
object MarstekJsonCodec {

  private val compactJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  private val prettyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
  }

  // Matches unquoted `ture` or `flase` after a colon in malformed firmware responses (see PDF pages 14, 18-21)
  private val TYPO_TURE_REGEX = Regex("""(:\s*)ture(\s*[,}\]])""", RegexOption.IGNORE_CASE)
  private val TYPO_FAI_REGEX = Regex("""(:\s*)fai(?:l|se)?(\s*[,}\]])""", RegexOption.IGNORE_CASE)

  /**
   * Sanitizes raw firmware JSON text before parsing.
   */
  fun sanitizeFirmwareJson(raw: String): String {
    var cleaned = raw.trim()
    cleaned = TYPO_TURE_REGEX.replace(cleaned, "$1true$2")
    cleaned = TYPO_FAI_REGEX.replace(cleaned, "$1false$2")
    return cleaned
  }

  fun formatPretty(raw: String): String {
    return try {
      val sanitized = sanitizeFirmwareJson(raw)
      val element = compactJson.parseToJsonElement(sanitized)
      prettyJson.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
      raw
    }
  }

  // ============================================================================
  // Request Builders (Chapter 3.1 - 3.8)
  // ============================================================================

  /** 3.1.1 Marstek.GetDevice */
  fun buildGetDeviceRequest(
    requestId: Int = 0,
    bleMac: String = "0",
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE)
      putJsonObject("params") {
        put("ble_mac", bleMac)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** Generic status request with `params: { "id": instanceId }` */
  fun buildInstanceStatusRequest(
    method: String,
    requestId: Int = 1,
    instanceId: Int = 0,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", method)
      putJsonObject("params") {
        put("id", instanceId)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.2.1 Wifi.GetStatus */
  fun buildWifiGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_WIFI_GET_STATUS, requestId, instanceId)

  /** 3.3.1 BLE.GetStatus */
  fun buildBleGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_BLE_GET_STATUS, requestId, instanceId)

  /** 3.4.1 Bat.GetStatus */
  fun buildBatGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_BAT_GET_STATUS, requestId, instanceId)

  /** 3.5.1 PV.GetStatus */
  fun buildPvGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_PV_GET_STATUS, requestId, instanceId)

  /** 3.6.1 ES.GetStatus */
  fun buildEsGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_ES_GET_STATUS, requestId, instanceId)

  /** 3.6.3 ES.GetMode */
  fun buildEsGetModeRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_ES_GET_MODE, requestId, instanceId)

  /** 3.7.1 EM.GetStatus */
  fun buildEmGetStatusRequest(requestId: Int = 1, instanceId: Int = 0): String =
    buildInstanceStatusRequest(MarstekApiConstants.METHOD_EM_GET_STATUS, requestId, instanceId)

  /** 3.6.2 ES.SetMode */
  fun buildEsSetModeRequest(
    config: EsSetModeConfig,
    requestId: Int = 1,
    instanceId: Int = 0,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_ES_SET_MODE)
      putJsonObject("params") {
        put("id", instanceId)
        putJsonObject("config") {
          when (config) {
            is EsSetModeConfig.Auto -> {
              put("mode", "Auto")
              putJsonObject("auto_cfg") {
                put("enable", config.enable)
              }
            }
            is EsSetModeConfig.Ai -> {
              put("mode", "AI")
              putJsonObject("ai_cfg") {
                put("enable", config.enable)
              }
            }
            is EsSetModeConfig.Manual -> {
              put("mode", "Manual")
              putJsonObject("manual_cfg") {
                put("time_num", config.timeNum)
                put("start_time", config.startTime)
                put("end_time", config.endTime)
                put("week_set", config.weekSet and 0x7F)
                put("power", config.power)
                put("enable", config.enable)
                if (config.manualSet != null) {
                  put("manual_set", config.manualSet)
                }
              }
            }
            is EsSetModeConfig.Passive -> {
              put("mode", "Passive")
              putJsonObject("passive_cfg") {
                put("power", config.power)
                put("cd_time", config.cdTime)
              }
            }
            is EsSetModeConfig.Ups -> {
              put("mode", if (config.useUppercaseWireMode) "UPS" else "Ups")
              putJsonObject("ups_cfg") {
                put("enable", config.enable)
              }
            }
          }
        }
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.8.1 DOD.SET (range 30-88, default 88) */
  fun buildDodSetRequest(
    value: Int,
    requestId: Int = 0,
    includeInstanceId: Int? = null,
  ): String {
    val clamped = value.coerceIn(MarstekApiConstants.MIN_DOD_VALUE, MarstekApiConstants.MAX_DOD_VALUE)
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_DOD_SET)
      putJsonObject("params") {
        if (includeInstanceId != null) put("id", includeInstanceId)
        put("value", clamped)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.8.2 Ble.Adv (0: enable, 1: disable) */
  fun buildBleAdvRequest(
    enable: Int,
    requestId: Int = 0,
    includeInstanceId: Int? = null,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_BLE_ADV)
      putJsonObject("params") {
        if (includeInstanceId != null) put("id", includeInstanceId)
        put("enable", if (enable == 0) 0 else 1)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.8.3 Led.Ctrl (1: on, 0: off) */
  fun buildLedCtrlRequest(
    state: Int,
    requestId: Int = 0,
    includeInstanceId: Int? = null,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_LED_CTRL)
      putJsonObject("params") {
        if (includeInstanceId != null) put("id", includeInstanceId)
        put("state", if (state == 1) 1 else 0)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.8.4 Set.Ver (800, 1200, 1500, 2200, 2500) */
  fun buildSetVerRequest(
    versionWatts: Int,
    requestId: Int = 0,
    includeInstanceId: Int? = null,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_SET_VER)
      putJsonObject("params") {
        if (includeInstanceId != null) put("id", includeInstanceId)
        put("version", versionWatts)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /** 3.8.5 Reset.Factory (1: clear accumulated data, 2: keep accumulated data) */
  fun buildResetFactoryRequest(
    type: Int,
    requestId: Int = 0,
    includeInstanceId: Int? = null,
  ): String {
    val obj = buildJsonObject {
      put("id", requestId)
      put("method", MarstekApiConstants.METHOD_RESET_FACTORY)
      putJsonObject("params") {
        if (includeInstanceId != null) put("id", includeInstanceId)
        put("type", if (type == 2) 2 else 1)
      }
    }
    return compactJson.encodeToString(JsonObject.serializer(), obj)
  }

  /**
   * Returns a ready-to-edit JSON template for any method in the API Console.
   */
  fun getTemplateForMethod(method: String): String {
    val raw = when (method) {
      MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE -> buildGetDeviceRequest(0, "0")
      MarstekApiConstants.METHOD_WIFI_GET_STATUS -> buildWifiGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_BLE_GET_STATUS -> buildBleGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_BAT_GET_STATUS -> buildBatGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_PV_GET_STATUS -> buildPvGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_ES_GET_STATUS -> buildEsGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_ES_SET_MODE -> buildEsSetModeRequest(
        EsSetModeConfig.Manual(
          timeNum = 1,
          startTime = "08:30",
          endTime = "20:30",
          weekSet = 127,
          power = 100,
          enable = 1,
          manualSet = 1,
        )
      )
      MarstekApiConstants.METHOD_ES_GET_MODE -> buildEsGetModeRequest(1, 0)
      MarstekApiConstants.METHOD_EM_GET_STATUS -> buildEmGetStatusRequest(1, 0)
      MarstekApiConstants.METHOD_DOD_SET -> buildDodSetRequest(88, 0)
      MarstekApiConstants.METHOD_BLE_ADV -> buildBleAdvRequest(0, 0)
      MarstekApiConstants.METHOD_LED_CTRL -> buildLedCtrlRequest(1, 0)
      MarstekApiConstants.METHOD_SET_VER -> buildSetVerRequest(2500, 0)
      MarstekApiConstants.METHOD_RESET_FACTORY -> buildResetFactoryRequest(2, 0)
      else -> buildGetDeviceRequest(0, "0")
    }
    return formatPretty(raw)
  }

  // ============================================================================
  // Response Parsers
  // ============================================================================

  fun <T> parseResponse(
    rawResponse: String,
    latencyMs: Long = 0L,
    resultMapper: (JsonObject, String?) -> T,
  ): ApiCallResult<T> {
    return try {
      val sanitized = sanitizeFirmwareJson(rawResponse)
      val root = compactJson.parseToJsonElement(sanitized).jsonObject
      val idStr = root["id"]?.toPrimitiveString()
      val src = root["src"]?.toPrimitiveString()

      val errorElement = root["error"]
      if (errorElement != null && errorElement !is JsonNull) {
        val errObj = errorElement.jsonObject
        val code = errObj["code"]?.toIntOrNull() ?: -32603
        val message = errObj["message"]?.toPrimitiveString() ?: "Unknown error"
        val data = errObj["data"]?.toPrimitiveString()
        return ApiCallResult.RpcFailure(
          id = idStr,
          src = src,
          error = RpcError(code = code, message = message, data = data),
          rawResponse = rawResponse,
          latencyMs = latencyMs,
        )
      }

      val resultObj = root["result"]?.jsonObject
        ?: return ApiCallResult.TransportFailure("Відсутнє поле 'result' у відповіді пристрою: $rawResponse")

      val parsedValue = resultMapper(resultObj, src)
      ApiCallResult.Success(
        value = parsedValue,
        src = src,
        rawResponse = rawResponse,
        latencyMs = latencyMs,
      )
    } catch (e: Exception) {
      ApiCallResult.TransportFailure(
        reason = "Помилка розбору JSON: ${e.message}",
        exception = e,
      )
    }
  }

  fun parseDeviceInfo(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<DeviceInfoResult> =
    parseResponse(rawResponse, latencyMs) { res, src ->
      DeviceInfoResult(
        device = res["device"]?.toPrimitiveString() ?: (src?.substringBefore("-") ?: "Unknown"),
        ver = res["ver"]?.toIntOrNull(),
        bleMac = res["ble_mac"]?.toPrimitiveString(),
        wifiMac = res["wifi_mac"]?.toPrimitiveString(),
        wifiName = res["wifi_name"]?.toPrimitiveString(),
        ip = res["ip"]?.toPrimitiveString(),
        src = src,
      )
    }

  fun parseWifiStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<WifiStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      WifiStatusResult(
        id = res["id"]?.toIntOrNull(),
        wifiMac = res["wifi_mac"]?.toPrimitiveString(),
        ssid = res["ssid"]?.toPrimitiveString(),
        rssi = res["rssi"]?.toIntOrNull(),
        staIp = res["sta_ip"]?.toPrimitiveString(),
        staGate = res["sta_gate"]?.toPrimitiveString(),
        staMask = res["sta_mask"]?.toPrimitiveString(),
        staDns = res["sta_dns"]?.toPrimitiveString(),
      )
    }

  fun parseBleStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<BleStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      BleStatusResult(
        id = res["id"]?.toIntOrNull(),
        state = res["state"]?.toPrimitiveString() ?: "unknown",
        bleMac = res["ble_mac"]?.toPrimitiveString(),
      )
    }

  fun parseBatteryStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<BatteryStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      val socPrim = res["soc"]
      BatteryStatusResult(
        id = res["id"]?.toIntOrNull(),
        soc = socPrim?.toDoubleOrNull(),
        socRaw = socPrim?.toPrimitiveString(),
        chargFlag = res["charg_flag"]?.toBooleanOrNull(),
        dischrgFlag = res["dischrg_flag"]?.toBooleanOrNull(),
        batTemp = res["bat_temp"]?.toDoubleOrNull(),
        batCapacity = res["bat_capacity"]?.toDoubleOrNull(),
        ratedCapacity = res["rated_capacity"]?.toDoubleOrNull(),
      )
    }

  fun parsePvStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<PvStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      val channels = mutableListOf<PvChannelStatus>()

      // Check for 4-channel MPPT properties (pv1_power..pv4_power as in PDF example p.9)
      for (ch in 1..4) {
        val power = res["pv${ch}_power"]?.toDoubleOrNull()
        val voltage = res["pv${ch}_voltage"]?.toDoubleOrNull()
        val current = res["pv${ch}_current"]?.toDoubleOrNull()
        val state = res["pv${ch}_state"]?.toIntOrNull() ?: res["PV${ch}_state"]?.toIntOrNull()
        if (power != null || voltage != null || current != null || state != null) {
          channels.add(
            PvChannelStatus(
              channelIndex = ch,
              powerWatts = power ?: 0.0,
              voltageVolts = voltage ?: 0.0,
              currentAmps = current ?: 0.0,
              state = state ?: 0,
            )
          )
        }
      }

      // Check for single-channel properties (pv_power, pv_voltage, pv_current, PV_state as in PDF table p.8-9)
      val singlePower = res["pv_power"]?.toDoubleOrNull()
      val singleVoltage = res["pv_voltage"]?.toDoubleOrNull()
      val singleCurrent = res["pv_current"]?.toDoubleOrNull()
      val singleState = res["PV_state"]?.toIntOrNull() ?: res["pv_state"]?.toIntOrNull()
      if (channels.isEmpty() && (singlePower != null || singleVoltage != null || singleCurrent != null || singleState != null)) {
        channels.add(
          PvChannelStatus(
            channelIndex = 0,
            powerWatts = singlePower ?: 0.0,
            voltageVolts = singleVoltage ?: 0.0,
            currentAmps = singleCurrent ?: 0.0,
            state = singleState ?: 0,
          )
        )
      }

      PvStatusResult(
        id = res["id"]?.toIntOrNull(),
        channels = channels,
      )
    }

  fun parseEsStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<EsStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      EsStatusResult(
        id = res["id"]?.toIntOrNull(),
        batSoc = res["bat_soc"]?.toDoubleOrNull(),
        batCap = res["bat_cap"]?.toDoubleOrNull(),
        pvPower = res["pv_power"]?.toDoubleOrNull(),
        ongridPower = res["ongrid_power"]?.toDoubleOrNull(),
        offgridPower = res["offgrid_power"]?.toDoubleOrNull(),
        batPower = res["bat_power"]?.toDoubleOrNull(),
        totalPvEnergyRaw = res["total_pv_energy"]?.toDoubleOrNull(),
        totalGridOutputEnergyWh = res["total_grid_output_energy"]?.toDoubleOrNull(),
        totalGridInputEnergyWh = res["total_grid_input_energy"]?.toDoubleOrNull(),
        totalLoadEnergyWh = res["total_load_energy"]?.toDoubleOrNull(),
      )
    }

  fun parseEsMode(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<EsModeResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      EsModeResult(
        id = res["id"]?.toIntOrNull(),
        mode = res["mode"]?.toPrimitiveString(),
        ongridPower = res["ongrid_power"]?.toDoubleOrNull(),
        offgridPower = res["offgrid_power"]?.toDoubleOrNull(),
        batSoc = res["bat_soc"]?.toDoubleOrNull(),
        ctState = res["ct_state"]?.toIntOrNull(),
        aPower = res["a_power"]?.toDoubleOrNull(),
        bPower = res["b_power"]?.toDoubleOrNull(),
        cPower = res["c_power"]?.toDoubleOrNull(),
        totalPower = res["total_power"]?.toDoubleOrNull(),
        inputEnergyRaw = res["input_energy"]?.toDoubleOrNull(),
        outputEnergyRaw = res["output_energy"]?.toDoubleOrNull(),
      )
    }

  fun parseEmStatus(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<EmStatusResult> =
    parseResponse(rawResponse, latencyMs) { res, _ ->
      EmStatusResult(
        id = res["id"]?.toIntOrNull(),
        ctState = res["ct_state"]?.toIntOrNull(),
        aPower = res["a_power"]?.toDoubleOrNull(),
        bPower = res["b_power"]?.toDoubleOrNull(),
        cPower = res["c_power"]?.toDoubleOrNull(),
        totalPower = res["total_power"]?.toDoubleOrNull(),
        inputEnergyRaw = res["input_energy"]?.toDoubleOrNull(),
        outputEnergyRaw = res["output_energy"]?.toDoubleOrNull(),
      )
    }

  fun parseSetOperationResult(rawResponse: String, latencyMs: Long = 0L): ApiCallResult<SetOperationResult> =
    parseResponse(rawResponse, latencyMs) { res, src ->
      SetOperationResult(
        id = res["id"]?.toIntOrNull(),
        setResult = res["set_result"]?.toBooleanOrNull() ?: false,
        src = src,
      )
    }

  // ============================================================================
  // JsonElement Extension Helpers
  // ============================================================================

  private fun JsonElement.toPrimitiveString(): String? {
    if (this is JsonNull) return null
    val prim = this as? JsonPrimitive ?: return this.toString()
    return prim.content
  }

  private fun JsonElement.toIntOrNull(): Int? {
    if (this is JsonNull) return null
    val prim = this as? JsonPrimitive ?: return null
    return prim.intOrNull ?: prim.doubleOrNull?.toInt() ?: prim.content.toIntOrNull()
  }

  private fun JsonElement.toDoubleOrNull(): Double? {
    if (this is JsonNull) return null
    val prim = this as? JsonPrimitive ?: return null
    return prim.doubleOrNull ?: prim.content.toDoubleOrNull()
  }

  private fun JsonElement.toBooleanOrNull(): Boolean? {
    if (this is JsonNull) return null
    val prim = this as? JsonPrimitive ?: return null
    prim.booleanOrNull?.let { return it }
    return when (prim.content.trim().lowercase()) {
      "true", "ture", "1", "yes" -> true
      "false", "fai", "fail", "0", "no" -> false
      else -> null
    }
  }
}
