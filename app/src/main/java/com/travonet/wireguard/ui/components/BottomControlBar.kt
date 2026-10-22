package com.travonet.wireguard.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travonet.wireguard.model.TunnelMode
import com.travonet.wireguard.model.TunnelState
import com.travonet.wireguard.model.TunnelStats
import com.travonet.wireguard.model.TunnelStatus

@Composable
fun BottomControlBar(
    tunnelState: TunnelState,
    tunnelStats: TunnelStats,
    selectedMode: TunnelMode,
    socksAddr: String,
    onModeChange: (TunnelMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning = tunnelState.status == TunnelStatus.CONNECTED
    val isBusy = tunnelState.status == TunnelStatus.CONNECTING || tunnelState.status == TunnelStatus.STOPPING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mode Selector (VPN vs SOCKS5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {
                    SegmentedButton(
                        selected = selectedMode == TunnelMode.VPN,
                        onClick = { if (!isRunning && !isBusy) onModeChange(TunnelMode.VPN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !isRunning && !isBusy
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("VPN (TUN)", fontSize = 12.sp)
                    }

                    SegmentedButton(
                        selected = selectedMode == TunnelMode.SOCKS5,
                        onClick = { if (!isRunning && !isBusy) onModeChange(TunnelMode.SOCKS5) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !isRunning && !isBusy
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SOCKS5", fontSize = 12.sp)
                    }
                }
            }

            // Status and Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status info
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (tunnelState.status) {
                                    TunnelStatus.CONNECTED -> Color(0xFF4CAF50)
                                    TunnelStatus.CONNECTING, TunnelStatus.STOPPING -> Color(0xFFFF9800)
                                    TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
                                    TunnelStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = when (tunnelState.status) {
                                TunnelStatus.CONNECTED -> if (tunnelState.mode == TunnelMode.VPN) "VPN активен" else "SOCKS5 активен"
                                TunnelStatus.CONNECTING -> "Подключение..."
                                TunnelStatus.STOPPING -> "Остановка..."
                                TunnelStatus.ERROR -> "Ошибка"
                                TunnelStatus.DISCONNECTED -> "Отключено"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (tunnelState.status) {
                                TunnelStatus.CONNECTED -> Color(0xFF4CAF50)
                                TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        if (isRunning) {
                            if (tunnelState.mode == TunnelMode.VPN) {
                                Text(
                                    text = "↑ ${tunnelStats.formattedTx()}  •  ↓ ${tunnelStats.formattedRx()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = socksAddr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("SOCKS5 Address", "socks5://$socksAddr")
                                            cm.setPrimaryClip(clip)
                                            Toast.makeText(context, "Адрес SOCKS5 скопирован!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Скопировать", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        } else if (tunnelState.status == TunnelStatus.ERROR) {
                            Text(
                                text = tunnelState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Готов к запуску",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Action Button (Play / Stop)
                if (isRunning) {
                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Отключить")
                        Spacer(Modifier.width(6.dp))
                        Text("Стоп", fontWeight = FontWeight.Bold)
                    }
                } else if (isBusy) {
                    FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Ждите...")
                    }
                } else {
                    Button(
                        onClick = onStartClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Подключить")
                        Spacer(Modifier.width(6.dp))
                        Text("Пуск", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
