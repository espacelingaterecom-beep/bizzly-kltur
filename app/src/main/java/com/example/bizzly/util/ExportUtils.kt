package com.example.bizzly.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.domain.StockAlertLevel
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DecodedLogoResult(
    val dataUri: String,
    val width: Int,
    val height: Int,
    val palette: BrandColors?
)

data class PreparedExport(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
    val downloadsUri: Uri?,
    val cacheUri: Uri?
)

object ExportUtils {

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 8f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }

    fun decodeAndResizeLogo(context: Context, uri: Uri): DecodedLogoResult? {
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val iw = max(1, rawBitmap.width)
        val ih = max(1, rawBitmap.height)
        val scale = min(1f, 520f / max(iw, ih).toFloat())
        val w = max(1, (iw * scale).roundToInt())
        val h = max(1, (ih * scale).roundToInt())

        val resized = if (w != iw || h != ih) {
            Bitmap.createScaledBitmap(rawBitmap, w, h, true).also { rawBitmap.recycle() }
        } else {
            rawBitmap
        }

        val palette = BizzlyCalculator.extractBrandPalette(resized)
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.PNG, 100, baos)
        resized.recycle()

        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return DecodedLogoResult(
            dataUri = "data:image/png;base64,$b64",
            width = w,
            height = h,
            palette = palette
        )
    }

    fun decodeDataUriBitmap(dataUri: String?): Bitmap? {
        if (dataUri.isNullOrBlank()) return null
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx < 0) return null
        val b64 = dataUri.substring(commaIdx + 1)
        return runCatching {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun buildQrMenuPayload(account: BusinessAccount?, data: BusinessData): String {
        val bizName = BizzlyCalculator.asciiUpperName(account?.name ?: "Bizzly")
        val sb = StringBuilder()
        sb.append(bizName).append(" - MENU\n")
        data.prod.forEach { p ->
            val asciiName = Normalizer.normalize(p.n, Normalizer.Form.NFD)
                .replace(Regex("[\\u0300-\\u036f]"), "")
            val price = if (p.pv % 1.0 == 0.0) p.pv.toLong().toString() else p.pv.toString()
            sb.append(asciiName).append(' ').append(price).append('\n')
        }
        return sb.toString()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.toByteArray(StandardCharsets.UTF_8).size > 2900) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 4
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        }.getOrNull()
    }

    fun generateInvoicePdf(
        account: BusinessAccount?,
        data: BusinessData,
        tableNumber: Int,
        paymentMode: String,
        cashGiven: Double
    ): ByteArray {
        val table = data.tb[tableNumber.toString()]
        val items = table?.it.orEmpty().entries.mapNotNull { (pidStr, qty) ->
            val product = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@mapNotNull null
            Triple(product.n, qty, qty * product.pv)
        }
        val total = BizzlyCalculator.tableTotal(data, tableNumber)
        val serverName = table?.let { BizzlyCalculator.serverName(data, it.sv) } ?: "Serveur"
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE))
        val logoBmp = decodeDataUriBitmap(account?.logo)
        val linesInfo = buildList {
            if (!account?.addr.isNullOrBlank()) add(account!!.addr)
            if (!account?.tel.isNullOrBlank()) add("Tél : ${account!!.tel}")
        }

        val pageWidth = 226
        val headHeight = (if (logoBmp != null) 46 else 22) + linesInfo.size * 12
        val pageHeight = max(240, 180 + headHeight + items.size * 16 + if (cashGiven > 0) 42 else 0)

        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A1F44")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 14f
                textAlign = Paint.Align.CENTER
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A1F44")
                textSize = 9.5f
            }
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A1F44")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 10f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8f
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#0A1F44")
                strokeWidth = 0.8f
            }

            var y = 24f
            if (logoBmp != null) {
                val rawW = (account?.lw?.takeIf { it > 0 } ?: max(1, logoBmp.width)).toFloat()
                val rawH = (account?.lh?.takeIf { it > 0 } ?: max(1, logoBmp.height)).toFloat()
                val ratio = (rawW / rawH).coerceIn(0.25f, 4.5f)
                val w = min(140f, 38f * ratio)
                val h = w / ratio
                val left = (pageWidth - w) / 2f
                canvas.drawBitmap(logoBmp, null, RectF(left, y, left + w, y + h), null)
                y += h + 12f
            } else {
                val name = account?.name ?: "Bizzly"
                titlePaint.textSize = if (name.length > 16) 12f else 15f
                canvas.drawText(ellipsizeToWidth(name, titlePaint, pageWidth - 24f), pageWidth / 2f, y + 6f, titlePaint)
                y += 18f
            }

            mutedPaint.textAlign = Paint.Align.CENTER
            linesInfo.forEach { line ->
                canvas.drawText(ellipsizeToWidth(line, mutedPaint, pageWidth - 20f), pageWidth / 2f, y, mutedPaint)
                y += 11f
            }

            y += 8f
            mutedPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("FACTURE - Table $tableNumber", 14f, y, boldPaint)
            y += 12f
            canvas.drawText(ellipsizeToWidth("$serverName · $nowStr", mutedPaint, pageWidth - 28f), 14f, y, mutedPaint)
            y += 16f

            items.forEach { (name, qty, amt) ->
                val priceStr = BizzlyCalculator.formatFcfa(amt)
                val priceW = textPaint.measureText(priceStr)
                val maxLabelW = (pageWidth - 28f) - priceW - 6f
                textPaint.textAlign = Paint.Align.LEFT
                val label = ellipsizeToWidth("${BizzlyCalculator.formatQty(qty)} x $name", textPaint, maxLabelW)
                canvas.drawText(label, 14f, y, textPaint)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(priceStr, pageWidth - 14f, y, textPaint)
                y += 15f
            }

            canvas.drawLine(14f, y - 4f, pageWidth - 14f, y - 4f, linePaint)
            y += 12f
            boldPaint.textSize = 11.5f
            boldPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("TOTAL", 14f, y, boldPaint)
            boldPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(BizzlyCalculator.formatFcfa(total), pageWidth - 14f, y, boldPaint)
            y += 18f

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 9.5f
            canvas.drawText("Paiement : $paymentMode", 14f, y, textPaint)
            if (cashGiven > 0.0) {
                y += 16f
                canvas.drawText("Donné : ${BizzlyCalculator.formatFcfa(cashGiven)}", 14f, y, textPaint)
                y += 13f
                boldPaint.textSize = 9.5f
                boldPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(
                    "Rendu : ${BizzlyCalculator.formatFcfa(max(0.0, cashGiven - total))}",
                    14f,
                    y,
                    boldPaint
                )
            }

            y += 24f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Merci de votre visite !", pageWidth / 2f, y, textPaint)

            doc.finishPage(page)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
            logoBmp?.recycle()
        }
    }

    fun generateQrMenuPdf(account: BusinessAccount?, data: BusinessData): ByteArray? {
        val qrBitmap = generateQrBitmap(buildQrMenuPayload(account, data), 640) ?: return null
        val pageW = 595
        val pageH = 842
        val brand = account?.brand ?: BrandColors()
        val prRgb = BizzlyCalculator.hexToRgb(brand.pr)
        val secRgb = BizzlyCalculator.hexToRgb(brand.sec, Triple(10, 31, 68))
        val bgRgb = if (BizzlyCalculator.relativeLuminance(secRgb) < 0.18) {
            secRgb
        } else {
            BizzlyCalculator.mixRgb(prRgb, Triple(0, 0, 0), 0.62)
        }
        val bgColor = Color.rgb(bgRgb.first, bgRgb.second, bgRgb.third)
        val circleRgb = BizzlyCalculator.mixRgb(bgRgb, Triple(255, 255, 255), 0.07)
        val circleColor = Color.rgb(circleRgb.first, circleRgb.second, circleRgb.third)
        val prColor = Color.rgb(prRgb.first, prRgb.second, prRgb.third)
        val prContrastText = if (BizzlyCalculator.contrastRatio(prRgb, Triple(255, 255, 255)) >= 3.0) {
            Color.WHITE
        } else {
            Color.parseColor("#0A1F44")
        }
        val prLightSafeRgb = BizzlyCalculator.fitToContrast(prRgb, Triple(248, 250, 252), 3.0)
        val prLightSafeColor = Color.rgb(prLightSafeRgb.first, prLightSafeRgb.second, prLightSafeRgb.third)
        val logoBmp = decodeDataUriBitmap(account?.logo)

        val doc = PdfDocument()
        try {
            // Page 1: Branded dark QR poster
            val p1 = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
            val c1 = p1.canvas
            c1.drawColor(bgColor)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = circleColor
            c1.drawCircle(610f, -8f, 250f, paint)
            c1.drawCircle(-10f, pageH + 10f, 190f, paint)

            // Logo card top center
            paint.color = Color.WHITE
            c1.drawRoundRect(RectF(147.5f, 48f, 447.5f, 152f), 18f, 18f, paint)
            if (logoBmp != null) {
                val rawW = (account?.lw?.takeIf { it > 0 } ?: max(1, logoBmp.width)).toFloat()
                val rawH = (account?.lh?.takeIf { it > 0 } ?: max(1, logoBmp.height)).toFloat()
                val ratio = (rawW / rawH).coerceIn(0.25f, 4.5f)
                val w = min(268f, 76f * ratio)
                val h = w / ratio
                val lx = (pageW - w) / 2f
                val ly = 100f - h / 2f
                c1.drawBitmap(logoBmp, null, RectF(lx, ly, lx + w, ly + h), null)
            } else {
                paint.color = bgColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val bn = account?.name ?: "Bizzly"
                paint.textSize = min(32f, 250f / max(1, bn.length / 2))
                paint.textAlign = Paint.Align.CENTER
                c1.drawText(ellipsizeToWidth(bn, paint, 260f), pageW / 2f, 110f, paint)
            }

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            c1.drawText("Scannez pour", pageW / 2f, 200f, paint)
            c1.drawText("découvrir notre menu", pageW / 2f, 238f, paint)

            paint.color = Color.rgb(189, 204, 230)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 13f
            c1.drawText("Ouvrez l'appareil photo de votre téléphone", pageW / 2f, 272f, paint)

            // QR white card
            paint.color = Color.WHITE
            c1.drawRoundRect(RectF(137.5f, 326f, 457.5f, 646f), 22f, 22f, paint)
            c1.drawBitmap(qrBitmap, null, RectF(162.5f, 351f, 432.5f, 621f), null)

            // Bottom pill
            paint.color = prColor
            c1.drawRoundRect(RectF(147.5f, 686f, 447.5f, 728f), 8f, 8f, paint)
            paint.color = prContrastText
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 12.5f
            c1.drawText("Aucune connexion internet nécessaire", pageW / 2f, 711f, paint)

            // Business contact & signature
            val contact = buildList {
                if (!account?.addr.isNullOrBlank()) add(account!!.addr)
                if (!account?.tel.isNullOrBlank()) add("Tél : ${account!!.tel}")
            }.joinToString("   -   ")
            if (contact.isNotBlank()) {
                paint.color = Color.rgb(209, 224, 242)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 10.5f
                c1.drawText(ellipsizeToWidth(contact, paint, pageW - 60f), pageW / 2f, 760f, paint)
            }
            paint.color = Color.rgb(153, 171, 204)
            paint.textSize = 8.5f
            c1.drawText("Propulsé par Bizzly · Fait par KLTUR COORPORATION", pageW / 2f, 804f, paint)

            doc.finishPage(p1)

            // Pages 2+: 2-column light menu pages
            val products = data.prod
            val perPage = 48
            val numPages = max(1, ceil(products.size / perPage.toDouble()).toInt())
            val navy = Color.parseColor("#0A1F44")
            val lightBg = Color.parseColor("#F8FAFC")
            val borderColor = Color.parseColor("#E2E8F0")
            val slate = Color.parseColor("#64748B")

            for (pg in 0 until numPages) {
                val start = pg * perPage
                val end = min(products.size, start + perPage)
                val slice = if (start < end) products.subList(start, end) else emptyList()
                val half = ceil(slice.size / 2.0).toInt().coerceAtLeast(1)
                val rowH = min(27f, 650f / half.toFloat())

                val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pg + 2).create())
                val c = p.canvas
                c.drawColor(lightBg)

                if (logoBmp != null) {
                    val rawW = (account?.lw?.takeIf { it > 0 } ?: max(1, logoBmp.width)).toFloat()
                    val rawH = (account?.lh?.takeIf { it > 0 } ?: max(1, logoBmp.height)).toFloat()
                    val ratio = (rawW / rawH).coerceIn(0.25f, 4.5f)
                    val w = min(170f, 38f * ratio)
                    val h = w / ratio
                    c.drawBitmap(logoBmp, null, RectF(40f, 55f - h / 2f, 40f + w, 55f + h / 2f), null)
                } else {
                    paint.color = navy
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 16f
                    paint.textAlign = Paint.Align.LEFT
                    c.drawText(ellipsizeToWidth(account?.name ?: "Bizzly", paint, 360f), 40f, 60f, paint)
                }

                paint.color = prLightSafeColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                paint.textAlign = Paint.Align.RIGHT
                c.drawText("NOTRE MENU", 555f, 58f, paint)

                paint.color = borderColor
                c.drawRect(40f, 78f, 555f, 79f, paint)

                slice.forEachIndexed { idx, prod ->
                    val col = if (idx < half) 0 else 1
                    val row = idx % half
                    val x = if (col == 1) 318f else 40f
                    val y = 108f + row * rowH

                    val priceStr = BizzlyCalculator.formatFcfa(prod.pv)
                    paint.color = prLightSafeColor
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 11.5f
                    paint.textAlign = Paint.Align.RIGHT
                    val priceW = paint.measureText(priceStr)
                    c.drawText(priceStr, x + 237f, y, paint)

                    paint.color = navy
                    paint.typeface = Typeface.DEFAULT
                    paint.textSize = 11.5f
                    paint.textAlign = Paint.Align.LEFT
                    val prodLabel = ellipsizeToWidth(prod.n, paint, 237f - priceW - 10f)
                    c.drawText(prodLabel, x, y, paint)

                    paint.color = borderColor
                    c.drawRect(x, y + 8f, x + 237f, y + 8.7f, paint)
                }

                paint.color = slate
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 9f
                paint.textAlign = Paint.Align.LEFT
                val footer = "Prix en francs CFA (F)" + if (numPages > 1) "  ·  page ${pg + 1}/$numPages" else ""
                c.drawText(footer, 40f, 804f, paint)

                doc.finishPage(p)
            }

            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
            qrBitmap.recycle()
            logoBmp?.recycle()
        }
    }

    fun generateBilanPdf(account: BusinessAccount?, data: BusinessData): ByteArray {
        val pageW = 595
        val pageH = 842
        val summary = BizzlyCalculator.calculateSummary(data)
        val brand = account?.brand ?: BrandColors()
        val prRgb = BizzlyCalculator.hexToRgb(brand.pr)
        val prSafe = BizzlyCalculator.fitToContrast(prRgb, Triple(255, 255, 255), 3.0)
        val prColor = Color.rgb(prSafe.first, prSafe.second, prSafe.third)
        val navy = Color.parseColor("#0A1F44")
        val slate = Color.parseColor("#64748B")
        val border = Color.parseColor("#E2E8F0")
        val cardBg = Color.parseColor("#F8FAFC")
        val green = Color.parseColor("#16A34A")
        val red = Color.parseColor("#E5484D")
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE))
        val logoBmp = decodeDataUriBitmap(account?.logo)

        val doc = PdfDocument()
        try {
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            var c = page.canvas
            c.drawColor(Color.WHITE)

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun newPageIfNeeded(y: Float, needed: Float): Float {
                if (y + needed <= pageH - 52f) return y
                p.color = slate
                p.typeface = Typeface.DEFAULT
                p.textSize = 8.5f
                p.textAlign = Paint.Align.LEFT
                c.drawText("Bizzly · ${account?.name ?: "Entreprise"} · page $pageNum", 40f, pageH - 26f, p)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                c = page.canvas
                c.drawColor(Color.WHITE)
                return 48f
            }

            // Header
            if (logoBmp != null) {
                val rawW = (account?.lw?.takeIf { it > 0 } ?: max(1, logoBmp.width)).toFloat()
                val rawH = (account?.lh?.takeIf { it > 0 } ?: max(1, logoBmp.height)).toFloat()
                val ratio = (rawW / rawH).coerceIn(0.25f, 4.5f)
                val w = min(120f, 34f * ratio)
                val h = w / ratio
                c.drawBitmap(logoBmp, null, RectF(40f, 54f - h / 2f, 40f + w, 54f + h / 2f), null)
            } else {
                p.color = navy
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 18f
                p.textAlign = Paint.Align.LEFT
                c.drawText(ellipsizeToWidth(account?.name ?: "Bizzly", p, 320f), 40f, 56f, p)
            }

            p.color = prColor
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 14f
            p.textAlign = Paint.Align.RIGHT
            c.drawText("BILAN FINANCIER", 555f, 50f, p)
            p.color = slate
            p.typeface = Typeface.DEFAULT
            p.textSize = 9.5f
            c.drawText("Édité le $nowStr", 555f, 66f, p)

            p.color = border
            c.drawRect(40f, 82f, 555f, 83.2f, p)

            var y = 104f

            // Highlight card: Reste a verser
            p.color = prColor
            c.drawRoundRect(RectF(40f, y, 555f, y + 74f), 12f, 12f, p)
            p.color = Color.WHITE
            p.typeface = Typeface.DEFAULT
            p.textSize = 11f
            p.textAlign = Paint.Align.LEFT
            c.drawText("RESTE À VERSER (période en cours)", 56f, y + 24f, p)
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 24f
            c.drawText(BizzlyCalculator.formatFcfa(summary.remainingToRemit), 56f, y + 54f, p)
            y += 90f

            // Summary metrics rows
            val metrics = listOf(
                Triple("Recettes enregistrées", BizzlyCalculator.formatFcfa(summary.recordedRevenue), navy),
                Triple("Versements remis", BizzlyCalculator.formatFcfa(summary.remitted), green),
                Triple("Dépenses", BizzlyCalculator.formatFcfa(summary.expenses), red),
                Triple("Bons du personnel", BizzlyCalculator.formatFcfa(summary.vouchers), navy),
                Triple(
                    if (summary.stockGap > 0) "Manquant de stock" else if (summary.stockGap < 0) "Excédent de stock" else "Écart de stock",
                    BizzlyCalculator.formatFcfa(abs(summary.stockGap)),
                    if (summary.stockGap > 0) red else if (summary.stockGap < 0) green else slate
                )
            )
            p.color = cardBg
            c.drawRoundRect(RectF(40f, y, 555f, y + 18f + metrics.size * 24f), 10f, 10f, p)
            var my = y + 24f
            metrics.forEachIndexed { idx, (lbl, valStr, col) ->
                p.color = slate
                p.typeface = Typeface.DEFAULT
                p.textSize = 11f
                p.textAlign = Paint.Align.LEFT
                c.drawText(lbl, 56f, my, p)
                p.color = col
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.RIGHT
                c.drawText(valStr, 539f, my, p)
                if (idx < metrics.lastIndex) {
                    p.color = border
                    c.drawRect(56f, my + 8f, 539f, my + 8.8f, p)
                }
                my += 24f
            }
            y += 30f + metrics.size * 24f

            // Top sales section
            if (summary.topSales.isNotEmpty()) {
                y = newPageIfNeeded(y, 50f)
                p.color = navy
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 13f
                p.textAlign = Paint.Align.LEFT
                c.drawText("Produits les plus vendus", 40f, y, p)
                y += 18f

                summary.topSales.take(12).forEach { item ->
                    y = newPageIfNeeded(y, 24f)
                    p.color = navy
                    p.typeface = Typeface.DEFAULT
                    p.textSize = 10.5f
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(ellipsizeToWidth(item.label, p, 260f), 44f, y, p)

                    p.color = slate
                    p.textAlign = Paint.Align.RIGHT
                    c.drawText("${BizzlyCalculator.formatQty(item.quantity)} vendus", 400f, y, p)

                    p.color = navy
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    c.drawText(BizzlyCalculator.formatFcfa(item.amount), 551f, y, p)

                    p.color = border
                    c.drawRect(40f, y + 6f, 555f, y + 6.7f, p)
                    y += 22f
                }
                y += 12f
            }

            // Closed periods history
            if (data.hist.isNotEmpty()) {
                y = newPageIfNeeded(y, 54f)
                p.color = navy
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 13f
                p.textAlign = Paint.Align.LEFT
                c.drawText("Périodes clôturées (${data.hist.size})", 40f, y, p)
                y += 18f

                data.hist.take(15).forEach { h ->
                    y = newPageIfNeeded(y, 24f)
                    p.color = navy
                    p.typeface = Typeface.DEFAULT
                    p.textSize = 10.5f
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(BizzlyCalculator.formatDateFr(h.d), 44f, y, p)

                    p.color = slate
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(
                        "Recettes ${BizzlyCalculator.formatFcfa(h.att)} · Versé ${BizzlyCalculator.formatFcfa(h.ver)}",
                        150f,
                        y,
                        p
                    )

                    p.color = if (h.rest > 0) red else green
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textAlign = Paint.Align.RIGHT
                    c.drawText(BizzlyCalculator.formatFcfa(h.rest), 551f, y, p)

                    p.color = border
                    c.drawRect(40f, y + 6f, 555f, y + 6.7f, p)
                    y += 22f
                }
            }

            p.color = slate
            p.typeface = Typeface.DEFAULT
            p.textSize = 8.5f
            p.textAlign = Paint.Align.LEFT
            c.drawText("Bizzly · Fait par KLTUR COORPORATION · page $pageNum", 40f, pageH - 26f, p)

            doc.finishPage(page)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            return out.toByteArray()
        } finally {
            doc.close()
            logoBmp?.recycle()
        }
    }

    fun generateExcelBytes(data: BusinessData): ByteArray {
        val vRows = mutableListOf<List<Any>>(
            listOf("Période", "Date", "Produit", "Quantité", "Prix unitaire", "Montant")
        )
        val tRows = mutableListOf<List<Any>>(
            listOf("Période", "Date", "Table", "Serveur", "Mode de paiement", "Total")
        )
        val svRows = mutableListOf<List<Any>>(
            listOf("Date", "Serveur", "Factures", "Total")
        )
        val dRows = mutableListOf<List<Any>>(
            listOf("Période", "Date", "Motif", "Montant", "Produit acheté", "Quantité")
        )
        val vrRows = mutableListOf<List<Any>>(
            listOf("Période", "Date", "Remis à", "Montant")
        )
        val bRows = mutableListOf<List<Any>>(
            listOf("Période", "Date", "Nom", "Montant")
        )

        data class Agg(val date: String, val label: String, var count: Int = 0, var total: Double = 0.0)
        val srvAgg = linkedMapOf<String, Agg>()
        val pmAgg = linkedMapOf<String, Agg>()

        for (period in BizzlyCalculator.allPeriods(data)) {
            for (date in period.sv.keys.sorted()) {
                val map = period.sv[date].orEmpty()
                for ((pidStr, q) in map) {
                    val prod = BizzlyCalculator.productByIdStr(data, pidStr)
                    val name = prod?.n ?: "(supprimé)"
                    val pv = prod?.pv ?: 0.0
                    vRows += listOf(period.label, BizzlyCalculator.formatDateFr(date), name, q, pv, q * pv)
                }
            }
            for (inv in period.paid.sortedBy { it.d }) {
                val srvName = BizzlyCalculator.serverName(data, inv.sv)
                val mode = inv.m.ifBlank { "Espèces" }
                tRows += listOf(period.label, BizzlyCalculator.formatDateFr(inv.d), inv.t, srvName, mode, inv.tot)

                val pmKey = "${inv.d}|$mode"
                val pm = pmAgg.getOrPut(pmKey) { Agg(inv.d, mode) }
                pm.count++
                pm.total += inv.tot

                val svKey = "${inv.d}|${inv.sv}"
                val sv = srvAgg.getOrPut(svKey) { Agg(inv.d, srvName) }
                sv.count++
                sv.total += inv.tot
            }
            for (dep in period.dep) {
                val pName = dep.pid?.let { BizzlyCalculator.productById(data, it)?.n ?: "?" } ?: ""
                val pQty: Any = dep.pid?.let { dep.q ?: 0.0 } ?: ""
                dRows += listOf(period.label, BizzlyCalculator.formatDateFr(dep.d), dep.w, dep.m, pName, pQty)
            }
            for (ver in period.vers) {
                vrRows += listOf(period.label, BizzlyCalculator.formatDateFr(ver.d), ver.w, ver.m)
            }
            for (bon in period.bons) {
                bRows += listOf(period.label, BizzlyCalculator.formatDateFr(bon.d), bon.w, bon.m)
            }
        }

        srvAgg.keys.sorted().forEach { k ->
            val a = srvAgg[k]!!
            svRows += listOf(BizzlyCalculator.formatDateFr(a.date), a.label, a.count, a.total)
        }
        val pmRows = mutableListOf<List<Any>>(
            listOf("Date", "Mode de paiement", "Factures", "Total")
        )
        pmAgg.keys.sorted().forEach { k ->
            val a = pmAgg[k]!!
            pmRows += listOf(BizzlyCalculator.formatDateFr(a.date), a.label, a.count, a.total)
        }

        val invRows = mutableListOf<List<Any>>(
            listOf(
                "Produit", "Prix de vente", "Stock début", "Entrées",
                "Achats (dépenses)", "Vendu", "Stock théorique", "Compté",
                "Écart (compté − théorique)", "Alerte stock"
            )
        )
        for (p in data.prod) {
            val info = BizzlyCalculator.stockInfo(data, p)
            val counted = p.f
            val alertStr = when (info.level) {
                StockAlertLevel.CRITICAL -> "Critique"
                StockAlertLevel.LOW -> "Bas"
                StockAlertLevel.NONE -> ""
            }
            invRows += listOf(
                p.n,
                p.pv,
                p.d,
                p.e,
                info.purchased,
                info.soldLogged,
                info.theoretical,
                counted ?: "",
                if (counted != null) counted - info.theoretical else "",
                alertStr
            )
        }

        val histRows = mutableListOf<List<Any>>(
            listOf("Date de clôture", "Recettes", "Versé", "Dépenses", "Bons", "Manquant / excédent de stock")
        )
        for (h in data.hist.reversed()) {
            histRows += listOf(BizzlyCalculator.formatDateFr(h.d), h.att, h.ver, h.dep, h.bon, h.rest)
        }

        val logRows = mutableListOf<List<Any>>(listOf("Date et heure", "Opération"))
        for (l in data.log) {
            logRows += listOf(BizzlyCalculator.formatDateTimeFr(l.t), l.m)
        }

        val sheets = listOf(
            "Ventes" to vRows,
            "Factures par table" to tRows,
            "Par serveur" to svRows,
            "Par mode de paiement" to pmRows,
            "Inventaire" to invRows,
            "Dépenses" to dRows,
            "Versements" to vrRows,
            "Bons personnel" to bRows,
            "Périodes clôturées" to histRows,
            "Journal" to logRows
        )
        return buildXlsxZip(sheets)
    }

    private fun buildXlsxZip(sheets: List<Pair<String, List<List<Any>>>>): ByteArray {
        val xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        val mainNs = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
        val relNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"

        fun xesc(s: String): String = s
            .replace(Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f]"), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun colName(colIndex: Int): String {
            var i = colIndex + 1
            val sb = StringBuilder()
            while (i > 0) {
                val m = (i - 1) % 26
                sb.insert(0, ('A'.code + m).toChar())
                i = (i - 1) / 26
            }
            return sb.toString()
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun putEntry(name: String, xmlBody: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write((xmlHeader + xmlBody).toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            val overrides = sheets.indices.joinToString("") { idx ->
                "<Override PartName=\"/xl/worksheets/sheet${idx + 1}.xml\" " +
                    "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            }
            putEntry(
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                    overrides + "</Types>"
            )

            putEntry(
                "_rels/.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"${relNs}officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>"
            )

            val sheetTags = sheets.mapIndexed { idx, (name, _) ->
                "<sheet name=\"${xesc(name)}\" sheetId=\"${idx + 1}\" r:id=\"rId${idx + 1}\"/>"
            }.joinToString("")
            putEntry(
                "xl/workbook.xml",
                "<workbook $mainNs xmlns:r=\"${relNs.dropLast(1)}\"><sheets>$sheetTags</sheets></workbook>"
            )

            val wbRels = sheets.indices.joinToString("") { idx ->
                "<Relationship Id=\"rId${idx + 1}\" Type=\"${relNs}worksheet\" Target=\"worksheets/sheet${idx + 1}.xml\"/>"
            }
            putEntry(
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$wbRels" +
                    "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"${relNs}styles\" Target=\"styles.xml\"/></Relationships>"
            )

            putEntry(
                "xl/styles.xml",
                "<styleSheet $mainNs><fonts count=\"2\">" +
                    "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                    "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
                    "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                    "<cellXfs count=\"2\">" +
                    "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                    "</cellXfs></styleSheet>"
            )

            sheets.forEachIndexed { idx, (_, rows) ->
                val widths = mutableListOf<Int>()
                for (r in rows) {
                    r.forEachIndexed { cIdx, v ->
                        val len = v.toString().length + 2
                        while (widths.size <= cIdx) widths += 8
                        widths[cIdx] = min(45, max(widths[cIdx], len))
                    }
                }
                val colsXml = widths.mapIndexed { cIdx, w ->
                    "<col min=\"${cIdx + 1}\" max=\"${cIdx + 1}\" width=\"$w\" customWidth=\"1\"/>"
                }.joinToString("")

                val rowsXml = rows.mapIndexed { rIdx, r ->
                    val styleAttr = if (rIdx == 0) " s=\"1\"" else ""
                    val cellsXml = r.mapIndexed { cIdx, v ->
                        val ref = "${colName(cIdx)}${rIdx + 1}"
                        when (v) {
                            is Number -> {
                                val dv = v.toDouble()
                                val numStr = if (!dv.isFinite()) "0" else if (dv % 1.0 == 0.0) dv.toLong().toString() else dv.toString()
                                "<c r=\"$ref\"$styleAttr><v>$numStr</v></c>"
                            }
                            else -> {
                                val s = v.toString()
                                if (s.isEmpty()) "" else "<c r=\"$ref\" t=\"inlineStr\"$styleAttr><is><t xml:space=\"preserve\">${xesc(s)}</t></is></c>"
                            }
                        }
                    }.joinToString("")
                    "<row r=\"${rIdx + 1}\">$cellsXml</row>"
                }.joinToString("")

                putEntry(
                    "xl/worksheets/sheet${idx + 1}.xml",
                    "<worksheet $mainNs><cols>$colsXml</cols><sheetData>$rowsXml</sheetData></worksheet>"
                )
            }
        }
        return out.toByteArray()
    }

    fun sanitizeFilename(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "export-bizzly" }

    fun saveToDownloads(
        context: Context,
        filename: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val safeName = sanitizeFilename(filename)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Bizzly")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Impossible d'ouvrir le flux de destination")
            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun writeCacheExportUri(context: Context, filename: String, bytes: ByteArray): Uri? = runCatching {
        val safeName = sanitizeFilename(filename)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, safeName)
        FileOutputStream(file).use {
            it.write(bytes)
            it.flush()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    fun prepareExport(
        context: Context,
        filename: String,
        bytes: ByteArray,
        mimeType: String
    ): PreparedExport {
        val safeName = sanitizeFilename(filename)
        val downloadsUri = saveToDownloads(context, safeName, bytes, mimeType)
        val cacheUri = writeCacheExportUri(context, safeName, bytes)
        return PreparedExport(
            filename = safeName,
            mimeType = mimeType,
            bytes = bytes,
            downloadsUri = downloadsUri,
            cacheUri = cacheUri
        )
    }

    fun writeUriBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: error("Flux d'écriture indisponible")
        true
    }.getOrDefault(false)

    fun openExport(context: Context, uri: Uri, mimeType: String): Boolean {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(null, uri)
        }
        return runCatching {
            context.startActivity(viewIntent)
            true
        }.getOrDefault(false)
    }

    fun shareExport(
        context: Context,
        uri: Uri,
        filename: String,
        mimeType: String,
        chooserTitle: String
    ): Boolean {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            clipData = ClipData.newRawUri(filename, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun writeAndShareExport(
        context: Context,
        filename: String,
        bytes: ByteArray,
        mimeType: String,
        chooserTitle: String
    ): Boolean {
        val uri = writeCacheExportUri(context, filename, bytes) ?: return false
        return shareExport(context, uri, filename, mimeType, chooserTitle)
    }

    fun shareText(context: Context, text: String, title: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
