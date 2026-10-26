package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LocalAudioRecorder
import com.example.data.SovereignRepository
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HubTab {
    CHATS,
    CHANNELS,
    FEED,
    CALLS,
    VAULT
}

class SovereignViewModel(
    private val repository: SovereignRepository = SovereignRepository(),
    private val audioRecorder: LocalAudioRecorder? = null
) : ViewModel() {

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SovereignViewModel::class.java))
                val appContext = context.applicationContext
                val repository = SovereignRepository(context = appContext)
                return SovereignViewModel(repository, LocalAudioRecorder(appContext)) as T
            }
        }
    }

    val currentUser = repository.currentUser
    val threads = repository.threads
    val messages = repository.messages
    val channels = repository.channels
    val channelPosts = repository.channelPosts
    val feedPosts = repository.feedPosts
    val activeCall = repository.activeCall
    val nodeLatency = repository.nodeLatency
    val gatewaySession = repository.gatewaySession
    val gatewayAccountBusy = repository.gatewayAccountBusy
    val gatewayAccountMessage = repository.gatewayAccountMessage

    fun registerGatewayAccount(baseUrl: String, handle: String, displayName: String, password: String) =
        repository.registerGatewayAccount(baseUrl, handle, displayName, password)

    fun loginGatewayAccount(baseUrl: String, handle: String, password: String) =
        repository.loginGatewayAccount(baseUrl, handle, password)

    fun logoutGatewayAccount() = repository.logoutGatewayAccount()

    fun synchronizeGatewayMessages() = repository.synchronizeGatewayMessages()

    // Current Hub Tab
    private val _currentTab = MutableStateFlow(HubTab.CHATS)
    val currentTab: StateFlow<HubTab> = _currentTab.asStateFlow()

    // Navigation inside tabs
    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _activeChannelId = MutableStateFlow<String?>(null)
    val activeChannelId: StateFlow<String?> = _activeChannelId.asStateFlow()

    // Global Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Message Input
    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    // Voice Note Recording Simulation
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()
    private val _voiceRecordingDuration = MutableStateFlow(0)
    val voiceRecordingDuration: StateFlow<Int> = _voiceRecordingDuration.asStateFlow()
    private var voiceJob: Job? = null

    // Ephemeral self-destruct setting for active chat
    private val _ephemeralTimerSec = MutableStateFlow(0)
    val ephemeralTimerSec: StateFlow<Int> = _ephemeralTimerSec.asStateFlow()

    // Dialogs
    private val _showNewChatDialog = MutableStateFlow(false)
    val showNewChatDialog: StateFlow<Boolean> = _showNewChatDialog.asStateFlow()

    private val _showNewChannelDialog = MutableStateFlow(false)
    val showNewChannelDialog: StateFlow<Boolean> = _showNewChannelDialog.asStateFlow()

    private val _showNewPostDialog = MutableStateFlow(false)
    val showNewPostDialog: StateFlow<Boolean> = _showNewPostDialog.asStateFlow()

    private val _showNodeServerDialog = MutableStateFlow(false)
    val showNodeServerDialog: StateFlow<Boolean> = _showNodeServerDialog.asStateFlow()

    private val _showDockerGuideDialog = MutableStateFlow(false)
    val showDockerGuideDialog: StateFlow<Boolean> = _showDockerGuideDialog.asStateFlow()

    private val _securityAuditInfo = MutableStateFlow<String?>(null)
    val securityAuditInfo: StateFlow<String?> = _securityAuditInfo.asStateFlow()

    fun selectTab(tab: HubTab) {
        _currentTab.value = tab
    }

    fun openThread(threadId: String) {
        _activeThreadId.value = threadId
    }

    fun markThreadAsRead(threadId: String) = repository.markThreadAsRead(threadId)

    fun closeThread() {
        _activeThreadId.value = null
        _messageInput.value = ""
    }

    fun openChannel(channelId: String) {
        _activeChannelId.value = channelId
    }

    fun closeChannel() {
        _activeChannelId.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMessageInput(text: String) {
        _messageInput.value = text
    }

    fun setEphemeralTimer(seconds: Int) {
        _ephemeralTimerSec.value = seconds
    }

    fun sendCurrentMessage() {
        val threadId = _activeThreadId.value ?: return
        val text = _messageInput.value.trim()
        if (text.isEmpty()) return

        repository.sendMessage(
            threadId = threadId,
            content = text,
            selfDestructSec = _ephemeralTimerSec.value
        )
        _messageInput.value = ""
    }

    fun startVoiceRecording() {
        val recorder = audioRecorder ?: return
        if (!recorder.start()) return
        _isRecordingVoice.value = true
        _voiceRecordingDuration.value = 0
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            while (_isRecordingVoice.value) {
                delay(1000)
                _voiceRecordingDuration.update { it + 1 }
            }
        }
    }

    fun stopAndSendVoiceRecording() {
        val duration = _voiceRecordingDuration.value.coerceAtLeast(1)
        _isRecordingVoice.value = false
        voiceJob?.cancel()
        val mediaPath = audioRecorder?.stop()
        if (mediaPath == null) {
            _voiceRecordingDuration.value = 0
            return
        }
        val threadId = _activeThreadId.value
        if (threadId == null) {
            java.io.File(mediaPath).delete()
            _voiceRecordingDuration.value = 0
            return
        }
        repository.sendMessage(
            threadId = threadId,
            content = "رسالة صوتية محلية",
            mediaType = MediaType.AUDIO,
            mediaUrl = mediaPath,
            audioDurationSec = duration,
            selfDestructSec = _ephemeralTimerSec.value
        )
        _voiceRecordingDuration.value = 0
    }

    fun cancelVoiceRecording() {
        _isRecordingVoice.value = false
        voiceJob?.cancel()
        audioRecorder?.cancel()
        _voiceRecordingDuration.value = 0
    }

    override fun onCleared() {
        audioRecorder?.cancel()
        super.onCleared()
    }

    fun broadcastPost(channelId: String, text: String) {
        if (text.isNotBlank()) {
            repository.broadcastToChannel(channelId, text.trim())
        }
    }

    fun publishSocialPost(content: String, hashtagsText: String) {
        if (content.isNotBlank()) {
            val tags = hashtagsText.split(" ", "،", ",")
                .map { it.trim().removePrefix("#") }
                .filter { it.isNotEmpty() }
            repository.createSocialPost(content.trim(), tags)
            _showNewPostDialog.value = false
        }
    }

    fun toggleLikePost(postId: String) = repository.toggleLikePost(postId)
    fun toggleRepostPost(postId: String) = repository.toggleRepostPost(postId)
    fun addComment(postId: String, text: String) {
        if (text.isNotBlank()) {
            repository.addCommentToPost(postId, text.trim())
        }
    }

    fun startCall(peerHandle: String, peerName: String, callType: CallType) {
        repository.startCall(peerHandle, peerName, callType)
    }

    fun toggleCallMute() = repository.toggleCallMute()
    fun toggleCallCamera() = repository.toggleCallCamera()
    fun toggleCallSpeaker() = repository.toggleCallSpeaker()
    fun endCall() = repository.endCall()

    // Dialog controls
    fun setShowNewChat(show: Boolean) { _showNewChatDialog.value = show }
    fun setShowNewChannel(show: Boolean) { _showNewChannelDialog.value = show }
    fun setShowNewPost(show: Boolean) { _showNewPostDialog.value = show }
    fun setShowNodeServer(show: Boolean) { _showNodeServerDialog.value = show }
    fun setShowDockerGuide(show: Boolean) { _showDockerGuideDialog.value = show }
    fun showSecurityAudit(info: String?) { _securityAuditInfo.value = info }

    fun createChat(title: String, handle: String, isGroup: Boolean) {
        val thread = repository.createChatThread(title, handle, isGroup)
        _showNewChatDialog.value = false
        openThread(thread.id)
    }

    fun createChannel(title: String, handle: String, description: String) {
        val channel = repository.createChannel(title, handle, description)
        _showNewChannelDialog.value = false
        openChannel(channel.id)
    }

    fun switchActiveUserForTesting() = repository.switchActiveUserForTesting()
    fun updateServerNodeUrl(newUrl: String) = repository.updateServerNodeUrl(newUrl)
    fun toggleRtl() = repository.toggleRtl()
    fun toggleDarkMode() = repository.toggleDarkMode()
    fun regenerateKeys() = repository.regenerateIdentityKeys()
    fun purgeVault() = repository.purgeLocalVault()
    fun createEncryptedBackup(password: CharArray) = repository.createEncryptedBackup(password)
    fun restoreEncryptedBackup(bytes: ByteArray, password: CharArray) =
        repository.restoreEncryptedBackup(bytes, password)
    fun pingNode() = repository.pingNode()
}
