package com.example.bizzly.domain

import android.graphics.Bitmap
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.Product
import com.example.bizzly.model.ServerEntry
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

enum class StockAlertLevel {
    NONE,
    LOW,
    CRITICAL
}

data class StockInfo(
    val theoretical: Double,
    val current: Double,
    val tracked: Boolean,
    val soldEffective: Double,
    val purchased: Double,
    val soldLogged: Double,
    val level: StockAlertLevel
)

data class ProductSalesItem(
    val id: Long,
    val label: String,
    val amount: Double,
    val quantity: Double
)

data class PeriodSummary(
    val theoreticalRevenue: Double,
    val recordedRevenue: Double,
    val remitted: Double,
    val expenses: Double,
    val vouchers: Double,
    val remainingToRemit: Double,
    val stockGap: Double,
    val anomalies: List<String>,
    val topSales: List<ProductSalesItem>
)

data class PeriodSnapshot(
    val label: String,
    val sv: Map<String, Map<String, Double>>,
    val paid: List<com.example.bizzly.model.PaidInvoice>,
    val dep: List<com.example.bizzly.model.CashMovement>,
    val vers: List<com.example.bizzly.model.CashMovement>,
    val bons: List<com.example.bizzly.model.CashMovement>
)

data class TableBillingBreakdown(
    val grossSubtotal: Double,
    val compedDiscount: Double,
    val afterComped: Double,
    val tableDiscount: Double,
    val netTotal: Double,
    val paidSoFar: Double,
    val remainingDue: Double
)

