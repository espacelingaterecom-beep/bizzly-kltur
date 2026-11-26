package com.example.bizzly.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object NullableDoubleAsEmptyStringSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableDoubleAsEmptyString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) {
            encoder.encodeString("")
        } else if (value % 1.0 == 0.0) {
            encoder.encodeLong(value.toLong())
        } else {
            encoder.encodeDouble(value)
        }
    }

    override fun deserialize(decoder: Decoder): Double? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonNull) return null
            if (element is JsonPrimitive) {
                if (element.isString) {
                    val text = element.content.trim()
                    if (text.isEmpty()) return null
                    return text.toDoubleOrNull()
                }
                return element.doubleOrNull
            }
            return null
        }
        val raw = decoder.decodeString().trim()
        return if (raw.isEmpty()) null else raw.toDoubleOrNull()
    }
}

enum class BizzlyRole(val key: String, val label: String) {
    GERANT("gerant", "Gérant"),
    CAISSE("caisse", "Caisse"),
    SERVEUR("serveur", "Serveur");

    companion object {
        fun fromKey(key: String?): BizzlyRole =
            entries.firstOrNull { it.key == key } ?: SERVEUR
    }
}

object BizzlyPermissions {
    const val TABLES = "tables"
    const val COMMANDE = "commande"
    const val ENCAISSER = "encaisser"
    const val SUPPR = "suppr"
    const val VENTES = "ventes"
    const val STOCK = "stock"
    const val PRIX = "prix"
    const val PRODUITS = "produits"
    const val ARGENT = "argent"
    const val DEPENSES = "depenses"
    const val BILAN = "bilan"
    const val EXPORT = "export"
    const val CLOTURE = "cloture"
    const val QR = "qr"
    const val RESET = "reset"
    const val RESTORE = "restore"
    const val REMISE_GERANT = "remise_gerant"

    private val caissePerms = setOf(
        TABLES, COMMANDE, ENCAISSER, SUPPR, VENTES, STOCK, PRIX,
        PRODUITS, ARGENT, DEPENSES, BILAN, EXPORT, CLOTURE, QR
    )
    private val serveurPerms = setOf(TABLES, COMMANDE)

    fun hasPermission(role: BizzlyRole, perm: String): Boolean = when (role) {
        BizzlyRole.GERANT -> true
        BizzlyRole.CAISSE -> perm in caissePerms
        BizzlyRole.SERVEUR -> perm in serveurPerms
    }

    fun canAccessTab(role: BizzlyRole, tab: BizzlyTab): Boolean = when (role) {
        BizzlyRole.GERANT -> true
        BizzlyRole.CAISSE -> tab != BizzlyTab.SRV
        BizzlyRole.SERVEUR -> tab == BizzlyTab.TABLES || tab == BizzlyTab.MORE
    }

    fun defaultTab(role: BizzlyRole): BizzlyTab =
        if (role == BizzlyRole.SERVEUR) BizzlyTab.TABLES else BizzlyTab.HOME
}

enum class BizzlyTab(val route: String, val label: String) {
    HOME("home", "Accueil"),
    TABLES("tb", "Tables"),
    SALES("sales", "Ventes"),
    STOCK("inv", "Stock"),
    CASH("cash", "Argent"),
    MORE("more", "Plus"),
    PROD("prod", "Produits"),
    SRV("srv", "Entreprise"),
    QR("qr", "Menu QR"),
    HIST("hist", "Bilan"),
    INVENTORY("inventory", "Inventaire")
}

val PAYMENT_MODES = listOf("Espèces", "Mobile Money", "Carte bancaire", "Mixte", "Autre")
val SINGLE_PAYMENT_MODES = listOf("Espèces", "Mobile Money", "Carte bancaire", "Autre")
val QUICK_CASH_AMOUNTS = listOf(1000L, 2000L, 5000L, 10000L, 20000L)

enum class PrinterPaperWidth(val mm: Int, val charsPerLine: Int, val label: String) {
    MM_58(58, 32, "58 mm (32 col.)"),
    MM_80(80, 48, "80 mm (48 col.)");

    companion object {
        fun fromMm(mm: Int): PrinterPaperWidth = if (mm >= 80) MM_80 else MM_58
    }
}

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

data class RestockOrderLine(
    val itemId: Long,
    val name: String,
    val sku: String,
    val category: String,
    val unit: String,
    val currentQty: Double,
    val minThreshold: Double,
    val suggestedQty: Double,
    val unitPrice: Double
)

@Serializable
data class BrandColors(
    val pr: String = "#2563EB",
    val sec: String = "#0A1F44",
    val ax: String = "#FF6B6B"
)

data class BrandThemeProposal(
    val name: String,
    val subtitle: String,
    val brand: BrandColors
)

@Serializable
data class BusinessProfile(
    val id: Long,
    val role: String,
    val name: String,
    val salt: String = "",
    val hash: String = "",
    val v: Int = 2
)

@Serializable
data class BusinessAccount(
    val id: String,
    val ident: String,
    val name: String,
    val email: String = "",
    val tel: String = "",
    val addr: String = "",
    val logo: String = "",
    val lw: Int = 0,
    val lh: Int = 0,
    val brand: BrandColors = BrandColors(),
    val profiles: List<BusinessProfile> = emptyList(),
    val created: String = ""
)

