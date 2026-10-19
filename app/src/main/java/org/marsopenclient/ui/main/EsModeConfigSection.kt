package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.marsopenclient.api.DeviceModelProfile
import org.marsopenclient.api.EsOperatingMode
import org.marsopenclient.api.ManualSetDirection
import org.marsopenclient.api.WeekBitmask

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EsModeConfigSection(
  uiState: MarstekUiState,
  onQueryEsMode: () -> Unit,
  onSelectModeTab: (EsOperatingMode) -> Unit,
  onUpdateAutoEnable: (Boolean) -> Unit,
  onUpdateAiEnable: (Boolean) -> Unit,
  onUpdateManualForm: ((ManualModeFormState) -> ManualModeFormState) -> Unit,
  onUpdatePassiveForm: ((PassiveModeFormState) -> PassiveModeFormState) -> Unit,
  onUpdateUpsForm: ((UpsModeFormState) -> UpsModeFormState) -> Unit,
  onApplyEsMode: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeProfile = uiState.selectedEndpoint?.profile ?: DeviceModelProfile.GENERIC

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 3.6.3 ES.GetMode Current Status Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "3.6.3 ES.GetMode (Поточний режим роботи)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Примітка: параметри CT та фаз A/B/C активні у режимах Auto та AI",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          OutlinedButton(onClick = onQueryEsMode) {
            Text("Опитати режим")
          }
        }

        val modeRes = uiState.esMode
        if (modeRes == null) {
          Text("Натисніть «Опитати режим» для отримання ES.GetMode.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("ID екземпляра (id)", "${modeRes.id ?: 0}")
          KeyValueRow("Активний режим (mode)", modeRes.mode ?: "null")
          KeyValueRow("Потужність в мережу (ongrid_power)", "${formatNum(modeRes.ongridPower)} W")
          KeyValueRow("Резервна потужність (offgrid_power)", "${formatNum(modeRes.offgridPower)} W")
          KeyValueRow("Заряд батареї (bat_soc)", "${formatNum(modeRes.batSoc)} %")
          KeyValueRow(
            label = "Статус CT (ct_state)",
            value = when (modeRes.ctState) {
              1 -> "1: Connected (Підключено)"
              0 -> "0: Not connected (Не підключено)"
              else -> "null"
            },
          )
          KeyValueRow("Фаза A (a_power)", "${formatNum(modeRes.aPower)} W")
          KeyValueRow("Фаза B (b_power)", "${formatNum(modeRes.bPower)} W")
          KeyValueRow("Фаза C (c_power)", "${formatNum(modeRes.cPower)} W")
          KeyValueRow("Загальна потужність CT (total_power)", "${formatNum(modeRes.totalPower)} W")
          KeyValueRow(
            label = "Вхідна енергія (input_energy)",
            value = "${formatNum(modeRes.inputEnergyRaw)} [×0.1 Wh] (${formatNum(modeRes.inputEnergyWh)} Wh)",
          )
          KeyValueRow(
            label = "Вихідна енергія (output_energy)",
            value = "${formatNum(modeRes.outputEnergyRaw)} [×0.1 Wh] (${formatNum(modeRes.outputEnergyWh)} Wh)",
          )
        }
      }
    }

    // 3.6.2 ES.SetMode Configuration Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "3.6.2 ES.SetMode (Налаштування режиму генерації)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Оберіть режим роботи пристрою (Auto, AI, Manual, Passive, UPS) та налаштуйте його параметри:",
          style = MaterialTheme.typography.bodySmall,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          EsOperatingMode.entries.forEach { modeOption ->
            FilterChip(
              selected = uiState.selectedEsModeTab == modeOption,
              onClick = { onSelectModeTab(modeOption) },
              label = { Text(modeOption.wireName) },
            )
          }
        }

        HorizontalDivider()

        when (uiState.selectedEsModeTab) {
          EsOperatingMode.AUTO -> {
            Text(
              text = "Режим Auto (auto_cfg)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Автоматичне балансування потужності за показниками трансформатора струму (CT) або розумного лічильника.",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("auto_cfg.enable (1 = ON, 0 = OFF):")
              Switch(
                checked = uiState.autoModeEnable,
                onCheckedChange = onUpdateAutoEnable,
              )
            }
          }

          EsOperatingMode.AI -> {
            Text(
              text = "Режим AI (ai_cfg)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Інтелектуальна оптимізація накопичення та віддачі енергії (AI Mode).",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("ai_cfg.enable (1 = ON, 0 = OFF):")
              Switch(
                checked = uiState.aiModeEnable,
                onCheckedChange = onUpdateAiEnable,
              )
            }
          }

          EsOperatingMode.MANUAL -> {
            val mf = uiState.manualForm
            val maxSlot = activeProfile.maxManualTimeNum
            Text(
              text = "Режим Manual (manual_cfg) — Розклад за часовими слотами",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Номер періоду time_num: Venus A/C/D/E підтримує 0–9; Venus E mini підтримує 0–5 (поточний ліміт профілю: 0–$maxSlot).",
              style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              (0..maxSlot).forEach { slot ->
                FilterChip(
                  selected = mf.timeNum == slot,
                  onClick = { onUpdateManualForm { it.copy(timeNum = slot) } },
                  label = { Text("#$slot") },
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = mf.startTime,
                onValueChange = { v -> onUpdateManualForm { it.copy(startTime = v) } },
                label = { Text("start_time [hh:mm]") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = mf.endTime,
                onValueChange = { v -> onUpdateManualForm { it.copy(endTime = v) } },
                label = { Text("end_time [hh:mm]") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }

            // Week Bitmask Builder (week_set: 0..127)
            Text(
              text = "Дні тижня (week_set): значення = ${mf.weekSet} • бінарний байт = ${WeekBitmask.toBinaryString(mf.weekSet)}",
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
            )
            Text(
              text = "Обрано: ${WeekBitmask.toSelectedDaysSummary(mf.weekSet)} (молодші 7 біт: 1=Пн, 3=Пн+Вт, ..., 127=Всі дні)",
              style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              WeekBitmask.DAYS.forEach { day ->
                val selected = WeekBitmask.isDaySelected(mf.weekSet, day.bitIndex)
                FilterChip(
                  selected = selected,
                  onClick = {
                    onUpdateManualForm {
                      it.copy(weekSet = WeekBitmask.toggleDay(it.weekSet, day.bitIndex))
                    }
                  },
                  label = { Text("${day.shortNameUk} (${day.mask})") },
                )
              }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 127) } }) {
                Text("Всі дні (127)")
              }
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 31) } }) {
                Text("Будні Пн-Пт (31)")
              }
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 96) } }) {
                Text("Вихідні Сб-Нд (96)")
              }
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 0) } }) {
                Text("Очистити (0)")
              }
            }

            OutlinedTextField(
              value = mf.powerWatts,
              onValueChange = { v -> onUpdateManualForm { it.copy(powerWatts = v) } },
              label = { Text("Потужність (power, [W])") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Активувати слот розкладу (enable: 1=ON, 0=OFF):")
              Switch(
                checked = mf.enable,
                onCheckedChange = { v -> onUpdateManualForm { it.copy(enable = v) } },
              )
            }

            HorizontalDivider()
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Параметр manual_set (для Venus E mini)",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "0: disable; 1: enable charge; 2: enable discharge; 3: auto",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
              Switch(
                checked = mf.includeManualSet,
                onCheckedChange = { v -> onUpdateManualForm { it.copy(includeManualSet = v) } },
              )
            }
            if (mf.includeManualSet) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManualSetDirection.entries.forEach { dir ->
                  FilterChip(
                    selected = mf.manualSetCode == dir.code,
                    onClick = { onUpdateManualForm { it.copy(manualSetCode = dir.code) } },
                    label = { Text(dir.label) },
                  )
                }
              }
            }
          }

          EsOperatingMode.PASSIVE -> {
            val pf = uiState.passiveForm
            Text(
              text = "Режим Passive (passive_cfg) — Пряме керування з таймером",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Встановлює задану потужність (power, [W]) на час зворотного відліку (cd_time, [s]).",
              style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
              value = pf.powerWatts,
              onValueChange = { v -> onUpdatePassiveForm { it.copy(powerWatts = v) } },
              label = { Text("Потужність (power, [W])") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = pf.cdTimeSeconds,
              onValueChange = { v -> onUpdatePassiveForm { it.copy(cdTimeSeconds = v) } },
              label = { Text("Таймер зворотного відліку (cd_time, [s])") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(60, 300, 900, 1800, 3600).forEach { sec ->
                FilterChip(
                  selected = pf.cdTimeSeconds == sec.toString(),
                  onClick = { onUpdatePassiveForm { it.copy(cdTimeSeconds = sec.toString()) } },
                  label = { Text("${sec}s") },
                )
              }
            }
          }

          EsOperatingMode.UPS -> {
            val uf = uiState.upsForm
            Text(
              text = "Режим UPS (ups_cfg) — Безперебійне живлення",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("ups_cfg.enable (1 = ON, 0 = OFF):")
              Switch(
                checked = uf.enable,
                onCheckedChange = { v -> onUpdateUpsForm { it.copy(enable = v) } },
              )
            }
            Text(
              text = "Формат рядка mode у JSON (у прикладі стор. 14 — \"UPS\", у таблиці стор. 11 — \"Ups\"):",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = uf.useUppercaseWireMode,
                onClick = { onUpdateUpsForm { it.copy(useUppercaseWireMode = true) } },
                label = { Text("\"UPS\" (За прикладом JSON)") },
              )
              FilterChip(
                selected = !uf.useUppercaseWireMode,
                onClick = { onUpdateUpsForm { it.copy(useUppercaseWireMode = false) } },
                label = { Text("\"Ups\" (За таблицею)") },
              )
            }
          }
        }

        Button(
          onClick = onApplyEsMode,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Надіслати ES.SetMode (${uiState.selectedEsModeTab.wireName})")
        }
      }
    }
  }
}
