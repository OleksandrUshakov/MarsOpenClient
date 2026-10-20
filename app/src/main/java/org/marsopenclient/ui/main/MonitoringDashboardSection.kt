package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun MonitoringDashboardSection(
  uiState: MarstekUiState,
  onQueryEsStatus: () -> Unit,
  onQueryBatteryStatus: () -> Unit,
  onQueryPvStatus: () -> Unit,
  onQueryEmStatus: () -> Unit,
  onRunDiscovery: () -> Unit = {},
  onGoToDevices: () -> Unit = {},
  onGoToModes: () -> Unit = {},
  onGoToLogs: () -> Unit = {},
  onEnableDemoMode: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val activeEndpoint = uiState.selectedEndpoint

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Onboarding / Empty State when no device is configured yet
    if (activeEndpoint == null) {
      ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Вітаємо у MarsOpenClient!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Додайте вашу зарядну станцію Marstek (Venus A, C, D, E або E mini), щоб переглядати заряд батареї, сонячну генерацію та керувати режимами роботи напряму через домашній Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Діагностика вашого смартфона:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = uiState.lanDiagnostics.summaryText,
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Переконайтеся, що в офіційному додатку Marstek увімкнено функцію «Open API» (стандартний порт: ${uiState.discoveryPort}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (uiState.isDiscovering && uiState.discoveryProgressText != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
              text = uiState.discoveryProgressText,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
            )
          }

          Button(
            onClick = onRunDiscovery,
            enabled = !uiState.isDiscovering,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              if (uiState.isDiscovering) {
                "Пошук станцій у Wi-Fi мережі..."
              } else {
                "Знайти станцію в мережі Wi-Fi (Автопошук)"
              }
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onGoToDevices,
              modifier = Modifier.weight(1f),
            ) {
              Text("Ввести IP вручну")
            }
            OutlinedButton(
              onClick = onEnableDemoMode,
              modifier = Modifier.weight(1f),
            ) {
              Text("Демо-режим")
            }
          }
        }
      }
      return@Column
    }

    // 2. Troubleshooting helper card when station is offline / timed out
    val es = uiState.esStatus
    val bat = uiState.batteryStatus
    val modeRes = uiState.esMode

    if (!uiState.isConnectedOnline && !uiState.isLoadingAll && es == null && bat == null) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
        ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Немає відповіді від станції (${activeEndpoint.ip}:${activeEndpoint.port})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          Text(
            text = "1. Відкрийте офіційний додаток Marstek та перевірте, чи увімкнено «Open API» і який порт вказано (за замовчуванням 30000).\n" +
              "2. Перевірте, що смартфон підключено до тієї ж Wi-Fi мережі (${uiState.lanDiagnostics.localIpv4 ?: "немає Wi-Fi IP"}).\n" +
              "3. Якщо роутер змінив IP станції — запустіть «Автопошук у мережі» на вкладці «Пристрої».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = onRunDiscovery,
              enabled = !uiState.isDiscovering,
              modifier = Modifier.weight(1f),
            ) {
              Text(if (uiState.isDiscovering) "Сканування..." else "Знайти новий IP")
            }
            OutlinedButton(
              onClick = onGoToLogs,
              modifier = Modifier.weight(1f),
            ) {
              Text("Журнал пакетів")
            }
          }
        }
      }
    }

    // 3. Hero Battery & Operating Status Card
    val socVal = bat?.soc ?: es?.batSoc ?: modeRes?.batSoc ?: activeEndpoint.lastKnownSoc?.toDouble()
    val batPowerW = es?.batPower
    val activeModeName = modeRes?.mode ?: activeEndpoint.lastKnownMode ?: "—"
    val remainingWh = bat?.batCapacity
    val ratedWh = bat?.ratedCapacity ?: es?.batCap
    val tempC = bat?.batTemp

    ElevatedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
      ),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Заряд батареї",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Text(
              text = if (socVal != null) "${formatNum(socVal)}%" else "— %",
              style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp),
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }

          Column(horizontalAlignment = Alignment.End) {
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(20.dp),
            ) {
              Text(
                text = "Режим: ${friendlyModeLabel(activeModeName)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onGoToModes) {
              Text("Змінити режим →")
            }
          }
        }

        val socProgress = ((socVal ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { socProgress },
          modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          val capText = when {
            remainingWh != null && ratedWh != null -> "${formatNum(remainingWh)} / ${formatNum(ratedWh)} Вт·год"
            ratedWh != null -> "Ємність: ${formatNum(ratedWh)} Вт·год"
            else -> "Ємність: —"
          }
          Text(
            text = capText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = if (tempC != null) "Температура: ${formatNum(tempC)} °C" else "Темп: —",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }

        if (bat != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            StatusBadgeChip(
              label = if (bat.chargFlag == true) "Заряд дозволено" else "Заряд заборонено",
              isPositive = bat.chargFlag == true,
            )
            StatusBadgeChip(
              label = if (bat.dischrgFlag == true) "Розряд дозволено" else "Розряд на паузі (DOD)",
              isPositive = bat.dischrgFlag == true,
            )
          }
        }
      }
    }

    // 4. Real-Time Power Flows Grid (2x2)
    Text(
      text = "Потоки потужності в реальному часі",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val pvPower = es?.pvPower ?: uiState.pvStatus?.totalPvPowerWatts
      PowerFlowCard(
        title = "Сонячні панелі (PV)",
        powerValue = "${formatNum(pvPower)} Вт",
        subtitle = if ((pvPower ?: 0.0) > 0) "Генерація активна" else "Очікування сонця",
        modifier = Modifier.weight(1f),
      )
      PowerFlowCard(
        title = "Потужність АКБ",
        powerValue = "${formatNum(batPowerW)} Вт",
        subtitle = when {
          batPowerW == null -> "Немає даних"
          batPowerW > 0 -> "Заряджання батареї"
          batPowerW < 0 -> "Розряджання батареї"
          else -> "В режимі очікування"
        },
        modifier = Modifier.weight(1f),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val ongridW = es?.ongridPower ?: modeRes?.ongridPower
      val offgridW = es?.offgridPower ?: modeRes?.offgridPower
      PowerFlowCard(
        title = "Домашня мережа (On-Grid)",
        powerValue = "${formatNum(ongridW)} Вт",
        subtitle = "Обмін з електромережею",
        modifier = Modifier.weight(1f),
      )
      PowerFlowCard(
        title = "Резерв UPS (Off-Grid)",
        powerValue = "${formatNum(offgridW)} Вт",
        subtitle = "Живлення резервних розеток",
        modifier = Modifier.weight(1f),
      )
    }

    // 5. Solar MPPT Channels Card (shown when PV data exists or device supports PV)
    val pv = uiState.pvStatus
    val supportsPv = activeEndpoint.profile.supportsPv
    if (supportsPv || (pv != null && pv.channels.isNotEmpty())) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Сонячні входи MPPT (PV)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Показники напруги, струму та потужності по кожному трекеру",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(onClick = onQueryPvStatus) {
              Text("Оновити PV")
            }
          }

          if (pv == null || pv.channels.isEmpty()) {
            Text(
              text = "Натисніть «Оновити PV», щоб отримати детальні показники по входах сонячних панелей.",
              style = MaterialTheme.typography.bodySmall,
            )
          } else {
            pv.channels.chunked(2).forEach { rowChannels ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                rowChannels.forEach { ch ->
                  Card(
                    colors = CardDefaults.cardColors(
                      containerColor = if (ch.isWorking) {
                        MaterialTheme.colorScheme.secondaryContainer
                      } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                      }
                    ),
                    modifier = Modifier.weight(1f),
                  ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Text(
                          text = ch.channelLabel,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                        )
                        Text(
                          text = if (ch.isWorking) "Активний" else "Очікування",
                          style = MaterialTheme.typography.labelSmall,
                          color = if (ch.isWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = "${formatNum(ch.powerWatts)} Вт",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                      )
                      Text(
                        text = "${formatNum(ch.voltageVolts)} В • ${formatNum(ch.currentAmps)} А",
                        style = MaterialTheme.typography.bodySmall,
                      )
                    }
                  }
                }
                if (rowChannels.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
    }

    // 6. Smart Meter / CT Transformer Card (EM.GetStatus)
    val em = uiState.emStatus
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Розумний лічильник / Датчик струму (CT)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            val ctConnected = em?.isCtConnected ?: modeRes?.isCtConnected ?: false
            Text(
              text = if (ctConnected) "Датчик CT підключено • Контроль по 3 фазах" else "Датчик CT не підключено (або режим без лічильника)",
              style = MaterialTheme.typography.bodySmall,
              color = if (ctConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(onClick = onQueryEmStatus) {
            Text("Оновити CT")
          }
        }

        val aPwr = em?.aPower ?: modeRes?.aPower
        val bPwr = em?.bPower ?: modeRes?.bPower
        val cPwr = em?.cPower ?: modeRes?.cPower
        val totPwr = em?.totalPower ?: modeRes?.totalPower

        if (em == null && modeRes == null) {
          Text("Немає даних від лічильника енергії.", style = MaterialTheme.typography.bodySmall)
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            PhasePill("Фаза A", "${formatNum(aPwr)} Вт", Modifier.weight(1f))
            PhasePill("Фаза B", "${formatNum(bPwr)} Вт", Modifier.weight(1f))
            PhasePill("Фаза C", "${formatNum(cPwr)} Вт", Modifier.weight(1f))
          }
          KeyValueRow("Сумарна потужність по фазах", "${formatNum(totPwr)} Вт")
          KeyValueRow("Імпорт за лічильником", "${formatNum(em?.inputEnergyWh ?: modeRes?.inputEnergyWh)} Вт·год")
          KeyValueRow("Експорт за лічильником", "${formatNum(em?.outputEnergyWh ?: modeRes?.outputEnergyWh)} Вт·год")
        }
      }
    }

    // 7. Cumulative Energy Counters Card (ES.GetStatus)
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Накопичена статистика енергії",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = onQueryEsStatus) {
            Text("Оновити")
          }
        }
        if (es == null) {
          Text("Оновіть дані станції для перегляду накопиченої енергії.", style = MaterialTheme.typography.bodySmall)
        } else {
          KeyValueRow(
            label = "Вироблено сонячними панелями",
            value = "${formatNum(es.totalPvEnergyKwh)} кВт·год (${formatNum(es.totalPvEnergyWh)} Вт·год)",
          )
          HorizontalDivider()
          KeyValueRow(
            label = "Віддано в домашню мережу",
            value = "${formatKwhUk(es.totalGridOutputEnergyWh)} (${formatNum(es.totalGridOutputEnergyWh)} Вт·год)",
          )
          HorizontalDivider()
          KeyValueRow(
            label = "Отримано з електромережі",
            value = "${formatKwhUk(es.totalGridInputEnergyWh)} (${formatNum(es.totalGridInputEnergyWh)} Вт·год)",
          )
          HorizontalDivider()
          KeyValueRow(
            label = "Спожито резервним навантаженням",
            value = "${formatKwhUk(es.totalLoadEnergyWh)} (${formatNum(es.totalLoadEnergyWh)} Вт·год)",
          )
        }
      }
    }
  }
}

@Composable
private fun PowerFlowCard(
  title: String,
  powerValue: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = powerValue,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PhasePill(
  phaseLabel: String,
  powerText: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(text = phaseLabel, style = MaterialTheme.typography.labelSmall)
      Text(text = powerText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun StatusBadgeChip(
  label: String,
  isPositive: Boolean,
) {
  Surface(
    color = if (isPositive) {
      MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    } else {
      MaterialTheme.colorScheme.errorContainer
    },
    shape = RoundedCornerShape(16.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = if (isPositive) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onErrorContainer
      },
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

internal fun friendlyModeLabel(rawMode: String?): String {
  return when (rawMode?.trim()?.lowercase()) {
    "auto" -> "Авто (Auto)"
    "ai" -> "ШІ (AI)"
    "manual" -> "За розкладом (Manual)"
    "passive" -> "Таймер (Passive)"
    "ups" -> "ДБЖ (UPS)"
    null, "", "null" -> "Невідомо"
    else -> rawMode
  }
}

internal fun formatNum(value: Double?): String {
  if (value == null) return "—"
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

internal fun formatKwhUk(wh: Double?): String {
  if (wh == null) return "— кВт·год"
  return String.format(Locale.US, "%.2f кВт·год", wh / 1000.0)
}
