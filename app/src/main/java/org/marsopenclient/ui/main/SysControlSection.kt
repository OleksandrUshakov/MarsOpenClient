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
import androidx.compose.material3.ElevatedCard
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
      title = { Text("Підтвердження скидання налаштувань") },
      text = {
        Text(
          "Ви дійсно бажаєте скинути станцію до заводських налаштувань?\n\n" +
            "${resetOption.titleUk}\n\n" +
            "Станція: ${endpoint?.name ?: "—"} (${endpoint?.ip})"
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
          Text("Підтвердити скидання")
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
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. DOD (Depth of Discharge) Card
    ElevatedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Глибина розряду батареї (DOD)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Визначає, яку максимальну частку заряду дозволено використовувати. За замовчуванням 88% (тобто 12% завжди залишається у резерві).",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Дозволений розряд (DOD): ${uiState.dodSliderValue}%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Мін. резерв заряду: ${100 - uiState.dodSliderValue}%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Slider(
          value = uiState.dodSliderValue.toFloat(),
          onValueChange = { onUpdateDodSlider(it.roundToInt()) },
          valueRange = MarstekApiConstants.MIN_DOD_VALUE.toFloat()..MarstekApiConstants.MAX_DOD_VALUE.toFloat(),
          steps = MarstekApiConstants.MAX_DOD_VALUE - MarstekApiConstants.MIN_DOD_VALUE - 1,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(50, 70, 80, 85, 88).forEach { preset ->
            FilterChip(
              selected = uiState.dodSliderValue == preset,
              onClick = { onUpdateDodSlider(preset) },
              label = { Text(if (preset == 88) "88% (Стандарт)" else "$preset% (резерв ${100 - preset}%)") },
            )
          }
        }
        Button(
          onClick = { onSendDod(uiState.dodSliderValue) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Зберегти глибину розряду (${uiState.dodSliderValue}%)")
        }
      }
    }

    // 2. Panel LED & Bluetooth Lock Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Індикація корпусу та захист Bluetooth",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        Text(
          text = "Світлодіодна індикація на передній панелі станції:",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { onSendLedCtrl(1) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Увімкнути LED")
          }
          OutlinedButton(
            onClick = { onSendLedCtrl(0) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Вимкнути LED (Нічний)")
          }
        }

        HorizontalDivider()

        Text(
          text = "Трансляція Bluetooth (захист від стороннього підключення):",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { onSendBleAdv(0) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Дозволити Bluetooth")
          }
          OutlinedButton(
            onClick = { onSendBleAdv(1) },
            modifier = Modifier.weight(1f),
          ) {
            Text("Заблокувати Bluetooth")
          }
        }
      }
    }

    // 3. Inverter Power Version Card (Set.Ver)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Потужність інвертора (Set.Ver)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Встановлює максимальну потужність заряду та віддачі відповідно до моделі станції та регіональних обмежень (напр. 800 Вт або 2500 Вт).",
          style = MaterialTheme.typography.bodySmall,
        )
        if (!profile.supportsVersionSet) {
          Text(
            text = "Примітка: Модель Venus E mini має фіксовану потужність 800 Вт.",
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
            label = { Text("Ліміт потужності (Вт)") },
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
            Text("Застосувати")
          }
        }
      }
    }

    // 4. Factory Reset & Compatibility Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Скидання до заводських налаштувань",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (endpoint != null && !endpoint.isSysFirmwareSupported) {
          Text(
            text = "Примітка: Системні команди SYS для Venus A/C/D/E потребують прошивки версії 150 або новішої (поточна: ${endpoint.firmwareVersion}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        FactoryResetType.entries.forEach { resetType ->
          OutlinedButton(
            onClick = { pendingResetType = resetType },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(resetType.titleUk)
          }
        }

        HorizontalDivider()
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Додавати ідентифікатор каналу (params.id = ${uiState.instanceId}) у системні команди:",
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
  }
}