@Serializable
data class Product(
    val id: Long,
    val n: String,
    val pv: Double = 0.0,
    val d: Double = 0.0,
    val e: Double = 0.0,
    @Serializable(with = NullableDoubleAsEmptyStringSerializer::class)
    val f: Double? = null
)

@Serializable
data class CashMovement(
    val d: String,
    val w: String = "",
    val m: Double = 0.0,
    val pid: Long? = null,
    val q: Double? = null,
    val u: String = "",
    val ts: Long = 0L
)

@Serializable
data class PaymentPart(
    val mode: String,
    val amount: Double
)

@Serializable
data class PaidInvoice(
    val d: String,
    val t: Int,
    val sv: Long,
    val tot: Double,
    val m: String = "Espèces",
    val u: String = "",
    val ts: Long = 0L,
    val payments: List<PaymentPart> = emptyList(),
    val discountAmount: Double = 0.0,
    val discountReason: String = ""
)

@Serializable
data class ClosedPeriod(
    val d: String,
    val att: Double,
    val ver: Double,
    val dep: Double,
    val bon: Double,
    val rest: Double,
    val u: String = "",
    val ts: Long = 0L
)

@Serializable
data class ArchivePeriod(
    val d: String,
    val sv: Map<String, Map<String, Double>> = emptyMap(),
    val paid: List<PaidInvoice> = emptyList(),
    val dep: List<CashMovement> = emptyList(),
    val vers: List<CashMovement> = emptyList(),
    val bons: List<CashMovement> = emptyList(),
    val u: String = "",
    val ts: Long = 0L
)

@Serializable
data class OpenTable(
    val sv: Long = 0L,
    val it: Map<String, Double> = emptyMap(),
    val comped: Map<String, Double> = emptyMap(),
    val discountType: String = "none",
    val discountValue: Double = 0.0,
    val discountReason: String = "",
    val paidPartial: Double = 0.0,
    val paidCount: Int = 0,
    val splitTotalParts: Int = 0
)

@Serializable
data class ServerEntry(
    val id: Long,
    val n: String
)

@Serializable
data class ActivityLog(
    val t: Long,
    val m: String
)

@Serializable
data class BusinessData(
    val prod: List<Product> = emptyList(),
    val vers: List<CashMovement> = emptyList(),
    val dep: List<CashMovement> = emptyList(),
    val bons: List<CashMovement> = emptyList(),
    val hist: List<ClosedPeriod> = emptyList(),
    val paid: List<PaidInvoice> = emptyList(),
    val sv: Map<String, Map<String, Double>> = emptyMap(),
    val tb: Map<String, OpenTable> = emptyMap(),
    val srv: List<ServerEntry> = emptyList(),
    val srvx: List<ServerEntry> = emptyList(),
    val log: List<ActivityLog> = emptyList(),
    val arch: List<ArchivePeriod> = emptyList(),
    val nt: Int = 20,
    val seuil: Int = 10,
    val next: Long = 1L,
    val lbk: String = "",
    val fv2: Int = 1
)

@Serializable
data class BackupEnvelope(
    val bz: Int = 2,
    val account: BusinessAccount,
    val data: BusinessData,
    val at: String
)

@Serializable
data class SyncCredentials(
    val key: String,
    val seq: Long = 0L,
    val snap: Map<String, String> = emptyMap()
)

enum class SyncStatus(val chipLabel: String, val detailLabel: String) {
    OFF("Hors ligne", "Désactivée"),
    SYNCING("Synchro…", "Synchronisation…"),
    OK("En ligne", "Connecté"),
    ERROR("Hors ligne", "Problème")
}

object SampleCatalog {
    val defaultItems: List<Pair<String, Double>> = listOf(
        "CASTEL GM" to 750.0,
        "MOCAF GM" to 750.0,
        "33 GM" to 750.0,
        "CASTEL PM" to 500.0,
        "XXL" to 600.0,
        "MOCAF PM" to 500.0,
        "CRISTAL" to 350.0,
        "HARP" to 1300.0,
        "JUS PM" to 350.0,
        "GUINNESS GM" to 2000.0,
        "GUINNESS PM" to 1000.0,
        "MUTZIG" to 1300.0,
        "DOPPEL" to 1000.0,
        "ISENBECK" to 1300.0,
        "HENKEN" to 1300.0,
        "PELFORTH" to 1000.0,
        "BOOSTER" to 600.0,
        "ROYAL DUTCH" to 1000.0,
        "TOP GM CAM" to 1000.0,
        "TOP GM PL" to 1000.0,
        "TOP PM PL" to 500.0,
        "PENASOL" to 2500.0,
        "CUVEE DU ROI" to 2500.0,
        "BARON" to 2500.0,
        "TIO DE LA BOTA" to 3000.0,
        "CAVE ROYAL" to 700.0,
        "EAU MINERALE" to 500.0,
        "JUS GM" to 700.0,
        "MALTA" to 600.0,
        "BEN BERA" to 1000.0,
        "REACTOR" to 500.0,
        "SAINT AULAIRE" to 2500.0,
        "VIEUX PAPE" to 2500.0
    )
}
