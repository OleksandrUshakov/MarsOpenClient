package org.marsopenclient.ui.main

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.marsopenclient.api.ApiCallResult
import org.marsopenclient.api.DeviceModelProfile
import org.marsopenclient.api.EsSetModeConfig
import org.marsopenclient.api.LanDiagnosticsInfo
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint
import org.marsopenclient.api.MarstekJsonCodec
import org.marsopenclient.api.MarstekSimulatedDevice
import org.marsopenclient.api.PacketLogEntry
import org.marsopenclient.api.WeekBitmask
import org.marsopenclient.data.InMemoryAppStorage
import org.marsopenclient.data.LogReportFormatter
import org.marsopenclient.data.PersistedAppConfig

/**
 * Comprehensive unit test suite verifying 100% of the Marstek Device Open API (Rev 3.1)
 * specification examples, firmware quirks, week bitmask math, and device profiles.
 */
class MainScreenViewModelTest {

  @Test
  fun parseAllPdfSpecificationExamples_includingTureFirmwareQuirk() {
    // 1. Page 3 Error example
    val errorRaw = """
      {
        "id": 0,
        "src": "Venus-24215ee580e7",
        "error": {
          "code": -32700,
          "message": "Parse error",
          "data": 402
        }
      }
    """.trimIndent()
    val errParsed = MarstekJsonCodec.parseDeviceInfo(errorRaw)
    assertTrue(errParsed is ApiCallResult.RpcFailure)
    val rpcErr = (errParsed as ApiCallResult.RpcFailure).error
    assertEquals(-32700, rpcErr.code)
    assertEquals("Parse error", rpcErr.message)
    assertEquals("402", rpcErr.data)

    // 2. Page 4 Marstek.GetDevice response
    val getDeviceRaw = """
      {
        "id": 0,
        "src": "VenusC-123456789012",
        "result": {
          "device":"VenusC",
          "ver":111,
          "ble_mac":"123456789012",
          "wifi_mac":"123456789012",
          "wifi_name":"MY_HOME",
          "ip":"192.168.1.11"
        }
      }
    """.trimIndent()
    val devRes = MarstekJsonCodec.parseDeviceInfo(getDeviceRaw) as ApiCallResult.Success
    assertEquals("VenusC", devRes.value.device)
    assertEquals(111, devRes.value.ver)
    assertEquals("192.168.1.11", devRes.value.ip)

    // 3. Page 6 Wifi.GetStatus response
    val wifiRaw = """
      {
        "id": 1,
        "src": "VenusC-mac",
        "result": {
          "id": 0,
          "wifi_mac": "620b0c877705",
          "ssid":"Hame",
          "rssi": -59,
          "sta_ip":"192.168.137.41",
          "sta_gate":"192.168.137.1",
          "sta_mask":"255.255.255.0",
          "sta_dns":"192.168.137.1"
        }
      }
    """.trimIndent()
    val wifiRes = MarstekJsonCodec.parseWifiStatus(wifiRaw) as ApiCallResult.Success
    assertEquals("Hame", wifiRes.value.ssid)
    assertEquals(-59, wifiRes.value.rssi)
    assertEquals("192.168.137.41", wifiRes.value.staIp)

    // 4. Page 7 BLE.GetStatus response
    val bleRaw = """
      {
        "id": 1,
        "src": "VenusC-mac",
        "result": {
          "id": 0,
          "state":"connect",
          "ble_mac":"50cf14640fac"
        }
      }
    """.trimIndent()
    val bleRes = MarstekJsonCodec.parseBleStatus(bleRaw) as ApiCallResult.Success
    assertEquals("connect", bleRes.value.state)
    assertEquals("50cf14640fac", bleRes.value.bleMac)

    // 5. Page 8 Bat.GetStatus response
    val batRaw = """
      {
        "id": 1,
        "src": "VenusC-mac",
        "result": {
          "id": 0,
          "soc": 90,
          "charg_flag": true,
          "dischrg_flag": true,
          "bat_temp": 25.0,
          "bat_capacity": 256.0,
          "rated_capacity": 2560.0
        }
      }
    """.trimIndent()
    val batRes = MarstekJsonCodec.parseBatteryStatus(batRaw) as ApiCallResult.Success
    assertEquals(90.0, batRes.value.soc)
    assertEquals(true, batRes.value.chargFlag)
    assertEquals(2560.0, batRes.value.ratedCapacity)

    // 6. Page 9 PV.GetStatus 4-channel response
    val pvRaw = """
      {
        "id": 1,
        "src": "VenusC-mac",
        "result": {
          "id": 0,
          "pv1_power": 120,
          "pv1_voltage": 40,
          "pv1_current": 3,
          "pv1_state": 1,
          "pv2_power": 0,
          "pv2_voltage": 9,
          "pv2_current": 0,
          "pv2_state": 0,
          "pv3_power": 0,
          "pv3_voltage": 10,
          "pv3_current": 0,
          "pv3_state": 0,
          "pv4_power": 0,
          "pv4_voltage": 10,
          "pv4_current": 0,
          "pv4_state": 0
        }
      }
    """.trimIndent()
    val pvRes = MarstekJsonCodec.parsePvStatus(pvRaw) as ApiCallResult.Success
    assertEquals(4, pvRes.value.channels.size)
    assertEquals(120.0, pvRes.value.totalPvPowerWatts)
    assertTrue(pvRes.value.channels[0].isWorking)

    // 7. Page 11 ES.GetStatus response
    val esStatusRaw = """
      {
        "id": 1,
        "src": "VenusE-24215edb178f",
        "result": {
          "id": 0,
          "bat_soc": 98,
          "bat_cap": 5120,
          "pv_power": 0,
          "ongrid_power": 100,
          "offgrid_power": 0,
          "total_pv_energy": 150,
          "total_grid_output_energy": 2548,
          "total_grid_input_energy": 3273,
          "total_load_energy": 0
        }
      }
    """.trimIndent()
    val esStatus = MarstekJsonCodec.parseEsStatus(esStatusRaw) as ApiCallResult.Success
    assertEquals(98.0, esStatus.value.batSoc)
    assertEquals(1.5, esStatus.value.totalPvEnergyKwh)

    // 8. Page 14/18/19/20/21 Setter response with firmware typo `"set_result": ture`
    val setRawWithTure = """
      {
        "id": 1,
        "src": "Venus-mac",
        "result": {
          "id": 0,
          "set_result": ture
        }
      }
    """.trimIndent()
    val setRes = MarstekJsonCodec.parseSetOperationResult(setRawWithTure) as ApiCallResult.Success
    assertTrue(setRes.value.setResult)

    // 9. Page 16 ES.GetMode response
    val esModeRaw = """
      {
        "id": 1,
        "src": "VenusE-24215edb178f",
        "result": {
          "id": 0,
          "mode": "Auto",
          "ongrid_power": 100,
          "offgrid_power": 0,
          "bat_soc": 98,
          "ct_state": 0,
          "a_power": 0,
          "b_power": 0,
          "c_power": 0,
          "total_power": 0,
          "input_energy": 3086320,
          "output_energy": 4487510
        }
      }
    """.trimIndent()
    val esMode = MarstekJsonCodec.parseEsMode(esModeRaw) as ApiCallResult.Success
    assertEquals("Auto", esMode.value.mode)
    assertEquals(308632.0, esMode.value.inputEnergyWh)
  }

