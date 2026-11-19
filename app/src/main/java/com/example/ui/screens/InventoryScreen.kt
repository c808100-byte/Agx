package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ProductEntity
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.components.rememberBarcodeScanRequester
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPrimary
import com.example.util.Formatters
import com.example.util.StockAlertNotifier
import java.util.Calendar

@Composable
fun InventoryScreen(
    state: KashirakUiState,
    onAddProduct: (String, Int, Double, Double, String, String, Int) -> Unit,
    onUpdateProduct: (String, String, Int, Double, Double, String, String, Int) -> Unit,
    onAddStock: (String, Int) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onOpenInventoryReport: () -> Unit,
    onUpdateAllMinStock: (Int) -> Unit = {},
    onToast: (String) -> Unit
) {
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency
    val context = LocalContext.current

    var expandedId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var scanTarget by remember { mutableStateOf("search") }

    var hasNotifPermission by remember {
        mutableStateOf(StockAlertNotifier.hasNotificationPermission(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            StockAlertNotifier.ensureChannel(context)
            onToast("تم تفعيل إشعارات تنبيه المخزون")
        } else {
            onToast("ستظهر التنبيهات داخل التطبيق فقط")
        }
    }

    var showBulkMinStock by remember { mutableStateOf(false) }
    var bulkMinStockText by remember { mutableStateOf("5") }

    // Add product form state
    var newName by remember { mutableStateOf("") }
    var newStock by remember { mutableStateOf("") }
    var newCost by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var newBarcode by remember { mutableStateOf("") }
    var newMinStock by remember { mutableStateOf("5") }
    var newExpiry by remember { mutableStateOf("") }

    val requestBarcodeScan = rememberBarcodeScanRequester(
        onToast = onToast
    ) { code ->
        if (scanTarget == "add") {
            newBarcode = code
            onToast("تم التقاط الباركود: $code")
        } else {
            searchQuery = code
            val exact = state.products.firstOrNull { it.barcode.equals(code, ignoreCase = true) }
            if (exact != null) {
                expandedId = exact.id
                editingId = null
                onToast("تم الوصول للمنتج: ${exact.name}")
            } else {
                onToast("لا يوجد منتج بهذا الباركود")
            }
        }
    }

    val trimmedQuery = searchQuery.trim()
    val filteredProducts = remember(state.products, trimmedQuery) {
        if (trimmedQuery.isBlank()) state.products
        else state.products.filter {
            it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.barcode.contains(trimmedQuery, ignoreCase = true)
        }
    }

    val totalPieces = state.products.sumOf { it.stock }
    val totalCostValue = state.products.sumOf { it.cost * it.stock }
    val totalSellValue = state.products.sumOf { it.price * it.stock }
    val soldTodayTotal = state.products.sumOf { state.soldTodayFor(it.name) }
    val lowStock = state.lowStockProducts()
    val expiring = state.expiringProducts()
    val attentionCount = lowStock.size + expiring.size

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "الجرد", style = MaterialTheme.typography.titleLarge)
        }

        // Top live search & barcode scan bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "ابحث بالاسم أو امسح الباركود",
                            color = ext.mutedText,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ext.mutedText
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("inv_search_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = ext.mutedText
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = ext.dividerLine,
                        focusedContainerColor = ext.cardBg,
                        unfocusedContainerColor = ext.cardBg
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inv_search_input")
                )

                Surface(
                    onClick = {
                        scanTarget = "search"
                        requestBarcodeScan()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = NavyPrimary,
                    border = BorderStroke(1.dp, GoldAccent),
                    modifier = Modifier
                        .size(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("inv_scan_search_barcode")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.cd_scan_barcode),
                        tint = GoldAccent,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Live search results directly under the top search bar when searching
        if (trimmedQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نتائج البحث السريع (${filteredProducts.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Surface(
                        onClick = { searchQuery = "" },
                        shape = RoundedCornerShape(999.dp),
                        color = ext.chipBg,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "عرض الكل",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                item {
                    KashirakCard {
                        Text(
                            text = "لا توجد منتجات مطابقة لـ \"$trimmedQuery\"",
                            color = ext.mutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                val looksLikeBarcode = trimmedQuery.all { it.isDigit() || it == '-' || it == '_' }
                                if (looksLikeBarcode) {
                                    newBarcode = trimmedQuery
                                } else {
                                    newName = trimmedQuery
                                }
                                searchQuery = ""
                                onToast("تمت تعبئة الحقل في نموذج الإضافة بالأسفل")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GoldAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("inv_quick_add_from_search")
                        ) {
                            Text(
                                text = "إضافة منتج جديد بهذا البحث",
                                fontWeight = FontWeight.Bold,
                                color = ext.goldDarkText
                            )
                        }
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { product ->
                    ProductExpandableRow(
                        product = product,
                        currency = cur,
                        soldToday = state.soldTodayFor(product.name),
                        isExpanded = expandedId == product.id,
                        isEditing = editingId == product.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == product.id) null else product.id
                            editingId = null
                        },
                        onStartEdit = { editingId = product.id },
                        onCancelEdit = { editingId = null },
                        onSaveEdit = { n, st, c, p, bc, exp, minSt ->
                            onUpdateProduct(product.id, n, st, c, p, bc, exp, minSt)
                            editingId = null
                        },
                        onAddStock = { qty -> onAddStock(product.id, qty) },
                        onDelete = {
                            onDeleteProduct(product.id)
                            if (expandedId == product.id) expandedId = null
                        }
                    )
                }
            }
        }

        // Summary 2x2 tiles
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallSummaryTile("إجمالي التكلفة", "${Formatters.money(totalCostValue)} $cur", Modifier.weight(1f))
                    SmallSummaryTile("إجمالي القطع", totalPieces.toString(), Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallSummaryTile("قيمة البيع المتوقعة", "${Formatters.money(totalSellValue)} $cur", Modifier.weight(1f))
                    SmallSummaryTile("مباع اليوم (قطع)", soldTodayTotal.toString(), Modifier.weight(1f))
                }
                Text(
                    text = "كل عملية بيع تُخصم من الجرد تلقائياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText
                )
            }
        }

        // Attention card
        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "بحاجة لانتباهك", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showBulkMinStock = !showBulkMinStock },
                            shape = RoundedCornerShape(999.dp),
                            color = ext.chipBg,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("inv_bulk_min_stock_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = ext.goldDarkText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "حد التنبيه",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (attentionCount > 0) ext.warnBg else ext.okBg
                        ) {
                            Text(
                                text = if (attentionCount > 0) attentionCount.toString() else "لا شيء",
                                color = if (attentionCount > 0) ext.warnFg else ext.okFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (showBulkMinStock) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledTextField(
                            value = bulkMinStockText,
                            onValueChange = { if (it.all(Char::isDigit)) bulkMinStockText = it },
                            placeholder = "الحد الأدنى للكل",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            testTag = "inv_bulk_min_stock_input"
                        )
                        Button(
                            onClick = {
                                val minVal = bulkMinStockText.toIntOrNull() ?: 5
                                onUpdateAllMinStock(minVal)
                                showBulkMinStock = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NavyPrimary,
                                contentColor = GoldAccent
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("inv_bulk_min_stock_save")
                        ) {
                            Text("تطبيق على الكل", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                    OutlinedButton(
                        onClick = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inv_enable_notifications_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = ext.goldDarkText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "تفعيل إشعارات الهاتف عند انخفاض المخزون",
                            color = ext.goldDarkText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                if (attentionCount == 0) {
                    Text(
                        text = "المخزون بخير",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    expiring.forEach { p ->
                        val left = Formatters.expiryDaysLeft(p.expiryDate) ?: 0
                        HorizontalDivider(color = ext.dividerLine)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${p.name} · ${p.stock} قطعة", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${Formatters.expiryText(left)} · ${p.expiryDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.mutedText
                                )
                            }
                            Text(
                                text = if (left < 0) "منتهي" else "انتباه",
                                color = ext.badFg,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    lowStock.forEach { p ->
                        HorizontalDivider(color = ext.dividerLine)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    searchQuery = p.name
                                    expandedId = p.id
                                    editingId = null
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = p.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (p.stock <= 0) "نفد المخزون (الحد الأدنى ${p.minStock})"
                                    else "المتبقي ${p.stock} من الحد الأدنى ${p.minStock}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.stock <= 0) ext.badFg else ext.warnFg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ext.chipBg
                            ) {
                                Text(
                                    text = "+ تزويد",
                                    color = NavyPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add product form
        item {
            KashirakCard {
                Text(text = "إدخال صنف للجرد", style = MaterialTheme.typography.titleMedium)
                StyledTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "اسم الصنف",
                    testTag = "inv_add_name"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StyledTextField(
                        value = newStock,
                        onValueChange = { newStock = it },
                        placeholder = "العدد",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        testTag = "inv_add_stock"
                    )
                    StyledTextField(
                        value = newCost,
                        onValueChange = { newCost = it },
                        placeholder = "سعر التكلفة",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "inv_add_cost"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledTextField(
                        value = newPrice,
                        onValueChange = { newPrice = it },
                        placeholder = "سعر البيع",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "inv_add_price"
                    )
                    StyledTextField(
                        value = newBarcode,
                        onValueChange = { newBarcode = it },
                        placeholder = "الباركود",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        testTag = "inv_add_barcode"
                    )
                    Surface(
                        onClick = {
                            scanTarget = "add"
                            requestBarcodeScan()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = ext.chipBg,
                        border = BorderStroke(1.dp, ext.dividerLine),
                        modifier = Modifier
                            .size(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("inv_scan_add_barcode")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.cd_scan_barcode),
                            tint = ext.goldDarkText,
                            modifier = Modifier.padding(13.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledTextField(
                        value = newMinStock,
                        onValueChange = { if (it.all(Char::isDigit)) newMinStock = it },
                        placeholder = "حد التنبيه (5)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.95f),
                        testTag = "inv_add_min_stock"
                    )
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    newExpiry = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.dividerLine),
                        modifier = Modifier.weight(1.25f).height(52.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (newExpiry.isBlank()) "الانتهاء (اختياري)" else newExpiry,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                GoldButton(
                    text = "إضافة منتج جديد",
                    testTag = "inv_add_submit",
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddProduct(
                                newName,
                                newStock.toIntOrNull() ?: 0,
                                newCost.toDoubleOrNull() ?: 0.0,
                                newPrice.toDoubleOrNull() ?: 0.0,
                                newBarcode,
                                newExpiry,
                                newMinStock.toIntOrNull() ?: 5
                            )
                            newName = ""
                            newStock = ""
                            newCost = ""
                            newPrice = ""
                            newBarcode = ""
                            newMinStock = "5"
                            newExpiry = ""
                        }
                    }
                )
            }
        }

        // Full products list (only when not actively searching to avoid duplicate LazyColumn keys)
        if (trimmedQuery.isBlank()) {
            item {
                Text(
                    text = "قائمة المنتجات (${state.products.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (state.products.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد منتجات بعد",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    )
                }
            } else {
                items(state.products, key = { it.id }) { product ->
                    ProductExpandableRow(
                        product = product,
                        currency = cur,
                        soldToday = state.soldTodayFor(product.name),
                        isExpanded = expandedId == product.id,
                        isEditing = editingId == product.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == product.id) null else product.id
                            editingId = null
                        },
                        onStartEdit = { editingId = product.id },
                        onCancelEdit = { editingId = null },
                        onSaveEdit = { n, st, c, p, bc, exp, minSt ->
                            onUpdateProduct(product.id, n, st, c, p, bc, exp, minSt)
                            editingId = null
                        },
                        onAddStock = { qty -> onAddStock(product.id, qty) },
                        onDelete = {
                            onDeleteProduct(product.id)
                            if (expandedId == product.id) expandedId = null
                        }
                    )
                }
            }
        }

        // Full inventory PDF button
        item {
            Button(
                onClick = onOpenInventoryReport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.heroBg,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inv_full_report_button")
            ) {
                Text(
                    text = "كشف الجرد الكامل (PDF / طباعة)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ProductExpandableRow(
    product: ProductEntity,
    currency: String,
    soldToday: Int,
    isExpanded: Boolean,
    isEditing: Boolean,
    onToggleExpand: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: (String, Int, Double, Double, String, String, Int) -> Unit,
    onAddStock: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalKashirakColors.current
    val context = LocalContext.current
    val expLeft = Formatters.expiryDaysLeft(product.expiryDate)
    val isLowStock = product.stock <= product.minStock

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ext.cardBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (isLowStock) ext.badFg.copy(alpha = 0.55f) else ext.dividerLine
            ),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable(onClick = onToggleExpand)
                .testTag("product_row_${product.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        if (isLowStock) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (product.stock <= 0) ext.badFg.copy(alpha = 0.14f) else ext.warnBg
                            ) {
                                Text(
                                    text = if (product.stock <= 0) "نفد" else "منخفض",
                                    color = if (product.stock <= 0) ext.badFg else ext.warnFg,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Formatters.money(product.price)} $currency",
                            color = ext.goldDarkText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                        if (product.barcode.isNotBlank()) {
                            Text(
                                text = "باركود: ${product.barcode}",
                                color = ext.mutedText,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    if (expLeft != null && expLeft <= 30 && product.stock > 0) {
                        Text(
                            text = Formatters.expiryText(expLeft),
                            color = ext.badFg,
                            fontSize = 12.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${product.stock} قطعة",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLowStock) ext.badFg else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "الحد: ${product.minStock}",
                        color = ext.mutedText,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded || isEditing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ext.mutedText
                )
            }
        }

        if (isEditing) {
            var eName by remember(product) { mutableStateOf(product.name) }
            var eStock by remember(product) { mutableStateOf(product.stock.toString()) }
            var eMinStock by remember(product) { mutableStateOf(product.minStock.toString()) }
            var eCost by remember(product) { mutableStateOf(product.cost.toString()) }
            var ePrice by remember(product) { mutableStateOf(product.price.toString()) }
            var eBarcode by remember(product) { mutableStateOf(product.barcode) }
            var eExp by remember(product) { mutableStateOf(product.expiryDate) }

            KashirakCard {
                StyledTextField(eName, { eName = it }, "اسم الصنف")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StyledTextField(
                        value = eStock,
                        onValueChange = { eStock = it },
                        placeholder = "الكمية",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    StyledTextField(
                        value = eCost,
                        onValueChange = { eCost = it },
                        placeholder = "التكلفة",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    StyledTextField(
                        value = ePrice,
                        onValueChange = { ePrice = it },
                        placeholder = "البيع",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StyledTextField(
                        value = eBarcode,
                        onValueChange = { eBarcode = it },
                        placeholder = "الباركود (اختياري)",
                        modifier = Modifier.weight(1.3f)
                    )
                    StyledTextField(
                        value = eMinStock,
                        onValueChange = { if (it.all(Char::isDigit)) eMinStock = it },
                        placeholder = "حد التنبيه",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                        testTag = "inv_edit_min_stock"
                    )
                }
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                eExp = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ext.dividerLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (eExp.isBlank()) "تاريخ الانتهاء (اختياري)" else "تاريخ الانتهاء: $eExp")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoldButton(
                        text = "حفظ",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSaveEdit(
                                eName,
                                eStock.toIntOrNull() ?: 0,
                                eCost.toDoubleOrNull() ?: 0.0,
                                ePrice.toDoubleOrNull() ?: 0.0,
                                eBarcode,
                                eExp,
                                eMinStock.toIntOrNull() ?: 5
                            )
                        }
                    )
                    Button(
                        onClick = onCancelEdit,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.chipBg,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("إلغاء", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (isExpanded) {
            var addQty by remember(product.id) { mutableStateOf("") }
            KashirakCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallSummaryTile("التكلفة", Formatters.money(product.cost), Modifier.weight(1f), compact = true)
                    SmallSummaryTile("حد التنبيه", "${product.minStock}", Modifier.weight(1f), compact = true)
                    SmallSummaryTile("مباع اليوم", soldToday.toString(), Modifier.weight(1f), compact = true)
                }
                Text(
                    text = "بداية اليوم: ${product.stock + soldToday} · سعر البيع ${Formatters.money(product.price)} $currency" +
                        if (product.expiryDate.isNotBlank()) " · الانتهاء ${product.expiryDate}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledTextField(
                        value = addQty,
                        onValueChange = { addQty = it },
                        placeholder = "كمية جديدة",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    GoldButton(
                        text = "إضافة للمخزون",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val qty = addQty.toIntOrNull() ?: 0
                            onAddStock(qty)
                            if (qty > 0) addQty = ""
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartEdit,
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.chipBg,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("تعديل", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, ext.badFg),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("حذف", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SmallSummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val ext = LocalKashirakColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (compact) MaterialTheme.colorScheme.background else ext.cardBg,
        border = BorderStroke(1.dp, ext.dividerLine)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 8.dp else 12.dp)
        ) {
            Text(
                text = label,
                color = ext.mutedText,
                fontSize = 11.5.sp,
                maxLines = 1
            )
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 15.sp else 18.sp,
                maxLines = 1
            )
        }
    }
}