object BizzlyCalculator {
    private val fcfaSymbols = DecimalFormatSymbols(Locale.FRANCE).apply {
        groupingSeparator = ' '
    }
    private val fcfaFormat = DecimalFormat("#,##0", fcfaSymbols)
    private val frDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
    private val frDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE)

    fun todayIso(): String = LocalDate.now().toString()

    fun formatDateFr(iso: String): String {
        return runCatching {
            LocalDate.parse(iso).format(frDateFormatter)
        }.getOrElse { iso }
    }

    fun formatDateTimeFr(epochMs: Long): String {
        return runCatching {
            Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .format(frDateTimeFormatter)
        }.getOrElse { "" }
    }

    fun formatFcfa(value: Double): String {
        val safe = if (value.isFinite()) value.roundToLong() else 0L
        return "${fcfaFormat.format(safe)} F"
    }

    fun formatQty(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(Locale.US, value)
    }

    fun slugify(input: String?): String {
        val normalized = Normalizer.normalize(input.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalized.ifEmpty { "entreprise" }
    }

    fun asciiUpperName(name: String): String {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^\\x20-\\x7e]"), "")
            .trim()
        return normalized.ifEmpty { "MENU" }.uppercase(Locale.ROOT)
    }

    fun clampNumber(v: Double): Double =
        if (!v.isFinite()) 0.0 else v.coerceIn(-1e12, 1e12)

    fun productById(data: BusinessData, id: Long): Product? =
        data.prod.firstOrNull { it.id == id }

    fun productByIdStr(data: BusinessData, idStr: String): Product? {
        val id = idStr.toLongOrNull() ?: return null
        return productById(data, id)
    }

    fun purchasedQty(data: BusinessData, pid: Long): Double =
        data.dep.filter { it.pid == pid }.sumOf { clampNumber(it.q ?: 0.0) }

    fun soldQty(data: BusinessData, pid: Long): Double {
        val key = pid.toString()
        var total = 0.0
        data.sv.values.forEach { dayMap ->
            total += clampNumber(dayMap[key] ?: 0.0)
        }
        return total
    }

    fun stockInfo(data: BusinessData, product: Product): StockInfo {
        val a = purchasedQty(data, product.id)
        val v = soldQty(data, product.id)
        val d = clampNumber(product.d)
        val e = clampNumber(product.e)
        val th = d + e + a - v
        val counted = product.f
        val cur = counted ?: th
        val tracked = counted != null || (d + e + a) > 0.0
        val soldEff = if (counted != null) d + e + a - counted else v
        val seuil = max(1, data.seuil)
        val criticalLimit = max(2.0, (seuil / 3.0).roundToInt().toDouble())
        val level = when {
            !tracked -> StockAlertLevel.NONE
            cur <= criticalLimit -> StockAlertLevel.CRITICAL
            cur < seuil -> StockAlertLevel.LOW
            else -> StockAlertLevel.NONE
        }
        return StockInfo(
            theoretical = th,
            current = cur,
            tracked = tracked,
            soldEffective = soldEff,
            purchased = a,
            soldLogged = v,
            level = level
        )
    }

    fun lowStockProducts(data: BusinessData): List<Pair<Product, StockInfo>> {
        return data.prod.map { it to stockInfo(data, it) }
            .filter { it.second.level != StockAlertLevel.NONE }
            .sortedBy { it.second.current }
    }

    fun dayTotal(data: BusinessData, dateIso: String): Double {
        val dayMap = data.sv[dateIso] ?: return 0.0
        var sum = 0.0
        for ((pidStr, qty) in dayMap) {
            val product = productByIdStr(data, pidStr) ?: continue
            sum += clampNumber(qty) * clampNumber(product.pv)
        }
        return sum
    }

    fun dayArticleCount(data: BusinessData, dateIso: String): Double {
        val dayMap = data.sv[dateIso] ?: return 0.0
        return dayMap.values.sumOf { clampNumber(it) }
    }

    fun autoRevenue(data: BusinessData): Double =
        data.sv.keys.sumOf { dayTotal(data, it) }

    fun dayAggregateSales(data: BusinessData): List<ProductSalesItem> {
        val byPid = LinkedHashMap<Long, ProductSalesItem>()
        for (dayMap in data.sv.values) {
            for ((pidStr, rawQty) in dayMap) {
                val qty = clampNumber(rawQty)
                if (qty <= 0.0) continue
                val p = productByIdStr(data, pidStr) ?: continue
                val existing = byPid[p.id]
                val amt = qty * clampNumber(p.pv)
                byPid[p.id] = if (existing == null) {
                    ProductSalesItem(p.id, p.n, amt, qty)
                } else {
                    existing.copy(
                        amount = existing.amount + amt,
                        quantity = existing.quantity + qty
                    )
                }
            }
        }
        return byPid.values.sortedByDescending { it.amount }
    }

    fun calculateSummary(data: BusinessData): PeriodSummary {
        var theoreticalRev = 0.0
        var gap = 0.0
        val anomalies = mutableListOf<String>()
        val stockSales = mutableListOf<ProductSalesItem>()

        for (p in data.prod) {
            val info = stockInfo(data, p)
            val pv = clampNumber(p.pv)
            val counted = p.f
            if (counted != null && counted > info.theoretical) {
                anomalies += p.n
            }
            theoreticalRev += info.soldEffective * pv
            if (counted != null) {
                gap += (info.theoretical - counted) * pv
            }
            if (info.soldEffective > 0.0) {
                stockSales += ProductSalesItem(p.id, p.n, info.soldEffective * pv, info.soldEffective)
            }
        }
        stockSales.sortByDescending { it.amount }

        val rec = autoRevenue(data)
        val ver = data.vers.sumOf { clampNumber(it.m) }
        val dep = data.dep.sumOf { clampNumber(it.m) }
        val bon = data.bons.sumOf { clampNumber(it.m) }
        val da = dayAggregateSales(data)

        return PeriodSummary(
            theoreticalRevenue = theoreticalRev,
            recordedRevenue = rec,
            remitted = ver,
            expenses = dep,
            vouchers = bon,
            remainingToRemit = rec - dep - bon - ver,
            stockGap = gap,
            anomalies = anomalies,
            topSales = da.ifEmpty { stockSales }
        )
    }

    fun allPeriods(data: BusinessData): List<PeriodSnapshot> {
        val list = data.arch.map {
            PeriodSnapshot(
                label = "Clôturée le ${formatDateFr(it.d)}",
                sv = it.sv,
                paid = it.paid,
                dep = it.dep,
                vers = it.vers,
                bons = it.bons
            )
        }.toMutableList()
        list += PeriodSnapshot(
            label = "En cours",
            sv = data.sv,
            paid = data.paid,
            dep = data.dep,
            vers = data.vers,
            bons = data.bons
        )
        return list
    }

    fun favoriteProducts(data: BusinessData, limit: Int = 6): List<Product> {
        val counts = mutableMapOf<Long, Double>()
        for (period in allPeriods(data)) {
            for (dayMap in period.sv.values) {
                for ((pidStr, q) in dayMap) {
                    val pid = pidStr.toLongOrNull() ?: continue
                    counts[pid] = (counts[pid] ?: 0.0) + q
                }
            }
        }
        val picked = counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { productById(data, it.key) }
            .take(limit)
            .toMutableList()
        for (p in data.prod) {
            if (picked.size >= limit) break
            if (picked.none { it.id == p.id }) picked += p
        }
        return picked
    }

    fun calculateTableBreakdown(data: BusinessData, table: OpenTable): TableBillingBreakdown {
        var gross = 0.0
        var compedSavings = 0.0
        for ((pidStr, rawQty) in table.it) {
            val qty = clampNumber(rawQty).coerceAtLeast(0.0)
            val p = productByIdStr(data, pidStr) ?: continue
            val price = clampNumber(p.pv).coerceAtLeast(0.0)
            gross += qty * price
            val compQty = (table.comped[pidStr] ?: 0.0).coerceIn(0.0, qty)
            compedSavings += compQty * price
        }
        val afterComped = (gross - compedSavings).coerceAtLeast(0.0)
        val tableDisc = when (table.discountType) {
            "percent" -> (afterComped * table.discountValue.coerceIn(0.0, 100.0) / 100.0).roundToLong().toDouble()
            "amount" -> table.discountValue.coerceIn(0.0, afterComped)
            else -> 0.0
        }
        val net = (afterComped - tableDisc).coerceAtLeast(0.0)
        val paidSoFar = table.paidPartial.coerceIn(0.0, net)
        val remaining = (net - paidSoFar).coerceAtLeast(0.0)
        return TableBillingBreakdown(
            grossSubtotal = gross,
            compedDiscount = compedSavings,
            afterComped = afterComped,
            tableDiscount = tableDisc,
            netTotal = net,
            paidSoFar = paidSoFar,
            remainingDue = remaining
        )
    }

    fun tableTotal(data: BusinessData, tableNum: Int): Double {
        val table = data.tb[tableNum.toString()] ?: return 0.0
        return calculateTableBreakdown(data, table).remainingDue
    }

    fun suggestedRestockQty(currentQty: Double, minThreshold: Double): Double {
        val target = (minThreshold.coerceAtLeast(1.0) * 2.0)
        return (target - currentQty.coerceAtLeast(0.0)).coerceAtLeast(1.0)
    }

    fun tableItemCount(data: BusinessData, tableNum: Int): Int {
        val table = data.tb[tableNum.toString()] ?: return 0
        return table.it.values.sumOf { it.roundToInt() }
    }

    fun isProductInOpenTable(data: BusinessData, pid: Long): Boolean {
        val key = pid.toString()
        return data.tb.values.any { (it.it[key] ?: 0.0) > 0.0 }
    }

    fun serverName(data: BusinessData, svId: Long): String {
        return data.srv.firstOrNull { it.id == svId }?.n
            ?: data.srvx.firstOrNull { it.id == svId }?.n
            ?: "Serveur"
    }

    fun syncServersWithAccount(account: BusinessAccount?, data: BusinessData): BusinessData {
        if (account == null) return data
        val servers = account.profiles.filter { it.role == BizzlyRole.SERVEUR.key }
        val oldArchive = data.srvx.toMutableList()
        for (s in data.srv) {
            if (servers.none { it.id == s.id } && oldArchive.none { it.id == s.id }) {
                oldArchive += s
            }
        }
        val nextSrv = servers.map { ServerEntry(it.id, it.name) }
            .ifEmpty { listOf(ServerEntry(9000001L, "Salle")) }
        return data.copy(srv = nextSrv, srvx = oldArchive)
    }

    // --- Color & Palette helpers matching PWA brand math ---

    fun hexToRgb(hex: String, fallback: Triple<Int, Int, Int> = Triple(37, 99, 235)): Triple<Int, Int, Int> {
        val clean = hex.trim().removePrefix("#")
        val expanded = if (clean.length == 3) {
            buildString {
                clean.forEach { append(it).append(it) }
            }
        } else clean
        if (expanded.length != 6) return fallback
        return runCatching {
            Triple(
                expanded.substring(0, 2).toInt(16),
                expanded.substring(2, 4).toInt(16),
                expanded.substring(4, 6).toInt(16)
            )
        }.getOrDefault(fallback)
    }

    fun rgbToHex(r: Int, g: Int, b: Int): String =
        "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    fun relativeLuminance(rgb: Triple<Int, Int, Int>): Double {
        fun comp(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * comp(rgb.first) + 0.7152 * comp(rgb.second) + 0.0722 * comp(rgb.third)
    }

    fun contrastRatio(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    fun mixRgb(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>, t: Double): Triple<Int, Int, Int> {
        return Triple(
            (a.first + (b.first - a.first) * t).roundToInt().coerceIn(0, 255),
            (a.second + (b.second - a.second) * t).roundToInt().coerceIn(0, 255),
            (a.third + (b.third - a.third) * t).roundToInt().coerceIn(0, 255)
        )
    }

    fun fitToContrast(color: Triple<Int, Int, Int>, background: Triple<Int, Int, Int>, minRatio: Double = 4.2): Triple<Int, Int, Int> {
        var current = color
        var t = 0.0
        val target = if (relativeLuminance(background) > 0.5) Triple(0, 0, 0) else Triple(255, 255, 255)
        while (contrastRatio(current, background) < minRatio && t < 1.0) {
            t += 0.05
            current = mixRgb(color, target, t)
        }
        return current
    }

    fun rgbToHsl(rgb: Triple<Int, Int, Int>): Triple<Float, Float, Float> {
        val r = rgb.first / 255f
        val g = rgb.second / 255f
        val b = rgb.third / 255f
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val l = (maxC + minC) / 2f
        if (maxC == minC) return Triple(0f, 0f, l)
        val d = maxC - minC
        val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
        val h = when (maxC) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        return Triple(h * 360f, s, l)
    }

    fun hslToRgb(hDeg: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        val hue = ((hDeg % 360f) + 360f) % 360f / 360f
        val sat = s.coerceIn(0f, 1f)
        val light = l.coerceIn(0f, 1f)
        if (sat == 0f) {
            val v = (light * 255f).roundToInt().coerceIn(0, 255)
            return Triple(v, v, v)
        }
        fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
            var t = tIn
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
        val p = 2f * light - q
        val r = (hue2rgb(p, q, hue + 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (hue2rgb(p, q, hue) * 255f).roundToInt().coerceIn(0, 255)
        val b = (hue2rgb(p, q, hue - 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        return Triple(r, g, b)
    }

    fun extractBrandThemeProposalsFromPixels(pixels: IntArray): List<com.example.bizzly.model.BrandThemeProposal> {
        data class Bucket(var n: Int = 0, var r: Long = 0, var g: Long = 0, var b: Long = 0)
        val buckets = HashMap<Int, Bucket>()
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a < 128) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            if (minOf(r, g, b) > 235) continue
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.n++
            bucket.r += r
            bucket.g += g
            bucket.b += b
        }
        if (buckets.isEmpty()) return emptyList()

        data class Candidate(val c: Triple<Int, Int, Int>, val n: Int, val sat: Double, val score: Double)
        val candidates = buckets.values.map { bk ->
            val r = (bk.r.toDouble() / bk.n).roundToInt()
            val g = (bk.g.toDouble() / bk.n).roundToInt()
            val b = (bk.b.toDouble() / bk.n).roundToInt()
            val mx = maxOf(r, g, b).toDouble()
            val mn = minOf(r, g, b).toDouble()
            val sat = if (mx > 0) (mx - mn) / mx else 0.0
            val lum = relativeLuminance(Triple(r, g, b))
            val chromaBoost = if (sat > 0.22 && lum in 0.03..0.72) 1.4 else 1.0
            Candidate(Triple(r, g, b), bk.n, sat, bk.n * (0.20 + sat * 1.35) * chromaBoost)
        }.sortedByDescending { it.score }

        fun colorDist(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
            val dx = (a.first - b.first).toDouble()
            val dy = (a.second - b.second).toDouble()
            val dz = (a.third - b.third).toDouble()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun tunePrimary(raw: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
            val lum = relativeLuminance(raw)
            return when {
                lum > 0.56 -> mixRgb(raw, Triple(0, 0, 0), 0.36)
                lum < 0.025 -> {
                    val (h, s, _) = rgbToHsl(raw)
                    if (s > 0.12f) hslToRgb(h, s.coerceAtLeast(0.45f), 0.28f) else Triple(30, 41, 59)
                }
                else -> raw
            }
        }

        fun tuneDark(raw: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
            val lum = relativeLuminance(raw)
            return if (lum > 0.14) mixRgb(raw, Triple(8, 12, 22), 0.72) else raw
        }

        val picked = mutableListOf<Candidate>()
        for (cand in candidates) {
            if (picked.isEmpty() || picked.all { colorDist(cand.c, it.c) > 68.0 && cand.n >= 2 }) {
                picked += cand
                if (picked.size >= 5) break
            }
        }
        val firstRaw = picked.first().c
        val secondRaw = picked.getOrNull(1)?.c
        val thirdRaw = picked.getOrNull(2)?.c

        val pr1 = tunePrimary(firstRaw)
        val (h1, s1, l1) = rgbToHsl(pr1)

        val sec1 = if (secondRaw != null && relativeLuminance(secondRaw) < 0.25) {
            tuneDark(secondRaw)
        } else {
            tuneDark(mixRgb(pr1, Triple(5, 10, 24), 0.76))
        }

        val ax1 = when {
            thirdRaw != null && relativeLuminance(thirdRaw) > 0.06 -> thirdRaw
            secondRaw != null && colorDist(secondRaw, pr1) > 70.0 && relativeLuminance(secondRaw) > 0.08 -> secondRaw
            else -> hslToRgb(h1 + 155f, maxOf(0.68f, s1), l1.coerceIn(0.46f, 0.62f))
        }

        val proposal1 = com.example.bizzly.model.BrandThemeProposal(
            name = "Fidèle au logo",
            subtitle = "Couleurs dominantes extraites du logo",
            brand = BrandColors(
                pr = rgbToHex(pr1.first, pr1.second, pr1.third),
                sec = rgbToHex(sec1.first, sec1.second, sec1.third),
                ax = rgbToHex(ax1.first, ax1.second, ax1.third)
            )
        )

        // Proposal 2: Vivid / Secondary-led contrast
        val rawPr2 = secondRaw?.takeIf { relativeLuminance(it) > 0.05 && colorDist(it, pr1) > 65.0 }
            ?: hslToRgb(h1 + 32f, maxOf(0.72f, s1), l1.coerceIn(0.36f, 0.52f))
        val pr2 = tunePrimary(rawPr2)
        val sec2 = tuneDark(firstRaw)
        val ax2 = if (colorDist(pr1, pr2) > 55.0) {
            mixRgb(pr1, Triple(255, 255, 255), 0.14)
        } else {
            hslToRgb(h1 + 195f, 0.76f, 0.56f)
        }
        val proposal2 = com.example.bizzly.model.BrandThemeProposal(
            name = "Vif & Contrasté",
            subtitle = "Accent dynamique inspiré du logo",
            brand = BrandColors(
                pr = rgbToHex(pr2.first, pr2.second, pr2.third),
                sec = rgbToHex(sec2.first, sec2.second, sec2.third),
                ax = rgbToHex(ax2.first, ax2.second, ax2.third)
            )
        )

        // Proposal 3: Refined & Deep
        val pr3 = mixRgb(pr1, sec1, 0.34)
        val sec3 = mixRgb(sec1, Triple(4, 8, 18), 0.45)
        val ax3 = mixRgb(ax1, Triple(255, 255, 255), 0.18)
        val proposal3 = com.example.bizzly.model.BrandThemeProposal(
            name = "Élégant & Feutré",
            subtitle = "Contraste doux pour un style moderne",
            brand = BrandColors(
                pr = rgbToHex(pr3.first, pr3.second, pr3.third),
                sec = rgbToHex(sec3.first, sec3.second, sec3.third),
                ax = rgbToHex(ax3.first, ax3.second, ax3.third)
            )
        )

        return listOf(proposal1, proposal2, proposal3).distinctBy { "${it.brand.pr}:${it.brand.sec}:${it.brand.ax}" }
    }

    fun extractBrandThemeProposals(bitmap: Bitmap): List<com.example.bizzly.model.BrandThemeProposal> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
        val pixels = IntArray(80 * 80)
        scaled.getPixels(pixels, 0, 80, 0, 0, 80, 80)
        if (scaled != bitmap) scaled.recycle()
        return extractBrandThemeProposalsFromPixels(pixels)
    }

    fun extractBrandPalette(bitmap: Bitmap): BrandColors? {
        return extractBrandThemeProposals(bitmap).firstOrNull()?.brand
    }
}
