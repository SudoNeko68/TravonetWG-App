package com.travonet.wireguard.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travonet.wireguard.model.EvasionStrategy
import com.travonet.wireguard.model.TunnelMode
import com.travonet.wireguard.model.WarpRelayMode
import com.travonet.wireguard.model.WgProfile
import com.travonet.wireguard.repository.AppRepository
import com.travonet.wireguard.service.TunnelManager
import com.travonet.wireguard.ui.components.BottomControlBar
import com.travonet.wireguard.ui.dialogs.AppSettingsDialog
import com.travonet.wireguard.ui.dialogs.WarpProgressDialog
import com.travonet.wireguard.ui.dialogs.WarpRelayDialog
import com.travonet.wireguard.ui.dialogs.WarpUiState
import com.travonet.wireguard.ui.screens.ConfigsScreen
import com.travonet.wireguard.ui.screens.StrategiesScreen
import com.travonet.wireguard.warp.WarpGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.travonet.backend.GoBackend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRequestStartTunnel: (WgProfile, EvasionStrategy, TunnelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var profiles by remember { mutableStateOf(repository.getProfiles()) }
    var selectedProfileId by remember { mutableStateOf(repository.getSelectedProfileId()) }

    var strategies by remember { mutableStateOf(repository.getStrategies()) }
    var selectedStrategyId by remember { mutableStateOf(repository.getSelectedStrategyId()) }

    var tunnelMode by remember { mutableStateOf(repository.getTunnelMode()) }
    var socksAddr by remember { mutableStateOf(repository.getSocksListenAddr()) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    var warpUiState by remember { mutableStateOf<WarpUiState?>(null) }
    var showWarpRelayDialog by remember { mutableStateOf(false) }
    var warpRelayMode by remember { mutableStateOf(repository.getWarpRelayMode()) }
    var warpRelayUrl by remember { mutableStateOf(repository.getWarpRelayUrl()) }
    var warpRelayToken by remember { mutableStateOf(repository.getWarpRelayToken()) }
    val scope = rememberCoroutineScope()

    val tunnelState by TunnelManager.state.collectAsState()
    val tunnelStats by TunnelManager.stats.collectAsState()

    val activeFallbackStrategy = remember(strategies, selectedStrategyId) {
        strategies.firstOrNull { it.id == selectedStrategyId } ?: strategies.first()
    }

    fun generateWarp() {
        if (warpUiState is WarpUiState.Running) return

        val name = WarpGenerator.nextName(profiles)
        val relayUrl = warpRelayUrl
        val relayToken = warpRelayToken
        warpUiState = WarpUiState.Running

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { WarpGenerator.generate(name, relayUrl, relayToken) }
            }
            result.onSuccess { profile ->
                repository.addOrUpdateProfile(profile)
                profiles = repository.getProfiles()
                selectedProfileId = profile.id
                repository.setSelectedProfileId(profile.id)
                selectedTab = 0
                warpUiState = WarpUiState.Success(profile.name, profile.endpoint)
            }.onFailure { error ->
                warpUiState = WarpUiState.Failure(
                    error.message?.takeIf { it.isNotBlank() }
                        ?: "Ошибка ${error.javaClass.simpleName}"
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = "TravonetWG",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text("DPI Bypass", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                        IconButton(
                            onClick = { generateWarp() },
                            enabled = warpUiState !is WarpUiState.Running
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = "Сгенерировать WARP")
                        }
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "О программе")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // The two top tab switchers requested by user
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Конфигурации (${profiles.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Стратегии обхода", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            BottomControlBar(
                tunnelState = tunnelState,
                tunnelStats = tunnelStats,
                selectedMode = tunnelMode,
                socksAddr = socksAddr,
                onModeChange = { newMode ->
                    tunnelMode = newMode
                    repository.setTunnelMode(newMode)
                },
                onStartClick = {
                    val activeProf = profiles.firstOrNull { it.id == selectedProfileId }
                        ?: profiles.firstOrNull()
                    if (activeProf == null) {
                        Toast.makeText(context, "Выберите конфигурацию WireGuard!", Toast.LENGTH_SHORT).show()
                        return@BottomControlBar
                    }
                    onRequestStartTunnel(activeProf, activeFallbackStrategy, tunnelMode)
                },
                onStopClick = {
                    TunnelManager.stop(context)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ConfigsScreen(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    fallbackStrategy = activeFallbackStrategy,
                    onSelectProfile = { id ->
                        selectedProfileId = id
                        repository.setSelectedProfileId(id)
                    },
                    onAddOrUpdateProfile = { newProf ->
                        repository.addOrUpdateProfile(newProf)
                        profiles = repository.getProfiles()
                    },
                    onDeleteProfile = { id ->
                        repository.deleteProfile(id)
                        profiles = repository.getProfiles()
                        selectedProfileId = repository.getSelectedProfileId()
                    }
                )

                1 -> StrategiesScreen(
                    strategies = strategies,
                    selectedStrategyId = selectedStrategyId,
                    onSelectStrategy = { id ->
                        selectedStrategyId = id
                        repository.setSelectedStrategyId(id)
                    },
                    onAddOrUpdateStrategy = { newStrat ->
                        repository.addOrUpdateCustomStrategy(newStrat)
                        strategies = repository.getStrategies()
                    },
                    onDeleteStrategy = { id ->
                        repository.deleteCustomStrategy(id)
                        strategies = repository.getStrategies()
                        selectedStrategyId = repository.getSelectedStrategyId()
                    }
                )
            }
        }
    }

    if (showSettingsDialog) {
        AppSettingsDialog(
            currentSocksAddr = socksAddr,
            currentRelayMode = warpRelayMode,
            customRelayUrl = repository.getCustomWarpRelayUrl(),
            customRelayToken = repository.getCustomWarpRelayToken(),
            builtInRelayUrl = repository.getBuiltInWarpRelayUrl(),
            onDismiss = { showSettingsDialog = false },
            onSave = { newAddr, relayMode, relayUrl, relayToken ->
                socksAddr = newAddr
                repository.setSocksListenAddr(newAddr)
                repository.saveWarpRelaySettings(relayMode, relayUrl, relayToken)
                warpRelayMode = repository.getWarpRelayMode()
                warpRelayUrl = repository.getWarpRelayUrl()
                warpRelayToken = repository.getWarpRelayToken()
                showSettingsDialog = false

                val relaySummary = when (relayMode) {
                    WarpRelayMode.AUTO -> "встроенный релей"
                    WarpRelayMode.CUSTOM -> "свой релей"
                    WarpRelayMode.NONE -> "без релея"
                }
                Toast.makeText(
                    context,
                    "Настройки сохранены: $relaySummary",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showInfoDialog) {
        val nativeVer = try {
            GoBackend.wgVersion() ?: "TravonetWG-go (Unknown)"
        } catch (e: Throwable) {
            "Ошибка JNI: ${e.message}"
        }

        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("О приложении TravonetWG") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TravonetWG — клиент WireGuard со встроенной L3/L4 десинхронизацией пакетов для обхода глубокого анализа ТСПУ / DPI.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text(
                        text = "• Режим VPN: системный TUN интерфейс (маршрутизирует весь интернет).\n• Режим SOCKS5: локальный прокси без прав VPN на $socksAddr.\n• Десинхронизация: если в конфигурации не указана Strategy, используется выбранная во второй вкладке.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    Text(
                        text = "Версия ядра: $nativeVer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }

    warpUiState?.let { state ->
        WarpProgressDialog(
            state = state,
            relayConfigured = warpRelayUrl.isNotBlank(),
            onDismiss = { warpUiState = null },
            onRetry = { generateWarp() },
            onConfigureRelay = {
                warpUiState = null
                showWarpRelayDialog = true
            }
        )
    }

    if (showWarpRelayDialog) {
        WarpRelayDialog(
            currentUrl = warpRelayUrl,
            currentToken = warpRelayToken,
            onSave = { url, token ->
                repository.setWarpRelayUrl(url)
                repository.setWarpRelayToken(token)
                warpRelayMode = repository.getWarpRelayMode()
                warpRelayUrl = repository.getWarpRelayUrl()
                warpRelayToken = repository.getWarpRelayToken()
                showWarpRelayDialog = false
                Toast.makeText(
                    context,
                    if (url.isBlank()) "Релей WARP отключён" else "Релей WARP сохранён",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showWarpRelayDialog = false }
        )
    }
}