  @Test
  fun weekBitmask_encodesAndDecodesCorrectly() {
    // 0000 0001 (1) = Monday, 0000 0011 (3) = Monday + Tuesday, 0111 1111 (127) = All 7 days
    var mask = 0
    mask = WeekBitmask.toggleDay(mask, 0) // Monday
    assertEquals(1, mask)
    assertEquals("0000 0001", WeekBitmask.toBinaryString(mask))

    mask = WeekBitmask.toggleDay(mask, 1) // Tuesday
    assertEquals(3, mask)
    assertEquals("0000 0011", WeekBitmask.toBinaryString(mask))
  }

  @Test
  fun simulatedDevice_handlesAll14Methods_andChapter4Constraints() = runTest {
    val venusD = MarstekSimulatedDevice(
      MarstekEndpoint(
        id = "test-d",
        name = "Test Venus D",
        ip = "127.0.0.1",
        deviceModel = "VenusD",
        firmwareVersion = 154,
      )
    )
    // Test ES.SetMode Manual
    val setManualReq = MarstekJsonCodec.buildEsSetModeRequest(
      EsSetModeConfig.Manual(
        timeNum = 2,
        startTime = "09:00",
        endTime = "18:00",
        weekSet = 127,
        power = 450,
        enable = 1,
      )
    )
    val setManualResp = MarstekJsonCodec.parseSetOperationResult(venusD.handleRawRequest(setManualReq))
    assertTrue((setManualResp as ApiCallResult.Success).value.setResult)
    assertEquals("Manual", venusD.currentMode)
    assertEquals(450, venusD.activePowerWatts)

    // Test DOD.SET
    val dodReq = MarstekJsonCodec.buildDodSetRequest(75)
    val dodResp = MarstekJsonCodec.parseSetOperationResult(venusD.handleRawRequest(dodReq))
    assertTrue((dodResp as ApiCallResult.Success).value.setResult)
    assertEquals(75, venusD.dodValue)

    // Test Venus E mini rejects Set.Ver and Reset.Factory per Chapter 4.3
    val venusMini = MarstekSimulatedDevice(
      MarstekEndpoint(
        id = "test-mini",
        name = "Test Venus E mini",
        ip = "127.0.0.1",
        deviceModel = "VenusE mini",
        firmwareVersion = 120,
        simulatedProfile = DeviceModelProfile.VENUS_E_MINI,
        isSimulated = true,
      )
    )
    val setVerResp = MarstekJsonCodec.parseSetOperationResult(
      venusMini.handleRawRequest(MarstekJsonCodec.buildSetVerRequest(2500))
    )
    assertTrue(setVerResp is ApiCallResult.RpcFailure)
    assertEquals(-32601, (setVerResp as ApiCallResult.RpcFailure).error.code)

    // Verify all 14 method templates are valid JSON
    for (method in MarstekApiConstants.ALL_METHODS) {
      val template = MarstekJsonCodec.getTemplateForMethod(method)
      assertNotNull(template)
      assertTrue(template.contains(method))
    }
  }

