package com.example.bizzly

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.ui.components.ConfirmActionDialog
import com.example.bizzly.ui.components.ExportReadyDialog
import com.example.bizzly.ui.components.PinAuthorizationOverlay
import com.example.bizzly.ui.screens.AuthFlowScreen
import com.example.bizzly.ui.screens.CashScreen
import com.example.bizzly.ui.screens.EnterpriseScreen
import com.example.bizzly.ui.screens.HistoryScreen
import com.example.bizzly.ui.screens.HomeScreen
import com.example.bizzly.ui.screens.InventoryScreen
import com.example.bizzly.ui.screens.MoreMenuScreen
import com.example.bizzly.ui.screens.ProductsScreen
import com.example.bizzly.ui.screens.QrMenuScreen
import com.example.bizzly.ui.screens.SalesScreen
import com.example.bizzly.ui.screens.StockScreen
import com.example.bizzly.ui.screens.TablesScreen
import com.example.bizzly.ui.theme.BizzlyTheme
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import com.example.bizzly.util.ExportUtils
import com.example.bizzly.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var externalTabRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalTabRoute = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)
        setContent {
            val vm: BizzlyViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val isDark = state.isDarkTheme

            LaunchedEffect(externalTabRoute) {
                val route = externalTabRoute
                if (!route.isNullOrBlank()) {
                    vm.handleExternalTabRequest(route)
                    externalTabRoute = null
                }
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark }
                )
                onDispose {}
            }

            BizzlyTheme(
                brand = state.activeBrand,
                isDark = isDark
            ) {
                BizzlyRootScreen(
                    state = state,
                    viewModel = vm
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)
        if (!tab.isNullOrBlank()) {
            externalTabRoute = tab
        }
    }
}

private data class NavTabItem(
    val tab: BizzlyTab,
    val label: String,
    val icon: ImageVector
)

private val mainNavTabs = listOf(
    NavTabItem(BizzlyTab.HOME, "Accueil", Icons.Default.Home),
    NavTabItem(BizzlyTab.TABLES, "Tables", Icons.Default.Restaurant),
    NavTabItem(BizzlyTab.SALES, "Ventes", Icons.AutoMirrored.Filled.ReceiptLong),
    NavTabItem(BizzlyTab.STOCK, "Stock", Icons.Default.Inventory2),
    NavTabItem(BizzlyTab.CASH, "Argent", Icons.Default.AccountBalanceWallet),
    NavTabItem(BizzlyTab.MORE, "Plus", Icons.Default.MoreHoriz)
)

