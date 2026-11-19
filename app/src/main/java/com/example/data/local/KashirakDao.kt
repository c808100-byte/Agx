package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KashirakDao {

    // --- Observe live UI streams (non-deleted rows for current userId) ---

    @Query("SELECT * FROM shop_settings WHERE userId = :uid LIMIT 1")
    fun observeSettings(uid: String): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM products WHERE userId = :uid AND deleted = 0 ORDER BY name ASC")
    fun observeProducts(uid: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE userId = :uid AND deleted = 0 AND stock <= minStock ORDER BY stock ASC, name ASC")
    fun observeLowStockProducts(uid: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM customers WHERE userId = :uid AND deleted = 0 ORDER BY name ASC")
    fun observeCustomers(uid: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM workers WHERE userId = :uid AND deleted = 0 ORDER BY name ASC")
    fun observeWorkers(uid: String): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM sales WHERE userId = :uid AND deleted = 0 ORDER BY timestampMillis ASC")
    fun observeSales(uid: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM expenses WHERE userId = :uid AND deleted = 0 ORDER BY timestampMillis ASC")
    fun observeExpenses(uid: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM ledger_events WHERE userId = :uid AND deleted = 0 ORDER BY timestampMillis ASC")
    fun observeLedgerEvents(uid: String): Flow<List<LedgerEventEntity>>

    @Query(
        """
        SELECT 
          (SELECT COUNT(*) FROM shop_settings WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM products WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM customers WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM workers WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM sales WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM expenses WHERE userId = :uid AND syncState != 0) +
          (SELECT COUNT(*) FROM ledger_events WHERE userId = :uid AND syncState != 0)
        """
    )
    fun observePendingSyncCount(uid: String): Flow<Int>

    // --- Single-shot lookups & sync queries ---

    @Query("SELECT * FROM shop_settings WHERE userId = :uid LIMIT 1")
    suspend fun getSettings(uid: String): ShopSettingsEntity?

    @Query("SELECT * FROM products WHERE userId = :uid")
    suspend fun getAllProducts(uid: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE userId = :uid AND barcode = :barcode AND deleted = 0 LIMIT 1")
    suspend fun findProductByBarcode(uid: String, barcode: String): ProductEntity?

    @Query("SELECT * FROM customers WHERE userId = :uid")
    suspend fun getAllCustomers(uid: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM workers WHERE userId = :uid")
    suspend fun getAllWorkers(uid: String): List<WorkerEntity>

    @Query("SELECT * FROM sales WHERE userId = :uid")
    suspend fun getAllSales(uid: String): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): SaleEntity?

    @Query("SELECT * FROM expenses WHERE userId = :uid")
    suspend fun getAllExpenses(uid: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM ledger_events WHERE userId = :uid")
    suspend fun getAllLedgerEvents(uid: String): List<LedgerEventEntity>

    // --- Upserts ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: ShopSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorker(worker: WorkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkers(workers: List<WorkerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSales(sales: List<SaleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(expenses: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedgerEvent(event: LedgerEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedgerEvents(events: List<LedgerEventEntity>)

    // --- Atomic domain transactions ---

    @Transaction
    suspend fun completeSaleTransaction(
        settings: ShopSettingsEntity,
        sale: SaleEntity,
        updatedProducts: List<ProductEntity>,
        updatedCustomer: CustomerEntity?
    ) {
        upsertSettings(settings)
        upsertSale(sale)
        if (updatedProducts.isNotEmpty()) upsertProducts(updatedProducts)
        if (updatedCustomer != null) upsertCustomer(updatedCustomer)
    }

    @Transaction
    suspend fun applySeedData(
        settings: ShopSettingsEntity,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        workers: List<WorkerEntity>
    ) {
        upsertSettings(settings)
        if (products.isNotEmpty()) upsertProducts(products)
        if (customers.isNotEmpty()) upsertCustomers(customers)
        if (workers.isNotEmpty()) upsertWorkers(workers)
    }

    @Transaction
    suspend fun applyDebtMutation(
        customer: CustomerEntity,
        event: LedgerEventEntity
    ) {
        upsertCustomer(customer)
        upsertLedgerEvent(event)
    }

    @Transaction
    suspend fun markAllSyncedExceptLocalDeletes(uid: String, nowMillis: Long) {
        // Handled per-item by sync engine after cloud ack
    }
}
