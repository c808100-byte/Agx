package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomerEntity
import com.example.data.local.KashirakDatabase
import com.example.data.local.LedgerEventEntity
import com.example.data.local.PaymentMethod
import com.example.data.local.ProductEntity
import com.example.data.local.SaleEntity
import com.example.data.local.SaleLineItem
import com.example.data.local.ShopSettingsEntity
import com.example.data.local.SyncState
import com.example.ui.KashirakUiState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.theme.KashirakTheme
import com.example.util.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: KashirakDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KashirakDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("كاشيرك الذكي", appName)
    }

    @Test
    fun `offline room transaction updates stock debt and pending sync`() = runTest {
        val dao = db.kashirakDao()
        val uid = "test-user-1"
        val now = 1_700_000_000_000L

        val settings = ShopSettingsEntity(userId = uid, nextInvoiceNumber = 10, updatedAtMillis = now)
        val prod = ProductEntity(id = "p1", userId = uid, name = "خبز", price = 3.0, cost = 2.0, stock = 10, updatedAtMillis = now)
        val cust = CustomerEntity(id = "c1", userId = uid, name = "أبو أحمد", debt = 50.0, openingDebt = 50.0, sinceMillis = now, updatedAtMillis = now)

        dao.applySeedData(settings, listOf(prod), listOf(cust), emptyList())

        val updatedSettings = settings.copy(nextInvoiceNumber = 11, syncState = SyncState.PENDING_UPSERT)
        val updatedProd = prod.copy(stock = 8, syncState = SyncState.PENDING_UPSERT)
        val updatedCust = cust.copy(debt = 56.0, syncState = SyncState.PENDING_UPSERT)
        val sale = SaleEntity(
            id = "s1",
            userId = uid,
            invoiceNumber = 10,
            timestampMillis = now,
            total = 6.0,
            cost = 4.0,
            method = PaymentMethod.DEBT.key,
            customerId = cust.id,
            customerName = cust.name,
            itemsJson = SaleLineItem.listToJson(listOf(SaleLineItem("خبز", 2, 3.0, 2.0))),
            syncState = SyncState.PENDING_UPSERT
        )

        dao.completeSaleTransaction(updatedSettings, sale, listOf(updatedProd), updatedCust)

        assertEquals(8, dao.getProductById("p1")?.stock)
        assertEquals(56.0, dao.getCustomerById("c1")?.debt ?: 0.0, 0.001)
        assertEquals(11, dao.getSettings(uid)?.nextInvoiceNumber)
        assertTrue(dao.getAllSales(uid).size == 1)

        val paymentEvent = LedgerEventEntity(
            id = "ev1",
            userId = uid,
            customerId = cust.id,
            type = "payment",
            timestampMillis = now + 1000,
            amount = 20.0,
            syncState = SyncState.PENDING_UPSERT
        )
        dao.applyDebtMutation(updatedCust.copy(debt = 36.0), paymentEvent)
        assertEquals(36.0, dao.getCustomerById("c1")?.debt ?: 0.0, 0.001)
    }

    @Test
    fun `product entity saves and queries barcode in room`() = runTest {
        val dao = db.kashirakDao()
        val uid = "test-user-barcode"
        val prod = ProductEntity(
            id = "p-bc-1",
            userId = uid,
            name = "حليب طازج",
            price = 7.5,
            cost = 5.0,
            stock = 18,
            barcode = "6291041500213"
        )
        dao.upsertProduct(prod)

        val found = dao.findProductByBarcode(uid, "6291041500213")
        assertEquals("حليب طازج", found?.name)
        assertEquals(7.5, found?.price ?: 0.0, 0.001)
        assertEquals(18, found?.stock)
        assertEquals("6291041500213", found?.barcode)
    }

    @Test
    fun `money formatter uses 2 decimals`() {
        assertEquals("50.00", Formatters.money(50.0))
        assertEquals("12.35", Formatters.money(12.346))
    }

    @Test
    fun `inventory top search bar filters live by name and barcode`() {
        val sampleState = KashirakUiState(
            isInitialized = true,
            products = listOf(
                ProductEntity(
                    id = "p1",
                    userId = "u1",
                    name = "حليب طازج",
                    price = 8.0,
                    cost = 5.0,
                    stock = 12,
                    barcode = "62910001"
                ),
                ProductEntity(
                    id = "p2",
                    userId = "u1",
                    name = "شاي أخضر",
                    price = 15.0,
                    cost = 10.0,
                    stock = 20,
                    barcode = "62999002"
                )
            )
        )

        composeTestRule.setContent {
            KashirakTheme {
                InventoryScreen(
                    state = sampleState,
                    onAddProduct = { _, _, _, _, _, _, _ -> },
                    onUpdateProduct = { _, _, _, _, _, _, _, _ -> },
                    onAddStock = { _, _ -> },
                    onDeleteProduct = {},
                    onOpenInventoryReport = {},
                    onToast = {}
                )
            }
        }

        // Top search bar and barcode scanner button should be visible immediately at the top
        composeTestRule.onNodeWithTag("inv_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("inv_scan_search_barcode").assertIsDisplayed()

        // Search by product name
        composeTestRule.onNodeWithTag("inv_search_input").performTextInput("حليب")
        composeTestRule.onNodeWithText("نتائج البحث السريع (1)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_row_p1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_row_p2").assertDoesNotExist()

        // Clear search and search by barcode
        composeTestRule.onNodeWithTag("inv_search_clear").performClick()
        composeTestRule.onNodeWithTag("inv_search_input").performTextInput("62999")
        composeTestRule.onNodeWithText("نتائج البحث السريع (1)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_row_p2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("product_row_p1").assertDoesNotExist()
    }

    @Test
    fun `room observeLowStockProducts triggers at minStock and clears after restock`() = runTest {
        val dao = db.kashirakDao()
        val uid = "test-user-lowstock"

        val healthy = ProductEntity(
            id = "p-ok",
            userId = uid,
            name = "أرز بسمتي",
            price = 25.0,
            cost = 18.0,
            stock = 12,
            minStock = 5
        )
        val atThreshold = ProductEntity(
            id = "p-low",
            userId = uid,
            name = "زيت نباتي",
            price = 14.0,
            cost = 10.0,
            stock = 4,
            minStock = 4
        )
        val outOfStock = ProductEntity(
            id = "p-out",
            userId = uid,
            name = "سكر ناعم",
            price = 9.0,
            cost = 6.0,
            stock = 0,
            minStock = 3
        )

        dao.upsertProduct(healthy)
        dao.upsertProduct(atThreshold)
        dao.upsertProduct(outOfStock)

        val lowList = dao.observeLowStockProducts(uid).first()
        assertEquals(listOf("p-out", "p-low"), lowList.map { it.id })

        // Restock "p-low" above its minStock (4 -> 10)
        dao.upsertProduct(atThreshold.copy(stock = 10))
        val afterRestock = dao.observeLowStockProducts(uid).first()
        assertEquals(listOf("p-out"), afterRestock.map { it.id })
    }

    @Test
    fun `home screen shows low stock alert banner and opens inventory`() {
        var openedInventory = false
        val state = KashirakUiState(
            isInitialized = true,
            products = listOf(
                ProductEntity(
                    id = "p1",
                    userId = "u1",
                    name = "قهوة عربية",
                    price = 30.0,
                    cost = 20.0,
                    stock = 2,
                    minStock = 5
                )
            )
        )

        composeTestRule.setContent {
            KashirakTheme {
                HomeScreen(
                    state = state,
                    onQuickSale = { _, _, _, _, _ -> },
                    onStepDate = {},
                    onPickDate = {},
                    onOpenInvoice = {},
                    onOpenInventory = { openedInventory = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("home_low_stock_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_low_stock_action").assertIsDisplayed().performClick()
        assertTrue(openedInventory)
    }

    @Test
    fun `inventory screen shows low stock badges and threshold controls`() {
        val state = KashirakUiState(
            isInitialized = true,
            products = listOf(
                ProductEntity(
                    id = "p_out",
                    userId = "u1",
                    name = "ملح طعام",
                    price = 3.0,
                    cost = 1.5,
                    stock = 0,
                    minStock = 5
                ),
                ProductEntity(
                    id = "p_low",
                    userId = "u1",
                    name = "شاي أحمر",
                    price = 12.0,
                    cost = 8.0,
                    stock = 3,
                    minStock = 4
                )
            )
        )

        composeTestRule.setContent {
            KashirakTheme {
                InventoryScreen(
                    state = state,
                    onAddProduct = { _, _, _, _, _, _, _ -> },
                    onUpdateProduct = { _, _, _, _, _, _, _, _ -> },
                    onAddStock = { _, _ -> },
                    onDeleteProduct = {},
                    onOpenInventoryReport = {},
                    onToast = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("inv_bulk_min_stock_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("نفد المخزون (الحد الأدنى 5)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("المتبقي 3 من الحد الأدنى 4").performScrollTo().assertIsDisplayed()
    }
}
