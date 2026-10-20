package org.marsopenclient.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.marsopenclient.api.DeviceModelProfile
import org.marsopenclient.api.LanDiagnosticsInfo
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint
import org.marsopenclient.api.PacketLogEntry

data class PersistedAppConfig(
  val savedDevices: List<MarstekEndpoint> = emptyList(),
  val selectedDeviceId: String? = null,
  val demoModeEnabled: Boolean = false,
  val defaultUdpPort: Int = MarstekApiConstants.DEFAULT_UDP_PORT,
)

interface LocalAppStorage {
  fun loadConfig(): PersistedAppConfig
  fun saveConfig(config: PersistedAppConfig)
  fun loadLogs(): List<PacketLogEntry>
  fun saveLogs(logs: List<PacketLogEntry>)
}

class InMemoryAppStorage : LocalAppStorage {
  private var config = PersistedAppConfig(demoModeEnabled = true)
  private var logs = emptyList<PacketLogEntry>()

  override fun loadConfig(): PersistedAppConfig = config
  override fun saveConfig(config: PersistedAppConfig) {
    this.config = config
  }

  override fun loadLogs(): List<PacketLogEntry> = logs
  override fun saveLogs(logs: List<PacketLogEntry>) {
    this.logs = logs
  }
}

class AndroidFileAppStorage(
  context: Context,
) : LocalAppStorage {

  private val appContext = context.applicationContext
  private val configFile = File(appContext.filesDir, "marsopenclient_config_v2.json")
  private val logsFile = File(appContext.filesDir, "marsopenclient_packet_logs_v2.json")

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
  }

  override fun loadConfig(): PersistedAppConfig {
    if (!configFile.exists()) {
      return PersistedAppConfig()
    }
    return try {
      val text = configFile.readText(Charsets.UTF_8)
      val root = json.parseToJsonElement(text).jsonObject
      val selectedId = root["selectedDeviceId"]?.asStringOrNull()
      val demoEnabled = root["demoModeEnabled"]?.asBooleanOrNull() ?: false
      val defaultPort = root["defaultUdpPort"]?.asIntOrNull() ?: MarstekApiConstants.DEFAULT_UDP_PORT
      val devicesArr = root["savedDevices"]?.jsonArray ?: JsonArray(emptyList())
      val devices = devicesArr.mapNotNull { elem ->
        val obj = elem.jsonObject
        val ip = obj["ip"]?.asStringOrNull() ?: return@mapNotNull null
        val port = obj["port"]?.asIntOrNull() ?: MarstekApiConstants.DEFAULT_UDP_PORT
        val id = obj["id"]?.asStringOrNull() ?: "$ip:$port"
        val model = obj["deviceModel"]?.asStringOrNull() ?: "VenusD"
        MarstekEndpoint(
          id = id,
          name = obj["name"]?.asStringOrNull() ?: "$model ($ip)",
          ip = ip,
          port = port,
          deviceModel = model,
          firmwareVersion = obj["firmwareVersion"]?.asIntOrNull(),
          bleMac = obj["bleMac"]?.asStringOrNull(),
          wifiMac = obj["wifiMac"]?.asStringOrNull(),
          wifiName = obj["wifiName"]?.asStringOrNull(),
          isSimulated = false,
          simulatedProfile = DeviceModelProfile.inferFromDeviceName(model),
          lastSeenMillis = obj["lastSeenMillis"]?.asLongOrNull(),
          lastKnownSoc = obj["lastKnownSoc"]?.asIntOrNull(),
          lastKnownMode = obj["lastKnownMode"]?.asStringOrNull(),
        )
      }
      PersistedAppConfig(
        savedDevices = devices,
        selectedDeviceId = selectedId,
        demoModeEnabled = demoEnabled,
        defaultUdpPort = defaultPort,
      )
    } catch (_: Exception) {
      PersistedAppConfig()
    }
  }

  override fun saveConfig(config: PersistedAppConfig) {
    try {
      val root = buildJsonObject {
        if (config.selectedDeviceId != null) {
          put("selectedDeviceId", config.selectedDeviceId)
        }
        put("demoModeEnabled", config.demoModeEnabled)
        put("defaultUdpPort", config.defaultUdpPort)
        put(
          "savedDevices",
          buildJsonArray {
            config.savedDevices.filter { !it.isSimulated }.forEach { ep ->
              add(
                buildJsonObject {
                  put("id", ep.id)
                  put("name", ep.name)
                  put("ip", ep.ip)
                  put("port", ep.port)
                  put("deviceModel", ep.deviceModel)
                  if (ep.firmwareVersion != null) put("firmwareVersion", ep.firmwareVersion)
                  if (ep.bleMac != null) put("bleMac", ep.bleMac)
                  if (ep.wifiMac != null) put("wifiMac", ep.wifiMac)
                  if (ep.wifiName != null) put("wifiName", ep.wifiName)
                  if (ep.lastSeenMillis != null) put("lastSeenMillis", ep.lastSeenMillis)
                  if (ep.lastKnownSoc != null) put("lastKnownSoc", ep.lastKnownSoc)
                  if (ep.lastKnownMode != null) put("lastKnownMode", ep.lastKnownMode)
                }
              )
            }
          },
        )
      }
      configFile.writeText(json.encodeToString(JsonObject.serializer(), root), Charsets.UTF_8)
    } catch (_: Exception) {
      // Ignore disk errors
    }
  }

  override fun loadLogs(): List<PacketLogEntry> {
    if (!logsFile.exists()) return emptyList()
    return try {
      val text = logsFile.readText(Charsets.UTF_8)
      val arr = json.parseToJsonElement(text).jsonArray
      arr.mapNotNull { elem ->
        val obj = elem.jsonObject
        val id = obj["id"]?.asLongOrNull() ?: return@mapNotNull null
        val ts = obj["timestampMillis"]?.asLongOrNull() ?: 0L
        val dirName = obj["direction"]?.asStringOrNull() ?: "SENT"
        val dir = try {
          PacketLogEntry.Direction.valueOf(dirName)
        } catch (_: Exception) {
          PacketLogEntry.Direction.SENT
        }
        PacketLogEntry(
          id = id,
          timestampMillis = ts,
          direction = dir,
          endpointAddress = obj["endpointAddress"]?.asStringOrNull() ?: "",
          method = obj["method"]?.asStringOrNull() ?: "",
          payload = obj["payload"]?.asStringOrNull() ?: "",
          latencyMs = obj["latencyMs"]?.asLongOrNull(),
          isError = obj["isError"]?.asBooleanOrNull() ?: false,
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  override fun saveLogs(logs: List<PacketLogEntry>) {
    try {
      val arr = buildJsonArray {
        logs.take(250).forEach { entry ->
          add(
            buildJsonObject {
              put("id", entry.id)
              put("timestampMillis", entry.timestampMillis)
              put("direction", entry.direction.name)
              put("endpointAddress", entry.endpointAddress)
              put("method", entry.method)
              put("payload", entry.payload)
              if (entry.latencyMs != null) put("latencyMs", entry.latencyMs)
              put("isError", entry.isError)
            }
          )
        }
      }
      logsFile.writeText(json.encodeToString(JsonArray.serializer(), arr), Charsets.UTF_8)
    } catch (_: Exception) {
      // Ignore disk errors
    }
  }

  private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.content
  }

  private fun kotlinx.serialization.json.JsonElement.asIntOrNull(): Int? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.intOrNull
  }

  private fun kotlinx.serialization.json.JsonElement.asLongOrNull(): Long? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.longOrNull
  }

  private fun kotlinx.serialization.json.JsonElement.asBooleanOrNull(): Boolean? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.booleanOrNull
  }
}

