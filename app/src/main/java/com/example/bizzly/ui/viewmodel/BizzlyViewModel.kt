package com.example.bizzly.ui.viewmodel

import android.app.Application
import android.content.res.Configuration
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bizzly.data.local.BizzlyDatabase
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.data.repository.BizzlyRepository
import com.example.bizzly.data.repository.InventoryRepository
import com.example.bizzly.data.repository.StockAlertEvent
import com.example.bizzly.data.sync.SupabaseSyncClient
import com.example.bizzly.data.sync.SyncException
import com.example.bizzly.data.sync.SyncSnapshotSummary
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.ActivityLog
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BizzlyPermissions
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.model.BluetoothPrinterDevice
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BrandThemeProposal
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.BusinessProfile
import com.example.bizzly.model.CashMovement
import com.example.bizzly.model.ClosedPeriod
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaidInvoice
import com.example.bizzly.model.PaymentPart
import com.example.bizzly.model.PrinterPaperWidth
import com.example.bizzly.model.Product
import com.example.bizzly.model.RestockOrderLine
import com.example.bizzly.model.SampleCatalog
import com.example.bizzly.model.SyncCredentials
import com.example.bizzly.model.SyncStatus
import com.example.bizzly.security.LockoutTracker
import com.example.bizzly.security.PinSecurity
import com.example.bizzly.util.EscPosPrinter
import com.example.bizzly.util.ExportUtils
import com.example.bizzly.util.NotificationHelper
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class AuthMode {
    PICK_BUSINESS,
    PICK_PROFILE,
    PIN_LOGIN,
    SIGNUP,
    LINK_PHONE,
    RESTORE_BACKUP,
    ADMIN_KLTUR
}

data class SignupFormState(
    val name: String = "",
    val ident: String = "",
    val identEdited: Boolean = false,
    val email: String = "",
    val tel: String = "",
    val addr: String = "",
    val pin: String = "",
    val pin2: String = "",
    val cpin: String = "",
    val logo: String = "",
    val lw: Int = 0,
    val lh: Int = 0,
    val brand: BrandColors = BrandColors(),
    val logoProposals: List<BrandThemeProposal> = emptyList(),
    val error: String = ""
)

data class AdminBusinessSummary(
    val account: BusinessAccount,
    val productCount: Int,
    val openTableCount: Int,
    val tablesCount: Int,
    val recordedRevenue: Double,
    val syncCreds: SyncCredentials?
)

data class AdminUiState(
    val isAuthenticated: Boolean = false,
    val needsSetup: Boolean = false,
    val authTab: Int = 0,
    val busy: Boolean = false,
    val error: String = "",
    val netlifyUrl: String = "",
    val supabaseEmail: String? = null,
    val businesses: List<AdminBusinessSummary> = emptyList(),
    val remoteSpaces: List<SupabaseSyncClient.RemoteSpaceOverview> = emptyList(),
    val selectedBusinessId: String? = null,
    val activeAdminTab: Int = 0,
    val snapshotsForBusiness: Pair<String, List<SyncSnapshotSummary>>? = null
)

sealed class PinPromptMode {
    data class Authorize(
        val permission: String,
        val title: String,
        val subtitle: String,
        val onAuthorized: () -> Unit
    ) : PinPromptMode()

    data class SetNewPin(
        val title: String,
        val initialSubtitle: String,
        val firstPin: String? = null,
        val onPinConfirmed: (String) -> Unit
    ) : PinPromptMode()
}

data class PinOverlayState(
    val mode: PinPromptMode,
    val pin: String = "",
    val subtitle: String,
    val error: String = "",
    val shakeTick: Int = 0,
    val busy: Boolean = false
)

data class ConfirmDialogState(
    val message: String,
    val confirmLabel: String = "Confirmer",
    val isDestructive: Boolean = false,
    val onConfirm: () -> Unit
)

data class ReadyExport(
    val filename: String,
    val mimeType: String,
    val chooserTitle: String,
    val bytes: ByteArray,
    val downloadsUri: Uri?,
    val cacheUri: Uri?,
    val savedToDownloads: Boolean,
    val tick: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReadyExport) return false
        return filename == other.filename && mimeType == other.mimeType && tick == other.tick
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + tick.hashCode()
        return result
    }
}

data class BizzlyUiState(
    val initialized: Boolean = false,
    val accounts: List<BusinessAccount> = emptyList(),
    val isDarkTheme: Boolean = false,
    val authMode: AuthMode = AuthMode.PICK_BUSINESS,
    val selectedBusinessId: String? = null,
    val selectedProfileId: Long? = null,
    val loginPin: String = "",
    val loginError: String = "",
    val loginShakeTick: Int = 0,
    val loginBusy: Boolean = false,
    val signup: SignupFormState = SignupFormState(),
    val pendingLegacyData: BusinessData? = null,
    val linkCodeInput: String = "",
    val linkError: String = "",
    val linkBusy: Boolean = false,

    val activeAccount: BusinessAccount? = null,
    val activeProfile: BusinessProfile? = null,
    val businessData: BusinessData = BusinessData(),
    val currentTab: BizzlyTab = BizzlyTab.HOME,
    val currentTable: Int? = null,
    val salesDate: String = BizzlyCalculator.todayIso(),
    val salesSearch: String = "",
    val chartRangeDays: Int = 7,
    val paymentMode: String = "Espèces",
    val cashGivenText: String = "",
    val mixedMode1: String = "Espèces",
    val mixedAmount1Text: String = "",
    val mixedMode2: String = "Mobile Money",
    val printerAddress: String? = null,
    val printerName: String? = null,
    val printerWidthMm: Int = 58,
    val pairedPrinters: List<BluetoothPrinterDevice> = emptyList(),
    val printerBusy: Boolean = false,

    val pinOverlay: PinOverlayState? = null,
    val confirmDialog: ConfirmDialogState? = null,

    val syncCreds: SyncCredentials? = null,
    val syncStatus: SyncStatus = SyncStatus.OFF,
    val syncBusy: Boolean = false,
    val syncDead: Boolean = false,
    val syncError: String = "",
    val lastSyncMs: Long = 0L,
    val syncMessage: String = "",
    val syncCodeDialogVisible: Boolean = false,
    val rekeyDialogVisible: Boolean = false,
    val rekeyCodeInput: String = "",
    val rekeyError: String = "",
    val rekeyBusy: Boolean = false,
    val snapshotsDialog: List<SyncSnapshotSummary>? = null,

    val readyExport: ReadyExport? = null,
    val exportBusy: Boolean = false,

    val enterpriseLogoProposals: List<BrandThemeProposal> = emptyList(),
    val admin: AdminUiState = AdminUiState(),

    val toastMessage: String? = null,
    val toastTick: Long = 0L
) {
    val activeBrand: BrandColors
        get() {
            val picked = when {
                activeAccount != null -> activeAccount.brand
                authMode == AuthMode.SIGNUP -> signup.brand
                (authMode == AuthMode.PICK_PROFILE || authMode == AuthMode.PIN_LOGIN) && selectedBusinessId != null ->
                    accounts.firstOrNull { it.id == selectedBusinessId }?.brand ?: BrandColors()
                else -> BrandColors()
            }
            return picked
        }

    val isLoggedIn: Boolean
        get() = activeAccount != null && activeProfile != null
}

class BizzlyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BizzlyDatabase.getInstance(application)
    private val repository = BizzlyRepository(application, db.bizzlyDao())
    private val inventoryRepository = InventoryRepository(db.inventoryDao())
    private val syncClient = SupabaseSyncClient(repository)
    private val lockout = LockoutTracker(application)

    private val _uiState = MutableStateFlow(BizzlyUiState())
    val uiState: StateFlow<BizzlyUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val inventoryItems: StateFlow<List<InventoryItemEntity>> = _uiState
        .map { it.activeAccount?.id }
        .distinctUntilChanged()
        .flatMapLatest { bizId ->
            if (bizId.isNullOrBlank()) flowOf(emptyList())
            else inventoryRepository.observeItems(bizId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val lowStockInventoryItems: StateFlow<List<InventoryItemEntity>> = _uiState
        .map { it.activeAccount?.id }
        .distinctUntilChanged()
        .flatMapLatest { bizId ->
            if (bizId.isNullOrBlank()) flowOf(emptyList())
            else inventoryRepository.observeLowStockItems(bizId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val persistMutex = Mutex()
    private val seenDocVersions = mutableMapOf<String, Long>()
    private var syncDebounceJob: Job? = null
    private var syncDirty = false
    private var syncAgain = false
    private var lastActivityMs = System.currentTimeMillis()
    private var oneShotAuthorized = false
    private var supabaseAdminToken: String? = null
    private var pendingTabRoute: String? = null

    init {
        NotificationHelper.ensureChannel(application)
        val sysNight = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialDark = repository.getDarkThemePreference() ?: sysNight
        val savedPrinterAddr = repository.getPrinterAddress()
        val savedPrinterName = repository.getPrinterName()
        val savedPrinterWidth = repository.getPrinterWidthMm()

        viewModelScope.launch {
            val initialAccounts = withContext(Dispatchers.IO) { repository.getAllAccounts() }
            val lastBiz = repository.getLastBusinessId()
            val hasLast = lastBiz != null && initialAccounts.any { it.id == lastBiz }
            val startMode = when {
                initialAccounts.isEmpty() -> AuthMode.SIGNUP
                hasLast -> AuthMode.PICK_PROFILE
                else -> AuthMode.PICK_BUSINESS
            }
            _uiState.update {
                it.copy(
                    initialized = true,
                    accounts = initialAccounts,
                    isDarkTheme = initialDark,
                    authMode = startMode,
                    selectedBusinessId = if (hasLast) lastBiz else null,
                    printerAddress = savedPrinterAddr,
                    printerName = savedPrinterName,
                    printerWidthMm = savedPrinterWidth
                )
            }
        }

        // Observe Room accounts continuously
        viewModelScope.launch {
            repository.accountsFlow.collect { accounts ->
                _uiState.update { st ->
                    val updatedActive = st.activeAccount?.let { cur ->
                        accounts.firstOrNull { it.id == cur.id }
                    }
                    st.copy(accounts = accounts, activeAccount = updatedActive ?: st.activeAccount)
                }
            }
        }

        // 4s periodic sync & 15m inactivity lock loop
        viewModelScope.launch {
            while (isActive) {
                delay(4000)
                val st = _uiState.value
                if (st.isLoggedIn) {
                    if (st.pinOverlay == null && System.currentTimeMillis() - lastActivityMs > 15 * 60_000L) {
                        lockNow()
                    } else if (st.syncCreds != null && !st.syncDead) {
                        runSyncCycle()
                    }
                }
            }
        }
    }

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg, toastTick = System.currentTimeMillis()) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun toggleTheme() {
        val next = !_uiState.value.isDarkTheme
        repository.setDarkThemePreference(next)
        _uiState.update { it.copy(isDarkTheme = next) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirmDialog = null) }
    }

    private fun askConfirm(message: String, confirmLabel: String = "Confirmer", isDestructive: Boolean = false, onConfirm: () -> Unit) {
        _uiState.update {
            it.copy(
                confirmDialog = ConfirmDialogState(
                    message = message,
                    confirmLabel = confirmLabel,
                    isDestructive = isDestructive,
                    onConfirm = {
                        _uiState.update { s -> s.copy(confirmDialog = null) }
                        onConfirm()
                    }
                )
            )
        }
    }

    // --- Auth Navigation ---

    fun navigateAuth(mode: AuthMode) {
        _uiState.update {
            it.copy(
                authMode = if (it.accounts.isEmpty() &&
                    mode != AuthMode.RESTORE_BACKUP &&
                    mode != AuthMode.LINK_PHONE &&
                    mode != AuthMode.ADMIN_KLTUR
                ) {
                    AuthMode.SIGNUP
                } else mode,
                loginPin = "",
                loginError = "",
                linkError = ""
            )
        }
    }

    fun selectBusiness(businessId: String) {
        _uiState.update {
            it.copy(
                selectedBusinessId = businessId,
                selectedProfileId = null,
                authMode = AuthMode.PICK_PROFILE,
                loginPin = "",
                loginError = ""
            )
        }
    }

    fun selectProfile(profileId: Long) {
        _uiState.update {
            it.copy(
                selectedProfileId = profileId,
                authMode = AuthMode.PIN_LOGIN,
                loginPin = "",
                loginError = ""
            )
        }
    }

    fun pressLoginPinKey(key: String) {
        val st = _uiState.value
        if (st.loginBusy || st.authMode != AuthMode.PIN_LOGIN) return
        val nextPin = when (key) {
            "x" -> st.loginPin.dropLast(1)
            else -> if (st.loginPin.length < 4 && key.all { it.isDigit() }) st.loginPin + key else st.loginPin
        }
        _uiState.update { it.copy(loginPin = nextPin, loginError = "") }
        if (nextPin.length == 4) {
            submitLoginPin()
        }
    }

    private fun submitLoginPin() {
        val st = _uiState.value
        val biz = st.accounts.firstOrNull { it.id == st.selectedBusinessId } ?: return
        val profile = biz.profiles.firstOrNull { it.id == st.selectedProfileId } ?: return
        val lockKey = "${biz.id}:${profile.id}"

        val waitMsg = lockout.checkWaitMessage(lockKey)
        if (waitMsg != null) {
            _uiState.update {
                it.copy(
                    loginPin = "",
                    loginError = waitMsg,
                    loginShakeTick = it.loginShakeTick + 1
                )
            }
            return
        }

        _uiState.update { it.copy(loginBusy = true) }
        viewModelScope.launch {
            val enteredPin = st.loginPin
            val verified = withContext(Dispatchers.Default) {
                PinSecurity.verifyPin(profile, enteredPin)
            }
            if (verified) {
                lockout.reset(lockKey)
                // Upgrade legacy v1 hash to v2 PBKDF2 transparently
                var activeBiz = biz
                var activeProf = profile
                if (profile.v != 2) {
                    val (newSalt, newHash) = withContext(Dispatchers.Default) {
                        PinSecurity.createPinHash(enteredPin)
                    }
                    activeProf = profile.copy(salt = newSalt, hash = newHash, v = 2)
                    activeBiz = biz.copy(profiles = biz.profiles.map { if (it.id == activeProf.id) activeProf else it })
                    repository.saveAccount(activeBiz)
                }
                enterSession(activeBiz, activeProf)
            } else {
                val lockMsg = lockout.recordFailure(lockKey)
                _uiState.update {
                    it.copy(
                        loginBusy = false,
                        loginPin = "",
                        loginError = lockMsg ?: "Code incorrect",
                        loginShakeTick = it.loginShakeTick + 1
                    )
                }
            }
        }
    }

    private suspend fun enterSession(account: BusinessAccount, profile: BusinessProfile) {
        val rawData = withContext(Dispatchers.IO) { repository.getBusinessData(account.id) }
        val syncedSrv = BizzlyCalculator.syncServersWithAccount(account, rawData)
        val creds = withContext(Dispatchers.IO) { repository.getSyncCredentials(account.id) }
        repository.setLastBusinessId(account.id)
        lastActivityMs = System.currentTimeMillis()
        seenDocVersions.clear()

        val role = BizzlyRole.fromKey(profile.role)
        val initialTab = if (pendingTabRoute == NotificationHelper.TAB_INVENTORY &&
            BizzlyPermissions.canAccessTab(role, BizzlyTab.INVENTORY)
        ) {
            pendingTabRoute = null
            BizzlyTab.INVENTORY
        } else {
            BizzlyPermissions.defaultTab(role)
        }
        val withLog = appendLog(syncedSrv, "Connexion (${role.label})")
        withContext(Dispatchers.IO) {
            repository.saveBusinessData(account.id, withLog)
            inventoryRepository.seedFromCatalogIfNeeded(account.id, withLog)
        }

        _uiState.update {
            it.copy(
                loginBusy = false,
                loginPin = "",
                loginError = "",
                activeAccount = account,
                activeProfile = profile,
                businessData = withLog,
                currentTab = initialTab,
                currentTable = null,
                salesDate = BizzlyCalculator.todayIso(),
                salesSearch = "",
                paymentMode = "Espèces",
                cashGivenText = "",
                pinOverlay = null,
                confirmDialog = null,
                syncCreds = creds,
                syncStatus = if (creds != null) SyncStatus.SYNCING else SyncStatus.OFF,
                syncDead = false,
                syncError = "",
                syncMessage = ""
            )
        }

        if (creds != null) {
            syncDirty = true
            runSyncCycle()
        }
    }

    fun lockNow() {
        syncDebounceJob?.cancel()
        val lastBizId = _uiState.value.activeAccount?.id
        _uiState.update {
            it.copy(
                activeAccount = null,
                activeProfile = null,
                businessData = BusinessData(),
                currentTable = null,
                pinOverlay = null,
                confirmDialog = null,
                syncCreds = null,
                syncStatus = SyncStatus.OFF,
                syncDead = false,
                syncError = "",
                syncCodeDialogVisible = false,
                rekeyDialogVisible = false,
                snapshotsDialog = null,
                selectedBusinessId = lastBizId,
                selectedProfileId = null,
                authMode = if (lastBizId != null) AuthMode.PICK_PROFILE else AuthMode.PICK_BUSINESS,
                loginPin = "",
                loginError = ""
            )
        }
    }

    fun switchBusiness() {
        syncDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                activeAccount = null,
                activeProfile = null,
                businessData = BusinessData(),
                currentTable = null,
                pinOverlay = null,
                confirmDialog = null,
                syncCreds = null,
                syncStatus = SyncStatus.OFF,
                selectedBusinessId = null,
                selectedProfileId = null,
                authMode = AuthMode.PICK_BUSINESS,
                loginPin = "",
                loginError = ""
            )
        }
    }

    // --- Signup ---

    fun updateSignup(transform: (SignupFormState) -> SignupFormState) {
        _uiState.update { it.copy(signup = transform(it.signup)) }
    }

    fun pickSignupLogo(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExportUtils.decodeAndResizeLogo(getApplication(), uri)
            }
            if (result == null) {
                showToast("Image illisible. Essayez un PNG ou un JPG.")
                return@launch
            }
            _uiState.update {
                it.copy(
                    signup = it.signup.copy(
                        logo = result.dataUri,
                        lw = result.width,
                        lh = result.height,
                        brand = result.palette ?: it.signup.brand,
                        logoProposals = result.proposals
                    )
                )
            }
            if (result.proposals.isNotEmpty()) {
                showToast("Thème proposé et appliqué selon les couleurs du logo.")
            }
        }
    }

    fun createBusinessAccount() {
        val st = _uiState.value
        val s = st.signup
        val name = s.name.trim()
        val idSlug = BizzlyCalculator.slugify(s.ident.ifBlank { name })
        val email = s.email.trim()

        fun fail(msg: String) {
            _uiState.update { it.copy(signup = it.signup.copy(error = msg)) }
            showToast(msg)
        }

        if (name.length < 2) return fail("Indiquez le nom de l’entreprise.")
        if (!Regex("^[a-z0-9-]{3,}$").matches(idSlug)) {
            return fail("L’identifiant : au moins 3 caractères (lettres, chiffres, tirets).")
        }
        if (st.accounts.any { it.ident == idSlug }) {
            return fail("Cet identifiant existe déjà sur cet appareil.")
        }
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email)) {
            return fail("Indiquez une adresse e-mail valide.")
        }
        if (!PinSecurity.isFourDigits(s.pin)) return fail("Le PIN du gérant doit avoir 4 chiffres.")
        if (PinSecurity.isWeakPin(s.pin)) {
            return fail("PIN trop simple (ex. 1234, 0000). Choisissez-en un autre.")
        }
        if (s.pin != s.pin2) return fail("Les deux PIN du gérant sont différents.")
        if (s.cpin.isNotEmpty() && !PinSecurity.isFourDigits(s.cpin)) {
            return fail("Le PIN de la caisse doit avoir 4 chiffres (ou laissez vide).")
        }
        if (s.cpin.isNotEmpty() && PinSecurity.isWeakPin(s.cpin)) {
            return fail("PIN de la caisse trop simple. Choisissez-en un autre.")
        }
        if (s.cpin.isNotEmpty() && s.cpin == s.pin) {
            return fail("La caisse et le gérant doivent avoir des PIN différents.")
        }

        _uiState.update { it.copy(loginBusy = true, signup = it.signup.copy(error = "")) }
        viewModelScope.launch {
            val (gSalt, gHash) = withContext(Dispatchers.Default) { PinSecurity.createPinHash(s.pin) }
            val gerant = BusinessProfile(
                id = 101L,
                role = BizzlyRole.GERANT.key,
                name = "Gérant",
                salt = gSalt,
                hash = gHash,
                v = 2
            )
            val caisse = if (s.cpin.isNotEmpty()) {
                val (cSalt, cHash) = withContext(Dispatchers.Default) { PinSecurity.createPinHash(s.cpin) }
                BusinessProfile(
                    id = 102L,
                    role = BizzlyRole.CAISSE.key,
                    name = "Caisse",
                    salt = cSalt,
                    hash = cHash,
                    v = 2
                )
            } else {
                BusinessProfile(id = 102L, role = BizzlyRole.CAISSE.key, name = "Caisse")
            }

            val profiles = mutableListOf(gerant, caisse)
            val data = st.pendingLegacyData ?: repository.freshBusinessData()
            data.srv.forEach { srv ->
                if (srv.id != 101L && srv.id != 102L && profiles.none { it.id == srv.id }) {
                    profiles += BusinessProfile(
                        id = srv.id,
                        role = BizzlyRole.SERVEUR.key,
                        name = srv.n.ifBlank { "Serveur" }
                    )
                }
            }

            val acc = BusinessAccount(
                id = "b${System.currentTimeMillis().toString(36)}",
                ident = idSlug,
                name = name,
                email = email,
                tel = s.tel.trim(),
                addr = s.addr.trim(),
                logo = s.logo,
                lw = s.lw,
                lh = s.lh,
                brand = s.brand,
                profiles = profiles,
                created = java.time.Instant.now().toString()
            )
            withContext(Dispatchers.IO) {
                repository.saveAccount(acc)
                repository.saveBusinessData(acc.id, data)
            }
            _uiState.update {
                it.copy(
                    signup = SignupFormState(),
                    pendingLegacyData = null
                )
            }
            enterSession(acc, gerant)
            showToast("Espace « $name » créé.")
        }
    }

    // --- Link Phone & Import Backup (Auth screen) ---

    fun updateLinkCode(code: String) {
        _uiState.update { it.copy(linkCodeInput = code, linkError = "") }
    }

    fun submitLinkPhone() {
        val st = _uiState.value
        val code = syncClient.normalizeSyncKey(st.linkCodeInput)
        if (code.length != 20) {
            _uiState.update { it.copy(linkError = "Le code doit avoir 20 caractères.") }
            return
        }
        _uiState.update { it.copy(linkBusy = true, linkError = "Connexion…") }
        viewModelScope.launch {
            val existingKeys = withContext(Dispatchers.IO) { repository.getAllSyncKeys() }
            if (code in existingKeys) {
                _uiState.update {
                    it.copy(linkBusy = false, linkError = "Ce téléphone est déjà lié à cette entreprise.")
                }
                return@launch
            }
            runCatching {
                val idents = st.accounts.map { it.ident }.toSet()
                syncClient.linkNewPhone(code, idents)
            }.onSuccess { result ->
                withContext(Dispatchers.IO) {
                    repository.saveAccount(result.account)
                    repository.saveBusinessData(result.account.id, result.data)
                    repository.saveSyncCredentials(result.account.id, result.credentials)
                    repository.setLastBusinessId(result.account.id)
                }
                _uiState.update {
                    it.copy(
                        linkBusy = false,
                        linkCodeInput = "",
                        linkError = "",
                        selectedBusinessId = result.account.id,
                        authMode = AuthMode.PICK_PROFILE
                    )
                }
                showToast("Téléphone lié à « ${result.account.name} ».")
            }.onFailure { err ->
                val msg = when {
                    err is SyncException && err.isInvalidKey -> "Code incorrect."
                    err is SyncException && err.isOffline -> "Pas de connexion à Internet."
                    else -> err.message ?: "Erreur de connexion."
                }
                _uiState.update { it.copy(linkBusy = false, linkError = msg) }
            }
        }
    }

    fun importBackupFromAuth(uri: Uri) {
        viewModelScope.launch {
            var oversized = false
            val raw = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val txt = BizzlyRepository.readStreamBoundedUtf8(stream)
                    if (txt == null) oversized = true
                    txt
                }
            }
            if (oversized) {
                showToast("Fichier trop volumineux (max 10 Mo).")
                return@launch
            }
            if (raw == null) {
                showToast("Impossible de lire le fichier.")
                return@launch
            }
            when (val parsed = repository.parseBackupJson(raw)) {
                is BizzlyRepository.ParsedBackup.FullV2 -> {
                    val existing = _uiState.value.accounts.firstOrNull {
                        it.id == parsed.account.id || it.ident == parsed.account.ident
                    }
                    if (existing != null) {
                        showToast("Cette entreprise existe déjà sur cet appareil. Connectez-vous en gérant, puis Plus > Bilan > Restaurer.")
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        repository.saveAccount(parsed.account)
                        repository.saveBusinessData(parsed.account.id, parsed.data)
                        repository.setLastBusinessId(parsed.account.id)
                    }
                    _uiState.update {
                        it.copy(
                            selectedBusinessId = parsed.account.id,
                            authMode = AuthMode.PICK_PROFILE
                        )
                    }
                    showToast("Sauvegarde restaurée.")
                }
                is BizzlyRepository.ParsedBackup.LegacyDataOnly -> {
                    _uiState.update {
                        it.copy(
                            pendingLegacyData = parsed.data,
                            signup = SignupFormState(),
                            authMode = AuthMode.SIGNUP
                        )
                    }
                    showToast("Anciennes données chargées. Créez l’espace qui les recevra.")
                }
                null -> showToast("Ce fichier n’est pas une sauvegarde valide.")
            }
        }
    }

    // --- PIN Authorization & Overlay ---

    private fun currentRole(): BizzlyRole =
        BizzlyRole.fromKey(_uiState.value.activeProfile?.role)

    fun can(permission: String): Boolean =
        BizzlyPermissions.hasPermission(currentRole(), permission)

    fun canTab(tab: BizzlyTab): Boolean =
        BizzlyPermissions.canAccessTab(currentRole(), tab)

    private fun requirePermission(permission: String, action: () -> Unit): Boolean {
        if (oneShotAuthorized || can(permission)) return true
        _uiState.update {
            it.copy(
                pinOverlay = PinOverlayState(
                    mode = PinPromptMode.Authorize(
                        permission = permission,
                        title = "Autorisation requise",
                        subtitle = "Code PIN du gérant ou de la caisse",
                        onAuthorized = action
                    ),
                    subtitle = "Code PIN du gérant ou de la caisse"
                )
            )
        }
        return false
    }

    private fun requireSensitivePin(permission: String, action: () -> Unit): Boolean {
        if (oneShotAuthorized) return true
        val subtitle = if (permission == BizzlyPermissions.RESET ||
            permission == BizzlyPermissions.RESTORE ||
            permission == BizzlyPermissions.REMISE_GERANT
        ) {
            "PIN du gérant"
        } else {
            "PIN du gérant ou de la caisse"
        }
        _uiState.update {
            it.copy(
                pinOverlay = PinOverlayState(
                    mode = PinPromptMode.Authorize(
                        permission = permission,
                        title = "Confirmez avec votre PIN",
                        subtitle = subtitle,
                        onAuthorized = action
                    ),
                    subtitle = subtitle
                )
            )
        }
        return false
    }

    private fun requireManagerPin(title: String, action: () -> Unit): Boolean {
        if (oneShotAuthorized) return true
        _uiState.update {
            it.copy(
                pinOverlay = PinOverlayState(
                    mode = PinPromptMode.Authorize(
                        permission = BizzlyPermissions.REMISE_GERANT,
                        title = title,
                        subtitle = "PIN du gérant requis",
                        onAuthorized = action
                    ),
                    subtitle = "PIN du gérant requis"
                )
            )
        }
        return false
    }

    fun pressOverlayPinKey(key: String) {
        val ov = _uiState.value.pinOverlay ?: return
        if (ov.busy) return
        val nextPin = when (key) {
            "x" -> ov.pin.dropLast(1)
            else -> if (ov.pin.length < 4 && key.all { it.isDigit() }) ov.pin + key else ov.pin
        }
        _uiState.update { it.copy(pinOverlay = ov.copy(pin = nextPin, error = "")) }
        if (nextPin.length == 4) {
            submitOverlayPin(nextPin)
        }
    }

    fun dismissPinOverlay() {
        _uiState.update { it.copy(pinOverlay = null) }
    }

    private fun submitOverlayPin(pin: String) {
        val st = _uiState.value
        val ov = st.pinOverlay ?: return
        when (val mode = ov.mode) {
            is PinPromptMode.Authorize -> {
                val acc = st.activeAccount ?: return
                val lockKey = "ov:${acc.id}"
                val waitMsg = lockout.checkWaitMessage(lockKey)
                if (waitMsg != null) {
                    _uiState.update {
                        it.copy(
                            pinOverlay = ov.copy(
                                pin = "",
                                error = waitMsg,
                                shakeTick = ov.shakeTick + 1,
                                busy = false
                            )
                        )
                    }
                    return
                }
                _uiState.update { it.copy(pinOverlay = ov.copy(busy = true)) }
                viewModelScope.launch {
                    val matchedProfile = withContext(Dispatchers.Default) {
                        acc.profiles.firstOrNull { p ->
                            p.hash.isNotBlank() &&
                                BizzlyPermissions.hasPermission(BizzlyRole.fromKey(p.role), mode.permission) &&
                                PinSecurity.verifyPin(p, pin)
                        }
                    }
                    if (matchedProfile != null) {
                        lockout.reset(lockKey)
                        _uiState.update { it.copy(pinOverlay = null) }
                        oneShotAuthorized = true
                        try {
                            mutateData("Autorisé par ${matchedProfile.name}") { it }
                            mode.onAuthorized()
                        } finally {
                            oneShotAuthorized = false
                        }
                    } else {
                        val lockMsg = lockout.recordFailure(lockKey)
                        _uiState.update {
                            it.copy(
                                pinOverlay = ov.copy(
                                    busy = false,
                                    pin = "",
                                    error = lockMsg ?: "Code non autorisé",
                                    shakeTick = ov.shakeTick + 1
                                )
                            )
                        }
                    }
                }
            }
            is PinPromptMode.SetNewPin -> {
                if (mode.firstPin == null) {
                    _uiState.update {
                        it.copy(
                            pinOverlay = ov.copy(
                                mode = mode.copy(firstPin = pin),
                                pin = "",
                                subtitle = "Entrez-le une seconde fois",
                                error = "",
                                busy = false
                            )
                        )
                    }
                } else if (mode.firstPin != pin) {
                    _uiState.update {
                        it.copy(
                            pinOverlay = ov.copy(
                                mode = mode.copy(firstPin = null),
                                pin = "",
                                subtitle = mode.initialSubtitle,
                                error = "Les deux codes sont différents.",
                                shakeTick = ov.shakeTick + 1,
                                busy = false
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(pinOverlay = null) }
                    mode.onPinConfirmed(pin)
                }
            }
        }
    }

    // --- Navigation ---

    fun handleExternalTabRequest(tabRoute: String?) {
        if (tabRoute.isNullOrBlank()) return
        if (!_uiState.value.isLoggedIn) {
            pendingTabRoute = tabRoute
            return
        }
        if (tabRoute == NotificationHelper.TAB_INVENTORY && canTab(BizzlyTab.INVENTORY)) {
            pendingTabRoute = null
            selectTab(BizzlyTab.INVENTORY)
        }
    }

    fun selectTab(tab: BizzlyTab) {
        touchActivity()
        if (!canTab(tab)) {
            showToast("Accès réservé à la caisse ou au gérant")
            return
        }
        _uiState.update { it.copy(currentTab = tab, currentTable = null) }
        if (tab == BizzlyTab.SRV) {
            refreshEnterpriseLogoProposals()
        }
    }

    fun setChartRangeDays(days: Int) {
        _uiState.update { it.copy(chartRangeDays = days) }
    }

    fun setSalesDate(dateIso: String) {
        _uiState.update { it.copy(salesDate = dateIso.ifBlank { BizzlyCalculator.todayIso() }) }
    }

    fun setSalesSearch(query: String) {
        _uiState.update { it.copy(salesSearch = query) }
    }

    // --- Persistence & Sync Scheduler ---

    private fun appendLog(data: BusinessData, message: String?): BusinessData {
        if (message.isNullOrBlank()) return data
        val nextLog = (listOf(ActivityLog(System.currentTimeMillis(), message)) + data.log).take(150)
        return data.copy(log = nextLog)
    }

    private fun mutateData(logMessage: String? = null, transform: (BusinessData) -> BusinessData) {
        touchActivity()
        val acc = _uiState.value.activeAccount ?: return
        val nextData = appendLog(transform(_uiState.value.businessData), logMessage)
        _uiState.update { it.copy(businessData = nextData) }
        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                repository.saveBusinessData(acc.id, nextData)
            }
        }
        scheduleSync()
    }

    private fun mutateAccount(logMessage: String? = null, transform: (BusinessAccount) -> BusinessAccount) {
        touchActivity()
        val curAcc = _uiState.value.activeAccount ?: return
        val updatedAcc = repository.sanitizeAccount(transform(curAcc)) ?: return
        val updatedCurProf = _uiState.value.activeProfile?.let { curP ->
            updatedAcc.profiles.firstOrNull { it.id == curP.id }
        }
        val nextData = BizzlyCalculator.syncServersWithAccount(updatedAcc, appendLog(_uiState.value.businessData, logMessage))
        _uiState.update {
            it.copy(
                activeAccount = updatedAcc,
                activeProfile = updatedCurProf ?: it.activeProfile,
                businessData = nextData
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                repository.saveAccount(updatedAcc)
                repository.saveBusinessData(updatedAcc.id, nextData)
            }
        }
        scheduleSync()
    }

    private fun scheduleSync() {
        if (_uiState.value.syncCreds == null) return
        syncDirty = true
        syncDebounceJob?.cancel()
        syncDebounceJob = viewModelScope.launch {
            delay(700)
            runSyncCycle()
        }
    }

    fun triggerManualSync() {
        _uiState.update { it.copy(syncDead = false) }
        viewModelScope.launch { runSyncCycle() }
    }

    private suspend fun runSyncCycle() {
        val st = _uiState.value
        val creds = st.syncCreds ?: return
        val acc = st.activeAccount ?: return
        if (st.syncDead) return
        if (st.syncBusy) {
            syncAgain = true
            return
        }

        _uiState.update { it.copy(syncBusy = true, syncStatus = SyncStatus.SYNCING) }
        try {
            var curCreds = creds
            var curAcc = acc
            var curData = _uiState.value.businessData

            if (syncDirty || curCreds.snap.isEmpty()) {
                syncDirty = false
                try {
                    val (pushedCreds, preppedData) = syncClient.pushChanges(curCreds, curAcc, curData)
                    curCreds = pushedCreds
                    curData = preppedData
                    withContext(Dispatchers.IO) {
                        repository.saveBusinessData(curAcc.id, curData)
                        repository.saveSyncCredentials(curAcc.id, curCreds)
                    }
                } catch (e: Exception) {
                    syncDirty = true
                    throw e
                }
            }

            val pull = syncClient.pullChanges(
                creds = curCreds,
                account = curAcc,
                data = curData,
                seenVersions = seenDocVersions,
                activeProfileId = _uiState.value.activeProfile?.id,
                currentOpenTable = _uiState.value.currentTable
            )

            withContext(Dispatchers.IO) {
                repository.saveSyncCredentials(pull.account.id, pull.credentials)
                if (pull.account != curAcc) {
                    repository.saveAccount(pull.account)
                }
                if (pull.changedCount > 0) {
                    repository.saveBusinessData(pull.account.id, pull.data)
                }
            }

            if (pull.mustLockProfileRemoved) {
                lockNow()
                showToast("Votre profil a été modifié sur un autre téléphone.")
                return
            }

            _uiState.update { cur ->
                val curT = cur.currentTable
                val tableClosedRemotely = pull.openTableGone != null && curT == pull.openTableGone
                cur.copy(
                    syncCreds = pull.credentials,
                    activeAccount = pull.account,
                    activeProfile = cur.activeProfile?.let { p ->
                        pull.account.profiles.firstOrNull { it.id == p.id }
                    },
                    businessData = if (pull.changedCount > 0) pull.data else curData,
                    currentTable = if (tableClosedRemotely) null else cur.currentTable,
                    syncBusy = false,
                    syncStatus = SyncStatus.OK,
                    lastSyncMs = System.currentTimeMillis(),
                    syncError = ""
                )
            }
            if (pull.openTableGone != null) {
                showToast("La table ${pull.openTableGone} a été encaissée ou libérée.")
            }
        } catch (e: SyncException) {
            if (e.isInvalidKey) {
                _uiState.update {
                    it.copy(
                        syncBusy = false,
                        syncDead = true,
                        syncStatus = SyncStatus.ERROR,
                        syncError = "Le code de synchronisation a été changé. Liez à nouveau ce téléphone."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        syncBusy = false,
                        syncStatus = SyncStatus.ERROR,
                        syncError = if (e.isOffline) "Hors ligne" else (e.message ?: "Erreur réseau")
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    syncBusy = false,
                    syncStatus = SyncStatus.ERROR,
                    syncError = e.message ?: "Erreur"
                )
            }
        }

        if (syncAgain && !_uiState.value.syncDead) {
            syncAgain = false
            runSyncCycle()
        }
    }

    // --- Tables & POS ---

    fun openTable(tableNumber: Int) {
        _uiState.update {
            it.copy(
                currentTable = tableNumber,
                paymentMode = "Espèces",
                cashGivenText = "",
                mixedMode1 = "Espèces",
                mixedAmount1Text = "",
                mixedMode2 = "Mobile Money"
            )
        }
    }

    fun closeTableDetail() {
        _uiState.update {
            it.copy(
                currentTable = null,
                paymentMode = "Espèces",
                cashGivenText = "",
                mixedMode1 = "Espèces",
                mixedAmount1Text = "",
                mixedMode2 = "Mobile Money"
            )
        }
    }

    fun setPaymentMode(mode: String) {
        _uiState.update { it.copy(paymentMode = mode, cashGivenText = "", mixedAmount1Text = "") }
    }

    fun setCashGiven(text: String) {
        _uiState.update { it.copy(cashGivenText = text.filter { ch -> ch.isDigit() }) }
    }

    fun setMixedMode1(mode: String) {
        _uiState.update { st ->
            val nextMode2 = if (st.mixedMode2 == mode) {
                if (mode == "Espèces") "Mobile Money" else "Espèces"
            } else st.mixedMode2
            st.copy(mixedMode1 = mode, mixedMode2 = nextMode2, cashGivenText = "")
        }
    }

    fun setMixedAmount1(text: String) {
        _uiState.update { it.copy(mixedAmount1Text = text.filter { ch -> ch.isDigit() }) }
    }

    fun setMixedMode2(mode: String) {
        _uiState.update { st ->
            val nextMode1 = if (st.mixedMode1 == mode) {
                if (mode == "Espèces") "Mobile Money" else "Espèces"
            } else st.mixedMode1
            st.copy(mixedMode1 = nextMode1, mixedMode2 = mode, cashGivenText = "")
        }
    }

    fun setTableServer(tableNumber: Int, serverId: Long) {
        if (!requirePermission(BizzlyPermissions.COMMANDE) { setTableServer(tableNumber, serverId) }) return
        mutateData { data ->
            val key = tableNumber.toString()
            val cur = data.tb[key] ?: OpenTable(sv = serverId)
            val nextTb = data.tb.toMutableMap()
            nextTb[key] = cur.copy(sv = serverId)
            data.copy(tb = nextTb)
        }
    }

    fun adjustTableItem(tableNumber: Int, productId: Long, delta: Double) {
        if (!requirePermission(BizzlyPermissions.COMMANDE) { adjustTableItem(tableNumber, productId, delta) }) return
        val prod = BizzlyCalculator.productById(_uiState.value.businessData, productId) ?: return
        val key = tableNumber.toString()
        val pidKey = productId.toString()
        val msg = "Table $tableNumber : ${if (delta > 0) "+" else "−"}1 ${prod.n}"

        mutateData(msg) { data ->
            val defaultSrv = data.srv.firstOrNull()?.id ?: 0L
            val cur = data.tb[key] ?: OpenTable(sv = defaultSrv)
            val nextItems = cur.it.toMutableMap()
            val nextQty = ((nextItems[pidKey] ?: 0.0) + delta).coerceAtLeast(0.0)
            if (nextQty > 0.0) nextItems[pidKey] = nextQty else nextItems.remove(pidKey)

            val nextComped = cur.comped.toMutableMap()
            val curComp = nextComped[pidKey] ?: 0.0
            if (curComp > nextQty) {
                if (nextQty > 0.0) nextComped[pidKey] = nextQty else nextComped.remove(pidKey)
            }

            val nextTb = data.tb.toMutableMap()
            if (nextItems.isEmpty() && cur.sv == defaultSrv && cur.paidPartial <= 0.0) {
                nextTb.remove(key)
            } else {
                nextTb[key] = cur.copy(it = nextItems, comped = nextComped)
            }
            data.copy(tb = nextTb)
        }
    }

    fun applyTableDiscountAndComped(
        tableNumber: Int,
        discountType: String,
        discountValue: Double,
        reason: String,
        compedMap: Map<String, Double>
    ) {
        val cleanReason = reason.trim()
        val hasAnyReduction = (discountType != "none" && discountValue > 0.0) || compedMap.values.any { it > 0.0 }
        if (hasAnyReduction && cleanReason.isEmpty()) {
            showToast("Un motif est obligatoire pour la remise ou les offerts.")
            return
        }
        if (!requireManagerPin("Autorisation Gérant (Remise / Offert)") {
                applyTableDiscountAndComped(tableNumber, discountType, discountValue, cleanReason, compedMap)
            }
        ) return

        val key = tableNumber.toString()
        val tbl = _uiState.value.businessData.tb[key] ?: return
        val cleanComped = LinkedHashMap<String, Double>()
        for ((pidStr, cq) in compedMap) {
            val maxQ = tbl.it[pidStr] ?: 0.0
            if (maxQ > 0.0 && cq > 0.0) {
                cleanComped[pidStr] = cq.coerceIn(0.0, maxQ)
            }
        }
        val cleanType = if (discountType == "percent" || discountType == "amount") discountType else "none"
        val cleanVal = when (cleanType) {
            "percent" -> discountValue.coerceIn(0.0, 100.0)
            "amount" -> discountValue.coerceAtLeast(0.0)
            else -> 0.0
        }
        val desc = buildString {
            append("Remise / offerts Table $tableNumber")
            if (cleanType == "percent" && cleanVal > 0) append(" (-${BizzlyCalculator.formatQty(cleanVal)}%)")
            else if (cleanType == "amount" && cleanVal > 0) append(" (-${BizzlyCalculator.formatFcfa(cleanVal)})")
            val compTotal = cleanComped.values.sum()
            if (compTotal > 0) append(" · ${BizzlyCalculator.formatQty(compTotal)} offert(s)")
            if (cleanReason.isNotBlank()) append(" · Motif : $cleanReason")
        }

        mutateData(desc) { d ->
            val current = d.tb[key] ?: return@mutateData d
            val tbMut = d.tb.toMutableMap()
            tbMut[key] = current.copy(
                comped = cleanComped,
                discountType = if (cleanVal > 0.0) cleanType else "none",
                discountValue = if (cleanVal > 0.0) cleanVal else 0.0,
                discountReason = if (hasAnyReduction) cleanReason else ""
            )
            d.copy(tb = tbMut)
        }
        showToast(if (hasAnyReduction) "Remise appliquée et tracée au journal." else "Remises retirées.")
    }

    fun payTable(tableNumber: Int) {
        if (!requirePermission(BizzlyPermissions.ENCAISSER) { payTable(tableNumber) }) return
        val st = _uiState.value
        val acc = st.activeAccount ?: return
        val data = st.businessData
        val key = tableNumber.toString()
        val tbl = data.tb[key] ?: return
        val breakdown = BizzlyCalculator.calculateTableBreakdown(data, tbl)
        val due = breakdown.remainingDue
        if (due <= 0.0 && tbl.it.isEmpty()) return

        val payments: List<PaymentPart>
        val modeLabel: String
        val cashDue: Double
        if (st.paymentMode == "Mixte") {
            val amt1 = st.mixedAmount1Text.toDoubleOrNull() ?: 0.0
            if (amt1 <= 0.0 || amt1 >= due) {
                showToast("En mode Mixte, saisissez un premier montant entre 1 F et ${BizzlyCalculator.formatFcfa(due - 1)}.")
                return
            }
            val amt2 = (due - amt1).coerceAtLeast(0.0)
            payments = listOf(
                PaymentPart(st.mixedMode1, amt1),
                PaymentPart(st.mixedMode2, amt2)
            )
            modeLabel = "${st.mixedMode1} (${BizzlyCalculator.formatFcfa(amt1)}) + ${st.mixedMode2} (${BizzlyCalculator.formatFcfa(amt2)})"
            cashDue = payments.filter { it.mode == "Espèces" }.sumOf { it.amount }
        } else {
            payments = listOf(PaymentPart(st.paymentMode, due))
            modeLabel = st.paymentMode
            cashDue = if (st.paymentMode == "Espèces") due else 0.0
        }

        val given = if (cashDue > 0.0) st.cashGivenText.toDoubleOrNull() ?: 0.0 else 0.0
        if (given > 0.0 && given < cashDue) {
            showToast("Le montant donné en espèces est insuffisant.")
            return
        }

        val today = BizzlyCalculator.todayIso()
        val srvName = BizzlyCalculator.serverName(data, tbl.sv)
        val changeSuffix = if (given > 0.0 && cashDue > 0.0) " · rendu ${BizzlyCalculator.formatFcfa(given - cashDue)}" else ""
        val discSuffix = if (breakdown.tableDiscount + breakdown.compedDiscount > 0.0) {
            " · remise ${BizzlyCalculator.formatFcfa(breakdown.tableDiscount + breakdown.compedDiscount)}"
        } else ""
        val msg = "Table $tableNumber encaissée · ${BizzlyCalculator.formatFcfa(due)} · $srvName · $modeLabel$discSuffix$changeSuffix"

        val physicalSoldMap = LinkedHashMap<Long, Double>()
        tbl.it.forEach { (pidStr, q) ->
            val pid = pidStr.toLongOrNull() ?: return@forEach
            if (q > 0.0) physicalSoldMap[pid] = (physicalSoldMap[pid] ?: 0.0) + q
        }

        mutateData(msg) { d ->
            val svMut = d.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
            val todayMap = svMut.getOrPut(today) { mutableMapOf() }
            for ((pidStr, q) in tbl.it) {
                val compQ = (tbl.comped[pidStr] ?: 0.0).coerceIn(0.0, q)
                val paidQ = (q - compQ).coerceAtLeast(0.0)
                if (paidQ > 0.0) {
                    todayMap[pidStr] = (todayMap[pidStr] ?: 0.0) + paidQ
                }
            }
            val paidRec = PaidInvoice(
                d = today,
                t = tableNumber,
                sv = tbl.sv,
                tot = due,
                m = modeLabel,
                u = syncClient.newUid(),
                ts = System.currentTimeMillis(),
                payments = payments,
                discountAmount = breakdown.tableDiscount + breakdown.compedDiscount,
                discountReason = tbl.discountReason
            )
            val tbMut = d.tb.toMutableMap().apply { remove(key) }
            d.copy(
                sv = svMut,
                paid = listOf(paidRec) + d.paid,
                tb = tbMut
            )
        }
        _uiState.update {
            it.copy(
                currentTable = null,
                cashGivenText = "",
                paymentMode = "Espèces",
                mixedAmount1Text = ""
            )
        }
        showToast("Table $tableNumber encaissée")

        viewModelScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                inventoryRepository.deductForSoldQuantities(acc.id, data.prod, physicalSoldMap)
            }
            handleStockAlertEvents(alerts)
        }
    }

    fun payTableEqualSplit(
        tableNumber: Int,
        totalParts: Int,
        payments: List<PaymentPart>,
        cashGiven: Double
    ) {
        if (!requirePermission(BizzlyPermissions.ENCAISSER) {
                payTableEqualSplit(tableNumber, totalParts, payments, cashGiven)
            }
        ) return
        val st = _uiState.value
        val acc = st.activeAccount ?: return
        val data = st.businessData
        val key = tableNumber.toString()
        val tbl = data.tb[key] ?: return
        val breakdown = BizzlyCalculator.calculateTableBreakdown(data, tbl)
        val remaining = breakdown.remainingDue
        if (remaining <= 0.0) return

        val safeTotalParts = totalParts.coerceIn(2, 20)
        val nextPartIdx = tbl.paidCount + 1
        val remainingParts = (safeTotalParts - tbl.paidCount).coerceAtLeast(1)
        val partAmount = if (remainingParts <= 1) {
            remaining
        } else {
            (remaining / remainingParts).roundToLong().toDouble().coerceIn(1.0, remaining)
        }
        val isFinalPart = remainingParts <= 1 || (remaining - partAmount) < 1.0
        val collected = if (isFinalPart) remaining else partAmount

        val cashPart = payments.filter { it.mode == "Espèces" }.sumOf { it.amount }
        if (cashGiven > 0.0 && cashGiven < cashPart) {
            showToast("Le montant donné en espèces est insuffisant.")
            return
        }

        val today = BizzlyCalculator.todayIso()
        val modeLabel = if (payments.size > 1) {
            payments.joinToString(" + ") { "${it.mode} (${BizzlyCalculator.formatFcfa(it.amount)})" }
        } else {
            payments.firstOrNull()?.mode ?: "Espèces"
        }

        if (isFinalPart) {
            val physicalSoldMap = LinkedHashMap<Long, Double>()
            tbl.it.forEach { (pidStr, q) ->
                val pid = pidStr.toLongOrNull() ?: return@forEach
                if (q > 0.0) physicalSoldMap[pid] = (physicalSoldMap[pid] ?: 0.0) + q
            }
            val msg = "Table $tableNumber soldée (part $nextPartIdx/$safeTotalParts) · ${BizzlyCalculator.formatFcfa(collected)} · $modeLabel"
            mutateData(msg) { d ->
                val svMut = d.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
                val todayMap = svMut.getOrPut(today) { mutableMapOf() }
                for ((pidStr, q) in tbl.it) {
                    val compQ = (tbl.comped[pidStr] ?: 0.0).coerceIn(0.0, q)
                    val paidQ = (q - compQ).coerceAtLeast(0.0)
                    if (paidQ > 0.0) {
                        todayMap[pidStr] = (todayMap[pidStr] ?: 0.0) + paidQ
                    }
                }
                val paidRec = PaidInvoice(
                    d = today,
                    t = tableNumber,
                    sv = tbl.sv,
                    tot = collected,
                    m = modeLabel,
                    u = syncClient.newUid(),
                    ts = System.currentTimeMillis(),
                    payments = payments,
                    discountAmount = breakdown.tableDiscount + breakdown.compedDiscount,
                    discountReason = tbl.discountReason
                )
                val tbMut = d.tb.toMutableMap().apply { remove(key) }
                d.copy(sv = svMut, paid = listOf(paidRec) + d.paid, tb = tbMut)
            }
            _uiState.update { it.copy(currentTable = null, cashGivenText = "", paymentMode = "Espèces", mixedAmount1Text = "") }
            showToast("Table $tableNumber entièrement réglée !")
            viewModelScope.launch {
                val alerts = withContext(Dispatchers.IO) {
                    inventoryRepository.deductForSoldQuantities(acc.id, data.prod, physicalSoldMap)
                }
                handleStockAlertEvents(alerts)
            }
        } else {
            val msg = "Part $nextPartIdx/$safeTotalParts Table $tableNumber · ${BizzlyCalculator.formatFcfa(collected)} · $modeLabel"
            mutateData(msg) { d ->
                val cur = d.tb[key] ?: return@mutateData d
                val paidRec = PaidInvoice(
                    d = today,
                    t = tableNumber,
                    sv = cur.sv,
                    tot = collected,
                    m = modeLabel,
                    u = syncClient.newUid(),
                    ts = System.currentTimeMillis(),
                    payments = payments
                )
                val tbMut = d.tb.toMutableMap()
                tbMut[key] = cur.copy(
                    paidPartial = cur.paidPartial + collected,
                    paidCount = nextPartIdx,
                    splitTotalParts = safeTotalParts
                )
                d.copy(paid = listOf(paidRec) + d.paid, tb = tbMut)
            }
            showToast("Part $nextPartIdx/$safeTotalParts encaissée (${BizzlyCalculator.formatFcfa(collected)})")
        }
    }

    fun payTableByItems(
        tableNumber: Int,
        selectedItems: Map<String, Double>,
        payments: List<PaymentPart>,
        cashGiven: Double
    ) {
        if (!requirePermission(BizzlyPermissions.ENCAISSER) {
                payTableByItems(tableNumber, selectedItems, payments, cashGiven)
            }
        ) return
        val st = _uiState.value
        val acc = st.activeAccount ?: return
        val data = st.businessData
        val key = tableNumber.toString()
        val tbl = data.tb[key] ?: return
        val fullBreakdown = BizzlyCalculator.calculateTableBreakdown(data, tbl)

        var selectedRaw = 0.0
        val physicalSoldMap = LinkedHashMap<Long, Double>()
        for ((pidStr, reqQ) in selectedItems) {
            val availQ = tbl.it[pidStr] ?: 0.0
            val takeQ = reqQ.coerceIn(0.0, availQ)
            if (takeQ <= 0.0) continue
            val p = BizzlyCalculator.productByIdStr(data, pidStr) ?: continue
            val compAvail = (tbl.comped[pidStr] ?: 0.0).coerceIn(0.0, availQ)
            val paidShare = ((availQ - compAvail) / availQ.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            selectedRaw += takeQ * paidShare * p.pv
            val pid = pidStr.toLongOrNull()
            if (pid != null) physicalSoldMap[pid] = (physicalSoldMap[pid] ?: 0.0) + takeQ
        }
        if (physicalSoldMap.isEmpty()) return

        val remainingItemsTotalQty = tbl.it.entries.sumOf { (pidStr, q) ->
            (q - (selectedItems[pidStr] ?: 0.0)).coerceAtLeast(0.0)
        }
        val closesTable = remainingItemsTotalQty <= 0.0001
        val collected = if (closesTable) {
            fullBreakdown.remainingDue
        } else {
            val ratio = if (fullBreakdown.afterComped > 0.0) {
                (selectedRaw / fullBreakdown.afterComped).coerceIn(0.0, 1.0)
            } else 1.0
            (fullBreakdown.remainingDue * ratio).roundToLong().toDouble().coerceAtMost(fullBreakdown.remainingDue)
        }

        val cashPart = payments.filter { it.mode == "Espèces" }.sumOf { it.amount }
        if (cashGiven > 0.0 && cashGiven < cashPart) {
            showToast("Le montant donné en espèces est insuffisant.")
            return
        }

        val today = BizzlyCalculator.todayIso()
        val modeLabel = if (payments.size > 1) {
            payments.joinToString(" + ") { "${it.mode} (${BizzlyCalculator.formatFcfa(it.amount)})" }
        } else {
            payments.firstOrNull()?.mode ?: "Espèces"
        }
        val msg = "Encaissement partiel Table $tableNumber · ${BizzlyCalculator.formatFcfa(collected)} · $modeLabel"

        mutateData(msg) { d ->
            val cur = d.tb[key] ?: return@mutateData d
            val svMut = d.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
            val todayMap = svMut.getOrPut(today) { mutableMapOf() }
            val nextItems = cur.it.toMutableMap()
            val nextComped = cur.comped.toMutableMap()

            for ((pidStr, reqQ) in selectedItems) {
                val avail = nextItems[pidStr] ?: continue
                val take = reqQ.coerceIn(0.0, avail)
                if (take <= 0.0) continue
                val compAvail = (nextComped[pidStr] ?: 0.0).coerceIn(0.0, avail)
                val compUsed = (compAvail * (take / avail)).coerceAtMost(take)
                val paidQ = (take - compUsed).coerceAtLeast(0.0)
                if (paidQ > 0.0) {
                    todayMap[pidStr] = (todayMap[pidStr] ?: 0.0) + paidQ
                }
                val left = (avail - take).coerceAtLeast(0.0)
                if (left > 0.0) nextItems[pidStr] = left else nextItems.remove(pidStr)
                val leftComp = (compAvail - compUsed).coerceIn(0.0, left)
                if (leftComp > 0.0) nextComped[pidStr] = leftComp else nextComped.remove(pidStr)
            }

            val paidRec = PaidInvoice(
                d = today,
                t = tableNumber,
                sv = cur.sv,
                tot = collected,
                m = modeLabel,
                u = syncClient.newUid(),
                ts = System.currentTimeMillis(),
                payments = payments,
                discountAmount = if (closesTable) fullBreakdown.tableDiscount + fullBreakdown.compedDiscount else 0.0,
                discountReason = if (closesTable) cur.discountReason else ""
            )
            val tbMut = d.tb.toMutableMap()
            if (nextItems.isEmpty()) {
                tbMut.remove(key)
            } else {
                tbMut[key] = cur.copy(
                    it = nextItems,
                    comped = nextComped,
                    paidCount = cur.paidCount + 1
                )
            }
            d.copy(sv = svMut, paid = listOf(paidRec) + d.paid, tb = tbMut)
        }

        if (closesTable) {
            _uiState.update { it.copy(currentTable = null, cashGivenText = "", paymentMode = "Espèces", mixedAmount1Text = "") }
            showToast("Table $tableNumber entièrement soldée !")
        } else {
            showToast("Articles encaissés (${BizzlyCalculator.formatFcfa(collected)})")
        }

        viewModelScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                inventoryRepository.deductForSoldQuantities(acc.id, data.prod, physicalSoldMap)
            }
            handleStockAlertEvents(alerts)
        }
    }

    fun freeTable(tableNumber: Int) {
        if (!requirePermission(BizzlyPermissions.SUPPR) { freeTable(tableNumber) }) return
        askConfirm("Libérer la table $tableNumber sans encaisser ?", isDestructive = true) {
            mutateData("Table $tableNumber libérée") { data ->
                val tbMut = data.tb.toMutableMap().apply { remove(tableNumber.toString()) }
                data.copy(tb = tbMut)
            }
            _uiState.update { it.copy(currentTable = null, cashGivenText = "", paymentMode = "Espèces", mixedAmount1Text = "") }
        }
    }

    // --- Sales Counter ---

    fun adjustDailySale(productId: Long, delta: Double) {
        if (!requirePermission(BizzlyPermissions.VENTES) { adjustDailySale(productId, delta) }) return
        val acc = _uiState.value.activeAccount ?: return
        val date = _uiState.value.salesDate
        val dataSnapshot = _uiState.value.businessData
        val prod = BizzlyCalculator.productById(dataSnapshot, productId) ?: return
        val pidKey = productId.toString()
        val prevQty = dataSnapshot.sv[date]?.get(pidKey) ?: 0.0
        val nextQty = (prevQty + delta).coerceAtLeast(0.0)
        val appliedDelta = nextQty - prevQty
        if (appliedDelta == 0.0) return

        val msg = "${if (appliedDelta > 0) "Vente +1 " else "Vente −1 "}${prod.n} · ${BizzlyCalculator.formatFcfa(prod.pv)}"
        mutateData(msg) { data ->
            val svMut = data.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
            val dayMap = svMut.getOrPut(date) { mutableMapOf() }
            if (nextQty > 0.0) dayMap[pidKey] = nextQty else dayMap.remove(pidKey)
            if (dayMap.isEmpty()) svMut.remove(date)
            data.copy(sv = svMut)
        }

        viewModelScope.launch {
            val alerts = withContext(Dispatchers.IO) {
                inventoryRepository.deductForSoldQuantities(
                    businessId = acc.id,
                    products = dataSnapshot.prod,
                    soldDeltaByProductId = mapOf(productId to appliedDelta)
                )
            }
            handleStockAlertEvents(alerts)
        }
    }

    // --- Stock & Products ---

    fun setStockAlertThreshold(threshold: Int) {
        if (!requirePermission(BizzlyPermissions.STOCK) { setStockAlertThreshold(threshold) }) return
        val safe = threshold.coerceIn(1, 100_000)
        mutateData("Seuil d’alerte stock : $safe") { it.copy(seuil = safe) }
    }

    fun updateProductField(productId: Long, field: String, rawValue: String) {
        val perm = if (field == "pv") BizzlyPermissions.PRIX else BizzlyPermissions.STOCK
        if (!requirePermission(perm) { updateProductField(productId, field, rawValue) }) return
        val prod = BizzlyCalculator.productById(_uiState.value.businessData, productId) ?: return
        val numVal = rawValue.trim().toDoubleOrNull()?.let { BizzlyCalculator.clampNumber(it) }
        val label = when (field) {
            "d" -> "Stock début"
            "e" -> "Entrée stock"
            "f" -> "Comptage"
            else -> "Prix"
        }
        val msgVal = if (field == "f" && rawValue.trim().isEmpty()) "effacé" else (numVal?.toInt()?.toString() ?: "0")
        mutateData("$label · ${prod.n} : $msgVal") { data ->
            val nextProds = data.prod.map { p ->
                if (p.id != productId) p else when (field) {
                    "d" -> p.copy(d = numVal ?: 0.0)
                    "e" -> p.copy(e = numVal ?: 0.0)
                    "f" -> p.copy(f = if (rawValue.trim().isEmpty()) null else (numVal ?: 0.0))
                    else -> p.copy(pv = numVal ?: 0.0)
                }
            }
            data.copy(prod = nextProds)
        }
    }

    fun addProduct(nameRaw: String, priceRaw: String) {
        if (!requirePermission(BizzlyPermissions.PRODUITS) { addProduct(nameRaw, priceRaw) }) return
        val name = nameRaw.trim().uppercase()
        if (name.isEmpty()) return
        val price = BizzlyCalculator.clampNumber(priceRaw.toDoubleOrNull() ?: 0.0)
        mutateData("Produit ajouté : $name") { data ->
            val newId = data.next
            val p = Product(id = newId, n = name, pv = price)
            data.copy(prod = data.prod + p, next = newId + 1L)
        }
    }

    fun deleteProduct(productId: Long) {
        if (!requirePermission(BizzlyPermissions.PRODUITS) { deleteProduct(productId) }) return
        val data = _uiState.value.businessData
        val prod = BizzlyCalculator.productById(data, productId) ?: return
        if (BizzlyCalculator.isProductInOpenTable(data, productId)) {
            showToast("Ce produit est dans une table ouverte. Encaissez ou libérez la table d’abord.")
            return
        }
        askConfirm("Supprimer ${prod.n} ?", isDestructive = true) {
            mutateData("Produit supprimé : ${prod.n}") { d ->
                d.copy(prod = d.prod.filterNot { it.id == productId })
            }
        }
    }

    fun loadSampleCatalog() {
        if (!requirePermission(BizzlyPermissions.PRODUITS) { loadSampleCatalog() }) return
        mutateData("Liste d’exemple chargée") { data ->
            var nextId = data.next
            val added = SampleCatalog.defaultItems.map { (name, price) ->
                Product(id = nextId++, n = name, pv = price)
            }
            data.copy(prod = data.prod + added, next = nextId)
        }
        showToast("Liste d’exemple chargée")
    }

    // --- Room Inventory & Stock Threshold Alerts ---

    fun saveInventoryItem(
        id: Long?,
        name: String,
        sku: String,
        category: String,
        unit: String,
        quantity: Double,
        minThreshold: Double,
        unitPrice: Double,
        notes: String
    ) {
        if (!requirePermission(BizzlyPermissions.STOCK) {
                saveInventoryItem(id, name, sku, category, unit, quantity, minThreshold, unitPrice, notes)
            }
        ) return
        val acc = _uiState.value.activeAccount ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            showToast("Le nom de l’article est obligatoire.")
            return
        }
        val cleanQty = quantity.coerceAtLeast(0.0)
        val cleanMin = minThreshold.coerceAtLeast(0.0)
        val cleanPrice = unitPrice.coerceAtLeast(0.0)
        val cleanUnit = unit.trim().ifEmpty { "unité" }

        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val existing = id?.let { inventoryItems.value.firstOrNull { row -> row.id == it } }
                val entity = InventoryItemEntity(
                    id = id ?: 0L,
                    businessId = acc.id,
                    productId = existing?.productId,
                    name = cleanName,
                    sku = sku.trim(),
                    category = category.trim(),
                    unit = cleanUnit,
                    quantity = cleanQty,
                    minThreshold = cleanMin,
                    unitPrice = cleanPrice,
                    notes = notes.trim()
                )
                inventoryRepository.upsertItem(entity)
                entity
            }
            if (!notifyIfCrossedThreshold(saved, wasLowBefore = false)) {
                showToast(if (id == null) "Article ajouté à l’inventaire." else "Article mis à jour.")
            }
        }
    }

    fun adjustInventoryQuantity(item: InventoryItemEntity, delta: Double) {
        if (!requirePermission(BizzlyPermissions.STOCK) { adjustInventoryQuantity(item, delta) }) return
        val wasLow = item.isBelowThreshold
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                inventoryRepository.adjustQuantity(item.id, delta)
            } ?: return@launch
            notifyIfCrossedThreshold(updated, wasLowBefore = wasLow)
        }
    }

    fun setInventoryQuantity(item: InventoryItemEntity, quantity: Double) {
        if (!requirePermission(BizzlyPermissions.STOCK) { setInventoryQuantity(item, quantity) }) return
        val wasLow = item.isBelowThreshold
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                inventoryRepository.setQuantity(item.id, quantity)
            } ?: return@launch
            if (!notifyIfCrossedThreshold(updated, wasLowBefore = wasLow)) {
                showToast("${updated.name} : ${BizzlyCalculator.formatQty(updated.quantity)} ${updated.unit}")
            }
        }
    }

    fun deleteInventoryItem(item: InventoryItemEntity) {
        if (!requirePermission(BizzlyPermissions.STOCK) { deleteInventoryItem(item) }) return
        askConfirm("Supprimer « ${item.name} » de l’inventaire ?", isDestructive = true) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    inventoryRepository.deleteItem(item.id)
                }
                showToast("Article supprimé.")
            }
        }
    }

    fun syncInventoryFromCatalog() {
        if (!requirePermission(BizzlyPermissions.STOCK) { syncInventoryFromCatalog() }) return
        val acc = _uiState.value.activeAccount ?: return
        val data = _uiState.value.businessData
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                inventoryRepository.syncMissingFromCatalog(acc.id, data)
            }
            if (added > 0) {
                showToast("$added article(s) importé(s) depuis le catalogue.")
            } else {
                showToast("Tous les produits du catalogue sont déjà dans l’inventaire.")
            }
        }
    }

    private fun shouldNotifyCurrentRole(): Boolean {
        val role = currentRole()
        return role == BizzlyRole.GERANT || role == BizzlyRole.CAISSE
    }

    private fun handleStockAlertEvents(events: List<StockAlertEvent>) {
        if (events.isEmpty()) return
        val acc = _uiState.value.activeAccount ?: return
        val items = events.map { it.item }
        if (shouldNotifyCurrentRole()) {
            NotificationHelper.notifyStockAlerts(getApplication(), acc.name, items)
        }
        val first = items.first()
        val msg = when {
            items.size == 1 && first.quantity <= 0.0 -> "Rupture de stock : ${first.name}"
            items.size == 1 -> "Alerte stock bas : ${first.name} (${BizzlyCalculator.formatQty(first.quantity)} / seuil ${BizzlyCalculator.formatQty(first.minThreshold)})"
            else -> "${items.size} alertes de stock déclenchées (${items.take(2).joinToString(", ") { it.name }}…)"
        }
        showToast(msg)
    }

    private fun notifyIfCrossedThreshold(item: InventoryItemEntity, wasLowBefore: Boolean): Boolean {
        val acc = _uiState.value.activeAccount
        return when {
            item.quantity <= 0.0 -> {
                if (acc != null && shouldNotifyCurrentRole()) {
                    NotificationHelper.notifyStockAlerts(getApplication(), acc.name, listOf(item))
                }
                showToast("Rupture de stock : ${item.name}")
                true
            }
            item.isBelowThreshold && !wasLowBefore -> {
                if (acc != null && shouldNotifyCurrentRole()) {
                    NotificationHelper.notifyStockAlerts(getApplication(), acc.name, listOf(item))
                }
                showToast(
                    "Alerte stock bas : ${item.name} (${BizzlyCalculator.formatQty(item.quantity)} / seuil ${BizzlyCalculator.formatQty(item.minThreshold)})"
                )
                true
            }
            else -> false
        }
    }

    fun syncBarToInventory() {
        if (!requirePermission(BizzlyPermissions.STOCK) { syncBarToInventory() }) return
        val acc = _uiState.value.activeAccount ?: return
        val data = _uiState.value.businessData
        if (data.prod.isEmpty()) {
            showToast("Aucun produit dans le catalogue.")
            return
        }
        askConfirm(
            "Mettre à jour l’inventaire avec les quantités actuelles du Comptage Bar (${data.prod.size} produits) ?"
        ) {
            viewModelScope.launch {
                val (count, alerts) = withContext(Dispatchers.IO) {
                    inventoryRepository.syncFromBarCounts(acc.id, data)
                }
                mutateData("Synchro Comptage Bar → Inventaire ($count articles)") { it }
                if (alerts.isNotEmpty()) {
                    handleStockAlertEvents(alerts)
                } else {
                    showToast("$count article(s) synchronisé(s) vers l’inventaire.")
                }
            }
        }
    }

    fun syncInventoryToBar() {
        if (!requirePermission(BizzlyPermissions.STOCK) { syncInventoryToBar() }) return
        val acc = _uiState.value.activeAccount ?: return
        val data = _uiState.value.businessData
        if (data.prod.isEmpty()) {
            showToast("Aucun produit dans le catalogue.")
            return
        }
        askConfirm(
            "Copier les quantités actuelles de l’Inventaire dans la colonne « Fin » du Comptage Bar ?"
        ) {
            viewModelScope.launch {
                val counts = withContext(Dispatchers.IO) {
                    inventoryRepository.getBarCountsFromInventory(acc.id, data)
                }
                if (counts.isEmpty()) {
                    showToast("Aucun produit correspondant trouvé dans l’inventaire.")
                    return@launch
                }
                mutateData("Synchro Inventaire → Comptage Bar (${counts.size} articles)") { d ->
                    val nextProds = d.prod.map { p ->
                        val qty = counts[p.id]
                        if (qty != null) p.copy(f = qty) else p
                    }
                    d.copy(prod = nextProds)
                }
                showToast("${counts.size} produit(s) mis à jour dans le Comptage Bar.")
            }
        }
    }

    fun exportRestockOrderPdf(supplierName: String, lines: List<RestockOrderLine>) {
        if (!requirePermission(BizzlyPermissions.EXPORT) { exportRestockOrderPdf(supplierName, lines) }) return
        if (lines.isEmpty()) {
            showToast("Aucun article à commander.")
            return
        }
        val acc = _uiState.value.activeAccount
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ExportUtils.generateRestockOrderPdf(acc, supplierName, lines)
                }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-bon-commande-${BizzlyCalculator.todayIso()}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = "Bon de commande fournisseur PDF"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le bon de commande PDF.")
            }
        }
    }

    fun exportRestockOrderExcel(supplierName: String, lines: List<RestockOrderLine>) {
        if (!requirePermission(BizzlyPermissions.EXPORT) { exportRestockOrderExcel(supplierName, lines) }) return
        if (lines.isEmpty()) {
            showToast("Aucun article à commander.")
            return
        }
        val acc = _uiState.value.activeAccount
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ExportUtils.generateRestockOrderExcel(acc, supplierName, lines)
                }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-bon-commande-${BizzlyCalculator.todayIso()}.xlsx"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    chooserTitle = "Bon de commande fournisseur Excel"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le bon de commande Excel.")
            }
        }
    }



    // --- Cash Movements (Versements, Dépenses, Bons) ---

    fun addCashMovement(kind: String, label: String, amountRaw: String, dateIso: String, productId: Long?, qtyRaw: String) {
        val perm = if (kind == "dep") BizzlyPermissions.DEPENSES else BizzlyPermissions.ARGENT
        if (!requirePermission(perm) { addCashMovement(kind, label, amountRaw, dateIso, productId, qtyRaw) }) return
        val amount = BizzlyCalculator.clampNumber(amountRaw.toDoubleOrNull() ?: 0.0)
        if (amount == 0.0) return
        val safeDate = if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateIso)) dateIso else BizzlyCalculator.todayIso()
        val qty = if (kind == "dep" && productId != null) BizzlyCalculator.clampNumber(qtyRaw.toDoubleOrNull() ?: 0.0) else null
        val movement = CashMovement(
            d = safeDate,
            w = label.trim(),
            m = amount,
            pid = if (kind == "dep") productId else null,
            q = qty,
            u = syncClient.newUid(),
            ts = System.currentTimeMillis()
        )
        val kindTitle = when (kind) {
            "vers" -> "Versement"
            "dep" -> "Dépense"
            else -> "Bon personnel"
        }
        val prodSuffix = if (kind == "dep" && productId != null) {
            val pName = BizzlyCalculator.productById(_uiState.value.businessData, productId)?.n ?: "?"
            " (+${BizzlyCalculator.formatQty(qty ?: 0.0)} $pName en stock)"
        } else ""
        val logMsg = "$kindTitle : ${BizzlyCalculator.formatFcfa(amount)}" +
            (if (movement.w.isNotBlank()) " · ${movement.w}" else "") + prodSuffix

        mutateData(logMsg) { d ->
            when (kind) {
                "vers" -> d.copy(vers = d.vers + movement)
                "dep" -> d.copy(dep = d.dep + movement)
                else -> d.copy(bons = d.bons + movement)
            }
        }
    }

    fun deleteCashMovement(kind: String, index: Int) {
        if (!requirePermission(BizzlyPermissions.SUPPR) { deleteCashMovement(kind, index) }) return
        val data = _uiState.value.businessData
        val list = when (kind) {
            "vers" -> data.vers
            "dep" -> data.dep
            else -> data.bons
        }
        val item = list.getOrNull(index) ?: return
        val logMsg = "Supprimé : ${BizzlyCalculator.formatFcfa(item.m)}" +
            if (item.w.isNotBlank()) " · ${item.w}" else ""
        mutateData(logMsg) { d ->
            when (kind) {
                "vers" -> d.copy(vers = d.vers.filterIndexed { i, _ -> i != index })
                "dep" -> d.copy(dep = d.dep.filterIndexed { i, _ -> i != index })
                else -> d.copy(bons = d.bons.filterIndexed { i, _ -> i != index })
            }
        }
    }

    // --- Period Close ---

    fun closePeriod() {
        if (!requirePermission(BizzlyPermissions.CLOTURE) { closePeriod() }) return
        askConfirm(
            "Clôturer ? Le stock de fin devient le stock de début, et versements, dépenses et bons sont archivés."
        ) {
            val today = BizzlyCalculator.todayIso()
            val nowTs = System.currentTimeMillis()
            mutateData("Période clôturée") { d ->
                val summary = BizzlyCalculator.calculateSummary(d)
                val archive = ArchivePeriod(
                    d = today,
                    sv = d.sv,
                    paid = d.paid,
                    dep = d.dep,
                    vers = d.vers,
                    bons = d.bons,
                    u = syncClient.newUid(),
                    ts = nowTs
                )
                val closed = ClosedPeriod(
                    d = today,
                    att = summary.recordedRevenue,
                    ver = summary.remitted,
                    dep = summary.expenses,
                    bon = summary.vouchers,
                    rest = summary.stockGap,
                    u = syncClient.newUid(),
                    ts = nowTs
                )
                val rolledProducts = d.prod.map { p ->
                    val info = BizzlyCalculator.stockInfo(d, p)
                    val endCount = p.f ?: info.theoretical.coerceAtLeast(0.0)
                    p.copy(d = endCount, e = 0.0, f = null)
                }
                d.copy(
                    arch = d.arch + archive,
                    hist = listOf(closed) + d.hist,
                    prod = rolledProducts,
                    vers = emptyList(),
                    dep = emptyList(),
                    bons = emptyList(),
                    sv = emptyMap(),
                    paid = emptyList()
                )
            }
            _uiState.update { it.copy(currentTab = BizzlyTab.HIST, currentTable = null) }
            showToast("Période clôturée")
        }
    }

    // --- Enterprise, Profiles, Brand, Tables ---

    fun updateBusinessField(field: String, rawValue: String) {
        if (!requirePermission(BizzlyPermissions.RESET) { updateBusinessField(field, rawValue) }) return
        val value = rawValue.trim()
        if (field == "name" && value.length < 2) {
            showToast("Le nom est obligatoire.")
            return
        }
        mutateAccount("Entreprise : $field modifié") { acc ->
            when (field) {
                "name" -> acc.copy(name = value)
                "email" -> acc.copy(email = value)
                "tel" -> acc.copy(tel = value)
                "addr" -> acc.copy(addr = value)
                else -> acc
            }
        }
    }

    fun pickAccountLogo(uri: Uri) {
        if (!requirePermission(BizzlyPermissions.RESET) { pickAccountLogo(uri) }) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExportUtils.decodeAndResizeLogo(getApplication(), uri)
            }
            if (result == null) {
                showToast("Image illisible.")
                return@launch
            }
            _uiState.update { it.copy(enterpriseLogoProposals = result.proposals) }
            mutateAccount("Logo modifié") { acc ->
                acc.copy(
                    logo = result.dataUri,
                    lw = result.width,
                    lh = result.height,
                    brand = result.palette ?: acc.brand
                )
            }
            if (result.proposals.isNotEmpty()) {
                showToast("Thème adapté aux couleurs de votre logo.")
            }
        }
    }

    fun removeAccountLogo() {
        if (!requirePermission(BizzlyPermissions.RESET) { removeAccountLogo() }) return
        _uiState.update { it.copy(enterpriseLogoProposals = emptyList()) }
        mutateAccount { acc -> acc.copy(logo = "", lw = 0, lh = 0) }
    }

    fun updateBrandColor(channel: String, hex: String) {
        if (!requirePermission(BizzlyPermissions.RESET) { updateBrandColor(channel, hex) }) return
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(hex)) return
        mutateAccount { acc ->
            val b = acc.brand
            val next = when (channel) {
                "pr" -> b.copy(pr = hex)
                "sec" -> b.copy(sec = hex)
                else -> b.copy(ax = hex)
            }
            acc.copy(brand = next)
        }
    }

    fun applyEnterpriseBrand(brand: BrandColors) {
        if (!requirePermission(BizzlyPermissions.RESET) { applyEnterpriseBrand(brand) }) return
        mutateAccount("Thème de couleurs modifié") { it.copy(brand = brand) }
        showToast("Thème appliqué à toute l’application.")
    }

    fun refreshEnterpriseLogoProposals() {
        val logo = _uiState.value.activeAccount?.logo
        if (logo.isNullOrBlank()) {
            _uiState.update { it.copy(enterpriseLogoProposals = emptyList()) }
            return
        }
        viewModelScope.launch {
            val proposals = withContext(Dispatchers.Default) {
                val bmp = ExportUtils.decodeDataUriBitmap(logo) ?: return@withContext emptyList()
                val res = BizzlyCalculator.extractBrandThemeProposals(bmp)
                bmp.recycle()
                res
            }
            _uiState.update { it.copy(enterpriseLogoProposals = proposals) }
        }
    }

    fun autoPaletteFromLogo() {
        if (!requirePermission(BizzlyPermissions.RESET) { autoPaletteFromLogo() }) return
        val acc = _uiState.value.activeAccount
        if (acc?.logo.isNullOrBlank()) {
            showToast("Ajoutez d’abord un logo.")
            return
        }
        viewModelScope.launch {
            val proposals = withContext(Dispatchers.Default) {
                val bmp = ExportUtils.decodeDataUriBitmap(acc?.logo) ?: return@withContext emptyList()
                val res = BizzlyCalculator.extractBrandThemeProposals(bmp)
                bmp.recycle()
                res
            }
            val first = proposals.firstOrNull()?.brand
            _uiState.update { it.copy(enterpriseLogoProposals = proposals) }
            if (first != null) {
                mutateAccount { it.copy(brand = first) }
                showToast("Thème extrait et appliqué à toute l’application.")
            } else {
                showToast("Impossible d’extraire les couleurs du logo.")
            }
        }
    }

    fun resetBrandToBizzly() {
        if (!requirePermission(BizzlyPermissions.RESET) { resetBrandToBizzly() }) return
        mutateAccount { it.copy(brand = BrandColors()) }
    }

    fun updateTableCount(countRaw: String) {
        if (!requirePermission(BizzlyPermissions.RESET) { updateTableCount(countRaw) }) return
        val data = _uiState.value.businessData
        var maxOpen = 0
        for ((k, tbl) in data.tb) {
            if (tbl.it.isNotEmpty()) maxOpen = maxOf(maxOpen, k.toIntOrNull() ?: 0)
        }
        val target = (countRaw.toIntOrNull() ?: data.nt).coerceIn(maxOf(1, maxOpen), 200)
        mutateData("Nombre de tables : $target") { it.copy(nt = target) }
    }

    fun renameProfile(profileId: Long, newName: String) {
        if (!requirePermission(BizzlyPermissions.RESET) { renameProfile(profileId, newName) }) return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        mutateAccount { acc ->
            acc.copy(profiles = acc.profiles.map { if (it.id == profileId) it.copy(name = trimmed) else it })
        }
    }

    fun changeProfileRole(profileId: Long, newRole: String) {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { changeProfileRole(profileId, newRole) }) return
        val validRole = BizzlyRole.entries.firstOrNull { it.key == newRole }?.key ?: return
        val acc = _uiState.value.activeAccount ?: return
        val prof = acc.profiles.firstOrNull { it.id == profileId } ?: return
        val otherGerants = acc.profiles.count { it.role == BizzlyRole.GERANT.key && it.id != profileId }
        if (prof.role == BizzlyRole.GERANT.key && validRole != BizzlyRole.GERANT.key && otherGerants == 0) {
            showToast("Il faut garder au moins un gérant.")
            return
        }
        mutateAccount { a ->
            a.copy(profiles = a.profiles.map { if (it.id == profileId) it.copy(role = validRole) else it })
        }
    }

    fun promptSetProfilePin(profileId: Long) {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { promptSetProfilePin(profileId) }) return
        val acc = _uiState.value.activeAccount ?: return
        val prof = acc.profiles.firstOrNull { it.id == profileId } ?: return
        val s0 = "Choisissez un code à 4 chiffres pour ${prof.name}"
        _uiState.update {
            it.copy(
                pinOverlay = PinOverlayState(
                    mode = PinPromptMode.SetNewPin(
                        title = "Nouveau code PIN",
                        initialSubtitle = s0,
                        onPinConfirmed = { newPin -> applyNewProfilePin(profileId, newPin) }
                    ),
                    subtitle = s0
                )
            )
        }
    }

    private fun applyNewProfilePin(profileId: Long, pin: String) {
        if (PinSecurity.isWeakPin(pin)) {
            showToast("Code trop simple (ex. 1234, 0000). Choisissez-en un autre.")
            return
        }
        val acc = _uiState.value.activeAccount ?: return
        viewModelScope.launch {
            val taken = withContext(Dispatchers.Default) {
                PinSecurity.findProfileWithSamePin(acc, pin, profileId)
            }
            if (taken != null) {
                showToast("Ce code est déjà utilisé par un autre profil.")
                return@launch
            }
            val (salt, hash) = withContext(Dispatchers.Default) { PinSecurity.createPinHash(pin) }
            val profName = acc.profiles.firstOrNull { it.id == profileId }?.name ?: "Profil"
            mutateAccount("PIN défini : $profName") { a ->
                a.copy(
                    profiles = a.profiles.map {
                        if (it.id == profileId) it.copy(salt = salt, hash = hash, v = 2) else it
                    }
                )
            }
            showToast("PIN enregistré.")
        }
    }

    fun addProfile(nameRaw: String, roleKey: String) {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { addProfile(nameRaw, roleKey) }) return
        val name = nameRaw.trim()
        if (name.isEmpty()) return
        val validRole = BizzlyRole.entries.firstOrNull { it.key == roleKey }?.key ?: BizzlyRole.SERVEUR.key
        val newId = System.currentTimeMillis()
        mutateAccount("Profil ajouté : $name") { a ->
            a.copy(profiles = a.profiles + BusinessProfile(id = newId, role = validRole, name = name))
        }
    }

    fun deleteProfile(profileId: Long) {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { deleteProfile(profileId) }) return
        val st = _uiState.value
        val acc = st.activeAccount ?: return
        val prof = acc.profiles.firstOrNull { it.id == profileId } ?: return
        if (st.activeProfile?.id == profileId) {
            showToast("Vous ne pouvez pas supprimer votre propre profil.")
            return
        }
        val otherGerants = acc.profiles.count { it.role == BizzlyRole.GERANT.key && it.id != profileId }
        if (prof.role == BizzlyRole.GERANT.key && otherGerants == 0) {
            showToast("Il faut garder au moins un gérant.")
            return
        }
        askConfirm("Supprimer le profil « ${prof.name} » ?", isDestructive = true) {
            mutateAccount("Profil supprimé : ${prof.name}") { a ->
                a.copy(profiles = a.profiles.filterNot { it.id == profileId })
            }
        }
    }

    fun removeBusinessFromDevice() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { removeBusinessFromDevice() }) return
        val acc = _uiState.value.activeAccount ?: return
        askConfirm(
            "Retirer « ${acc.name} » de cet appareil ? Faites d’abord une sauvegarde : les données seront effacées ici.",
            isDestructive = true
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    repository.deleteBusiness(acc.id)
                    inventoryRepository.deleteAllForBusiness(acc.id)
                }
                switchBusiness()
                showToast("Entreprise retirée de cet appareil.")
            }
        }
    }

    // --- Backup, Restore, Reset, Exports ---

    fun resetBusinessData() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { resetBusinessData() }) return
        askConfirm("Tout effacer ? Toutes les données seront perdues.", isDestructive = true) {
            val acc = _uiState.value.activeAccount
            val fresh = BizzlyCalculator.syncServersWithAccount(acc, repository.freshBusinessData())
            _uiState.update { it.copy(businessData = fresh, currentTab = BizzlyTab.HOME, currentTable = null) }
            if (acc != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    persistMutex.withLock {
                        repository.saveBusinessData(acc.id, fresh)
                        inventoryRepository.deleteAllForBusiness(acc.id)
                    }
                }
                scheduleSync()
            }
            showToast("Données réinitialisées.")
        }
    }

    fun restoreBackupInSession(uri: Uri) {
        if (!requireSensitivePin(BizzlyPermissions.RESTORE) { restoreBackupInSession(uri) }) return
        val acc = _uiState.value.activeAccount ?: return
        viewModelScope.launch {
            var oversized = false
            val raw = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val txt = BizzlyRepository.readStreamBoundedUtf8(stream)
                    if (txt == null) oversized = true
                    txt
                }
            }
            if (oversized) {
                showToast("Fichier trop volumineux (max 10 Mo).")
                return@launch
            }
            if (raw == null) {
                showToast("Impossible de lire le fichier.")
                return@launch
            }
            val parsed = repository.parseBackupJson(raw)
            val restoredData = when (parsed) {
                is BizzlyRepository.ParsedBackup.FullV2 -> {
                    if (BizzlyCalculator.slugify(parsed.account.ident) != acc.ident) {
                        showToast("Cette sauvegarde appartient à une autre entreprise.")
                        return@launch
                    }
                    parsed.data
                }
                is BizzlyRepository.ParsedBackup.LegacyDataOnly -> parsed.data
                null -> {
                    showToast("Ce fichier n’est pas une sauvegarde valide.")
                    return@launch
                }
            }
            askConfirm("Remplacer les données actuelles par cette sauvegarde ?", isDestructive = true) {
                val synced = BizzlyCalculator.syncServersWithAccount(acc, restoredData)
                val logged = appendLog(synced, "Sauvegarde restaurée")
                _uiState.update { it.copy(businessData = logged, currentTab = BizzlyTab.HOME, currentTable = null) }
                viewModelScope.launch(Dispatchers.IO) {
                    persistMutex.withLock {
                        repository.saveBusinessData(acc.id, logged)
                    }
                }
                scheduleSync()
                showToast("Sauvegarde restaurée")
            }
        }
    }

    fun buildBackupJsonAndMarkSaved(): Pair<String, String>? {
        val acc = _uiState.value.activeAccount ?: return null
        val today = BizzlyCalculator.todayIso()
        val updatedData = _uiState.value.businessData.copy(lbk = today)
        _uiState.update { it.copy(businessData = updatedData) }
        viewModelScope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                repository.saveBusinessData(acc.id, updatedData)
            }
        }
        val filename = "${BizzlyCalculator.slugify(acc.ident)}-sauvegarde-$today.json"
        val jsonStr = repository.createBackupJson(acc, updatedData)
        return filename to jsonStr
    }

    fun dismissReadyExport() {
        _uiState.update { it.copy(readyExport = null) }
    }

    private suspend fun publishExport(
        filename: String,
        bytes: ByteArray,
        mimeType: String,
        chooserTitle: String
    ) {
        val prepared = withContext(Dispatchers.IO) {
            ExportUtils.prepareExport(getApplication(), filename, bytes, mimeType)
        }
        _uiState.update {
            it.copy(
                exportBusy = false,
                readyExport = ReadyExport(
                    filename = prepared.filename,
                    mimeType = prepared.mimeType,
                    chooserTitle = chooserTitle,
                    bytes = prepared.bytes,
                    downloadsUri = prepared.downloadsUri,
                    cacheUri = prepared.cacheUri,
                    savedToDownloads = prepared.downloadsUri != null
                )
            )
        }
        if (prepared.downloadsUri != null) {
            showToast("Téléchargé dans Téléchargements/Bizzly : ${prepared.filename}")
        }
    }

    fun shareTableInvoicePdf(tableNumber: Int, isProforma: Boolean = false) {
        val perm = if (isProforma) BizzlyPermissions.COMMANDE else BizzlyPermissions.ENCAISSER
        if (!requirePermission(perm) { shareTableInvoicePdf(tableNumber, isProforma) }) return
        val st = _uiState.value
        val acc = st.activeAccount
        val data = st.businessData
        val tbl = data.tb[tableNumber.toString()] ?: OpenTable()
        val breakdown = BizzlyCalculator.calculateTableBreakdown(data, tbl)
        val due = breakdown.remainingDue
        val payments = if (st.paymentMode == "Mixte") {
            val amt1 = st.mixedAmount1Text.toDoubleOrNull()?.coerceIn(0.0, due) ?: 0.0
            if (amt1 > 0.0 && amt1 < due) {
                listOf(PaymentPart(st.mixedMode1, amt1), PaymentPart(st.mixedMode2, due - amt1))
            } else emptyList()
        } else emptyList()
        val cashGiven = if (st.paymentMode == "Espèces" || (st.paymentMode == "Mixte" && (st.mixedMode1 == "Espèces" || st.mixedMode2 == "Espèces"))) {
            st.cashGivenText.toDoubleOrNull() ?: 0.0
        } else 0.0

        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ExportUtils.generateInvoicePdf(
                        account = acc,
                        data = data,
                        tableNumber = tableNumber,
                        paymentMode = st.paymentMode,
                        cashGiven = cashGiven,
                        payments = payments,
                        isProforma = isProforma
                    )
                }
                val prefix = if (isProforma) "note" else "facture"
                val filename = "$prefix-${BizzlyCalculator.slugify(acc?.name)}-table-$tableNumber-${BizzlyCalculator.todayIso()}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = if (isProforma) "Note de table $tableNumber" else "Facture Table $tableNumber"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le PDF.")
            }
        }
    }

    // --- Bluetooth Thermal Printing (ESC/POS 58mm / 80mm) ---

    fun refreshPairedPrinters() {
        if (!_uiState.value.isLoggedIn) return
        val list = EscPosPrinter.listPairedPrinters(getApplication())
        _uiState.update { it.copy(pairedPrinters = list) }
    }

    fun selectPrinter(device: BluetoothPrinterDevice) {
        if (!_uiState.value.isLoggedIn || !can(BizzlyPermissions.COMMANDE)) return
        repository.setPrinterAddress(device.address)
        repository.setPrinterName(device.name)
        _uiState.update { it.copy(printerAddress = device.address, printerName = device.name) }
        showToast("Imprimante sélectionnée : ${device.name}")
    }

    fun clearSelectedPrinter() {
        if (!_uiState.value.isLoggedIn || !can(BizzlyPermissions.COMMANDE)) return
        repository.setPrinterAddress(null)
        repository.setPrinterName(null)
        _uiState.update { it.copy(printerAddress = null, printerName = null) }
        showToast("Imprimante retirée.")
    }

    fun setPrinterWidthMm(mm: Int) {
        if (!_uiState.value.isLoggedIn || !can(BizzlyPermissions.COMMANDE)) return
        val clean = if (mm >= 80) 80 else 58
        repository.setPrinterWidthMm(clean)
        _uiState.update { it.copy(printerWidthMm = clean) }
    }

    fun printTestReceipt() {
        if (!_uiState.value.isLoggedIn || !can(BizzlyPermissions.COMMANDE)) return
        val st = _uiState.value
        val addr = st.printerAddress
        if (addr.isNullOrBlank()) {
            showToast("Sélectionnez d’abord une imprimante Bluetooth.")
            return
        }
        val width = PrinterPaperWidth.fromMm(st.printerWidthMm)
        val payload = EscPosPrinter.buildTestReceipt(st.activeAccount, width)
        sendToPrinter(addr, payload, "Test imprimé (${width.mm} mm).")
    }

    fun printTableReceipt(tableNumber: Int, isProforma: Boolean) {
        val perm = if (isProforma) BizzlyPermissions.COMMANDE else BizzlyPermissions.ENCAISSER
        if (!requirePermission(perm) { printTableReceipt(tableNumber, isProforma) }) return
        val st = _uiState.value
        val addr = st.printerAddress
        if (addr.isNullOrBlank()) {
            showToast("Configurez une imprimante Bluetooth (Entreprise ou bouton Imprimer).")
            return
        }
        val tbl = st.businessData.tb[tableNumber.toString()] ?: return
        val breakdown = BizzlyCalculator.calculateTableBreakdown(st.businessData, tbl)
        val due = breakdown.remainingDue
        val payments = if (st.paymentMode == "Mixte") {
            val amt1 = st.mixedAmount1Text.toDoubleOrNull()?.coerceIn(0.0, due) ?: 0.0
            if (amt1 > 0.0 && amt1 < due) {
                listOf(PaymentPart(st.mixedMode1, amt1), PaymentPart(st.mixedMode2, due - amt1))
            } else emptyList()
        } else emptyList()
        val cashGiven = if (!isProforma && (st.paymentMode == "Espèces" || st.paymentMode == "Mixte")) {
            st.cashGivenText.toDoubleOrNull() ?: 0.0
        } else 0.0

        val width = PrinterPaperWidth.fromMm(st.printerWidthMm)
        val payload = EscPosPrinter.buildTableReceipt(
            account = st.activeAccount,
            data = st.businessData,
            tableNumber = tableNumber,
            table = tbl,
            paymentLabel = st.paymentMode,
            payments = payments,
            cashGiven = cashGiven,
            isProforma = isProforma,
            width = width
        )
        sendToPrinter(
            addr,
            payload,
            if (isProforma) "Note de table $tableNumber imprimée." else "Ticket table $tableNumber imprimé."
        )
    }

    fun printKitchenBarTicket(tableNumber: Int) {
        if (!requirePermission(BizzlyPermissions.COMMANDE) { printKitchenBarTicket(tableNumber) }) return
        val st = _uiState.value
        val addr = st.printerAddress
        if (addr.isNullOrBlank()) {
            showToast("Configurez une imprimante Bluetooth (Entreprise ou bouton Imprimer).")
            return
        }
        val tbl = st.businessData.tb[tableNumber.toString()] ?: return
        val width = PrinterPaperWidth.fromMm(st.printerWidthMm)
        val payload = EscPosPrinter.buildKitchenBarTicket(
            account = st.activeAccount,
            data = st.businessData,
            tableNumber = tableNumber,
            table = tbl,
            width = width
        )
        sendToPrinter(addr, payload, "Bon cuisine/bar Table $tableNumber imprimé.")
    }

    private fun sendToPrinter(address: String, payload: ByteArray, successMessage: String) {
        _uiState.update { it.copy(printerBusy = true) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                EscPosPrinter.printBytes(getApplication(), address, payload)
            }
            _uiState.update { it.copy(printerBusy = false) }
            res.onSuccess {
                showToast(successMessage)
            }.onFailure { err ->
                showToast(err.message ?: "Échec d’impression Bluetooth.")
            }
        }
    }

    fun exportQrMenuPdf() {
        if (!requirePermission(BizzlyPermissions.QR) { exportQrMenuPdf() }) return
        val st = _uiState.value
        val acc = st.activeAccount
        val data = st.businessData
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { ExportUtils.generateQrMenuPdf(acc, data) }
                if (bytes == null) {
                    _uiState.update { it.copy(exportBusy = false) }
                    showToast("Menu trop long pour un QR code.")
                    return@launch
                }
                val filename = "menu-${BizzlyCalculator.slugify(acc?.name)}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = "Menu QR ${acc?.name.orEmpty()}"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le menu PDF.")
            }
        }
    }

    fun exportBilanPdf() {
        if (!requirePermission(BizzlyPermissions.EXPORT) { exportBilanPdf() }) return
        val st = _uiState.value
        val acc = st.activeAccount
        val data = st.businessData
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { ExportUtils.generateBilanPdf(acc, data) }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-bilan-${BizzlyCalculator.todayIso()}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = "Bilan ${acc?.name.orEmpty()}"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le bilan PDF.")
            }
        }
    }

    fun exportExcelFile() {
        if (!requirePermission(BizzlyPermissions.EXPORT) { exportExcelFile() }) return
        val st = _uiState.value
        val acc = st.activeAccount
        val data = st.businessData
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { ExportUtils.generateExcelBytes(data) }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-donnees-${BizzlyCalculator.todayIso()}.xlsx"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    chooserTitle = "Exporter en Excel"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer le fichier Excel.")
            }
        }
    }

    fun exportBackupFile() {
        val pair = buildBackupJsonAndMarkSaved() ?: return
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                publishExport(
                    filename = pair.first,
                    bytes = pair.second.toByteArray(Charsets.UTF_8),
                    mimeType = "application/json",
                    chooserTitle = "Envoyer la sauvegarde"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de préparer la sauvegarde.")
            }
        }
    }

    // --- Cloud Sync Actions (Supabase) ---

    fun enableCloudSync() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { enableCloudSync() }) return
        val acc = _uiState.value.activeAccount ?: return
        _uiState.update { it.copy(syncMessage = "Connexion en cours…", syncBusy = true) }
        viewModelScope.launch {
            try {
                val key = syncClient.createSyncSpace(acc.name)
                val initialCreds = SyncCredentials(key = key, seq = 0L, snap = emptyMap())
                withContext(Dispatchers.IO) { repository.saveSyncCredentials(acc.id, initialCreds) }
                syncDirty = true
                _uiState.update {
                    it.copy(
                        syncCreds = initialCreds,
                        syncDead = false,
                        syncMessage = "",
                        syncBusy = false
                    )
                }
                runSyncCycle()
                _uiState.update { it.copy(syncCodeDialogVisible = true) }
            } catch (e: SyncException) {
                withContext(Dispatchers.IO) { repository.clearSyncCredentials(acc.id) }
                _uiState.update {
                    it.copy(
                        syncCreds = null,
                        syncBusy = false,
                        syncMessage = if (e.isOffline) {
                            "Pas de connexion à Internet."
                        } else {
                            "Échec : ${e.message}"
                        }
                    )
                }
            }
        }
    }

    fun showSyncCodeDialog() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { showSyncCodeDialog() }) return
        _uiState.update { it.copy(syncCodeDialogVisible = true) }
    }

    fun dismissSyncCodeDialog() {
        _uiState.update { it.copy(syncCodeDialogVisible = false) }
    }

    fun formattedSyncKey(): String? =
        _uiState.value.syncCreds?.key?.let { syncClient.formatSyncKey(it) }

    fun rotateCloudSyncKey() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { rotateCloudSyncKey() }) return
        val acc = _uiState.value.activeAccount ?: return
        val cur = _uiState.value.syncCreds ?: return
        viewModelScope.launch {
            try {
                val newKey = syncClient.rotateSyncKey(cur.key)
                val updated = cur.copy(key = newKey)
                withContext(Dispatchers.IO) { repository.saveSyncCredentials(acc.id, updated) }
                _uiState.update {
                    it.copy(
                        syncCreds = updated,
                        syncDead = false,
                        syncCodeDialogVisible = true,
                        syncMessage = ""
                    )
                }
                showToast("Nouveau code actif. Les autres téléphones doivent se relier.")
            } catch (e: Exception) {
                _uiState.update { it.copy(syncMessage = "Échec : ${e.message}") }
            }
        }
    }

    fun disableCloudSync() {
        if (!requireSensitivePin(BizzlyPermissions.RESET) { disableCloudSync() }) return
        val acc = _uiState.value.activeAccount ?: return
        askConfirm(
            "Désactiver la synchronisation sur ce téléphone ? Les données restent ici et en ligne. Vous pourrez la réactiver avec le code.",
            isDestructive = true
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repository.clearSyncCredentials(acc.id) }
                _uiState.update {
                    it.copy(
                        syncCreds = null,
                        syncStatus = SyncStatus.OFF,
                        syncDead = false,
                        syncError = "",
                        syncMessage = ""
                    )
                }
            }
        }
    }

    fun openCloudSnapshots() {
        val cur = _uiState.value.syncCreds ?: return
        viewModelScope.launch {
            try {
                val list = syncClient.fetchSnapshots(cur.key)
                _uiState.update { it.copy(snapshotsDialog = list, syncMessage = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncMessage = "Échec : ${e.message}") }
            }
        }
    }

    fun dismissSnapshotsDialog() {
        _uiState.update { it.copy(snapshotsDialog = null) }
    }

    fun restoreCloudSnapshot(day: String) {
        if (!requireSensitivePin(BizzlyPermissions.RESTORE) { restoreCloudSnapshot(day) }) return
        val cur = _uiState.value.syncCreds ?: return
        val acc = _uiState.value.activeAccount ?: return
        val currentSrv = _uiState.value.businessData.srv
        viewModelScope.launch {
            try {
                val restored = syncClient.fetchSnapshotData(cur.key, day, currentSrv)
                val synced = BizzlyCalculator.syncServersWithAccount(acc, restored)
                askConfirm(
                    "Remplacer toutes les données par la copie du ${BizzlyCalculator.formatDateFr(day)} ?",
                    isDestructive = true
                ) {
                    val logged = appendLog(synced, "Restauration de la copie du $day")
                    _uiState.update {
                        it.copy(
                            snapshotsDialog = null,
                            businessData = logged,
                            currentTab = BizzlyTab.HOME,
                            currentTable = null
                        )
                    }
                    syncDirty = true
                    viewModelScope.launch(Dispatchers.IO) { repository.saveBusinessData(acc.id, logged) }
                    scheduleSync()
                    showToast("Copie restaurée.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncMessage = "Échec : ${e.message}") }
            }
        }
    }

    fun openRekeyDialog() {
        _uiState.update { it.copy(rekeyDialogVisible = true, rekeyCodeInput = "", rekeyError = "") }
    }

    fun dismissRekeyDialog() {
        _uiState.update { it.copy(rekeyDialogVisible = false) }
    }

    fun updateRekeyInput(value: String) {
        _uiState.update { it.copy(rekeyCodeInput = value, rekeyError = "") }
    }

    fun submitRekey() {
        val st = _uiState.value
        val acc = st.activeAccount ?: return
        val cur = st.syncCreds ?: return
        val code = syncClient.normalizeSyncKey(st.rekeyCodeInput)
        if (code.length != 20) {
            _uiState.update { it.copy(rekeyError = "Le code doit avoir 20 caractères.") }
            return
        }
        _uiState.update { it.copy(rekeyBusy = true, rekeyError = "") }
        viewModelScope.launch {
            try {
                val valid = syncClient.verifyRekeyCode(code, acc.name)
                if (!valid) {
                    _uiState.update {
                        it.copy(rekeyBusy = false, rekeyError = "Ce code n’appartient pas à cette entreprise.")
                    }
                    return@launch
                }
                val updated = cur.copy(key = code, seq = 0L)
                withContext(Dispatchers.IO) { repository.saveSyncCredentials(acc.id, updated) }
                seenDocVersions.clear()
                syncDirty = true
                _uiState.update {
                    it.copy(
                        syncCreds = updated,
                        syncDead = false,
                        rekeyBusy = false,
                        rekeyDialogVisible = false
                    )
                }
                showToast("Téléphone relié.")
                runSyncCycle()
            } catch (e: SyncException) {
                val msg = when {
                    e.isInvalidKey -> "Code incorrect."
                    e.isOffline -> "Pas de connexion à Internet."
                    else -> e.message ?: "Erreur"
                }
                _uiState.update { it.copy(rekeyBusy = false, rekeyError = msg) }
            }
        }
    }

    // --- KLTUR COORPORATION Admin Console & Netlify Portal ---

    fun openAdminPanel() {
        syncDebounceJob?.cancel()
        val hasCred = repository.hasAdminCredential()
        val netlifyUrl = repository.getNetlifyAdminUrl()
        _uiState.update {
            it.copy(
                activeAccount = null,
                activeProfile = null,
                businessData = BusinessData(),
                currentTable = null,
                pinOverlay = null,
                confirmDialog = null,
                syncCreds = null,
                syncStatus = SyncStatus.OFF,
                authMode = AuthMode.ADMIN_KLTUR,
                admin = it.admin.copy(
                    needsSetup = !hasCred,
                    error = "",
                    netlifyUrl = netlifyUrl
                )
            )
        }
        if (_uiState.value.admin.isAuthenticated) {
            refreshAdminDashboard()
        }
    }

    fun closeAdminPanel() {
        _uiState.update { st ->
            val hasAccounts = st.accounts.isNotEmpty()
            st.copy(
                authMode = if (hasAccounts) AuthMode.PICK_BUSINESS else AuthMode.SIGNUP,
                admin = st.admin.copy(error = "", snapshotsForBusiness = null)
            )
        }
    }

    fun setAdminAuthTab(tab: Int) {
        _uiState.update { it.copy(admin = it.admin.copy(authTab = tab, error = "")) }
    }

    fun setAdminDashboardTab(tab: Int) {
        _uiState.update { it.copy(admin = it.admin.copy(activeAdminTab = tab, error = "")) }
    }

    fun selectAdminBusiness(businessId: String?) {
        _uiState.update { it.copy(admin = it.admin.copy(selectedBusinessId = businessId)) }
    }

    fun setupAdminPasscode(installCode: String, code: String, confirm: String) {
        val installLockKey = "admin:install"
        val waitMsg = lockout.checkWaitMessage(installLockKey)
        if (waitMsg != null) {
            _uiState.update { it.copy(admin = it.admin.copy(error = waitMsg)) }
            return
        }
        val clean = code.trim()
        if (clean.length < 4 || clean.length > 32) {
            _uiState.update { it.copy(admin = it.admin.copy(error = "La clé d’administrateur doit contenir 4 à 32 caractères.")) }
            return
        }
        if (clean.all { it == clean[0] } || (PinSecurity.isFourDigits(clean) && PinSecurity.isWeakPin(clean))) {
            _uiState.update { it.copy(admin = it.admin.copy(error = "Clé trop simple. Choisissez une clé plus sûre.")) }
            return
        }
        if (clean != confirm.trim()) {
            _uiState.update { it.copy(admin = it.admin.copy(error = "Les deux saisies de clé ne correspondent pas.")) }
            return
        }
        _uiState.update { it.copy(admin = it.admin.copy(busy = true, error = "")) }
        viewModelScope.launch {
            val installOk = withContext(Dispatchers.Default) {
                PinSecurity.verifyAdminInstallCode(installCode)
            }
            if (!installOk) {
                val lockMsg = lockout.recordFailure(installLockKey)
                _uiState.update {
                    it.copy(
                        admin = it.admin.copy(
                            busy = false,
                            error = lockMsg ?: "Code d'installation incorrect."
                        )
                    )
                }
                return@launch
            }
            lockout.reset(installLockKey)
            val (salt, hash) = withContext(Dispatchers.Default) { PinSecurity.createCredentialHash(clean) }
            repository.saveAdminCredential(salt, hash)
            _uiState.update {
                it.copy(
                    admin = it.admin.copy(
                        isAuthenticated = true,
                        needsSetup = false,
                        busy = false,
                        error = ""
                    )
                )
            }
            refreshAdminDashboard()
            showToast("Accès Admin KLTUR configuré.")
        }
    }

    fun loginAdminPasscode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            _uiState.update { it.copy(admin = it.admin.copy(error = "Saisissez le code administrateur.")) }
            return
        }
        val lockKey = "admin:kltur"
        val waitMsg = lockout.checkWaitMessage(lockKey)
        if (waitMsg != null) {
            _uiState.update { it.copy(admin = it.admin.copy(error = waitMsg)) }
            return
        }
        _uiState.update { it.copy(admin = it.admin.copy(busy = true, error = "")) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { repository.verifyAdminCredential(clean) }
            if (ok) {
                lockout.reset(lockKey)
                _uiState.update {
                    it.copy(
                        admin = it.admin.copy(
                            isAuthenticated = true,
                            busy = false,
                            error = ""
                        )
                    )
                }
                refreshAdminDashboard()
            } else {
                val lockMsg = lockout.recordFailure(lockKey)
                _uiState.update {
                    it.copy(
                        admin = it.admin.copy(
                            busy = false,
                            error = lockMsg ?: "Code administrateur incorrect."
                        )
                    )
                }
            }
        }
    }

    fun loginAdminSupabase(email: String, password: String) {
        val cleanEmail = email.trim()
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(cleanEmail) || password.isEmpty()) {
            _uiState.update {
                it.copy(admin = it.admin.copy(error = "Indiquez un e-mail et un mot de passe Supabase valides."))
            }
            return
        }
        val lockKey = "admin:supabase"
        val waitMsg = lockout.checkWaitMessage(lockKey)
        if (waitMsg != null) {
            _uiState.update { it.copy(admin = it.admin.copy(error = waitMsg)) }
            return
        }
        _uiState.update { it.copy(admin = it.admin.copy(busy = true, error = "")) }
        viewModelScope.launch {
            runCatching {
                syncClient.signInSupabaseAdmin(cleanEmail, password)
            }.onSuccess { session ->
                lockout.reset(lockKey)
                supabaseAdminToken = session.accessToken
                val remoteSpaces = runCatching {
                    syncClient.fetchRemoteSpacesWithAdminToken(session.accessToken)
                }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        admin = it.admin.copy(
                            isAuthenticated = true,
                            busy = false,
                            error = "",
                            supabaseEmail = session.email,
                            remoteSpaces = remoteSpaces
                        )
                    )
                }
                refreshAdminDashboard()
                showToast("Connecté à Supabase (${session.email}).")
            }.onFailure { err ->
                val lockMsg = if (err is SyncException && err.isOffline) null else lockout.recordFailure(lockKey)
                _uiState.update {
                    it.copy(
                        admin = it.admin.copy(
                            busy = false,
                            error = lockMsg ?: (err.message ?: "Échec de connexion Supabase.")
                        )
                    )
                }
            }
        }
    }

    fun logoutAdmin() {
        supabaseAdminToken = null
        _uiState.update {
            it.copy(
                admin = it.admin.copy(
                    isAuthenticated = false,
                    supabaseEmail = null,
                    remoteSpaces = emptyList(),
                    selectedBusinessId = null,
                    snapshotsForBusiness = null,
                    error = ""
                )
            )
        }
    }

    fun updateAdminPasscode(currentCode: String, newCode: String, confirm: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        val hasExisting = repository.hasAdminCredential()
        val lockKey = "admin:kltur"
        if (hasExisting) {
            val waitMsg = lockout.checkWaitMessage(lockKey)
            if (waitMsg != null) {
                showToast(waitMsg)
                return
            }
        }
        val cleanNew = newCode.trim()
        if (cleanNew.length < 4 || cleanNew.length > 32) {
            showToast("Le nouveau code doit contenir 4 à 32 caractères.")
            return
        }
        if (cleanNew.all { it == cleanNew[0] } || (PinSecurity.isFourDigits(cleanNew) && PinSecurity.isWeakPin(cleanNew))) {
            showToast("Code trop simple.")
            return
        }
        if (cleanNew != confirm.trim()) {
            showToast("Les deux nouveaux codes diffèrent.")
            return
        }
        viewModelScope.launch {
            if (hasExisting) {
                val ok = withContext(Dispatchers.Default) { repository.verifyAdminCredential(currentCode.trim()) }
                if (!ok) {
                    val lockMsg = lockout.recordFailure(lockKey)
                    showToast(lockMsg ?: "Code actuel incorrect.")
                    return@launch
                }
                lockout.reset(lockKey)
            }
            val (salt, hash) = withContext(Dispatchers.Default) { PinSecurity.createCredentialHash(cleanNew) }
            repository.saveAdminCredential(salt, hash)
            _uiState.update { it.copy(admin = it.admin.copy(needsSetup = false)) }
            showToast("Code Admin KLTUR mis à jour.")
        }
    }

    fun saveNetlifyAdminUrl(rawUrl: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        val clean = rawUrl.trim()
        if (clean.isNotEmpty() && !repository.isValidHttpsUrl(clean)) {
            showToast("Utilisez une adresse HTTPS valide (ex. https://votre-admin.netlify.app).")
            return
        }
        repository.setNetlifyAdminUrl(clean)
        _uiState.update { it.copy(admin = it.admin.copy(netlifyUrl = clean)) }
        showToast(if (clean.isEmpty()) "URL Netlify effacée." else "Page Netlify enregistrée.")
    }

    fun refreshAdminDashboard() {
        viewModelScope.launch {
            val summaries = withContext(Dispatchers.IO) {
                val accs = repository.getAllAccounts()
                val syncMap = repository.getAllSyncStatesMap()
                accs.map { acc ->
                    val data = repository.getBusinessData(acc.id)
                    val summary = BizzlyCalculator.calculateSummary(data)
                    val openTables = data.tb.count { it.value.it.isNotEmpty() }
                    AdminBusinessSummary(
                        account = acc,
                        productCount = data.prod.size,
                        openTableCount = openTables,
                        tablesCount = data.nt,
                        recordedRevenue = summary.recordedRevenue,
                        syncCreds = syncMap[acc.id]
                    )
                }
            }
            val token = supabaseAdminToken
            val remote = if (!token.isNullOrBlank()) {
                runCatching { syncClient.fetchRemoteSpacesWithAdminToken(token) }.getOrDefault(_uiState.value.admin.remoteSpaces)
            } else {
                _uiState.value.admin.remoteSpaces
            }
            _uiState.update { st ->
                val validSel = st.admin.selectedBusinessId?.takeIf { id -> summaries.any { it.account.id == id } }
                st.copy(
                    admin = st.admin.copy(
                        businesses = summaries,
                        remoteSpaces = remote,
                        selectedBusinessId = validSel
                    )
                )
            }
        }
    }

    fun adminOpenBusinessAsManager(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        viewModelScope.launch {
            val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return@launch
            val prof = acc.profiles.firstOrNull { it.role == BizzlyRole.GERANT.key }
                ?: acc.profiles.firstOrNull()
                ?: return@launch
            enterSession(acc, prof)
            showToast("Session Admin ouverte : ${acc.name}")
        }
    }

    fun adminUpdateBusinessInfo(
        businessId: String,
        name: String,
        email: String,
        tel: String,
        addr: String,
        tablesCount: Int
    ) {
        if (!_uiState.value.admin.isAuthenticated) return
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            showToast("Le nom de l’entreprise doit contenir au moins 2 caractères.")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val acc = repository.getAccount(businessId) ?: return@withContext
                val updatedAcc = acc.copy(
                    name = cleanName,
                    email = email.trim(),
                    tel = tel.trim(),
                    addr = addr.trim()
                )
                repository.saveAccount(updatedAcc)
                val data = repository.getBusinessData(businessId)
                val safeTables = tablesCount.coerceIn(1, 200)
                if (safeTables != data.nt) {
                    repository.saveBusinessData(businessId, data.copy(nt = safeTables))
                }
            }
            refreshAdminDashboard()
            showToast("Entreprise mise à jour.")
        }
    }

    fun adminResetProfilePin(businessId: String, profileId: Long, newPin: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        if (!PinSecurity.isFourDigits(newPin) || PinSecurity.isWeakPin(newPin)) {
            showToast("Le PIN doit comporter 4 chiffres non triviaux.")
            return
        }
        viewModelScope.launch {
            val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return@launch
            val taken = withContext(Dispatchers.Default) {
                PinSecurity.findProfileWithSamePin(acc, newPin, profileId)
            }
            if (taken != null) {
                showToast("Ce PIN est déjà utilisé par un autre profil.")
                return@launch
            }
            val (salt, hash) = withContext(Dispatchers.Default) { PinSecurity.createPinHash(newPin) }
            val updated = acc.copy(
                profiles = acc.profiles.map {
                    if (it.id == profileId) it.copy(salt = salt, hash = hash, v = 2) else it
                }
            )
            withContext(Dispatchers.IO) { repository.saveAccount(updated) }
            refreshAdminDashboard()
            showToast("PIN réinitialisé.")
        }
    }

    fun adminLinkRemoteBusiness(codeRaw: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        val code = syncClient.normalizeSyncKey(codeRaw)
        if (code.length != 20) {
            showToast("Le code de synchronisation doit contenir 20 caractères.")
            return
        }
        _uiState.update { it.copy(admin = it.admin.copy(busy = true, error = "")) }
        viewModelScope.launch {
            val existingKeys = withContext(Dispatchers.IO) { repository.getAllSyncKeys() }
            if (code in existingKeys) {
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                showToast("Cette entreprise est déjà liée sur cet appareil.")
                return@launch
            }
            runCatching {
                val idents = _uiState.value.accounts.map { it.ident }.toSet()
                syncClient.linkNewPhone(code, idents)
            }.onSuccess { res ->
                withContext(Dispatchers.IO) {
                    repository.saveAccount(res.account)
                    repository.saveBusinessData(res.account.id, res.data)
                    repository.saveSyncCredentials(res.account.id, res.credentials)
                }
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                refreshAdminDashboard()
                showToast("Entreprise « ${res.account.name} » importée depuis Supabase.")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(admin = it.admin.copy(busy = false, error = err.message ?: "Import impossible."))
                }
                showToast(err.message ?: "Import impossible.")
            }
        }
    }

    fun adminEnableSyncForBusiness(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(admin = it.admin.copy(busy = true)) }
        viewModelScope.launch {
            try {
                val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return@launch
                val data = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
                val key = syncClient.createSyncSpace(acc.name)
                val creds = SyncCredentials(key = key, seq = 0L, snap = emptyMap())
                val (pushed, prepped) = syncClient.pushChanges(creds, acc, data)
                withContext(Dispatchers.IO) {
                    repository.saveBusinessData(businessId, prepped)
                    repository.saveSyncCredentials(businessId, pushed)
                }
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                refreshAdminDashboard()
                showToast("Synchro activée (${syncClient.formatSyncKey(key)}).")
            } catch (e: Exception) {
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                showToast("Échec synchro : ${e.message}")
            }
        }
    }

    fun adminSyncBusinessNow(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(admin = it.admin.copy(busy = true)) }
        viewModelScope.launch {
            try {
                syncOneBusinessInBackground(businessId)
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                refreshAdminDashboard()
                showToast("Synchronisation réussie.")
            } catch (e: Exception) {
                _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
                showToast("Échec synchro : ${e.message}")
            }
        }
    }

    fun adminSyncAllBusinesses() {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(admin = it.admin.copy(busy = true)) }
        viewModelScope.launch {
            var okCount = 0
            val ids = _uiState.value.admin.businesses.filter { it.syncCreds != null }.map { it.account.id }
            for (id in ids) {
                if (runCatching { syncOneBusinessInBackground(id) }.isSuccess) okCount++
            }
            _uiState.update { it.copy(admin = it.admin.copy(busy = false)) }
            refreshAdminDashboard()
            showToast("$okCount / ${ids.size} entreprise(s) synchronisée(s).")
        }
    }

    private suspend fun syncOneBusinessInBackground(businessId: String) {
        val creds = withContext(Dispatchers.IO) { repository.getSyncCredentials(businessId) } ?: return
        val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return
        val data = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
        val (pushed, prepped) = syncClient.pushChanges(creds, acc, data)
        val seen = mutableMapOf<String, Long>()
        val pulled = syncClient.pullChanges(pushed, acc, prepped, seen, null, null)
        withContext(Dispatchers.IO) {
            repository.saveSyncCredentials(businessId, pulled.credentials)
            repository.saveAccount(pulled.account)
            if (pulled.changedCount > 0 || prepped != data) {
                repository.saveBusinessData(businessId, pulled.data)
            }
        }
    }

    fun adminFetchSnapshots(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { repository.getSyncCredentials(businessId) } ?: return@launch
            runCatching { syncClient.fetchSnapshots(creds.key) }
                .onSuccess { list ->
                    _uiState.update { it.copy(admin = it.admin.copy(snapshotsForBusiness = businessId to list)) }
                }
                .onFailure { err -> showToast("Échec : ${err.message}") }
        }
    }

    fun adminDismissSnapshots() {
        _uiState.update { it.copy(admin = it.admin.copy(snapshotsForBusiness = null)) }
    }

    fun adminRestoreSnapshot(businessId: String, day: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { repository.getSyncCredentials(businessId) } ?: return@launch
            val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return@launch
            val curData = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
            runCatching {
                val restored = syncClient.fetchSnapshotData(creds.key, day, curData.srv)
                BizzlyCalculator.syncServersWithAccount(acc, restored)
            }.onSuccess { synced ->
                val logged = appendLog(synced, "Admin KLTUR : copie restaurée ($day)")
                withContext(Dispatchers.IO) { repository.saveBusinessData(businessId, logged) }
                _uiState.update { it.copy(admin = it.admin.copy(snapshotsForBusiness = null)) }
                refreshAdminDashboard()
                showToast("Copie du ${BizzlyCalculator.formatDateFr(day)} restaurée.")
            }.onFailure { err ->
                showToast("Échec : ${err.message}")
            }
        }
    }

    fun adminExportExcel(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) }
                val data = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
                val bytes = withContext(Dispatchers.IO) { ExportUtils.generateExcelBytes(data) }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-donnees-${BizzlyCalculator.todayIso()}.xlsx"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    chooserTitle = "Exporter Excel (${acc?.name.orEmpty()})"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Export Excel impossible.")
            }
        }
    }

    fun adminExportBilanPdf(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) }
                val data = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
                val bytes = withContext(Dispatchers.IO) { ExportUtils.generateBilanPdf(acc, data) }
                val filename = "${BizzlyCalculator.slugify(acc?.name)}-bilan-${BizzlyCalculator.todayIso()}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = "Bilan PDF (${acc?.name.orEmpty()})"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Bilan PDF impossible.")
            }
        }
    }

    fun adminExportBackup(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val acc = withContext(Dispatchers.IO) { repository.getAccount(businessId) } ?: return@launch
                val data = withContext(Dispatchers.IO) { repository.getBusinessData(businessId) }
                val jsonStr = repository.createBackupJson(acc, data)
                val filename = "${BizzlyCalculator.slugify(acc.ident)}-sauvegarde-${BizzlyCalculator.todayIso()}.json"
                publishExport(
                    filename = filename,
                    bytes = jsonStr.toByteArray(Charsets.UTF_8),
                    mimeType = "application/json",
                    chooserTitle = "Sauvegarde (${acc.name})"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Sauvegarde impossible.")
            }
        }
    }

    fun adminDeleteBusiness(businessId: String) {
        if (!_uiState.value.admin.isAuthenticated) return
        val target = _uiState.value.admin.businesses.firstOrNull { it.account.id == businessId }?.account ?: return
        askConfirm(
            "Supprimer « ${target.name} » de cet appareil ? Pensez à exporter une sauvegarde d’abord.",
            confirmLabel = "Supprimer",
            isDestructive = true
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    repository.deleteBusiness(businessId)
                    inventoryRepository.deleteAllForBusiness(businessId)
                }
                refreshAdminDashboard()
                showToast("« ${target.name} » supprimée de l’appareil.")
            }
        }
    }
}
