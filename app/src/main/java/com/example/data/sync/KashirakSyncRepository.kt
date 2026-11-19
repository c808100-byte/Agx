package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.CustomerEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.KashirakDao
import com.example.data.local.LedgerEventEntity
import com.example.data.local.ProductEntity
import com.example.data.local.SaleEntity
import com.example.data.local.SaleLineItem
import com.example.data.local.ShopSettingsEntity
import com.example.data.local.SyncState
import com.example.data.local.WorkerEntity
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class KashirakSyncRepository(
    private val dao: KashirakDao,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val connectivityObserver: ConnectivityObserver
) {
    constructor(
        context: Context,
        dao: KashirakDao,
        connectivityObserver: ConnectivityObserver
    ) : this(
        dao = dao,
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        connectivityObserver = connectivityObserver
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "User must be signed in with Google before syncing." }
        return uid
    }

    /**
     * Initializes local Room DB for the signed-in Google account.
     * If local DB has no data for this UID, checks Firestore first (if online) to restore cloud data;
     * otherwise seeds starter shop data locally in Room so the app works immediately offline.
     */
    suspend fun initializeAccountData(uid: String) {
        val existingSettings = dao.getSettings(uid)
        if (existingSettings != null) return

        var restoredFromCloud = false
        if (connectivityObserver.isOnline.value) {
            try {
                val userDoc = db.collection("users").document(uid).get(Source.SERVER).await()
                if (userDoc.exists()) {
                    mergeSettingsFromSnapshot(uid, userDoc)
                    pullAllCollectionsFromServer(uid)
                    restoredFromCloud = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(e, OperationType.GET, "users/$uid")
                }
            }
        }

        if (!restoredFromCloud && dao.getSettings(uid) == null) {
            val now = System.currentTimeMillis()
            val starterSettings = ShopSettingsEntity(
                userId = uid,
                shopName = "جبر",
                ownerName = "احمد",
                phone = "",
                currency = "₪",
                cash0 = 50.0,
                ownerPin = "1234",
                role = "owner",
                activeWorkerName = "",
                permItems = false,
                permReports = false,
                permDebts = true,
                permExpenses = false,
                permProfit = false,
                permCash = false,
                nextInvoiceNumber = 10,
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )
            val starterProducts = listOf(
                ProductEntity(id = newDocId(), userId = uid, name = "ماء 0.5 لتر", price = 2.0, cost = 1.0, stock = 40, updatedAtMillis = now),
                ProductEntity(id = newDocId(), userId = uid, name = "خبز", price = 3.0, cost = 2.0, stock = 25, updatedAtMillis = now),
                ProductEntity(id = newDocId(), userId = uid, name = "عصير", price = 5.0, cost = 3.0, stock = 30, updatedAtMillis = now),
                ProductEntity(id = newDocId(), userId = uid, name = "شوكولاتة", price = 4.0, cost = 2.5, stock = 20, updatedAtMillis = now)
            )
            val starterCustomers = listOf(
                CustomerEntity(
                    id = newDocId(),
                    userId = uid,
                    name = "أبو أحمد",
                    debt = 50.0,
                    openingDebt = 50.0,
                    sinceMillis = now,
                    updatedAtMillis = now
                )
            )
            val starterWorkers = listOf(
                WorkerEntity(
                    id = newDocId(),
                    userId = uid,
                    name = "الكاشير",
                    pin = "1111",
                    updatedAtMillis = now
                )
            )
            dao.applySeedData(starterSettings, starterProducts, starterCustomers, starterWorkers)
        }
    }

    /**
     * Real-time Firestore listener stream that merges cloud updates into Room when signed in.
     * Emits Unit on each processed cloud change, or catches errors cleanly so Compose never crashes.
     */
    fun observeCloudChanges(uid: String): Flow<Result<Unit>> = flow {
        val currentUid = requireUserId()
        check(currentUid == uid) { "Active user mismatch" }
        val userDocRef = db.collection("users").document(uid)

        kotlinx.coroutines.flow.merge(
            userDocRef.snapshots().map { snap ->
                if (snap.exists() && !snap.metadata.hasPendingWrites()) {
                    mergeSettingsFromSnapshot(uid, snap)
                }
            },
            userDocRef.collection("products").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeProductFromSnapshot(uid, it) }
                }
            },
            userDocRef.collection("customers").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeCustomerFromSnapshot(uid, it) }
                }
            },
            userDocRef.collection("workers").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeWorkerFromSnapshot(uid, it) }
                }
            },
            userDocRef.collection("sales").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeSaleFromSnapshot(uid, it) }
                }
            },
            userDocRef.collection("expenses").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeExpenseFromSnapshot(uid, it) }
                }
            },
            userDocRef.collection("ledgerEvents").snapshots().map { q ->
                if (!q.metadata.hasPendingWrites()) {
                    q.documents.forEach { mergeLedgerFromSnapshot(uid, it) }
                }
            }
        ).map<Unit, Result<Unit>> {
            Result.success(Unit)
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val ex = firestoreError ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(ex, OperationType.LIST, "users/$uid")
            }
            emit(Result.failure(firestoreError ?: error))
        }.collect { result ->
            emit(result)
        }
    }

    /**
     * Pushes all local pending mutations to Firestore and pulls latest cloud state.
     */
    suspend fun syncNow(expectedUid: String): Result<Int> {
        return try {
            val uid = requireUserId()
            check(uid == expectedUid) { "Signed-in user changed." }
            if (!connectivityObserver.isOnline.value) {
                return Result.failure(IllegalStateException("لا يوجد اتصال بالإنترنت حالياً"))
            }

            var syncedCount = 0
            val userDocRef = db.collection("users").document(uid)

            // 1. Sync ShopSettings
            dao.getSettings(uid)?.let { s ->
                if (s.syncState != SyncState.SYNCED) {
                    val createFields = mapOf(
                        "userId" to uid,
                        "shopName" to s.shopName.take(80).ifBlank { "المحل" },
                        "ownerName" to s.ownerName.take(80),
                        "phone" to s.phone.take(30),
                        "currency" to s.currency.take(10).ifBlank { "₪" },
                        "cash0" to s.cash0,
                        "ownerPin" to s.ownerPin.take(4).padEnd(4, '0'),
                        "permItems" to s.permItems,
                        "permReports" to s.permReports,
                        "permDebts" to s.permDebts,
                        "permExpenses" to s.permExpenses,
                        "permProfit" to s.permProfit,
                        "permCash" to s.permCash,
                        "nextInvoiceNumber" to s.nextInvoiceNumber.coerceAtLeast(1),
                        "clientUpdatedAt" to s.updatedAtMillis,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    val updateFields = createFields.toMutableMap().apply {
                        remove("userId")
                        remove("createdAt")
                    }
                    upsertDocument(userDocRef, createFields, updateFields)
                    dao.upsertSettings(
                        s.copy(
                            syncState = SyncState.SYNCED,
                            lastSyncedAtMillis = System.currentTimeMillis()
                        )
                    )
                    syncedCount++
                }
            }

            // 2. Sync Products
            dao.getAllProducts(uid).filter { it.syncState != SyncState.SYNCED }.forEach { p ->
                val ref = userDocRef.collection("products").document(p.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "name" to p.name.take(120).ifBlank { "صنف" },
                    "price" to p.price.coerceAtLeast(0.0),
                    "cost" to p.cost.coerceAtLeast(0.0),
                    "stock" to p.stock.coerceAtLeast(0),
                    "minStock" to p.minStock.coerceAtLeast(0),
                    "barcode" to p.barcode.take(64),
                    "expiryDate" to p.expiryDate.take(20),
                    "deleted" to p.deleted,
                    "clientUpdatedAt" to p.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertProduct(p.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // 3. Sync Customers
            dao.getAllCustomers(uid).filter { it.syncState != SyncState.SYNCED }.forEach { c ->
                val ref = userDocRef.collection("customers").document(c.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "name" to c.name.take(120).ifBlank { "زبون" },
                    "debt" to c.debt,
                    "openingDebt" to c.openingDebt,
                    "sinceMillis" to c.sinceMillis.coerceAtLeast(0L),
                    "phone" to c.phone.take(30),
                    "note" to c.note.take(300),
                    "deleted" to c.deleted,
                    "clientUpdatedAt" to c.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertCustomer(c.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // 4. Sync Workers
            dao.getAllWorkers(uid).filter { it.syncState != SyncState.SYNCED }.forEach { w ->
                val ref = userDocRef.collection("workers").document(w.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "name" to w.name.take(100).ifBlank { "عامل" },
                    "pin" to w.pin.take(4).padEnd(4, '1'),
                    "deleted" to w.deleted,
                    "clientUpdatedAt" to w.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertWorker(w.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // 5. Sync Sales
            dao.getAllSales(uid).filter { it.syncState != SyncState.SYNCED }.forEach { s ->
                val ref = userDocRef.collection("sales").document(s.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "invoiceNumber" to s.invoiceNumber.coerceAtLeast(1),
                    "timestampMillis" to s.timestampMillis.coerceAtLeast(0L),
                    "total" to s.total.coerceAtLeast(0.0),
                    "cost" to s.cost.coerceAtLeast(0.0),
                    "method" to s.method,
                    "payName" to s.payName.take(80),
                    "customerId" to s.customerId.take(128),
                    "customerName" to s.customerName.take(120),
                    "items" to s.items.take(200).map { it.toFirestoreMap() },
                    "cashierName" to s.cashierName.take(100),
                    "deleted" to s.deleted,
                    "clientUpdatedAt" to s.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertSale(s.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // 6. Sync Expenses
            dao.getAllExpenses(uid).filter { it.syncState != SyncState.SYNCED }.forEach { ex ->
                val ref = userDocRef.collection("expenses").document(ex.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "timestampMillis" to ex.timestampMillis.coerceAtLeast(0L),
                    "title" to ex.title.take(150).ifBlank { "مصروف" },
                    "amount" to ex.amount.coerceAtLeast(0.0),
                    "deleted" to ex.deleted,
                    "clientUpdatedAt" to ex.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertExpense(ex.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // 7. Sync LedgerEvents
            dao.getAllLedgerEvents(uid).filter { it.syncState != SyncState.SYNCED }.forEach { ev ->
                val ref = userDocRef.collection("ledgerEvents").document(ev.id)
                val createFields = mapOf(
                    "userId" to uid,
                    "customerId" to ev.customerId.take(128),
                    "type" to ev.type,
                    "timestampMillis" to ev.timestampMillis.coerceAtLeast(0L),
                    "amount" to ev.amount.coerceAtLeast(0.0),
                    "note" to ev.note.take(200),
                    "deleted" to ev.deleted,
                    "clientUpdatedAt" to ev.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val updateFields = createFields.toMutableMap().apply {
                    remove("userId")
                    remove("createdAt")
                }
                upsertDocument(ref, createFields, updateFields)
                dao.upsertLedgerEvent(ev.copy(syncState = SyncState.SYNCED))
                syncedCount++
            }

            // Pull latest from server & update lastSyncedAt
            pullAllCollectionsFromServer(uid)
            dao.getSettings(uid)?.let { s ->
                dao.upsertSettings(s.copy(lastSyncedAtMillis = System.currentTimeMillis()))
            }

            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$expectedUid")
            Result.failure(e)
        }
    }

    private suspend fun upsertDocument(
        ref: com.google.firebase.firestore.DocumentReference,
        createPayload: Map<String, Any>,
        updatePayload: Map<String, Any>
    ) {
        val snap = ref.get(Source.SERVER).await()
        if (snap.exists()) {
            ref.update(updatePayload).await()
        } else {
            ref.set(createPayload).await()
        }
    }

    private suspend fun pullAllCollectionsFromServer(uid: String) {
        val base = db.collection("users").document(uid)
        val userSnap = base.get(Source.SERVER).await()
        if (userSnap.exists()) mergeSettingsFromSnapshot(uid, userSnap)

        base.collection("products").get(Source.SERVER).await().documents.forEach {
            mergeProductFromSnapshot(uid, it)
        }
        base.collection("customers").get(Source.SERVER).await().documents.forEach {
            mergeCustomerFromSnapshot(uid, it)
        }
        base.collection("workers").get(Source.SERVER).await().documents.forEach {
            mergeWorkerFromSnapshot(uid, it)
        }
        base.collection("sales").get(Source.SERVER).await().documents.forEach {
            mergeSaleFromSnapshot(uid, it)
        }
        base.collection("expenses").get(Source.SERVER).await().documents.forEach {
            mergeExpenseFromSnapshot(uid, it)
        }
        base.collection("ledgerEvents").get(Source.SERVER).await().documents.forEach {
            mergeLedgerFromSnapshot(uid, it)
        }
    }

    private suspend fun mergeSettingsFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getSettings(uid)
        if (local != null && local.syncState != SyncState.SYNCED && local.updatedAtMillis >= remoteUpdated) {
            return
        }
        if (local != null && remoteUpdated <= local.updatedAtMillis && local.syncState == SyncState.SYNCED) {
            return
        }
        val merged = ShopSettingsEntity(
            userId = uid,
            shopName = (data["shopName"] as? String)?.ifBlank { "المحل" } ?: local?.shopName ?: "جبر",
            ownerName = data["ownerName"] as? String ?: local?.ownerName ?: "",
            phone = data["phone"] as? String ?: local?.phone ?: "",
            currency = (data["currency"] as? String)?.ifBlank { "₪" } ?: local?.currency ?: "₪",
            cash0 = (data["cash0"] as? Number)?.toDouble() ?: local?.cash0 ?: 0.0,
            ownerPin = (data["ownerPin"] as? String)?.take(4) ?: local?.ownerPin ?: "1234",
            role = local?.role ?: "owner",
            activeWorkerName = local?.activeWorkerName ?: "",
            permItems = data["permItems"] as? Boolean ?: false,
            permReports = data["permReports"] as? Boolean ?: false,
            permDebts = data["permDebts"] as? Boolean ?: true,
            permExpenses = data["permExpenses"] as? Boolean ?: false,
            permProfit = data["permProfit"] as? Boolean ?: false,
            permCash = data["permCash"] as? Boolean ?: false,
            nextInvoiceNumber = maxOf(
                (data["nextInvoiceNumber"] as? Number)?.toInt() ?: 10,
                local?.nextInvoiceNumber ?: 10
            ),
            updatedAtMillis = remoteUpdated,
            syncState = SyncState.SYNCED,
            lastSyncedAtMillis = System.currentTimeMillis()
        )
        dao.upsertSettings(merged)
    }

    private suspend fun mergeProductFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getProductById(doc.id)
        if (local != null && local.updatedAtMillis >= remoteUpdated) return
        dao.upsertProduct(
            ProductEntity(
                id = doc.id,
                userId = uid,
                name = data["name"] as? String ?: "",
                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                cost = (data["cost"] as? Number)?.toDouble() ?: 0.0,
                stock = (data["stock"] as? Number)?.toInt() ?: 0,
                minStock = (data["minStock"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 5,
                barcode = data["barcode"] as? String ?: "",
                expiryDate = data["expiryDate"] as? String ?: "",
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    private suspend fun mergeCustomerFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getCustomerById(doc.id)
        if (local != null && local.updatedAtMillis >= remoteUpdated) return
        dao.upsertCustomer(
            CustomerEntity(
                id = doc.id,
                userId = uid,
                name = data["name"] as? String ?: "",
                debt = (data["debt"] as? Number)?.toDouble() ?: 0.0,
                openingDebt = (data["openingDebt"] as? Number)?.toDouble() ?: 0.0,
                sinceMillis = (data["sinceMillis"] as? Number)?.toLong() ?: 0L,
                phone = data["phone"] as? String ?: "",
                note = data["note"] as? String ?: "",
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    private suspend fun mergeWorkerFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getAllWorkers(uid).firstOrNull { it.id == doc.id }
        if (local != null && local.updatedAtMillis >= remoteUpdated) return
        dao.upsertWorker(
            WorkerEntity(
                id = doc.id,
                userId = uid,
                name = data["name"] as? String ?: "",
                pin = data["pin"] as? String ?: "1111",
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    private suspend fun mergeSaleFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getSaleById(doc.id)
        if (local != null && local.updatedAtMillis >= remoteUpdated) return

        val rawItems = (data["items"] as? List<*>)?.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            SaleLineItem(
                name = m["name"] as? String ?: "",
                qty = (m["qty"] as? Number)?.toInt() ?: 1,
                price = (m["price"] as? Number)?.toDouble() ?: 0.0,
                cost = (m["cost"] as? Number)?.toDouble() ?: 0.0
            )
        } ?: emptyList()

        dao.upsertSale(
            SaleEntity(
                id = doc.id,
                userId = uid,
                invoiceNumber = (data["invoiceNumber"] as? Number)?.toInt() ?: 1,
                timestampMillis = (data["timestampMillis"] as? Number)?.toLong() ?: 0L,
                total = (data["total"] as? Number)?.toDouble() ?: 0.0,
                cost = (data["cost"] as? Number)?.toDouble() ?: 0.0,
                method = data["method"] as? String ?: "cash",
                payName = data["payName"] as? String ?: "",
                customerId = data["customerId"] as? String ?: "",
                customerName = data["customerName"] as? String ?: "",
                itemsJson = SaleLineItem.listToJson(rawItems),
                cashierName = data["cashierName"] as? String ?: "",
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    private suspend fun mergeExpenseFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getExpenseById(doc.id)
        if (local != null && local.updatedAtMillis >= remoteUpdated) return
        dao.upsertExpense(
            ExpenseEntity(
                id = doc.id,
                userId = uid,
                timestampMillis = (data["timestampMillis"] as? Number)?.toLong() ?: 0L,
                title = data["title"] as? String ?: "",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    private suspend fun mergeLedgerFromSnapshot(uid: String, doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val remoteUpdated = (data["clientUpdatedAt"] as? Number)?.toLong() ?: 0L
        val local = dao.getAllLedgerEvents(uid).firstOrNull { it.id == doc.id }
        if (local != null && local.updatedAtMillis >= remoteUpdated) return
        dao.upsertLedgerEvent(
            LedgerEventEntity(
                id = doc.id,
                userId = uid,
                customerId = data["customerId"] as? String ?: "",
                type = data["type"] as? String ?: "payment",
                timestampMillis = (data["timestampMillis"] as? Number)?.toLong() ?: 0L,
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                note = data["note"] as? String ?: "",
                updatedAtMillis = remoteUpdated,
                deleted = data["deleted"] as? Boolean ?: false,
                syncState = SyncState.SYNCED
            )
        )
    }

    /**
     * Exports all data in the exact JSON backup format compatible with the HTML Kashirak app.
     */
    suspend fun exportBackupJson(uid: String): String {
        val settings = dao.getSettings(uid) ?: ShopSettingsEntity(userId = uid)
        val products = dao.getAllProducts(uid).filter { !it.deleted }
        val customers = dao.getAllCustomers(uid).filter { !it.deleted }
        val workers = dao.getAllWorkers(uid).filter { !it.deleted }
        val sales = dao.getAllSales(uid).filter { !it.deleted }
        val expenses = dao.getAllExpenses(uid).filter { !it.deleted }
        val ledger = dao.getAllLedgerEvents(uid).filter { !it.deleted }

        val sObj = JSONObject().apply {
            put("shop", settings.shopName)
            put("ownerName", settings.ownerName)
            put("phone", settings.phone)
            put("cur", settings.currency)
            put("cash0", settings.cash0)
            put("ownerPin", settings.ownerPin)
            put("nid", settings.nextInvoiceNumber)
            put("perms", JSONObject().apply {
                put("items", settings.permItems)
                put("reports", settings.permReports)
                put("debts", settings.permDebts)
                put("expenses", settings.permExpenses)
                put("profit", settings.permProfit)
                put("cash", settings.permCash)
            })
            put("products", JSONArray().apply {
                products.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("price", p.price)
                        put("cost", p.cost)
                        put("stock", p.stock)
                        put("minStock", p.minStock)
                        put("barcode", p.barcode)
                        put("exp", p.expiryDate)
                    })
                }
            })
            put("customers", JSONArray().apply {
                customers.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("debt", c.debt)
                        put("open", c.openingDebt)
                        put("since", c.sinceMillis)
                        put("phone", c.phone)
                        put("note", c.note)
                    })
                }
            })
            put("workers", JSONArray().apply {
                workers.forEach { w ->
                    put(JSONObject().apply {
                        put("id", w.id)
                        put("name", w.name)
                        put("pin", w.pin)
                    })
                }
            })
            put("sales", JSONArray().apply {
                sales.forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.invoiceNumber)
                        put("uid", s.id)
                        put("ts", s.timestampMillis)
                        put("total", s.total)
                        put("cost", s.cost)
                        put("method", s.method)
                        put("payName", s.payName)
                        put("custId", s.customerId)
                        put("custName", s.customerName)
                        put("by", s.cashierName)
                        put("items", JSONArray().apply {
                            s.items.forEach { it -> put(it.toJsonObject()) }
                        })
                    })
                }
            })
            put("expenses", JSONArray().apply {
                expenses.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("ts", e.timestampMillis)
                        put("title", e.title)
                        put("amount", e.amount)
                    })
                }
            })
            put("payments", JSONArray().apply {
                ledger.filter { it.type == "payment" }.forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id)
                        put("ts", l.timestampMillis)
                        put("amount", l.amount)
                        put("custId", l.customerId)
                    })
                }
            })
            put("debtAdds", JSONArray().apply {
                ledger.filter { it.type == "debt_add" }.forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id)
                        put("ts", l.timestampMillis)
                        put("amount", l.amount)
                        put("custId", l.customerId)
                        put("note", l.note)
                    })
                }
            })
        }

        return JSONObject().apply {
            put("app", "kashirak")
            put("v", 1)
            put("ts", System.currentTimeMillis())
            put("S", sObj)
        }.toString(2)
    }

    suspend fun importBackupJson(uid: String, rawJson: String): Result<Triple<Int, Int, Int>> {
        return try {
            val root = JSONObject(rawJson)
            if (root.optString("app") != "kashirak") {
                return Result.failure(IllegalArgumentException("الملف ليس نسخة احتياطية صالحة"))
            }
            val s = root.getJSONObject("S")
            val pArr = s.getJSONArray("products")
            val cArr = s.getJSONArray("customers")
            val saArr = s.getJSONArray("sales")
            val now = System.currentTimeMillis()

            // Soft-delete existing rows so cloud sync propagates clean replacement
            dao.getAllProducts(uid).forEach {
                dao.upsertProduct(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllCustomers(uid).forEach {
                dao.upsertCustomer(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllWorkers(uid).forEach {
                dao.upsertWorker(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllSales(uid).forEach {
                dao.upsertSale(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllExpenses(uid).forEach {
                dao.upsertExpense(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }
            dao.getAllLedgerEvents(uid).forEach {
                dao.upsertLedgerEvent(it.copy(deleted = true, updatedAtMillis = now, syncState = SyncState.PENDING_DELETE))
            }

            val perms = s.optJSONObject("perms")
            val newSettings = ShopSettingsEntity(
                userId = uid,
                shopName = s.optString("shop", "جبر").ifBlank { "المحل" },
                ownerName = s.optString("ownerName", ""),
                phone = s.optString("phone", ""),
                currency = s.optString("cur", "₪").ifBlank { "₪" },
                cash0 = s.optDouble("cash0", 0.0),
                ownerPin = s.optString("ownerPin", "1234").take(4).padEnd(4, '0'),
                role = "owner",
                activeWorkerName = "",
                permItems = perms?.optBoolean("items", false) ?: false,
                permReports = perms?.optBoolean("reports", false) ?: false,
                permDebts = perms?.optBoolean("debts", true) ?: true,
                permExpenses = perms?.optBoolean("expenses", false) ?: false,
                permProfit = perms?.optBoolean("profit", false) ?: false,
                permCash = perms?.optBoolean("cash", false) ?: false,
                nextInvoiceNumber = s.optInt("nid", 10).coerceAtLeast(1),
                updatedAtMillis = now,
                syncState = SyncState.PENDING_UPSERT
            )
            dao.upsertSettings(newSettings)

            val custIdMap = mutableMapOf<String, String>()
            val newCustomers = mutableListOf<CustomerEntity>()
            for (i in 0 until cArr.length()) {
                val c = cArr.getJSONObject(i)
                val oldId = c.opt("id")?.toString().orEmpty()
                val docId = sanitizeOrNewId(oldId)
                if (oldId.isNotBlank()) custIdMap[oldId] = docId
                val debt = c.optDouble("debt", 0.0)
                newCustomers.add(
                    CustomerEntity(
                        id = docId,
                        userId = uid,
                        name = c.optString("name", "زبون"),
                        debt = debt,
                        openingDebt = if (c.has("open")) c.optDouble("open", debt) else debt,
                        sinceMillis = c.optLong("since", now),
                        phone = c.optString("phone", ""),
                        note = c.optString("note", ""),
                        updatedAtMillis = now,
                        deleted = false,
                        syncState = SyncState.PENDING_UPSERT
                    )
                )
            }
            dao.upsertCustomers(newCustomers)

            val newProducts = mutableListOf<ProductEntity>()
            for (i in 0 until pArr.length()) {
                val p = pArr.getJSONObject(i)
                val docId = sanitizeOrNewId(p.opt("id")?.toString())
                newProducts.add(
                    ProductEntity(
                        id = docId,
                        userId = uid,
                        name = p.optString("name", "صنف"),
                        price = p.optDouble("price", 0.0).coerceAtLeast(0.0),
                        cost = p.optDouble("cost", 0.0).coerceAtLeast(0.0),
                        stock = p.optInt("stock", 0).coerceAtLeast(0),
                        minStock = p.optInt("minStock", 5).coerceAtLeast(0),
                        barcode = p.optString("barcode", ""),
                        expiryDate = p.optString("exp", ""),
                        updatedAtMillis = now,
                        deleted = false,
                        syncState = SyncState.PENDING_UPSERT
                    )
                )
            }
            dao.upsertProducts(newProducts)

            val wArr = s.optJSONArray("workers")
            if (wArr != null) {
                val newWorkers = mutableListOf<WorkerEntity>()
                for (i in 0 until wArr.length()) {
                    val w = wArr.getJSONObject(i)
                    newWorkers.add(
                        WorkerEntity(
                            id = sanitizeOrNewId(w.opt("id")?.toString()),
                            userId = uid,
                            name = w.optString("name", "الكاشير"),
                            pin = w.optString("pin", "1111").take(4).padEnd(4, '1'),
                            updatedAtMillis = now,
                            deleted = false,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
                dao.upsertWorkers(newWorkers)
            }

            val newSales = mutableListOf<SaleEntity>()
            for (i in 0 until saArr.length()) {
                val sa = saArr.getJSONObject(i)
                val oldCustId = sa.opt("custId")?.toString().orEmpty()
                val mappedCustId = custIdMap[oldCustId] ?: ""
                val itemsArr = sa.optJSONArray("items")
                val lineItems = mutableListOf<SaleLineItem>()
                if (itemsArr != null) {
                    for (k in 0 until itemsArr.length()) {
                        val it = itemsArr.getJSONObject(k)
                        lineItems.add(
                            SaleLineItem(
                                name = it.optString("name", ""),
                                qty = it.optInt("qty", 1),
                                price = it.optDouble("price", 0.0),
                                cost = it.optDouble("cost", 0.0)
                            )
                        )
                    }
                }
                newSales.add(
                    SaleEntity(
                        id = sanitizeOrNewId(sa.optString("uid").ifBlank { sa.opt("id")?.toString() }),
                        userId = uid,
                        invoiceNumber = sa.optInt("id", i + 1).coerceAtLeast(1),
                        timestampMillis = sa.optLong("ts", now),
                        total = sa.optDouble("total", 0.0).coerceAtLeast(0.0),
                        cost = sa.optDouble("cost", 0.0).coerceAtLeast(0.0),
                        method = sa.optString("method", "cash"),
                        payName = sa.optString("payName", ""),
                        customerId = mappedCustId,
                        customerName = sa.optString("custName", ""),
                        itemsJson = SaleLineItem.listToJson(lineItems),
                        cashierName = sa.optString("by", ""),
                        updatedAtMillis = now,
                        deleted = false,
                        syncState = SyncState.PENDING_UPSERT
                    )
                )
            }
            dao.upsertSales(newSales)

            val exArr = s.optJSONArray("expenses")
            if (exArr != null) {
                val newExpenses = mutableListOf<ExpenseEntity>()
                for (i in 0 until exArr.length()) {
                    val e = exArr.getJSONObject(i)
                    newExpenses.add(
                        ExpenseEntity(
                            id = sanitizeOrNewId(e.opt("id")?.toString()),
                            userId = uid,
                            timestampMillis = e.optLong("ts", now),
                            title = e.optString("title", "مصروف"),
                            amount = e.optDouble("amount", 0.0).coerceAtLeast(0.0),
                            updatedAtMillis = now,
                            deleted = false,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
                dao.upsertExpenses(newExpenses)
            }

            val ledgerEvents = mutableListOf<LedgerEventEntity>()
            val payArr = s.optJSONArray("payments")
            if (payArr != null) {
                for (i in 0 until payArr.length()) {
                    val p = payArr.getJSONObject(i)
                    val cid = custIdMap[p.opt("custId")?.toString().orEmpty()] ?: continue
                    ledgerEvents.add(
                        LedgerEventEntity(
                            id = sanitizeOrNewId(p.opt("id")?.toString()),
                            userId = uid,
                            customerId = cid,
                            type = "payment",
                            timestampMillis = p.optLong("ts", now),
                            amount = p.optDouble("amount", 0.0).coerceAtLeast(0.0),
                            note = "",
                            updatedAtMillis = now,
                            deleted = false,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
            }
            val addArr = s.optJSONArray("debtAdds")
            if (addArr != null) {
                for (i in 0 until addArr.length()) {
                    val d = addArr.getJSONObject(i)
                    val cid = custIdMap[d.opt("custId")?.toString().orEmpty()] ?: continue
                    ledgerEvents.add(
                        LedgerEventEntity(
                            id = sanitizeOrNewId(d.opt("id")?.toString()),
                            userId = uid,
                            customerId = cid,
                            type = "debt_add",
                            timestampMillis = d.optLong("ts", now),
                            amount = d.optDouble("amount", 0.0).coerceAtLeast(0.0),
                            note = d.optString("note", ""),
                            updatedAtMillis = now,
                            deleted = false,
                            syncState = SyncState.PENDING_UPSERT
                        )
                    )
                }
            }
            dao.upsertLedgerEvents(ledgerEvents)

            Result.success(Triple(newProducts.size, newSales.size, newCustomers.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Backup", "Failed to import backup JSON", e)
            Result.failure(e)
        }
    }

    private fun sanitizeOrNewId(raw: String?): String {
        if (!raw.isNullOrBlank() && raw.length in 6..120 && raw.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            return raw
        }
        return newDocId()
    }

    companion object {
        fun newDocId(): String = UUID.randomUUID().toString()
    }
}
