package org.marsopenclient.ui.main

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiConsoleSection(
  uiState: MarstekUiState,
  packetLogs: List<PacketLogEntry>,
  onSelectMethodTemplate: (String) -> Unit,
  onUpdateRawRequest: (String) -> Unit,
  onSendRawRequest: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Raw JSON-RPC Command Builder Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "2.1 Консоль JSON-RPC over UDP (Всі 14 методів API)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Оберіть шаблон методу з офіційної документації Marstek Rev 3.1 або відредагуйте JSON вручну:",
          style = MaterialTheme.typography.bodySmall,
        )
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

    // Live UDP Packet Inspector Card
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Журнал UDP пакетів (${packetLogs.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(onClick = onClearLogs) {
            Text("Очистити")
          }
        }
        if (packetLogs.isEmpty()) {
          Text("Журнал порожній. Виконайте будь-яку команду для перегляду трафіку.", style = MaterialTheme.typography.bodySmall)
        } else {
          packetLogs.take(25).forEach { entry ->
            Card(
              colors = CardDefaults.cardColors(
                containerColor = when (entry.direction) {
                  PacketLogEntry.Direction.SENT -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                  PacketLogEntry.Direction.RECEIVED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                  PacketLogEntry.Direction.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                }
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "${entry.direction.name} • ${entry.method}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "${formatTime(entry.timestampMillis)}${entry.latencyMs?.let { " (${it}ms)" } ?: ""}",
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

    // Section 2.1 Error Code Reference Table
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Довідник кодів помилок JSON-RPC (Розділ 2.1)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        KeyValueRow("-32700", "Parse error (Невалідний JSON)")
        KeyValueRow("-32600", "Invalid Request (Некоректний запит)")
        KeyValueRow("-32601", "Method not found (Метод відсутній)")
        KeyValueRow("-32602", "Invalid params (Некоректні параметри)")
        KeyValueRow("-32603", "Internal error (Внутрішня помилка RPC)")
        KeyValueRow("-32000..-32099", "Server error (Помилка сервера пристрою)")
      }
    }
  }
}

private fun formatTime(millis: Long): String {
  val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
  return sdf.format(Date(millis))
}
