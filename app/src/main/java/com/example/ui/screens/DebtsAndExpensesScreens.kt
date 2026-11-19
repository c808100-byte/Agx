package com.example.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomerEntity
import com.example.data.local.ExpenseEntity
import com.example.ui.KashirakUiState
import com.example.ui.components.GoldButton
import com.example.ui.components.KashirakCard
import com.example.ui.components.StyledTextField
import com.example.ui.theme.LocalKashirakColors
import com.example.util.Formatters
import java.util.Calendar

@Composable
fun DebtsScreen(
    state: KashirakUiState,
    onAddCustomer: (String, Double, String) -> Unit,
    onPayDebt: (String, Double) -> Unit,
    onAddDebt: (String, Double, String) -> Unit,
    onOpenCustomerStatement: (CustomerEntity) -> Unit,
    onOpenAllCustomersReport: () -> Unit
) {
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency

    var cName by remember { mutableStateOf("") }
    var cDebt by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "الديون", style = MaterialTheme.typography.titleLarge)
        }

        // Add Customer Form
        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StyledTextField(
                        value = cName,
                        onValueChange = { cName = it },
                        placeholder = "اسم الزبون",
                        modifier = Modifier.weight(1f),
                        testTag = "debt_add_name"
                    )
                    StyledTextField(
                        value = cDebt,
                        onValueChange = { cDebt = it },
                        placeholder = "الدين الحالي",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "debt_add_initial"
                    )
                }
                StyledTextField(
                    value = cPhone,
                    onValueChange = { cPhone = it },
                    placeholder = "رقم الهاتف (اختياري)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    testTag = "debt_add_phone"
                )
                GoldButton(
                    text = "إضافة زبون",
                    testTag = "debt_add_submit",
                    onClick = {
                        if (cName.isNotBlank()) {
                            onAddCustomer(cName, cDebt.toDoubleOrNull() ?: 0.0, cPhone)
                            cName = ""
                            cDebt = ""
                            cPhone = ""
                        }
                    }
                )
            }
        }

        if (state.customers.isEmpty()) {
            item {
                Text(
                    text = "لا يوجد زبائن",
                    color = ext.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                )
            }
        } else {
            items(state.customers, key = { it.id }) { customer ->
                CustomerDebtCard(
                    customer = customer,
                    currency = cur,
                    onOpenStatement = { onOpenCustomerStatement(customer) },
                    onPayDebt = { amt -> onPayDebt(customer.id, amt) },
                    onAddDebt = { amt, note -> onAddDebt(customer.id, amt, note) }
                )
            }
        }

        item {
            Button(
                onClick = onOpenAllCustomersReport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.heroBg,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("debts_full_report_button")
            ) {
                Text(
                    text = "كشف كل الزبائن (PDF / طباعة)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CustomerDebtCard(
    customer: CustomerEntity,
    currency: String,
    onOpenStatement: () -> Unit,
    onPayDebt: (Double) -> Unit,
    onAddDebt: (Double, String) -> Unit
) {
    val ext = LocalKashirakColors.current
    var payText by remember(customer.id) { mutableStateOf("") }
    var addAmtText by remember(customer.id) { mutableStateOf("") }
    var addNoteText by remember(customer.id) { mutableStateOf("") }

    KashirakCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onOpenStatement)
                    .testTag("customer_open_${customer.id}")
            ) {
                Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = "عليه ${Formatters.money(customer.debt)} $currency · كشف الحساب ‹",
                    color = ext.mutedText,
                    fontSize = 12.5.sp
                )
            }

            StyledTextField(
                value = payText,
                onValueChange = { payText = it },
                placeholder = "تسديد",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(105.dp)
            )
            GoldButton(
                text = "تسديد",
                modifier = Modifier.width(78.dp),
                onClick = {
                    val v = payText.toDoubleOrNull() ?: 0.0
                    onPayDebt(v)
                    if (v > 0.0) payText = ""
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledTextField(
                value = addAmtText,
                onValueChange = { addAmtText = it },
                placeholder = "مبلغ دين جديد",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            StyledTextField(
                value = addNoteText,
                onValueChange = { addNoteText = it },
                placeholder = "بيان (اختياري)",
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val v = addAmtText.toDoubleOrNull() ?: 0.0
                    onAddDebt(v, addNoteText)
                    if (v > 0.0) {
                        addAmtText = ""
                        addNoteText = ""
                    }
                },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.chipBg,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("إضافة دين", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
fun ExpensesScreen(
    state: KashirakUiState,
    onAddExpense: (Double, String) -> Unit,
    onUpdateExpense: (String, Double, String, Long) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onOpenExpensesReport: () -> Unit
) {
    val ext = LocalKashirakColors.current
    val cur = state.settings.currency
    val context = LocalContext.current

    var eAmount by remember { mutableStateOf("") }
    var eTitle by remember { mutableStateOf("") }
    var expandedExpId by remember { mutableStateOf<String?>(null) }
    var armedDeleteId by remember { mutableStateOf<String?>(null) }

    val recentExpenses = remember(state.expenses) {
        state.expenses.takeLast(30).asReversed()
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "المصروف", style = MaterialTheme.typography.titleLarge)
        }

        item {
            KashirakCard {
                Text(text = "تسجيل مصروف", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StyledTextField(
                        value = eAmount,
                        onValueChange = { eAmount = it },
                        placeholder = "المبلغ",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        testTag = "exp_add_amount"
                    )
                    StyledTextField(
                        value = eTitle,
                        onValueChange = { eTitle = it },
                        placeholder = "البيان",
                        modifier = Modifier.weight(1f),
                        testTag = "exp_add_title"
                    )
                }
                GoldButton(
                    text = "حفظ المصروف",
                    testTag = "exp_add_submit",
                    onClick = {
                        val amt = eAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0 && eTitle.isNotBlank()) {
                            onAddExpense(amt, eTitle)
                            eAmount = ""
                            eTitle = ""
                        }
                    }
                )
            }
        }

        item {
            KashirakCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "المصروفات", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "اضغط للتعديل أو الحذف",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText
                    )
                }

                if (recentExpenses.isEmpty()) {
                    Text(
                        text = "لا توجد مصروفات",
                        color = ext.mutedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    recentExpenses.forEach { expense ->
                        HorizontalDivider(color = ext.dividerLine)
                        ExpenseExpandableRow(
                            expense = expense,
                            currency = cur,
                            isExpanded = expandedExpId == expense.id,
                            isDeleteArmed = armedDeleteId == expense.id,
                            onToggle = {
                                expandedExpId = if (expandedExpId == expense.id) null else expense.id
                                armedDeleteId = null
                            },
                            onSave = { amt, title, newTs ->
                                onUpdateExpense(expense.id, amt, title, newTs)
                                expandedExpId = null
                                armedDeleteId = null
                            },
                            onCancel = {
                                expandedExpId = null
                                armedDeleteId = null
                            },
                            onDeleteClick = {
                                if (armedDeleteId != expense.id) {
                                    armedDeleteId = expense.id
                                } else {
                                    onDeleteExpense(expense.id)
                                    expandedExpId = null
                                    armedDeleteId = null
                                }
                            },
                            onPickDate = { currentTs, onPicked ->
                                val cal = Calendar.getInstance().apply { timeInMillis = currentTs }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val next = Calendar.getInstance().apply {
                                            timeInMillis = currentTs
                                            set(y, m, d)
                                        }.timeInMillis
                                        onPicked(next)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).apply {
                                    datePicker.maxDate = System.currentTimeMillis()
                                }.show()
                            }
                        )
                    }
                    if (state.expenses.size > 30) {
                        Text(
                            text = "يظهر آخر 30 مصروفاً. الكشف الكامل في الزر أدناه.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onOpenExpensesReport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.heroBg,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("exp_full_report_button")
            ) {
                Text(
                    text = "كشف المصروفات (PDF / طباعة)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ExpenseExpandableRow(
    expense: ExpenseEntity,
    currency: String,
    isExpanded: Boolean,
    isDeleteArmed: Boolean,
    onToggle: () -> Unit,
    onSave: (Double, String, Long) -> Unit,
    onCancel: () -> Unit,
    onDeleteClick: () -> Unit,
    onPickDate: (Long, (Long) -> Unit) -> Unit
) {
    val ext = LocalKashirakColors.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${Formatters.shortDate(expense.timestampMillis)} · ${expense.title}",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${Formatters.money(expense.amount)} $currency",
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (isExpanded) {
            var xAmt by remember(expense) { mutableStateOf(expense.amount.toString()) }
            var xTitle by remember(expense) { mutableStateOf(expense.title) }
            var xTs by remember(expense) { mutableStateOf(expense.timestampMillis) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, ext.dividerLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyledTextField(
                            value = xAmt,
                            onValueChange = { xAmt = it },
                            placeholder = "المبلغ",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        StyledTextField(
                            xTitle,
                            { xTitle = it },
                            "البيان",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = { onPickDate(xTs) { xTs = it } },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.dividerLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("التاريخ: ${Formatters.isoDay(xTs)}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GoldButton(
                            text = "حفظ",
                            modifier = Modifier.weight(1f),
                            onClick = { onSave(xAmt.toDoubleOrNull() ?: expense.amount, xTitle, xTs) }
                        )
                        Button(
                            onClick = onCancel,
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
                    OutlinedButton(
                        onClick = onDeleteClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ext.badFg),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.badFg),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = if (isDeleteArmed) "اضغط مرة أخرى لتأكيد الحذف" else "حذف المصروف",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