object LogReportFormatter {
  fun buildDiagnosticReport(
    lanInfo: LanDiagnosticsInfo,
    selectedEndpoint: MarstekEndpoint?,
    savedDevices: List<MarstekEndpoint>,
    logs: List<PacketLogEntry>,
  ): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return buildString {
      appendLine("====================================================")
      appendLine("MarsOpenClient — Діагностичний журнал (Marstek API v3.1)")
      appendLine("Час формування: ${dateFormat.format(Date())}")
      appendLine("====================================================")
      appendLine()
      appendLine("--- 1. Діагностика локальної мережі смартфона ---")
      appendLine("Локальний IPv4 (Wi-Fi): ${lanInfo.localIpv4 ?: "не виявлено"}")
      appendLine("Мережевий інтерфейс: ${lanInfo.networkInterfaceName ?: "невідомий"}")
      appendLine("Підмережа /24: ${lanInfo.subnetPrefix?.let { "$it.0/24" } ?: "—"}")
      appendLine("Адреси Broadcast: ${lanInfo.broadcastAddresses.joinToString(", ")}")
      appendLine("Wi-Fi транспорт активний: ${lanInfo.isWifiTransportActive}")
      appendLine("VPN активний: ${lanInfo.isVpnActive}")
      appendLine()
      appendLine("--- 2. Активна станція ---")
      if (selectedEndpoint == null) {
        appendLine("Не обрано жодної станції")
      } else {
        appendLine("Назва: ${selectedEndpoint.name}")
        appendLine("Адреса: ${selectedEndpoint.ip}:${selectedEndpoint.port}")
        appendLine("Модель: ${selectedEndpoint.deviceModel} (FW: ${selectedEndpoint.firmwareVersion ?: "?"})")
        appendLine("BLE MAC: ${selectedEndpoint.bleMac ?: "—"} | Wi-Fi MAC: ${selectedEndpoint.wifiMac ?: "—"}")
        appendLine("Режим симуляції: ${selectedEndpoint.isSimulated}")
      }
      appendLine()
      appendLine("--- 3. Збережені станції (${savedDevices.size}) ---")
      savedDevices.forEachIndexed { idx, ep ->
        val seen = ep.lastSeenMillis?.let { dateFormat.format(Date(it)) } ?: "ніколи"
        appendLine("${idx + 1}. ${ep.name} [${ep.ip}:${ep.port}] Модель=${ep.deviceModel}, SOC=${ep.lastKnownSoc ?: "?"}%, Востаннє на зв'язку=$seen")
      }
      appendLine()
      appendLine("--- 4. Журнал UDP пакетів (${logs.size} записів, від найновіших) ---")
      logs.forEach { entry ->
        val ts = dateFormat.format(Date(entry.timestampMillis))
        val lat = entry.latencyMs?.let { " (${it} ms)" } ?: ""
        appendLine("[$ts] [${entry.direction.name}]$lat ${entry.method} @ ${entry.endpointAddress}")
        appendLine("  ${entry.payload}")
      }
    }
  }
}
