package com.example.bizzly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.RestockOrderLine
import com.example.bizzly.ui.components.bringIntoViewOnIme
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily

@Composable
fun InventoryItemEditorDialog(
    initial: InventoryItemEntity?,
    defaultThreshold: Int,
    onSave: (
        id: Long?,
        name: String,
        sku: String,
        category: String,
        unit: String,
        quantity: Double,
        minThreshold: Double,
        unitPrice: Double,
        notes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val focusManager = LocalFocusManager.current

    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category.orEmpty()) }
    var unit by remember(initial) { mutableStateOf(initial?.unit?.ifBlank { "unité" } ?: "unité") }
    var sku by remember(initial) { mutableStateOf(initial?.sku.orEmpty()) }
    var qtyText by remember(initial) {
        mutableStateOf(initial?.let { BizzlyCalculator.formatQty(it.quantity) } ?: "0")
    }
    var thresholdText by remember(initial) {
        mutableStateOf(
            initial?.let { BizzlyCalculator.formatQty(it.minThreshold) }
                ?: defaultThreshold.coerceAtLeast(1).toString()
        )
    }
    var priceText by remember(initial) {
        mutableStateOf(
            initial?.unitPrice?.takeIf { it > 0.0 }?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = if (initial == null) "Nouvel article en stock" else "Modifier l’article",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de l’article *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("inv_editor_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Catégorie") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_category_input")
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unité") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(0.85f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_unit_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Quantité *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_qty_input")
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Seuil d’alerte *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_threshold_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Prix unitaire (F)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_price_input")
                    )
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("Réf / SKU") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .weight(0.9f)
                            .bringIntoViewOnIme()
                            .testTag("inv_editor_sku_input")
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (fournisseur, casier…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("inv_editor_notes_input")
                )
                if (errorText.isNotBlank()) {
                    Text(errorText, color = tokens.ko, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val qty = qtyText.toDoubleOrNull()
                    val thresh = thresholdText.toDoubleOrNull()
                    if (cleanName.isEmpty()) {
                        errorText = "Entrez un nom d’article."
                        return@Button
                    }
                    if (qty == null || qty < 0.0) {
                        errorText = "Quantité invalide."
                        return@Button
                    }
                    if (thresh == null || thresh < 0.0) {
                        errorText = "Seuil invalide."
                        return@Button
                    }
                    onSave(
                        initial?.id,
                        cleanName,
                        sku.trim(),
                        category.trim(),
                        unit.trim().ifBlank { "unité" },
                        qty,
                        thresh,
                        priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        notes.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier.testTag("inv_editor_save_btn")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = tokens.muted)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryQuickQtyDialog(
    item: InventoryItemEntity,
    onApplyExact: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var qtyText by remember(item.id, item.quantity) {
        mutableStateOf(BizzlyCalculator.formatQty(item.quantity))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Seuil d’alerte : ${BizzlyCalculator.formatQty(item.minThreshold)} ${item.unit}",
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Quantité (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("inv_quick_qty_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-5, -1, 1, 5, 10).forEach { delta ->
                        OutlinedButton(
                            onClick = {
                                val cur = qtyText.toDoubleOrNull() ?: 0.0
                                qtyText = BizzlyCalculator.formatQty((cur + delta).coerceAtLeast(0.0))
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                color = if (delta < 0) tokens.ko else tokens.accentReadable,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = qtyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onApplyExact(parsed)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier.testTag("inv_quick_qty_apply_btn")
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = tokens.muted)
            }
        }
    )
}

@Composable
fun RestockOrderDialog(
    lowStockItems: List<InventoryItemEntity>,
    onExportPdf: (supplierName: String, lines: List<RestockOrderLine>) -> Unit,
    onExportExcel: (supplierName: String, lines: List<RestockOrderLine>) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var supplierName by remember { mutableStateOf("") }
    val qtyInputs = remember(lowStockItems) {
        mutableStateMapOf<Long, String>().apply {
            lowStockItems.forEach { item ->
                val sug = BizzlyCalculator.suggestedRestockQty(item.quantity, item.minThreshold)
                put(item.id, BizzlyCalculator.formatQty(sug))
            }
        }
    }

    val currentLines = lowStockItems.mapNotNull { item ->
        val qty = qtyInputs[item.id]?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        if (qty <= 0.0) null else RestockOrderLine(
            itemId = item.id,
            name = item.name,
            sku = item.sku,
            category = item.category,
            unit = item.unit,
            currentQty = item.quantity,
            minThreshold = item.minThreshold,
            suggestedQty = qty,
            unitPrice = item.unitPrice
        )
    }
    val estTotal = currentLines.sumOf { (it.suggestedQty * it.unitPrice).coerceAtLeast(0.0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = "Bon de commande fournisseur",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Objectif conseillé : 2× le seuil minimal. Ajustez les quantités avant export.",
                    fontSize = 12.5.sp,
                    color = tokens.muted
                )
                OutlinedTextField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    label = { Text("Nom du fournisseur (optionnel)") },
                    placeholder = { Text("Ex. Brasserie / Grossiste…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("restock_supplier_input")
                )

                HorizontalDivider(color = tokens.border)

                lowStockItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, color = tokens.text, fontSize = 13.5.sp)
                            Text(
                                text = "Stock ${BizzlyCalculator.formatQty(item.quantity)} / seuil ${BizzlyCalculator.formatQty(item.minThreshold)} ${item.unit}",
                                fontSize = 11.5.sp,
                                color = if (item.quantity <= 0.0) tokens.ko else tokens.warn
                            )
                        }
                        OutlinedTextField(
                            value = qtyInputs[item.id] ?: "0",
                            onValueChange = { raw ->
                                qtyInputs[item.id] = raw.filter { ch -> ch.isDigit() || ch == '.' }
                            },
                            label = { Text("Qté", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .width(86.dp)
                                .bringIntoViewOnIme()
                                .testTag("restock_qty_${item.id}")
                        )
                    }
                }

                if (estTotal > 0.0) {
                    HorizontalDivider(color = tokens.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estimation (${currentLines.size} lignes)", fontSize = 13.sp, color = tokens.muted)
                        Text(
                            text = BizzlyCalculator.formatFcfa(estTotal),
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = tokens.accentReadable
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onExportExcel(supplierName.trim(), currentLines) },
                    enabled = currentLines.isNotEmpty(),
                    modifier = Modifier.testTag("restock_export_excel_btn")
                ) {
                    Text("Excel", color = tokens.text)
                }
                Button(
                    onClick = { onExportPdf(supplierName.trim(), currentLines) },
                    enabled = currentLines.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier.testTag("restock_export_pdf_btn")
                ) {
                    Text("PDF")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = tokens.muted)
            }
        }
    )
}

