package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.KashirakDatabase
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.authStateFlow
import com.example.data.sync.ConnectivityObserver
import com.example.data.sync.KashirakSyncRepository
import com.example.ui.AppTab
import com.example.ui.KashirakViewModel
import com.example.ui.components.KashirakBottomNav
import com.example.ui.components.KashirakHeader
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.WorkersScreen
import com.example.ui.sheets.CustomerStatementSheet
import com.example.ui.sheets.InvoiceEditSheet
import com.example.ui.sheets.OwnerRoleDialog
import com.example.ui.sheets.PinPadDialog
import com.example.ui.sheets.ReportPreviewSheet
import com.example.ui.sheets.SellSheet
import com.example.ui.sheets.SettingsSheet
import com.example.ui.sheets.SyncAccountSheet
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.KashirakTheme
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPrimary
import com.example.util.PdfReportGenerator
import com.example.util.StockAlertNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KashirakTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {
            // Firebase AuthStateListener updates currentUser automatically
        })
    } else {
        key(user.uid) {
            AuthenticatedPosApp(user = user)
        }
    }
}

@Composable
private fun AuthenticatedPosApp(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val vm: KashirakViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = KashirakDatabase.getInstance(app)
                val connectivity = ConnectivityObserver(app)
                val syncRepo = KashirakSyncRepository(app, db.kashirakDao(), connectivity)
                KashirakViewModel(
                    dao = db.kashirakDao(),
                    syncRepo = syncRepo,
                    connectivityObserver = connectivity,
                    currentUserId = user.uid
                )
            }
        }
    )

    val state by vm.uiState.collectAsStateWithLifecycle()

    var showSellSheet by remember { mutableStateOf(false) }
    var editingSaleId by remember { mutableStateOf<String?>(null) }
    var statementCustomerId by remember { mutableStateOf<String?>(null) }
    var reportKind by remember { mutableStateOf<String?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSyncSheet by remember { mutableStateOf(false) }
    var showOwnerRoleDialog by remember { mutableStateOf(false) }
    var pinDialogMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        StockAlertNotifier.ensureChannel(context)
    }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            delay(2400L)
            vm.clearToast()
        }
    }

    LaunchedEffect(state.lowStockAlert?.id) {
        val alert = state.lowStockAlert
        if (alert != null && alert.products.isNotEmpty()) {
            StockAlertNotifier.notifyLowStock(
                context = context,
                triggeredProducts = alert.products,
                totalLowCount = state.lowStockProducts().size
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                KashirakHeader(
                    state = state,
                    onSyncClick = { vm.syncNow() },
                    onAccountPillClick = { showSyncSheet = true },
                    onRoleClick = {
                        if (state.isOwner) {
                            showOwnerRoleDialog = true
                        } else {
                            pinDialogMode = "owner"
                        }
                    }
                )
            },
            bottomBar = {
                KashirakBottomNav(
                    state = state,
                    onSelectTab = { vm.selectTab(it) },
                    onOpenSell = { showSellSheet = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 540.dp)
                        .fillMaxSize()
                ) {
                    if (!state.isInitialized) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    } else {
                        when (state.activeTab) {
                            AppTab.HOME -> HomeScreen(
                                state = state,
                                onQuickSale = { amt, label, method, payName, custId ->
                                    vm.recordQuickSale(amt, label, method, payName, custId)
                                },
                                onStepDate = { delta -> vm.stepHomeDate(delta) },
                                onPickDate = { millis -> vm.setHomeDate(millis) },
                                onOpenInvoice = { sale ->
                                    if (state.isOwner) {
                                        editingSaleId = sale.id
                                    } else {
                                        vm.showToast("تعديل الفاتورة للمالك فقط")
                                    }
                                },
                                onOpenInventory = { vm.selectTab(AppTab.INVENTORY) }
                            )

                            AppTab.DEBTS -> DebtsScreen(
                                state = state,
                                onAddCustomer = { name, initDebt, phone ->
                                    vm.addCustomer(name, initDebt, phone)
                                },
                                onPayDebt = { cid, amt -> vm.payCustomerDebt(cid, amt) },
                                onAddDebt = { cid, amt, note -> vm.addCustomerDebt(cid, amt, note) },
                                onOpenCustomerStatement = { cust -> statementCustomerId = cust.id },
                                onOpenAllCustomersReport = { reportKind = "customers" }
                            )

                            AppTab.EXPENSES -> ExpensesScreen(
                                state = state,
                                onAddExpense = { amt, title -> vm.addExpense(amt, title) },
                                onUpdateExpense = { id, amt, title, ts ->
                                    vm.updateExpense(id, amt, title, ts)
                                },
                                onDeleteExpense = { id -> vm.deleteExpense(id) },
                                onOpenExpensesReport = { reportKind = "expenses" }
                            )

                            AppTab.REPORTS -> ReportsScreen(
                                state = state,
                                onOpenInvoice = { sale ->
                                    if (state.isOwner) {
                                        editingSaleId = sale.id
                                    } else {
                                        vm.showToast("تعديل الفاتورة للمالك فقط")
                                    }
                                }
                            )

                            AppTab.INVENTORY -> InventoryScreen(
                                state = state,
                                onAddProduct = { name, stock, cost, price, barcode, exp, minStock ->
                                    vm.addProduct(name, stock, cost, price, barcode, exp, minStock)
                                },
                                onUpdateProduct = { id, name, stock, cost, price, barcode, exp, minStock ->
                                    vm.updateProduct(id, name, stock, cost, price, barcode, exp, minStock)
                                },
                                onAddStock = { id, qty -> vm.addStock(id, qty) },
                                onDeleteProduct = { id -> vm.deleteProduct(id) },
                                onOpenInventoryReport = { reportKind = "inventory" },
                                onUpdateAllMinStock = { minStock -> vm.updateAllProductsMinStock(minStock) },
                                onToast = { vm.showToast(it) }
                            )

                            AppTab.MORE -> MoreScreen(
                                onOpenWorkers = { vm.selectTab(AppTab.WORKERS) },
                                onOpenSettings = { showSettingsSheet = true },
                                onOpenSyncSheet = { showSyncSheet = true }
                            )

                            AppTab.WORKERS -> WorkersScreen(
                                state = state,
                                onBack = { vm.selectTab(AppTab.MORE) },
                                onAddWorker = { name, pin -> vm.addWorker(name, pin) },
                                onDeleteWorker = { id -> vm.deleteWorker(id) }
                            )
                        }
                    }
                }
            }
        }

        // Full-screen overlay sheets (rendered above header & bottom nav)
        if (showSellSheet) {
            SellSheet(
                state = state,
                onClose = { showSellSheet = false },
                onCheckout = { cart, method, payName, custId ->
                    vm.checkoutCart(cart, method, payName, custId)
                },
                onToast = { vm.showToast(it) }
            )
        }

        statementCustomerId?.let { cid ->
            val customer = state.customers.firstOrNull { it.id == cid }
            if (customer != null) {
                CustomerStatementSheet(
                    customer = customer,
                    state = state,
                    onClose = { statementCustomerId = null },
                    onUpdateCustomer = { name, phone, note ->
                        vm.updateCustomer(customer.id, name, phone, note)
                    },
                    onDeleteCustomer = { vm.deleteCustomer(customer.id) },
                    onOpenSaleById = { saleId ->
                        if (state.isOwner) {
                            statementCustomerId = null
                            editingSaleId = saleId
                        } else {
                            vm.showToast("تعديل الفاتورة للمالك فقط")
                        }
                    },
                    onToast = { vm.showToast(it) }
                )
            } else {
                statementCustomerId = null
            }
        }

        editingSaleId?.let { sid ->
            val sale = state.sales.firstOrNull { it.id == sid }
            if (sale != null) {
                InvoiceEditSheet(
                    sale = sale,
                    state = state,
                    onClose = { editingSaleId = null },
                    onSave = { newItems, method, payName, custId ->
                        vm.updateInvoice(sale.id, newItems, method, payName, custId)
                    },
                    onDelete = { vm.deleteInvoice(sale.id) }
                )
            } else {
                editingSaleId = null
            }
        }

        reportKind?.let { kind ->
            val reportData = remember(kind, state.products, state.expenses, state.customers, state.sales, state.ledgerEvents) {
                when (kind) {
                    "inventory" -> PdfReportGenerator.buildInventoryReportData(state.products)
                    "expenses" -> PdfReportGenerator.buildExpensesReportData(state.expenses)
                    else -> PdfReportGenerator.buildAllCustomersReportData(state.customers) { c ->
                        val stmt = state.customerStatement(c)
                        stmt.totalPurchases to stmt.totalPaid
                    }
                }
            }
            ReportPreviewSheet(
                data = reportData,
                shopName = state.settings.shopName,
                onClose = { reportKind = null },
                onToast = { vm.showToast(it) }
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                state = state,
                userEmail = user.email,
                onClose = { showSettingsSheet = false },
                onSaveShop = { name, owner, phone, cur, cash0 ->
                    vm.saveShopSettings(name, owner, phone, cur, cash0)
                },
                onChangeOwnerPin = { vm.changeOwnerPin(it) },
                onTogglePermission = { key, enabled -> vm.togglePermission(key, enabled) },
                onOpenSyncSheet = {
                    showSettingsSheet = false
                    showSyncSheet = true
                },
                onGenerateBackupJson = { vm.generateBackupJson() },
                onImportBackupJson = { json -> vm.restoreFromBackupJson(json) },
                onWipeKeepCustomers = { vm.wipeAppKeepCustomers() },
                onWipeCustomersOnly = { vm.wipeCustomersOnly() },
                onResetAll = { vm.resetAllData() },
                onToast = { vm.showToast(it) }
            )
        }

        if (showSyncSheet) {
            SyncAccountSheet(
                state = state,
                userEmail = user.email,
                userDisplayName = user.displayName,
                onClose = { showSyncSheet = false },
                onSyncNow = { vm.syncNow() },
                onSignOut = {
                    showSyncSheet = false
                    GoogleAuthManager.signOut(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }

        // Dialogs (Role choice & PIN keypad)
        if (showOwnerRoleDialog) {
            OwnerRoleDialog(
                onOpenSettings = {
                    showOwnerRoleDialog = false
                    showSettingsSheet = true
                },
                onSwitchToCashier = {
                    showOwnerRoleDialog = false
                    if (state.workers.isEmpty()) {
                        vm.showToast("أضف عاملاً من المزيد ← العمال أولاً")
                    } else {
                        pinDialogMode = "cashier"
                    }
                },
                onDismiss = { showOwnerRoleDialog = false }
            )
        }

        pinDialogMode?.let { mode ->
            PinPadDialog(
                title = if (mode == "owner") "أدخل الرقم السري للمالك" else "أدخل الرقم السري للعامل",
                onDismiss = { pinDialogMode = null },
                onVerifyPin = { pin ->
                    if (mode == "owner") vm.verifyOwnerPinAndSwitch(pin)
                    else vm.verifyWorkerPinAndSwitch(pin)
                },
                onWrongPin = {
                    vm.showToast(if (mode == "owner") "الرقم السري غير صحيح" else "لا يوجد عامل بهذا الرقم")
                }
            )
        }

        // Floating low-stock warning banner when products drop to/below minimum threshold
        val alertEvent = state.lowStockAlert
        AnimatedVisibility(
            visible = alertEvent != null && alertEvent.products.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 74.dp, start = 16.dp, end = 16.dp)
        ) {
            val ext = LocalKashirakColors.current
            val alertItems = alertEvent?.products.orEmpty()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.warnBg,
                border = BorderStroke(1.5.dp, ext.warnFg),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .testTag("low_stock_alert_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.cd_low_stock_alert),
                        tint = ext.warnFg,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تنبيه: مخزون وصل للحد الأدنى",
                            color = ext.warnFg,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = alertItems.take(2).joinToString("، ") { "${it.name} (${it.stock}/${it.minStock})" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.allowed(AppTab.INVENTORY) && state.activeTab != AppTab.INVENTORY) {
                        TextButton(
                            onClick = {
                                vm.dismissLowStockAlert()
                                vm.selectTab(AppTab.INVENTORY)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("الجرد", color = NavyPrimary, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    IconButton(
                        onClick = { vm.dismissLowStockAlert() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("low_stock_alert_dismiss")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = ext.warnFg
                        )
                    }
                }
            }
        }

        // Floating toast overlay above bottom nav & visible over sheets
        AnimatedVisibility(
            visible = state.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = NavyPrimary.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, GoldAccent),
                shadowElevation = 6.dp,
                modifier = Modifier.testTag("toast_banner")
            ) {
                Text(
                    text = state.toastMessage.orEmpty(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
