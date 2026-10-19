package org.marsopenclient.api

import kotlin.math.roundToInt

/**
 * Marstek Device Open API (Rev 3.1) Domain Models.
 * Covers Chapter II (Protocol & Error Codes), Chapter III (Components 3.1 - 3.8),
 * and Chapter IV (Device Profiles & Capabilities).
 */
object MarstekApiConstants {
  const val DEFAULT_UDP_PORT = 30000
  const val RECOMMENDED_PORT_MIN = 49152
  const val RECOMMENDED_PORT_MAX = 65535
  const val DEFAULT_DOD_VALUE = 88
  const val MIN_DOD_VALUE = 30
  const val MAX_DOD_VALUE = 88
  const val MIN_SYS_FIRMWARE_VERSION = 150

  // Method Names (Chapter 3)
  const val METHOD_MARSTEK_GET_DEVICE = "Marstek.GetDevice"
  const val METHOD_WIFI_GET_STATUS = "Wifi.GetStatus"
  const val METHOD_BLE_GET_STATUS = "BLE.GetStatus"
  const val METHOD_BAT_GET_STATUS = "Bat.GetStatus"
  const val METHOD_PV_GET_STATUS = "PV.GetStatus"
  const val METHOD_ES_GET_STATUS = "ES.GetStatus"
  const val METHOD_ES_SET_MODE = "ES.SetMode"
  const val METHOD_ES_GET_MODE = "ES.GetMode"
  const val METHOD_EM_GET_STATUS = "EM.GetStatus"
  const val METHOD_DOD_SET = "DOD.SET"
  const val METHOD_BLE_ADV = "Ble.Adv"
  const val METHOD_LED_CTRL = "Led.Ctrl"
  const val METHOD_SET_VER = "Set.Ver"
  const val METHOD_RESET_FACTORY = "Reset.Factory"

  val ALL_METHODS = listOf(
    METHOD_MARSTEK_GET_DEVICE,
    METHOD_WIFI_GET_STATUS,
    METHOD_BLE_GET_STATUS,
    METHOD_BAT_GET_STATUS,
    METHOD_PV_GET_STATUS,
    METHOD_ES_GET_STATUS,
    METHOD_ES_SET_MODE,
    METHOD_ES_GET_MODE,
    METHOD_EM_GET_STATUS,
    METHOD_DOD_SET,
    METHOD_BLE_ADV,
    METHOD_LED_CTRL,
    METHOD_SET_VER,
    METHOD_RESET_FACTORY,
  )

  val POWER_VERSIONS = listOf(
    PowerVersionOption(800, "800W (Universal / Balcony)"),
    PowerVersionOption(1200, "1200W (Venus A)"),
    PowerVersionOption(1500, "1500W (Venus A)"),
    PowerVersionOption(2200, "2200W (Venus D)"),
    PowerVersionOption(2500, "2500W (Venus E / C)"),
  )
}

data class PowerVersionOption(
  val watts: Int,
  val label: String,
)

/**
 * Chapter 4: Device model capability profiles.
 */
enum class DeviceModelProfile(
  val displayName: String,
  val supportsPv: Boolean,
  val supportsVersionSet: Boolean,
  val supportsFactoryReset: Boolean,
  val requiresFw150ForSys: Boolean,
  val maxManualTimeNum: Int,
  val supportsManualSetDirection: Boolean,
  val supportedComponentsSummary: String,
) {
  VENUS_C_E(
    displayName = "Venus C / Venus E",
    supportsPv = false,
    supportsVersionSet = true,
    supportsFactoryReset = true,
    requiresFw150ForSys = true,
    maxManualTimeNum = 9, // 0-9
    supportsManualSetDirection = false,
    supportedComponentsSummary = "Marstek, WiFi, Bluetooth, Battery, ES, EM, SYS (FW >= 150)",
  ),
  VENUS_D_A(
    displayName = "Venus D / Venus A",
    supportsPv = true,
    supportsVersionSet = true,
    supportsFactoryReset = true,
    requiresFw150ForSys = true,
    maxManualTimeNum = 9, // 0-9
    supportsManualSetDirection = false,
    supportedComponentsSummary = "Marstek, WiFi, Bluetooth, Battery, PV, ES, EM, SYS (FW >= 150)",
  ),
  VENUS_E_MINI(
    displayName = "Venus E mini",
    supportsPv = false,
    supportsVersionSet = false,
    supportsFactoryReset = false,
    requiresFw150ForSys = false,
    maxManualTimeNum = 5, // 0-5
    supportsManualSetDirection = true,
    supportedComponentsSummary = "Marstek, WiFi, Bluetooth, Battery, ES, EM, SYS (DOD, Ble_block, Led_Ctrl)",
  ),
  GENERIC(
    displayName = "Універсальний Marstek (Всі функції)",
    supportsPv = true,
    supportsVersionSet = true,
    supportsFactoryReset = true,
    requiresFw150ForSys = false,
    maxManualTimeNum = 9,
    supportsManualSetDirection = true,
    supportedComponentsSummary = "Повний доступ до всіх методів API Rev 3.1",
  );

  companion object {
    fun inferFromDeviceName(deviceName: String?): DeviceModelProfile {
      if (deviceName.isNullOrBlank()) return GENERIC
      val normalized = deviceName.trim().lowercase()
      return when {
        normalized.contains("mini") -> VENUS_E_MINI
        normalized.contains("venusd") || normalized.contains("venus d") ||
          normalized.contains("venusa") || normalized.contains("venus a") -> VENUS_D_A
        normalized.contains("venusc") || normalized.contains("venus c") ||
          normalized.contains("venuse") || normalized.contains("venus e") -> VENUS_C_E
        else -> GENERIC
      }
    }
  }
}

