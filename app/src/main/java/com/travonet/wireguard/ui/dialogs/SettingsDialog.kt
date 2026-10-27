package com.travonet.wireguard.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travonet.wireguard.model.WarpRelayMode

/**
 * Общие настройки приложения: SOCKS5, источник релея для регистрации WARP,
 * параметры foreground службы, wakelock, автопереподключения и батареи.
 */
@Composable
fun AppSettingsDialog(
    currentSocksAddr: String,
    currentRelayMode: WarpRelayMode,
    customRelayUrl: String,
    customRelayToken: String,
    builtInRelayUrl: String,
    isForegroundService: Boolean,
    isWakeLock: Boolean,
    isAutoReconnect: Boolean,
    isShowAddresses: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        socksAddr: String,
        mode: WarpRelayMode,
        url: String,
        token: String,
        foregroundService: Boolean,
        wakeLock: Boolean,
        autoReconnect: Boolean,
        showAddresses: Boolean
    ) -> Unit
) {
    var socksAddr by remember { mutableStateOf(currentSocksAddr) }
    var relayMode by remember { mutableStateOf(currentRelayMode) }
    var relayUrl by remember { mutableStateOf(customRelayUrl) }
    var relayToken by remember { mutableStateOf(customRelayToken) }
    var foregroundService by remember { mutableStateOf(isForegroundService) }
    var wakeLock by remember { mutableStateOf(isWakeLock) }
    var autoReconnect by remember { mutableStateOf(isAutoReconnect) }
    var showAddresses by remember { mutableStateOf(isShowAddresses) }

    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        )
    }

    val customRelayValid = relayMode != WarpRelayMode.CUSTOM || relayUrl.isNotBlank()
    val canSave = socksAddr.isNotBlank() && customRelayValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SOCKS5",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = socksAddr,
                    onValueChange = { socksAddr = it },
                    label = { Text("IP:Порт локального прокси") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "По умолчанию 127.0.0.1:1080. Для доступа из локальной сети — 0.0.0.0:1080.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Служба и надёжность соединения",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingsSwitchRow(
                    title = "Уведомление службы (Foreground)",
                    subtitle = "Постоянное уведомление со статистикой трафика. Предотвращает выгрузку приложения системой.",
                    checked = foregroundService,
                    onCheckedChange = { foregroundService = it }
                )

                SettingsSwitchRow(
                    title = "Удержание WakeLock",
                    subtitle = "Предотвращает глубокий сон процессора во время активного VPN (может влиять на расход батареи).",
                    checked = wakeLock,
                    onCheckedChange = { wakeLock = it }
                )

                SettingsSwitchRow(
                    title = "Автоперезапуск при сбое",
                    subtitle = "Автоматически переподключать туннель при потере handshake или смене сети.",
                    checked = autoReconnect,
                    onCheckedChange = { autoReconnect = it }
                )

                SettingsSwitchRow(
                    title = "Пропуск оптимизации батареи",
                    subtitle = if (isIgnoringBatteryOptimizations) {
                        "Исключение активно (система не усыпляет VPN)."
                    } else {
                        "Ограничена системой (может обрывать связь в фоне)."
                    },
                    checked = isIgnoringBatteryOptimizations,
                    onCheckedChange = { checked ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                if (checked) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                )

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Отображение",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingsSwitchRow(
                    title = "Предпросмотр адресов",
                    subtitle = "Отображать IP-адрес и Endpoint в карточках конфигураций.",
                    checked = showAddresses,
                    onCheckedChange = { showAddresses = it }
                )

                HorizontalDivider(Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Релей регистрации WARP",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Через релей уходит только публичный ключ. Приватный ключ генерируется на телефоне и не покидает его.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RelayModeRow(
                    title = "Встроенный в сборку",
                    subtitle = if (builtInRelayUrl.isBlank()) {
                        "В этой сборке релей не задан"
                    } else {
                        builtInRelayUrl
                    },
                    selected = relayMode == WarpRelayMode.AUTO,
                    enabled = builtInRelayUrl.isNotBlank(),
                    onClick = { relayMode = WarpRelayMode.AUTO }
                )

                RelayModeRow(
                    title = "Свой релей",
                    subtitle = "Указать URL и токен вручную",
                    selected = relayMode == WarpRelayMode.CUSTOM,
                    enabled = true,
                    onClick = {
                        relayMode = WarpRelayMode.CUSTOM
                        if (relayUrl.isBlank()) relayUrl = builtInRelayUrl
                    }
                )

                if (relayMode == WarpRelayMode.CUSTOM) {
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        label = { Text("URL релея") },
                        placeholder = { Text("https://relay.example/register") },
                        singleLine = true,
                        isError = relayUrl.isBlank(),
                        supportingText = {
                            if (relayUrl.isBlank()) {
                                Text("Введите URL или выберите «Без релея»")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = relayToken,
                        onValueChange = { relayToken = it },
                        label = { Text("Токен") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                RelayModeRow(
                    title = "Без релея",
                    subtitle = "Прямая регистрация в Cloudflare (может блокироваться провайдером)",
                    selected = relayMode == WarpRelayMode.NONE,
                    enabled = true,
                    onClick = { relayMode = WarpRelayMode.NONE }
                )

                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        socksAddr.trim(),
                        relayMode,
                        relayUrl.trim(),
                        relayToken.trim(),
                        foregroundService,
                        wakeLock,
                        autoReconnect,
                        showAddresses
                    )
                },
                enabled = canSave
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun RelayModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
