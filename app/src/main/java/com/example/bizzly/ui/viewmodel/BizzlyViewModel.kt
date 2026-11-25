package com.example.bizzly.ui.viewmodel

import android.app.Application
import android.content.res.Configuration
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bizzly.data.local.BizzlyDatabase
import com.example.bizzly.data.repository.BizzlyRepository
import com.example.bizzly.data.sync.SupabaseSyncClient
import com.example.bizzly.data.sync.SyncException
import com.example.bizzly.data.sync.SyncSnapshotSummary
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.ActivityLog
import com.example.bizzly.model.ArchivePeriod
import com.example.bizzly.model.BizzlyPermissions
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.BusinessProfile
import com.example.bizzly.model.CashMovement
import com.example.bizzly.model.ClosedPeriod
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaidInvoice
import com.example.bizzly.model.Product
import com.example.bizzly.model.SampleCatalog
import com.example.bizzly.model.SyncCredentials
import com.example.bizzly.model.SyncStatus
import com.example.bizzly.security.LockoutTracker
import com.example.bizzly.security.PinSecurity
import com.example.bizzly.util.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthMode {
    PICK_BUSINESS,
    PICK_PROFILE,
    PIN_LOGIN,
    SIGNUP,
    LINK_PHONE,
    RESTORE_BACKUP
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
    val error: String = ""
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
    private val syncClient = SupabaseSyncClient(repository)
    private val lockout = LockoutTracker(application)

    private val _uiState = MutableStateFlow(BizzlyUiState())
    val uiState: StateFlow<BizzlyUiState> = _uiState.asStateFlow()

    private val seenDocVersions = mutableMapOf<String, Long>()
    private var syncDebounceJob: Job? = null
    private var syncDirty = false
    private var syncAgain = false
    private var lastActivityMs = System.currentTimeMillis()
    private var oneShotAuthorized = false

    init {
        val sysNight = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialDark = repository.getDarkThemePreference() ?: sysNight

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
                    selectedBusinessId = if (hasLast) lastBiz else null
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
                authMode = if (it.accounts.isEmpty() && mode != AuthMode.RESTORE_BACKUP && mode != AuthMode.LINK_PHONE) {
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
        val withLog = appendLog(syncedSrv, "Connexion (${role.label})")
        withContext(Dispatchers.IO) { repository.saveBusinessData(account.id, withLog) }

        _uiState.update {
            it.copy(
                loginBusy = false,
                loginPin = "",
                loginError = "",
                activeAccount = account,
                activeProfile = profile,
                businessData = withLog,
                currentTab = BizzlyPermissions.defaultTab(role),
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
                        brand = result.palette ?: it.signup.brand
                    )
                )
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
            val raw = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
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
        val subtitle = if (permission == BizzlyPermissions.RESET || permission == BizzlyPermissions.RESTORE) {
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

    fun selectTab(tab: BizzlyTab) {
        touchActivity()
        if (!canTab(tab)) {
            showToast("Accès réservé à la caisse ou au gérant")
            return
        }
        _uiState.update { it.copy(currentTab = tab, currentTable = null) }
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
            repository.saveBusinessData(acc.id, nextData)
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
            repository.saveAccount(updatedAcc)
            repository.saveBusinessData(updatedAcc.id, nextData)
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
                cashGivenText = ""
            )
        }
    }

    fun closeTableDetail() {
        _uiState.update {
            it.copy(
                currentTable = null,
                paymentMode = "Espèces",
                cashGivenText = ""
            )
        }
    }

    fun setPaymentMode(mode: String) {
        _uiState.update { it.copy(paymentMode = mode, cashGivenText = "") }
    }

    fun setCashGiven(text: String) {
        _uiState.update { it.copy(cashGivenText = text.filter { ch -> ch.isDigit() }) }
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

            val nextTb = data.tb.toMutableMap()
            if (nextItems.isEmpty() && cur.sv == defaultSrv) {
                nextTb.remove(key)
            } else {
                nextTb[key] = cur.copy(it = nextItems)
            }
            data.copy(tb = nextTb)
        }
    }

    fun payTable(tableNumber: Int) {
        if (!requirePermission(BizzlyPermissions.ENCAISSER) { payTable(tableNumber) }) return
        val st = _uiState.value
        val data = st.businessData
        val key = tableNumber.toString()
        val tbl = data.tb[key] ?: return
        val total = BizzlyCalculator.tableTotal(data, tableNumber)
        if (total <= 0.0) return

        val given = if (st.paymentMode == "Espèces") st.cashGivenText.toDoubleOrNull() ?: 0.0 else 0.0
        if (given > 0.0 && given < total) {
            showToast("Le montant donné est insuffisant.")
            return
        }

        val today = BizzlyCalculator.todayIso()
        val srvName = BizzlyCalculator.serverName(data, tbl.sv)
        val changeSuffix = if (given > 0.0) " · rendu ${BizzlyCalculator.formatFcfa(given - total)}" else ""
        val msg = "Table $tableNumber encaissée · ${BizzlyCalculator.formatFcfa(total)} · $srvName · ${st.paymentMode}$changeSuffix"

        mutateData(msg) { d ->
            val svMut = d.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
            val todayMap = svMut.getOrPut(today) { mutableMapOf() }
            for ((pidStr, q) in tbl.it) {
                todayMap[pidStr] = (todayMap[pidStr] ?: 0.0) + q
            }
            val paidRec = PaidInvoice(
                d = today,
                t = tableNumber,
                sv = tbl.sv,
                tot = total,
                m = st.paymentMode,
                u = syncClient.newUid(),
                ts = System.currentTimeMillis()
            )
            val tbMut = d.tb.toMutableMap().apply { remove(key) }
            d.copy(
                sv = svMut,
                paid = listOf(paidRec) + d.paid,
                tb = tbMut
            )
        }
        _uiState.update { it.copy(currentTable = null, cashGivenText = "", paymentMode = "Espèces") }
        showToast("Table $tableNumber encaissée")
    }

    fun freeTable(tableNumber: Int) {
        if (!requirePermission(BizzlyPermissions.SUPPR) { freeTable(tableNumber) }) return
        askConfirm("Libérer la table $tableNumber sans encaisser ?", isDestructive = true) {
            mutateData("Table $tableNumber libérée") { data ->
                val tbMut = data.tb.toMutableMap().apply { remove(tableNumber.toString()) }
                data.copy(tb = tbMut)
            }
            _uiState.update { it.copy(currentTable = null, cashGivenText = "", paymentMode = "Espèces") }
        }
    }

    // --- Sales Counter ---

    fun adjustDailySale(productId: Long, delta: Double) {
        if (!requirePermission(BizzlyPermissions.VENTES) { adjustDailySale(productId, delta) }) return
        val date = _uiState.value.salesDate
        val prod = BizzlyCalculator.productById(_uiState.value.businessData, productId) ?: return
        val msg = "${if (delta > 0) "Vente +1 " else "Vente −1 "}${prod.n} · ${BizzlyCalculator.formatFcfa(prod.pv)}"
        mutateData(msg) { data ->
            val svMut = data.sv.mapValues { it.value.toMutableMap() }.toMutableMap()
            val dayMap = svMut.getOrPut(date) { mutableMapOf() }
            val pidKey = productId.toString()
            val nextQty = ((dayMap[pidKey] ?: 0.0) + delta).coerceAtLeast(0.0)
            if (nextQty > 0.0) dayMap[pidKey] = nextQty else dayMap.remove(pidKey)
            if (dayMap.isEmpty()) svMut.remove(date)
            data.copy(sv = svMut)
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
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExportUtils.decodeAndResizeLogo(getApplication(), uri)
            }
            if (result == null) {
                showToast("Image illisible.")
                return@launch
            }
            mutateAccount("Logo modifié") { acc ->
                acc.copy(
                    logo = result.dataUri,
                    lw = result.width,
                    lh = result.height,
                    brand = result.palette ?: acc.brand
                )
            }
        }
    }

    fun removeAccountLogo() {
        mutateAccount { acc -> acc.copy(logo = "", lw = 0, lh = 0) }
    }

    fun updateBrandColor(channel: String, hex: String) {
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

    fun autoPaletteFromLogo() {
        val acc = _uiState.value.activeAccount
        val bmp = ExportUtils.decodeDataUriBitmap(acc?.logo)
        if (bmp == null) {
            showToast("Ajoutez d’abord un logo.")
            return
        }
        val pal = BizzlyCalculator.extractBrandPalette(bmp)
        bmp.recycle()
        if (pal != null) {
            mutateAccount { it.copy(brand = pal) }
        }
    }

    fun resetBrandToBizzly() {
        mutateAccount { it.copy(brand = BrandColors()) }
    }

    fun updateTableCount(countRaw: String) {
        val data = _uiState.value.businessData
        var maxOpen = 0
        for ((k, tbl) in data.tb) {
            if (tbl.it.isNotEmpty()) maxOpen = maxOf(maxOpen, k.toIntOrNull() ?: 0)
        }
        val target = (countRaw.toIntOrNull() ?: data.nt).coerceIn(maxOf(1, maxOpen), 200)
        mutateData("Nombre de tables : $target") { it.copy(nt = target) }
    }

    fun renameProfile(profileId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        mutateAccount { acc ->
            acc.copy(profiles = acc.profiles.map { if (it.id == profileId) it.copy(name = trimmed) else it })
        }
    }

    fun changeProfileRole(profileId: Long, newRole: String) {
        val acc = _uiState.value.activeAccount ?: return
        val prof = acc.profiles.firstOrNull { it.id == profileId } ?: return
        val otherGerants = acc.profiles.count { it.role == BizzlyRole.GERANT.key && it.id != profileId }
        if (prof.role == BizzlyRole.GERANT.key && newRole != BizzlyRole.GERANT.key && otherGerants == 0) {
            showToast("Il faut garder au moins un gérant.")
            return
        }
        mutateAccount { a ->
            a.copy(profiles = a.profiles.map { if (it.id == profileId) it.copy(role = newRole) else it })
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
        val name = nameRaw.trim()
        if (name.isEmpty()) return
        val newId = System.currentTimeMillis()
        mutateAccount("Profil ajouté : $name") { a ->
            a.copy(profiles = a.profiles + BusinessProfile(id = newId, role = roleKey, name = name))
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
                withContext(Dispatchers.IO) { repository.deleteBusiness(acc.id) }
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
                viewModelScope.launch(Dispatchers.IO) { repository.saveBusinessData(acc.id, fresh) }
                scheduleSync()
            }
            showToast("Données réinitialisées.")
        }
    }

    fun restoreBackupInSession(uri: Uri) {
        if (!requireSensitivePin(BizzlyPermissions.RESTORE) { restoreBackupInSession(uri) }) return
        val acc = _uiState.value.activeAccount ?: return
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
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
                    repository.saveBusinessData(acc.id, logged)
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
            repository.saveBusinessData(acc.id, updatedData)
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

    fun shareTableInvoicePdf(tableNumber: Int) {
        val st = _uiState.value
        val acc = st.activeAccount
        val data = st.businessData
        val cashGiven = if (st.paymentMode == "Espèces") st.cashGivenText.toDoubleOrNull() ?: 0.0 else 0.0
        _uiState.update { it.copy(exportBusy = true) }
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ExportUtils.generateInvoicePdf(acc, data, tableNumber, st.paymentMode, cashGiven)
                }
                val filename = "facture-${BizzlyCalculator.slugify(acc?.name)}-table-$tableNumber-${BizzlyCalculator.todayIso()}.pdf"
                publishExport(
                    filename = filename,
                    bytes = bytes,
                    mimeType = "application/pdf",
                    chooserTitle = "Facture Table $tableNumber"
                )
            }.onFailure {
                _uiState.update { s -> s.copy(exportBusy = false) }
                showToast("Impossible de générer la facture PDF.")
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
}
