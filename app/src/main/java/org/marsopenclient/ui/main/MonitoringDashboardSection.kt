package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun MonitoringDashboardSection(
  uiState: MarstekUiState,
  onQueryEsStatus: () -> Unit,
  onQueryBatteryStatus: () -> Unit,
  onQueryPvStatus: () -> Unit,
  onQueryEmStatus: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeEndpoint = uiState.selectedEndpoint

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 3.6.1 ES.GetStatus Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "3.6.1 ES.GetStatus (Енергосистема)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Поточні потоки потужності та накопичена статистика енергії",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          OutlinedButton(onClick = onQueryEsStatus) {
            Text("Оновити ES")
          }
        }

        val es = uiState.esStatus
        if (es == null) {
          Text("Натисніть «Оновити ES» для запиту ES.GetStatus.", style = MaterialTheme.typography.bodySmall)
        } else {
          val socProgress = ((es.batSoc ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = { socProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            MetricTile("SOC (bat_soc)", "${formatNum(es.batSoc)} %", Modifier.weight(1f))
            MetricTile("Ємність (bat_cap)", "${formatNum(es.batCap)} Wh", Modifier.weight(1f))
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            MetricTile("Сонце (pv_power)", "${formatNum(es.pvPower)} W", Modifier.weight(1f))
            MetricTile("АКБ (bat_power)", "${formatNum(es.batPower)} W", Modifier.weight(1f))
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            MetricTile("В мережу (ongrid)", "${formatNum(es.ongridPower)} W", Modifier.weight(1f))
            MetricTile("Резерв (offgrid)", "${formatNum(es.offgridPower)} W", Modifier.weight(1f))
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "Накопичена статистика енергії:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          KeyValueRow("ID екземпляра (id)", "${es.id ?: 0}")
          KeyValueRow(
            label = "Генерація PV (total_pv_energy)",
            value = "${formatNum(es.totalPvEnergyRaw)} [×0.01 kWh] (${formatNum(es.totalPvEnergyKwh)} kWh)",
          )
          KeyValueRow(
            label = "Віддано в мережу (total_grid_output_energy)",
            value = "${formatNum(es.totalGridOutputEnergyWh)} Wh (${formatKwhFromWh(es.totalGridOutputEnergyWh)})",
          )
          KeyValueRow(
            label = "Взято з мережі (total_grid_input_energy)",
            value = "${formatNum(es.totalGridInputEnergyWh)} Wh (${formatKwhFromWh(es.totalGridInputEnergyWh)})",
          )
          KeyValueRow(
            label = "Спожито навантаженням (total_load_energy)",
            value = "${formatNum(es.totalLoadEnergyWh)} Wh (${formatKwhFromWh(es.totalLoadEnergyWh)})",
          )
        }
      }
    }

    // 3.4.1 Bat.GetStatus Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "3.4.1 Bat.GetStatus (Батарея)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = onQueryBatteryStatus) {
            Text("Опитати Bat")
          }
        }
        val bat = uiState.batteryStatus
        if (bat == null) {
          Text("Натисніть «Опитати Bat» для отримання стану акумулятора.", style = MaterialTheme.typography.bodySmall)
        } else {
          val socFloat = ((bat.soc ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = { socFloat },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp),
          )
          Spacer(modifier = Modifier.height(2.dp))
          KeyValueRow("ID екземпляра (id)", "${bat.id ?: 0}")
          KeyValueRow("Заряд батареї (soc)", "${bat.socRaw ?: formatNum(bat.soc)} %")
          KeyValueRow(
            label = "Дозвіл заряджання (charg_flag)",
            value = if (bat.chargFlag == true) "true (Дозволено)" else "false (Заборонено)",
          )
          KeyValueRow(
            label = "Дозвіл розряджання (dischrg_flag)",
            value = if (bat.dischrgFlag == true) "true (Дозволено)" else "false (Заборонено)",
          )
          KeyValueRow("Температура (bat_temp)", "${formatNum(bat.batTemp)} °C")
          KeyValueRow("Залишкова ємність (bat_capacity)", "${formatNum(bat.batCapacity)} Wh")
          KeyValueRow("Номінальна ємність (rated_capacity)", "${formatNum(bat.ratedCapacity)} Wh")
        }
      }
    }

    // 3.5.1 PV.GetStatus Card (Venus A / Venus D)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "3.5.1 PV.GetStatus (Сонячні панелі)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            val supportsPv = activeEndpoint?.profile?.supportsPv ?: true
            Text(
              text = if (supportsPv) {
                "Підтримується моделями Venus A / Venus D (1–4 MPPT входи)"
              } else {
                "Примітка (Розділ 4): Модель ${activeEndpoint.deviceModel} офіційно не має PV-компонента, але ви можете надіслати запит вручну."
              },
              style = MaterialTheme.typography.bodySmall,
              color = if (supportsPv) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
          }
          OutlinedButton(onClick = onQueryPvStatus) {
            Text("Опитати PV")
          }
        }

        val pv = uiState.pvStatus
        if (pv == null || pv.channels.isEmpty()) {
          Text("Дані PV.GetStatus відсутні.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("ID екземпляра (id)", "${pv.id ?: 0}")
          KeyValueRow("Сумарна потужність PV", "${formatNum(pv.totalPvPowerWatts)} W")
          HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
          pv.channels.forEach { ch ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (ch.isWorking) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Канал ${ch.channelLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = if (ch.isWorking) "1: Work (Працює)" else "0: Standby (Очікування)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ch.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                KeyValueRow("Потужність (${ch.channelLabel.lowercase()}_power)", "${formatNum(ch.powerWatts)} W")
                KeyValueRow("Напруга (${ch.channelLabel.lowercase()}_voltage)", "${formatNum(ch.voltageVolts)} V")
                KeyValueRow("Струм (${ch.channelLabel.lowercase()}_current)", "${formatNum(ch.currentAmps)} A")
              }
            }
          }
        }
      }
    }

    // 3.7.1 EM.GetStatus Card (Energy Meter / CT)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "3.7.1 EM.GetStatus (Лічильник / CT)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Дані трансформатора струму (CT) або розумного лічильника по фазах A/B/C",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          OutlinedButton(onClick = onQueryEmStatus) {
            Text("Опитати EM")
          }
        }
        val em = uiState.emStatus
        if (em == null) {
          Text("Натисніть «Опитати EM» для отримання стану лічильника енергії.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow("ID екземпляра (id)", "${em.id ?: 0}")
          KeyValueRow(
            label = "Статус CT (ct_state)",
            value = when (em.ctState) {
              1 -> "1: Connected (Підключено)"
              0 -> "0: Not connected (Не підключено)"
              else -> "null"
            },
          )
          KeyValueRow("Фаза A (a_power)", "${formatNum(em.aPower)} W")
          KeyValueRow("Фаза B (b_power)", "${formatNum(em.bPower)} W")
          KeyValueRow("Фаза C (c_power)", "${formatNum(em.cPower)} W")
          KeyValueRow("Сумарна потужність (total_power)", "${formatNum(em.totalPower)} W")
          KeyValueRow(
            label = "Накопичений вхід (input_energy)",
            value = "${formatNum(em.inputEnergyRaw)} [×0.1 Wh] (${formatNum(em.inputEnergyWh)} Wh)",
          )
          KeyValueRow(
            label = "Накопичений вихід (output_energy)",
            value = "${formatNum(em.outputEnergyRaw)} [×0.1 Wh] (${formatNum(em.outputEnergyWh)} Wh)",
          )
        }
      }
    }
  }
}

@Composable
private fun MetricTile(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.padding(4.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
  }
}

internal fun formatNum(value: Double?): String {
  if (value == null) return "null"
  return if (value % 1.0 == 0.0) {
    value.toLong().toString()
  } else {
    String.format(Locale.US, "%.2f", value)
  }
}

internal fun formatKwhFromWh(wh: Double?): String {
  if (wh == null) return "—"
  return String.format(Locale.US, "%.2f kWh", wh / 1000.0)
}
