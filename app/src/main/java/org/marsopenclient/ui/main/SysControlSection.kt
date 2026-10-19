package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.marsopenclient.api.DeviceModelProfile
import org.marsopenclient.api.FactoryResetType
import org.marsopenclient.api.MarstekApiConstants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SysControlSection(
  uiState: MarstekUiState,
  onUpdateDodSlider: (Int) -> Unit,
  onSendDod: (Int) -> Unit,
  onSendBleAdv: (Int) -> Unit,
  onSendLedCtrl: (Int) -> Unit,
  onUpdateCustomVersionWatts: (String) -> Unit,
  onSendSetVersion: (Int) -> Unit,
  onSendFactoryReset: (Int) -> Unit,
  onToggleIncludeInstanceId: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val endpoint = uiState.selectedEndpoint
  val profile = endpoint?.profile ?: DeviceModelProfile.GENERIC
  var pendingResetType by rememberSaveable { mutableStateOf<FactoryResetType?>(null) }

  if (pendingResetType != null) {
    val resetOption = pendingResetType!!
    AlertDialog(
      onDismissRequest = { pendingResetType = null },
      title = { Text("Підтвердження Reset.Factory") },
      text = {
        Text(
          "Ви дійсно бажаєте виконати команду скидання до заводських налаштувань?\n\n" +
            "${resetOption.titleUk}\n(${resetOption.descriptionUk})\n\n" +
            "Цільовий пристрій: ${endpoint?.name ?: "—"} (${endpoint?.ip})"
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSendFactoryReset(resetOption.typeCode)
            pendingResetType = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Виконати Reset.Factory (type=${resetOption.typeCode})")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingResetType = null }) {
          Text("Скасувати")
        }
      },
    )
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Firmware & Model Compatibility Banner (Chapter 4)
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "3.8 SYS — Системні команди керування",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Профіль пристрою: ${profile.displayName} • Прошивка: ${endpoint?.firmwareVersion ?: "невідома"}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
        if (endpoint != null && !endpoint.isSysFirmwareSupported) {
          Text(
            text = "Увага (Розділ IV): Для моделей Venus C/E/D/A команди SYS потребують версії прошивки FW >= 150 (поточна: ${endpoint.firmwareVersion}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Додавати params.id (${uiState.instanceId}) у запити SYS:",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = uiState.includeInstanceIdInSys,
            onCheckedChange = onToggleIncludeInstanceId,
          )
        }
      }
    }

    // 3.8.1 DOD.SET
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "3.8.1 Глибина розряду — DOD (DOD.SET)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Діапазон налаштування: 30–88% (значення за замовчуванням: 88%).",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          text = "Обране значення DOD: ${uiState.dodSliderValue}% (Залишок у резерві: ${100 - uiState.dodSliderValue}%)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Slider(
          value = uiState.dodSliderValue.toFloat(),
          onValueChange = { onUpdateDodSlider(it.roundToInt()) },
          valueRange = MarstekApiConstants.MIN_DOD_VALUE.toFloat()..MarstekApiConstants.MAX_DOD_VALUE.toFloat(),
          steps = MarstekApiConstants.MAX_DOD_VALUE - MarstekApiConstants.MIN_DOD_VALUE - 1,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(30, 50, 70, 80, 88).forEach { preset ->
            FilterChip(
              selected = uiState.dodSliderValue == preset,
              onClick = { onUpdateDodSlider(preset) },
              label = { Text(if (preset == 88) "88% (Default)" else "$preset%") },
            )
          }
        }
        Button(
          onClick = { onSendDod(uiState.dodSliderValue) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Застосувати DOD.SET (value = ${uiState.dodSliderValue})")
        }
      }
    }

    // 3.8.2 Ble_block (Ble.Adv)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "3.8.2 Блокування Bluetooth — Ble_block (Ble.Adv)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Керування Bluetooth-трансляцією пристрою. Зверніть увагу на коди в специфікації: 0 = enable (увімкнути трансляцію), 1 = disable (вимкнути/заблокувати).",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { onSendBleAdv(0) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Увімкнути BLE (enable: 0)")
          }
          OutlinedButton(
            onClick = { onSendBleAdv(1) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Заблокувати BLE (enable: 1)")
          }
        }
      }
    }

    // 3.8.3 Led_Ctrl (Led.Ctrl)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "3.8.3 Індикація панелі — Led_Ctrl (Led.Ctrl)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Керування світлодіодами передньої панелі пристрою: 1 = ON (увімкнено), 0 = OFF (вимкнено).",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { onSendLedCtrl(1) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Увімкнути LED (state: 1)")
          }
          OutlinedButton(
            onClick = { onSendLedCtrl(0) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Вимкнути LED (state: 0)")
          }
        }
      }
    }

    // 3.8.4 Version_Set (Set.Ver)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "3.8.4 Версія потужності — Version_Set (Set.Ver)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Налаштування ліміту потужності заряду/розряду: 800W, 1200W (VA), 1500W (VA), 2200W (VD), 2500W (VE).",
          style = MaterialTheme.typography.bodySmall,
        )
        if (!profile.supportsVersionSet) {
          Text(
            text = "Примітка (Розділ 4.3): Venus E mini підтримує лише DOD, Ble_block та Led_Ctrl у розділі SYS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MarstekApiConstants.POWER_VERSIONS.forEach { opt ->
            FilterChip(
              selected = uiState.customVersionWatts == opt.watts.toString(),
              onClick = { onUpdateCustomVersionWatts(opt.watts.toString()) },
              label = { Text(opt.label) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = uiState.customVersionWatts,
            onValueChange = onUpdateCustomVersionWatts,
            label = { Text("version [W]") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = {
              val v = uiState.customVersionWatts.toIntOrNull() ?: 2500
              onSendSetVersion(v)
            },
          ) {
            Text("Надіслати Set.Ver")
          }
        }
      }
    }

    // 3.8.5 Factory_Reset (Reset.Factory)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "3.8.5 Скидання до заводських налаштувань — Factory_Reset (Reset.Factory)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (!profile.supportsFactoryReset) {
          Text(
            text = "Примітка (Розділ 4.3): Venus E mini не підтримує команду Reset.Factory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        FactoryResetType.entries.forEach { resetType ->
          OutlinedButton(
            onClick = { pendingResetType = resetType },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("${resetType.titleUk}")
          }
        }
      }
    }

    // Chapter IV: Device Support Matrix Reference Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Розділ IV: Матриця підтримки пристроїв Marstek (Rev 3.1)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        HorizontalDivider()
        Text(
          text = "4.1 Venus C / Venus E:\n• 1. Marstek, 2. WiFi, 3. Bluetooth, 4. Battery, 5. ES, 6. EM, 7. SYS (FW >= 150)\n• Manual time_num: 0–9",
          style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text(
          text = "4.2 Venus D / Venus A:\n• 1. Marstek, 2. WiFi, 3. Bluetooth, 4. Battery, 5. PV (only for Venus A/D), 6. ES, 7. EM, 8. SYS (FW >= 150)\n• Manual time_num: 0–9",
          style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text(
          text = "4.3 Venus E mini:\n• 1. Marstek, 2. WiFi, 3. Bluetooth, 4. Battery, 5. ES, 6. EM, 7. SYS (лише DOD, Ble_block, Led_Ctrl)\n• Manual time_num: 0–5, підтримує manual_set (0..3)",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}
