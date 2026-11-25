package com.example.bizzly

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.ui.components.ConfirmActionDialog
import com.example.bizzly.ui.components.ExportReadyDialog
import com.example.bizzly.ui.components.PinAuthorizationOverlay
import com.example.bizzly.ui.screens.AuthFlowScreen
import com.example.bizzly.ui.screens.CashScreen
import com.example.bizzly.ui.screens.EnterpriseScreen
import com.example.bizzly.ui.screens.HistoryScreen
import com.example.bizzly.ui.screens.HomeScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: BizzlyViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            BizzlyTheme(
                brand = state.activeBrand,
                isDark = state.isDarkTheme
            ) {
                BizzlyRootScreen(
                    state = state,
                    viewModel = vm
                )
            }
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
                Box(modifier = Modifier.padding(innerPadding)) {
                    AuthFlowScreen(state = state, viewModel = viewModel)
                }
            }
        } else {
            val visibleNavTabs = mainNavTabs.filter { viewModel.canTab(it.tab) }
            val activeNavTab = when (state.currentTab) {
                BizzlyTab.PROD, BizzlyTab.SRV, BizzlyTab.QR, BizzlyTab.HIST -> BizzlyTab.MORE
                else -> state.currentTab
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 840.dp

                Scaffold(
                    containerColor = tokens.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = tokens.card,
                                tonalElevation = 4.dp
                            ) {
                                visibleNavTabs.forEach { item ->
                                    val selected = activeNavTab == item.tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { viewModel.selectTab(item.tab) },
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label
                                            )
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
                                            indicatorColor = tokens.tint,
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
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = tokens.card,
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
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label
                                            )
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
                                            indicatorColor = tokens.tint,
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
                                .fillMaxHeight(),
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
