package com.example.bizzly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.domain.StockAlertLevel
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.CashMovement
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.HeroPrimaryHighlight
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import java.time.LocalDate

@Composable
fun SalesScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val date = state.salesDate
    val dayMap = data.sv[date].orEmpty()
    val dayRev = BizzlyCalculator.dayTotal(data, date)
    val articleCount = BizzlyCalculator.dayArticleCount(data, date)
    val query = state.salesSearch.trim().lowercase()
    val filteredProducts = remember(data.prod, query) {
        if (query.isEmpty()) data.prod else data.prod.filter { it.n.lowercase().contains(query) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Ventes du jour",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            HeroPrimaryHighlight(
                pillText = BizzlyCalculator.formatDateFr(date),
                bigValue = BizzlyCalculator.formatFcfa(dayRev),
                caption = "${BizzlyCalculator.formatQty(articleCount)} articles vendus · comptés dans les recettes"
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizzlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            val prev = runCatching { LocalDate.parse(date).minusDays(1).toString() }.getOrDefault(date)
                            viewModel.setSalesDate(prev)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                            .testTag("sales_prev_day_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Jour précédent", tint = tokens.text)
                    }
                    OutlinedButton(
                        onClick = { viewModel.setSalesDate(BizzlyCalculator.todayIso()) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sales_today_btn")
                    ) {
                        Text(
                            text = if (date == BizzlyCalculator.todayIso()) "Aujourd’hui · ${BizzlyCalculator.formatDateFr(date)}"
                            else BizzlyCalculator.formatDateFr(date),
                            color = tokens.text
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = runCatching { LocalDate.parse(date).plusDays(1).toString() }.getOrDefault(date)
                            viewModel.setSalesDate(next)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                            .testTag("sales_next_day_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Jour suivant", tint = tokens.text)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.salesSearch,
                    onValueChange = viewModel::setSalesSearch,
                    placeholder = { Text("Chercher un produit") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_search_input")
                )
            }

            BizzlyCard {
                if (filteredProducts.isEmpty()) {
                    Text(
                        text = if (data.prod.isEmpty()) "Aucun produit dans le catalogue." else "Aucun produit trouvé.",
                        color = tokens.muted,
                        fontSize = 13.5.sp
                    )
                } else {
                    filteredProducts.forEachIndexed { idx, prod ->
                        val qty = dayMap[prod.id.toString()] ?: 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.n, fontWeight = FontWeight.Bold, color = tokens.text)
                                Text(
                                    text = BizzlyCalculator.formatFcfa(prod.pv) +
                                        if (qty > 0.0) " · ${BizzlyCalculator.formatFcfa(qty * prod.pv)}" else "",
                                    fontSize = 13.sp,
                                    color = tokens.muted
                                )
                            }
                            IconButton(
                                onClick = { viewModel.adjustDailySale(prod.id, -1.0) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.background)
                                    .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                    .testTag("sales_minus_${prod.id}")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Moins", tint = tokens.accentReadable)
                            }
                            Text(
                                text = BizzlyCalculator.formatQty(qty),
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = tokens.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(34.dp)
                            )
                            IconButton(
                                onClick = { viewModel.adjustDailySale(prod.id, 1.0) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.background)
                                    .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                    .testTag("sales_plus_${prod.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus", tint = tokens.accentReadable)
                            }
                        }
                        if (idx < filteredProducts.lastIndex) HorizontalDivider(color = tokens.border)
                    }
                }
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
fun StockScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    var seuilText by remember(data.seuil) { mutableStateOf(data.seuil.toString()) }

    var totalQty = 0.0
    var totalVal = 0.0
    data.prod.forEach { p ->
        val info = BizzlyCalculator.stockInfo(data, p)
        totalQty += info.soldEffective
        totalVal += info.soldEffective * p.pv
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Début = au bar · Entrées = dépôt · Fin = comptage réel · Vendu = calcul auto",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizzlyCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Alerte stock bas sous", modifier = Modifier.weight(1f), color = tokens.text)
                    OutlinedTextField(
                        value = seuilText,
                        onValueChange = { seuilText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(96.dp)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused && seuilText.toIntOrNull() != data.seuil) {
                                    seuilText.toIntOrNull()?.let { viewModel.setStockAlertThreshold(it) }
                                }
                            }
                            .testTag("stock_seuil_input")
                    )
                    Button(
                        onClick = { seuilText.toIntOrNull()?.let { viewModel.setStockAlertThreshold(it) } },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("OK")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(tokens.warn))
                        Text("stock bas", fontSize = 12.5.sp, color = tokens.muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(tokens.ko))
                        Text("critique", fontSize = 12.5.sp, color = tokens.muted)
                    }
                }
            }

            if (data.prod.isEmpty()) {
                BizzlyCard {
                    Text("Ajoutez d’abord des produits pour suivre le stock.", color = tokens.muted)
                }
            } else {
                data.prod.forEach { p ->
                    val info = BizzlyCalculator.stockInfo(data, p)
                    var dText by remember(p.id, p.d) {
                        mutableStateOf(if (p.d == 0.0) "" else BizzlyCalculator.formatQty(p.d))
                    }
                    var eText by remember(p.id, p.e) {
                        mutableStateOf(if (p.e == 0.0) "" else BizzlyCalculator.formatQty(p.e))
                    }
                    var fText by remember(p.id, p.f) {
                        mutableStateOf(p.f?.let { BizzlyCalculator.formatQty(it) } ?: "")
                    }

                    BizzlyCard(
                        borderColor = when (info.level) {
                            StockAlertLevel.CRITICAL -> tokens.ko
                            StockAlertLevel.LOW -> tokens.warn
                            StockAlertLevel.NONE -> null
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (info.level != StockAlertLevel.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (info.level == StockAlertLevel.CRITICAL) tokens.ko else tokens.warn)
                                    )
                                }
                                Text(p.n, fontWeight = FontWeight.Bold, color = tokens.text)
                            }
                            Text(
                                text = "Reste ${BizzlyCalculator.formatQty(info.current)}" +
                                    if (info.purchased > 0.0) " · +${BizzlyCalculator.formatQty(info.purchased)} achetés" else "",
                                fontSize = 12.5.sp,
                                color = if (info.level == StockAlertLevel.CRITICAL) tokens.ko else tokens.muted
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = dText,
                                onValueChange = { dText = it },
                                label = { Text("Début", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged {
                                        if (!it.isFocused && (dText.toDoubleOrNull() ?: 0.0) != p.d) {
                                            viewModel.updateProductField(p.id, "d", dText)
                                        }
                                    }
                                    .testTag("stock_d_${p.id}")
                            )
                            OutlinedTextField(
                                value = eText,
                                onValueChange = { eText = it },
                                label = { Text("Entrées", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged {
                                        if (!it.isFocused && (eText.toDoubleOrNull() ?: 0.0) != p.e) {
                                            viewModel.updateProductField(p.id, "e", eText)
                                        }
                                    }
                                    .testTag("stock_e_${p.id}")
                            )
                            OutlinedTextField(
                                value = fText,
                                onValueChange = { fText = it },
                                label = { Text("Fin", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged {
                                        if (!it.isFocused) {
                                            val curVal = if (fText.trim().isEmpty()) null else fText.toDoubleOrNull()
                                            if (curVal != p.f) {
                                                viewModel.updateProductField(p.id, "f", fText)
                                            }
                                        }
                                    }
                                    .testTag("stock_f_${p.id}")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Vendu : ${BizzlyCalculator.formatQty(info.soldEffective)}",
                                fontSize = 13.sp,
                                color = if (info.soldEffective < 0) tokens.ko else tokens.muted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = BizzlyCalculator.formatFcfa(info.soldEffective * p.pv),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.text
                            )
                        }
                    }
                }

                BizzlyCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total vendu (${BizzlyCalculator.formatQty(totalQty)})", fontWeight = FontWeight.Bold, color = tokens.text)
                        Text(
                            text = BizzlyCalculator.formatFcfa(totalVal),
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = tokens.accentReadable
                        )
                    }
                }
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
fun CashScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val summary = BizzlyCalculator.calculateSummary(data)

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Argent",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            HeroPrimaryHighlight(
                pillText = "Reste à verser",
                bigValue = BizzlyCalculator.formatFcfa(summary.remainingToRemit),
                caption = "Recettes ${BizzlyCalculator.formatFcfa(summary.recordedRevenue)} − Dépenses ${BizzlyCalculator.formatFcfa(summary.expenses)} − Bons ${BizzlyCalculator.formatFcfa(summary.vouchers)} − Versé ${BizzlyCalculator.formatFcfa(summary.remitted)}"
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Auto-calculated daily sales
            val saleDates = data.sv.keys.sorted().filter { BizzlyCalculator.dayTotal(data, it) > 0.0 }
            SectionHeading("Recettes · ${BizzlyCalculator.formatFcfa(summary.recordedRevenue)}")
            BizzlyCard {
                if (saleDates.isEmpty()) {
                    Text("Aucune vente saisie pour l’instant.", fontSize = 13.sp, color = tokens.muted)
                } else {
                    saleDates.forEachIndexed { idx, d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${BizzlyCalculator.formatDateFr(d)} · Ventes du jour (auto)", color = tokens.text, fontSize = 13.5.sp)
                            Text(BizzlyCalculator.formatFcfa(BizzlyCalculator.dayTotal(data, d)), fontWeight = FontWeight.Bold, color = tokens.text)
                        }
                        if (idx < saleDates.lastIndex) HorizontalDivider(color = tokens.border)
                    }
                }
            }

            CashMovementSection(
                kind = "vers",
                title = "Versements remis · ${BizzlyCalculator.formatFcfa(summary.remitted)}",
                placeholder = "Remis à",
                items = data.vers,
                products = emptyList(),
                viewModel = viewModel
            )

            CashMovementSection(
                kind = "dep",
                title = "Dépenses · ${BizzlyCalculator.formatFcfa(summary.expenses)}",
                placeholder = "Motif",
                items = data.dep,
                products = data.prod,
                viewModel = viewModel
            )

            CashMovementSection(
                kind = "bons",
                title = "Bons du personnel · ${BizzlyCalculator.formatFcfa(summary.vouchers)}",
                placeholder = "Nom",
                items = data.bons,
                products = emptyList(),
                viewModel = viewModel
            )

            BizzlySignatureFooter()
        }
    }
}

