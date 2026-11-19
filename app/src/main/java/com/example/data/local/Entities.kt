package com.example.data.local

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ui.theme.PmBankColor
import com.example.ui.theme.PmCashColor
import com.example.ui.theme.PmDebtColor
import com.example.ui.theme.PmJawwalColor
import com.example.ui.theme.PmPalpayColor
import org.json.JSONArray
import org.json.JSONObject

object SyncState {
    const val SYNCED = 0
    const val PENDING_UPSERT = 1
    const val PENDING_DELETE = 2
}

enum class PaymentMethod(
    val key: String,
    val arabicLabel: String,
    val accentColor: Color
) {
    CASH("cash", "نقدي", PmCashColor),
    JAWWAL("jawwal", "جوال باي", PmJawwalColor),
    BANK("bank", "بنك", PmBankColor),
    PALPAY("palpay", "محفظة بال باي", PmPalpayColor),
    DEBT("debt", "أجل على الزبون", PmDebtColor);

    companion object {
        fun fromKey(key: String): PaymentMethod =
            entries.firstOrNull { it.key == key } ?: CASH
    }
}

data class SaleLineItem(
    val name: String,
    val qty: Int,
    val price: Double,
    val cost: Double
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("name", name)
        put("qty", qty)
        put("price", price)
        put("cost", cost)
    }

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "name" to name,
        "qty" to qty,
        "price" to price,
        "cost" to cost
    )

    companion object {
        fun listToJson(items: List<SaleLineItem>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<SaleLineItem> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            SaleLineItem(
                                name = o.optString("name", ""),
                                qty = o.optInt("qty", 1),
                                price = o.optDouble("price", 0.0),
                                cost = o.optDouble("cost", 0.0)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

@Entity(tableName = "shop_settings")
data class ShopSettingsEntity(
    @PrimaryKey val userId: String,
    val shopName: String = "جبر",
    val ownerName: String = "احمد",
    val phone: String = "",
    val currency: String = "₪",
    val cash0: Double = 50.0,
    val ownerPin: String = "1234",
    val role: String = "owner",
    val activeWorkerName: String = "",
    val permItems: Boolean = false,
    val permReports: Boolean = false,
    val permDebts: Boolean = true,
    val permExpenses: Boolean = false,
    val permProfit: Boolean = false,
    val permCash: Boolean = false,
    val nextInvoiceNumber: Int = 10,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncState: Int = SyncState.PENDING_UPSERT,
    val lastSyncedAtMillis: Long = 0L
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val price: Double,
    val cost: Double,
    val stock: Int,
    val minStock: Int = 5,
    val barcode: String = "",
    val expiryDate: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val debt: Double,
    val openingDebt: Double,
    val sinceMillis: Long,
    val phone: String = "",
    val note: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
)

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val pin: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val invoiceNumber: Int,
    val timestampMillis: Long,
    val total: Double,
    val cost: Double,
    val method: String,
    val payName: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val itemsJson: String,
    val cashierName: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
) {
    val items: List<SaleLineItem>
        get() = SaleLineItem.listFromJson(itemsJson)
}

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val timestampMillis: Long,
    val title: String,
    val amount: Double,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
)

@Entity(tableName = "ledger_events")
data class LedgerEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val customerId: String,
    val type: String, // "payment" or "debt_add"
    val timestampMillis: Long,
    val amount: Double,
    val note: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncState: Int = SyncState.PENDING_UPSERT
)