/**
 * Target Marstek device endpoint on the LAN (or local simulator).
 */
data class MarstekEndpoint(
  val id: String,
  val name: String,
  val ip: String,
  val port: Int = MarstekApiConstants.DEFAULT_UDP_PORT,
  val deviceModel: String = "VenusD",
  val firmwareVersion: Int? = null,
  val bleMac: String? = null,
  val wifiMac: String? = null,
  val wifiName: String? = null,
  val isSimulated: Boolean = false,
  val simulatedProfile: DeviceModelProfile = DeviceModelProfile.inferFromDeviceName(deviceModel),
) {
  val profile: DeviceModelProfile
    get() = if (isSimulated) simulatedProfile else DeviceModelProfile.inferFromDeviceName(deviceModel)

  val isSysFirmwareSupported: Boolean
    get() {
      val fw = firmwareVersion ?: return true
      return !profile.requiresFw150ForSys || fw >= MarstekApiConstants.MIN_SYS_FIRMWARE_VERSION
    }
}

/**
 * Section 2.1: JSON-RPC Error code definition and explanation.
 */
data class RpcError(
  val code: Int,
  val message: String,
  val data: String? = null,
) {
  val standardMeaning: String
    get() = when {
      code == -32700 -> "Parse error: Сервер отримав невалідний JSON під час парсингу тексту"
      code == -32600 -> "Invalid Request: Надісланий JSON не є коректним об'єктом запиту"
      code == -32601 -> "Method not found: Метод не існує або недоступний на цій моделі/прошивці"
      code == -32602 -> "Invalid params: Некоректні параметри методу"
      code == -32603 -> "Internal error: Внутрішня помилка JSON-RPC"
      code in -32099..-32000 -> "Server error: Зарезервована помилка сервера пристрою"
      else -> "Помилка прикладного рівня пристрою"
    }
}

sealed class ApiCallResult<out T> {
  data class Success<T>(
    val value: T,
    val src: String?,
    val rawResponse: String,
    val latencyMs: Long,
  ) : ApiCallResult<T>()

  data class RpcFailure(
    val id: String?,
    val src: String?,
    val error: RpcError,
    val rawResponse: String,
    val latencyMs: Long,
  ) : ApiCallResult<Nothing>()

  data class TransportFailure(
    val reason: String,
    val exception: Throwable? = null,
  ) : ApiCallResult<Nothing>()
}

/**
 * 3.1.1 Marstek.GetDevice Response
 */
data class DeviceInfoResult(
  val device: String,
  val ver: Int?,
  val bleMac: String?,
  val wifiMac: String?,
  val wifiName: String?,
  val ip: String?,
  val src: String? = null,
)

/**
 * 3.2.1 Wifi.GetStatus Response
 */
data class WifiStatusResult(
  val id: Int?,
  val wifiMac: String?,
  val ssid: String?,
  val rssi: Int?,
  val staIp: String?,
  val staGate: String?,
  val staMask: String?,
  val staDns: String?,
)

/**
 * 3.3.1 BLE.GetStatus Response
 */
data class BleStatusResult(
  val id: Int?,
  val state: String,
  val bleMac: String?,
)

/**
 * 3.4.1 Bat.GetStatus Response
 */