@Composable
private fun CashMovementSection(
    kind: String,
    title: String,
    placeholder: String,
    items: List<CashMovement>,
    products: List<com.example.bizzly.model.Product>,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    var label by remember(kind) { mutableStateOf("") }
    var amount by remember(kind) { mutableStateOf("") }
    var dateText by remember(kind) { mutableStateOf(BizzlyCalculator.todayIso()) }
    var selectedPid by remember(kind) { mutableStateOf<Long?>(null) }
    var qtyText by remember(kind) { mutableStateOf("") }
    var prodMenuExpanded by remember { mutableStateOf(false) }

    SectionHeading(title)
    BizzlyCard {
        if (items.isEmpty()) {
            Text("Rien pour l’instant.", fontSize = 13.sp, color = tokens.muted)
        } else {
            items.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${BizzlyCalculator.formatDateFr(item.d)} · ${item.w.ifBlank { "—" }}",
                            color = tokens.text,
                            fontSize = 14.sp
                        )
                        if (item.pid != null) {
                            val pName = products.firstOrNull { it.id == item.pid }?.n ?: "?"
                            Text(
                                text = "→ +${BizzlyCalculator.formatQty(item.q ?: 0.0)} $pName ajouté au stock",
                                fontSize = 12.sp,
                                color = tokens.muted
                            )
                        }
                    }
                    Text(
                        text = BizzlyCalculator.formatFcfa(item.m),
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                    IconButton(
                        onClick = { viewModel.deleteCashMovement(kind, idx) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cash_del_${kind}_$idx")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = tokens.ko)
                    }
                }
                if (idx < items.lastIndex) HorizontalDivider(color = tokens.border)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cash_label_$kind")
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                label = { Text("Montant") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .testTag("cash_amount_$kind")
            )
        }

        if (kind == "dep" && products.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val chosen = products.firstOrNull { it.id == selectedPid }
                    OutlinedButton(
                        onClick = { prodMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cash_dep_prod_picker")
                    ) {
                        Text(
                            text = chosen?.n ?: "Achat de produit ? (facultatif)",
                            color = if (chosen == null) tokens.muted else tokens.text,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = prodMenuExpanded,
                        onDismissRequest = { prodMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Aucun produit") },
                            onClick = {
                                selectedPid = null
                                prodMenuExpanded = false
                            }
                        )
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.n) },
                                onClick = {
                                    selectedPid = p.id
                                    prodMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Quantité") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("cash_dep_qty_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Date (AAAA-MM-JJ)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cash_date_$kind")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.addCashMovement(kind, label, amount, dateText, selectedPid, qtyText)
                if ((amount.toDoubleOrNull() ?: 0.0) != 0.0) {
                    label = ""
                    amount = ""
                    selectedPid = null
                    qtyText = ""
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.primary,
                contentColor = tokens.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("cash_add_btn_$kind")
        ) {
            Text("Ajouter", fontWeight = FontWeight.SemiBold)
        }
    }
}
