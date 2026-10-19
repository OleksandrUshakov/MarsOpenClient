package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesNetworkSection(
  uiState: MarstekUiState,
  onSelectEndpoint: (MarstekEndpoint) -> Unit,
  onUpdateDiscoveryConfig: (port: String, bleMac: String) -> Unit,
  onRunDiscovery: () -> Unit,
  onAddManualDevice: (name: String, ip: String, port: Int, model: String, fw: Int?) -> Unit,
  onUpdateInstanceId: (Int) -> Unit,
  onQueryDeviceInfo: (String?) -> Unit,
  onQueryWifi: () -> Unit,
  onQueryBle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAddForm by rememberSaveable { mutableStateOf(false) }
  var manualName by rememberSaveable { mutableStateOf("") }
  var manualIp by rememberSaveable { mutableStateOf("192.168.1.11") }
  var manualPort by rememberSaveable { mutableStateOf(MarstekApiConstants.DEFAULT_UDP_PORT.toString()) }
  var manualModel by rememberSaveable { mutableStateOf("VenusD") }
  var manualFw by rememberSaveable { mutableStateOf("154") }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 2.2.2 LAN UDP Discovery Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "2.2.2 Пошук пристроїв у LAN (UDP Broadcast)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Відправляє широкомовний пакет Marstek.GetDevice у локальну мережу. Порт за замовчуванням: 30000 (рекомендований діапазон у додатку Marstek: 49152–65535).",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = uiState.discoveryPort,
            onValueChange = { onUpdateDiscoveryConfig(it, uiState.discoveryBleMac) },
            label = { Text("UDP Порт") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = uiState.discoveryBleMac,
            onValueChange = { onUpdateDiscoveryConfig(uiState.discoveryPort, it) },
            label = { Text("ble_mac (\"0\" = всі)") },
            singleLine = true,
            modifier = Modifier.weight(1.3f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onRunDiscovery,
            enabled = !uiState.isDiscovering,
            modifier = Modifier.weight(1f),
          ) {
            Text(if (uiState.isDiscovering) "Сканування LAN..." else "Сканувати LAN (Broadcast)")
          }
          OutlinedButton(
            onClick = { showAddForm = !showAddForm },
          ) {
            Text(if (showAddForm) "Сховати форму" else "+ Додати IP")
          }
        }

        if (showAddForm) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "Додати пристрій за статичною IP-адресою",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = manualIp,
              onValueChange = { manualIp = it },
              label = { Text("IP-адреса") },
              singleLine = true,
              modifier = Modifier.weight(1.4f),
            )
            OutlinedTextField(
              value = manualPort,
              onValueChange = { manualPort = it },
              label = { Text("UDP Порт") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = manualName,
              onValueChange = { manualName = it },
              label = { Text("Назва (опційно)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = manualFw,
              onValueChange = { manualFw = it },
              label = { Text("FW версія (напр. 154)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
          Text("Модель пристрою (Розділ IV):", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("VenusD", "VenusA", "VenusC", "VenusE", "VenusE mini").forEach { model ->
              FilterChip(
                selected = manualModel == model,
                onClick = { manualModel = model },
                label = { Text(model) },
              )
            }
          }
          Button(
            onClick = {
              onAddManualDevice(
                manualName,
                manualIp,
                manualPort.toIntOrNull() ?: MarstekApiConstants.DEFAULT_UDP_PORT,
                manualModel,
                manualFw.toIntOrNull(),
              )
              showAddForm = false
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Зберегти та підключитися")
          }
        }
      }
    }

    // Device Selector & Instance ID Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Доступні пристрої (LAN та Симулятори для тестування)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        uiState.endpoints.forEach { ep ->
          val isSelected = ep.id == uiState.selectedEndpoint?.id
          Card(
            onClick = { onSelectEndpoint(ep) },
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surface
              }
            ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = ep.name,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${if (ep.isSimulated) "[DEMO СИМУЛЯТОР] " else "[LAN UDP] "}IP: ${ep.ip}:${ep.port} • Модель: ${ep.deviceModel} • FW: ${ep.firmwareVersion ?: "?"}",
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  text = "Профіль: ${ep.profile.supportedComponentsSummary}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (isSelected) {
                Text(
                  text = "АКТИВНИЙ",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("ID екземпляра (params.id):", style = MaterialTheme.typography.bodyMedium)
          listOf(0, 1, 2).forEach { idVal ->
            FilterChip(
              selected = uiState.instanceId == idVal,
              onClick = { onUpdateInstanceId(idVal) },
              label = { Text("id = $idVal") },
            )
          }
        }
      }
    }

    // 3.1.1 Marstek.GetDevice Status Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "3.1.1 Marstek.GetDevice",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = { onQueryDeviceInfo(null) }) {
            Text("Опитати")
          }
        }
        val info = uiState.deviceInfo
        if (info == null) {
          Text("Натисніть «Опитати», щоб отримати базову інформацію про пристрій.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("Джерело (src)", info.src ?: "—")
          KeyValueRow("Модель (device)", info.device)
          KeyValueRow("Версія прошивки (ver)", "${info.ver ?: "—"} ${if ((info.ver ?: 0) >= 150) "(SYS >= 150 підтримується)" else ""}")
          KeyValueRow("BLE MAC (ble_mac)", info.bleMac ?: "—")
          KeyValueRow("WiFi MAC (wifi_mac)", info.wifiMac ?: "—")
          KeyValueRow("Мережа WiFi (wifi_name)", info.wifiName ?: "—")
          KeyValueRow("IP-адреса (ip)", info.ip ?: "—")
        }
      }
    }

    // 3.2.1 Wifi.GetStatus Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "3.2.1 Wifi.GetStatus",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = onQueryWifi) {
            Text("Опитати WiFi")
          }
        }
        val wifi = uiState.wifiStatus
        if (wifi == null) {
          Text("Немає даних Wifi.GetStatus.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("ID екземпляра (id)", "${wifi.id ?: 0}")
          KeyValueRow("WiFi SSID (ssid)", wifi.ssid ?: "null")
          KeyValueRow("Рівень сигналу (rssi)", "${wifi.rssi ?: "—"} dBm ${rssiQualityLabel(wifi.rssi)}")
          KeyValueRow("WiFi MAC (wifi_mac)", wifi.wifiMac ?: "—")
          KeyValueRow("IP пристрою (sta_ip)", wifi.staIp ?: "null")
          KeyValueRow("Шлюз (sta_gate)", wifi.staGate ?: "null")
          KeyValueRow("Маска підмережі (sta_mask)", wifi.staMask ?: "null")
          KeyValueRow("DNS сервер (sta_dns)", wifi.staDns ?: "null")
        }
      }
    }

    // 3.3.1 BLE.GetStatus Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "3.3.1 BLE.GetStatus (Bluetooth)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = onQueryBle) {
            Text("Опитати BLE")
          }
        }
        val ble = uiState.bleStatus
        if (ble == null) {
          Text("Немає даних BLE.GetStatus.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("ID екземпляра (id)", "${ble.id ?: 0}")
          KeyValueRow("Стан Bluetooth (state)", ble.state)
          KeyValueRow("Bluetooth MAC (ble_mac)", ble.bleMac ?: "—")
        }
      }
    }
  }
}

private fun rssiQualityLabel(rssi: Int?): String {
  if (rssi == null) return ""
  return when {
    rssi >= -55 -> "(Відмінний)"
    rssi >= -67 -> "(Добрий)"
    rssi >= -78 -> "(Задовільний)"
    else -> "(Слабкий)"
  }
}

@Composable
fun KeyValueRow(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      fontWeight = FontWeight.Medium,
    )
  }
}