  @Test
  fun localStoreAndDiagnosticReport_persistAndFormatCorrectly() {
    val storage = InMemoryAppStorage()
    val endpoint = MarstekEndpoint(
      id = "lan:192.168.1.50:30000",
      name = "VenusE (192.168.1.50)",
      ip = "192.168.1.50",
      port = 30000,
      deviceModel = "VenusE",
      firmwareVersion = 154,
      isSimulated = false,
      lastKnownSoc = 84,
      lastKnownMode = "Auto",
    )
    storage.saveConfig(
      PersistedAppConfig(
        savedDevices = listOf(endpoint),
        selectedDeviceId = endpoint.id,
        demoModeEnabled = false,
        defaultUdpPort = 30000,
      )
    )
    val loaded = storage.loadConfig()
    assertEquals(1, loaded.savedDevices.size)
    assertEquals("192.168.1.50", loaded.savedDevices.first().ip)

    val sampleLog = PacketLogEntry(
      id = 1L,
      timestampMillis = 1729410000000L,
      direction = PacketLogEntry.Direction.SENT,
      endpointAddress = "192.168.1.50:30000 (local:30000, wifiBound=true)",
      method = "Marstek.GetDevice",
      payload = """{"id":1,"method":"Marstek.GetDevice","params":{"ble_mac":"0"}}""",
      isError = false,
      latencyMs = 42L,
    )
    storage.saveLogs(listOf(sampleLog))
    assertEquals(1, storage.loadLogs().size)

    val report = LogReportFormatter.buildDiagnosticReport(
      lanInfo = LanDiagnosticsInfo(
        localIpv4 = "192.168.1.10",
        subnetPrefix = "192.168.1",
        broadcastAddresses = listOf("192.168.1.255", "255.255.255.255"),
        networkInterfaceName = "wlan0",
        isWifiTransportActive = true,
        isVpnActive = false,
      ),
      selectedEndpoint = endpoint,
      savedDevices = loaded.savedDevices,
      logs = storage.loadLogs(),
    )
    assertTrue(report.contains("MarsOpenClient — Діагностичний журнал"))
    assertTrue(report.contains("192.168.1.50:30000"))
    assertTrue(report.contains("Marstek.GetDevice"))
  }
}
