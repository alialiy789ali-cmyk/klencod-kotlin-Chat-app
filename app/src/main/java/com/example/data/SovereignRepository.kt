package com.example.data

import android.content.Context
import com.example.crypto.CryptoEngine
import com.example.crypto.E2eeEngine
import okhttp3.WebSocket
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SovereignRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    context: Context? = null
) {

    private val appContext = context?.applicationContext
    private val encryptedStore = appContext?.let(::EncryptedStateStore)
    private val gatewayHealthClient = appContext?.let { GatewayHealthClient() }
    private val gatewayApi = SovereignGatewayApi()
    private val persistenceMutex = Mutex()
    private val gatewaySyncMutex = Mutex()
    private var eventsSocket: WebSocket? = null
    private val _gatewaySession = MutableStateFlow<GatewaySession?>(null)
    val gatewaySession: StateFlow<GatewaySession?> = _gatewaySession.asStateFlow()
    private val _gatewayAccountBusy = MutableStateFlow(false)
    val gatewayAccountBusy: StateFlow<Boolean> = _gatewayAccountBusy.asStateFlow()
    private val _gatewayAccountMessage = MutableStateFlow<String?>(null)
    val gatewayAccountMessage: StateFlow<String?> = _gatewayAccountMessage.asStateFlow()

    // Current User Profile
    private val _currentUser = MutableStateFlow(
        UserProfile(
            handle = "@ali_sovereign",
            displayName = "علي - مهندس النظم",
            bio = "مدافع عن الخصوصية والسيادة الرقمية • Self-Hosted Node Operator",
            avatarColor = 0xFF10B981,
            publicKeyFingerprint = "7F8B 3A91 E204 55BC 90A1 4C18",
            serverNodeUrl = "https://node.sovereign.local:8443",
            isConnected = false,
            isRtl = true,
            isDarkMode = true
        )
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    // Alternate user for dual-user testing (المستخدم B)
    private val secondaryUser = UserProfile(
        handle = "@tariq_privacy",
        displayName = "طارق - مستشار الأمن",
        bio = "باحث في الأنظمة الموزعة • ملف مستخدم تجريبي",
        avatarColor = 0xFF38BDF8,
        publicKeyFingerprint = "9B21 C410 882E 11FA 4402 7E10",
        serverNodeUrl = "https://node.sovereign.local:8443",
        isConnected = false,
        isRtl = true,
        isDarkMode = true
    )

    // Chat Threads
    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    // Messages mapped by threadId
    private val _messages = MutableStateFlow<Map<String, List<MessageItem>>>(emptyMap())
    val messages: StateFlow<Map<String, List<MessageItem>>> = _messages.asStateFlow()

    // Channels
    private val _channels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val channels: StateFlow<List<ChannelItem>> = _channels.asStateFlow()

    // Channel Posts mapped by channelId
    private val _channelPosts = MutableStateFlow<Map<String, List<ChannelPost>>>(emptyMap())
    val channelPosts: StateFlow<Map<String, List<ChannelPost>>> = _channelPosts.asStateFlow()

    // Social Feed Posts
    private val _feedPosts = MutableStateFlow<List<SocialPost>>(emptyList())
    val feedPosts: StateFlow<List<SocialPost>> = _feedPosts.asStateFlow()

    // Active Call Session
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    // Network connection latency status
    private val _nodeLatency = MutableStateFlow(24)
    val nodeLatency: StateFlow<Int> = _nodeLatency.asStateFlow()

    init {
        val saved = encryptedStore?.read()
        if (saved == null) {
            seedInitialData()
            persistState()
        } else {
            _currentUser.value = saved.currentUser
            _threads.value = saved.threads
            _messages.value = saved.messages
            _channels.value = saved.channels
            _channelPosts.value = saved.channelPosts
            _feedPosts.value = saved.feedPosts
            _gatewaySession.value = saved.gatewaySession
        }
        if (!gatewaySession.value?.accessToken.isNullOrBlank()) connectGatewayEvents()
        pingNode()
    }

    private fun currentSnapshot(): RepositorySnapshot = RepositorySnapshot(
        currentUser = _currentUser.value,
        threads = _threads.value,
        messages = _messages.value,
        channels = _channels.value,
        channelPosts = _channelPosts.value,
        feedPosts = _feedPosts.value,
        gatewaySession = _gatewaySession.value
    )

    private fun persistState() {
        val store = encryptedStore ?: return
        scope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                runCatching { store.write(currentSnapshot()) }
            }
        }
    }

    fun createEncryptedBackup(password: CharArray): ByteArray {
        val portableSnapshot = currentSnapshot().copy(
            messages = _messages.value.mapValues { (_, messages) ->
                messages.map { message -> if (message.mediaUrl == null) message else message.copy(mediaUrl = null) }
            },
            // Never export a bearer token. Private E2EE keys are included only inside the
            // password-encrypted backup so the account can be recovered on another device.
            gatewaySession = _gatewaySession.value?.copy(accessToken = "")
        )
        return EncryptedBackup.encrypt(portableSnapshot, password)
    }

    fun restoreEncryptedBackup(bytes: ByteArray, password: CharArray): Boolean {
        val snapshot = EncryptedBackup.decrypt(bytes, password) ?: return false
        appContext?.filesDir?.resolve("voice-notes")?.deleteRecursively()
        _currentUser.value = snapshot.currentUser
        _threads.value = snapshot.threads
        _messages.value = snapshot.messages
        _channels.value = snapshot.channels
        _channelPosts.value = snapshot.channelPosts
        _feedPosts.value = snapshot.feedPosts
        _gatewaySession.value = snapshot.gatewaySession?.copy(accessToken = "")
        disconnectGatewayEvents()
        persistState()
        _gatewayAccountMessage.value = if (_gatewaySession.value != null) "استُعيدت مفاتيح الحساب. سجّل الدخول مجددًا لمزامنة الرسائل." else null
        return true
    }

    private fun seedInitialData() {
        val thread1Id = "thread_tariq"
        val thread2Id = "thread_group_sovereign"
        val thread3Id = "thread_fatima"

        _threads.value = listOf(
            ChatThread(
                id = thread1Id,
                title = "طارق - مستشار الأمن",
                handle = "@tariq_privacy",
                isGroup = false,
                lastMessage = "هذه رسالة عينة محفوظة محليًا.",
                lastTimestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                unreadCount = 1,
                avatarColor = 0xFF38BDF8,
                isVerified = false,
                isEncrypted = false,
                peerPublicKeyFingerprint = "9B21 C410 882E 11FA 4402 7E10"
            ),
            ChatThread(
                id = thread2Id,
                title = "مجلس السيادة التقنية (مجموعة تجريبية)",
                handle = "@sovereign_core_team",
                isGroup = true,
                lastMessage = "تم تجهيز ملفات Docker Compose لتشغيل العقدة ذاتياً.",
                lastTimestamp = System.currentTimeMillis() - 1000 * 60 * 35,
                unreadCount = 3,
                avatarColor = 0xFF10B981,
                isVerified = false,
                isEncrypted = false,
                membersCount = 12
            ),
            ChatThread(
                id = thread3Id,
                title = "د. فاطمة الزهراء",
                handle = "@fatima_crypto",
                isGroup = false,
                lastMessage = "مرفق تقرير فحص بروتوكول المصافحة والتشفير.",
                lastTimestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                unreadCount = 0,
                avatarColor = 0xFFA855F7,
                isVerified = false,
                isEncrypted = false,
                peerPublicKeyFingerprint = "3D44 11FE 0988 A1C2 BB39 9901"
            )
        )

        _messages.value = mapOf(
            thread1Id to listOf(
                MessageItem(
                    id = "m1",
                    threadId = thread1Id,
                    senderHandle = "@tariq_privacy",
                    recipientHandle = "@ali_sovereign",
                    content = "مرحبًا علي، هذه محادثة عينة لعرض الواجهة.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    status = MessageStatus.READ,
                    isEncrypted = false,
                    isSelf = false
                ),
                MessageItem(
                    id = "m2",
                    threadId = thread1Id,
                    senderHandle = "@ali_sovereign",
                    recipientHandle = "@tariq_privacy",
                    content = "رسالة عينة محلية؛ لا تُرسل تلقائيًا عبر البوابة.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 10,
                    status = MessageStatus.READ,
                    isEncrypted = false,
                    isSelf = true
                ),
                MessageItem(
                    id = "m3",
                    threadId = thread1Id,
                    senderHandle = "@tariq_privacy",
                    recipientHandle = "@ali_sovereign",
                    content = "هذه رسالة عينة محفوظة محليًا.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                    status = MessageStatus.DELIVERED,
                    isEncrypted = false,
                    isSelf = false
                )
            ),
            thread2Id to listOf(
                MessageItem(
                    id = "mg1",
                    threadId = thread2Id,
                    senderHandle = "@omar_sys",
                    recipientHandle = "@sovereign_core_team",
                    content = "السلام عليكم جميعاً، نرحب بكم في غرفة العمليات السيادية.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 60,
                    status = MessageStatus.READ,
                    isEncrypted = false,
                    isSelf = false
                ),
                MessageItem(
                    id = "mg2",
                    threadId = thread2Id,
                    senderHandle = "@ali_sovereign",
                    recipientHandle = "@sovereign_core_team",
                    content = "تم تجهيز ملفات Docker Compose لتشغيل العقدة ذاتياً.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 35,
                    status = MessageStatus.READ,
                    isEncrypted = false,
                    isSelf = true
                )
            )
        )

        // Channels
        val c1Id = "channel_sovereign_broadcast"
        val c2Id = "channel_crypto_tech"
        val c3Id = "channel_digital_defense"

        _channels.value = listOf(
            ChannelItem(
                id = c1Id,
                title = "قناة إعلانات السيادة الرقمية",
                handle = "@sovereign_official",
                description = "القناة الرسمية لنشر التحديثات والأبحاث المتعلقة بالأنظمة المستقلة ومفاتيح التشفير.",
                subscriberCount = 14820,
                avatarColor = 0xFF10B981,
                isPublic = true,
                isAdmin = true,
                isVerified = false
            ),
            ChannelItem(
                id = c2Id,
                title = "تقنيات التشفير المتقدم (E2EE Lab)",
                handle = "@crypto_advances",
                description = "أوراق بحثية ومناقشات حول بروتوكولات Signal وMatrix والخصوصية الصفرية.",
                subscriberCount = 8940,
                avatarColor = 0xFF38BDF8,
                isPublic = true,
                isAdmin = false,
                isVerified = false
            ),
            ChannelItem(
                id = c3Id,
                title = "درع الأمن السيبراني والمراقبة",
                handle = "@cyber_shield",
                description = "رصد التسريبات وتحليل التهديدات وتأمين خوادم الاستضافة الذاتية.",
                subscriberCount = 21300,
                avatarColor = 0xFFF59E0B,
                isPublic = true,
                isAdmin = false,
                isVerified = false
            )
        )

        _channelPosts.value = mapOf(
            c1Id to listOf(
                ChannelPost(
                    id = "cp1",
                    channelId = c1Id,
                    channelTitle = "قناة إعلانات السيادة الرقمية",
                    channelHandle = "@sovereign_official",
                    text = "📢 بيان رسمي: تم إطلاق بنية الاستضافة الذاتية الكاملة لشبكة Sovereign. بياناتك لم تعد ملكاً لأي احتكار رقمي.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 180,
                    viewsCount = 3400,
                    forwardsCount = 412,
                    isPinned = true
                ),
                ChannelPost(
                    id = "cp2",
                    channelId = c1Id,
                    channelTitle = "قناة إعلانات السيادة الرقمية",
                    channelHandle = "@sovereign_official",
                    text = "🔒 نصيحة اليوم: قم دائماً بمطابقة بصمة الأمان (Safety Number) قبل إرسال ملفات حساسة عبر الاتصال المشفر طرفياً.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                    viewsCount = 1820,
                    forwardsCount = 195
                )
            ),
            c2Id to listOf(
                ChannelPost(
                    id = "cp3",
                    channelId = c2Id,
                    channelTitle = "تقنيات التشفير المتقدم (E2EE Lab)",
                    channelHandle = "@crypto_advances",
                    text = "تحليل بروتوكول تبادل المفاتيح Double Ratchet: لماذا يعد الأفضل لحماية الرسائل حتى في حال اختراق مفتاح جلسة سابقة.",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 300,
                    viewsCount = 950,
                    forwardsCount = 88
                )
            )
        )

        // Social Posts (Timeline)
        _feedPosts.value = listOf(
            SocialPost(
                id = "p1",
                authorHandle = "@ali_sovereign",
                authorName = "علي - مهندس النظم",
                authorAvatarColor = 0xFF10B981,
                content = "السيادة الرقمية ليست ترفاً أو خياراً ثانوياً؛ بل هي حق أصيل لكل أمة ومجتمع لحماية هويته وبياناته من التجسس والتحكم المركزي. مرحباً بكم في عصر التواصل الحر! 🌐🛡️",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 20,
                likesCount = 42,
                repostsCount = 15,
                commentsCount = 3,
                isLiked = true,
                hashtags = listOf("السيادة_الرقمية", "الخصوصية_أولاً", "SelfHosted"),
                replies = listOf(
                    PostComment(
                        id = "c1",
                        authorHandle = "@tariq_privacy",
                        authorName = "طارق - مستشار الأمن",
                        text = "خطوة جبارة في الاتجاه الصحيح، الخصوصية الافتراضية أصبحت حقيقة.",
                        timestamp = System.currentTimeMillis() - 1000 * 60 * 12
                    )
                )
            ),
            SocialPost(
                id = "p2",
                authorHandle = "@fatima_crypto",
                authorName = "د. فاطمة الزهراء",
                authorAvatarColor = 0xFFA855F7,
                content = "أكملنا اليوم فحص التشفير الطرفي للرسائل والمكالمات. المفاتيح تولد على جهازك الخاص ولا تغادره أبداً. لا خادم وسيط يستطيع فك الشفرة. 🔐✨",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 95,
                likesCount = 89,
                repostsCount = 34,
                commentsCount = 5,
                isLiked = false,
                hashtags = listOf("E2EE", "الأمن_السيبراني", "Cryptography")
            ),
            SocialPost(
                id = "p3",
                authorHandle = "@open_source_arabia",
                authorName = "مجتمع المصدر المفتوح",
                authorAvatarColor = 0xFF38BDF8,
                content = "أحدث إصدار يدعم الاستضافة الذاتية عبر Docker وPostgreSQL وWebRTC جاهز للتحميل والتشغيل. ابنِ عقدتك الخاصة اليوم! 💻🚀",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 240,
                likesCount = 132,
                repostsCount = 57,
                commentsCount = 11,
                isLiked = false,
                hashtags = listOf("OpenSource", "Docker", "DevOps")
            )
        )
    }

    fun registerGatewayAccount(baseUrl: String, handle: String, displayName: String, password: String) {
        if (_gatewayAccountBusy.value) return
        scope.launch {
            _gatewayAccountBusy.value = true
            _gatewayAccountMessage.value = "جارٍ إنشاء مفاتيح الحساب والتسجيل…"
            try {
                val url = baseUrl.trim().trimEnd('/')
                require(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://10.0.2.2")) {
                    "استخدم HTTPS، أو localhost للمحاكاة المحلية فقط."
                }
                val normalized = normalizeGatewayHandle(handle)
                val storedAccount = _gatewaySession.value
                require(storedAccount == null || normalizeGatewayHandle(storedAccount.handle) == normalized) {
                    "تطبيق جهاز واحد يدعم حساب بوابة واحدًا حاليًا؛ صدّر نسخة مشفّرة قبل إزالة بيانات الحساب الحالي."
                }
                // Reuse a locally saved keypair after a lost registration response. Never
                // silently replace keys for an account that may already have server messages.
                val existingKeys = _gatewaySession.value?.takeIf { normalizeGatewayHandle(it.handle) == normalized }
                val generatedSigning = if (existingKeys == null) withContext(Dispatchers.IO) { E2eeEngine.generateKeyMaterial() } else null
                val generatedEncryption = if (existingKeys == null) withContext(Dispatchers.IO) { E2eeEngine.generateKeyMaterial() } else null
                val draft = GatewaySession(
                    accessToken = existingKeys?.accessToken.orEmpty(),
                    handle = "@$normalized", displayName = existingKeys?.displayName ?: displayName.trim(),
                    signingPublicKeyPem = existingKeys?.signingPublicKeyPem ?: generatedSigning!!.publicKeyPem,
                    signingPrivateKeyPkcs8Base64 = existingKeys?.signingPrivateKeyPkcs8Base64 ?: generatedSigning!!.privateKeyPkcs8Base64,
                    encryptionPublicKeyPem = existingKeys?.encryptionPublicKeyPem ?: generatedEncryption!!.publicKeyPem,
                    encryptionPrivateKeyPkcs8Base64 = existingKeys?.encryptionPrivateKeyPkcs8Base64 ?: generatedEncryption!!.privateKeyPkcs8Base64
                )
                _gatewaySession.value = draft
                _currentUser.update { it.copy(serverNodeUrl = url) }
                persistStateNow()
                val result = try {
                    gatewayApi.register(url, normalized, draft.displayName, password, draft.signingPublicKeyPem, draft.encryptionPublicKeyPem)
                } catch (error: GatewayApiException) {
                    if (error.statusCode == 409 && existingKeys == null) {
                        _gatewaySession.value = storedAccount
                        persistStateNow()
                    }
                    throw error
                }
                _gatewaySession.value = draft.copy(accessToken = result.token, handle = result.handle, displayName = result.displayName)
                _currentUser.update { it.copy(handle = result.handle, displayName = result.displayName, serverNodeUrl = url) }
                persistStateNow()
                connectGatewayEvents()
                _gatewayAccountMessage.value = "تم إنشاء الحساب. مفتاحا التشفير والتوقيع محفوظان داخل الخزنة المحلية المشفّرة."
                syncGatewayMessages()
            } catch (error: Exception) {
                _gatewayAccountMessage.value = "تعذر التسجيل: ${error.message ?: "خطأ اتصال"}"
            } finally { _gatewayAccountBusy.value = false }
        }
    }

    fun loginGatewayAccount(baseUrl: String, handle: String, password: String) {
        if (_gatewayAccountBusy.value) return
        scope.launch {
            _gatewayAccountBusy.value = true
            _gatewayAccountMessage.value = "جارٍ تسجيل الدخول…"
            try {
                val url = baseUrl.trim().trimEnd('/')
                require(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://10.0.2.2")) {
                    "استخدم HTTPS، أو localhost للمحاكاة المحلية فقط."
                }
                val normalized = normalizeGatewayHandle(handle)
                val local = _gatewaySession.value?.takeIf { normalizeGatewayHandle(it.handle) == normalized }
                    ?: throw IllegalStateException("لا توجد مفاتيح خاصة محلية لهذا الحساب؛ استعد النسخة الاحتياطية المشفّرة أولًا.")
                val result = gatewayApi.login(url, normalized, password)
                require(pemPublicKeyEquals(local.signingPublicKeyPem, result.identityPublicKey) &&
                    pemPublicKeyEquals(local.encryptionPublicKeyPem, result.encryptionPublicKey)) {
                    "مفاتيح الخادم لا تطابق المفاتيح المحلية. أوقفنا الدخول لحماية الرسائل."
                }
                _gatewaySession.value = local.copy(accessToken = result.token, handle = result.handle, displayName = result.displayName)
                _currentUser.update { it.copy(handle = result.handle, displayName = result.displayName, serverNodeUrl = url) }
                persistStateNow()
                connectGatewayEvents()
                _gatewayAccountMessage.value = "تم تسجيل الدخول؛ جارٍ مزامنة الرسائل المشفّرة."
                syncGatewayMessages()
            } catch (error: Exception) {
                _gatewayAccountMessage.value = "تعذر تسجيل الدخول: ${error.message ?: "خطأ اتصال"}"
            } finally { _gatewayAccountBusy.value = false }
        }
    }

    fun logoutGatewayAccount() {
        disconnectGatewayEvents()
        _gatewaySession.update { it?.copy(accessToken = "") }
        _gatewayAccountMessage.value = "تم تسجيل الخروج. بقيت المفاتيح الخاصة محليًا؛ لن تتم المزامنة حتى تسجيل الدخول."
        persistState()
    }

    fun synchronizeGatewayMessages() {
        val session = _gatewaySession.value?.takeIf { it.accessToken.isNotBlank() } ?: return
        scope.launch { syncGatewayMessages(session) }
    }

    private suspend fun sendThroughGateway(session: GatewaySession, thread: ChatThread, message: MessageItem) {
        try {
            require(message.mediaType == MediaType.NONE) { "إرسال الوسائط غير مدعوم بعد؛ لم تُرفع أي بيانات صوتية أو ملفات." }
            val baseUrl = _currentUser.value.serverNodeUrl
            val recipient = normalizeGatewayHandle(thread.handle)
            val peerKeys = gatewayApi.getPeerKeys(baseUrl, session.accessToken, recipient)
            val envelope = withContext(Dispatchers.IO) {
                E2eeEngine.encrypt(
                    plaintext = message.content,
                    senderHandle = session.handle,
                    recipientHandle = "@$recipient",
                    recipientEncryptionPublicKeyPem = peerKeys.encryptionPublicKey,
                    senderSigningPrivateKeyPkcs8Base64 = session.signingPrivateKeyPkcs8Base64,
                    messageId = message.id,
                    sentAt = message.timestamp
                )
            }
            val remoteId = gatewayApi.sendEncryptedMessage(baseUrl, session.accessToken, "@$recipient", envelope, message.selfDestructSec)
            updateMessage(message.threadId, message.id) { it.copy(status = MessageStatus.SENT, isEncrypted = true, remoteId = remoteId) }
            _threads.update { threads -> threads.map { if (it.id == thread.id) it.copy(isEncrypted = true) else it } }
            persistState()
        } catch (error: Exception) {
            updateMessage(message.threadId, message.id) { it.copy(status = MessageStatus.FAILED, isEncrypted = false) }
            _gatewayAccountMessage.value = "تعذر إرسال الرسالة إلى الخادم: ${error.message ?: "خطأ اتصال"}"
            persistState()
        }
    }

    private suspend fun syncGatewayMessages(session: GatewaySession? = _gatewaySession.value) {
        val activeSession = session ?: return
        if (activeSession.accessToken.isBlank()) return
        gatewaySyncMutex.withLock {
            try {
                val records = gatewayApi.listMessages(_currentUser.value.serverNodeUrl, activeSession.accessToken)
                val keyCache = mutableMapOf<String, GatewayPeerKeys>()
                for (record in records) {
                    val isOutgoing = normalizeGatewayHandle(record.senderHandle) == normalizeGatewayHandle(activeSession.handle)
                    val clientId = record.clientMessageId.ifBlank { record.envelope.messageId }
                    if (isOutgoing) {
                        val existing = _messages.value.entries.firstNotNullOfOrNull { (threadId, items) ->
                            items.firstOrNull { it.id == clientId }?.let { threadId to it }
                        }
                        if (existing != null) {
                            val status = when {
                                record.readAt != null -> MessageStatus.READ
                                record.deliveredAt != null -> MessageStatus.DELIVERED
                                else -> MessageStatus.SENT
                            }
                            updateMessage(existing.first, clientId) { it.copy(status = status, isEncrypted = true, remoteId = record.serverId) }
                        }
                        continue
                    }
                    if (_messages.value.values.any { items -> items.any { it.id == clientId } }) continue
                    val sender = normalizeGatewayHandle(record.senderHandle)
                    val peerKeys = keyCache.getOrPut(sender) {
                        gatewayApi.getPeerKeys(_currentUser.value.serverNodeUrl, activeSession.accessToken, sender)
                    }
                    val plaintext = withContext(Dispatchers.IO) {
                        E2eeEngine.decrypt(
                            envelope = record.envelope,
                            senderHandle = "@$sender",
                            recipientHandle = activeSession.handle,
                            recipientEncryptionPrivateKeyPkcs8Base64 = activeSession.encryptionPrivateKeyPkcs8Base64,
                            senderSigningPublicKeyPem = peerKeys.identityPublicKey
                        )
                    }
                    val thread = _threads.value.firstOrNull { normalizeGatewayHandle(it.handle) == sender && !it.isGroup }
                        ?: ChatThread(
                            id = "gateway_$sender", title = "@$sender", handle = "@$sender",
                            lastMessage = plaintext, lastTimestamp = record.envelope.sentAt,
                            unreadCount = 0, avatarColor = 0xFF38BDF8, isEncrypted = true
                        )
                    val message = MessageItem(
                        id = clientId, threadId = thread.id,
                        senderHandle = "@$sender", recipientHandle = activeSession.handle,
                        content = plaintext, timestamp = record.envelope.sentAt,
                        status = if (record.readAt != null) MessageStatus.READ else MessageStatus.DELIVERED,
                        isEncrypted = true, isSelf = false, remoteId = record.serverId
                    )
                    _messages.update { it + (thread.id to (it[thread.id].orEmpty() + message)) }
                    _threads.update { list ->
                        val existing = list.firstOrNull { it.id == thread.id }
                        if (existing == null) listOf(thread.copy(lastMessage = plaintext, lastTimestamp = message.timestamp, unreadCount = 1)) + list
                        else list.map { current -> if (current.id == thread.id) current.copy(lastMessage = plaintext, lastTimestamp = message.timestamp, unreadCount = current.unreadCount + 1, isEncrypted = true) else current }
                    }
                }
                persistState()
                _gatewayAccountMessage.value = "تمت مزامنة ${records.size} رسالة مشفّرة من البوابة."
            } catch (error: Exception) {
                _gatewayAccountMessage.value = "تعذرت المزامنة: ${error.message ?: "خطأ اتصال"}"
            }
        }
    }

    private fun connectGatewayEvents() {
        val session = _gatewaySession.value?.takeIf { it.accessToken.isNotBlank() } ?: return
        disconnectGatewayEvents()
        eventsSocket = runCatching {
            gatewayApi.openEvents(_currentUser.value.serverNodeUrl, session.accessToken) { event ->
                if (event.startsWith("message.")) scope.launch { syncGatewayMessages(session) }
                else if (event == "connection.error") _gatewayAccountMessage.value = "انقطع WebSocket أو انتهت الجلسة؛ سجّل الدخول مجددًا أو استخدم المزامنة اليدوية."
            }
        }.getOrElse {
            _gatewayAccountMessage.value = "تعذر فتح قناة WebSocket؛ ستظل المزامنة اليدوية متاحة."
            null
        }
    }

    private fun disconnectGatewayEvents() {
        gatewayApi.closeEvents(eventsSocket)
        eventsSocket = null
    }

    private fun updateMessage(threadId: String, messageId: String, transform: (MessageItem) -> MessageItem) {
        _messages.update { map ->
            val current = map[threadId] ?: return@update map
            map + (threadId to current.map { if (it.id == messageId) transform(it) else it })
        }
    }

    private suspend fun persistStateNow() {
        val store = encryptedStore ?: return
        withContext(Dispatchers.IO) { persistenceMutex.withLock { store.write(currentSnapshot()) } }
    }

    private fun normalizeGatewayHandle(value: String): String = value.trim().removePrefix("@").lowercase().also {
        require(it.matches(Regex("[a-z0-9_]{3,32}"))) { "اسم الحساب يجب أن يتكون من 3 إلى 32 حرفًا لاتينيًا أو رقمًا أو _" }
    }

    private fun pemPublicKeyEquals(first: String, second: String): Boolean =
        first.filter { it.isLetterOrDigit() } == second.filter { it.isLetterOrDigit() }

    // ==========================================
    // Messaging API & Realtime WebSocket Events
    // ==========================================

    fun sendMessage(
        threadId: String,
        content: String,
        mediaType: MediaType = MediaType.NONE,
        mediaUrl: String? = null,
        audioDurationSec: Int = 0,
        selfDestructSec: Int = 0
    ) {
        val user = _currentUser.value
        val thread = _threads.value.find { it.id == threadId } ?: return

        val activeGatewaySession = _gatewaySession.value?.takeIf {
            it.accessToken.isNotBlank() && normalizeGatewayHandle(it.handle) == normalizeGatewayHandle(user.handle)
        }
        val newMsg = MessageItem(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            senderHandle = user.handle,
            recipientHandle = thread.handle,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = if (activeGatewaySession != null) MessageStatus.SENDING else MessageStatus.SENT,
            isEncrypted = false,
            mediaType = mediaType,
            mediaUrl = mediaUrl,
            audioDurationSec = audioDurationSec,
            selfDestructSec = selfDestructSec,
            isSelf = true
        )

        // Add to messages map
        _messages.update { currentMap ->
            val list = (currentMap[threadId] ?: emptyList()) + newMsg
            currentMap + (threadId to list)
        }

        // Update thread last message
        _threads.update { list ->
            list.map {
                if (it.id == threadId) {
                    it.copy(
                        lastMessage = if (mediaType == MediaType.AUDIO) "🎙️ رسالة صوتية (${audioDurationSec}s)" else content,
                        lastTimestamp = newMsg.timestamp
                    )
                } else it
            }
        }
        persistState()
        if (activeGatewaySession != null && !thread.isGroup) {
            scope.launch { sendThroughGateway(activeGatewaySession, thread, newMsg) }
        } else if (activeGatewaySession != null && thread.isGroup) {
            updateMessage(newMsg.threadId, newMsg.id) { it.copy(status = MessageStatus.FAILED) }
            _gatewayAccountMessage.value = "إرسال المجموعات غير مدعوم من بوابة الرسائل الحالية."
        }

        // Disappearing messages are removed from this device after the selected interval.
        if (selfDestructSec > 0) {
            scope.launch {
                delay(selfDestructSec * 1000L)
                _messages.update { currentMap ->
                    val remaining = (currentMap[threadId] ?: emptyList()).filterNot { it.id == newMsg.id }
                    currentMap + (threadId to remaining)
                }
                newMsg.mediaUrl?.let { File(it).delete() }
                _threads.update { list ->
                    list.map { current ->
                        if (current.id != threadId || current.lastMessage != content) current
                        else current.copy(lastMessage = _messages.value[threadId]?.lastOrNull()?.content.orEmpty())
                    }
                }
                persistState()
            }
        }
    }

    fun markThreadAsRead(threadId: String) {
        _threads.update { list ->
            list.map {
                if (it.id == threadId) it.copy(unreadCount = 0) else it
            }
        }
        val session = _gatewaySession.value?.takeIf { it.accessToken.isNotBlank() }
        if (session != null) {
            _messages.value[threadId].orEmpty().filter { !it.isSelf && it.status != MessageStatus.READ && it.remoteId != null }.forEach { message ->
                scope.launch {
                    runCatching { gatewayApi.markRead(_currentUser.value.serverNodeUrl, session.accessToken, message.remoteId!!) }
                        .onSuccess { updateMessage(threadId, message.id) { it.copy(status = MessageStatus.READ) } }
                }
            }
        }
        persistState()
    }

    fun createChatThread(title: String, handle: String, isGroup: Boolean): ChatThread {
        val newThread = ChatThread(
            id = "thread_${UUID.randomUUID().toString().take(8)}",
            title = title,
            handle = if (handle.startsWith("@")) handle else "@$handle",
            isGroup = isGroup,
            lastMessage = "تم إنشاء محادثة محلية.",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            avatarColor = if (isGroup) 0xFF10B981 else 0xFF38BDF8,
            isVerified = false,
            isEncrypted = false,
            peerPublicKeyFingerprint = CryptoEngine.generateSafetyNumber(_currentUser.value.handle, handle)
        )

        _threads.update { listOf(newThread) + it }
        persistState()
        return newThread
    }

    // ==========================================
    // Channel & Broadcast APIs
    // ==========================================

    fun broadcastToChannel(channelId: String, text: String) {
        val channel = _channels.value.find { it.id == channelId } ?: return
        val newPost = ChannelPost(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            channelTitle = channel.title,
            channelHandle = channel.handle,
            text = text,
            timestamp = System.currentTimeMillis(),
            viewsCount = 1,
            forwardsCount = 0
        )

        _channelPosts.update { map ->
            val list = listOf(newPost) + (map[channelId] ?: emptyList())
            map + (channelId to list)
        }
        persistState()
    }

    fun createChannel(title: String, handle: String, description: String): ChannelItem {
        val newChannel = ChannelItem(
            id = "channel_${UUID.randomUUID().toString().take(8)}",
            title = title,
            handle = if (handle.startsWith("@")) handle else "@$handle",
            description = description,
            subscriberCount = 1,
            avatarColor = 0xFF10B981,
            isPublic = true,
            isAdmin = true,
            isVerified = false
        )

        _channels.update { listOf(newChannel) + it }
        persistState()
        return newChannel
    }

    // ==========================================
    // Social Network Feed APIs
    // ==========================================

    fun createSocialPost(content: String, hashtags: List<String>) {
        val user = _currentUser.value
        val newPost = SocialPost(
            id = UUID.randomUUID().toString(),
            authorHandle = user.handle,
            authorName = user.displayName,
            authorAvatarColor = user.avatarColor,
            content = content,
            timestamp = System.currentTimeMillis(),
            likesCount = 0,
            repostsCount = 0,
            commentsCount = 0,
            isLiked = false,
            isReposted = false,
            hashtags = hashtags
        )

        _feedPosts.update { listOf(newPost) + it }
        persistState()
    }

    fun toggleLikePost(postId: String) {
        _feedPosts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    val newLiked = !post.isLiked
                    post.copy(
                        isLiked = newLiked,
                        likesCount = if (newLiked) post.likesCount + 1 else (post.likesCount - 1).coerceAtLeast(0)
                    )
                } else post
            }
        }
        persistState()
    }

    fun toggleRepostPost(postId: String) {
        _feedPosts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    val newReposted = !post.isReposted
                    post.copy(
                        isReposted = newReposted,
                        repostsCount = if (newReposted) post.repostsCount + 1 else (post.repostsCount - 1).coerceAtLeast(0)
                    )
                } else post
            }
        }
        persistState()
    }

    fun addCommentToPost(postId: String, commentText: String) {
        val user = _currentUser.value
        val comment = PostComment(
            id = UUID.randomUUID().toString(),
            authorHandle = user.handle,
            authorName = user.displayName,
            text = commentText,
            timestamp = System.currentTimeMillis()
        )

        _feedPosts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    post.copy(
                        commentsCount = post.commentsCount + 1,
                        replies = post.replies + comment
                    )
                } else post
            }
        }
        persistState()
    }

    // ==========================================
    // Calls & WebRTC Signaling
    // ==========================================

    fun startCall(peerHandle: String, peerName: String, callType: CallType) {
        val session = CallSession(
            id = UUID.randomUUID().toString(),
            peerHandle = peerHandle,
            peerName = peerName,
            callType = callType,
            state = CallState.RINGING,
            durationSec = 0,
            securityFingerprint = CryptoEngine.generateSafetyNumber(_currentUser.value.handle, peerHandle)
        )
        _activeCall.value = session

        // Simulate peer answering after 2 seconds
        scope.launch {
            delay(2000)
            if (_activeCall.value?.id == session.id) {
                _activeCall.update { it?.copy(state = CallState.CONNECTED) }
                // Call timer
                while (_activeCall.value?.state == CallState.CONNECTED) {
                    delay(1000)
                    _activeCall.update { it?.copy(durationSec = (it.durationSec) + 1) }
                }
            }
        }
    }

    fun toggleCallMute() {
        _activeCall.update { it?.copy(isMuted = !(it.isMuted)) }
    }

    fun toggleCallCamera() {
        _activeCall.update { it?.copy(isCameraOff = !(it.isCameraOff)) }
    }

    fun toggleCallSpeaker() {
        _activeCall.update { it?.copy(isSpeakerOn = !(it.isSpeakerOn)) }
    }

    fun endCall() {
        _activeCall.update { it?.copy(state = CallState.ENDED) }
        scope.launch {
            delay(500)
            _activeCall.value = null
        }
    }

    // ==========================================
    // Sovereignty, Dual-User Switch & Node Settings
    // ==========================================

    fun switchActiveUserForTesting() {
        if (_gatewaySession.value != null) {
            _gatewayAccountMessage.value = "تبديل المستخدمين التجريبيين متاح قبل إنشاء حساب البوابة فقط؛ التطبيق يدير حساب بوابة واحدًا حاليًا."
            return
        }
        val current = _currentUser.value
        if (current.handle == "@ali_sovereign") {
            _currentUser.value = secondaryUser
        } else {
            _currentUser.value = UserProfile(
                handle = "@ali_sovereign",
                displayName = "علي - مهندس النظم",
                bio = "مدافع عن الخصوصية والسيادة الرقمية • Self-Hosted Node Operator",
                avatarColor = 0xFF10B981,
                publicKeyFingerprint = "7F8B 3A91 E204 55BC 90A1 4C18",
                serverNodeUrl = "https://node.sovereign.local:8443",
                isConnected = false,
                isRtl = current.isRtl,
                isDarkMode = current.isDarkMode
            )
        }
        persistState()
    }

    fun updateServerNodeUrl(newUrl: String) {
        _currentUser.update { it.copy(serverNodeUrl = newUrl.trim().trimEnd('/')) }
        persistState()
        pingNode()
        if (!_gatewaySession.value?.accessToken.isNullOrBlank()) {
            connectGatewayEvents()
            synchronizeGatewayMessages()
        }
    }

    fun toggleRtl() {
        _currentUser.update { it.copy(isRtl = !it.isRtl) }
        persistState()
    }

    fun toggleDarkMode() {
        _currentUser.update { it.copy(isDarkMode = !it.isDarkMode) }
        persistState()
    }

    fun pingNode() {
        val client = gatewayHealthClient
        if (client == null) {
            _currentUser.update { it.copy(isConnected = false) }
            _nodeLatency.value = 0
            return
        }
        val endpoint = _currentUser.value.serverNodeUrl
        scope.launch(Dispatchers.IO) {
            val latency = client.probe(endpoint)
            _nodeLatency.value = latency?.toInt() ?: 0
            _currentUser.update { it.copy(isConnected = latency != null) }
            persistState()
        }
    }

    fun regenerateIdentityKeys() {
        val newFingerprint = CryptoEngine.generateKeyFingerprint(_currentUser.value.handle)
        _currentUser.update { it.copy(publicKeyFingerprint = newFingerprint) }
        persistState()
    }

    fun purgeLocalVault() {
        encryptedStore?.clear()
        appContext?.filesDir?.resolve("voice-notes")?.deleteRecursively()
        _messages.value = emptyMap()
        _threads.value = emptyList()
        _feedPosts.value = emptyList()
        _channels.value = emptyList()
        _channelPosts.value = emptyMap()
        persistState()
    }
}
