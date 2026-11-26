package com.example.bizzly.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bizzly.MainActivity
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.domain.BizzlyCalculator

object NotificationHelper {
    const val CHANNEL_STOCK_ALERTS = "bizzly_stock_alerts"
    const val EXTRA_OPEN_TAB = "bizzly_open_tab"
    const val TAB_INVENTORY = "inventory"
    private const val NOTIFICATION_ID_STOCK = 4102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_STOCK_ALERTS)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_STOCK_ALERTS,
                    "Alertes de stock",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications lors d’un passage sous le seuil ou d’une rupture de stock"
                    enableVibration(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

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

    fun notifyStockAlerts(
        context: Context,
        businessName: String,
        alerts: List<InventoryItemEntity>
    ) {
        if (alerts.isEmpty() || !hasNotificationPermission(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_INVENTORY)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_STOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val outCount = alerts.count { it.quantity <= 0.0 }
        val title = when {
            alerts.size == 1 && alerts.first().quantity <= 0.0 ->
                "Rupture de stock · ${alerts.first().name}"
            alerts.size == 1 ->
                "Stock bas · ${alerts.first().name}"
            outCount > 0 ->
                "${alerts.size} alertes de stock ($outCount rupture${if (outCount > 1) "s" else ""})"
            else ->
                "${alerts.size} articles sous le seuil minimal"
        }

        val summaryLines = alerts.take(5).map { item ->
            val tag = if (item.quantity <= 0.0) "RUPTURE" else "${BizzlyCalculator.formatQty(item.quantity)} / ${BizzlyCalculator.formatQty(item.minThreshold)} ${item.unit}"
            "${item.name} : $tag"
        }
        val body = if (alerts.size == 1) {
            val first = alerts.first()
            "$businessName · Reste ${BizzlyCalculator.formatQty(first.quantity)} ${first.unit} (seuil ${BizzlyCalculator.formatQty(first.minThreshold)})"
        } else {
            "$businessName · " + summaryLines.joinToString(", ")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_STOCK_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (alerts.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(businessName)
            summaryLines.forEach { inbox.addLine(it) }
            if (alerts.size > 5) {
                inbox.addLine("… et ${alerts.size - 5} autre(s)")
            }
            builder.setStyle(inbox)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_STOCK, builder.build())
        }
    }
}
