package com.travonet.wireguard.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travonet.wireguard.model.DefaultStrategies
import com.travonet.wireguard.model.EvasionStrategy
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditStrategyDialog(
    strategy: EvasionStrategy? = null,
    onDismiss: () -> Unit,
    onSave: (EvasionStrategy) -> Unit
) {
    var name by remember { mutableStateOf(strategy?.name ?: "Моя стратегия обхода") }
    var description by remember { mutableStateOf(strategy?.description ?: "") }
    var dsl by remember {
        mutableStateOf(
            strategy?.dsl ?: "mimic(quic) -> jumbo_fake(size=1600) -> fake_udp(size=148, corrupt=true)"
        )
    }
    var fakeTtl by remember { mutableStateOf(strategy?.fakeTtl ?: "auto") }
    var preJunk by remember { mutableStateOf((strategy?.preJunk ?: 64).toString()) }
    var normalTtl by remember { mutableStateOf((strategy?.normalTtl ?: 64).toString()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun appendToken(token: String) {
        val trimmed = dsl.trim()
        dsl = if (trimmed.isEmpty()) {
            token
        } else if (trimmed.endsWith("->")) {
            "$trimmed $token"
        } else {
            "$trimmed -> $token"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (strategy == null) "Создать стратегию DSL" else "Редактировать стратегию")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название стратегии") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (где и как работает)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Конструктор цепочки DSL:",
                    style = MaterialTheme.typography.labelMedium
                )

                // Quick insertion buttons
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { appendToken("fake(148)") },
                        label = { Text("fake(148)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("jumbo_fake(size=1600)") },
                        label = { Text("jumbo_fake", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("mimic(quic)") },
                        label = { Text("mimic(quic)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("ipopt_shift(8, mode=ts)") },
                        label = { Text("ipopt_shift", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("junk(64)") },
                        label = { Text("junk(64)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("fake_udp(size=148, ttl=5)") },
                        label = { Text("fake_udp", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { appendToken("sleep(10ms)") },
                        label = { Text("sleep(10ms)", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = dsl,
                    onValueChange = {
                        dsl = it
                        errorMessage = null
                    },
                    label = { Text("Формула стратегии (DSL)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    isError = errorMessage != null
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fakeTtl,
                        onValueChange = { fakeTtl = it },
                        label = { Text("FakeTTL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = preJunk,
                        onValueChange = { preJunk = it },
                        label = { Text("PreJunk (байт)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Команды DSL:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• fake_udp(size, ttl=, corrupt=, ipopt=) — фейковый handshake\n• jumbo_fake(size) — крупный фейк (не меньше 1500 байт)\n• junk(size) — мусорный UDP-пакет\n• mimic(quic|stun|dns) — мимикрия под протокол\n• ipopt_shift(len, mode=ts|rr|nop) — сдвиг IPv4-опций\n• sleep(duration) — задержка (напр. 10ms)\n\nДействия frag/fake_frag/badsum требуют root и отклоняются.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Укажите название стратегии"
                        return@Button
                    }
                    if (DefaultStrategies.hasRawOnlyActions(dsl)) {
                        errorMessage = "frag/fake_frag/badsum требуют raw-сокет и недоступны без root. " +
                            "Используйте junk, fake_udp, jumbo_fake, mimic, ipopt_shift или sleep."
                        return@Button
                    }
                    val newStrategy = EvasionStrategy(
                        id = strategy?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        description = description.trim(),
                        dsl = dsl.trim(),
                        fakeTtl = fakeTtl.trim().ifEmpty { "auto" },
                        preJunk = preJunk.toIntOrNull() ?: 64,
                        normalTtl = normalTtl.toIntOrNull() ?: 64,
                        isCustom = true
                    )
                    onSave(newStrategy)
                }
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
