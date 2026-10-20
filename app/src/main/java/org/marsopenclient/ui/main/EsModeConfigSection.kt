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
import androidx.compose.material3.ElevatedCard
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
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Current Mode Summary Card (ES.GetMode)
    ElevatedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Поточний режим роботи станції",
              style = MaterialTheme.typography.labelLarge,
            )
            val modeRes = uiState.esMode
            Text(
              text = friendlyModeLabel(modeRes?.mode ?: uiState.selectedEndpoint?.lastKnownMode),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
          }
          OutlinedButton(onClick = onQueryEsMode) {
            Text("Оновити")
          }
        }

        val modeRes = uiState.esMode
        if (modeRes != null) {
          HorizontalDivider()
          KeyValueRow("Потужність мережі (On-Grid)", "${formatNum(modeRes.ongridPower)} Вт")
          KeyValueRow("Резервне навантаження (Off-Grid)", "${formatNum(modeRes.offgridPower)} Вт")
          KeyValueRow("Заряд акумулятора", "${formatNum(modeRes.batSoc)} %")
          KeyValueRow(
            label = "Датчик струму (CT)",
            value = if (modeRes.isCtConnected) "Підключено" else "Не підключено",
          )
          if (modeRes.isCtConnected) {
            KeyValueRow("Потужність по фазах (A / B / C)", "${formatNum(modeRes.aPower)} / ${formatNum(modeRes.bPower)} / ${formatNum(modeRes.cPower)} Вт")
            KeyValueRow("Сумарна потужність CT", "${formatNum(modeRes.totalPower)} Вт")
          }
        }
      }
    }

    // 2. Mode Selector & Configuration Card (ES.SetMode)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Вибір та налаштування режиму",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Оберіть бажаний режим роботи станції та натисніть кнопку «Застосувати режим» внизу:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val modeShortTitles = mapOf(
            EsOperatingMode.AUTO to "Авто (Auto)",
            EsOperatingMode.AI to "ШІ (AI)",
            EsOperatingMode.MANUAL to "За розкладом",
            EsOperatingMode.PASSIVE to "Ручний з таймером",
            EsOperatingMode.UPS to "ДБЖ (UPS)",
          )
          EsOperatingMode.entries.forEach { modeOption ->
            FilterChip(
              selected = uiState.selectedEsModeTab == modeOption,
              onClick = { onSelectModeTab(modeOption) },
              label = { Text(modeShortTitles[modeOption] ?: modeOption.wireName) },
            )
          }
        }

        HorizontalDivider()

        when (uiState.selectedEsModeTab) {
          EsOperatingMode.AUTO -> {
            Text(
              text = "Автоматичний режим (Auto)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Станція автоматично компенсує споживання будинку на основі показників трансформатора струму (CT) або розумного лічильника, підтримуючи нульовий баланс із мережею.",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Активувати автоматичний режим:", fontWeight = FontWeight.Medium)
              Switch(
                checked = uiState.autoModeEnable,
                onCheckedChange = onUpdateAutoEnable,
              )
            }
          }

          EsOperatingMode.AI -> {
            Text(
              text = "Інтелектуальний режим (AI)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Розумна оптимізація накопичення сонячної енергії та віддачі у пікові години на основі профілю споживання.",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Активувати режим ШІ:", fontWeight = FontWeight.Medium)
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
              text = "Робота за розкладом (Manual)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Дозволяє налаштувати до ${maxSlot + 1} часових періодів із фіксованою потужністю на обрані дні тижня.",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "Оберіть номер інтервалу (слот 0–$maxSlot):",
              style = MaterialTheme.typography.labelMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              (0..maxSlot).forEach { slot ->
                FilterChip(
                  selected = mf.timeNum == slot,
                  onClick = { onUpdateManualForm { it.copy(timeNum = slot) } },
                  label = { Text("Слот #$slot") },
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
                label = { Text("Початок (ГГ:ХХ)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = mf.endTime,
                onValueChange = { v -> onUpdateManualForm { it.copy(endTime = v) } },
                label = { Text("Кінець (ГГ:ХХ)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }

            // Friendly Week Days Selector
            Text(
              text = "Дні тижня: ${WeekBitmask.toSelectedDaysSummary(mf.weekSet)}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
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
                  label = { Text(day.shortNameUk) },
                )
              }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 127) } }) {
                Text("Щодня")
              }
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 31) } }) {
                Text("Будні (Пн–Пт)")
              }
              OutlinedButton(onClick = { onUpdateManualForm { it.copy(weekSet = 96) } }) {
                Text("Вихідні (Сб–Нд)")
              }
            }

            OutlinedTextField(
              value = mf.powerWatts,
              onValueChange = { v -> onUpdateManualForm { it.copy(powerWatts = v) } },
              label = { Text("Задана потужність (Вт)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("150", "300", "500", "800", "1200", "2000").forEach { presetW ->
                FilterChip(
                  selected = mf.powerWatts == presetW,
                  onClick = { onUpdateManualForm { it.copy(powerWatts = presetW) } },
                  label = { Text("$presetW Вт") },
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Увімкнути цей інтервал розкладу:", fontWeight = FontWeight.Medium)
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
                  text = "Напрямок роботи (для Venus E mini)",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Вибір між заряджанням, розряджанням та авто-режимом (manual_set)",
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
              text = "Ручне керування з таймером (Passive)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Миттєво встановлює задану потужність (Вт) на вказаний час зворотного відліку.",
              style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
              value = pf.powerWatts,
              onValueChange = { v -> onUpdatePassiveForm { it.copy(powerWatts = v) } },
              label = { Text("Потужність (Вт)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("100", "300", "500", "800", "1500").forEach { w ->
                FilterChip(
                  selected = pf.powerWatts == w,
                  onClick = { onUpdatePassiveForm { it.copy(powerWatts = w) } },
                  label = { Text("$w Вт") },
                )
              }
            }

            OutlinedTextField(
              value = pf.cdTimeSeconds,
              onValueChange = { v -> onUpdatePassiveForm { it.copy(cdTimeSeconds = v) } },
              label = { Text("Тривалість роботи (секунди)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
            Text("Швидкий вибір часу:", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              val presets = listOf(
                60 to "1 хв",
                300 to "5 хв",
                900 to "15 хв",
                1800 to "30 хв",
                3600 to "1 год",
                7200 to "2 год",
              )
              presets.forEach { (sec, label) ->
                FilterChip(
                  selected = pf.cdTimeSeconds == sec.toString(),
                  onClick = { onUpdatePassiveForm { it.copy(cdTimeSeconds = sec.toString()) } },
                  label = { Text(label) },
                )
              }
            }
          }

          EsOperatingMode.UPS -> {
            val uf = uiState.upsForm
            Text(
              text = "Джерело безперебійного живлення (UPS)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Підтримує резервне живлення підключених споживачів на випадок зникнення електроенергії в мережі.",
              style = MaterialTheme.typography.bodySmall,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Увімкнути режим ДБЖ (UPS):", fontWeight = FontWeight.Medium)
              Switch(
                checked = uf.enable,
                onCheckedChange = { v -> onUpdateUpsForm { it.copy(enable = v) } },
              )
            }
            Text(
              text = "Варіант написання команди (для сумісності різних ревізій прошивок):",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = uf.useUppercaseWireMode,
                onClick = { onUpdateUpsForm { it.copy(useUppercaseWireMode = true) } },
                label = { Text("\"UPS\" (Стандарт)") },
              )
              FilterChip(
                selected = !uf.useUppercaseWireMode,
                onClick = { onUpdateUpsForm { it.copy(useUppercaseWireMode = false) } },
                label = { Text("\"Ups\" (Альтернативний)") },
              )
            }
          }
        }

        Button(
          onClick = onApplyEsMode,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Застосувати режим «${uiState.selectedEsModeTab.displayName}»")
        }
      }
    }
  }
}
