package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SovereignAvatar(
    name: String,
    colorHex: Long,
    sizeDp: Int = 44,
    showShield: Boolean = false,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().take(1).uppercase()
    val baseColor = Color(colorHex)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(baseColor, baseColor.copy(alpha = 0.7f))
                )
            )
            .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp * 0.42).sp
        )

        if (showShield) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((sizeDp * 0.38).dp)
                    .clip(CircleShape)
                    .background(SovereignVoid)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Encrypted",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size((sizeDp * 0.28).dp)
                )
            }
        }
    }
}

@Composable
fun SovereignHeader(
    title: String,
    subtitle: String,
    latencyMs: Int,
    isConnected: Boolean,
    isRtl: Boolean,
    onToggleRtl: () -> Unit,
    onSwitchUser: () -> Unit,
    currentUserHandle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldDim.copy(alpha = 0.2f))
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Sovereign Shield",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (isConnected) EmeraldPrimary else SlateMuted).copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, (if (isConnected) EmeraldPrimary else SlateMuted).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) EmeraldPrimary else SlateMuted)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConnected) "${latencyMs}ms" else "LOCAL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConnected) EmeraldPrimary else SlateMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Dual-User Switch Button for testing
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CyanSignal.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .testTag("switch_user_btn")
                            .clickable(onClick = onSwitchUser)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch User",
                                tint = CyanSignal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentUserHandle.take(7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanSignal
                            )
                        }
                    }

                    // RTL / LTR Toggle Button
                    IconButton(
                        onClick = onToggleRtl,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("toggle_rtl_btn")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isRtl) "EN" else "عربي",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SovereignSecurityBadge(
    fingerprint: String,
    onAuditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SovereignVoid,
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onAuditClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted Lock",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "تخزين محلي مشفّر على هذا الجهاز",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldPrimary
                    )
                    Text(
                        text = "معرّف محلي تجريبي: $fingerprint",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SlateMuted
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Local encryption",
                tint = EmeraldPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
