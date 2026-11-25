package com.example.bizzly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PAYMENT_MODES
import com.example.bizzly.model.QUICK_CASH_AMOUNTS
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.BusinessAvatarBox
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun TablesScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val openTable = state.currentTable
    if (openTable != null && openTable > 0) {
        TableDetailScreen(
            tableNumber = openTable,
            state = state,
            viewModel = viewModel
        )
    } else {
        TableGridScreen(
            state = state,
            viewModel = viewModel
        )
    }
}

@Composable
private fun TableGridScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val openCount = data.tb.count { it.value.it.isNotEmpty() }
    val roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Tables · $openCount ouverte${if (openCount > 1) "s" else ""}",
            account = state.activeAccount,
            roleLabel = roleLabel,
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val cols = when {
                maxWidth >= 700.dp -> 6
                maxWidth >= 500.dp -> 5
                else -> 4
            }
            val totalTables = data.nt.coerceIn(1, 200)
            val rows = (totalTables + cols - 1) / cols

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until cols) {
                            val num = r * cols + c + 1
                            if (num <= totalTables) {
                                val tot = BizzlyCalculator.tableTotal(data, num)
                                val count = BizzlyCalculator.tableItemCount(data, num)
                                val occupied = count > 0
                                Surface(
                                    onClick = { viewModel.openTable(num) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (occupied) tokens.tint else tokens.card,
                                    border = BorderStroke(
                                        width = if (occupied) 1.5.dp else 1.dp,
                                        color = if (occupied) tokens.primary else tokens.border
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(78.dp)
                                        .testTag("table_tile_$num")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = num.toString(),
                                            fontFamily = SoraFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = if (occupied) tokens.accentReadable else tokens.text
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (occupied) "$count · ${BizzlyCalculator.formatFcfa(tot)}" else "Libre",
                                            fontSize = 11.sp,
                                            color = if (occupied) tokens.text else tokens.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                BizzlySignatureFooter()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableDetailScreen(
    tableNumber: Int,
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val tbl = data.tb[tableNumber.toString()] ?: OpenTable(sv = data.srv.firstOrNull()?.id ?: 0L)
    val total = BizzlyCalculator.tableTotal(data, tableNumber)
    val favorites = remember(data.prod, data.sv, data.arch) {
        BizzlyCalculator.favoriteProducts(data, 6)
    }

    var productPickerExpanded by remember { mutableStateOf(false) }
    var serverPickerExpanded by remember { mutableStateOf(false) }
    var selectedAddPid by remember(data.prod) { mutableStateOf(data.prod.firstOrNull()?.id) }

    BackHandler {
        viewModel.closeTableDetail()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Table $tableNumber · ${BizzlyCalculator.formatFcfa(total)}",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = viewModel::closeTableDetail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("table_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour aux tables",
                    tint = tokens.text,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Toutes les tables", color = tokens.text)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Server selector
            BizzlyCard {
                Text("Serveur", fontSize = 13.sp, color = tokens.muted)
                Spacer(modifier = Modifier.height(6.dp))
                Box {
                    val curSrvName = BizzlyCalculator.serverName(data, tbl.sv)
                    OutlinedButton(
                        onClick = { serverPickerExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("table_server_picker")
                    ) {
                        Text(curSrvName, color = tokens.text, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(
                        expanded = serverPickerExpanded,
                        onDismissRequest = { serverPickerExpanded = false }
                    ) {
                        data.srv.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.n) },
                                onClick = {
                                    serverPickerExpanded = false
                                    viewModel.setTableServer(tableNumber, s.id)
                                }
                            )
                        }
                    }
                }
            }

            // Fast favorite products (3 columns)
            if (favorites.isNotEmpty()) {
                BizzlyCard {
                    Text("Produits rapides", fontSize = 13.sp, color = tokens.muted)
                    Spacer(modifier = Modifier.height(10.dp))
                    val rows = favorites.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { prod ->
                                    val qtyOnTable = (tbl.it[prod.id.toString()] ?: 0.0).toInt()
                                    Box(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            onClick = { viewModel.adjustTableItem(tableNumber, prod.id, 1.0) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = tokens.background,
                                            border = BorderStroke(1.dp, tokens.border),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .testTag("quick_prod_${prod.id}")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = prod.n,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = BizzlyCalculator.formatFcfa(prod.pv),
                                                    fontSize = 11.5.sp,
                                                    color = tokens.accentReadable
                                                )
                                            }
                                        }
                                        if (qtyOnTable > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(tokens.accent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = qtyOnTable.toString(),
                                                    color = tokens.onAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Full catalog add dropdown
            if (data.prod.isNotEmpty()) {
                BizzlyCard {
                    Text("Ajouter un produit", fontSize = 13.sp, color = tokens.muted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            val selectedProd = data.prod.firstOrNull { it.id == selectedAddPid } ?: data.prod.first()
                            OutlinedButton(
                                onClick = { productPickerExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("table_catalog_dropdown")
                            ) {
                                Text(
                                    text = "${selectedProd.n} · ${BizzlyCalculator.formatFcfa(selectedProd.pv)}",
                                    color = tokens.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            DropdownMenu(
                                expanded = productPickerExpanded,
                                onDismissRequest = { productPickerExpanded = false }
                            ) {
                                data.prod.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text("${prod.n} · ${BizzlyCalculator.formatFcfa(prod.pv)}") },
                                        onClick = {
                                            selectedAddPid = prod.id
                                            productPickerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                selectedAddPid?.let { viewModel.adjustTableItem(tableNumber, it, 1.0) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.primary,
                                contentColor = tokens.onPrimary
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("table_catalog_add_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Ajouter")
                        }
                    }
                }
            }

            // Current order items
            if (tbl.it.isNotEmpty()) {
                BizzlyCard {
                    Text("Commande en cours", fontSize = 13.sp, color = tokens.muted)
                    Spacer(modifier = Modifier.height(6.dp))
                    val entries = tbl.it.entries.toList()
                    entries.forEachIndexed { idx, (pidStr, qty) ->
                        val prod = BizzlyCalculator.productByIdStr(data, pidStr)
                        if (prod != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.n, fontWeight = FontWeight.SemiBold, color = tokens.text)
                                    Text(
                                        text = BizzlyCalculator.formatFcfa(qty * prod.pv),
                                        fontSize = 12.5.sp,
                                        color = tokens.muted
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.adjustTableItem(tableNumber, prod.id, -1.0) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tokens.background)
                                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                        .testTag("table_minus_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Moins", tint = tokens.accentReadable)
                                }
                                Text(
                                    text = BizzlyCalculator.formatQty(qty),
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.text,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(32.dp)
                                )
                                IconButton(
                                    onClick = { viewModel.adjustTableItem(tableNumber, prod.id, 1.0) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tokens.background)
                                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                        .testTag("table_plus_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", tint = tokens.accentReadable)
                                }
                            }
                            if (idx < entries.lastIndex) HorizontalDivider(color = tokens.border)
                        }
                    }
                    HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total table", fontWeight = FontWeight.Bold, color = tokens.text)
                        Text(
                            text = BizzlyCalculator.formatFcfa(total),
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = tokens.accentReadable
                        )
                    }
                }

                // Payment mode & cash calculator
                BizzlyCard {
                    Text("Mode de paiement", fontSize = 13.sp, color = tokens.muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PAYMENT_MODES.forEach { mode ->
                            val selected = state.paymentMode == mode
                            Surface(
                                onClick = { viewModel.setPaymentMode(mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) tokens.primary else tokens.background,
                                border = BorderStroke(1.dp, if (selected) tokens.primary else tokens.border),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("pay_mode_$mode")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode,
                                        color = if (selected) tokens.onPrimary else tokens.text,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    if (state.paymentMode == "Espèces") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.cashGivenText,
                            onValueChange = viewModel::setCashGiven,
                            label = { Text("Montant donné par le client (F)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cash_given_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QUICK_CASH_AMOUNTS.forEach { amt ->
                                OutlinedButton(
                                    onClick = { viewModel.setCashGiven(amt.toString()) },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("${amt / 1000}k", color = tokens.accentReadable, fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.setCashGiven(total.roundToLong().toString()) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Exact", color = tokens.accentReadable, fontWeight = FontWeight.Bold)
                            }
                        }

                        val givenVal = state.cashGivenText.toDoubleOrNull() ?: 0.0
                        if (givenVal > 0.0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val ok = givenVal >= total
                            val boxBg = (if (ok) tokens.ok else tokens.ko).copy(alpha = 0.14f)
                            val boxBorder = if (ok) tokens.ok else tokens.ko
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(boxBg)
                                    .border(2.dp, boxBorder, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (ok) "À RENDRE AU CLIENT" else "IL MANQUE",
                                    fontSize = 12.5.sp,
                                    color = tokens.muted
                                )
                                Text(
                                    text = BizzlyCalculator.formatFcfa(abs(givenVal - total)),
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp,
                                    color = boxBorder
                                )
                            }
                        }
                    }
                }

                // Monospace thermal receipt preview ("paper")
                val paperBg = Color(0xFFF8FAFC)
                val paperInk = Color(0xFF0A1F44)
                val acc = state.activeAccount
                val givenVal = if (state.paymentMode == "Espèces") state.cashGivenText.toDoubleOrNull() ?: 0.0 else 0.0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(paperBg)
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (acc?.logo?.isNotBlank() == true) {
                            BusinessAvatarBox(account = acc, size = 48.dp)
                        } else {
                            Text(
                                text = acc?.name ?: "Bizzly",
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = paperInk
                            )
                        }
                    }
                    if (!acc?.addr.isNullOrBlank()) {
                        Text(acc!!.addr, fontSize = 11.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    if (!acc?.tel.isNullOrBlank()) {
                        Text("Tél : ${acc!!.tel}", fontSize = 11.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("FACTURE · Table $tableNumber", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = paperInk, fontSize = 12.5.sp)
                    Text("Serveur : ${BizzlyCalculator.serverName(data, tbl.sv)}", fontFamily = FontFamily.Monospace, color = Color(0xFF64748B), fontSize = 11.5.sp)
                    HorizontalDivider(color = Color(0xFF94A3B8), modifier = Modifier.padding(vertical = 4.dp))
                    tbl.it.forEach { (pidStr, q) ->
                        val p = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@forEach
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${BizzlyCalculator.formatQty(q)} x ${p.n}", fontFamily = FontFamily.Monospace, color = paperInk, fontSize = 12.5.sp)
                            Text(BizzlyCalculator.formatFcfa(q * p.pv), fontFamily = FontFamily.Monospace, color = paperInk, fontSize = 12.5.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF94A3B8), modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = paperInk, fontSize = 14.sp)
                        Text(BizzlyCalculator.formatFcfa(total), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = paperInk, fontSize = 14.sp)
                    }
                    Text("Paiement : ${state.paymentMode}", fontFamily = FontFamily.Monospace, color = paperInk, fontSize = 12.sp)
                    if (givenVal > 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Donné", fontFamily = FontFamily.Monospace, color = paperInk, fontSize = 12.sp)
                            Text(BizzlyCalculator.formatFcfa(givenVal), fontFamily = FontFamily.Monospace, color = paperInk, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (givenVal >= total) "RENDU" else "IL MANQUE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = paperInk, fontSize = 12.5.sp)
                            Text(BizzlyCalculator.formatFcfa(abs(givenVal - total)), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = paperInk, fontSize = 12.5.sp)
                        }
                    }
                }

                // Actions
                Button(
                    onClick = { viewModel.payTable(tableNumber) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("table_pay_btn")
                ) {
                    Text("Encaisser la table (${BizzlyCalculator.formatFcfa(total)})", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.shareTableInvoicePdf(tableNumber) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("table_pdf_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = tokens.text)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Facture PDF", color = tokens.text)
                }

                OutlinedButton(
                    onClick = { viewModel.freeTable(tableNumber) },
                    border = BorderStroke(1.dp, tokens.ko),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("table_free_btn")
                ) {
                    Text("Libérer la table sans encaisser", color = tokens.ko)
                }
            } else {
                BizzlyCard {
                    Text(
                        text = "Table libre. Ajoutez des produits ci-dessus pour ouvrir la commande.",
                        fontSize = 13.5.sp,
                        color = tokens.muted
                    )
                }
            }

            BizzlySignatureFooter()
        }
    }
}
