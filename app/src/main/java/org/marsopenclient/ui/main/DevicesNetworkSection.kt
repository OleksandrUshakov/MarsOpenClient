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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.MarstekEndpoint

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesNetworkSection(
  uiState: MarstekUiState,
  onSelectEndpoint: (MarstekEndpoint) -> Unit,
  onUpdateDiscoveryConfig: (port: String, bleMac: String) -> Unit,
  onToggleDeepSubnetScan: (Boolean) -> Unit = {},
  onRunDiscovery: () -> Unit,
  onSaveOrUpdateDevice: (existingId: String?, name: String, ip: String, port: Int, model: String, fw: Int?) -> Unit,
  onDeleteSavedDevice: (String) -> Unit = {},
  onToggleDemoMode: (Boolean) -> Unit = {},
  onUpdateInstanceId: (Int) -> Unit,
  onQueryDeviceInfo: (String?) -> Unit,
  onQueryWifi: () -> Unit,
  onQueryBle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showEditForm by rememberSaveable { mutableStateOf(false) }
  var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
  var formName by rememberSaveable { mutableStateOf("") }
  var formIp by rememberSaveable {
    mutableStateOf(
      uiState.lanDiagnostics.subnetPrefix?.let { "$it.50" } ?: "192.168.1.50"
    )
  }
  var formPort by rememberSaveable { mutableStateOf(MarstekApiConstants.DEFAULT_UDP_PORT.toString()) }
  var formModel by rememberSaveable { mutableStateOf("VenusE") }
  var formFw by rememberSaveable { mutableStateOf("154") }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Automatic LAN Search & Discovery Card
    ElevatedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Пошук станцій Marstek у Wi-Fi мережі",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = uiState.lanDiagnostics.summaryText,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Усі знайдені станції автоматично зберігаються у пам'яті смартфона. Стандартний UDP-порт Open API у додатку Marstek — 30000 (або рекомендований 49152–65535).",
          style = MaterialTheme.typography.bodySmall,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = uiState.discoveryPort,
            onValueChange = { onUpdateDiscoveryConfig(it, uiState.discoveryBleMac) },
            label = { Text("UDP Порт") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1.4f),
          ) {
            listOf("30000", "49152", "50000").forEach { presetPort ->
              FilterChip(
                selected = uiState.discoveryPort.trim() == presetPort,
                onClick = { onUpdateDiscoveryConfig(presetPort, uiState.discoveryBleMac) },
                label = { Text(presetPort) },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Глибоке сканування підмережі (${uiState.lanDiagnostics.subnetPrefix ?: "192.168.x"}.1–254)",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Знаходить станцію навіть якщо роутер блокує Broadcast-пакети",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.deepSubnetScanEnabled,
            onCheckedChange = onToggleDeepSubnetScan,
          )
        }

        if (uiState.isDiscovering) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          if (uiState.discoveryProgressText != null) {
            Text(
              text = uiState.discoveryProgressText,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
          }
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
            Text(if (uiState.isDiscovering) "Йде пошук..." else "Знайти станції в мережі")
          }
          OutlinedButton(
            onClick = {
              editingDeviceId = null
              formName = ""
              formIp = uiState.lanDiagnostics.subnetPrefix?.let { "$it.50" } ?: "192.168.1.50"
              formPort = uiState.discoveryPort.ifBlank { "30000" }
              showEditForm = !showEditForm
            },
          ) {
            Text(if (showEditForm && editingDeviceId == null) "Сховати" else "+ Вручну")
          }
        }
      }
    }

    // 2. Add / Edit Station Form Card
    if (showEditForm) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = if (editingDeviceId != null) "Редагування збереженої станції" else "Додати станцію за IP-адресою",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "IP-адресу станції можна переглянути в офіційному додатку Marstek або у списку підключених пристроїв вашого Wi-Fi роутера.",
            style = MaterialTheme.typography.bodySmall,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = formIp,
              onValueChange = { formIp = it },
              label = { Text("IP-адреса станції") },
              singleLine = true,
              modifier = Modifier.weight(1.4f),
            )
            OutlinedTextField(
              value = formPort,
              onValueChange = { formPort = it },
              label = { Text("UDP Порт") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = formName,
              onValueChange = { formName = it },
              label = { Text("Назва (напр. Балкон / Гараж)") },
              singleLine = true,
              modifier = Modifier.weight(1.3f),
            )
            OutlinedTextField(
              value = formFw,
              onValueChange = { formFw = it },
              label = { Text("Версія FW") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.weight(0.9f),
            )
          }
          Text("Модель зарядної станції:", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("VenusE", "VenusC", "VenusD", "VenusA", "VenusE mini").forEach { model ->
              FilterChip(
                selected = formModel == model,
                onClick = { formModel = model },
                label = { Text(model) },
              )
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                onSaveOrUpdateDevice(
                  editingDeviceId,
                  formName,
                  formIp,
                  formPort.toIntOrNull() ?: MarstekApiConstants.DEFAULT_UDP_PORT,
                  formModel,
                  formFw.toIntOrNull(),
                )
                showEditForm = false
                editingDeviceId = null
              },
              modifier = Modifier.weight(1f),
            ) {
              Text("Зберегти та підключитися")
            }
            OutlinedButton(
              onClick = {
                showEditForm = false
                editingDeviceId = null
              },
            ) {
              Text("Скасувати")
            }
          }
        }
      }
    }

    // 3. Saved Local Stations List
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Збережені станції (${uiState.savedDevices.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Зберігаються локально",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (uiState.savedDevices.isEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Список збережених станцій порожній",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Натисніть «Знайти станції в мережі» вище або додайте IP-адресу вашої станції вручну — вона автоматично збережеться у додатку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          uiState.savedDevices.forEach { ep ->
            val isSelected = ep.id == uiState.selectedEndpoint?.id
            SavedStationCard(
              endpoint = ep,
              isSelected = isSelected,
              onSelect = { onSelectEndpoint(ep) },
              onEdit = {
                editingDeviceId = ep.id
                formName = ep.name
                formIp = ep.ip
                formPort = ep.port.toString()
                formModel = ep.deviceModel
                formFw = (ep.firmwareVersion ?: 154).toString()
                showEditForm = true
              },
              onDelete = { onDeleteSavedDevice(ep.id) },
            )
          }
        }
      }
    }

    // 4. Active Station Network Details (Marstek.GetDevice, Wifi.GetStatus, BLE.GetStatus)
    val activeEp = uiState.selectedEndpoint
    if (activeEp != null) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Паспорт станції та мережа",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "${activeEp.name} (${activeEp.ip}:${activeEp.port})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = {
                onQueryDeviceInfo(null)
                onQueryWifi()
                onQueryBle()
              },
            ) {
              Text("Оновити дані")
            }
          }

          val info = uiState.deviceInfo
          KeyValueRow("Модель", info?.device ?: activeEp.deviceModel)
          KeyValueRow("Версія прошивки (FW)", "${info?.ver ?: activeEp.firmwareVersion ?: "—"}")
          KeyValueRow("Серійне ім'я (src)", info?.src ?: "—")
          KeyValueRow("Wi-Fi MAC", info?.wifiMac ?: uiState.wifiStatus?.wifiMac ?: activeEp.wifiMac ?: "—")
          KeyValueRow("Bluetooth MAC", info?.bleMac ?: uiState.bleStatus?.bleMac ?: activeEp.bleMac ?: "—")

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "Параметри підключення Wi-Fi та Bluetooth",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          val wifi = uiState.wifiStatus
          val ble = uiState.bleStatus
          KeyValueRow("Мережа Wi-Fi (SSID)", wifi?.ssid ?: info?.wifiName ?: activeEp.wifiName ?: "—")
          KeyValueRow("Рівень сигналу Wi-Fi", wifi?.rssi?.let { "$it dBm ${rssiQualityLabel(it)}" } ?: "—")
          KeyValueRow("IP-адреса станції", wifi?.staIp ?: info?.ip ?: activeEp.ip)
          KeyValueRow("Основний шлюз", wifi?.staGate ?: "—")
          KeyValueRow("Маска підмережі", wifi?.staMask ?: "—")
          KeyValueRow("DNS сервер", wifi?.staDns ?: "—")
          KeyValueRow("Статус Bluetooth", ble?.state ?: "—")
        }
      }
    }

    // 5. Demo Simulator Toggle & Advanced Instance ID Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Режим демо-симулятора",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Додає віртуальні станції (Venus D, Venus E, Venus E mini) для ознайомлення з інтерфейсом без фізичної батареї",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = uiState.demoModeEnabled,
            onCheckedChange = onToggleDemoMode,
          )
        }

        if (uiState.demoModeEnabled) {
          HorizontalDivider()
          Text("Віртуальні демо-станції:", style = MaterialTheme.typography.labelMedium)
          uiState.demoDevices.forEach { demoEp ->
            val isSelected = demoEp.id == uiState.selectedEndpoint?.id
            Card(
              onClick = { onSelectEndpoint(demoEp) },
              colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(demoEp.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                  Text(demoEp.profile.supportedComponentsSummary, style = MaterialTheme.typography.labelSmall)
                }
                if (isSelected) {
                  Text("АКТИВНА", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        HorizontalDivider()
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Канал пристрою (params.id):", style = MaterialTheme.typography.bodySmall)
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
  }
}

@Composable
private fun SavedStationCard(
  endpoint: MarstekEndpoint,
  isSelected: Boolean,
  onSelect: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    onClick = onSelect,
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
      }
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = endpoint.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "IP: ${endpoint.ip}:${endpoint.port} • Модель: ${endpoint.deviceModel} (FW ${endpoint.firmwareVersion ?: "?"})",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (isSelected) {
          Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              text = "АКТИВНА",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      val lastSeenStr = endpoint.lastSeenMillis?.let {
        SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date(it))
      } ?: "ще не було відповіді"
      val socStr = endpoint.lastKnownSoc?.let { "Заряд: $it% • " } ?: ""
      val modeStr = endpoint.lastKnownMode?.let { "Режим: $it • " } ?: ""
      Text(
        text = "${socStr}${modeStr}Останній зв'язок: $lastSeenStr",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        if (!isSelected) {
          TextButton(onClick = onSelect) {
            Text("Підключитися")
          }
        }
        TextButton(onClick = onEdit) {
          Text("Змінити")
        }
        TextButton(onClick = onDelete) {
          Text("Видалити", color = MaterialTheme.colorScheme.error)
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
      .padding(vertical = 3.dp),
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
