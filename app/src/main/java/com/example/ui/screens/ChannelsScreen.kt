package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelItem
import com.example.model.ChannelPost
import com.example.ui.SovereignViewModel
import com.example.ui.components.SovereignAvatar
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChannelsScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val activeChannelId by viewModel.activeChannelId.collectAsState()
    val channels by viewModel.channels.collectAsState()

    if (activeChannelId != null) {
        val channel = channels.find { it.id == activeChannelId }
        if (channel != null) {
            ChannelDetailScreen(
                channel = channel,
                viewModel = viewModel,
                onBack = { viewModel.closeChannel() }
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SovereignBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyanSignal.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = CyanSignal,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "القنوات والمجتمعات السيادية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "قنوات محلية تجريبية؛ لا يوجد بثّ عبر الخادم حاليًا",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                        }
                    }
                }

                // Channels List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelItemRow(
                            channel = channel,
                            onClick = { viewModel.openChannel(channel.id) }
                        )
                        HorizontalDivider(color = SovereignBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }

            // Create Channel FAB
            FloatingActionButton(
                onClick = { viewModel.setShowNewChannel(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("create_channel_fab"),
                containerColor = CyanSignal,
                contentColor = SovereignVoid,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Channel")
            }
        }
    }
}

@Composable
fun ChannelItemRow(
    channel: ChannelItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("channel_item_${channel.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SovereignAvatar(
            name = channel.title,
            colorHex = channel.avatarColor,
            sizeDp = 48
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
                        text = channel.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = CyanSignal,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = "${channel.subscriberCount} مشترك",
                    fontSize = 11.sp,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = channel.description,
                fontSize = 12.sp,
                color = SlateMuted,
                maxLines = 2
            )
        }
    }
}

@Composable
fun ChannelDetailScreen(
    channel: ChannelItem,
    viewModel: SovereignViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val allChannelPosts by viewModel.channelPosts.collectAsState()
    val posts = remember(allChannelPosts, channel.id) {
        allChannelPosts[channel.id] ?: emptyList()
    }

    var broadcastText by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_channels_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SovereignAvatar(
                        name = channel.title,
                        colorHex = channel.avatarColor,
                        sizeDp = 42
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (channel.isVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = CyanSignal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = "${channel.handle} • ${channel.subscriberCount} مشترك",
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Admin Broadcast Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = broadcastText,
                        onValueChange = { broadcastText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("broadcast_post_input"),
                        placeholder = { Text("نشر بيان أو إعلان في القناة...", fontSize = 13.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanSignal,
                            unfocusedBorderColor = SovereignBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (broadcastText.isNotBlank()) {
                                viewModel.broadcastPost(channel.id, broadcastText)
                                broadcastText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyanSignal)
                            .testTag("send_broadcast_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Broadcast",
                            tint = SovereignVoid
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = AmberWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(
                        "محتوى القناة تجريبي ومحفوظ محليًا؛ النشر والاشتراك لا يتزامنان مع خادم حاليًا.",
                        modifier = Modifier.padding(10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // Channel Description Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, SovereignBorder)
                ) {
                    Text(
                        text = channel.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            items(posts, key = { it.id }) { post ->
                ChannelPostCard(post = post, timeFormatted = timeFormatter.format(Date(post.timestamp)))
            }
        }
    }
}

@Composable
fun ChannelPostCard(
    post: ChannelPost,
    timeFormatted: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SovereignBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (post.isPinned) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = AmberWarning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "منشور مثبت في القناة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                }
            }

            Text(
                text = post.text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Views",
                            tint = SlateMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${post.viewsCount}",
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Forwards",
                            tint = SlateMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${post.forwardsCount}",
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }
                }

                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = SlateMuted
                )
            }
        }
    }
}
