package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class WireMessage(val id: String, val from: String, val text: String, val time: Long)
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private suspend fun callServer(base: String, endpoint: String, method: String = "GET", token: String = "", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
    val cleanBase = base.trim().trimEnd('/')
    require(cleanBase.startsWith("http://") || cleanBase.startsWith("https://")) { "أدخل رابط سيرفر صحيحًا يبدأ بـ http:// أو https://" }
    val builder = Request.Builder().url(cleanBase + endpoint)
    if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
    if (method == "POST") builder.post((body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
    val response = httpClient.newCall(builder.build()).execute()
    response.use {
        val data = it.body?.string().orEmpty()
        val json = try { JSONObject(data) } catch (_: Exception) { JSONObject() }
        if (!it.isSuccessful) throw IllegalStateException(json.optString("error", "HTTP ${it.code}"))
        json
    }
}

/** Temporary real-network text messaging UI; replaces mocked Chats tab in debug testing. */
@Composable
fun LiveChatScreen() {
    var serverUrl by rememberSaveable { mutableStateOf("http://127.0.0.1:8080") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recipient by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // Intentionally not persisted on disk.
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(emptyList<WireMessage>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, recipient, serverUrl) {
        if (token.isNotBlank() && recipient.isNotBlank()) {
            while (isActive) {
                try {
                    val peer = recipient.trim().lowercase().removePrefix("@")
                    val response = callServer(serverUrl, "/messages?with=$peer", token = token)
                    val arr = response.getJSONArray("messages")
                    messages = (0 until arr.length()).map { index ->
                        val m = arr.getJSONObject(index)
                        WireMessage(m.getString("id"), m.getString("from"), m.getString("text"), m.optLong("time"))
                    }
                    error = ""
                } catch (e: Exception) {
                    error = e.message ?: "تعذر الاتصال بالسيرفر"
                }
                delay(2500)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("المراسلة الحقيقية - نسخة اختبار", style = MaterialTheme.typography.titleLarge)
        if (token.isBlank()) {
            Text("أنشئ حسابًا أو سجّل الدخول. يستخدم الهاتفان عنوان السيرفر نفسه.")
            OutlinedTextField(serverUrl, {serverUrl = it}, modifier = Modifier.fillMaxWidth(), singleLine = true, label = {Text("عنوان السيرفر")})
            OutlinedTextField(username, {username = it.lowercase().trim().removePrefix("@")}, modifier = Modifier.fillMaxWidth(), singleLine = true, label = {Text("اسم المستخدم (حروف إنجليزية صغيرة)")})
            OutlinedTextField(password, {password = it}, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), label = {Text("كلمة المرور (8 أحرف أو أكثر)")})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val body = JSONObject().put("username", username).put("password", password)
                            val response = callServer(serverUrl, "/register", "POST", body = body)
                            token = response.getString("token")
                            error = ""
                            password = ""
                        } catch (e: Exception) {error = e.message ?: "فشل إنشاء الحساب"}
                        busy = false
                    }
                }) {Text("إنشاء حساب")}
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val body = JSONObject().put("username", username).put("password", password)
                            val response = callServer(serverUrl, "/login", "POST", body = body)
                            token = response.getString("token")
                            error = ""
                            password = ""
                        } catch (e: Exception) {error = e.message ?: "فشل تسجيل الدخول"}
                        busy = false
                    }
                }) {Text("دخول")}
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حساب: $username", modifier = Modifier.weight(1f))
                TextButton(onClick = {token = ""; messages = emptyList(); error = ""}) {Text("خروج")}
            }
            OutlinedTextField(recipient, {recipient = it.lowercase().trim().removePrefix("@")}, modifier = Modifier.fillMaxWidth(), singleLine = true, label = {Text("اسم المستلِم (مثال: tariq)")})
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(messages, key = {it.id}) { message ->
                    val mine = message.from == username
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if(mine) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(12.dp), color = if(mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp).widthIn(max = 270.dp)) {
                                Text(if(mine) "أنت" else message.from, style = MaterialTheme.typography.labelSmall)
                                Text(message.text)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(draft, {draft = it}, modifier = Modifier.weight(1f), label = {Text("اكتب رسالة")}, maxLines = 3)
                Button(enabled = !busy && draft.isNotBlank() && recipient.isNotBlank(), onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val peer = recipient.trim().lowercase().removePrefix("@")
                            val body = JSONObject().put("to", peer).put("text", draft)
                            callServer(serverUrl, "/messages", "POST", token, body)
                            draft = ""
                            val result = callServer(serverUrl, "/messages?with=$peer", token = token)
                            val arr = result.getJSONArray("messages")
                            messages = (0 until arr.length()).map { i ->
                                val m = arr.getJSONObject(i)
                                WireMessage(m.getString("id"),m.getString("from"),m.getString("text"),m.optLong("time"))
                            }
                            error = ""
                        } catch (e: Exception) { error = e.message ?: "فشل الإرسال" }
                        busy = false
                    }
                }) {Text("إرسال")}
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text("للتجربة على Wi-Fi موثوقة فقط. هذه الرسائل ليست مشفرة طرفيًا.", style = MaterialTheme.typography.labelSmall)
    }
}
