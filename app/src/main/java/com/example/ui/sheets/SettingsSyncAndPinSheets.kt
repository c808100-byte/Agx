package com.example.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPrimary
import com.example.util.Formatters
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private val PERMISSION_ITEMS = listOf(
    "items" to "الجرد (إضافة وتعديل وحذف)",
    "reports" to "التقارير",
    "debts" to "الديون والبيع الآجل",
    "expenses" to "تسجيل المصروفات",
    "profit" to "رؤية الأرباح",
    "cash" to "رؤية الصندوق"
)

@Composable
fun SettingsSheet(
    state: KashirakUiState,
    userEmail: String?,
    onClose: () -> Unit,
    onSaveShop: (String, String, String, String, Double) -> Unit,
    onChangeOwnerPin: (String) -> Boolean,
    onTogglePermission: (String, Boolean) -> Unit,
    onOpenSyncSheet: () -> Unit,
    onGenerateBackupJson: suspend () -> String,
    onImportBackupJson: (String) -> Unit,
    onWipeKeepCustomers: () -> Unit,
    onWipeCustomersOnly: () -> Unit,
    onResetAll: () -> Unit,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sName by remember(state.settings) { mutableStateOf(state.settings.shopName) }
    var sOwner by remember(state.settings) { mutableStateOf(state.settings.ownerName) }
    var sPhone by remember(state.settings) { mutableStateOf(state.settings.phone) }
    var sCur by remember(state.settings) { mutableStateOf(state.settings.currency) }
    var sCash0 by remember(state.settings) { mutableStateOf(state.settings.cash0.toString()) }
    var newPin by remember { mutableStateOf("") }

    var wipeAppArmed by remember { mutableStateOf(false) }
    var wipeCustArmed by remember { mutableStateOf(false) }
    var resetArmed by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = onGenerateBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    onToast("تم حفظ النسخة الاحتياطية")
                } catch (_: Exception) {
                    onToast("تعذر حفظ النسخة الاحتياطية")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }
                if (!text.isNullOrBlank()) {
                    pendingImportJson = text
                } else {
                    onToast("تعذر قراءة الملف")
                }
            } catch (_: Exception) {
                onToast("تعذر قراءة الملف")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = "إعدادات المحل",
            onClose = onClose,
            closeTestTag = "settings_close_button"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shop Info
            item {
                KashirakCard {
                    Text(text = "بيانات المحل", style = MaterialTheme.typography.titleMedium)
                    StyledTextField(sName, { sName = it }, "اسم المحل", testTag = "set_shop_name")
                    StyledTextField(sOwner, { sOwner = it }, "اسم المالك", testTag = "set_owner_name")
                    StyledTextField(
                        value = sPhone,
                        onValueChange = { sPhone = it },
                        placeholder = "رقم الهاتف",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StyledTextField(
                            value = sCur,
                            onValueChange = { if (it.length <= 5) sCur = it },
                            placeholder = "العملة",
                            modifier = Modifier.weight(1f)
                        )
                        StyledTextField(
                            value = sCash0,
                            onValueChange = { sCash0 = it },
                            placeholder = "رصيد بداية الصندوق",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    GoldButton(
                        text = "حفظ بيانات المحل",
                        testTag = "set_save_shop",
                        onClick = {
                            onSaveShop(sName, sOwner, sPhone, sCur, sCash0.toDoubleOrNull() ?: 0.0)
                        }
                    )
                }
            }

            // Owner PIN
            item {
                KashirakCard {
                    Text(text = "الرقم السري للمالك", style = MaterialTheme.typography.titleMedium)
                    StyledTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                        placeholder = "رقم سري جديد (4 أرقام)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        testTag = "set_owner_pin"
                    )
                    GoldButton(
                        text = "تغيير الرقم السري",
                        onClick = {
                            if (onChangeOwnerPin(newPin)) newPin = ""
                        }
                    )
                    Text(
                        text = "أرقام العمال السرية تضبطها من صفحة العمال.",
                        color = ext.mutedText,
                        fontSize = 12.sp
                    )
                }
            }

            // Cashier Permissions
            item {
                KashirakCard {
                    Text(text = "صلاحيات الكاشير", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "البيع متاح دائماً. فعّل ما يمكن للكاشير فتحه:",
                        color = ext.mutedText,
                        fontSize = 12.5.sp
                    )
                    PERMISSION_ITEMS.forEach { (key, label) ->
                        val checked = when (key) {
                            "items" -> state.settings.permItems
                            "reports" -> state.settings.permReports
                            "debts" -> state.settings.permDebts
                            "expenses" -> state.settings.permExpenses
                            "profit" -> state.settings.permProfit
                            "cash" -> state.settings.permCash
                            else -> false
                        }
                        HorizontalDivider(color = ext.dividerLine)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, modifier = Modifier.weight(1f))
                            Switch(
                                checked = checked,
                                onCheckedChange = { onTogglePermission(key, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ext.goldDarkText
                                )
                            )
                        }
                    }
                }
            }

            // Google Account & Cloud Sync
            item {
                KashirakCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "حساب جوجل والمزامنة", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (state.isOnline) ext.okBg else ext.warnBg
                        ) {
                            Text(
                                text = if (state.isOnline) "متصل" else "دون إنترنت",
                                color = if (state.isOnline) ext.okFg else ext.warnFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = userEmail ?: "حساب جوجل مرتبط",
                        color = ext.mutedText,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onOpenSyncSheet,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.chipBg,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("إدارة المزامنة وحساب جوجل", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // JSON Backup & Restore
            item {
                KashirakCard {
                    Text(text = "نسخة احتياطية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "النسخة تشمل الأصناف والمبيعات والزبائن والمصروفات والعمال والإعدادات.",
                        color = ext.mutedText,
                        fontSize = 12.5.sp
                    )
                    Button(
                        onClick = {
                            val name = "نسخة-كاشيرك-${Formatters.isoDay(System.currentTimeMillis())}.json"
                            exportLauncher.launch(name)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.heroBg, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("تصدير نسخة احتياطية", fontWeight = FontWeight.Bold)
                    }

                    if (pendingImportJson != null) {
                        OutlinedButton(
                            onClick = {
                                val json = pendingImportJson
                                pendingImportJson = null
                                if (json != null) onImportBackupJson(json)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ext.badFg),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "اضغط للتأكيد: استبدال كل البيانات الحالية بالنسخة",
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = { pendingImportJson = null },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.chipBg,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إلغاء الاستيراد", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ext.chipBg,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("استيراد نسخة احتياطية", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Data Wipe
            item {
                KashirakCard {
                    Text(text = "تفريغ البيانات", style = MaterialTheme.typography.titleMedium)

                    OutlinedButton(
                        onClick = {
                            if (!wipeAppArmed) {
                                wipeAppArmed = true
                                wipeCustArmed = false
                                resetArmed = false
                            } else {
                                onWipeKeepCustomers()
                                wipeAppArmed = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.badFg),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (wipeAppArmed) "اضغط مرة أخرى للتأكيد" else "تفريغ التطبيق بدون الزبائن",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "يمسح الأصناف والمبيعات والمصروفات والتسديدات ويُبقي الزبائن وديونهم الحالية والعمال والإعدادات.",
                        color = ext.mutedText,
                        fontSize = 12.sp
                    )

                    OutlinedButton(
                        onClick = {
                            if (!wipeCustArmed) {
                                wipeCustArmed = true
                                wipeAppArmed = false
                                resetArmed = false
                            } else {
                                onWipeCustomersOnly()
                                wipeCustArmed = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.badFg),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (wipeCustArmed) "اضغط مرة أخرى للتأكيد" else "تفريغ الزبائن",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "يحذف كل الزبائن فقط، وتبقى الفواتير القديمة كما هي.",
                        color = ext.mutedText,
                        fontSize = 12.sp
                    )

                    OutlinedButton(
                        onClick = {
                            if (!resetArmed) {
                                resetArmed = true
                                wipeAppArmed = false
                                wipeCustArmed = false
                            } else {
                                onResetAll()
                                resetArmed = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ext.badFg),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (resetArmed) "اضغط مرة أخرى للتأكيد" else "مسح كل البيانات",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncAccountSheet(
    state: KashirakUiState,
    userEmail: String?,
    userDisplayName: String?,
    onClose: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = "المزامنة وحساب جوجل",
            onClose = onClose,
            closeTestTag = "sync_sheet_close"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ext.heroBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "حساب جوجل المرتبط",
                        color = ext.heroFg,
                        fontSize = 13.sp
                    )
                    Text(
                        text = userDisplayName?.ifBlank { null } ?: "مستخدم جوجل",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حالة الاتصال", fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (state.isOnline) ext.okFg else ext.warnFg,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (state.isOnline) "متصل بالإنترنت" else "بدون إنترنت (محلي)",
                            color = if (state.isOnline) ext.okFg else ext.warnFg,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(color = ext.dividerLine)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تغييرات بانتظار المزامنة", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${state.pendingSyncCount}",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (state.pendingSyncCount > 0) ext.warnFg else ext.okFg
                    )
                }
                if (state.settings.lastSyncedAtMillis > 0L) {
                    HorizontalDivider(color = ext.dividerLine)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("آخر مزامنة", fontWeight = FontWeight.Bold)
                        Text(
                            text = Formatters.dateTimeShort(state.settings.lastSyncedAtMillis),
                            color = ext.mutedText
                        )
                    }
                }
            }

            Text(
                text = "تُحفظ جميع الفواتير والأصناف والديون والمصروفات في قاعدة البيانات المحلية (SQLite) لتعمل بدون إنترنت، وتتم مزامنتها تلقائياً مع حساب جوجل عند عودة الاتصال.",
                color = ext.mutedText,
                fontSize = 13.sp
            )

            GoldButton(
                text = if (state.isSyncing) "جاري المزامنة..." else "مزامنة الآن",
                enabled = !state.isSyncing,
                testTag = "sync_now_button",
                onClick = onSyncNow
            )

            if (state.isSyncing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("جاري مزامنة البيانات مع السحابة...", color = ext.mutedText)
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ext.badFg),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("تسجيل الخروج من حساب جوجل", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PinPadDialog(
    title: String,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onWrongPin: () -> Unit
) {
    val ext = LocalKashirakColors.current
    var buf by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ext.cardBg,
            modifier = Modifier.widthIn(max = 330.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)

                // 4 PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (i in 0 until 4) {
                        val filled = i < buf.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (filled) ext.goldDarkText else Color.Transparent)
                                .border(2.dp, ext.goldDarkText, CircleShape)
                        )
                    }
                }

                // 3x4 keypad (LTR layout for digits 1..9, 0)
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Keep digits ordered 1 2 3 even in RTL
                            row.reversed().forEach { digit ->
                                PinKeyButton(
                                    label = digit,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (buf.length < 4) {
                                            val next = buf + digit
                                            buf = next
                                            if (next.length == 4) {
                                                if (onVerifyPin(next)) {
                                                    onDismiss()
                                                } else {
                                                    buf = ""
                                                    onWrongPin()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { if (buf.isNotEmpty()) buf = buf.dropLast(1) },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.cd_backspace),
                                tint = ext.mutedText
                            )
                        }
                        PinKeyButton(
                            label = "0",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (buf.length < 4) {
                                    val next = buf + "0"
                                    buf = next
                                    if (next.length == 4) {
                                        if (onVerifyPin(next)) {
                                            onDismiss()
                                        } else {
                                            buf = ""
                                            onWrongPin()
                                        }
                                    }
                                }
                            }
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("إلغاء", color = ext.mutedText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val ext = LocalKashirakColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = ext.chipBg,
        modifier = modifier.height(50.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OwnerRoleDialog(
    onOpenSettings: () -> Unit,
    onSwitchToCashier: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalKashirakColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ext.cardBg,
            modifier = Modifier.widthIn(max = 330.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "المالك", style = MaterialTheme.typography.titleMedium)
                Text(text = "اختر ما تريد", color = ext.mutedText, fontSize = 13.sp)
                GoldButton(text = "إعدادات المحل", onClick = onOpenSettings)
                Button(
                    onClick = onSwitchToCashier,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.chipBg,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("التبديل إلى الكاشير", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = ext.mutedText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
