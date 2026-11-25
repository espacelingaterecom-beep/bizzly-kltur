package com.example.bizzly.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.bizzly.data.local.BizzlyDao
import com.example.bizzly.data.local.BusinessAccountEntity
import com.example.bizzly.data.local.BusinessDataEntity
import com.example.bizzly.data.local.SyncStateEntity
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BackupEnvelope
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.BusinessProfile
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.CashMovement
import com.example.bizzly.model.ClosedPeriod
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaidInvoice
import com.example.bizzly.model.Product
import com.example.bizzly.model.ServerEntry
import com.example.bizzly.model.SyncCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToLong

class BizzlyRepository(
    context: Context,
    private val dao: BizzlyDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bizzly_prefs", Context.MODE_PRIVATE)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    val accountsFlow: Flow<List<BusinessAccount>> = dao.observeAccounts().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { sanitizeAccount(json.decodeFromString<BusinessAccount>(entity.jsonPayload)) }
                .getOrNull()
        }
    }

    suspend fun getAllAccounts(): List<BusinessAccount> {
        return dao.getAllAccounts().mapNotNull { entity ->
            runCatching { sanitizeAccount(json.decodeFromString<BusinessAccount>(entity.jsonPayload)) }
                .getOrNull()
        }
    }

    suspend fun getAccount(businessId: String): BusinessAccount? {
        val entity = dao.getAccountById(businessId) ?: return null
        return runCatching { sanitizeAccount(json.decodeFromString<BusinessAccount>(entity.jsonPayload)) }
            .getOrNull()
    }

    suspend fun saveAccount(account: BusinessAccount) {
        val clean = sanitizeAccount(account) ?: account
        dao.upsertAccount(
            BusinessAccountEntity(
                id = clean.id,
                ident = clean.ident,
                name = clean.name,
                jsonPayload = json.encodeToString(clean)
            )
        )
    }

    suspend fun deleteBusiness(businessId: String) {
        dao.deleteAccount(businessId)
        dao.deleteBusinessData(businessId)
        dao.deleteSyncState(businessId)
        if (getLastBusinessId() == businessId) {
            setLastBusinessId(null)
        }
    }

    suspend fun getBusinessData(businessId: String): BusinessData {
        val entity = dao.getBusinessData(businessId) ?: return freshBusinessData()
        return runCatching {
            sanitizeData(json.decodeFromString<BusinessData>(entity.jsonPayload))
        }.getOrElse { freshBusinessData() }
    }

    suspend fun saveBusinessData(businessId: String, data: BusinessData) {
        val clean = sanitizeData(data)
        dao.upsertBusinessData(
            BusinessDataEntity(
                businessId = businessId,
                jsonPayload = json.encodeToString(clean)
            )
        )
    }

    suspend fun getSyncCredentials(businessId: String): SyncCredentials? {
        val entity = dao.getSyncState(businessId) ?: return null
        if (!Regex("^[0-9A-Z]{20}$").matches(entity.syncKey)) return null
        val snap = runCatching {
            json.decodeFromString<Map<String, String>>(entity.snapJson)
        }.getOrDefault(emptyMap())
        return SyncCredentials(key = entity.syncKey, seq = entity.seq, snap = snap)
    }

    suspend fun getAllSyncKeys(): Set<String> {
        return dao.getAllSyncStates().map { it.syncKey }.toSet()
    }

    suspend fun saveSyncCredentials(businessId: String, creds: SyncCredentials) {
        dao.upsertSyncState(
            SyncStateEntity(
                businessId = businessId,
                syncKey = creds.key,
                seq = creds.seq,
                snapJson = json.encodeToString(creds.snap)
            )
        )
    }

    suspend fun clearSyncCredentials(businessId: String) {
        dao.deleteSyncState(businessId)
    }

    fun getLastBusinessId(): String? = prefs.getString("last_business_id", null)

    fun setLastBusinessId(businessId: String?) {
        prefs.edit().putString("last_business_id", businessId).apply()
    }

    fun getDarkThemePreference(): Boolean? {
        return if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme", false) else null
    }

    fun setDarkThemePreference(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
    }

    fun createBackupJson(account: BusinessAccount, data: BusinessData): String {
        val envelope = BackupEnvelope(
            bz = 2,
            account = account,
            data = data,
            at = Instant.now().toString()
        )
        return json.encodeToString(envelope)
    }

    sealed class ParsedBackup {
        data class FullV2(val account: BusinessAccount, val data: BusinessData) : ParsedBackup()
        data class LegacyDataOnly(val data: BusinessData) : ParsedBackup()
    }

    fun parseBackupJson(raw: String): ParsedBackup? {
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val bzVal = root["bz"]?.toString()?.toIntOrNull()
            if (bzVal == 2 && root.containsKey("account") && root.containsKey("data")) {
                val accRaw = json.decodeFromJsonElement(BusinessAccount.serializer(), root["account"]!!)
                val dataRaw = json.decodeFromJsonElement(BusinessData.serializer(), root["data"]!!)
                val acc = sanitizeAccount(accRaw) ?: return@runCatching null
                val data = sanitizeData(dataRaw)
                ParsedBackup.FullV2(acc, data)
            } else if (root.containsKey("prod")) {
                val dataRaw = json.decodeFromJsonElement(BusinessData.serializer(), JsonObject(root))
                ParsedBackup.LegacyDataOnly(sanitizeData(dataRaw))
            } else {
                null
            }
        }.getOrNull()
    }

    fun freshBusinessData(): BusinessData = BusinessData(
        prod = emptyList(),
        srv = emptyList(),
        nt = 20,
        seuil = 10,
        next = 1L,
        fv2 = 1
    )

    fun sanitizeAccount(raw: BusinessAccount): BusinessAccount? {
        val cleanId = raw.id.replace(Regex("[^a-zA-Z0-9]"), "").take(24)
        val cleanName = cleanText(raw.name, 60).trim()
        if (cleanId.isEmpty() || cleanName.isEmpty()) return null
        val cleanIdent = BizzlyCalculator.slugify(raw.ident.ifBlank { cleanName })
        val validLogo = if (
            Regex("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$").matches(raw.logo) &&
            raw.logo.length < 2_000_000
        ) raw.logo else ""
        val lw = if (validLogo.isNotEmpty()) raw.lw.coerceIn(1, 5000) else 0
        val lh = if (validLogo.isNotEmpty()) raw.lh.coerceIn(1, 5000) else 0
        val brand = BrandColors(
            pr = cleanHex6(raw.brand.pr, "#2563EB"),
            sec = cleanHex6(raw.brand.sec, "#0A1F44"),
            ax = cleanHex6(raw.brand.ax, "#FF6B6B")
        )
        val seenIds = mutableSetOf<Long>()
        val profiles = raw.profiles.take(100).mapNotNull { p ->
            if (p.id <= 0L || !seenIds.add(p.id)) return@mapNotNull null
            val role = BizzlyRole.entries.firstOrNull { it.key == p.role } ?: return@mapNotNull null
            val hasHash = Regex("^[0-9a-f]{16,64}$").matches(p.hash) &&
                Regex("^[a-z0-9]{4,40}$").matches(p.salt)
            BusinessProfile(
                id = p.id,
                role = role.key,
                name = cleanText(p.name, 40).trim().ifEmpty { role.label },
                salt = if (hasHash) p.salt else "",
                hash = if (hasHash) p.hash else "",
                v = if (hasHash && p.v == 2) 2 else if (hasHash) 1 else 2
            )
        }
        if (profiles.none { it.role == BizzlyRole.GERANT.key }) return null
        return BusinessAccount(
            id = cleanId,
            ident = cleanIdent,
            name = cleanName,
            email = cleanText(raw.email, 120),
            tel = cleanText(raw.tel, 40),
            addr = cleanText(raw.addr, 120),
            logo = validLogo,
            lw = lw,
            lh = lh,
            brand = brand,
            profiles = profiles,
            created = cleanText(raw.created, 40)
        )
    }

    fun sanitizeData(raw: BusinessData): BusinessData {
        val fv2 = raw.fv2 != 0
        val prod = raw.prod.take(5000).map { p ->
            val counted = if (!fv2 && p.f == 0.0) null else p.f?.let { BizzlyCalculator.clampNumber(it) }
            Product(
                id = p.id,
                n = cleanText(p.n, 80),
                pv = BizzlyCalculator.clampNumber(p.pv),
                d = BizzlyCalculator.clampNumber(p.d),
                e = BizzlyCalculator.clampNumber(p.e),
                f = counted
            )
        }
        fun cleanMove(m: CashMovement) = CashMovement(
            d = cleanDate(m.d),
            w = cleanText(m.w, 80),
            m = BizzlyCalculator.clampNumber(m.m),
            pid = m.pid,
            q = if (m.pid != null) BizzlyCalculator.clampNumber(m.q ?: 0.0) else null,
            u = cleanUid(m.u),
            ts = m.ts
        )
        fun cleanPaid(p: PaidInvoice) = PaidInvoice(
            d = cleanDate(p.d),
            t = p.t,
            sv = p.sv,
            tot = BizzlyCalculator.clampNumber(p.tot),
            m = cleanText(p.m, 30).ifBlank { "Espèces" },
            u = cleanUid(p.u),
            ts = p.ts
        )
        fun cleanSvMap(sv: Map<String, Map<String, Double>>): Map<String, Map<String, Double>> {
            val out = LinkedHashMap<String, Map<String, Double>>()
            for ((date, items) in sv) {
                if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) continue
                val cleanItems = LinkedHashMap<String, Double>()
                for ((pidStr, q) in items) {
                    if (Regex("^\\d{1,15}$").matches(pidStr) && q != 0.0) {
                        cleanItems[pidStr] = BizzlyCalculator.clampNumber(q)
                    }
                }
                if (cleanItems.isNotEmpty()) out[date] = cleanItems
            }
            return out
        }

        val cleanTb = LinkedHashMap<String, OpenTable>()
        for ((key, tbl) in raw.tb) {
            if (!Regex("^\\d{1,4}$").matches(key)) continue
            val items = LinkedHashMap<String, Double>()
            for ((pidStr, q) in tbl.it) {
                if (Regex("^\\d{1,15}$").matches(pidStr) && q > 0.0) {
                    items[pidStr] = BizzlyCalculator.clampNumber(q)
                }
            }
            if (items.isNotEmpty() || tbl.sv != 0L) {
                cleanTb[key] = OpenTable(sv = tbl.sv, it = items)
            }
        }

        val maxProdId = (prod.maxOfOrNull { it.id } ?: 0L) + 1L
        return BusinessData(
            prod = prod,
            vers = raw.vers.take(50_000).map(::cleanMove),
            dep = raw.dep.take(50_000).map(::cleanMove),
            bons = raw.bons.take(50_000).map(::cleanMove),
            hist = raw.hist.take(50_000).map { h ->
                ClosedPeriod(
                    d = cleanDate(h.d),
                    att = BizzlyCalculator.clampNumber(h.att),
                    ver = BizzlyCalculator.clampNumber(h.ver),
                    dep = BizzlyCalculator.clampNumber(h.dep),
                    bon = BizzlyCalculator.clampNumber(h.bon),
                    rest = BizzlyCalculator.clampNumber(h.rest),
                    u = cleanUid(h.u),
                    ts = h.ts
                )
            },
            paid = raw.paid.take(50_000).map(::cleanPaid),
            sv = cleanSvMap(raw.sv),
            tb = cleanTb,
            srv = raw.srv.take(200).map { ServerEntry(it.id, cleanText(it.n, 40)) },
            srvx = raw.srvx.take(500).map { ServerEntry(it.id, cleanText(it.n, 40)) },
            log = raw.log.take(150).map { it.copy(m = cleanText(it.m, 300)) },
            arch = raw.arch.take(400).map { a ->
                ArchivePeriod(
                    d = cleanDate(a.d),
                    sv = cleanSvMap(a.sv),
                    paid = a.paid.map(::cleanPaid),
                    dep = a.dep.map(::cleanMove),
                    vers = a.vers.map(::cleanMove),
                    bons = a.bons.map(::cleanMove),
                    u = cleanUid(a.u),
                    ts = a.ts
                )
            },
            nt = raw.nt.coerceIn(1, 200),
            seuil = raw.seuil.coerceIn(1, 100_000),
            next = max(raw.next, maxProdId),
            lbk = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw.lbk)) raw.lbk else "",
            fv2 = 1
        )
    }

    private fun cleanText(value: String, maxLen: Int): String =
        value.replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").take(maxLen)

    private fun cleanDate(value: String): String =
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) value else BizzlyCalculator.todayIso()

    private fun cleanUid(value: String): String =
        if (Regex("^[a-z0-9]{6,24}$").matches(value)) value else ""

    private fun cleanHex6(value: String, fallback: String): String =
        if (Regex("^#[0-9a-fA-F]{6}$").matches(value)) value else fallback
}