data class BatteryStatusResult(
  val id: Int?,
  val soc: Double?,
  val socRaw: String?,
  val chargFlag: Boolean?,
  val dischrgFlag: Boolean?,
  val batTemp: Double?,
  val batCapacity: Double?,
  val ratedCapacity: Double?,
)

/**
 * 3.5.1 PV.GetStatus Channel & Result (only for Venus A/D)
 * Supports both single-channel (pv_power, pv_voltage, pv_current, PV_state)
 * and 4-channel MPPT (pv1_.. to pv4_..) formats shown in the specification.
 */
data class PvChannelStatus(
  val channelIndex: Int, // 0 for single channel, 1..4 for pv1..pv4
  val powerWatts: Double,
  val voltageVolts: Double,
  val currentAmps: Double,
  val state: Int, // 1: Work, 0: Standby
) {
  val isWorking: Boolean get() = state == 1
  val channelLabel: String get() = if (channelIndex == 0) "PV" else "PV$channelIndex"
}

data class PvStatusResult(
  val id: Int?,
  val channels: List<PvChannelStatus>,
) {
  val totalPvPowerWatts: Double
    get() = channels.sumOf { it.powerWatts }
}

/**
 * 3.6.1 ES.GetStatus Response
 */
data class EsStatusResult(
  val id: Int?,
  val batSoc: Double?,
  val batCap: Double?,
  val pvPower: Double?,
  val ongridPower: Double?,
  val offgridPower: Double?,
  val batPower: Double?,
  val totalPvEnergyRaw: Double?, // [0.01 * KWh]
  val totalGridOutputEnergyWh: Double?, // [Wh]
  val totalGridInputEnergyWh: Double?, // [Wh]
  val totalLoadEnergyWh: Double?, // [Wh]
) {
  /** Total solar energy generated in kWh (raw is in units of 0.01 kWh) */
  val totalPvEnergyKwh: Double?
    get() = totalPvEnergyRaw?.let { (it * 0.01 * 100.0).roundToInt() / 100.0 }

  /** Total solar energy generated in Wh (0.01 kWh = 10 Wh) */
  val totalPvEnergyWh: Double?
    get() = totalPvEnergyRaw?.let { it * 10.0 }
}

/**
 * 3.6.2 ES.SetMode Configuration Models
 */
enum class EsOperatingMode(val wireName: String, val displayName: String) {
  AUTO("Auto", "Auto (Автоматичний)"),
  AI("AI", "AI (Штучний інтелект)"),
  MANUAL("Manual", "Manual (Ручний за розкладом)"),
  PASSIVE("Passive", "Passive (Пасивний з таймером)"),
  UPS("UPS", "UPS (Джерело безперебійного живлення)"),
}

/**
 * Manual mode direction (manual_set, specifically for Venus E mini, 0..3)
 */
enum class ManualSetDirection(val code: Int, val label: String) {
  DISABLE(0, "0: Вимкнено (Disable)"),
  CHARGE(1, "1: Заряджання (Enable Charge)"),
  DISCHARGE(2, "2: Розряджання (Enable Discharge)"),
  AUTO(3, "3: Авто (Auto)");

  companion object {
    fun fromCode(code: Int): ManualSetDirection = entries.firstOrNull { it.code == code } ?: CHARGE
  }
}

/**
 * Helper for `week_set` (a byte 8 bits, low 7 bits effective, highest bit invalid):
 * 0000 0001 (1) = Monday
 * 0000 0010 (2) = Tuesday
 * 0000 0100 (4) = Wednesday
 * 0000 1000 (8) = Thursday
 * 0001 0000 (16) = Friday
 * 0010 0000 (32) = Saturday
 * 0100 0000 (64) = Sunday
 * All 7 days = 127 (0111 1111)
 */
object WeekBitmask {
  val DAYS = listOf(
    DayBit(0, 1, "Пн", "Monday"),
    DayBit(1, 2, "Вт", "Tuesday"),
    DayBit(2, 4, "Ср", "Wednesday"),
    DayBit(3, 8, "Чт", "Thursday"),
    DayBit(4, 16, "Пт", "Friday"),
    DayBit(5, 32, "Сб", "Saturday"),
    DayBit(6, 64, "Нд", "Sunday"),
  )

  data class DayBit(
    val bitIndex: Int,
    val mask: Int,
    val shortNameUk: String,
    val nameEn: String,
  )

  fun isDaySelected(weekSet: Int, dayIndex: Int): Boolean {
    val mask = 1 shl dayIndex
    return (weekSet and mask) != 0
  }

