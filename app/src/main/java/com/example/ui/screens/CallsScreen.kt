package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CallSession
import com.example.model.CallState
import com.example.model.CallType
import com.example.ui.SovereignViewModel
import com.example.ui.components.SovereignAvatar
import com.example.ui.theme.*

@Composable
fun CallsScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val activeCall by viewModel.activeCall.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Informational banner for the non-networked call UI
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SovereignBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimary.copy(alpha = 0.2f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "واجهة مكالمات تجريبية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "لا يتم حاليًا نقل الصوت أو الفيديو عبر الشبكة",
                            fontSize = 12.sp,
                            color = SlateMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "جهات الاتصال السريعة",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Call Contacts
            val quickPeers = listOf(
                Triple("طارق - مستشار الأمن", "@tariq_privacy", 0xFF38BDF8),
                Triple("د. فاطمة الزهراء", "@fatima_crypto", 0xFFA855F7),
                Triple("عمر - مسؤول الشبكات", "@omar_sys", 0xFF10B981)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPeers.size) { index ->
                    val (name, handle, colorHex) = quickPeers[index]
                    CallContactRow(
                        name = name,
                        handle = handle,
                        colorHex = colorHex,
                        onAudioCall = { viewModel.startCall(handle, name, CallType.AUDIO) },
                        onVideoCall = { viewModel.startCall(handle, name, CallType.VIDEO) }
                    )
                }
            }
        }

        // Active Call Overlay
        activeCall?.let { call ->
            ActiveCallOverlay(
                call = call,
                onMuteToggle = { viewModel.toggleCallMute() },
                onCameraToggle = { viewModel.toggleCallCamera() },
                onSpeakerToggle = { viewModel.toggleCallSpeaker() },
                onEndCall = { viewModel.endCall() }
            )
        }
    }
}

@Composable
fun CallContactRow(
    name: String,
    handle: String,
    colorHex: Long,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SovereignBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SovereignAvatar(name = name, colorHex = colorHex, sizeDp = 44, showShield = false)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "$handle • واجهة تجريبية فقط", fontSize = 11.sp, color = AmberWarning)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onAudioCall,
                    modifier = Modifier.testTag("quick_call_audio_${handle}")
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Audio Call", tint = EmeraldPrimary)
                }
                IconButton(
                    onClick = onVideoCall,
                    modifier = Modifier.testTag("quick_call_video_${handle}")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = CyanSignal)
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    call: CallSession,
    onMuteToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SovereignVoid, SovereignSurface, SovereignVoid)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("active_call_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Call Status Bar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = EmeraldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (call.callType == CallType.AUDIO) "محاكاة مكالمة صوتية" else "محاكاة مكالمة مرئية",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (call.state) {
                        CallState.RINGING -> "محاكاة بدء الاتصال..."
                        CallState.CONNECTING -> "المصافحة الرقمية (P2P Handshake)..."
                        CallState.CONNECTED -> {
                            val mins = call.durationSec / 60
                            val secs = call.durationSec % 60
                            "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
                        }
                        CallState.ENDED -> "انتهت المكالمة"
                    },
                    fontSize = 14.sp,
                    color = SlateMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Center: Avatar or Simulated Video Stream
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (call.callType == CallType.VIDEO && !call.isCameraOff) {
                    // Simulated WebRTC video viewport
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SovereignSurfaceVariant)
                            .border(2.dp, EmeraldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = CyanSignal,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "معاينة واجهة فقط — لا يوجد بث فيديو",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TitaniumWhite
                            )
                            Text(
                                text = "معدل الإطارات: 60fps • 1080p",
                                fontSize = 11.sp,
                                color = EmeraldPrimary
                            )
                        }

                        // Local User Pip
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(70.dp, 90.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SovereignVoid)
                                .border(1.dp, SovereignBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "كاميرتك", fontSize = 10.sp, color = SlateMuted)
                        }
                    }
                } else {
                    // Audio Avatar
                    SovereignAvatar(
                        name = call.peerName,
                        colorHex = 0xFF10B981,
                        sizeDp = 100,
                        showShield = false
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = call.peerName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitaniumWhite
                )

                Text(
                    text = call.peerHandle,
                    fontSize = 14.sp,
                    color = SlateMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Non-cryptographic demo session identifier
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SovereignVoid,
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "معرّف جلسة تجريبي:",
                            fontSize = 10.sp,
                            color = SlateMuted
                        )
                        Text(
                            text = call.securityFingerprint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldPrimary
                        )
                    }
                }
            }

            // Bottom Call Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic
                IconButton(
                    onClick = onMuteToggle,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (call.isMuted) RoseDestruct else SovereignSurfaceVariant)
                        .testTag("call_mute_toggle")
                ) {
                    Icon(
                        imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = TitaniumWhite
                    )
                }

                // End Call
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(RoseDestruct)
                        .testTag("end_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = TitaniumWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Camera Toggle
                IconButton(
                    onClick = onCameraToggle,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (call.isCameraOff) SovereignBorder else SovereignSurfaceVariant)
                        .testTag("call_camera_toggle")
                ) {
                    Icon(
                        imageVector = if (call.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Camera",
                        tint = TitaniumWhite
                    )
                }

                // Speaker Toggle
                IconButton(
                    onClick = onSpeakerToggle,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (call.isSpeakerOn) CyanSignal else SovereignSurfaceVariant)
                        .testTag("call_speaker_toggle")
                ) {
                    Icon(
                        imageVector = if (call.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Speaker",
                        tint = if (call.isSpeakerOn) SovereignVoid else TitaniumWhite
                    )
                }
            }
        }
    }
}
