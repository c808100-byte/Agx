package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomerEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.KashirakDao
import com.example.data.local.LedgerEventEntity
import com.example.data.local.PaymentMethod
import com.example.data.local.ProductEntity
import com.example.data.local.SaleEntity
import com.example.data.local.SaleLineItem
import com.example.data.local.ShopSettingsEntity
import com.example.data.local.SyncState
import com.example.data.local.WorkerEntity
import com.example.data.sync.ConnectivityObserver
import com.example.data.sync.KashirakSyncRepository
import com.example.util.Formatters
import com.example.util.StatementRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("الرئيسية"),
    DEBTS("الديون"),
    EXPENSES("المصروف"),
    REPORTS("التقارير"),
    INVENTORY("الجرد"),
    MORE("المزيد"),
    WORKERS("العمال")
}

data class DailyBarPoint(
    val dayStartMillis: Long,
    val dayLabel: String,
    val total: Double,
    val isToday: Boolean
)

data class CustomerStatementResult(
    val customer: CustomerEntity,
    val rows: List<StatementRow>,
    val totalPurchases: Double,
    val totalPaid: Double
)

data class LowStockAlertEvent(
    val id: Long,
    val products: List<ProductEntity>
)

data class KashirakUiState(
    val isInitialized: Boolean = false,
    val settings: ShopSettingsEntity = ShopSettingsEntity(userId = ""),
    val products: List<ProductEntity> = emptyList(),
    val dbLowStockProducts: List<ProductEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val workers: List<WorkerEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val ledgerEvents: List<LedgerEventEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val activeTab: AppTab = AppTab.HOME,
    val selectedHomeDateMillis: Long? = null,
    val toastMessage: String? = null,
    val lowStockAlert: LowStockAlertEvent? = null
) {
    val isOwner: Boolean get() = settings.role == "owner"

    fun can(permission: String): Boolean {
        if (isOwner) return true
        return when (permission) {
            "items" -> settings.permItems
            "reports" -> settings.permReports
            "debts" -> settings.permDebts
            "expenses" -> settings.permExpenses
            "profit" -> settings.permProfit
            "cash" -> settings.permCash
            else -> false
        }
    }

    fun allowed(tab: AppTab): Boolean {
        if (isOwner) return true
        return when (tab) {
            AppTab.HOME -> true
            AppTab.INVENTORY -> can("items")
            AppTab.REPORTS -> can("reports")
            AppTab.DEBTS -> can("debts")
            AppTab.EXPENSES -> can("expenses")
            AppTab.MORE, AppTab.WORKERS -> false
        }
    }

    val todaySalesTotal: Double
        get() {
            val t0 = Formatters.dayStartMillis()
            return sales.filter { it.timestampMillis >= t0 }.sumOf { it.total }
        }

    val todaySalesCount: Int
        get() {
            val t0 = Formatters.dayStartMillis()
            return sales.count { it.timestampMillis >= t0 }
        }

    val todayProfit: Double
        get() {
            val t0 = Formatters.dayStartMillis()
            val ts = sales.filter { it.timestampMillis >= t0 }
            val exp = expenses.filter { it.timestampMillis >= t0 }.sumOf { it.amount }
            return ts.sumOf { it.total } - ts.sumOf { it.cost } - exp
        }

    val expectedCash: Double
        get() {
            val cashSales = sales.filter { it.method == PaymentMethod.CASH.key }.sumOf { it.total }
            val payments = ledgerEvents.filter { it.type == "payment" }.sumOf { it.amount }
            val exp = expenses.sumOf { it.amount }
            return settings.cash0 + cashSales + payments - exp
        }

    val totalDebt: Double
        get() = customers.sumOf { it.debt }

    fun soldTodayFor(productName: String): Int {
        val t0 = Formatters.dayStartMillis()
        var qty = 0
        sales.forEach { s ->
            if (s.timestampMillis >= t0) {
                s.items.forEach { item ->
                    if (item.name == productName) qty += item.qty
                }
            }
        }
        return qty
    }

    fun lowStockProducts(): List<ProductEntity> =
        if (dbLowStockProducts.isNotEmpty()) dbLowStockProducts
        else products.filter { it.stock <= it.minStock }.sortedBy { it.stock }

    fun expiringProducts(): List<ProductEntity> {
        return products.filter { p ->
            val left = Formatters.expiryDaysLeft(p.expiryDate)
            left != null && left <= 30 && p.stock > 0
        }.sortedBy { Formatters.expiryDaysLeft(it.expiryDate) ?: Int.MAX_VALUE }
    }

    fun last7DaysChart(): List<DailyBarPoint> {
        val today0 = Formatters.dayStartMillis()
        return (6 downTo 0).map { offset ->
            val start = Formatters.addDays(today0, -offset)
            val end = Formatters.addDays(start, 1)
            val total = sales.filter { it.timestampMillis in start until end }.sumOf { it.total }
            DailyBarPoint(
                dayStartMillis = start,
                dayLabel = Formatters.shortDayName(start),
                total = total,
                isToday = offset == 0
            )
        }
    }

    fun customerNameForSale(sale: SaleEntity): String {
        if (sale.customerId.isNotBlank()) {
            customers.firstOrNull { it.id == sale.customerId }?.let { return it.name }
        }
        return sale.customerName
    }

    fun customerStatement(customer: CustomerEntity): CustomerStatementResult {
        val raw = mutableListOf<StatementRow>()
        if (customer.openingDebt != 0.0) {
            raw.add(
                StatementRow(
                    ts = customer.sinceMillis,
                    kind = "open",
                    saleId = null,
                    invoiceNumber = null,
                    isPaidSale = false,
                    total = customer.openingDebt,
                    label = "رصيد سابق",
                    debit = customer.openingDebt,
                    credit = 0.0,
                    balance = 0.0
                )
            )
        }
        sales.filter { it.customerId == customer.id }.forEach { s ->
            val pm = PaymentMethod.fromKey(s.method).arabicLabel +
                if (s.payName.isNotBlank()) " · ${s.payName}" else ""
            val itemsDesc = s.items.joinToString("، ") { "${it.name} ×${it.qty}" }
            val isPaid = s.method != PaymentMethod.DEBT.key
            raw.add(
                StatementRow(
                    ts = s.timestampMillis,
                    kind = "sale",
                    saleId = s.id,
                    invoiceNumber = s.invoiceNumber,
                    isPaidSale = isPaid,
                    total = s.total,
                    label = "فاتورة #${s.invoiceNumber} ($pm) · $itemsDesc",
                    debit = s.total,
                    credit = if (isPaid) s.total else 0.0,
                    balance = 0.0
                )
            )
        }
        ledgerEvents.filter { it.customerId == customer.id }.forEach { ev ->
            if (ev.type == "payment") {
                raw.add(
                    StatementRow(
                        ts = ev.timestampMillis,
                        kind = "pay",
                        saleId = null,
                        invoiceNumber = null,
                        isPaidSale = false,
                        total = ev.amount,
                        label = "تسديد",
                        debit = 0.0,
                        credit = ev.amount,
                        balance = 0.0
                    )
                )
            } else {
                raw.add(
                    StatementRow(
                        ts = ev.timestampMillis,
                        kind = "add",
                        saleId = null,
                        invoiceNumber = null,
                        isPaidSale = false,
                        total = ev.amount,
                        label = "دين مضاف" + if (ev.note.isNotBlank()) " · ${ev.note}" else "",
                        debit = ev.amount,
                        credit = 0.0,
                        balance = 0.0
                    )
                )
            }
        }
        raw.sortBy { it.ts }
        var bal = 0.0
        val computed = raw.map { row ->
            bal += row.debit - row.credit
            row.copy(balance = bal)
        }
        val buys = computed.filter { it.kind == "sale" }.sumOf { it.debit }
        val paid = computed.sumOf { it.credit }
        return CustomerStatementResult(customer, computed, buys, paid)
    }
}

