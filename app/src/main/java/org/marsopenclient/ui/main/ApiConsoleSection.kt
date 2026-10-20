package org.marsopenclient.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.marsopenclient.api.MarstekApiConstants
import org.marsopenclient.api.PacketLogEntry

private enum class LogFilterMode(val labelUk: String) {
  ALL("Всі"),
  ERRORS("Помилки"),
  SENT("Відправлені"),
  RECEIVED("Отримані"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiConsoleSection(
  uiState: MarstekUiState,
  packetLogs: List<PacketLogEntry>,
  onSelectMethodTemplate: (String) -> Unit,
  onUpdateRawRequest: (String) -> Unit,
  onSendRawRequest: () -> Unit,
  onClearLogs: () -> Unit,
  onBuildDiagnosticReport: () -> String = { "" },
  onNotifyMessage: (String, Boolean) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var filterMode by rememberSaveable { mutableStateOf(LogFilterMode.ALL) }
  var showRawTerminal by rememberSaveable { mutableStateOf(false) }

  val saveLogFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain"),
  ) { uri ->
    if (uri != null) {
      try {
        val reportText = onBuildDiagnosticReport()
        context.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(reportText.toByteArray(Charsets.UTF_8))
        }
        onNotifyMessage("Журнал успішно збережено у файл", false)
      } catch (e: Exception) {
        onNotifyMessage("Не вдалося зберегти файл журналу: ${e.localizedMessage}", true)
      }
    }
  }

  val filteredLogs = when (filterMode) {
    LogFilterMode.ALL -> packetLogs
    LogFilterMode.ERRORS -> packetLogs.filter { it.isError || it.direction == PacketLogEntry.Direction.ERROR }
    LogFilterMode.SENT -> packetLogs.filter { it.direction == PacketLogEntry.Direction.SENT }
    LogFilterMode.RECEIVED -> packetLogs.filter { it.direction == PacketLogEntry.Direction.RECEIVED }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Save / Share / Export Diagnostic Log Card
    ElevatedCard(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Збереження журналу та мережева діагностика",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Журнал автоматично зберігається у пам'яті смартфона між перезапусками. Ви можете зберегти повний звіт у текстовий файл або поділитися ним.",
          style = MaterialTheme.typography.bodySmall,
        )

        KeyValueRow("Wi-Fi IP смартфона", uiState.lanDiagnostics.localIpv4 ?: "не виявлено")
        KeyValueRow("Мережевий інтерфейс", uiState.lanDiagnostics.networkInterfaceName ?: "—")
        KeyValueRow("Підмережа сканування", uiState.lanDiagnostics.subnetPrefix?.let { "$it.0/24" } ?: "—")
        KeyValueRow("VPN активний", if (uiState.lanDiagnostics.isVpnActive) "Так (трафік прив'язано до Wi-Fi)" else "Ні")
        KeyValueRow("Всього записів у журналі", "${packetLogs.size}")

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = {
              val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
              saveLogFileLauncher.launch("marsopenclient-log-$stamp.txt")
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("Зберегти у файл")
          }
          OutlinedButton(
            onClick = {
              val reportText = onBuildDiagnosticReport()
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MarsOpenClient Diagnostic Log")
                putExtra(Intent.EXTRA_TEXT, reportText)
              }
              context.startActivity(Intent.createChooser(sendIntent, "Поділитися журналом"))
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("Поділитися")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val reportText = onBuildDiagnosticReport()
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              clipboard?.setPrimaryClip(ClipData.newPlainText("MarsOpenClient Log", reportText))
              onNotifyMessage("Журнал діагностики скопійовано в буфер обміну", false)
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("Скопіювати звіт")
          }
          OutlinedButton(
            onClick = onClearLogs,
            modifier = Modifier.weight(1f),
          ) {
            Text("Очистити журнал")
          }
        }
      }
    }

    // 2. Live UDP Packet Log Viewer with Filter Chips
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Журнал обміну пакетами UDP (${filteredLogs.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LogFilterMode.entries.forEach { mode ->
            FilterChip(
              selected = filterMode == mode,
              onClick = { filterMode = mode },
              label = { Text(mode.labelUk) },
            )
          }
        }

        if (filteredLogs.isEmpty()) {
          Text(
            text = "Немає записів для обраного фільтра.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          filteredLogs.take(60).forEach { entry ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = when (entry.direction) {
                  PacketLogEntry.Direction.SENT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                  PacketLogEntry.Direction.RECEIVED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                  PacketLogEntry.Direction.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                }
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  val dirLabel = when (entry.direction) {
                    PacketLogEntry.Direction.SENT -> "ВІДПРАВЛЕНО →"
                    PacketLogEntry.Direction.RECEIVED -> "← ОТРИМАНО"
                    PacketLogEntry.Direction.ERROR -> "⚠ ПОМИЛКА"
                  }
                  Text(
                    text = "$dirLabel • ${entry.method}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "${formatTime(entry.timestampMillis)}${entry.latencyMs?.let { " (${it} мс)" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
                Text(
                  text = entry.endpointAddress,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = entry.payload,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                )
              }
            }
          }
        }
      }
    }

    // 3. Expandable Raw JSON-RPC Command Builder for Advanced Users
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Інженерна консоль JSON-RPC",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Ручне тестування будь-якого з 14 методів протоколу Marstek Open API v3.1",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(onClick = { showRawTerminal = !showRawTerminal }) {
            Text(if (showRawTerminal) "Згорнути" else "Відкрити")
          }
        }

        if (showRawTerminal) {
          HorizontalDivider()
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MarstekApiConstants.ALL_METHODS.forEach { method ->
              FilterChip(
                selected = uiState.consoleSelectedMethod == method,
                onClick = { onSelectMethodTemplate(method) },
                label = { Text(method, fontSize = 11.sp) },
              )
            }
          }

          OutlinedTextField(
            value = uiState.consoleRawRequest,
            onValueChange = onUpdateRawRequest,
            label = { Text("JSON-RPC Запит (UDP Payload)") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            minLines = 6,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth(),
          )

          Button(
            onClick = onSendRawRequest,
            modifier = Modifier.fillMaxWidth(),
          ) {
            val ep = uiState.selectedEndpoint
            Text("Надіслати UDP пакет до ${ep?.ip ?: "—"}:${ep?.port ?: 30000}")
          }

          if (uiState.consoleLastResponse.isNotBlank()) {
            HorizontalDivider()
            Text(
              text = "Остання відповідь пристрою:",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = uiState.consoleLastResponse,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }
      }
    }
  }
}

private fun formatTime(millis: Long): String {
  val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
  return sdf.format(Date(millis))
}
