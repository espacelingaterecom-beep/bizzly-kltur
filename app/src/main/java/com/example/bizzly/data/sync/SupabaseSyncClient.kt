package com.example.bizzly.data.sync

import com.example.BuildConfig
import com.example.bizzly.data.repository.BizzlyRepository
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.CashMovement
import com.example.bizzly.model.ClosedPeriod
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaidInvoice
import com.example.bizzly.model.Product
import com.example.bizzly.model.ServerEntry
import com.example.bizzly.model.SyncCredentials
import com.example.bizzly.security.PinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlin.math.max

@Serializable
data class SyncSnapshotSummary(
    val day: String,
    val n: Int = 0
)

class SyncException(message: String, val isInvalidKey: Boolean = false, val isOffline: Boolean = false) :
    Exception(message)

class SupabaseSyncClient(private val repository: BizzlyRepository) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val b32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val uidChars = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()

    fun generateSyncKey(): String {
        val sb = StringBuilder(20)
        repeat(20) {
            sb.append(b32[random.nextInt(32)])
        }
        return sb.toString()
    }

    fun formatSyncKey(key: String): String =
        key.chunked(5).joinToString("-")

    fun normalizeSyncKey(raw: String): String {
        return raw.uppercase()
            .replace(Regex("[^0-9A-Z]"), "")
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .replace('U', 'V')
    }

    fun newUid(): String {
        val sb = StringBuilder(16)
        repeat(16) { sb.append(uidChars[random.nextInt(uidChars.length)]) }
        return sb.toString()
    }

    fun canonicalJson(element: JsonElement): String {
        return when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> element.toString()
            is JsonArray -> element.joinToString(",", "[", "]") { canonicalJson(it) }
            is JsonObject -> {
                element.keys.sorted().joinToString(",", "{", "}") { k ->
                    "${Json.encodeToString(k)}:${canonicalJson(element[k]!!)}"
                }
            }
        }
    }

    private fun prepareUids(data: BusinessData): BusinessData {
        val now = System.currentTimeMillis()
        fun ensureMoves(list: List<CashMovement>): List<CashMovement> {
            val n = list.size
            return list.mapIndexed { idx, m ->
                m.copy(
                    u = m.u.ifBlank { newUid() },
                    ts = if (m.ts != 0L) m.ts else now - (n - idx)
                )
            }
        }
        val paid = data.paid.mapIndexed { idx, p ->
            p.copy(
                u = p.u.ifBlank { newUid() },
                ts = if (p.ts != 0L) p.ts else now - idx
            )
        }
        val hist = data.hist.mapIndexed { idx, h ->
            h.copy(
                u = h.u.ifBlank { newUid() },
                ts = if (h.ts != 0L) h.ts else now - idx
            )
        }
        val arch = data.arch.mapIndexed { idx, a ->
            a.copy(
                u = a.u.ifBlank { newUid() },
                ts = if (a.ts != 0L) a.ts else now - (data.arch.size - idx)
            )
        }
        return data.copy(
            vers = ensureMoves(data.vers),
            dep = ensureMoves(data.dep),
            bons = ensureMoves(data.bons),
            paid = paid,
            hist = hist,
            arch = arch
        )
    }

    fun buildDocumentsMap(account: BusinessAccount?, data: BusinessData): Pair<BusinessData, Map<String, String>> {
        val prepped = prepareUids(data)
        val docs = LinkedHashMap<String, String>()
        if (account != null) {
            val accObj = json.encodeToJsonElement(BusinessAccount.serializer(), account).jsonObject
            val withoutId = JsonObject(accObj.filterKeys { it != "id" })
            docs["acct/1"] = canonicalJson(withoutId)
        }
        for (p in prepped.prod) {
            docs["prod/${p.id}"] = canonicalJson(json.encodeToJsonElement(Product.serializer(), p))
        }
        prepped.vers.forEach { docs["vers/${it.u}"] = canonicalJson(json.encodeToJsonElement(CashMovement.serializer(), it)) }
        prepped.dep.forEach { docs["dep/${it.u}"] = canonicalJson(json.encodeToJsonElement(CashMovement.serializer(), it)) }
        prepped.bons.forEach { docs["bons/${it.u}"] = canonicalJson(json.encodeToJsonElement(CashMovement.serializer(), it)) }
        prepped.paid.forEach { docs["paid/${it.u}"] = canonicalJson(json.encodeToJsonElement(PaidInvoice.serializer(), it)) }
        prepped.hist.forEach { docs["hist/${it.u}"] = canonicalJson(json.encodeToJsonElement(ClosedPeriod.serializer(), it)) }
        prepped.arch.forEach { docs["arch/${it.u}"] = canonicalJson(json.encodeToJsonElement(ArchivePeriod.serializer(), it)) }

        for ((date, dayMap) in prepped.sv) {
            for ((pidStr, q) in dayMap) {
                if (q != 0.0) {
                    docs["sv/$date/$pidStr"] = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
                }
            }
        }
        for ((tableKey, tbl) in prepped.tb) {
            docs["tb/$tableKey"] = canonicalJson(JsonObject(mapOf("sv" to JsonPrimitive(tbl.sv))))
            for ((pidStr, q) in tbl.it) {
                if (q != 0.0) {
                    docs["ti/$tableKey/$pidStr"] = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
                }
            }
        }
        val metaObj = JsonObject(
            mapOf(
                "nt" to JsonPrimitive(prepped.nt),
                "seuil" to JsonPrimitive(prepped.seuil),
                "next" to JsonPrimitive(prepped.next),
                "srvx" to json.encodeToJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(ServerEntry.serializer()),
                    prepped.srvx
                )
            )
        )
        docs["meta/1"] = canonicalJson(metaObj)
        return prepped to docs
    }

    suspend fun rpc(functionName: String, params: JsonObject): JsonElement = withContext(Dispatchers.IO) {
        val url = URL("${BuildConfig.SUPABASE_URL}/rest/v1/rpc/$functionName")
        val conn = (url.openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(params.toString())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                val parsedMsg = runCatching {
                    val obj = json.parseToJsonElement(body).jsonObject
                    obj["message"]?.jsonPrimitive?.content ?: obj["error"]?.jsonPrimitive?.content
                }.getOrNull() ?: "HTTP $code"
                val invalidKey = parsedMsg.contains("invalid key", ignoreCase = true)
                throw SyncException(parsedMsg, isInvalidKey = invalidKey, isOffline = false)
            }

            if (body.isBlank()) JsonNull else json.parseToJsonElement(body)
        } catch (e: SyncException) {
            throw e
        } catch (e: Exception) {
            throw SyncException(
                e.message ?: "Hors ligne",
                isInvalidKey = false,
                isOffline = true
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun createSyncSpace(businessName: String): String {
        val key = generateSyncKey()
        rpc(
            "bz_create",
            JsonObject(
                mapOf(
                    "p_key" to JsonPrimitive(key),
                    "p_name" to JsonPrimitive(businessName)
                )
            )
        )
        return key
    }

    suspend fun rotateSyncKey(oldKey: String): String {
        val newKey = generateSyncKey()
        rpc(
            "bz_rotate",
            JsonObject(
                mapOf(
                    "p_key" to JsonPrimitive(oldKey),
                    "p_new_key" to JsonPrimitive(newKey)
                )
            )
        )
        return newKey
    }

    suspend fun fetchSnapshots(key: String): List<SyncSnapshotSummary> {
        val res = rpc("bz_snaps", JsonObject(mapOf("p_key" to JsonPrimitive(key))))
        if (res !is JsonArray) return emptyList()
        return res.mapNotNull { el ->
            runCatching { json.decodeFromJsonElement(SyncSnapshotSummary.serializer(), el) }.getOrNull()
        }
    }

    suspend fun fetchSnapshotData(key: String, day: String, baseServers: List<ServerEntry>): BusinessData {
        val res = rpc(
            "bz_snap_get",
            JsonObject(mapOf("p_key" to JsonPrimitive(key), "p_day" to JsonPrimitive(day)))
        )
        if (res !is JsonArray) throw SyncException("Copie introuvable.")
        var st = repository.freshBusinessData().copy(srv = baseServers)
        for (el in res) {
            val obj = el.jsonObject
            val k = obj["k"]?.jsonPrimitive?.content ?: continue
            if (k == "acct/1") continue
            val d = obj["d"]
            st = applyDoc(st, k, d, d == null || d is JsonNull)
        }
        return repository.sanitizeData(finishSync(st))
    }

    suspend fun pushChanges(
        creds: SyncCredentials,
        account: BusinessAccount,
        data: BusinessData
    ): Pair<SyncCredentials, BusinessData> {
        val (prepped, docs) = buildDocumentsMap(account, data)
        val curHashes = LinkedHashMap<String, String>()
        val changes = mutableListOf<JsonObject>()

        for ((k, docStr) in docs) {
            val h = PinSecurity.h53(docStr)
            curHashes[k] = h
            if (creds.snap[k] != h) {
                changes += JsonObject(
                    mapOf(
                        "k" to JsonPrimitive(k),
                        "d" to json.parseToJsonElement(docStr)
                    )
                )
            }
        }
        for (oldKey in creds.snap.keys) {
            if (!docs.containsKey(oldKey)) {
                changes += JsonObject(
                    mapOf(
                        "k" to JsonPrimitive(oldKey),
                        "d" to JsonNull
                    )
                )
            }
        }

        val updatedSnap = creds.snap.toMutableMap()
        var idx = 0
        while (idx < changes.size) {
            val end = minOf(idx + 150, changes.size)
            val part = JsonArray(changes.subList(idx, end))
            rpc(
                "bz_push",
                JsonObject(
                    mapOf(
                        "p_key" to JsonPrimitive(creds.key),
                        "p_docs" to part
                    )
                )
            )
            for (item in part) {
                val obj = item.jsonObject
                val k = obj["k"]!!.jsonPrimitive.content
                val d = obj["d"]
                if (d == null || d is JsonNull) {
                    updatedSnap.remove(k)
                } else {
                    updatedSnap[k] = curHashes[k]!!
                }
            }
            idx = end
        }
        return creds.copy(snap = updatedSnap) to prepped
    }

    data class PullResult(
        val credentials: SyncCredentials,
        val account: BusinessAccount,
        val data: BusinessData,
        val changedCount: Int,
        val openTableGone: Int?,
        val mustLockProfileRemoved: Boolean
    )

    suspend fun pullChanges(
        creds: SyncCredentials,
        account: BusinessAccount,
        data: BusinessData,
        seenVersions: MutableMap<String, Long>,
        activeProfileId: Long?,
        currentOpenTable: Int?
    ): PullResult {
        var since = creds.seq
        var recent = true
        val pulledDocs = mutableListOf<JsonObject>()
        for (page in 0 until 60) {
            val res = rpc(
                "bz_pull",
                JsonObject(
                    mapOf(
                        "p_key" to JsonPrimitive(creds.key),
                        "p_since" to JsonPrimitive(since),
                        "p_recent" to JsonPrimitive(recent)
                    )
                )
            ).jsonObject
            val docsArr = res["docs"]?.jsonArray
            docsArr?.forEach { if (it is JsonObject) pulledDocs += it }
            val respSeq = res["seq"]?.jsonPrimitive?.longOrNull ?: since
            since = max(since, respSeq)
            recent = false
            val more = res["more"]?.jsonPrimitive?.booleanOrNull == true
            if (!more) break
        }

        val fresh = pulledDocs.filter { doc ->
            val k = doc["k"]?.jsonPrimitive?.content ?: return@filter false
            val s = doc["s"]?.jsonPrimitive?.longOrNull ?: -1L
            seenVersions[k] != s
        }

        var nextAccount = account
        var nextData = data
        var changedCount = 0
        var openTableGone: Int? = null
        var mustLock = false
        val updatedSnap = creds.snap.toMutableMap()

        if (fresh.isNotEmpty()) {
            val (_, localDocs) = buildDocumentsMap(nextAccount, nextData)
            for (doc in fresh) {
                val k = doc["k"]?.jsonPrimitive?.content ?: continue
                val s = doc["s"]?.jsonPrimitive?.longOrNull ?: 0L
                val isDel = doc["del"]?.jsonPrimitive?.booleanOrNull == true || doc["d"] == null || doc["d"] is JsonNull
                seenVersions[k] = s

                if (currentOpenTable != null && currentOpenTable > 0 && k == "tb/$currentOpenTable" && isDel) {
                    openTableGone = currentOpenTable
                }

                val hasPendingLocal = if (localDocs.containsKey(k)) {
                    PinSecurity.h53(localDocs[k]!!) != updatedSnap[k]
                } else {
                    updatedSnap.containsKey(k)
                }
                if (hasPendingLocal) continue

                val remoteEl = doc["d"]
                if (!isDel && remoteEl != null && localDocs.containsKey(k) && canonicalJson(remoteEl) == localDocs[k]) {
                    updatedSnap[k] = PinSecurity.h53(localDocs[k]!!)
                    continue
                }

                if (k == "acct/1") {
                    if (!isDel && remoteEl is JsonObject) {
                        val mergedObj = JsonObject(remoteEl.toMutableMap().apply {
                            put("id", JsonPrimitive(nextAccount.id))
                        })
                        val parsedAcc = runCatching {
                            json.decodeFromJsonElement(BusinessAccount.serializer(), mergedObj)
                        }.getOrNull()?.let { repository.sanitizeAccount(it) }
                        if (parsedAcc != null) {
                            nextAccount = parsedAcc.copy(id = nextAccount.id)
                            if (activeProfileId != null &&
                                nextAccount.profiles.none { it.id == activeProfileId && it.hash.isNotBlank() }
                            ) {
                                mustLock = true
                            }
                        }
                    }
                    continue
                }

                nextData = applyDoc(nextData, k, remoteEl, isDel)
                changedCount++
            }
            if (seenVersions.size > 3000) seenVersions.clear()
            if (changedCount > 0) {
                nextData = repository.sanitizeData(
                    BizzlyCalculator.syncServersWithAccount(nextAccount, finishSync(nextData))
                )
                val (_, afterDocs) = buildDocumentsMap(nextAccount, nextData)
                for (k in afterDocs.keys) {
                    updatedSnap[k] = PinSecurity.h53(afterDocs[k]!!)
                }
                val keysToDrop = updatedSnap.keys.filter { !afterDocs.containsKey(it) }
                keysToDrop.forEach { updatedSnap.remove(it) }
            }
        }

        val nextCreds = creds.copy(seq = since, snap = updatedSnap)
        return PullResult(
            credentials = nextCreds,
            account = nextAccount,
            data = nextData,
            changedCount = changedCount,
            openTableGone = openTableGone,
            mustLockProfileRemoved = mustLock
        )
    }

    data class LinkPhoneResult(
        val account: BusinessAccount,
        val data: BusinessData,
        val credentials: SyncCredentials
    )

    suspend fun linkNewPhone(normalizedCode: String, existingIdents: Set<String>): LinkPhoneResult {
        var since = 0L
        val docs = mutableListOf<JsonObject>()
        for (page in 0 until 200) {
            val res = rpc(
                "bz_pull",
                JsonObject(
                    mapOf(
                        "p_key" to JsonPrimitive(normalizedCode),
                        "p_since" to JsonPrimitive(since),
                        "p_recent" to JsonPrimitive(false)
                    )
                )
            ).jsonObject
            res["docs"]?.jsonArray?.forEach { if (it is JsonObject) docs += it }
            since = max(since, res["seq"]?.jsonPrimitive?.longOrNull ?: since)
            if (res["more"]?.jsonPrimitive?.booleanOrNull != true) break
        }
        val accDoc = docs.firstOrNull {
            it["k"]?.jsonPrimitive?.content == "acct/1" &&
                it["del"]?.jsonPrimitive?.booleanOrNull != true &&
                it["d"] is JsonObject
        } ?: throw SyncException("Aucune entreprise trouvée pour ce code.")

        val newId = "s${System.currentTimeMillis().toString(36)}"
        val accObj = JsonObject(accDoc["d"]!!.jsonObject.toMutableMap().apply {
            put("id", JsonPrimitive(newId))
        })
        val decodedAcc = runCatching {
            json.decodeFromJsonElement(BusinessAccount.serializer(), accObj)
        }.getOrNull() ?: throw SyncException("Données de l’entreprise invalides.")

        var cleanAcc = repository.sanitizeAccount(decodedAcc)
            ?: throw SyncException("Données de l’entreprise invalides.")
        var ident = cleanAcc.ident
        while (ident in existingIdents) {
            ident = "$ident-2"
        }
        cleanAcc = cleanAcc.copy(ident = ident)

        var st = repository.freshBusinessData()
        for (doc in docs) {
            val k = doc["k"]?.jsonPrimitive?.content ?: continue
            if (k == "acct/1") continue
            val isDel = doc["del"]?.jsonPrimitive?.booleanOrNull == true || doc["d"] == null || doc["d"] is JsonNull
            st = applyDoc(st, k, doc["d"], isDel)
        }
        st = repository.sanitizeData(BizzlyCalculator.syncServersWithAccount(cleanAcc, finishSync(st)))
        val (prepped, docsMap) = buildDocumentsMap(cleanAcc, st)
        val snap = docsMap.mapValues { (_, v) -> PinSecurity.h53(v) }
        return LinkPhoneResult(
            account = cleanAcc,
            data = prepped,
            credentials = SyncCredentials(key = normalizedCode, seq = since, snap = snap)
        )
    }

    suspend fun verifyRekeyCode(normalizedCode: String, expectedBusinessName: String): Boolean {
        var since = 0L
        var found: JsonObject? = null
        for (page in 0 until 200) {
            val res = rpc(
                "bz_pull",
                JsonObject(
                    mapOf(
                        "p_key" to JsonPrimitive(normalizedCode),
                        "p_since" to JsonPrimitive(since),
                        "p_recent" to JsonPrimitive(false)
                    )
                )
            ).jsonObject
            found = res["docs"]?.jsonArray?.firstOrNull {
                val obj = it.jsonObject
                obj["k"]?.jsonPrimitive?.content == "acct/1" &&
                    obj["del"]?.jsonPrimitive?.booleanOrNull != true &&
                    obj["d"] is JsonObject
            }?.jsonObject
            since = max(since, res["seq"]?.jsonPrimitive?.longOrNull ?: since)
            if (found != null || res["more"]?.jsonPrimitive?.booleanOrNull != true) break
        }
        val remoteName = found?.get("d")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: return false
        return remoteName == expectedBusinessName
    }

    private fun applyDoc(state: BusinessData, key: String, docEl: JsonElement?, isDel: Boolean): BusinessData {
        val parts = key.split("/")
        val prefix = parts.getOrNull(0) ?: return state
        return when (prefix) {
            "prod" -> {
                val id = parts.getOrNull(1)?.toLongOrNull() ?: return state
                val filtered = state.prod.filterNot { it.id == id }
                if (isDel || docEl == null || docEl is JsonNull) {
                    state.copy(prod = filtered)
                } else {
                    val p = runCatching { json.decodeFromJsonElement(Product.serializer(), docEl) }.getOrNull()
                        ?: return state
                    state.copy(prod = filtered + p)
                }
            }
            "vers", "dep", "bons" -> {
                val u = parts.getOrNull(1) ?: return state
                if (!Regex("^[a-z0-9]{6,24}$").matches(u)) return state
                val rec = if (isDel || docEl == null || docEl is JsonNull) null else {
                    runCatching { json.decodeFromJsonElement(CashMovement.serializer(), docEl).copy(u = u) }.getOrNull()
                }
                fun replaceIn(list: List<CashMovement>): List<CashMovement> {
                    val mut = list.toMutableList()
                    val idx = mut.indexOfFirst { it.u == u }
                    if (rec == null) {
                        if (idx >= 0) mut.removeAt(idx)
                    } else {
                        if (idx >= 0) mut[idx] = rec else mut += rec
                    }
                    return mut
                }
                when (prefix) {
                    "vers" -> state.copy(vers = replaceIn(state.vers))
                    "dep" -> state.copy(dep = replaceIn(state.dep))
                    else -> state.copy(bons = replaceIn(state.bons))
                }
            }
            "paid" -> {
                val u = parts.getOrNull(1) ?: return state
                if (!Regex("^[a-z0-9]{6,24}$").matches(u)) return state
                val rec = if (isDel || docEl == null || docEl is JsonNull) null else {
                    runCatching { json.decodeFromJsonElement(PaidInvoice.serializer(), docEl).copy(u = u) }.getOrNull()
                }
                val mut = state.paid.toMutableList()
                val idx = mut.indexOfFirst { it.u == u }
                if (rec == null) {
                    if (idx >= 0) mut.removeAt(idx)
                } else {
                    if (idx >= 0) mut[idx] = rec else mut += rec
                }
                state.copy(paid = mut)
            }
            "hist" -> {
                val u = parts.getOrNull(1) ?: return state
                if (!Regex("^[a-z0-9]{6,24}$").matches(u)) return state
                val rec = if (isDel || docEl == null || docEl is JsonNull) null else {
                    runCatching { json.decodeFromJsonElement(ClosedPeriod.serializer(), docEl).copy(u = u) }.getOrNull()
                }
                val mut = state.hist.toMutableList()
                val idx = mut.indexOfFirst { it.u == u }
                if (rec == null) {
                    if (idx >= 0) mut.removeAt(idx)
                } else {
                    if (idx >= 0) mut[idx] = rec else mut += rec
                }
                state.copy(hist = mut)
            }
            "arch" -> {
                val u = parts.getOrNull(1) ?: return state
                if (!Regex("^[a-z0-9]{6,24}$").matches(u)) return state
                val rec = if (isDel || docEl == null || docEl is JsonNull) null else {
                    runCatching { json.decodeFromJsonElement(ArchivePeriod.serializer(), docEl).copy(u = u) }.getOrNull()
                }
                val mut = state.arch.toMutableList()
                val idx = mut.indexOfFirst { it.u == u }
                if (rec == null) {
                    if (idx >= 0) mut.removeAt(idx)
                } else {
                    if (idx >= 0) mut[idx] = rec else mut += rec
                }
                state.copy(arch = mut)
            }
            "sv" -> {
                val date = parts.getOrNull(1) ?: return state
                val pid = parts.getOrNull(2) ?: return state
                if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) || !Regex("^\\d{1,15}$").matches(pid)) {
                    return state
                }
                val q = if (isDel || docEl == null || docEl is JsonNull) 0.0 else {
                    docEl.jsonPrimitive.doubleOrNull ?: 0.0
                }
                val svMut = state.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
                if (q != 0.0) {
                    val dayMap = svMut.getOrPut(date) { mutableMapOf() }
                    dayMap[pid] = q
                } else {
                    svMut[date]?.remove(pid)
                    if (svMut[date]?.isEmpty() == true) svMut.remove(date)
                }
                state.copy(sv = svMut)
            }
            "tb" -> {
                val tableKey = parts.getOrNull(1) ?: return state
                if (!Regex("^\\d{1,4}$").matches(tableKey)) return state
                val tbMut = state.tb.toMutableMap()
                if (isDel || docEl == null || docEl is JsonNull) {
                    tbMut.remove(tableKey)
                } else {
                    val svId = (docEl as? JsonObject)?.get("sv")?.jsonPrimitive?.longOrNull ?: 0L
                    val existing = tbMut[tableKey] ?: OpenTable()
                    tbMut[tableKey] = existing.copy(sv = svId)
                }
                state.copy(tb = tbMut)
            }
            "ti" -> {
                val tableKey = parts.getOrNull(1) ?: return state
                val pid = parts.getOrNull(2) ?: return state
                if (!Regex("^\\d{1,4}$").matches(tableKey) || !Regex("^\\d{1,15}$").matches(pid)) {
                    return state
                }
                val tbMut = state.tb.toMutableMap()
                val q = if (isDel || docEl == null || docEl is JsonNull) 0.0 else {
                    docEl.jsonPrimitive.doubleOrNull ?: 0.0
                }
                if (q > 0.0) {
                    val defaultSrv = state.srv.firstOrNull()?.id ?: 0L
                    val existing = tbMut[tableKey] ?: OpenTable(sv = defaultSrv)
                    val items = existing.it.toMutableMap()
                    items[pid] = q
                    tbMut[tableKey] = existing.copy(it = items)
                } else {
                    val existing = tbMut[tableKey]
                    if (existing != null) {
                        val items = existing.it.toMutableMap()
                        items.remove(pid)
                        tbMut[tableKey] = existing.copy(it = items)
                    }
                }
                state.copy(tb = tbMut)
            }
            "meta" -> {
                val obj = docEl as? JsonObject ?: return state
                val nt = obj["nt"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 200) ?: state.nt
                val seuil = obj["seuil"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 100_000) ?: state.seuil
                val next = max(state.next, obj["next"]?.jsonPrimitive?.longOrNull ?: state.next)
                val extraSrv = obj["srvx"]?.jsonArray?.mapNotNull {
                    runCatching { json.decodeFromJsonElement(ServerEntry.serializer(), it) }.getOrNull()
                }.orEmpty()
                val mergedSrvx = state.srvx.toMutableList()
                for (s in extraSrv) {
                    if (mergedSrvx.none { it.id == s.id }) mergedSrvx += s
                }
                state.copy(nt = nt, seuil = seuil, next = next, srvx = mergedSrvx)
            }
            else -> state
        }
    }

    private fun finishSync(state: BusinessData): BusinessData {
        val sortedProd = state.prod.sortedBy { it.id }
        val sortedPaid = state.paid.sortedByDescending { it.ts }
        val sortedHist = state.hist.sortedByDescending { it.ts }
        val sortedVers = state.vers.sortedBy { it.ts }
        val sortedDep = state.dep.sortedBy { it.ts }
        val sortedBons = state.bons.sortedBy { it.ts }
        val sortedArch = state.arch.sortedBy { it.ts }
        val maxId = (sortedProd.maxOfOrNull { it.id } ?: 0L) + 1L
        return state.copy(
            prod = sortedProd,
            paid = sortedPaid,
            hist = sortedHist,
            vers = sortedVers,
            dep = sortedDep,
            bons = sortedBons,
            arch = sortedArch,
            next = max(state.next, maxId)
        )
    }
}