class KashirakViewModel(
    private val dao: KashirakDao,
    private val syncRepo: KashirakSyncRepository,
    private val connectivityObserver: ConnectivityObserver,
    val currentUserId: String
) : ViewModel() {

    private val _activeTab = MutableStateFlow(AppTab.HOME)
    private val _selectedHomeDate = MutableStateFlow<Long?>(null)
    private val _toast = MutableStateFlow<String?>(null)
    private val _lowStockAlert = MutableStateFlow<LowStockAlertEvent?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)
    private val _isInitialized = MutableStateFlow(false)

    private val dbDataFlow = combine(
        dao.observeSettings(currentUserId),
        dao.observeProducts(currentUserId),
        dao.observeCustomers(currentUserId),
        dao.observeWorkers(currentUserId),
        dao.observeSales(currentUserId)
    ) { settings, products, customers, workers, sales ->
        DbPart1(settings, products, customers, workers, sales)
    }

    private val dbMetaFlow = combine(
        dao.observeExpenses(currentUserId),
        dao.observeLedgerEvents(currentUserId),
        dao.observePendingSyncCount(currentUserId),
        connectivityObserver.isOnline,
        dao.observeLowStockProducts(currentUserId)
    ) { expenses, ledger, pending, online, lowStock ->
        DbPart2(expenses, ledger, pending, online, lowStock)
    }

    private val uiControlsPartA = combine(
        _activeTab,
        _selectedHomeDate,
        _toast,
        _lowStockAlert
    ) { tab, homeDate, toast, lowStockAlert ->
        UiPartA(tab, homeDate, toast, lowStockAlert)
    }

    private val uiControlsFlow = combine(
        uiControlsPartA,
        _isSyncing,
        _syncError,
        _isInitialized
    ) { partA, syncing, syncError, initialized ->
        UiPart(
            tab = partA.tab,
            homeDate = partA.homeDate,
            toast = partA.toast,
            lowStockAlert = partA.lowStockAlert,
            syncing = syncing,
            syncError = syncError,
            initialized = initialized
        )
    }

    val uiState: StateFlow<KashirakUiState> = combine(
        dbDataFlow,
        dbMetaFlow,
        uiControlsFlow
    ) { p1, p2, ui ->
        val settings = p1.settings ?: ShopSettingsEntity(userId = currentUserId)
        val base = KashirakUiState(
            isInitialized = ui.initialized,
            settings = settings,
            products = p1.products,
            dbLowStockProducts = p2.lowStockProducts,
            customers = p1.customers,
            workers = p1.workers,
            sales = p1.sales,
            expenses = p2.expenses,
            ledgerEvents = p2.ledger,
            pendingSyncCount = p2.pendingCount,
            isOnline = p2.isOnline,
            isSyncing = ui.syncing,
            syncError = ui.syncError,
            activeTab = ui.tab,
            selectedHomeDateMillis = ui.homeDate,
            toastMessage = ui.toast,
            lowStockAlert = ui.lowStockAlert
        )
        if (base.allowed(ui.tab)) base else base.copy(activeTab = AppTab.HOME)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = KashirakUiState()
    )

    init {
        viewModelScope.launch {
            syncRepo.initializeAccountData(currentUserId)
            _isInitialized.value = true
        }

        // Listen to cloud updates in real time when signed in
        viewModelScope.launch {
            syncRepo.observeCloudChanges(currentUserId).collect { result ->
                result.onFailure { err ->
                    _syncError.value = err.message
                }
            }
        }

        // Auto-sync pending local offline changes whenever online
        viewModelScope.launch {
            combine(
                connectivityObserver.isOnline,
                dao.observePendingSyncCount(currentUserId)
            ) { online, pending -> online to pending }
                .distinctUntilChanged()
                .filter { (online, pending) -> online && pending > 0 }
                .debounce(1000L)
                .collect {
                    performSync(silent = true)
                }
        }
    }

    fun selectTab(tab: AppTab) {
        if (uiState.value.allowed(tab)) {
            _activeTab.value = tab
        }
    }

    fun showToast(msg: String) {
        _toast.value = msg
    }

    fun clearToast() {
        _toast.value = null
    }

    fun dismissLowStockAlert() {
        _lowStockAlert.value = null
    }

    private fun checkAndAlertLowStock(
        previousById: Map<String, ProductEntity>,
        updatedList: List<ProductEntity>
    ): List<ProductEntity> {
        val triggered = updatedList.filter { p ->
            if (p.deleted || p.stock > p.minStock) return@filter false
            val prev = previousById[p.id]
            prev == null || prev.stock > prev.minStock || p.stock < prev.stock || p.minStock > prev.minStock
        }
        if (triggered.isNotEmpty()) {
            _lowStockAlert.value = LowStockAlertEvent(System.currentTimeMillis(), triggered)
        }
        return triggered
    }

    fun setHomeDate(millis: Long?) {
        val t0 = Formatters.dayStartMillis()
        _selectedHomeDate.value = if (millis == null || millis >= t0) null else Formatters.dayStartMillis(millis)
    }

    fun stepHomeDate(deltaDays: Int) {
        val t0 = Formatters.dayStartMillis()
        val cur = _selectedHomeDate.value ?: t0
        val next = Formatters.addDays(cur, deltaDays)
        _selectedHomeDate.value = if (next >= t0) null else next
    }

    fun syncNow() {
        viewModelScope.launch {
            performSync(silent = false)
        }
    }

    private suspend fun performSync(silent: Boolean) {
        if (_isSyncing.value) return
        if (!connectivityObserver.isOnline.value) {
            if (!silent) showToast("لا يوجد اتصال بالإنترنت — تم الحفظ في قاعدة البيانات المحلية")
            return
        }
        _isSyncing.value = true
        _syncError.value = null
        val res = syncRepo.syncNow(currentUserId)
        _isSyncing.value = false
        res.onSuccess { count ->
            if (!silent) {
                showToast(
                    if (count > 0) "تمت مزامنة $count سجل مع حساب جوجل"
                    else "البيانات متزامنة مع حساب جوجل"
                )
            }
        }.onFailure { err ->
            _syncError.value = err.localizedMessage
            if (!silent) showToast("تعذرت المزامنة: ${err.localizedMessage ?: "خطأ في الاتصال"}")
        }
    }

    // --- Sales & POS ---

    fun recordQuickSale(
        amount: Double,
        label: String,
        method: PaymentMethod,
        payName: String,
        customerId: String?
    ) {
        if (amount <= 0.0) {
            showToast("أدخل مبلغاً صحيحاً")
            return
        }
        val state = uiState.value
        val now = System.currentTimeMillis()
        var targetCustomer: CustomerEntity? = null
        var custName = ""
        if (method == PaymentMethod.DEBT) {
            val found = state.customers.firstOrNull { it.id == customerId }
            if (found == null) {
                showToast("اختر زبوناً للبيع الآجل")
                return
            }
            targetCustomer = found.copy(
                debt = found.debt + amount,
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )
            custName = found.name
        } else if (!customerId.isNullOrBlank()) {
            custName = state.customers.firstOrNull { it.id == customerId }?.name.orEmpty()
        }

        val cleanPayName = if (method == PaymentMethod.DEBT) "" else payName.trim()
        val invNum = state.settings.nextInvoiceNumber.coerceAtLeast(1)
        val itemName = label.trim().ifBlank { "بيع سريع" }
        val sale = SaleEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            invoiceNumber = invNum,
            timestampMillis = now,
            total = amount,
            cost = 0.0,
            method = method.key,
            payName = cleanPayName,
            customerId = targetCustomer?.id ?: customerId.orEmpty(),
            customerName = custName,
            itemsJson = SaleLineItem.listToJson(listOf(SaleLineItem(itemName, 1, amount, 0.0))),
            cashierName = if (state.isOwner) "" else state.settings.activeWorkerName,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        val updatedSettings = state.settings.copy(
            nextInvoiceNumber = invNum + 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )

        viewModelScope.launch {
            dao.completeSaleTransaction(updatedSettings, sale, emptyList(), targetCustomer)
            showToast("تم تسجيل بيع سريع: ${Formatters.money(amount)} ${state.settings.currency}")
        }
    }

    fun checkoutCart(
        cartQuantities: Map<String, Int>,
        method: PaymentMethod,
        payName: String,
        customerId: String?
    ): Boolean {
        if (cartQuantities.isEmpty()) return false
        val state = uiState.value
        val now = System.currentTimeMillis()
        var total = 0.0
        var cost = 0.0
        val lineItems = mutableListOf<SaleLineItem>()
        val updatedProducts = mutableListOf<ProductEntity>()

        for ((prodId, qty) in cartQuantities) {
            if (qty <= 0) continue
            val p = state.products.firstOrNull { it.id == prodId } ?: continue
            total += p.price * qty
            cost += p.cost * qty
            lineItems.add(SaleLineItem(p.name, qty, p.price, p.cost))
            updatedProducts.add(
                p.copy(
                    stock = (p.stock - qty).coerceAtLeast(0),
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
        }
        if (lineItems.isEmpty()) return false

        var targetCustomer: CustomerEntity? = null
        var custName = ""
        if (method == PaymentMethod.DEBT) {
            val found = state.customers.firstOrNull { it.id == customerId }
            if (found == null) {
                showToast("اختر الزبون للبيع الآجل")
                return false
            }
            targetCustomer = found.copy(
                debt = found.debt + total,
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )
            custName = found.name
        } else if (!customerId.isNullOrBlank()) {
            val found = state.customers.firstOrNull { it.id == customerId }
            custName = found?.name.orEmpty()
        }

        val cleanPayName = if (method == PaymentMethod.DEBT) "" else payName.trim()
        val invNum = state.settings.nextInvoiceNumber.coerceAtLeast(1)
        val sale = SaleEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            invoiceNumber = invNum,
            timestampMillis = now,
            total = total,
            cost = cost,
            method = method.key,
            payName = cleanPayName,
            customerId = targetCustomer?.id ?: customerId.orEmpty(),
            customerName = custName,
            itemsJson = SaleLineItem.listToJson(lineItems),
            cashierName = if (state.isOwner) "" else state.settings.activeWorkerName,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        val updatedSettings = state.settings.copy(
            nextInvoiceNumber = invNum + 1,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )

        val previousById = state.products.associateBy { it.id }
        viewModelScope.launch {
            dao.completeSaleTransaction(updatedSettings, sale, updatedProducts, targetCustomer)
            val alerted = checkAndAlertLowStock(previousById, updatedProducts)
            val pmText = method.arabicLabel + if (cleanPayName.isNotBlank()) " · $cleanPayName" else ""
            val whoText = if (custName.isNotBlank()) " · $custName" else ""
            if (alerted.isNotEmpty()) {
                val names = alerted.take(2).joinToString("، ") { "${it.name} (${it.stock})" }
                showToast("تم البيع · تنبيه انخفاض المخزون: $names")
            } else {
                showToast("تم البيع: ${Formatters.money(total)} ${state.settings.currency} · $pmText$whoText")
            }
        }
        return true
    }

    fun updateInvoice(
        saleId: String,
        newItems: List<SaleLineItem>,
        newMethod: PaymentMethod,
        newPayName: String,
        newCustomerId: String
    ): Boolean {
        val state = uiState.value
        val existingSale = state.sales.firstOrNull { it.id == saleId } ?: return false
        val validItems = newItems.filter { it.qty > 0 }
        if (validItems.isEmpty()) {
            showToast("الفاتورة فارغة، استخدم حذف الفاتورة")
            return false
        }
        if (newMethod == PaymentMethod.DEBT && newCustomerId.isBlank()) {
            showToast("اختر الزبون للبيع الآجل")
            return false
        }

        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val productMap = state.products.associateBy { it.name }.toMutableMap()
            val modifiedProducts = mutableMapOf<String, ProductEntity>()

            // Revert old stock
            existingSale.items.forEach { oldItem ->
                productMap[oldItem.name]?.let { p ->
                    val updated = p.copy(
                        stock = p.stock + oldItem.qty,
                        updatedAtMillis = now,
                        syncState = SyncState.PENDING_UPSERT
                    )
                    productMap[oldItem.name] = updated
                    modifiedProducts[updated.id] = updated
                }
            }
            // Apply new stock
            validItems.forEach { newItem ->
                productMap[newItem.name]?.let { p ->
                    val updated = p.copy(
                        stock = (p.stock - newItem.qty).coerceAtLeast(0),
                        updatedAtMillis = now,
                        syncState = SyncState.PENDING_UPSERT
                    )
                    productMap[newItem.name] = updated
                    modifiedProducts[updated.id] = updated
                }
            }

            // Adjust customer debts
            val customerMap = state.customers.associateBy { it.id }.toMutableMap()
            val modifiedCustomers = mutableMapOf<String, CustomerEntity>()
            if (existingSale.method == PaymentMethod.DEBT.key && existingSale.customerId.isNotBlank()) {
                customerMap[existingSale.customerId]?.let { c ->
                    val updated = c.copy(
                        debt = c.debt - existingSale.total,
                        updatedAtMillis = now,
                        syncState = SyncState.PENDING_UPSERT
                    )
                    customerMap[c.id] = updated
                    modifiedCustomers[c.id] = updated
                }
            }
            val newTotal = validItems.sumOf { it.price * it.qty }
            val newCost = validItems.sumOf { it.cost * it.qty }
            val newCust = customerMap[newCustomerId]
            if (newMethod == PaymentMethod.DEBT && newCust != null) {
                val updated = newCust.copy(
                    debt = newCust.debt + newTotal,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
                modifiedCustomers[updated.id] = updated
            }

            val updatedSale = existingSale.copy(
                total = newTotal,
                cost = newCost,
                method = newMethod.key,
                payName = if (newMethod == PaymentMethod.DEBT) "" else newPayName.trim(),
                customerId = newCustomerId,
                customerName = newCust?.name.orEmpty(),
                itemsJson = SaleLineItem.listToJson(validItems),
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )

            val previousById = state.products.associateBy { it.id }
            val modifiedList = modifiedProducts.values.toList()
            dao.upsertProducts(modifiedList)
            dao.upsertCustomers(modifiedCustomers.values.toList())
            dao.upsertSale(updatedSale)
            checkAndAlertLowStock(previousById, modifiedList)
            showToast("تم تعديل الفاتورة")
        }
        return true
    }

    fun deleteInvoice(saleId: String) {
        val state = uiState.value
        val existingSale = state.sales.firstOrNull { it.id == saleId } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val modifiedProducts = mutableListOf<ProductEntity>()
            existingSale.items.forEach { oldItem ->
                state.products.firstOrNull { it.name == oldItem.name }?.let { p ->
                    modifiedProducts.add(
                        p.copy(
                            stock = p.stock + oldItem.qty,
                            updatedAtMillis = now,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
            }
            if (modifiedProducts.isNotEmpty()) dao.upsertProducts(modifiedProducts)

            if (existingSale.method == PaymentMethod.DEBT.key && existingSale.customerId.isNotBlank()) {
                state.customers.firstOrNull { it.id == existingSale.customerId }?.let { c ->
                    dao.upsertCustomer(
                        c.copy(
                            debt = c.debt - existingSale.total,
                            updatedAtMillis = now,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
            }

            dao.upsertSale(
                existingSale.copy(
                    deleted = true,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_DELETE
                )
            )
            showToast("تم حذف الفاتورة")
        }
    }

    // --- Products / Inventory ---

    fun addProduct(
        name: String,
        stock: Int,
        cost: Double,
        price: Double,
        barcode: String,
        expiryIso: String,
        minStock: Int = 5
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val now = System.currentTimeMillis()
        val p = ProductEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            name = cleanName,
            price = price.coerceAtLeast(0.0),
            cost = cost.coerceAtLeast(0.0),
            stock = stock.coerceAtLeast(0),
            minStock = minStock.coerceAtLeast(0),
            barcode = barcode.trim().take(64),
            expiryDate = expiryIso.trim(),
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.upsertProduct(p)
            val alerted = checkAndAlertLowStock(emptyMap(), listOf(p))
            if (alerted.isNotEmpty()) {
                showToast("تمت إضافة ${p.name} (تنبيه: الكمية عند الحد الأدنى ${p.minStock})")
            } else {
                showToast("تمت إضافة المنتج")
            }
        }
    }

    fun updateProduct(
        id: String,
        name: String,
        stock: Int,
        cost: Double,
        price: Double,
        barcode: String,
        expiryIso: String,
        minStock: Int = 5
    ) {
        val existing = uiState.value.products.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            name = name.trim().ifBlank { existing.name },
            stock = stock.coerceAtLeast(0),
            minStock = minStock.coerceAtLeast(0),
            cost = cost.coerceAtLeast(0.0),
            price = price.coerceAtLeast(0.0),
            barcode = barcode.trim().take(64),
            expiryDate = expiryIso.trim(),
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.upsertProduct(updated)
            checkAndAlertLowStock(mapOf(existing.id to existing), listOf(updated))
            showToast("تم حفظ التعديل")
        }
    }

    fun updateAllProductsMinStock(minStock: Int) {
        val cleanThreshold = minStock.coerceAtLeast(0)
        val currentProducts = uiState.value.products
        if (currentProducts.isEmpty()) {
            showToast("لا توجد منتجات بعد")
            return
        }
        val now = System.currentTimeMillis()
        val previousById = currentProducts.associateBy { it.id }
        val updated = currentProducts.map {
            it.copy(
                minStock = cleanThreshold,
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )
        }
        viewModelScope.launch {
            dao.upsertProducts(updated)
            val alerted = checkAndAlertLowStock(previousById, updated)
            if (alerted.isNotEmpty()) {
                showToast("تم ضبط الحد الأدنى ($cleanThreshold) · يوجد ${alerted.size} صنف منخفض")
            } else {
                showToast("تم ضبط الحد الأدنى للتنبيه ($cleanThreshold) لجميع المنتجات")
            }
        }
    }

    fun addStock(productId: String, amountToAdd: Int) {
        if (amountToAdd <= 0) {
            showToast("أدخل كمية صحيحة")
            return
        }
        val existing = uiState.value.products.firstOrNull { it.id == productId } ?: return
        val now = System.currentTimeMillis()
        val nextStock = existing.stock + amountToAdd
        viewModelScope.launch {
            dao.upsertProduct(
                existing.copy(
                    stock = nextStock,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            // Dismiss stale banner if all remaining products are now above minStock
            val currentAlert = _lowStockAlert.value
            if (currentAlert != null && nextStock > existing.minStock) {
                val remaining = currentAlert.products.filter { it.id != existing.id }
                _lowStockAlert.value = if (remaining.isEmpty()) null else currentAlert.copy(products = remaining)
            }
            showToast("أُضيف $amountToAdd إلى ${existing.name}")
        }
    }

    fun deleteProduct(productId: String) {
        val existing = uiState.value.products.firstOrNull { it.id == productId } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertProduct(
                existing.copy(
                    deleted = true,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_DELETE
                )
            )
            showToast("تم حذف الصنف")
        }
    }

    // --- Customers & Debts ---

    fun addCustomer(name: String, initialDebt: Double, phone: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val now = System.currentTimeMillis()
        val debtVal = initialDebt.coerceAtLeast(0.0)
        val c = CustomerEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            name = cleanName,
            debt = debtVal,
            openingDebt = debtVal,
            sinceMillis = now,
            phone = phone.trim(),
            note = "",
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.upsertCustomer(c)
            showToast("تمت إضافة الزبون")
        }
    }

    fun updateCustomer(id: String, name: String, phone: String, note: String) {
        val existing = uiState.value.customers.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertCustomer(
                existing.copy(
                    name = name.trim().ifBlank { existing.name },
                    phone = phone.trim(),
                    note = note.trim(),
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تم حفظ بيانات الزبون")
        }
    }

    fun deleteCustomer(id: String) {
        val existing = uiState.value.customers.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertCustomer(
                existing.copy(
                    deleted = true,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_DELETE
                )
            )
            showToast("تم حذف الزبون")
        }
    }

    fun payCustomerDebt(customerId: String, rawAmount: Double) {
        val existing = uiState.value.customers.firstOrNull { it.id == customerId } ?: return
        val amount = minOf(rawAmount, existing.debt)
        if (amount <= 0.0) {
            showToast("أدخل مبلغاً صحيحاً")
            return
        }
        val now = System.currentTimeMillis()
        val updatedCust = existing.copy(
            debt = existing.debt - amount,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        val event = LedgerEventEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            customerId = existing.id,
            type = "payment",
            timestampMillis = now,
            amount = amount,
            note = "",
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.applyDebtMutation(updatedCust, event)
            showToast("تم تسجيل تسديد ${Formatters.money(amount)} ${uiState.value.settings.currency}")
        }
    }

    fun addCustomerDebt(customerId: String, amount: Double, note: String) {
        val existing = uiState.value.customers.firstOrNull { it.id == customerId } ?: return
        if (amount <= 0.0) {
            showToast("أدخل مبلغاً صحيحاً")
            return
        }
        val now = System.currentTimeMillis()
        val updatedCust = existing.copy(
            debt = existing.debt + amount,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        val event = LedgerEventEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            customerId = existing.id,
            type = "debt_add",
            timestampMillis = now,
            amount = amount,
            note = note.trim(),
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.applyDebtMutation(updatedCust, event)
            showToast("تمت إضافة دين ${Formatters.money(amount)} ${uiState.value.settings.currency} على ${existing.name}")
        }
    }

    // --- Expenses ---

    fun addExpense(amount: Double, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || amount <= 0.0) {
            showToast("أدخل المبلغ والبيان بشكل صحيح")
            return
        }
        val now = System.currentTimeMillis()
        val e = ExpenseEntity(
            id = KashirakSyncRepository.newDocId(),
            userId = currentUserId,
            timestampMillis = now,
            title = cleanTitle,
            amount = amount,
            updatedAtMillis = now,
            syncState = SyncState.PENDING_UPSERT
        )
        viewModelScope.launch {
            dao.upsertExpense(e)
            showToast("تم حفظ المصروف")
        }
    }

    fun updateExpense(id: String, amount: Double, title: String, newTimestampMillis: Long) {
        val existing = uiState.value.expenses.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertExpense(
                existing.copy(
                    amount = amount.coerceAtLeast(0.0),
                    title = title.trim().ifBlank { existing.title },
                    timestampMillis = minOf(newTimestampMillis, now),
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تم تعديل المصروف")
        }
    }

    fun deleteExpense(id: String) {
        val existing = uiState.value.expenses.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertExpense(
                existing.copy(
                    deleted = true,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_DELETE
                )
            )
            showToast("تم حذف المصروف")
        }
    }

    // --- Workers & Role Switching ---

    fun addWorker(name: String, pin: String): Boolean {
        val cleanName = name.trim()
        val cleanPin = pin.trim()
        if (cleanName.isBlank()) return false
        if (!cleanPin.matches(Regex("^\\d{4}$"))) {
            showToast("الرقم السري يجب أن يكون 4 أرقام")
            return false
        }
        val state = uiState.value
        if (cleanPin == state.settings.ownerPin || state.workers.any { it.pin == cleanPin }) {
            showToast("هذا الرقم مستخدم، اختر رقماً آخر")
            return false
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertWorker(
                WorkerEntity(
                    id = KashirakSyncRepository.newDocId(),
                    userId = currentUserId,
                    name = cleanName,
                    pin = cleanPin,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تمت إضافة العامل")
        }
        return true
    }

    fun deleteWorker(id: String) {
        val existing = uiState.value.workers.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertWorker(
                existing.copy(
                    deleted = true,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_DELETE
                )
            )
            showToast("تم حذف العامل")
        }
    }

    fun verifyOwnerPinAndSwitch(pin: String): Boolean {
        val s = uiState.value.settings
        if (pin != s.ownerPin) return false
        viewModelScope.launch {
            dao.upsertSettings(s.copy(role = "owner", activeWorkerName = ""))
            _activeTab.value = AppTab.HOME
            showToast("تم الدخول كمالك")
        }
        return true
    }

    fun verifyWorkerPinAndSwitch(pin: String): Boolean {
        val s = uiState.value.settings
        val worker = uiState.value.workers.firstOrNull { it.pin == pin } ?: return false
        viewModelScope.launch {
            dao.upsertSettings(s.copy(role = "cashier", activeWorkerName = worker.name))
            _activeTab.value = AppTab.HOME
            showToast("مرحباً ${worker.name}")
        }
        return true
    }

    // --- Settings, Backup & Wipe ---

    fun saveShopSettings(
        shopName: String,
        ownerName: String,
        phone: String,
        currency: String,
        cash0: Double
    ) {
        val s = uiState.value.settings
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertSettings(
                s.copy(
                    shopName = shopName.trim().ifBlank { "المحل" },
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    currency = currency.trim().ifBlank { "₪" },
                    cash0 = cash0,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تم حفظ بيانات المحل")
        }
    }

    fun changeOwnerPin(newPin: String): Boolean {
        if (!newPin.matches(Regex("^\\d{4}$"))) {
            showToast("الرقم السري يجب أن يكون 4 أرقام")
            return false
        }
        val s = uiState.value.settings
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsertSettings(
                s.copy(
                    ownerPin = newPin,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تم تغيير الرقم السري")
        }
        return true
    }

    fun togglePermission(permKey: String, enabled: Boolean) {
        val s = uiState.value.settings
        val now = System.currentTimeMillis()
        val updated = when (permKey) {
            "items" -> s.copy(permItems = enabled)
            "reports" -> s.copy(permReports = enabled)
            "debts" -> s.copy(permDebts = enabled)
            "expenses" -> s.copy(permExpenses = enabled)
            "profit" -> s.copy(permProfit = enabled)
            "cash" -> s.copy(permCash = enabled)
            else -> s
        }.copy(updatedAtMillis = now, syncState = SyncState.PENDING_UPSERT)
        viewModelScope.launch {
            dao.upsertSettings(updated)
            showToast("تم حفظ الصلاحية")
        }
    }

    suspend fun generateBackupJson(): String = syncRepo.exportBackupJson(currentUserId)

    fun restoreFromBackupJson(json: String) {
        viewModelScope.launch {
            val res = syncRepo.importBackupJson(currentUserId, json)
            res.onSuccess { (p, s, c) ->
                _activeTab.value = AppTab.HOME
                showToast("تم استيراد النسخة ($p صنف · $s فاتورة · $c زبون)")
            }.onFailure { err ->
                showToast(err.message ?: "الملف ليس نسخة احتياطية صالحة")
            }
        }
    }

    fun wipeAppKeepCustomers() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.getAllProducts(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertProduct(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllSales(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertSale(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllExpenses(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertExpense(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllLedgerEvents(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertLedgerEvent(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllCustomers(currentUserId).filter { !it.deleted }.forEach { c ->
                dao.upsertCustomer(
                    c.copy(
                        openingDebt = c.debt,
                        sinceMillis = now,
                        updatedAtMillis = now,
                        syncState = SyncState.PENDING_UPSERT
                    )
                )
            }
            showToast("تم تفريغ التطبيق وبقي الزبائن")
        }
    }

    fun wipeCustomersOnly() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.getAllCustomers(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertCustomer(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            showToast("تم تفريغ الزبائن")
        }
    }

    fun resetAllData() {
        val now = System.currentTimeMillis()
        val s = uiState.value.settings
        viewModelScope.launch {
            dao.getAllProducts(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertProduct(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllCustomers(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertCustomer(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllSales(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertSale(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllExpenses(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertExpense(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllLedgerEvents(currentUserId).filter { !it.deleted }.forEach {
                dao.upsertLedgerEvent(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.upsertSettings(
                ShopSettingsEntity(
                    userId = currentUserId,
                    ownerPin = s.ownerPin,
                    updatedAtMillis = now,
                    syncState = SyncState.PENDING_UPSERT
                )
            )
            showToast("تم مسح البيانات")
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityObserver.unregister()
    }
}

private data class DbPart1(
    val settings: ShopSettingsEntity?,
    val products: List<ProductEntity>,
    val customers: List<CustomerEntity>,
    val workers: List<WorkerEntity>,
    val sales: List<SaleEntity>
)

private data class DbPart2(
    val expenses: List<ExpenseEntity>,
    val ledger: List<LedgerEventEntity>,
    val pendingCount: Int,
    val isOnline: Boolean,
    val lowStockProducts: List<ProductEntity>
)

private data class UiPartA(
    val tab: AppTab,
    val homeDate: Long?,
    val toast: String?,
    val lowStockAlert: LowStockAlertEvent?
)

private data class UiPart(
    val tab: AppTab,
    val homeDate: Long?,
    val toast: String?,
    val lowStockAlert: LowStockAlertEvent?,
    val syncing: Boolean,
    val syncError: String?,
    val initialized: Boolean
)
