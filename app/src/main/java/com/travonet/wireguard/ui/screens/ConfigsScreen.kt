package com.travonet.wireguard.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travonet.wireguard.model.EvasionStrategy
import com.travonet.wireguard.model.WgProfile
import com.travonet.wireguard.parser.WgConfigParser
import com.travonet.wireguard.ui.dialogs.AddEditProfileDialog
import com.travonet.wireguard.ui.dialogs.DeleteConfirmDialog
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsScreen(
    profiles: List<WgProfile>,
    selectedProfileId: String?,
    fallbackStrategy: EvasionStrategy,
    onSelectProfile: (String) -> Unit,
    onAddOrUpdateProfile: (WgProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf<WgProfile?>(null) }
    var isAddingProfile by remember { mutableStateOf(false) }
    var deletingProfileId by remember { mutableStateOf<String?>(null) }

    // File picker for .conf files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()

                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".conf")
                    ?: "Импортированный профиль"
                val profile = WgConfigParser.parseProfile(fileName, content)
                onAddOrUpdateProfile(profile)
                onSelectProfile(profile.id)
                Toast.makeText(context, "Конфигурация импортирована!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Quick Action Bar (NekoBox style)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Каталог конфигураций",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Всего профилей: ${profiles.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.contains("[Interface]", ignoreCase = true)) {
                                    val profile = WgConfigParser.parseProfile("Профиль из буфера", text)
                                    onAddOrUpdateProfile(profile)
                                    onSelectProfile(profile.id)
                                    Toast.makeText(context, "Импортировано из буфера!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Буфер обмена не содержит конфигурацию WireGuard", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Импорт из буфера", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Открыть .conf файл", modifier = Modifier.size(18.dp))
                    }

                    FilledIconButton(
                        onClick = { isAddingProfile = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Нет добавленных конфигураций WireGuard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Button(onClick = { isAddingProfile = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Создать первую конфигурацию")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == selectedProfileId
                    val borderColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "border"
                    )

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProfile(profile.id) },
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectProfile(profile.id) }
                            )

                            Spacer(Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (isSelected) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("АКТИВЕН", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                if (profile.endpoint.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = profile.endpoint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (profile.address.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = profile.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                // Strategy Badge: Config or Fallback
                                if (profile.hasEmbeddedStrategy) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "⚡ В конфиге: ${profile.embeddedStrategy}",
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "⚙️ Из вкладки: ${fallbackStrategy.name}",
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }

                            // Card actions
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = { editingProfile = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать", modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("WireGuard Config", profile.rawConfig)
                                        cm.setPrimaryClip(clip)
                                        Toast.makeText(context, "Конфигурация скопирована", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Скопировать", modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { deletingProfileId = profile.id },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddingProfile) {
        AddEditProfileDialog(
            profile = null,
            onDismiss = { isAddingProfile = false },
            onSave = {
                onAddOrUpdateProfile(it)
                onSelectProfile(it.id)
                isAddingProfile = false
            }
        )
    }

    editingProfile?.let { prof ->
        AddEditProfileDialog(
            profile = prof,
            onDismiss = { editingProfile = null },
            onSave = {
                onAddOrUpdateProfile(it)
                editingProfile = null
            }
        )
    }

    deletingProfileId?.let { profId ->
        val prof = profiles.firstOrNull { it.id == profId }
        DeleteConfirmDialog(
            title = "Удалить профиль?",
            message = "Вы действительно хотите удалить конфигурацию «${prof?.name ?: ""}»?",
            onDismiss = { deletingProfileId = null },
            onConfirm = {
                onDeleteProfile(profId)
                deletingProfileId = null
            }
        )
    }
}
