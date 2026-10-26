package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserProfile
import com.example.ui.SovereignViewModel
import com.example.ui.components.SovereignAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultSettingsScreen(
    viewModel: SovereignViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val latency by viewModel.nodeLatency.collectAsState()
    val gatewaySession by viewModel.gatewaySession.collectAsState()
    val gatewayBusy by viewModel.gatewayAccountBusy.collectAsState()
    val gatewayMessage by viewModel.gatewayAccountMessage.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showPurgeConfirmation by remember { mutableStateOf(false) }
    var gatewayUrl by remember(currentUser.serverNodeUrl) { mutableStateOf(currentUser.serverNodeUrl) }
    var gatewayHandle by remember(currentUser.handle) { mutableStateOf(currentUser.handle.removePrefix("@")) }
    var gatewayDisplayName by remember(currentUser.displayName) { mutableStateOf(currentUser.displayName) }
    var gatewayPassword by remember { mutableStateOf("") }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val password = backupPassword.toCharArray()
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = viewModel.createEncryptedBackup(password)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("تعذر فتح ملف الوجهة")
                    }
                }
                password.fill('\u0000')
                backupStatus = if (result.isSuccess) "تم تصدير النسخة الاحتياطية المشفرة." else "فشل التصدير: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
                backupPassword = ""
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val password = backupPassword.toCharArray()
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("تعذر قراءة الملف")
                        require(bytes.size <= 64 * 1024 * 1024) { "حجم النسخة أكبر من الحد المسموح" }
                        viewModel.restoreEncryptedBackup(bytes, password)
                    }
                }
                password.fill('\u0000')
                backupStatus = when {
                    result.isFailure -> "فشلت الاستعادة. تحقق من الملف وكلمة المرور."
                    result.getOrDefault(false) -> "تمت استعادة البيانات المشفرة. لن تُستعاد الملفات الصوتية ضمن النسخة."
                    else -> "تعذر فك النسخة. كلمة المرور أو الملف غير صحيح."
                }
                backupPassword = ""
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Identity & Sovereignty Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SovereignAvatar(
                        name = currentUser.displayName,
                        colorHex = currentUser.avatarColor,
                        sizeDp = 54,
                        showShield = true
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = currentUser.handle,
                            fontSize = 13.sp,
                            color = CyanSignal,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = currentUser.bio,
                            fontSize = 11.sp,
                            color = SlateMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Public key fingerprint
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SovereignVoid,
                    border = BorderStroke(1.dp, SovereignBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "معرّف المستخدم المحلي (غير موثق تشفيريًا):",
                                fontSize = 10.sp,
                                color = SlateMuted
                            )
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUser.publicKeyFingerprint,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dual-User Quick Switch Button (اختبار تعدد المستخدمين)
                Button(
                    onClick = { viewModel.switchActiveUserForTesting() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("switch_user_account_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanSignal.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, CyanSignal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapCalls,
                        contentDescription = null,
                        tint = CyanSignal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentUser.handle == "@ali_sovereign") "التبديل إلى المستخدم B (@tariq_privacy)" else "التبديل إلى المستخدم A (@ali_sovereign)",
                        color = CyanSignal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Gateway account: credentials are used transiently; only keys and the encrypted session token are persisted.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("حساب بوابة الرسائل", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyanSignal)
                Text(
                    "أنشئ حسابًا أو سجّل الدخول إلى خادمك. مفاتيح E2EE الخاصة تبقى في الخزنة المشفّرة على الجهاز؛ كلمة المرور لا تُحفظ.",
                    fontSize = 12.sp, color = SlateMuted
                )
                OutlinedTextField(
                    value = gatewayUrl, onValueChange = { gatewayUrl = it },
                    label = { Text("عنوان الخادم HTTPS") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gatewayHandle, onValueChange = { gatewayHandle = it.removePrefix("@") },
                    label = { Text("اسم الحساب") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gatewayDisplayName, onValueChange = { gatewayDisplayName = it },
                    label = { Text("الاسم الظاهر (للتسجيل)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gatewayPassword, onValueChange = { gatewayPassword = it },
                    label = { Text("كلمة المرور — 10 أحرف على الأقل") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (gatewaySession != null) {
                    Text(
                        if (!gatewaySession?.accessToken.isNullOrBlank()) "الجلسة متصلة باسم ${gatewaySession?.handle}؛ الرمز محمي داخل التخزين المحلي." else "المفاتيح المحلية موجودة، يلزم تسجيل الدخول لمزامنة الرسائل.",
                        fontSize = 12.sp, color = if (!gatewaySession?.accessToken.isNullOrBlank()) EmeraldPrimary else CyanSignal
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !gatewayBusy && gatewayPassword.length >= 10,
                        onClick = {
                            viewModel.registerGatewayAccount(gatewayUrl, gatewayHandle, gatewayDisplayName, gatewayPassword)
                            gatewayPassword = ""
                        }, modifier = Modifier.weight(1f)
                    ) { Text("إنشاء حساب") }
                    OutlinedButton(
                        enabled = !gatewayBusy && gatewayPassword.length >= 10,
                        onClick = {
                            viewModel.loginGatewayAccount(gatewayUrl, gatewayHandle, gatewayPassword)
                            gatewayPassword = ""
                        }, modifier = Modifier.weight(1f)
                    ) { Text("تسجيل الدخول") }
                }
                if (!gatewaySession?.accessToken.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = viewModel::synchronizeGatewayMessages, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(6.dp)); Text("مزامنة الآن")
                        }
                        OutlinedButton(onClick = viewModel::logoutGatewayAccount, modifier = Modifier.weight(1f)) {
                            Text("تسجيل الخروج")
                        }
                    }
                }
                if (gatewayBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                gatewayMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    "حاليًا تدعم البوابة الرسائل النصية الفردية فقط. المجموعات والوسائط الصوتية/الملفات لا تُرفع، وميزة E2EE نموذج P-256 وليست بروتوكول Signal.",
                    fontSize = 11.sp, color = RoseDestruct
                )
            }
        }

        // Section: Self-Hosted Infrastructure
        Text(
            text = "البنية التحتية والاستضافة الذاتية (Self-Hosting)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SovereignBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "عنوان العقدة الخادمة (Sovereign Node URL)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentUser.serverNodeUrl,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.setShowNodeServer(true) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("change_node_url_btn")
                    ) {
                        Text("تغيير", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = (if (currentUser.isConnected) EmeraldPrimary else RoseDestruct).copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (currentUser.isConnected) EmeraldPrimary else RoseDestruct)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentUser.isConnected) "متصل بالعقدة (${latency}ms)" else "غير متصل بالعقدة • تحقق من العنوان والشبكة",
                            fontSize = 12.sp,
                            color = if (currentUser.isConnected) EmeraldPrimary else SlateMuted
                        )
                    }

                    TextButton(onClick = { viewModel.pingNode() }) {
                        Text("فحص الخادم", fontSize = 11.sp, color = CyanSignal)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = SovereignBorder)

                // Docker Guide Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setShowDockerGuide(true) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = CyanSignal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "دليل تشغيل خادم Docker Compose الذاتي",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SlateMuted
                    )
                }
            }
        }

        // Section: Cryptography & Vault Security
        Text(
            text = "الخزنة الرقمية وإدارة التشفير",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SovereignBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Regenerate Keys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.regenerateKeys() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "توليد معرّف محلي تجريبي جديد", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "لا ينشئ هذا الإجراء مفتاح تشفير أو بروتوكول Ratchet", fontSize = 11.sp, color = SlateMuted)
                        }
                    }
                }

                HorizontalDivider(color = SovereignBorder)

                // Export Backup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBackupDialog = true; backupStatus = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "النسخ الاحتياطي والاستعادة", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "تصدير واستعادة ملف محمي بكلمة مرور", fontSize = 11.sp, color = SlateMuted)
                        }
                    }
                }

                HorizontalDivider(color = SovereignBorder)

                // Purge Vault
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPurgeConfirmation = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = RoseDestruct,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "تطهير الذاكرة المحلية (Zero-Trace Wipe)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RoseDestruct)
                            Text(text = "حذف كافة الرسائل وسجلات الجلسة المحلية بأمان", fontSize = 11.sp, color = SlateMuted)
                        }
                    }
                }
            }
        }

        // Section: Preferences (Language & Theme)
        Text(
            text = "التخصيص واللغة",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, SovereignBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "اتجاه الكتابة واللغة (RTL / LTR)", fontSize = 13.sp)
                    Button(
                        onClick = { viewModel.toggleRtl() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = if (currentUser.isRtl) "العربية (RTL)" else "English (LTR)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = SovereignBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "الوضع المظلم السيادي (Sovereign OLED)", fontSize = 13.sp)
                    Switch(
                        checked = currentUser.isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false; backupPassword = "" },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخة احتياطية مشفّرة")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "اختر كلمة مرور قوية لا تقل عن 10 أحرف. ستحتاج إليها للاستعادة. ملفات الرسائل الصوتية لا تُضمّن في النسخة.",
                        fontSize = 12.sp,
                        color = SlateMuted
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("كلمة مرور النسخة") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    backupStatus?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("فشل") || it.startsWith("تعذر")) RoseDestruct else EmeraldPrimary) }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = backupPassword.length >= 10,
                        onClick = { exportBackupLauncher.launch("sovereign-backup.svb") }
                    ) { Text("تصدير") }
                    TextButton(
                        enabled = backupPassword.length >= 10,
                        onClick = { importBackupLauncher.launch(arrayOf("*/*")) }
                    ) { Text("استعادة") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false; backupPassword = "" }) { Text("إغلاق") }
            }
        )
    }

    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("مسح البيانات المحلية؟") },
            text = { Text("سيتم حذف المحادثات والمنشورات والقنوات المحفوظة على هذا الجهاز. لا يمكن التراجع عن ذلك.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purgeVault()
                    showPurgeConfirmation = false
                }) {
                    Text("مسح البيانات", color = RoseDestruct)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) { Text("إلغاء") }
            }
        )
    }
}