@Composable
private fun BizzlyRootScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lowStockInventory by viewModel.lowStockInventoryItems.collectAsStateWithLifecycle()


    val handleCreateDocumentResult: (android.net.Uri?) -> Unit = { uri ->
        val exp = state.readyExport
        if (uri != null && exp != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ExportUtils.writeUriBytes(context, uri, exp.bytes)
                }
                if (ok) {
                    viewModel.showToast("Enregistré : ${exp.filename}")
                } else {
                    viewModel.showToast("Impossible d’enregistrer le fichier.")
                }
            }
        }
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = handleCreateDocumentResult
    )
    val xlsxSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        onResult = handleCreateDocumentResult
    )
    val jsonSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = handleCreateDocumentResult
    )
    val anySaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
        onResult = handleCreateDocumentResult
    )

    var askedNotifOnce by rememberSaveable { mutableStateOf(false) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(state.isLoggedIn, state.activeProfile?.role) {
        val role = state.activeProfile?.let { BizzlyRole.fromKey(it.role) }
        val shouldNotify = role == BizzlyRole.GERANT || role == BizzlyRole.CAISSE
        if (state.isLoggedIn &&
            shouldNotify &&
            !askedNotifOnce &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            askedNotifOnce = true
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.toastTick) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        viewModel.touchActivity()
                    }
                }
            }
    ) {
        if (!state.initialized) {
            Box(modifier = Modifier.fillMaxSize())
        } else if (!state.isLoggedIn) {
            Scaffold(
                containerColor = tokens.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                ) {
                    AuthFlowScreen(state = state, viewModel = viewModel)
                }
            }
        } else {
            val visibleNavTabs = mainNavTabs.filter { viewModel.canTab(it.tab) }
            val activeNavTab = when (state.currentTab) {
                BizzlyTab.PROD, BizzlyTab.SRV, BizzlyTab.QR, BizzlyTab.HIST, BizzlyTab.INVENTORY -> BizzlyTab.MORE
                else -> state.currentTab
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 840.dp

                Scaffold(
                    containerColor = tokens.background,
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = tokens.navContainer,
                                tonalElevation = 4.dp
                            ) {
                                visibleNavTabs.forEach { item ->
                                    val selected = activeNavTab == item.tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(item.tab) },
                                        icon = {
                                            val lowCount = if (item.tab == BizzlyTab.STOCK) lowStockInventory.size else 0
                                            if (lowCount > 0) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(
                                                            containerColor = tokens.ko,
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(if (lowCount > 99) "99+" else lowCount.toString())
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = tokens.accentReadable,
                                            selectedTextColor = tokens.accentReadable,
                                            indicatorColor = tokens.navIndicator,
                                            unselectedIconColor = tokens.muted,
                                            unselectedTextColor = tokens.muted
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${item.tab.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = tokens.navContainer,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                            ) {
                                visibleNavTabs.forEach { item ->
                                    val selected = activeNavTab == item.tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(item.tab) },
                                        icon = {
                                            val lowCount = if (item.tab == BizzlyTab.STOCK) lowStockInventory.size else 0
                                            if (lowCount > 0) {
                                                BadgedBox(
                                                    badge = {
                                                        Badge(
                                                            containerColor = tokens.ko,
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(if (lowCount > 99) "99+" else lowCount.toString())
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = tokens.accentReadable,
                                            selectedTextColor = tokens.accentReadable,
                                            indicatorColor = tokens.navIndicator,
                                            unselectedIconColor = tokens.muted,
                                            unselectedTextColor = tokens.muted
                                        ),
                                        modifier = Modifier.testTag("rail_tab_${item.tab.route}")
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .imePadding(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 820.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                when (state.currentTab) {
                                    BizzlyTab.HOME -> HomeScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.TABLES -> TablesScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.SALES -> SalesScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.STOCK -> StockScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.CASH -> CashScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.MORE -> MoreMenuScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.PROD -> ProductsScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.SRV -> EnterpriseScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.QR -> QrMenuScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.HIST -> HistoryScreen(state = state, viewModel = viewModel)
                                    BizzlyTab.INVENTORY -> InventoryScreen(state = state, viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        state.pinOverlay?.let { ov ->
            PinAuthorizationOverlay(
                overlay = ov,
                onKey = viewModel::pressOverlayPinKey,
                onDismiss = viewModel::dismissPinOverlay
            )
        }

        state.confirmDialog?.let { dlg ->
            ConfirmActionDialog(
                state = dlg,
                onDismiss = viewModel::dismissConfirm
            )
        }

        state.readyExport?.let { exp ->
            ExportReadyDialog(
                export = exp,
                onOpen = {
                    val uris = listOfNotNull(exp.cacheUri, exp.downloadsUri)
                    val opened = uris.any { uri -> ExportUtils.openExport(context, uri, exp.mimeType) }
                    if (!opened) {
                        viewModel.showToast(
                            if (exp.savedToDownloads) {
                                "Aucune application installée pour ouvrir ce fichier. Enregistré dans Téléchargements/Bizzly."
                            } else {
                                "Aucune application installée pour ouvrir ce fichier. Utilisez Enregistrer ou Partager."
                            }
                        )
                    }
                },
                onShare = {
                    val uri = exp.cacheUri ?: exp.downloadsUri
                    val shared = uri != null && ExportUtils.shareExport(
                        context = context,
                        uri = uri,
                        filename = exp.filename,
                        mimeType = exp.mimeType,
                        chooserTitle = exp.chooserTitle
                    )
                    if (!shared) {
                        viewModel.showToast("Aucune application de partage disponible.")
                    }
                },
                onSaveAs = {
                    when (exp.mimeType) {
                        "application/pdf" -> pdfSaveLauncher.launch(exp.filename)
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                            xlsxSaveLauncher.launch(exp.filename)
                        "application/json" -> jsonSaveLauncher.launch(exp.filename)
                        else -> anySaveLauncher.launch(exp.filename)
                    }
                },
                onDismiss = viewModel::dismissReadyExport
            )
        }

        if (state.exportBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = tokens.primary)
            }
        }
    }
}
