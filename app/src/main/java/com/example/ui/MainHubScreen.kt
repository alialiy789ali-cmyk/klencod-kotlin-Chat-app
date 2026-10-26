package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SovereignHeader
import com.example.ui.screens.*
import com.example.ui.theme.*

@Composable
fun MainHubScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val latency by viewModel.nodeLatency.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val channels by viewModel.channels.collectAsState()

    val totalUnreadChats = remember(threads) { threads.sumOf { it.unreadCount } }

    val showNewChatDialog by viewModel.showNewChatDialog.collectAsState()
    val showNewChannelDialog by viewModel.showNewChannelDialog.collectAsState()
    val showNewPostDialog by viewModel.showNewPostDialog.collectAsState()
    val showNodeServerDialog by viewModel.showNodeServerDialog.collectAsState()
    val showDockerGuideDialog by viewModel.showDockerGuideDialog.collectAsState()

    // Enforce RTL or LTR dynamically based on user setting
    val layoutDirection = if (currentUser.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                SovereignHeader(
                    title = "السيادي Sovereign",
                    subtitle = if (currentUser.isRtl) "نموذج محلي للمراسلة والمجتمع" else "Local messaging prototype",
                    latencyMs = latency,
                    isConnected = currentUser.isConnected,
                    isRtl = currentUser.isRtl,
                    onToggleRtl = { viewModel.toggleRtl() },
                    onSwitchUser = { viewModel.switchActiveUserForTesting() },
                    currentUserHandle = currentUser.handle
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.testTag("hub_bottom_nav")
                ) {
                    // Tab 1: Chats
                    NavigationBarItem(
                        selected = currentTab == HubTab.CHATS,
                        onClick = { viewModel.selectTab(HubTab.CHATS) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (totalUnreadChats > 0) {
                                        Badge(containerColor = EmeraldPrimary) {
                                            Text("$totalUnreadChats", color = SovereignVoid, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentTab == HubTab.CHATS) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "Chats"
                                )
                            }
                        },
                        label = { Text("المراسلات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_chats")
                    )

                    // Tab 2: Channels
                    NavigationBarItem(
                        selected = currentTab == HubTab.CHANNELS,
                        onClick = { viewModel.selectTab(HubTab.CHANNELS) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == HubTab.CHANNELS) Icons.Default.Campaign else Icons.Outlined.Campaign,
                                contentDescription = "Channels"
                            )
                        },
                        label = { Text("القنوات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanSignal,
                            selectedTextColor = CyanSignal,
                            indicatorColor = CyanSignal.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_channels")
                    )

                    // Tab 3: Feed
                    NavigationBarItem(
                        selected = currentTab == HubTab.FEED,
                        onClick = { viewModel.selectTab(HubTab.FEED) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == HubTab.FEED) Icons.Default.DynamicFeed else Icons.Outlined.DynamicFeed,
                                contentDescription = "Feed"
                            )
                        },
                        label = { Text("الموجز", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_feed")
                    )

                    // Tab 4: Calls
                    NavigationBarItem(
                        selected = currentTab == HubTab.CALLS,
                        onClick = { viewModel.selectTab(HubTab.CALLS) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == HubTab.CALLS) Icons.Default.Call else Icons.Outlined.Call,
                                contentDescription = "Calls"
                            )
                        },
                        label = { Text("المكالمات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_calls")
                    )

                    // Tab 5: Vault
                    NavigationBarItem(
                        selected = currentTab == HubTab.VAULT,
                        onClick = { viewModel.selectTab(HubTab.VAULT) },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == HubTab.VAULT) Icons.Default.Shield else Icons.Outlined.Shield,
                                contentDescription = "Vault"
                            )
                        },
                        label = { Text("السيادة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_vault")
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    HubTab.CHATS -> LiveChatScreen()
                    HubTab.CHANNELS -> ChannelsScreen(viewModel = viewModel)
                    HubTab.FEED -> FeedScreen(viewModel = viewModel)
                    HubTab.CALLS -> CallsScreen(viewModel = viewModel)
                    HubTab.VAULT -> VaultSettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // ==========================================
    // Interactive Dialogs
    // ==========================================

    // New Chat Dialog
    if (showNewChatDialog) {
        var title by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var isGroup by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.setShowNewChat(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddComment, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isGroup) "إنشاء مجموعة محلية" else "بدء محادثة محلية جديدة")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("الاسم أو اسم المجموعة") },
                        modifier = Modifier.fillMaxWidth().testTag("new_chat_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("المعرف الفريد (e.g. @username)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_chat_handle_input"),
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isGroup,
                            onCheckedChange = { isGroup = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مجموعة متعددة الأطراف (Multi-Party Room)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createChat(title.trim(), handle.trim().ifEmpty { "@contact" }, isGroup)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("بدء المحادثة", color = SovereignVoid)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowNewChat(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // New Channel Dialog
    if (showNewChannelDialog) {
        var title by remember { mutableStateOf("") }
        var handle by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { viewModel.setShowNewChannel(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = CyanSignal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنشاء قناة بث سيادية")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم القناة") },
                        modifier = Modifier.fillMaxWidth().testTag("new_channel_title_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it },
                        label = { Text("معرف القناة العام (e.g. @channel_news)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_channel_handle_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("وصف القناة ومجالها") },
                        modifier = Modifier.fillMaxWidth().testTag("new_channel_desc_input"),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.createChannel(title.trim(), handle.trim().ifEmpty { "@channel" }, desc.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSignal)
                ) {
                    Text("إنشاء القناة", color = SovereignVoid)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowNewChannel(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // New Social Post Dialog
    if (showNewPostDialog) {
        var content by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("السيادة_الرقمية الخصوصية") }

        AlertDialog(
            onDismissRequest = { viewModel.setShowNewPost(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نشر في الموجز العام")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("ماذا يدور في فكرك الحر؟") },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("new_post_content_input"),
                        maxLines = 5
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("الوسوم (مفصولة بمسافات)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_post_tags_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            viewModel.publishSocialPost(content, tags)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("نشر المنشور", color = SovereignVoid)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowNewPost(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Change Node Server Dialog
    if (showNodeServerDialog) {
        var nodeUrl by remember { mutableStateOf(currentUser.serverNodeUrl) }

        AlertDialog(
            onDismissRequest = { viewModel.setShowNodeServer(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = CyanSignal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تغيير عنوان العقدة الخادمة")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "احفظ عنوان خادمك محليًا. الاتصال به غير مفعّل في هذه النسخة:",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )
                    OutlinedTextField(
                        value = nodeUrl,
                        onValueChange = { nodeUrl = it },
                        label = { Text("عنوان الخادم (إعداد محفوظ فقط)") },
                        modifier = Modifier.fillMaxWidth().testTag("node_url_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateServerNodeUrl(nodeUrl.trim())
                        viewModel.setShowNodeServer(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSignal)
                ) {
                    Text("حفظ محليًا", color = SovereignVoid)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowNodeServer(false) }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Self-hosting information: no runnable backend is included yet.
    if (showDockerGuideDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowDockerGuide(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الخادم الذاتي غير جاهز للتشغيل")
                }
            },
            text = {
                Text(
                    "أُضيفت الآن بوابة Node.js وREST/WebSocket وملفات Docker وTLS proxy. لكن تطبيق Android لم يُربط بها بعد، وتشفير الرسائل الطرفي ومكالمات WebRTC ما زالا غير جاهزين. راجع DEPLOYMENT.md لإعداد الخادم، ولا تستخدمه للإنتاج قبل مراجعة الأمن.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.setShowDockerGuide(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إغلاق", color = SovereignVoid)
                }
            }
        )
    }

}
