package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PaymentMethod
import com.example.data.local.SaleEntity
import com.example.ui.AppTab
import com.example.ui.DailyBarPoint
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPrimary
import com.example.util.Formatters
import java.util.Calendar
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: KashirakUiState,
    onQuickSale: (Double, String, PaymentMethod, String, String?) -> Unit,
    onStepDate: (Int) -> Unit,
    onPickDate: (Long?) -> Unit,
    onOpenInvoice: (SaleEntity) -> Unit,
    onOpenInventory: () -> Unit = {}
) {
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency
    val context = LocalContext.current

    var quickAmount by remember { mutableStateOf("") }
    var quickNote by remember { mutableStateOf("") }
    var quickMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var quickPayName by remember { mutableStateOf("") }
    var selectedCustomerId by remember(state.customers) {
        mutableStateOf(state.customers.firstOrNull()?.id)
    }
    var custDropdownExpanded by remember { mutableStateOf(false) }

    val t0 = Formatters.dayStartMillis()
    val sd = state.selectedHomeDateMillis ?: t0
    val isToday = sd == t0
    val nextDay = Formatters.addDays(sd, 1)
    val daySales = remember(state.sales, sd) {
        state.sales.filter { it.timestampMillis in sd until nextDay }
    }
    val dayTotal = remember(daySales) { daySales.sumOf { it.total } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Stat Grid (2x2 matching HTML)
        item {
            val secondaryCards = buildList<Pair<String, String>> {
                if (state.can("profit")) add("صافي الصندوق بعد المصاريف" to Formatters.money(abs(state.todayProfit)))
                if (state.can("debts")) add("ديون على الزبائن" to Formatters.money(state.totalDebt))
                if (state.can("cash")) add("المتوقع في الصندوق" to Formatters.money(abs(state.expectedCash)))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroSalesCard(
                        title = "مبيعات اليوم",
                        amount = Formatters.money(state.todaySalesTotal),
                        currency = cur,
                        invoiceCount = state.todaySalesCount,
                        modifier = Modifier.weight(1f)
                    )
                    if (secondaryCards.isNotEmpty()) {
                        StatTileCard(
                            label = secondaryCards[0].first,
                            amount = secondaryCards[0].second,
                            currency = cur,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (secondaryCards.size == 2) {
                    StatTileCard(
                        label = secondaryCards[1].first,
                        amount = secondaryCards[1].second,
                        currency = cur,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (secondaryCards.size >= 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatTileCard(
                            label = secondaryCards[1].first,
                            amount = secondaryCards[1].second,
                            currency = cur,
                            modifier = Modifier.weight(1f)
                        )
                        StatTileCard(
                            label = secondaryCards[2].first,
                            amount = secondaryCards[2].second,
                            currency = cur,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Low-stock alert banner on Home when products reach minimum threshold
        val lowStockItems = state.lowStockProducts()
        if (lowStockItems.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ext.warnBg,
                    border = BorderStroke(1.dp, ext.warnFg.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_low_stock_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = stringResource(R.string.cd_low_stock_alert),
                                    tint = ext.warnFg,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "تنبيه انخفاض المخزون",
                                    color = ext.warnFg,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ext.warnFg
                            ) {
                                Text(
                                    text = "${lowStockItems.size} صنف",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }

                        lowStockItems.take(3).forEach { prod ->
                            val statusText = if (prod.stock <= 0) "نفد المخزون" else "متبقي ${prod.stock}"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = prod.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$statusText (الحد ${prod.minStock})",
                                    color = if (prod.stock <= 0) ext.badFg else ext.warnFg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        if (state.allowed(AppTab.INVENTORY)) {
                            Button(
                                onClick = onOpenInventory,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NavyPrimary,
                                    contentColor = GoldAccent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_low_stock_action")
                            ) {
                                Text("عرض وتزويد المخزون", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Quick Sale Card ("مبيعات سريعة")
        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مبيعات سريعة",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ext.goldDarkText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StyledTextField(
                        value = quickAmount,
                        onValueChange = { quickAmount = it },
                        placeholder = "المبلغ",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "quick_sale_amount"
                    )
                    StyledTextField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        placeholder = "البيان (اختياري)",
                        modifier = Modifier.weight(1f),
                        testTag = "quick_sale_note"
                    )
                }

                val allowedMethods = PaymentMethod.entries.filter {
                    it != PaymentMethod.DEBT || state.can("debts")
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedMethods.forEach { method ->
                        val selected = quickMethod == method
                        Surface(
                            onClick = { quickMethod = method },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) NavyPrimary else ext.chipBg,
                            modifier = Modifier.testTag("quick_pm_${method.key}")
                        ) {
                            Text(
                                text = method.arabicLabel,
                                color = if (selected) GoldAccent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (quickMethod != PaymentMethod.DEBT) {
                    StyledTextField(
                        value = quickPayName,
                        onValueChange = { quickPayName = it },
                        placeholder = "اسم طريقة الدفع يدوياً (اختياري)",
                        testTag = "quick_pay_name"
                    )
                } else {
                    val selectedCustName = state.customers.firstOrNull { it.id == selectedCustomerId }?.name
                        ?: "لا يوجد زبائن - أضف زبوناً من الديون"
                    ExposedDropdownMenuBox(
                        expanded = custDropdownExpanded,
                        onExpandedChange = {
                            if (state.customers.isNotEmpty()) custDropdownExpanded = !custDropdownExpanded
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedCustName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الزبون") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = ext.dividerLine
                            ),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("quick_customer_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = custDropdownExpanded,
                            onDismissRequest = { custDropdownExpanded = false }
                        ) {
                            state.customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = {
                                        selectedCustomerId = c.id
                                        custDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                GoldButton(
                    text = "تسجيل البيع",
                    testTag = "quick_sale_submit",
                    onClick = {
                        val amt = quickAmount.toDoubleOrNull() ?: 0.0
                        onQuickSale(amt, quickNote, quickMethod, quickPayName, selectedCustomerId)
                        if (amt > 0.0 && (quickMethod != PaymentMethod.DEBT || selectedCustomerId != null)) {
                            quickAmount = ""
                            quickNote = ""
                            quickPayName = ""
                        }
                    }
                )
            }
        }

        // 3. Daily Sales Card with Date Navigator & Payment Breakdown
        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isToday) "مبيعات اليوم" else "مبيعات يوم ${Formatters.shortDate(sd)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${Formatters.money(dayTotal)} $cur",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Date Navigator Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onStepDate(-1) },
                        shape = RoundedCornerShape(11.dp),
                        color = ext.chipBg,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_prev_day")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.cd_prev_day),
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = sd }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val chosen = Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    onPickDate(chosen)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.dividerLine),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("home_date_picker")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatters.isoDay(sd),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        onClick = { if (!isToday) onStepDate(1) },
                        enabled = !isToday,
                        shape = RoundedCornerShape(11.dp),
                        color = ext.chipBg.copy(alpha = if (isToday) 0.4f else 1f),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_next_day")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.cd_next_day),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!isToday) {
                    Surface(
                        onClick = { onPickDate(null) },
                        shape = RoundedCornerShape(11.dp),
                        color = ext.chipBg,
                        modifier = Modifier.fillMaxWidth().testTag("home_back_today")
                    ) {
                        Text(
                            text = "العودة إلى اليوم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Text(
                    text = "${daySales.size} فاتورة",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText
                )

                // Color-coded payment method boxes
                PaymentMethod.entries.forEach { method ->
                    val sumMethod = daySales.filter { it.method == method.key }.sumOf { it.total }
                    if (sumMethod > 0.0) {
                        PaymentMethodSummaryBox(
                            label = method.arabicLabel,
                            amount = "${Formatters.money(sumMethod)} $cur",
                            accent = method.accentColor
                        )
                    }
                }

                if (daySales.isEmpty()) {
                    Text(
                        text = "لا توجد مبيعات في هذا اليوم",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    )
                } else {
                    daySales.asReversed().forEach { sale ->
                        HorizontalDivider(color = ext.dividerLine)
                        val pm = PaymentMethod.fromKey(sale.method)
                        val pmTxt = pm.arabicLabel + if (sale.payName.isNotBlank()) " · ${sale.payName}" else ""
                        val custTxt = state.customerNameForSale(sale)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenInvoice(sale) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("sale_row_${sale.invoiceNumber}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${Formatters.timeOnly(sale.timestampMillis)} · $pmTxt${if (custTxt.isNotBlank()) " · $custTxt" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${Formatters.money(sale.total)} $cur",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. 7-Day Gold Sales Chart
        item {
            WeeklySalesChartCard(
                points = state.last7DaysChart(),
                currency = cur
            )
        }
    }
}

@Composable
private fun HeroSalesCard(
    title: String,
    amount: String,
    currency: String,
    invoiceCount: Int,
    modifier: Modifier = Modifier
) {
    val ext = LocalKashirakColors.current
    Surface(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        color = ext.heroBg,
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Decorative gold circle outline matching HTML `.stat.hero::after`
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = GoldAccent.copy(alpha = 0.28f),
                    radius = 58.dp.toPx(),
                    center = Offset(x = 24.dp.toPx(), y = size.height + 10.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = ext.heroFg,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = GoldAccent, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)) {
                            append(amount)
                        }
                        withStyle(SpanStyle(color = GoldAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)) {
                            append(" $currency")
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$invoiceCount فاتورة",
                    color = ext.heroFg,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun StatTileCard(
    label: String,
    amount: String,
    currency: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalKashirakColors.current
    Surface(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(22.dp),
        color = ext.cardBg,
        border = BorderStroke(1.dp, ext.dividerLine),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = ext.mutedText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)) {
                        append(amount)
                    }
                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                        append(" $currency")
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaymentMethodSummaryBox(
    label: String,
    amount: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(width = 0.dp, color = Color.Transparent, shape = RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(42.dp)
                .background(accent)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(text = amount, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun WeeklySalesChartCard(
    points: List<DailyBarPoint>,
    currency: String
) {
    val ext = LocalKashirakColors.current
    val maxVal = (points.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)

    KashirakCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "مبيعات آخر 7 أيام", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${Formatters.money(points.sumOf { it.total })} $currency",
                color = ext.goldDarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { pt ->
                val ratio = (pt.total / maxVal).toFloat().coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pt.total > 0) pt.total.toInt().toString() else "0",
                        color = ext.mutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val barH = if (pt.total > 0.0) (ratio * 92f).coerceAtLeast(6f).dp else 2.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .height(barH)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(GoldAccent.copy(alpha = if (pt.isToday) 1f else 0.78f))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (pt.isToday) GoldAccent else ext.chipBg)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = pt.dayLabel,
                        fontSize = 11.sp,
                        color = if (pt.isToday) MaterialTheme.colorScheme.onSurface else ext.mutedText,
                        fontWeight = if (pt.isToday) FontWeight.ExtraBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
