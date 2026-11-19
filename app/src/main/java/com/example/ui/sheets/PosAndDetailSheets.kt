package com.example.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CustomerEntity
import com.example.data.local.PaymentMethod
import com.example.data.local.SaleEntity
import com.example.data.local.SaleLineItem
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.components.rememberBarcodeScanRequester
import com.example.ui.screens.SmallSummaryTile
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPillBg
import com.example.ui.theme.NavyPrimary
import com.example.util.Formatters
import com.example.util.PdfReportGenerator
import com.example.util.ReportTableData

@Composable
fun SheetTopBar(
    title: String,
    onClose: () -> Unit,
    closeTestTag: String
) {
    val ext = LocalKashirakColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ext.headerBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyPillBg)
                    .clickable(onClick = onClose)
                    .testTag(closeTestTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = Color.White
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(GoldAccent)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SellSheet(
    state: KashirakUiState,
    onClose: () -> Unit,
    onCheckout: (Map<String, Int>, PaymentMethod, String, String?) -> Boolean,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency

    var query by remember { mutableStateOf("") }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var payMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var payName by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var custMenuOpen by remember { mutableStateOf(false) }

    val requestSellBarcodeScan = rememberBarcodeScanRequester(
        onToast = onToast
    ) { code ->
        val exact = state.products.firstOrNull { it.barcode.equals(code, ignoreCase = true) }
        if (exact != null) {
            cart[exact.id] = (cart[exact.id] ?: 0) + 1
            query = ""
            onToast("أُضيف للسلة: ${exact.name}")
        } else {
            query = code
            onToast("لا يوجد صنف بالباركود: $code")
        }
    }

    val filteredProducts = remember(state.products, query) {
        val q = query.trim()
        if (q.isBlank()) state.products
        else state.products.filter {
            it.name.contains(q, ignoreCase = true) || it.barcode.contains(q, ignoreCase = true)
        }
    }

    val cartTotal = cart.entries.sumOf { (id, qty) ->
        val p = state.products.firstOrNull { it.id == id }
        (p?.price ?: 0.0) * qty
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = "فاتورة بيع",
            onClose = onClose,
            closeTestTag = "sell_close_button"
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StyledTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "ابحث عن صنف أو باركود",
                        modifier = Modifier.weight(1f),
                        testTag = "sell_search_input"
                    )
                    Surface(
                        onClick = requestSellBarcodeScan,
                        shape = RoundedCornerShape(14.dp),
                        color = NavyPrimary,
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier
                            .size(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("sell_scan_barcode")
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

            item {
                if (filteredProducts.isEmpty()) {
                    Text(
                        text = "لا توجد أصناف مطابقة",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 3
                    ) {
                        filteredProducts.forEach { p ->
                            Surface(
                                onClick = { cart[p.id] = (cart[p.id] ?: 0) + 1 },
                                shape = RoundedCornerShape(16.dp),
                                color = ext.cardBg,
                                border = BorderStroke(1.dp, ext.dividerLine),
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .width(114.dp)
                                    .testTag("sell_prod_${p.id}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                                    Text(
                                        text = p.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المخزون ${p.stock}",
                                        color = ext.mutedText,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "${Formatters.money(p.price)} $cur",
                                        color = ext.goldDarkText,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "السلة",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (cart.isEmpty()) {
                item {
                    Text(
                        text = "اضغط على صنف لإضافته",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
            } else {
                items(cart.entries.toList(), key = { it.key }) { (prodId, qty) ->
                    val product = state.products.firstOrNull { it.id == prodId }
                    if (product != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = product.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${Formatters.money(product.price)} $cur",
                                    color = ext.mutedText,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QtySquareButton("−") {
                                    val next = qty - 1
                                    if (next <= 0) cart.remove(prodId) else cart[prodId] = next
                                }
                                Text(text = qty.toString(), fontWeight = FontWeight.ExtraBold)
                                QtySquareButton("+") {
                                    cart[prodId] = qty + 1
                                }
                            }
                            Text(
                                text = Formatters.money(product.price * qty),
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(64.dp),
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(color = ext.dividerLine)
                    }
                }
            }
        }

        // Bottom Checkout Bar
        Surface(
            color = ext.navBg,
            border = BorderStroke(1.dp, ext.dividerLine),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allowedMethods = PaymentMethod.entries.filter {
                    it != PaymentMethod.DEBT || state.can("debts")
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedMethods.forEach { method ->
                        val selected = payMethod == method
                        Surface(
                            onClick = {
                                payMethod = method
                                if (method == PaymentMethod.DEBT) {
                                    if (state.customers.isEmpty()) {
                                        onToast("أضف زبوناً من صفحة الديون أولاً")
                                    } else if (selectedCustomerId == null) {
                                        selectedCustomerId = state.customers.first().id
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) NavyPrimary else ext.chipBg,
                            modifier = Modifier.testTag("sell_pm_${method.key}")
                        ) {
                            Text(
                                text = method.arabicLabel,
                                color = if (selected) GoldAccent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                if (payMethod != PaymentMethod.DEBT) {
                    StyledTextField(
                        value = payName,
                        onValueChange = { payName = it },
                        placeholder = "اسم الدفع يدوياً (اختياري)، مثال: اسم البنك أو المحفظة",
                        testTag = "sell_payname_input"
                    )
                }

                val custLabel = state.customers.firstOrNull { it.id == selectedCustomerId }?.name ?: "بدون زبون"
                ExposedDropdownMenuBox(
                    expanded = custMenuOpen,
                    onExpandedChange = { custMenuOpen = !custMenuOpen }
                ) {
                    OutlinedTextField(
                        value = custLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الزبون") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = custMenuOpen)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = ext.dividerLine
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = custMenuOpen,
                        onDismissRequest = { custMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون زبون") },
                            onClick = {
                                selectedCustomerId = null
                                custMenuOpen = false
                            }
                        )
                        state.customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedCustomerId = c.id
                                    custMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (selectedCustomerId != null) {
                    val chosen = state.customers.firstOrNull { it.id == selectedCustomerId }?.name.orEmpty()
                    Text(
                        text = if (payMethod == PaymentMethod.DEBT) "سيُسجَّل الدين على: $chosen" else "الزبون: $chosen",
                        color = ext.mutedText,
                        fontSize = 12.5.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "المجموع", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(
                        text = "${Formatters.money(cartTotal)} $cur",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }

                val canCheckout = cart.isNotEmpty() && (payMethod != PaymentMethod.DEBT || selectedCustomerId != null)
                GoldButton(
                    text = "إتمام البيع",
                    enabled = canCheckout,
                    testTag = "sell_checkout_button",
                    onClick = {
                        if (onCheckout(cart.toMap(), payMethod, payName, selectedCustomerId)) {
                            onClose()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QtySquareButton(symbol: String, onClick: () -> Unit) {
    val ext = LocalKashirakColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ext.chipBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditSheet(
    sale: SaleEntity,
    state: KashirakUiState,
    onClose: () -> Unit,
    onSave: (List<SaleLineItem>, PaymentMethod, String, String) -> Boolean,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency

    var itemsState by remember(sale.id) { mutableStateOf(sale.items) }
    var method by remember(sale.id) { mutableStateOf(PaymentMethod.fromKey(sale.method)) }
    var payName by remember(sale.id) { mutableStateOf(sale.payName) }
    var customerId by remember(sale.id) { mutableStateOf(sale.customerId) }
    var deleteArmed by remember(sale.id) { mutableStateOf(false) }
    var custOpen by remember { mutableStateOf(false) }

    val total = itemsState.sumOf { it.price * it.qty }
    val custName = state.customerNameForSale(sale)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = "فاتورة #${sale.invoiceNumber}",
            onClose = onClose,
            closeTestTag = "invoice_close_button"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val meta = buildString {
                    append(Formatters.dateTimeShort(sale.timestampMillis))
                    if (sale.cashierName.isNotBlank()) append(" · ${sale.cashierName}")
                    if (custName.isNotBlank()) append(" · $custName")
                }
                Text(text = meta, color = ext.mutedText, fontSize = 13.sp)
            }

            item {
                KashirakCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    itemsState.forEachIndexed { idx, line ->
                        if (idx > 0) HorizontalDivider(color = ext.dividerLine)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = line.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${Formatters.money(line.price)} $cur",
                                    color = ext.mutedText,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QtySquareButton("−") {
                                    deleteArmed = false
                                    itemsState = itemsState.toMutableList().also {
                                        if (line.qty > 0) it[idx] = line.copy(qty = line.qty - 1)
                                    }
                                }
                                Text(text = line.qty.toString(), fontWeight = FontWeight.ExtraBold)
                                QtySquareButton("+") {
                                    deleteArmed = false
                                    itemsState = itemsState.toMutableList().also {
                                        it[idx] = line.copy(qty = line.qty + 1)
                                    }
                                }
                            }
                            Text(
                                text = Formatters.money(line.price * line.qty),
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "المجموع", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(text = "${Formatters.money(total)} $cur", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }

            item {
                Text(text = "طريقة الدفع", color = ext.mutedText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { pm ->
                        val selected = method == pm
                        Surface(
                            onClick = {
                                method = pm
                                deleteArmed = false
                                if (pm == PaymentMethod.DEBT && customerId.isBlank() && state.customers.isNotEmpty()) {
                                    customerId = state.customers.first().id
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) NavyPrimary else ext.chipBg
                        ) {
                            Text(
                                text = pm.arabicLabel,
                                color = if (selected) GoldAccent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (method != PaymentMethod.DEBT) {
                item {
                    StyledTextField(
                        value = payName,
                        onValueChange = { payName = it },
                        placeholder = "اسم الدفع يدوياً (اختياري)"
                    )
                }
            }

            item {
                val label = state.customers.firstOrNull { it.id == customerId }?.name ?: "بدون زبون"
                ExposedDropdownMenuBox(
                    expanded = custOpen,
                    onExpandedChange = { custOpen = !custOpen }
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الزبون") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custOpen) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = custOpen,
                        onDismissRequest = { custOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("بدون زبون") },
                            onClick = { customerId = ""; custOpen = false }
                        )
                        state.customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { customerId = c.id; custOpen = false }
                            )
                        }
                    }
                }
            }

            item {
                GoldButton(
                    text = "حفظ التعديل",
                    testTag = "invoice_save_button",
                    onClick = {
                        if (onSave(itemsState, method, payName, customerId)) onClose()
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        if (!deleteArmed) deleteArmed = true else {
                            onDelete()
                            onClose()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.badFg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("invoice_delete_button")
                ) {
                    Text(
                        text = if (deleteArmed) "اضغط مرة أخرى لتأكيد حذف الفاتورة" else "حذف الفاتورة",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "التعديل والحذف يعيدان الكميات للجرد ويصحّحان دين الزبون تلقائياً.",
                    color = ext.mutedText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CustomerStatementSheet(
    customer: CustomerEntity,
    state: KashirakUiState,
    onClose: () -> Unit,
    onUpdateCustomer: (String, String, String) -> Unit,
    onDeleteCustomer: () -> Unit,
    onOpenSaleById: (String) -> Unit,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency
    val context = LocalContext.current

    val stmtResult = remember(customer, state.sales, state.ledgerEvents) {
        state.customerStatement(customer)
    }

    var eName by remember(customer.id) { mutableStateOf(customer.name) }
    var ePhone by remember(customer.id) { mutableStateOf(customer.phone) }
    var eNote by remember(customer.id) { mutableStateOf(customer.note) }
    var deleteArmed by remember(customer.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = customer.name,
            onClose = onClose,
            closeTestTag = "cust_stmt_close"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallSummaryTile("المستحق", Formatters.money(customer.debt), Modifier.weight(1f))
                    SmallSummaryTile("المشتريات", Formatters.money(stmtResult.totalPurchases), Modifier.weight(1f))
                    SmallSummaryTile("المدفوع", Formatters.money(stmtResult.totalPaid), Modifier.weight(1f))
                }
            }

            item {
                KashirakCard {
                    Text(text = "بيانات الزبون", style = MaterialTheme.typography.titleMedium)
                    StyledTextField(eName, { eName = it }, "الاسم")
                    StyledTextField(ePhone, { ePhone = it }, "الهاتف")
                    StyledTextField(eNote, { eNote = it }, "ملاحظات")
                    GoldButton(
                        text = "حفظ التعديلات",
                        onClick = { onUpdateCustomer(eName, ePhone, eNote) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val bytes = PdfReportGenerator.generateCustomerStatementPdf(
                                    context = context,
                                    shopName = state.settings.shopName,
                                    currency = cur,
                                    customer = customer,
                                    rows = stmtResult.rows,
                                    totalPurchases = stmtResult.totalPurchases,
                                    totalPaid = stmtResult.totalPaid
                                )
                                PdfReportGenerator.sharePdf(context, "كشف حساب - ${customer.name}.pdf", bytes)
                            } catch (_: Exception) {
                                onToast("تعذر تجهيز ملف PDF")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ext.heroBg, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("مشاركة / حفظ PDF", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try {
                                val bytes = PdfReportGenerator.generateCustomerStatementPdf(
                                    context = context,
                                    shopName = state.settings.shopName,
                                    currency = cur,
                                    customer = customer,
                                    rows = stmtResult.rows,
                                    totalPurchases = stmtResult.totalPurchases,
                                    totalPaid = stmtResult.totalPaid
                                )
                                PdfReportGenerator.printPdf(context, "كشف حساب ${customer.name}", bytes)
                            } catch (_: Exception) {
                                onToast("تعذر الطباعة")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ext.chipBg,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("طباعة", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        if (!deleteArmed) deleteArmed = true else {
                            onDeleteCustomer()
                            onClose()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ext.badFg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(
                        text = if (deleteArmed) "اضغط مرة أخرى لتأكيد حذف الزبون" else "حذف الزبون",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Text(text = "كشف الحساب", style = MaterialTheme.typography.titleMedium)
            }

            if (stmtResult.rows.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد حركات بعد",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    )
                }
            } else {
                items(stmtResult.rows) { row ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardBg,
                        border = BorderStroke(1.dp, ext.dividerLine),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (row.saleId != null) {
                                    Modifier.clickable { onOpenSaleById(row.saleId) }
                                } else Modifier
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Formatters.shortDate(row.ts),
                                    color = ext.mutedText,
                                    fontSize = 12.sp
                                )
                                val (amtTxt, amtColor) = when {
                                    row.kind == "pay" -> "−${Formatters.money(row.credit)} $cur" to ext.okFg
                                    row.kind == "sale" && row.isPaidSale -> "${Formatters.money(row.total)} $cur" to MaterialTheme.colorScheme.onSurface
                                    else -> "+${Formatters.money(row.debit)} $cur" to ext.badFg
                                }
                                Text(text = amtTxt, fontWeight = FontWeight.ExtraBold, color = amtColor)
                            }
                            Text(text = row.label, fontSize = 13.5.sp)
                            Text(
                                text = (if (row.kind == "sale" && row.isPaidSale) "مدفوعة · " else "") +
                                    "الرصيد بعد الحركة: ${Formatters.money(row.balance)} $cur",
                                color = ext.mutedText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportPreviewSheet(
    data: ReportTableData,
    shopName: String,
    onClose: () -> Unit,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val ext = LocalKashirakColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SheetTopBar(
            title = data.title,
            onClose = onClose,
            closeTestTag = "report_preview_close"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val bytes = PdfReportGenerator.generateTableReportPdf(context, shopName, data)
                            PdfReportGenerator.sharePdf(context, data.fileName, bytes)
                        } catch (_: Exception) {
                            onToast("تعذر تجهيز ملف PDF")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ext.heroBg, contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("حفظ / مشاركة PDF", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        try {
                            val bytes = PdfReportGenerator.generateTableReportPdf(context, shopName, data)
                            PdfReportGenerator.printPdf(context, data.title, bytes)
                        } catch (_: Exception) {
                            onToast("تعذر الطباعة")
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.chipBg,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("طباعة", fontWeight = FontWeight.Bold)
                }
            }

            KashirakCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(10.dp)
            ) {
                val hScroll = rememberScrollState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            data.columns.forEach { col ->
                                Text(
                                    text = col.header,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.mutedText,
                                    fontSize = 13.sp,
                                    textAlign = if (col.alignCenter) TextAlign.Center else TextAlign.Start,
                                    modifier = Modifier.width((col.widthRatio * 54).dp)
                                )
                            }
                        }
                        HorizontalDivider(color = ext.dividerLine)
                    }

                    if (data.rows.isEmpty()) {
                        item {
                            Text(
                                text = "لا توجد بيانات",
                                color = ext.mutedText,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(data.rows) { r ->
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                r.forEachIndexed { i, value ->
                                    val col = data.columns[i]
                                    Text(
                                        text = value,
                                        fontSize = 13.sp,
                                        textAlign = if (col.alignCenter) TextAlign.Center else TextAlign.Start,
                                        modifier = Modifier.width((col.widthRatio * 54).dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = ext.dividerLine)
                        }
                    }

                    item {
                        HorizontalDivider(thickness = 2.dp, color = GoldAccent)
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            data.totals.forEachIndexed { i, value ->
                                val col = data.columns[i]
                                Text(
                                    text = value,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    textAlign = if (col.alignCenter) TextAlign.Center else TextAlign.Start,
                                    modifier = Modifier.width((col.widthRatio * 54).dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
