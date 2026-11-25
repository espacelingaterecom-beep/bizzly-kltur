package com.example.bizzly.domain

import android.graphics.Bitmap
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.BizzlyRole
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

    fun tableTotal(data: BusinessData, tableNum: Int): Double {
        val table = data.tb[tableNum.toString()] ?: return 0.0
        var sum = 0.0
        for ((pidStr, q) in table.it) {
            val p = productByIdStr(data, pidStr) ?: continue
            sum += clampNumber(q) * clampNumber(p.pv)
        }
        return sum
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

    fun extractBrandPalette(bitmap: Bitmap): BrandColors? {
        val scaled = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
        val pixels = IntArray(80 * 80)
        scaled.getPixels(pixels, 0, 80, 0, 0, 80, 80)
        if (scaled != bitmap) scaled.recycle()

        data class Bucket(var n: Int = 0, var r: Long = 0, var g: Long = 0, var b: Long = 0)
        val buckets = HashMap<Int, Bucket>()
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a < 140) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            if (minOf(r, g, b) > 232) continue
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.n++
            bucket.r += r
            bucket.g += g
            bucket.b += b
        }
        if (buckets.isEmpty()) return null

        data class Candidate(val c: Triple<Int, Int, Int>, val n: Int, val score: Double)
        val candidates = buckets.values.map { bk ->
            val r = (bk.r.toDouble() / bk.n).roundToInt()
            val g = (bk.g.toDouble() / bk.n).roundToInt()
            val b = (bk.b.toDouble() / bk.n).roundToInt()
            val mx = maxOf(r, g, b).toDouble()
            val mn = minOf(r, g, b).toDouble()
            val sat = if (mx > 0) (mx - mn) / mx else 0.0
            Candidate(Triple(r, g, b), bk.n, bk.n * (0.25 + sat))
        }.sortedByDescending { it.score }

        fun farFrom(c: Triple<Int, Int, Int>, picked: List<Triple<Int, Int, Int>>): Boolean {
            return picked.all { p ->
                val dx = (c.first - p.first).toDouble()
                val dy = (c.second - p.second).toDouble()
                val dz = (c.third - p.third).toDouble()
                sqrt(dx * dx + dy * dy + dz * dz) > 80.0
            }
        }

        var pr = candidates.first().c
        val picked = mutableListOf(pr)
        var sec: Triple<Int, Int, Int>? = null
        var ax: Triple<Int, Int, Int>? = null
        for (i in 1 until candidates.size) {
            if (sec != null && ax != null) break
            val cand = candidates[i]
            if (cand.n >= 3 && farFrom(cand.c, picked)) {
                if (sec == null) {
                    sec = cand.c
                    picked += sec
                } else if (relativeLuminance(cand.c) > 0.03) {
                    ax = cand.c
                    picked += ax
                }
            }
        }
        if (relativeLuminance(pr) > 0.55) pr = mixRgb(pr, Triple(0, 0, 0), 0.35)
        sec = if (sec == null) {
            mixRgb(pr, Triple(0, 0, 0), 0.72)
        } else if (relativeLuminance(sec) > 0.2) {
            mixRgb(sec, Triple(0, 0, 0), 0.65)
        } else sec
        if (ax == null) ax = mixRgb(pr, Triple(255, 255, 255), 0.4)

        return BrandColors(
            pr = rgbToHex(pr.first, pr.second, pr.third),
            sec = rgbToHex(sec.first, sec.second, sec.third),
            ax = rgbToHex(ax.first, ax.second, ax.third)
        )
    }
}
