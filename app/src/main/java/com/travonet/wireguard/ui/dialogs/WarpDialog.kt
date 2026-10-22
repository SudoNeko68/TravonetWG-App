package com.travonet.wireguard.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

sealed interface WarpUiState {
    data object Running : WarpUiState
    data class Success(val profileName: String, val endpoint: String) : WarpUiState
    data class Failure(val message: String) : WarpUiState
}

@Composable
fun WarpProgressDialog(
    state: WarpUiState,
    relayConfigured: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onConfigureRelay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state !is WarpUiState.Running) onDismiss()
        },
        title = {
            Text(
                when (state) {
                    is WarpUiState.Running -> "Генерация WARP"
                    is WarpUiState.Success -> "WARP создан"
                    is WarpUiState.Failure -> "Не удалось"
                }
            )
        },
        text = {
            when (state) {
                is WarpUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (relayConfigured) {
                                "Регистрируем устройство через релей…"
                            } else {
                                "Регистрируем устройство в Cloudflare…"
                            }
                        )
                        Text(
                            "Обычно 5–20 секунд",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is WarpUiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Профиль «${state.profileName}» добавлен и выбран.")
                    Text(
                        "Endpoint: ${state.endpoint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is WarpUiState.Failure -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.message)
                    Text(
                        "Прямая регистрация блокируется провайдером по имени узла. " +
                            "Укажите релей на незаблокированном сервере — кнопка «Релей».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (state !is WarpUiState.Running) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
        dismissButton = {
            if (state is WarpUiState.Failure) {
                Row {
                    TextButton(onClick = onConfigureRelay) { Text("Релей") }
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }
            }
        }
    )
}

@Composable
fun WarpRelayDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Релей регистрации WARP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Если Cloudflare API заблокирован, укажите свой релей. " +
                        "Через него уходит только публичный ключ, приватный остаётся на телефоне.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL релея") },
                    placeholder = { Text("https://relay.example/register") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Токен (необязательно)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Пустой URL отключает релей.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url.trim(), token.trim()) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
