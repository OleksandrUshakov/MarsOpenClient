package org.marsopenclient.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Interactive local simulator of Marstek Energy Storage devices (Rev 3.1).
 * Accurately simulates Venus D/A (with 4-channel PV), Venus C/E, and Venus E mini
 * so the application can be explored and verified without physical hardware on the LAN.
 */
class MarstekSimulatedDevice(
  val endpoint: MarstekEndpoint,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  var currentMode: String = "Auto"
    private set
  var socPercent: Int = 92
    private set
  var dodValue: Int = MarstekApiConstants.DEFAULT_DOD_VALUE
    private set
  var bleAdvDisabled: Boolean = false // 0: enable, 1: disable
    private set
  var ledOn: Boolean = true // 1: on, 0: off
    private set
  var powerVersionWatts: Int = when (endpoint.profile) {
    DeviceModelProfile.VENUS_D_A -> 2200
    DeviceModelProfile.VENUS_C_E -> 2500
    DeviceModelProfile.VENUS_E_MINI -> 800
    DeviceModelProfile.GENERIC -> 2500
  }
    private set
  var activePowerWatts: Int = 380
    private set
  var pvTotalPowerWatts: Int = if (endpoint.profile.supportsPv) 640 else 0
    private set
  var totalPvEnergyRaw001Kwh: Int = if (endpoint.profile.supportsPv) 1485 else 0 // 14.85 kWh
    private set
  var totalGridOutputWh: Int = 25480
    private set
  var totalGridInputWh: Int = 32730
    private set
  var totalLoadWh: Int = 18400
    private set

  fun handleRawRequest(rawRequest: String): String {
    val root = try {
      json.parseToJsonElement(rawRequest).jsonObject
    } catch (_: Exception) {
      return buildErrorJson(id = 0, code = -32700, message = "Parse error", data = 402)
    }

    val reqId = (root["id"] as? JsonPrimitive)?.intOrNull ?: 0
    val method = (root["method"] as? JsonPrimitive)?.content
      ?: return buildErrorJson(id = reqId, code = -32600, message = "Invalid Request")
    val params = root["params"]?.jsonObject ?: JsonObject(emptyMap())
    val instanceId = (params["id"] as? JsonPrimitive)?.intOrNull ?: 0
    val srcName = "${endpoint.deviceModel}-${endpoint.bleMac ?: "24215edb178f"}"

    return when (method) {
      MarstekApiConstants.METHOD_MARSTEK_GET_DEVICE -> {
        buildResultJson(reqId, srcName) {
          put("device", endpoint.deviceModel)
          put("ver", endpoint.firmwareVersion ?: 154)
          put("ble_mac", endpoint.bleMac ?: "24215edb178f")
          put("wifi_mac", endpoint.wifiMac ?: "620b0c877705")
          put("wifi_name", endpoint.wifiName ?: "MARSTEK_HOME_LAN")
          put("ip", endpoint.ip)
        }
      }

      MarstekApiConstants.METHOD_WIFI_GET_STATUS -> {
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("wifi_mac", endpoint.wifiMac ?: "620b0c877705")
          put("ssid", endpoint.wifiName ?: "MARSTEK_HOME_LAN")
          put("rssi", -54)
          put("sta_ip", endpoint.ip)
          put("sta_gate", "192.168.1.1")
          put("sta_mask", "255.255.255.0")
          put("sta_dns", "192.168.1.1")
        }
      }

      MarstekApiConstants.METHOD_BLE_GET_STATUS -> {
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("state", if (bleAdvDisabled) "disconnect" else "connect")
          put("ble_mac", endpoint.bleMac ?: "50cf14640fac")
        }
      }

      MarstekApiConstants.METHOD_BAT_GET_STATUS -> {
        val ratedWh = if (endpoint.profile == DeviceModelProfile.VENUS_E_MINI) 2560.0 else 5120.0
        val remainingWh = (ratedWh * socPercent / 100.0)
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("soc", socPercent)
          put("charg_flag", true)
          put("dischrg_flag", socPercent > (100 - dodValue))
          put("bat_temp", 26.5)
          put("bat_capacity", remainingWh)
          put("rated_capacity", ratedWh)
        }
      }

      MarstekApiConstants.METHOD_PV_GET_STATUS -> {
        if (!endpoint.profile.supportsPv) {
          return buildErrorJson(
            id = reqId,
            src = srcName,
            code = -32601,
            message = "Method not found (PV is only supported on Venus A/D)",
          )
        }
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("pv1_power", 210)
          put("pv1_voltage", 42)
          put("pv1_current", 5.0)
          put("pv1_state", 1)
          put("pv2_power", 195)
          put("pv2_voltage", 39)
          put("pv2_current", 5.0)
          put("pv2_state", 1)
          put("pv3_power", 235)
          put("pv3_voltage", 47)
          put("pv3_current", 5.0)
          put("pv3_state", 1)
          put("pv4_power", 0)
          put("pv4_voltage", 10)
          put("pv4_current", 0.0)
          put("pv4_state", 0)
        }
      }

      MarstekApiConstants.METHOD_ES_GET_STATUS -> {
        val ratedWh = if (endpoint.profile == DeviceModelProfile.VENUS_E_MINI) 2560 else 5120
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("bat_soc", socPercent)
          put("bat_cap", ratedWh)
          put("pv_power", pvTotalPowerWatts)
          put("ongrid_power", activePowerWatts)
          put("offgrid_power", if (currentMode.equals("UPS", ignoreCase = true)) 140 else 0)
          put("bat_power", pvTotalPowerWatts - activePowerWatts)
          put("total_pv_energy", totalPvEnergyRaw001Kwh)
          put("total_grid_output_energy", totalGridOutputWh)
          put("total_grid_input_energy", totalGridInputWh)
          put("total_load_energy", totalLoadWh)
        }
      }

      MarstekApiConstants.METHOD_ES_SET_MODE -> {
        val cfg = params["config"]?.jsonObject
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: missing config object")
        val mode = (cfg["mode"] as? JsonPrimitive)?.content
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: missing mode")
        currentMode = mode
        when (mode) {
          "Manual" -> {
            val manualCfg = cfg["manual_cfg"]?.jsonObject
            val pwr = (manualCfg?.get("power") as? JsonPrimitive)?.intOrNull
            if (pwr != null) activePowerWatts = pwr
          }
          "Passive" -> {
            val passiveCfg = cfg["passive_cfg"]?.jsonObject
            val pwr = (passiveCfg?.get("power") as? JsonPrimitive)?.intOrNull
            if (pwr != null) activePowerWatts = pwr
          }
        }
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      MarstekApiConstants.METHOD_ES_GET_MODE -> {
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("mode", currentMode)
          put("ongrid_power", activePowerWatts)
          put("offgrid_power", if (currentMode.equals("UPS", ignoreCase = true)) 140 else 0)
          put("bat_soc", socPercent)
          put("ct_state", 1)
          put("a_power", 145)
          put("b_power", 120)
          put("c_power", 115)
          put("total_power", 380)
          put("input_energy", totalGridInputWh * 10)
          put("output_energy", totalGridOutputWh * 10)
        }
      }

      MarstekApiConstants.METHOD_EM_GET_STATUS -> {
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("ct_state", 1)
          put("a_power", 145)
          put("b_power", 120)
          put("c_power", 115)
          put("total_power", 380)
          put("input_energy", totalGridInputWh * 10)
          put("output_energy", totalGridOutputWh * 10)
        }
      }

      MarstekApiConstants.METHOD_DOD_SET -> {
        val v = (params["value"] as? JsonPrimitive)?.intOrNull
          ?: (params["Value"] as? JsonPrimitive)?.intOrNull
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: value required (30-88)")
        if (v !in MarstekApiConstants.MIN_DOD_VALUE..MarstekApiConstants.MAX_DOD_VALUE) {
          return buildErrorJson(reqId, srcName, -32602, "Invalid params: DOD value must be in 30..88")
        }
        dodValue = v
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      MarstekApiConstants.METHOD_BLE_ADV -> {
        val enableCode = (params["enable"] as? JsonPrimitive)?.intOrNull
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: enable (0 or 1)")
        bleAdvDisabled = (enableCode == 1)
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      MarstekApiConstants.METHOD_LED_CTRL -> {
        val stateCode = (params["state"] as? JsonPrimitive)?.intOrNull
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: state (1 or 0)")
        ledOn = (stateCode == 1)
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      MarstekApiConstants.METHOD_SET_VER -> {
        if (!endpoint.profile.supportsVersionSet) {
          return buildErrorJson(reqId, srcName, -32601, "Method Set.Ver is not supported on Venus E mini")
        }
        val verWatts = (params["version"] as? JsonPrimitive)?.intOrNull
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: version required")
        powerVersionWatts = verWatts
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      MarstekApiConstants.METHOD_RESET_FACTORY -> {
        if (!endpoint.profile.supportsFactoryReset) {
          return buildErrorJson(reqId, srcName, -32601, "Method Reset.Factory is not supported on Venus E mini")
        }
        val resetType = (params["type"] as? JsonPrimitive)?.intOrNull
          ?: return buildErrorJson(reqId, srcName, -32602, "Invalid params: type (1 or 2)")
        dodValue = MarstekApiConstants.DEFAULT_DOD_VALUE
        currentMode = "Auto"
        bleAdvDisabled = false
        ledOn = true
        if (resetType == 1) {
          totalPvEnergyRaw001Kwh = 0
          totalGridOutputWh = 0
          totalGridInputWh = 0
          totalLoadWh = 0
        }
        buildResultJson(reqId, srcName) {
          put("id", instanceId)
          put("set_result", true)
        }
      }

      else -> buildErrorJson(reqId, srcName, -32601, "Method not found: $method")
    }
  }

  private fun buildResultJson(
    id: Int,
    src: String,
    resultBuilder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
  ): String {
    val obj = buildJsonObject {
      put("id", id)
      put("src", src)
      putJsonObject("result", resultBuilder)
    }
    return json.encodeToString(JsonObject.serializer(), obj)
  }

  private fun buildErrorJson(
    id: Int,
    src: String = "Venus-Simulator",
    code: Int,
    message: String,
    data: Int? = null,
  ): String {
    val obj = buildJsonObject {
      put("id", id)
      put("src", src)
      putJsonObject("error") {
        put("code", code)
        put("message", message)
        if (data != null) put("data", data)
      }
    }
    return json.encodeToString(JsonObject.serializer(), obj)
  }

  companion object {
    fun createDefaultSimulatedEndpoints(): List<MarstekEndpoint> = listOf(
      MarstekEndpoint(
        id = "sim-venusd",
        name = "Demo Venus D (PV + ES + EM + SYS)",
        ip = "192.168.1.101",
        port = MarstekApiConstants.DEFAULT_UDP_PORT,
        deviceModel = "VenusD",
        firmwareVersion = 154,
        bleMac = "009b08a5ac28",
        wifiMac = "620b0c877705",
        wifiName = "MY_HOME_SOLAR",
        isSimulated = true,
        simulatedProfile = DeviceModelProfile.VENUS_D_A,
      ),
      MarstekEndpoint(
        id = "sim-venuse",
        name = "Demo Venus E (2500W, FW 152)",
        ip = "192.168.1.102",
        port = MarstekApiConstants.DEFAULT_UDP_PORT,
        deviceModel = "VenusE",
        firmwareVersion = 152,
        bleMac = "24215edb178f",
        wifiMac = "012123456789",
        wifiName = "MY_HOME",
        isSimulated = true,
        simulatedProfile = DeviceModelProfile.VENUS_C_E,
      ),
      MarstekEndpoint(
        id = "sim-venuse-mini",
        name = "Demo Venus E mini (800W)",
        ip = "192.168.1.103",
        port = MarstekApiConstants.DEFAULT_UDP_PORT,
        deviceModel = "VenusE mini",
        firmwareVersion = 120,
        bleMac = "50cf14640fac",
        wifiMac = "50cf14640fad",
        wifiName = "MY_HOME",
        isSimulated = true,
        simulatedProfile = DeviceModelProfile.VENUS_E_MINI,
      ),
    )
  }
}
