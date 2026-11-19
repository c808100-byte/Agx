package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.ProductEntity

object StockAlertNotifier {

    private const val CHANNEL_ID = "low_stock_alerts"
    private const val NOTIFICATION_ID = 4102

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stock_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.stock_alert_channel_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyLowStock(
        context: Context,
        triggeredProducts: List<ProductEntity>,
        totalLowCount: Int
    ) {
        if (triggeredProducts.isEmpty()) return
        if (!hasNotificationPermission(context)) return

        try {
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_tab", "inventory")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (triggeredProducts.size == 1) {
                val p = triggeredProducts.first()
                if (p.stock <= 0) "نفد مخزون: ${p.name}" else "تنبيه مخزون منخفض: ${p.name}"
            } else {
                "تنبيه مخزون منخفض (${triggeredProducts.size} أصناف)"
            }

            val summaryBody = if (triggeredProducts.size == 1) {
                val p = triggeredProducts.first()
                "الكمية الحالية ${p.stock} قطعة (الحد الأدنى ${p.minStock})"
            } else {
                triggeredProducts.take(3).joinToString("، ") { "${it.name} (${it.stock})" } +
                    if (totalLowCount > triggeredProducts.size) " · إجمالي المنخفض: $totalLowCount" else ""
            }

            val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
            triggeredProducts.take(5).forEach { p ->
                val status = if (p.stock <= 0) "نفد المخزون" else "متبقي ${p.stock} قطعة"
                inbox.addLine("${p.name}: $status (الحد الأدنى ${p.minStock})")
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(summaryBody)
                .setStyle(inbox)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Safe no-op in headless test environments
        }
    }
}
