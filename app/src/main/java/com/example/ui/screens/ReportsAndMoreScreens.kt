package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PaymentMethod
import com.example.data.local.SaleEntity
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.theme.LocalKashirakColors
import com.example.util.Formatters

@Composable
fun ReportsScreen(
    state: KashirakUiState,
    onOpenInvoice: (SaleEntity) -> Unit
) {
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency
    val t0 = Formatters.dayStartMillis()

    fun periodStats(fromMillis: Long): Triple<Double, Double, Int> {
        val salesIn = state.sales.filter { it.timestampMillis >= fromMillis }
        val total = salesIn.sumOf { it.total }
        val exp = state.expenses.filter { it.timestampMillis >= fromMillis }.sumOf { it.amount }
        val profit = total - salesIn.sumOf { it.cost } - exp
        return Triple(total, profit, salesIn.size)
    }

    val periods = listOf(
        "اليوم" to periodStats(t0),
        "آخر 7 أيام" to periodStats(Formatters.addDays(t0, -6)),
        "آخر 30 يوماً" to periodStats(Formatters.addDays(t0, -29)),
        "الكل" to periodStats(0L)
    )

    val topProducts = remember(state.sales) {
        val counts = mutableMapOf<String, Int>()
        state.sales.forEach { s ->
            s.items.forEach { item ->
                counts[item.name] = (counts[item.name] ?: 0) + item.qty
            }
        }
        counts.entries.sortedByDescending { it.value }.take(5)
    }

    val recentSales = remember(state.sales) {
        state.sales.takeLast(8).asReversed()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "التقارير", style = MaterialTheme.typography.titleLarge)
        }

        // 2x2 period tiles
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (rowIdx in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (colIdx in 0..1) {
                            val (label, stats) = periods[rowIdx * 2 + colIdx]
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = ext.cardBg,
                                border = BorderStroke(1.dp, ext.dividerLine)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = "مبيعات $label", color = ext.mutedText, fontSize = 12.sp)
                                    Text(
                                        text = "${Formatters.money(stats.first)} $cur",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    val sub = if (state.can("profit")) {
                                        "ربح ${Formatters.money(stats.second)} · ${stats.third} فاتورة"
                                    } else {
                                        "${stats.third} فاتورة"
                                    }
                                    Text(text = sub, color = ext.mutedText, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7-day chart
        item {
            WeeklySalesChartCard(
                points = state.last7DaysChart(),
                currency = cur
            )
        }

        // Breakdown by payment method
        item {
            KashirakCard {
                Text(text = "حسب طريقة الدفع", style = MaterialTheme.typography.titleMedium)
                PaymentMethod.entries.forEach { method ->
                    val sumMethod = state.sales.filter { it.method == method.key }.sumOf { it.total }
                    HorizontalDivider(color = ext.dividerLine)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = method.arabicLabel)
                        Text(text = "${Formatters.money(sumMethod)} $cur", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Best sellers
        item {
            KashirakCard {
                Text(text = "الأكثر مبيعاً", style = MaterialTheme.typography.titleMedium)
                if (topProducts.isEmpty()) {
                    Text(
                        text = "لا توجد مبيعات بعد",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                } else {
                    topProducts.forEach { (name, qty) ->
                        HorizontalDivider(color = ext.dividerLine)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = name, fontWeight = FontWeight.Bold)
                            Text(text = "$qty قطعة")
                        }
                    }
                }
            }
        }

        // Recent invoices
        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "آخر الفواتير", style = MaterialTheme.typography.titleMedium)
                    Text(text = "اضغط للتعديل", color = ext.mutedText, fontSize = 12.sp)
                }
                if (recentSales.isEmpty()) {
                    Text(
                        text = "لا توجد فواتير",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                } else {
                    recentSales.forEach { sale ->
                        HorizontalDivider(color = ext.dividerLine)
                        val pm = PaymentMethod.fromKey(sale.method).arabicLabel +
                            if (sale.payName.isNotBlank()) " · ${sale.payName}" else ""
                        val cust = state.customerNameForSale(sale)
                        val by = if (sale.cashierName.isNotBlank()) " · ${sale.cashierName}" else ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onOpenInvoice(sale) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${Formatters.timeOnly(sale.timestampMillis)} · $pm${if (cust.isNotBlank()) " · $cust" else ""}$by",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${Formatters.money(sale.total)} $cur",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreScreen(
    onOpenWorkers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "المزيد", style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MoreMenuTile(
                label = "العمال",
                icon = Icons.Default.Group,
                testTag = "more_workers_tile",
                onClick = onOpenWorkers,
                modifier = Modifier.weight(1f)
            )
            MoreMenuTile(
                label = "الإعدادات",
                icon = Icons.Default.Settings,
                testTag = "more_settings_tile",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }

        MoreMenuTile(
            label = "المزامنة وحساب جوجل",
            icon = Icons.Default.CloudSync,
            testTag = "more_sync_tile",
            onClick = onOpenSyncSheet,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MoreMenuTile(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalKashirakColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ext.cardBg,
        border = BorderStroke(1.dp, ext.dividerLine),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 22.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ext.goldDarkText,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun WorkersScreen(
    state: KashirakUiState,
    onBack: () -> Unit,
    onAddWorker: (String, String) -> Boolean,
    onDeleteWorker: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val ext = LocalKashirakColors.current

    var wName by remember { mutableStateOf("") }
    var wPin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "العمال", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ext.chipBg,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("رجوع", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            KashirakCard {
                StyledTextField(
                    value = wName,
                    onValueChange = { wName = it },
                    placeholder = "اسم العامل",
                    testTag = "worker_add_name"
                )
                StyledTextField(
                    value = wPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) wPin = it },
                    placeholder = "رقم سري من 4 أرقام",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "worker_add_pin"
                )
                Text(
                    text = "يدخل العامل بهذا الرقم. صلاحياته تحددها من إعدادات المحل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText
                )
                GoldButton(
                    text = "إضافة عامل",
                    testTag = "worker_add_submit",
                    onClick = {
                        if (onAddWorker(wName, wPin)) {
                            wName = ""
                            wPin = ""
                        }
                    }
                )
            }
        }

        if (state.workers.isEmpty()) {
            item {
                Text(
                    text = "لم تضف عمالاً بعد",
                    color = ext.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                )
            }
        } else {
            items(state.workers, key = { it.id }) { worker ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.cardBg,
                    border = BorderStroke(1.dp, ext.dividerLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = worker.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "الرقم السري: ${worker.pin}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText
                            )
                        }
                        OutlinedButton(
                            onClick = { onDeleteWorker(worker.id) },
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, ext.badFg),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg)
                        ) {
                            Text("حذف", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