  fun toggleDay(weekSet: Int, dayIndex: Int): Int {
    val mask = 1 shl dayIndex
    return (weekSet xor mask) and 0x7F
  }

  fun toBinaryString(weekSet: Int): String {
    val clamped = weekSet and 0x7F
    val bits = clamped.toString(2).padStart(8, '0')
    return "${bits.substring(0, 4)} ${bits.substring(4, 8)}"
  }

  fun toSelectedDaysSummary(weekSet: Int): String {
    val clamped = weekSet and 0x7F
    if (clamped == 127) return "Щодня (127)"
    if (clamped == 0) return "Жодного дня (0)"
    if (clamped == 31) return "Будні Пн-Пт (31)"
    if (clamped == 96) return "Вихідні Сб-Нд (96)"
    return DAYS.filter { (clamped and it.mask) != 0 }.joinToString(", ") { it.shortNameUk }
  }
}

sealed class EsSetModeConfig {
  data class Auto(val enable: Int = 1) : EsSetModeConfig()
  data class Ai(val enable: Int = 1) : EsSetModeConfig()
  data class Manual(
    val timeNum: Int = 1,
    val startTime: String = "08:30",
    val endTime: String = "20:30",
    val weekSet: Int = 127,
    val power: Int = 100,
    val enable: Int = 1,
    val manualSet: Int? = 1,
  ) : EsSetModeConfig()

  data class Passive(
    val power: Int = 100,
    val cdTime: Int = 300,
  ) : EsSetModeConfig()

  data class Ups(
    val enable: Int = 1,
    val useUppercaseWireMode: Boolean = true, // "UPS" (example p.14) vs "Ups" (table p.11)
  ) : EsSetModeConfig()
}

/**
 * 3.6.3 ES.GetMode Response
 */
data class EsModeResult(
  val id: Int?,
  val mode: String?,
  val ongridPower: Double?,
  val offgridPower: Double?,
  val batSoc: Double?,
  val ctState: Int?,
  val aPower: Double?,
  val bPower: Double?,
  val cPower: Double?,
  val totalPower: Double?,
  val inputEnergyRaw: Double?, // [Wh] (*0.1)
  val outputEnergyRaw: Double?, // [Wh] (*0.1)
) {
  val isCtConnected: Boolean get() = ctState == 1
  val inputEnergyWh: Double? get() = inputEnergyRaw?.let { it * 0.1 }
  val outputEnergyWh: Double? get() = outputEnergyRaw?.let { it * 0.1 }
}

/**
 * 3.7.1 EM.GetStatus Response
 */
data class EmStatusResult(
  val id: Int?,
  val ctState: Int?,
  val aPower: Double?,
  val bPower: Double?,
  val cPower: Double?,
  val totalPower: Double?,
  val inputEnergyRaw: Double?, // [0.1 Wh]
  val outputEnergyRaw: Double?, // [0.1 Wh]
) {
  val isCtConnected: Boolean get() = ctState == 1
  val inputEnergyWh: Double? get() = inputEnergyRaw?.let { it * 0.1 }
  val outputEnergyWh: Double? get() = outputEnergyRaw?.let { it * 0.1 }
}

/**
 * Generic setter response used by ES.SetMode, DOD.SET, Ble.Adv, Led.Ctrl, Set.Ver, Reset.Factory
 */
data class SetOperationResult(
  val id: Int?,
  val setResult: Boolean,
  val src: String? = null,
)

/**
 * 3.8.5 Factory Reset types
 */
enum class FactoryResetType(val typeCode: Int, val titleUk: String, val descriptionUk: String) {
  CLEAR_ACCUMULATED_DATA(
    typeCode = 1,
    titleUk = "Тип 1: Повне скидання (очистити накопичені дані)",
    descriptionUk = "Reset to factory settings, clear accumulated data",
  ),
  KEEP_ACCUMULATED_DATA(
    typeCode = 2,
    titleUk = "Тип 2: Скидання зі збереженням статистики",
    descriptionUk = "Reset to factory settings, keep accumulated data",
  ),
}

/**
 * Packet log entry for live UDP console inspection.
 */
data class PacketLogEntry(
  val id: Long,
  val timestampMillis: Long,
  val direction: Direction,
  val endpointAddress: String,
  val method: String,
  val payload: String,
  val latencyMs: Long? = null,
  val isError: Boolean = false,
) {
  enum class Direction {
    SENT,
    RECEIVED,
    ERROR,
  }
}
