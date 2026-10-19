package org.marsopenclient.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import org.marsopenclient.theme.MarsOpenClientTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MarstekDashboardViewModel = viewModel { MarstekDashboardViewModel() },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val packetLogs by viewModel.packetLogs.collectAsStateWithLifecycle()

  MarsOpenClientAppContent(
    uiState = uiState,
    packetLogs = packetLogs,
    onSelectSection = viewModel::selectSection,
    onDismissBanner = viewModel::dismissBanner,
    onRefreshAll = { viewModel.refreshAllStatuses(silentBanner = false) },
    onToggleAutoPoll = viewModel::toggleAutoPoll,
    onSelectEndpoint = viewModel::selectEndpoint,
    onUpdateDiscoveryConfig = viewModel::updateDiscoveryConfig,
    onRunDiscovery = viewModel::runLanDiscovery,
    onAddManualDevice = viewModel::addManualEndpoint,
    onUpdateInstanceId = viewModel::updateInstanceId,
    onQueryDeviceInfo = viewModel::queryDeviceInfo,
    onQueryWifi = viewModel::queryWifiStatus,
    onQueryBle = viewModel::queryBleStatus,
    onQueryBattery = viewModel::queryBatteryStatus,
    onQueryPv = viewModel::queryPvStatus,
    onQueryEsStatus = viewModel::queryEsStatus,
    onQueryEsMode = viewModel::queryEsMode,
    onQueryEmStatus = viewModel::queryEmStatus,
    onSelectModeTab = viewModel::selectEsModeTab,
    onUpdateAutoEnable = viewModel::updateAutoModeEnable,
    onUpdateAiEnable = viewModel::updateAiModeEnable,
    onUpdateManualForm = viewModel::updateManualForm,
    onUpdatePassiveForm = viewModel::updatePassiveForm,
    onUpdateUpsForm = viewModel::updateUpsForm,
    onApplyEsMode = viewModel::applySelectedEsMode,
    onUpdateDodSlider = viewModel::updateDodSlider,
    onSendDod = viewModel::sendDodSet,
    onSendBleAdv = viewModel::sendBleAdv,
    onSendLedCtrl = viewModel::sendLedCtrl,
    onUpdateCustomVersionWatts = viewModel::updateCustomVersionWatts,
    onSendSetVersion = viewModel::sendSetVersion,
    onSendFactoryReset = viewModel::sendFactoryReset,
    onToggleIncludeInstanceId = viewModel::toggleIncludeInstanceIdInSys,
    onSelectConsoleMethod = viewModel::selectConsoleMethodTemplate,
    onUpdateConsoleRequest = viewModel::updateConsoleRawRequest,
    onSendConsoleRequest = viewModel::sendConsoleRawRequest,
    onClearLogs = viewModel::clearLogs,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarsOpenClientAppContent(
  uiState: MarstekUiState,
  packetLogs: List<org.marsopenclient.api.PacketLogEntry>,
  onSelectSection: (AppSection) -> Unit,
  onDismissBanner: () -> Unit,
  onRefreshAll: () -> Unit,
  onToggleAutoPoll: (Boolean) -> Unit,
  onSelectEndpoint: (org.marsopenclient.api.MarstekEndpoint) -> Unit,
  onUpdateDiscoveryConfig: (String, String) -> Unit,
  onRunDiscovery: () -> Unit,
  onAddManualDevice: (String, String, Int, String, Int?) -> Unit,
  onUpdateInstanceId: (Int) -> Unit,
  onQueryDeviceInfo: (String?) -> Unit,
  onQueryWifi: () -> Unit,
  onQueryBle: () -> Unit,
  onQueryBattery: () -> Unit,
  onQueryPv: () -> Unit,
  onQueryEsStatus: () -> Unit,
  onQueryEsMode: () -> Unit,
  onQueryEmStatus: () -> Unit,
  onSelectModeTab: (org.marsopenclient.api.EsOperatingMode) -> Unit,
  onUpdateAutoEnable: (Boolean) -> Unit,
  onUpdateAiEnable: (Boolean) -> Unit,
  onUpdateManualForm: ((ManualModeFormState) -> ManualModeFormState) -> Unit,
  onUpdatePassiveForm: ((PassiveModeFormState) -> PassiveModeFormState) -> Unit,
  onUpdateUpsForm: ((UpsModeFormState) -> UpsModeFormState) -> Unit,
  onApplyEsMode: () -> Unit,
  onUpdateDodSlider: (Int) -> Unit,
  onSendDod: (Int) -> Unit,
  onSendBleAdv: (Int) -> Unit,
  onSendLedCtrl: (Int) -> Unit,
  onUpdateCustomVersionWatts: (String) -> Unit,
  onSendSetVersion: (Int) -> Unit,
  onSendFactoryReset: (Int) -> Unit,
  onToggleIncludeInstanceId: (Boolean) -> Unit,
  onSelectConsoleMethod: (String) -> Unit,
  onUpdateConsoleRequest: (String) -> Unit,
  onSendConsoleRequest: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeEndpoint = uiState.selectedEndpoint

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "MarsOpenClient • Marstek API v3.1",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = activeEndpoint?.let {
                "${if (it.isSimulated) "[DEMO] " else "[LAN] "}${it.name} (${it.ip}:${it.port})"
              } ?: "Пристрій не обрано",
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        actions = {
          Button(
            onClick = onRefreshAll,
            enabled = !uiState.isLoadingAll,
            modifier = Modifier.padding(end = 8.dp),
          ) {
            Text(if (uiState.isLoadingAll) "..." else "Оновити")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      )
    },
    bottomBar = {
      NavigationBar {
        AppSection.entries.forEach { section ->
          val badgeIcon = when (section) {
            AppSection.DEVICES -> "LAN"
            AppSection.DASHBOARD -> "ES/PV"
            AppSection.ES_MODE -> "MODE"
            AppSection.SYS_CONTROL -> "SYS"
            AppSection.CONSOLE -> "UDP"
          }
          NavigationBarItem(
            selected = uiState.currentSection == section,
            onClick = { onSelectSection(section) },
            icon = {
              Text(
                text = badgeIcon,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
              )
            },
            label = {
              Text(
                text = section.titleUk,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Quick Device Switcher & Auto-Poll Bar
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Швидкий вибір пристрою:",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("Авто-опитування (5с):", style = MaterialTheme.typography.labelSmall)
              Switch(
                checked = uiState.autoPollEnabled,
                onCheckedChange = onToggleAutoPoll,
              )
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            uiState.endpoints.take(3).forEach { ep ->
              FilterChip(
                selected = ep.id == activeEndpoint?.id,
                onClick = { onSelectEndpoint(ep) },
                label = {
                  Text(
                    text = ep.deviceModel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
              )
            }
          }
        }
      }

      // Status / Error Banner
      if (uiState.statusBannerMessage != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (uiState.isBannerError) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.tertiaryContainer
            }
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = uiState.statusBannerMessage,
              style = MaterialTheme.typography.bodySmall,
              color = if (uiState.isBannerError) {
                MaterialTheme.colorScheme.onErrorContainer
              } else {
                MaterialTheme.colorScheme.onTertiaryContainer
              },
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissBanner) {
              Text("OK")
            }
          }
        }
      }

      // Main Section Content
      when (uiState.currentSection) {
        AppSection.DEVICES -> {
          DevicesNetworkSection(
            uiState = uiState,
            onSelectEndpoint = onSelectEndpoint,
            onUpdateDiscoveryConfig = onUpdateDiscoveryConfig,
            onRunDiscovery = onRunDiscovery,
            onAddManualDevice = onAddManualDevice,
            onUpdateInstanceId = onUpdateInstanceId,
            onQueryDeviceInfo = onQueryDeviceInfo,
            onQueryWifi = onQueryWifi,
            onQueryBle = onQueryBle,
          )
        }
        AppSection.DASHBOARD -> {
          MonitoringDashboardSection(
            uiState = uiState,
            onQueryEsStatus = onQueryEsStatus,
            onQueryBatteryStatus = onQueryBattery,
            onQueryPvStatus = onQueryPv,
            onQueryEmStatus = onQueryEmStatus,
          )
        }
        AppSection.ES_MODE -> {
          EsModeConfigSection(
            uiState = uiState,
            onQueryEsMode = onQueryEsMode,
            onSelectModeTab = onSelectModeTab,
            onUpdateAutoEnable = onUpdateAutoEnable,
            onUpdateAiEnable = onUpdateAiEnable,
            onUpdateManualForm = onUpdateManualForm,
            onUpdatePassiveForm = onUpdatePassiveForm,
            onUpdateUpsForm = onUpdateUpsForm,
            onApplyEsMode = onApplyEsMode,
          )
        }
        AppSection.SYS_CONTROL -> {
          SysControlSection(
            uiState = uiState,
            onUpdateDodSlider = onUpdateDodSlider,
            onSendDod = onSendDod,
            onSendBleAdv = onSendBleAdv,
            onSendLedCtrl = onSendLedCtrl,
            onUpdateCustomVersionWatts = onUpdateCustomVersionWatts,
            onSendSetVersion = onSendSetVersion,
            onSendFactoryReset = onSendFactoryReset,
            onToggleIncludeInstanceId = onToggleIncludeInstanceId,
          )
        }
        AppSection.CONSOLE -> {
          ApiConsoleSection(
            uiState = uiState,
            packetLogs = packetLogs,
            onSelectMethodTemplate = onSelectConsoleMethod,
            onUpdateRawRequest = onUpdateConsoleRequest,
            onSendRawRequest = onSendConsoleRequest,
            onClearLogs = onClearLogs,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  MarsOpenClientTheme {
    MainScreen()
  }
}
