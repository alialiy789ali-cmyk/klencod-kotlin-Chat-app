package com.example.model

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class MediaType {
    NONE,
    AUDIO,
    IMAGE,
    FILE
}

data class MessageItem(
    val id: String,
    val threadId: String,
    val senderHandle: String,
    val recipientHandle: String,
    val content: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val isEncrypted: Boolean = false,
    val mediaType: MediaType = MediaType.NONE,
    val mediaUrl: String? = null,
    val audioDurationSec: Int = 0,
    val selfDestructSec: Int = 0,
    val isSelf: Boolean = false,
    val remoteId: String? = null
)

data class ChatThread(
    val id: String,
    val title: String,
    val handle: String,
    val isGroup: Boolean = false,
    val lastMessage: String = "",
    val lastTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val avatarColor: Long = 0xFF10B981,
    val isVerified: Boolean = false,
    val isEncrypted: Boolean = false,
    val membersCount: Int = 2,
    val isPinned: Boolean = false,
    val peerPublicKeyFingerprint: String = "SHA256:7F8B:3A91:E204:55BC:90A1"
)

data class ChannelItem(
    val id: String,
    val title: String,
    val handle: String,
    val description: String,
    val subscriberCount: Int,
    val avatarColor: Long = 0xFF38BDF8,
    val isPublic: Boolean = true,
    val isAdmin: Boolean = false,
    val isVerified: Boolean = false,
    val unreadCount: Int = 0
)

data class ChannelPost(
    val id: String,
    val channelId: String,
    val channelTitle: String,
    val channelHandle: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null,
    val viewsCount: Int = 120,
    val forwardsCount: Int = 14,
    val isPinned: Boolean = false
)

data class SocialPost(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val authorAvatarColor: Long = 0xFF10B981,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null,
    val likesCount: Int = 0,
    val repostsCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val isReposted: Boolean = false,
    val hashtags: List<String> = emptyList(),
    val replies: List<PostComment> = emptyList()
)

data class PostComment(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallState {
    RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class CallSession(
    val id: String,
    val peerHandle: String,
    val peerName: String,
    val callType: CallType = CallType.AUDIO,
    val state: CallState = CallState.RINGING,
    val durationSec: Int = 0,
    val isMuted: Boolean = false,
    val isCameraOff: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val securityFingerprint: String = "7492 8812 0943 5519"
)

data class UserProfile(
    val handle: String = "@sovereign_user",
    val displayName: String = "Sovereign Citizen",
    val bio: String = "مستقل رقمياً • Sovereign by Design • تخزين محلي مشفّر",
    val avatarColor: Long = 0xFF10B981,
    val publicKeyFingerprint: String = "4A7B 89E1 FC02 3381 990B 52AA",
    val serverNodeUrl: String = "https://node.sovereign.local:8443",
    val isConnected: Boolean = false,
    val isRtl: Boolean = true,
    val isDarkMode: Boolean = true
)
