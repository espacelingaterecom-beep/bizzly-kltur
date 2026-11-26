package com.example.bizzly.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BluetoothPrinterDevice
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaymentPart
import com.example.bizzly.model.PrinterPaperWidth
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object EscPosPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_ON = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_DOUBLE_H_ON = byteArrayOf(0x1D, 0x21, 0x01)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val GS_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x03)

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getAdapter(context) ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun listPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = getAdapter(context) ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { dev ->
                    BluetoothPrinterDevice(
                        name = dev.name?.takeIf { it.isNotBlank() } ?: "Imprimante Bluetooth",
                        address = dev.address
                    )
                }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun printBytes(context: Context, deviceAddress: String, payload: ByteArray): Result<Unit> {
        if (!hasBluetoothPermission(context)) {
            return Result.failure(IllegalStateException("Permission Bluetooth requise."))
        }
        val adapter = getAdapter(context)
            ?: return Result.failure(IllegalStateException("Bluetooth non disponible sur cet appareil."))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Activez le Bluetooth pour imprimer."))
        }
        val device = runCatching { adapter.getRemoteDevice(deviceAddress) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Imprimante introuvable ($deviceAddress)."))

        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { adapter.cancelDiscovery() }
            }
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use { s ->
                s.connect()
                val out = s.outputStream
                out.write(payload)
                out.flush()
            }
        }
    }

    fun buildTestReceipt(account: BusinessAccount?, width: PrinterPaperWidth): ByteArray {
        val cols = width.charsPerLine
        val buf = EscPosBuilder(cols)
        buf.init()
        buf.center()
        buf.bold(true)
        buf.doubleHeight(true)
        buf.line(account?.name ?: "Bizzly")
        buf.doubleHeight(false)
        buf.bold(false)
        buf.line("TEST IMPRESSION ${width.mm} MM")
        buf.rule()
        buf.left()
        buf.twoCols("Largeur", "${width.mm} mm ($cols col)")
        buf.twoCols("Date", nowFormatted())
        buf.rule()
        buf.center()
        buf.line("Imprimante ESC/POS prete")
        buf.feedAndCut()
        return buf.toByteArray()
    }

    fun buildTableReceipt(
        account: BusinessAccount?,
        data: BusinessData,
        tableNumber: Int,
        table: OpenTable,
        paymentLabel: String,
        payments: List<PaymentPart>,
        cashGiven: Double,
        isProforma: Boolean,
        width: PrinterPaperWidth
    ): ByteArray {
        val cols = width.charsPerLine
        val breakdown = BizzlyCalculator.calculateTableBreakdown(data, table)
        val serverName = BizzlyCalculator.serverName(data, table.sv)
        val buf = EscPosBuilder(cols)

        buf.init()
        buf.center()
        buf.bold(true)
        buf.doubleHeight(true)
        buf.line(account?.name ?: "Bizzly")
        buf.doubleHeight(false)
        buf.bold(false)
        if (!account?.addr.isNullOrBlank()) buf.line(account!!.addr)
        if (!account?.tel.isNullOrBlank()) buf.line("Tel : ${account!!.tel}")
        buf.rule()

        buf.bold(true)
        buf.line(if (isProforma) "NOTE DE TABLE $tableNumber" else "TICKET DE CAISSE - TABLE $tableNumber")
        buf.bold(false)
        buf.left()
        buf.twoCols("Serveur", serverName)
        buf.twoCols("Date", nowFormatted())
        buf.rule()

        table.it.forEach { (pidStr, qty) ->
            val p = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@forEach
            val compQty = (table.comped[pidStr] ?: 0.0).coerceIn(0.0, qty)
            val paidQty = (qty - compQty).coerceAtLeast(0.0)
            val lineAmt = paidQty * p.pv
            buf.twoCols("${BizzlyCalculator.formatQty(qty)}x ${p.n}", BizzlyCalculator.formatFcfa(lineAmt))
            if (compQty > 0.0) {
                buf.line("  dont ${BizzlyCalculator.formatQty(compQty)} offert(s)")
            }
        }
        buf.rule()

        if (breakdown.compedDiscount > 0.0 || breakdown.tableDiscount > 0.0) {
            buf.twoCols("Sous-total brut", BizzlyCalculator.formatFcfa(breakdown.grossSubtotal))
            if (breakdown.compedDiscount > 0.0) {
                buf.twoCols("Offerts", "-${BizzlyCalculator.formatFcfa(breakdown.compedDiscount)}")
            }
            if (breakdown.tableDiscount > 0.0) {
                val discLabel = if (table.discountType == "percent") {
                    "Remise (${BizzlyCalculator.formatQty(table.discountValue)}%)"
                } else {
                    "Remise"
                }
                buf.twoCols(discLabel, "-${BizzlyCalculator.formatFcfa(breakdown.tableDiscount)}")
            }
            if (table.discountReason.isNotBlank()) {
                buf.line("Motif: ${table.discountReason}")
            }
            buf.rule()
        }

        if (breakdown.paidSoFar > 0.0) {
            buf.twoCols("Total net", BizzlyCalculator.formatFcfa(breakdown.netTotal))
            buf.twoCols("Deja regle", "-${BizzlyCalculator.formatFcfa(breakdown.paidSoFar)}")
        }

        buf.bold(true)
        buf.doubleHeight(true)
        buf.twoCols(if (isProforma) "A PAYER" else "TOTAL", BizzlyCalculator.formatFcfa(breakdown.remainingDue))
        buf.doubleHeight(false)
        buf.bold(false)

        if (isProforma) {
            buf.center()
            buf.line("NOTE PROVISOIRE - NON PAYEE")
        } else {
            buf.rule()
            if (payments.size > 1) {
                buf.line("Paiement mixte :")
                payments.forEach { part ->
                    buf.twoCols("  ${part.mode}", BizzlyCalculator.formatFcfa(part.amount))
                }
            } else {
                buf.twoCols("Paiement", paymentLabel)
            }
            if (cashGiven > 0.0) {
                buf.twoCols("Donne", BizzlyCalculator.formatFcfa(cashGiven))
                val change = (cashGiven - breakdown.remainingDue).coerceAtLeast(0.0)
                buf.bold(true)
                buf.twoCols("Rendu", BizzlyCalculator.formatFcfa(change))
                buf.bold(false)
            }
            buf.center()
            buf.line("Merci de votre visite !")
        }

        buf.feedAndCut()
        return buf.toByteArray()
    }

    fun buildKitchenBarTicket(
        account: BusinessAccount?,
        data: BusinessData,
        tableNumber: Int,
        table: OpenTable,
        width: PrinterPaperWidth
    ): ByteArray {
        val cols = width.charsPerLine
        val serverName = BizzlyCalculator.serverName(data, table.sv)
        val buf = EscPosBuilder(cols)

        buf.init()
        buf.center()
        buf.bold(true)
        buf.doubleSize(true)
        buf.line("BON CUISINE / BAR")
        buf.doubleSize(false)
        buf.line(account?.name ?: "Bizzly")
        buf.doubleHeight(true)
        buf.line("TABLE $tableNumber")
        buf.doubleHeight(false)
        buf.bold(false)
        buf.left()
        buf.twoCols("Serveur", serverName)
        buf.twoCols("Heure", nowFormatted())
        buf.rule()

        buf.bold(true)
        buf.doubleHeight(true)
        table.it.forEach { (pidStr, qty) ->
            val p = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@forEach
            val line = "${BizzlyCalculator.formatQty(qty)}x ${p.n}"
            buf.line(line)
        }
        buf.doubleHeight(false)
        buf.bold(false)
        buf.rule()
        buf.feedAndCut()
        return buf.toByteArray()
    }

    private fun getAdapter(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun nowFormatted(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE))

    private class EscPosBuilder(private val cols: Int) {
        private val stream = ByteArrayOutputStream()

        fun init() {
            stream.write(ESC_INIT)
        }

        fun left() {
            stream.write(ESC_ALIGN_LEFT)
        }

        fun center() {
            stream.write(ESC_ALIGN_CENTER)
        }

        fun bold(enabled: Boolean) {
            stream.write(if (enabled) ESC_BOLD_ON else ESC_BOLD_OFF)
        }

        fun doubleHeight(enabled: Boolean) {
            stream.write(if (enabled) ESC_DOUBLE_H_ON else ESC_NORMAL_SIZE)
        }

        fun doubleSize(enabled: Boolean) {
            stream.write(if (enabled) ESC_DOUBLE_ON else ESC_NORMAL_SIZE)
        }

        fun rule() {
            line("-".repeat(cols))
        }

        fun line(text: String) {
            val clean = toPrinterAscii(text)
            if (clean.length <= cols) {
                stream.write(clean.toByteArray(Charsets.US_ASCII))
                stream.write('\n'.code)
            } else {
                var idx = 0
                while (idx < clean.length) {
                    val end = minOf(clean.length, idx + cols)
                    stream.write(clean.substring(idx, end).toByteArray(Charsets.US_ASCII))
                    stream.write('\n'.code)
                    idx = end
                }
            }
        }

        fun twoCols(leftText: String, rightText: String) {
            val l = toPrinterAscii(leftText)
            val r = toPrinterAscii(rightText)
            val minGap = 1
            val maxLeft = (cols - r.length - minGap).coerceAtLeast(4)
            val lFit = if (l.length > maxLeft) l.take(maxLeft - 1) + "." else l
            val spaces = (cols - lFit.length - r.length).coerceAtLeast(1)
            val combined = (lFit + " ".repeat(spaces) + r).take(cols)
            stream.write(combined.toByteArray(Charsets.US_ASCII))
            stream.write('\n'.code)
        }

        fun feedAndCut() {
            stream.write("\n\n\n".toByteArray(Charsets.US_ASCII))
            stream.write(GS_CUT_PARTIAL)
        }

        fun toByteArray(): ByteArray = stream.toByteArray()

        private fun toPrinterAscii(input: String): String {
            val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace(Regex("[\\u0300-\\u036f]"), "")
                .replace("€", "EUR")
                .replace("·", "-")
                .replace("−", "-")
                .replace("×", "x")
                .replace("’", "'")
            return nfd.replace(Regex("[^\\x20-\\x7e]"), " ").trimEnd()
        }
    }
}
