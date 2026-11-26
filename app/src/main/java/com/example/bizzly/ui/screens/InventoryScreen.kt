package com.example.bizzly.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.data.local.InventoryStockStatus
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.bringIntoViewOnIme
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import com.example.bizzly.util.NotificationHelper

@Composable
fun InventoryScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Inventaire & alertes de stock",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                border = BorderStroke(1.dp, tokens.headerControlBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inv_back_more_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.onHeader)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.onHeader)
            }
        }

        InventoryTrackerContent(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryTrackerContent(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    val items by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val lowStockItems by viewModel.lowStockInventoryItems.collectAsStateWithLifecycle()
    val role = state.activeProfile?.let { BizzlyRole.fromKey(it.role) }
    val canReceiveNotif = role == BizzlyRole.GERANT || role == BizzlyRole.CAISSE
    var hasNotifPerm by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted || NotificationHelper.hasNotificationPermission(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableIntStateOf(0) } // 0 = Tous, 1 = Alertes, 2 = Ruptures
    var editorVisible by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var quickQtyItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var restockDialogOpen by remember { mutableStateOf(false) }

    val outOfStockCount = remember(items) { items.count { it.quantity <= 0.0 } }
    val lowOnlyCount = remember(items) { items.count { it.quantity > 0.0 && it.isBelowThreshold } }
    val totalValue = remember(items) { items.sumOf { (it.quantity * it.unitPrice).coerceAtLeast(0.0) } }

    val filteredItems = remember(items, searchQuery, filterMode) {
        val q = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchesFilter = when (filterMode) {
                1 -> item.isBelowThreshold
                2 -> item.quantity <= 0.0
                else -> true
            }
            val matchesSearch = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                item.sku.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (canReceiveNotif && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BizzlyCard(borderColor = tokens.primary) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = tokens.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifications de stock critique", fontWeight = FontWeight.Bold, color = tokens.text, fontSize = 13.5.sp)
                        Text(
                            text = "Recevez une alerte dès qu’un article passe sous son seuil.",
                            fontSize = 12.sp,
                            color = tokens.muted
                        )
                    }
                    Button(
                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("inv_enable_notif_btn")
                    ) {
                        Text("Activer", fontSize = 12.5.sp)
                    }
                }
            }
        }

        if (lowStockItems.isNotEmpty()) {
            val alertColor = if (outOfStockCount > 0) tokens.ko else tokens.warn
            BizzlyCard(
                borderColor = alertColor,
                modifier = Modifier.testTag("inv_low_stock_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = alertColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${lowStockItems.size} alerte${if (lowStockItems.size > 1) "s" else ""} de seuil",
                            fontWeight = FontWeight.Bold,
                            color = tokens.text
                        )
                        val detailParts = buildList {
                            if (outOfStockCount > 0) add("$outOfStockCount en rupture")
                            if (lowOnlyCount > 0) add("$lowOnlyCount sous le seuil")
                        }
                        if (detailParts.isNotEmpty()) {
                            Text(
                                text = detailParts.joinToString(" · "),
                                fontSize = 12.5.sp,
                                color = alertColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { filterMode = if (filterMode == 1) 0 else 1 },
                        border = BorderStroke(1.dp, alertColor),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = if (filterMode == 1) "Tout voir" else "Filtrer",
                            color = alertColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = lowStockItems.take(5).joinToString(" · ") {
                        "${it.name} (${BizzlyCalculator.formatQty(it.quantity)}/${BizzlyCalculator.formatQty(it.minThreshold)})"
                    } + if (lowStockItems.size > 5) " …" else "",
                    fontSize = 12.5.sp,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { restockDialogOpen = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = alertColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("inv_restock_order_btn")
                ) {
                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bon de commande fournisseur (${lowStockItems.size})", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // KPI Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InventoryKpiBox(
                label = "Articles",
                value = items.size.toString(),
                valueColor = tokens.text,
                modifier = Modifier.weight(1f)
            )
            InventoryKpiBox(
                label = "Alertes",
                value = lowStockItems.size.toString(),
                valueColor = if (lowStockItems.isNotEmpty()) tokens.ko else tokens.ok,
                modifier = Modifier.weight(1f)
            )
            InventoryKpiBox(
                label = "Valeur",
                value = BizzlyCalculator.formatFcfa(totalValue),
                valueColor = tokens.accentReadable,
                modifier = Modifier.weight(1.35f)
            )
        }

        // Actions + Search + Filters
        BizzlyCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        editingItem = null
                        editorVisible = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("inv_add_item_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajouter un article", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                if (state.businessData.prod.isNotEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::syncInventoryFromCatalog,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                            .testTag("inv_sync_catalog_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = tokens.accentReadable, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Catalogue", color = tokens.text, maxLines = 1)
                    }
                }
            }

            if (state.businessData.prod.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::syncBarToInventory,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("inv_sync_bar_to_inv_btn")
                    ) {
                        Text("Comptage Bar → Inv.", color = tokens.text, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = viewModel::syncInventoryToBar,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("inv_sync_inv_to_bar_btn")
                    ) {
                        Text("Inv. → Comptage Bar", color = tokens.text, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher article, catégorie, SKU") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnIme()
                    .testTag("inv_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterMode == 0,
                    onClick = { filterMode = 0 },
                    label = { Text("Tous (${items.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.primary,
                        selectedLabelColor = tokens.onPrimary
                    ),
                    modifier = Modifier.testTag("inv_filter_all")
                )
                FilterChip(
                    selected = filterMode == 1,
                    onClick = { filterMode = 1 },
                    label = { Text("Sous seuil (${lowStockItems.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.warn,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("inv_filter_low")
                )
                FilterChip(
                    selected = filterMode == 2,
                    onClick = { filterMode = 2 },
                    label = { Text("Ruptures ($outOfStockCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.ko,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("inv_filter_out")
                )
            }
        }

        if (filteredItems.isEmpty()) {
            BizzlyCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = tokens.muted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (items.isEmpty()) {
                            "Aucun article dans l’inventaire."
                        } else {
                            "Aucun article ne correspond à ce filtre."
                        },
                        color = tokens.muted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            filteredItems.forEach { item ->
                InventoryItemCard(
                    item = item,
                    onDecrement = { viewModel.adjustInventoryQuantity(item, -1.0) },
                    onIncrement = { viewModel.adjustInventoryQuantity(item, 1.0) },
                    onQuickQty = { quickQtyItem = item },
                    onEdit = {
                        editingItem = item
                        editorVisible = true
                    },
                    onDelete = { viewModel.deleteInventoryItem(item) }
                )
            }
        }

        BizzlySignatureFooter()
    }

    if (editorVisible) {
        InventoryItemEditorDialog(
            initial = editingItem,
            defaultThreshold = state.businessData.seuil,
            onSave = { id, name, sku, category, unit, quantity, minThreshold, unitPrice, notes ->
                viewModel.saveInventoryItem(
                    id = id,
                    name = name,
                    sku = sku,
                    category = category,
                    unit = unit,
                    quantity = quantity,
                    minThreshold = minThreshold,
                    unitPrice = unitPrice,
                    notes = notes
                )
                editorVisible = false
            },
            onDismiss = { editorVisible = false }
        )
    }

    quickQtyItem?.let { target ->
        InventoryQuickQtyDialog(
            item = target,
            onApplyExact = { qty ->
                viewModel.setInventoryQuantity(target, qty)
                quickQtyItem = null
            },
            onDismiss = { quickQtyItem = null }
        )
    }

    if (restockDialogOpen && lowStockItems.isNotEmpty()) {
        RestockOrderDialog(
            lowStockItems = lowStockItems,
            onExportPdf = { supplier, lines ->
                restockDialogOpen = false
                viewModel.exportRestockOrderPdf(supplier, lines)
            },
            onExportExcel = { supplier, lines ->
                restockDialogOpen = false
                viewModel.exportRestockOrderExcel(supplier, lines)
            },
            onDismiss = { restockDialogOpen = false }
        )
    }
}

@Composable
private fun InventoryKpiBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.card)
            .border(1.dp, tokens.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = tokens.muted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItemEntity,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onQuickQty: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val status = item.stockStatus()
    val statusColor = when (status) {
        InventoryStockStatus.OUT_OF_STOCK -> tokens.ko
        InventoryStockStatus.LOW_STOCK -> tokens.warn
        InventoryStockStatus.HEALTHY -> tokens.ok
    }
    val statusLabel = when (status) {
        InventoryStockStatus.OUT_OF_STOCK -> "Rupture"
        InventoryStockStatus.LOW_STOCK -> "Stock bas"
        InventoryStockStatus.HEALTHY -> "En stock"
    }
    val borderColor = when (status) {
        InventoryStockStatus.OUT_OF_STOCK -> tokens.ko
        InventoryStockStatus.LOW_STOCK -> tokens.warn
        InventoryStockStatus.HEALTHY -> null
    }

    BizzlyCard(borderColor = borderColor, modifier = Modifier.testTag("inv_item_card_${item.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    color = tokens.text,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = buildList {
                    if (item.category.isNotBlank()) add(item.category)
                    if (item.sku.isNotBlank()) add("SKU ${item.sku}")
                    if (item.unitPrice > 0.0) add(BizzlyCalculator.formatFcfa(item.unitPrice))
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        fontSize = 12.sp,
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val denominator = (item.minThreshold * 2.0).coerceAtLeast(1.0)
        val progress = (item.quantity / denominator).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            color = statusColor,
            trackColor = tokens.border,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onQuickQty)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("inv_quick_qty_btn_${item.id}")
            ) {
                Text(
                    text = "${BizzlyCalculator.formatQty(item.quantity)} ${item.unit}",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (item.isBelowThreshold) statusColor else tokens.text
                )
                Text(
                    text = "Seuil min : ${BizzlyCalculator.formatQty(item.minThreshold)} ${item.unit}",
                    fontSize = 12.sp,
                    color = tokens.muted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.background)
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("inv_minus_${item.id}")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Retirer 1", tint = tokens.ko)
                }
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.background)
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("inv_plus_${item.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter 1", tint = tokens.accentReadable)
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inv_edit_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = tokens.muted)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inv_del_${item.id}")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = tokens.ko)
                }
            }
        }

        if (item.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.notes,
                fontSize = 12.sp,
                color = tokens.muted
            )
        }
    }
}
