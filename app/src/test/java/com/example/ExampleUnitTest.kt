package com.example

import com.example.crypto.CryptoEngine
import com.example.data.EncryptedBackup
import com.example.data.RepositorySnapshot
import com.example.data.SovereignRepository
import com.example.model.CallType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    @Test
    fun testCryptoEngine_Sha256AndSafetyNumbers() {
        val hash = CryptoEngine.sha256("sovereign_identity".toByteArray())
        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 produces 64 hex characters

        val safetyNumber = CryptoEngine.generateSafetyNumber("@alice", "@bob")
        assertNotNull(safetyNumber)
        // Format is "XXXX XXXX XXXX XXXX" (19 chars with spaces)
        assertEquals(19, safetyNumber.length)

        // Safety number must be symmetric regardless of parameter order
        val safetyNumberReversed = CryptoEngine.generateSafetyNumber("@bob", "@alice")
        assertEquals(safetyNumber, safetyNumberReversed)
    }

    @Test
    fun testRepository_DualUserSwitching() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val repository = SovereignRepository(testScope)

        val userA = repository.currentUser.value
        assertEquals("@ali_sovereign", userA.handle)

        // Switch to User B
        repository.switchActiveUserForTesting()
        val userB = repository.currentUser.value
        assertEquals("@tariq_privacy", userB.handle)

        // Switch back to User A
        repository.switchActiveUserForTesting()
        val userAReturned = repository.currentUser.value
        assertEquals("@ali_sovereign", userAReturned.handle)
    }

    @Test
    fun testRepository_MessagingAndThreadCreation() = runTest {
        val repository = SovereignRepository(this)

        val newThread = repository.createChatThread(
            title = "غرفة العمليات",
            handle = "@ops_room",
            isGroup = true
        )
        assertNotNull(newThread.id)
        assertTrue(newThread.isGroup)

        val initialThreads = repository.threads.value
        assertTrue(initialThreads.any { it.id == newThread.id })

        // Send message
        repository.sendMessage(
            threadId = newThread.id,
            content = "اختبار رسالة مشفرة"
        )

        val messages = repository.messages.value[newThread.id]
        assertNotNull(messages)
        assertEquals(1, messages?.size)
        assertEquals("اختبار رسالة مشفرة", messages?.first()?.content)
    }

    @Test
    fun testRepository_SocialPostAndInteractions() = runTest {
        val repository = SovereignRepository(this)

        val initialPostCount = repository.feedPosts.value.size
        repository.createSocialPost(
            content = "منشور سيادي جديد للحرية الرقمية",
            hashtags = listOf("حرية", "سيادة")
        )

        val currentPosts = repository.feedPosts.value
        assertEquals(initialPostCount + 1, currentPosts.size)

        val newPost = currentPosts.first()
        assertEquals("منشور سيادي جديد للحرية الرقمية", newPost.content)
        assertFalse(newPost.isLiked)

        // Like Post
        repository.toggleLikePost(newPost.id)
        val likedPost = repository.feedPosts.value.first()
        assertTrue(likedPost.isLiked)
        assertEquals(1, likedPost.likesCount)

        // Add Comment
        repository.addCommentToPost(newPost.id, "تعليق داعم ومؤكد")
        val commentedPost = repository.feedPosts.value.first()
        assertEquals(1, commentedPost.commentsCount)
        assertEquals("تعليق داعم ومؤكد", commentedPost.replies.last().text)
    }

    @Test
    fun testRepository_Calls() = runTest {
        val repository = SovereignRepository(this)

        assertNull(repository.activeCall.value)

        repository.startCall(
            peerHandle = "@tariq_privacy",
            peerName = "طارق",
            callType = CallType.AUDIO
        )

        val call = repository.activeCall.value
        assertNotNull(call)
        assertEquals("@tariq_privacy", call?.peerHandle)
        assertEquals(CallType.AUDIO, call?.callType)

        repository.toggleCallMute()
        assertTrue(repository.activeCall.value?.isMuted == true)

        repository.endCall()
    }

    @Test
    fun encryptedBackupRoundTripsAndRejectsWrongPassword() {
        val snapshot = RepositorySnapshot(
            currentUser = com.example.model.UserProfile(handle = "@backup_user"),
            threads = emptyList(),
            messages = emptyMap(),
            channels = emptyList(),
            channelPosts = emptyMap(),
            feedPosts = emptyList()
        )
        val password = "correct horse battery".toCharArray()
        val encrypted = EncryptedBackup.encrypt(snapshot, password)

        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("@backup_user"))
        assertEquals(snapshot, EncryptedBackup.decrypt(encrypted, "correct horse battery".toCharArray()))
        assertNull(EncryptedBackup.decrypt(encrypted, "wrong password".toCharArray()))
    }
}
