package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.SovereignViewModel
import com.example.ui.components.SovereignAvatar
import com.example.ui.components.SovereignSecurityBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedFilter by remember { mutableStateOf(0) } // 0: الكل, 1: مباشر, 2: مجموعات

    if (activeThreadId != null) {
        val thread = threads.find { it.id == activeThreadId }
        if (thread != null) {
            ConversationDetailScreen(
                thread = thread,
                viewModel = viewModel,
                onBack = { viewModel.closeThread() }
            )
        }
    } else {
        // Chat List Screen
        val filteredThreads = remember(threads, searchQuery, selectedFilter) {
            threads.filter { thread ->
                val matchesSearch = searchQuery.isBlank() ||
                        thread.title.contains(searchQuery, ignoreCase = true) ||
                        thread.handle.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    1 -> !thread.isGroup
                    2 -> thread.isGroup
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search & Filter Row
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_search_input"),
                        placeholder = { Text("بحث في المحادثات المحلية...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = SlateMuted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateMuted)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = SovereignBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == 0,
                            onClick = { selectedFilter = 0 },
                            label = { Text("جميع المحادثات (${threads.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == 1,
                            onClick = { selectedFilter = 1 },
                            label = { Text("محادثات فردية") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedFilter == 2,
                            onClick = { selectedFilter = 2 },
                            label = { Text("المجموعات") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }
                }

                if (filteredThreads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = SlateMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لا توجد محادثات تطابق بحثك",
                                color = SlateMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredThreads, key = { it.id }) { thread ->
                            ChatThreadRow(
                                thread = thread,
                                onClick = { viewModel.openThread(thread.id) }
                            )
                            HorizontalDivider(color = SovereignBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // FAB to Start New Encrypted Chat
            FloatingActionButton(
                onClick = { viewModel.setShowNewChat(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("new_chat_fab"),
                containerColor = EmeraldPrimary,
                contentColor = SovereignVoid,
                shape = CircleShape
            ) {
                Icon(Icons.Default.AddComment, contentDescription = "New Chat")
            }
        }
    }
}

@Composable
fun ChatThreadRow(
    thread: ChatThread,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_thread_${thread.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SovereignAvatar(
            name = thread.title,
            colorHex = thread.avatarColor,
            sizeDp = 50,
            showShield = thread.isEncrypted
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (thread.isGroup) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Group",
                            tint = CyanSignal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = timeFormatter.format(Date(thread.lastTimestamp)),
                    fontSize = 11.sp,
                    color = SlateMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thread.isEncrypted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = thread.lastMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (thread.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${thread.unreadCount}",
                            color = SovereignVoid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationDetailScreen(
    thread: ChatThread,
    viewModel: SovereignViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceRecording() }

    val allMessages by viewModel.messages.collectAsState()
    val threadMessages = remember(allMessages, thread.id) {
        allMessages[thread.id] ?: emptyList()
    }
    val messageInput by viewModel.messageInput.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val voiceDuration by viewModel.voiceRecordingDuration.collectAsState()
    val ephemeralTimer by viewModel.ephemeralTimerSec.collectAsState()
    val listState = rememberLazyListState()

    var showSecurityAudit by remember { mutableStateOf(false) }

    LaunchedEffect(threadMessages.size) {
        if (threadMessages.isNotEmpty()) {
            listState.animateScrollToItem(threadMessages.size - 1)
        }
    }
    LaunchedEffect(thread.id, threadMessages) {
        viewModel.markThreadAsRead(thread.id)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_chats_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SovereignAvatar(
                        name = thread.title,
                        colorHex = thread.avatarColor,
                        sizeDp = 40,
                        showShield = false
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showSecurityAudit = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = thread.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Local storage",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (thread.isGroup) "${thread.membersCount} أعضاء • بيانات محلية" else "${thread.handle} • محفوظ محليًا",
                            fontSize = 11.sp,
                            color = EmeraldPrimary
                        )
                    }

                    // Call Actions
                    IconButton(
                        onClick = { viewModel.startCall(thread.handle, thread.title, CallType.AUDIO) },
                        modifier = Modifier.testTag("call_audio_btn")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Audio Call", tint = EmeraldPrimary)
                    }

                    IconButton(
                        onClick = { viewModel.startCall(thread.handle, thread.title, CallType.VIDEO) },
                        modifier = Modifier.testTag("call_video_btn")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = CyanSignal)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    // Ephemeral Message indicator bar
                    if (ephemeralTimer > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AmberWarning.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "الرسائل ذاتية الاختفاء مفعلة: ${ephemeralTimer} ثانية",
                                    fontSize = 11.sp,
                                    color = AmberWarning,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "إلغاء",
                                fontSize = 11.sp,
                                color = RoseDestruct,
                                modifier = Modifier.clickable { viewModel.setEphemeralTimer(0) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ephemeral Timer Toggle Button
                        IconButton(
                            onClick = {
                                val next = when (ephemeralTimer) {
                                    0 -> 10
                                    10 -> 60
                                    60 -> 3600
                                    else -> 0
                                }
                                viewModel.setEphemeralTimer(next)
                            },
                            modifier = Modifier.testTag("ephemeral_timer_toggle")
                        ) {
                            Icon(
                                imageVector = if (ephemeralTimer > 0) Icons.Default.Timer else Icons.Outlined.Timer,
                                contentDescription = "Disappearing timer",
                                tint = if (ephemeralTimer > 0) AmberWarning else SlateMuted
                            )
                        }

                        if (isRecordingVoice) {
                            // Recording UI
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(RoseDestruct.copy(alpha = 0.15f))
                                    .border(1.dp, RoseDestruct.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(RoseDestruct)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "جاري التسجيل: 00:${voiceDuration.toString().padStart(2, '0')}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseDestruct
                                    )
                                }

                                Row {
                                    Text(
                                        text = "إلغاء",
                                        fontSize = 12.sp,
                                        color = SlateMuted,
                                        modifier = Modifier
                                            .clickable { viewModel.cancelVoiceRecording() }
                                            .padding(end = 10.dp)
                                    )
                                    Text(
                                        text = "إرسال ✓",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.clickable { viewModel.stopAndSendVoiceRecording() }
                                    )
                                }
                            }
                        } else {
                            // Text Input
                            OutlinedTextField(
                                value = messageInput,
                                onValueChange = { viewModel.setMessageInput(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input"),
                                placeholder = { Text("اكتب رسالة… تُشفّر عند اتصال الحساب، وإلا تبقى محلية", fontSize = 13.sp) },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldPrimary,
                                    unfocusedBorderColor = SovereignBorder,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            if (messageInput.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.sendCurrentMessage() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary)
                                        .testTag("send_message_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = SovereignVoid
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.startVoiceRecording()
                                        } else {
                                            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldDim.copy(alpha = 0.2f))
                                        .testTag("record_voice_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record Voice Note",
                                        tint = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Security verification banner
            SovereignSecurityBadge(
                fingerprint = thread.peerPublicKeyFingerprint,
                onAuditClick = { showSecurityAudit = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(threadMessages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }
    }

    // Security Verification Dialog
    if (showSecurityAudit) {
        AlertDialog(
            onDismissRequest = { showSecurityAudit = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("معلومات التخزين والحماية")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (thread.isEncrypted) "الرسائل التي تمت مزامنتها هنا أُرسلت داخل envelope مشفّر وموقّع بـ P-256. الرسائل القديمة أو المحلية قد لا تكون قد أُرسلت. تحقق الهوية خارج القناة غير متاح، وهذا ليس Signal ولا يوفر forward secrecy؛ التخزين المحلي محمي بمفتاح Android Keystore." else "هذه محادثة محلية أو تجريبية ولم يثبت إرسالها إلى الطرف الآخر. التخزين المحلي مشفّر على الجهاز؛ لا تعتبرها E2EE حتى يظهر نجاح الإرسال عبر حساب وخادم فعليين.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SovereignVoid,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = thread.peerPublicKeyFingerprint,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = EmeraldPrimary
                        )
                    }
                    Text(
                        text = "لا يمكن التحقق من الاتصال أو مقاومة هجمات الوسيط في النسخة الحالية.",
                        fontSize = 12.sp,
                        color = AmberWarning
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSecurityAudit = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إغلاق", color = SovereignVoid)
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: MessageItem,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var audioPlayer by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember(message.id) { mutableStateOf(false) }
    DisposableEffect(message.id) {
        onDispose { runCatching { audioPlayer?.release() } }
    }
    val isSelf = message.isSelf

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSelf) 16.dp else 2.dp,
                bottomEnd = if (isSelf) 2.dp else 16.dp
            ),
            color = if (isSelf) EmeraldDim else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                0.8.dp,
                if (isSelf) EmeraldPrimary.copy(alpha = 0.6f) else SovereignBorder
            ),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isSelf) {
                    Text(
                        text = message.senderHandle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanSignal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (message.mediaType == MediaType.AUDIO) {
                    // Voice note waveform simulation
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelf) TitaniumWhite else EmeraldPrimary,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable(enabled = message.mediaUrl != null) {
                                    runCatching {
                                        if (isPlayingAudio) {
                                            audioPlayer?.pause()
                                            isPlayingAudio = false
                                        } else {
                                            audioPlayer?.release()
                                            audioPlayer = MediaPlayer().apply {
                                                setDataSource(message.mediaUrl ?: return@clickable)
                                                setOnCompletionListener {
                                                    isPlayingAudio = false
                                                    audioPlayer?.release()
                                                    audioPlayer = null
                                                }
                                                prepare()
                                                start()
                                            }
                                            isPlayingAudio = true
                                        }
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "Pause voice message" else "Play voice message",
                                tint = SovereignVoid,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        // Waveform bars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barHeights = listOf(8, 14, 20, 10, 16, 22, 12, 18, 14, 10, 16, 8)
                            barHeights.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(h.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelf) TitaniumWhite.copy(alpha = 0.8f) else EmeraldPrimary)
                                )
                            }
                        }
                        Text(
                            text = "0:${message.audioDurationSec.toString().padStart(2, '0')}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelf) TitaniumWhite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        color = if (isSelf) TitaniumWhite else MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.selfDestructSec > 0) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Self destruct",
                            tint = AmberWarning,
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    Text(
                        text = timeFormatter.format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = if (isSelf) TitaniumWhite.copy(alpha = 0.7f) else SlateMuted
                    )

                    if (isSelf) {
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.READ -> Icons.Default.DoneAll
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.SENT -> if (message.isEncrypted) Icons.Default.Check else Icons.Default.CloudOff
                                MessageStatus.SENDING -> Icons.Default.Schedule
                                MessageStatus.FAILED -> Icons.Default.ErrorOutline
                            },
                            contentDescription = when {
                                message.status == MessageStatus.FAILED -> "فشل الإرسال"
                                message.status == MessageStatus.SENDING -> "جارٍ الإرسال"
                                message.status == MessageStatus.SENT && !message.isEncrypted -> "محفوظ محليًا، لم يُرسل إلى الخادم"
                                message.status == MessageStatus.READ -> "مقروء"
                                message.status == MessageStatus.DELIVERED -> "تم التسليم"
                                else -> "أُرسل مشفّرًا"
                            },
                            tint = when (message.status) {
                                MessageStatus.READ -> CyanSignal
                                MessageStatus.FAILED -> RoseDestruct
                                else -> TitaniumWhite.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
