package com.example.bizzly.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BluetoothPrinterDevice
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PAYMENT_MODES
import com.example.bizzly.model.PaymentPart
import com.example.bizzly.model.PrinterPaperWidth
import com.example.bizzly.model.SINGLE_PAYMENT_MODES
import com.example.bizzly.ui.components.bringIntoViewOnIme
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.util.EscPosPrinter
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixedPaymentEditor(
    totalDue: Double,
    mode1: String,
    amount1Text: String,
    mode2: String,
    onMode1Change: (String) -> Unit,
    onAmount1Change: (String) -> Unit,
    onMode2Change: (String) -> Unit,
    cashGivenText: String,
    onCashGivenChange: (String) -> Unit,
    testTagPrefix: String = "mixed"
) {
    val tokens = LocalBizzlyTokens.current
    val amt1 = amount1Text.toDoubleOrNull()?.coerceIn(0.0, totalDue) ?: 0.0
    val amt2 = (totalDue - amt1).coerceAtLeast(0.0)
    val cashLegDue = (if (mode1 == "Espèces") amt1 else 0.0) + (if (mode2 == "Espèces") amt2 else 0.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.background)
            .border(1.dp, tokens.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "1er règlement",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.muted
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SINGLE_PAYMENT_MODES.forEach { m ->
                val selected = mode1 == m
                FilterChip(
                    selected = selected,
                    onClick = { onMode1Change(m) },
                    label = { Text(m, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.primary,
                        selectedLabelColor = tokens.onPrimary
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_m1_$m")
                )
            }
        }
        OutlinedTextField(
            value = amount1Text,
            onValueChange = { onAmount1Change(it.filter { ch -> ch.isDigit() }) },
            label = { Text("Montant 1er règlement (F)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnIme()
                .testTag("${testTagPrefix}_amt1_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0.25, 0.5, 0.75).forEach { frac ->
                val label = "${(frac * 100).toInt()}%"
                OutlinedButton(
                    onClick = {
                        val valAmt = (totalDue * frac).roundToLong().coerceIn(1L, (totalDue.roundToLong() - 1L).coerceAtLeast(1L))
                        onAmount1Change(valAmt.toString())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(label, fontSize = 12.sp, color = tokens.accentReadable, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = tokens.border)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "2e règlement (solde)",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.muted
            )
            Text(
                text = BizzlyCalculator.formatFcfa(amt2),
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = tokens.accentReadable
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SINGLE_PAYMENT_MODES.forEach { m ->
                val selected = mode2 == m
                FilterChip(
                    selected = selected,
                    onClick = { onMode2Change(m) },
                    label = { Text(m, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tokens.primary,
                        selectedLabelColor = tokens.onPrimary
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_m2_$m")
                )
            }
        }

        if (cashLegDue > 0.0 && amt1 > 0.0 && amt1 < totalDue) {
            OutlinedTextField(
                value = cashGivenText,
                onValueChange = { onCashGivenChange(it.filter { ch -> ch.isDigit() }) },
                label = { Text("Espèces reçues (pour ${BizzlyCalculator.formatFcfa(cashLegDue)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnIme()
                    .testTag("${testTagPrefix}_cash_given_input")
            )
            val given = cashGivenText.toDoubleOrNull() ?: 0.0
            if (given > 0.0) {
                val ok = given >= cashLegDue
                Text(
                    text = if (ok) {
                        "Monnaie à rendre : ${BizzlyCalculator.formatFcfa(given - cashLegDue)}"
                    } else {
                        "Il manque : ${BizzlyCalculator.formatFcfa(cashLegDue - given)}"
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) tokens.ok else tokens.ko
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableDiscountDialog(
    tableNumber: Int,
    data: BusinessData,
    table: OpenTable,
    onApply: (discountType: String, discountValue: Double, reason: String, compedMap: Map<String, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var discountType by remember(tableNumber, table.discountType) {
        mutableStateOf(if (table.discountType == "percent" || table.discountType == "amount") table.discountType else "none")
    }
    var discountValueText by remember(tableNumber, table.discountValue) {
        mutableStateOf(
            if (table.discountValue > 0.0) BizzlyCalculator.formatQty(table.discountValue) else ""
        )
    }
    var reason by remember(tableNumber, table.discountReason) {
        mutableStateOf(table.discountReason)
    }
    val compedState = remember(tableNumber, table.comped) {
        mutableStateMapOf<String, Double>().apply {
            putAll(table.comped)
        }
    }
    var errorMsg by remember { mutableStateOf("") }

    val parsedDiscount = discountValueText.toDoubleOrNull() ?: 0.0
    val previewTable = table.copy(
        comped = compedState.toMap(),
        discountType = discountType,
        discountValue = parsedDiscount,
        discountReason = reason
    )
    val previewBreakdown = BizzlyCalculator.calculateTableBreakdown(data, previewTable)
    val hasReduction = (discountType != "none" && parsedDiscount > 0.0) || compedState.values.any { it > 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = "Remise & Offerts · Table $tableNumber",
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
                    text = "Validation par PIN Gérant requise. Chaque remise est tracée dans le journal.",
                    fontSize = 12.sp,
                    color = tokens.muted
                )

                Text("Remise globale", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tokens.text)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("none" to "Aucune", "percent" to "Pourcentage (%)", "amount" to "Montant (F)").forEach { (key, label) ->
                        FilterChip(
                            selected = discountType == key,
                            onClick = {
                                discountType = key
                                if (key == "none") discountValueText = ""
                            },
                            label = { Text(label, fontSize = 12.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primary,
                                selectedLabelColor = tokens.onPrimary
                            ),
                            modifier = Modifier.testTag("disc_type_$key")
                        )
                    }
                }

                if (discountType != "none") {
                    OutlinedTextField(
                        value = discountValueText,
                        onValueChange = { discountValueText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = {
                            Text(if (discountType == "percent") "Pourcentage (%)" else "Montant de la remise (F)")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("disc_value_input")
                    )
                }

                HorizontalDivider(color = tokens.border)
                Text("Produits offerts", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tokens.text)
                table.it.forEach { (pidStr, qty) ->
                    val prod = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@forEach
                    val compQ = (compedState[pidStr] ?: 0.0).coerceIn(0.0, qty)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.n, fontWeight = FontWeight.Medium, color = tokens.text, fontSize = 13.5.sp)
                            Text(
                                text = "${BizzlyCalculator.formatQty(qty)} sur table · ${BizzlyCalculator.formatFcfa(prod.pv)}",
                                fontSize = 11.5.sp,
                                color = tokens.muted
                            )
                        }
                        IconButton(
                            onClick = {
                                val next = (compQ - 1.0).coerceAtLeast(0.0)
                                if (next > 0.0) compedState[pidStr] = next else compedState.remove(pidStr)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("disc_comp_minus_$pidStr")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Moins offert", tint = tokens.muted)
                        }
                        Text(
                            text = BizzlyCalculator.formatQty(compQ),
                            fontWeight = FontWeight.Bold,
                            color = if (compQ > 0.0) tokens.accentReadable else tokens.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(30.dp)
                        )
                        IconButton(
                            onClick = {
                                val next = (compQ + 1.0).coerceAtMost(qty)
                                if (next > 0.0) compedState[pidStr] = next
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("disc_comp_plus_$pidStr")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Offrir 1", tint = tokens.accentReadable)
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        errorMsg = ""
                    },
                    label = { Text(if (hasReduction) "Motif obligatoire *" else "Motif (optionnel)") },
                    placeholder = { Text("Ex. Geste commercial, erreur service, VIP…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("disc_reason_input")
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tokens.background,
                    border = BorderStroke(1.dp, tokens.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sous-total brut", fontSize = 12.sp, color = tokens.muted)
                            Text(BizzlyCalculator.formatFcfa(previewBreakdown.grossSubtotal), fontSize = 12.sp, color = tokens.text)
                        }
                        if (previewBreakdown.compedDiscount > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Offerts", fontSize = 12.sp, color = tokens.warn)
                                Text("-${BizzlyCalculator.formatFcfa(previewBreakdown.compedDiscount)}", fontSize = 12.sp, color = tokens.warn)
                            }
                        }
                        if (previewBreakdown.tableDiscount > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Remise", fontSize = 12.sp, color = tokens.warn)
                                Text("-${BizzlyCalculator.formatFcfa(previewBreakdown.tableDiscount)}", fontSize = 12.sp, color = tokens.warn)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reste à payer", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = tokens.text)
                            Text(
                                BizzlyCalculator.formatFcfa(previewBreakdown.remainingDue),
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = tokens.accentReadable
                            )
                        }
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = tokens.ko, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasReduction && reason.trim().isEmpty()) {
                        errorMsg = "Saisissez un motif pour la remise ou les produits offerts."
                        return@Button
                    }
                    onApply(discountType, parsedDiscount, reason.trim(), compedState.toMap())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier.testTag("disc_apply_btn")
            ) {
                Text("Valider (PIN Gérant)")
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
fun TableSplitBillDialog(
    tableNumber: Int,
    data: BusinessData,
    table: OpenTable,
    onPayEqual: (totalParts: Int, payments: List<PaymentPart>, cashGiven: Double) -> Unit,
    onPayByItems: (selectedItems: Map<String, Double>, payments: List<PaymentPart>, cashGiven: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val fullBreakdown = BizzlyCalculator.calculateTableBreakdown(data, table)
    val remaining = fullBreakdown.remainingDue

    var splitTab by remember { mutableIntStateOf(0) } // 0 = Parts égales, 1 = Par articles
    var totalParts by remember(table.splitTotalParts) {
        mutableIntStateOf(if (table.splitTotalParts >= 2) table.splitTotalParts else 2)
    }
    val selectedItemQtys = remember(tableNumber, table.it) {
        mutableStateMapOf<String, Double>()
    }

    var payMode by remember { mutableStateOf("Espèces") }
    var cashGivenText by remember { mutableStateOf("") }
    var mixedM1 by remember { mutableStateOf("Espèces") }
    var mixedAmt1Text by remember { mutableStateOf("") }
    var mixedM2 by remember { mutableStateOf("Mobile Money") }
    var errorMsg by remember { mutableStateOf("") }

    // Calculate amount to collect for current selection
    val equalPartsLocked = table.paidCount > 0 && table.splitTotalParts >= 2
    val safeTotalParts = (if (equalPartsLocked) table.splitTotalParts else totalParts).coerceIn(2, 20)
    val remainingParts = (safeTotalParts - table.paidCount).coerceAtLeast(1)
    val equalPartAmount = if (remainingParts <= 1) {
        remaining
    } else {
        (remaining / remainingParts).roundToLong().toDouble().coerceIn(1.0, remaining)
    }

    var selectedRaw = 0.0
    var selectedAny = false
    for ((pidStr, reqQ) in selectedItemQtys) {
        val availQ = table.it[pidStr] ?: 0.0
        val takeQ = reqQ.coerceIn(0.0, availQ)
        if (takeQ > 0.0) {
            selectedAny = true
            val p = BizzlyCalculator.productByIdStr(data, pidStr) ?: continue
            val compAvail = (table.comped[pidStr] ?: 0.0).coerceIn(0.0, availQ)
            val paidShare = ((availQ - compAvail) / availQ.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
            selectedRaw += takeQ * paidShare * p.pv
        }
    }
    val remainingItemsQtyAfter = table.it.entries.sumOf { (pidStr, q) ->
        (q - (selectedItemQtys[pidStr] ?: 0.0)).coerceAtLeast(0.0)
    }
    val itemsClosesTable = selectedAny && remainingItemsQtyAfter <= 0.0001
    val byItemsAmount = when {
        !selectedAny -> 0.0
        itemsClosesTable -> remaining
        else -> {
            val ratio = if (fullBreakdown.afterComped > 0.0) {
                (selectedRaw / fullBreakdown.afterComped).coerceIn(0.0, 1.0)
            } else 1.0
            (remaining * ratio).roundToLong().toDouble().coerceAtMost(remaining)
        }
    }

    val amountToPay = if (splitTab == 0) equalPartAmount else byItemsAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = "Partage d’addition · Table $tableNumber",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = splitTab == 0,
                        onClick = {
                            splitTab = 0
                            errorMsg = ""
                        },
                        label = { Text("Parts égales", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tokens.primary,
                            selectedLabelColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("split_tab_equal")
                    )
                    FilterChip(
                        selected = splitTab == 1,
                        onClick = {
                            splitTab = 1
                            errorMsg = ""
                        },
                        label = { Text("Par articles", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tokens.primary,
                            selectedLabelColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("split_tab_items")
                    )
                }

                if (splitTab == 0) {
                    if (table.paidCount > 0) {
                        Text(
                            text = "Part ${table.paidCount + 1} / $safeTotalParts · Déjà réglé : ${BizzlyCalculator.formatFcfa(table.paidPartial)} · Reste : ${BizzlyCalculator.formatFcfa(remaining)}",
                            fontSize = 12.5.sp,
                            color = tokens.accentReadable,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text("Nombre de convives / parts", fontSize = 13.sp, color = tokens.muted)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { totalParts = (totalParts - 1).coerceAtLeast(2) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.background)
                                    .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                    .testTag("split_parts_minus")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Moins de parts", tint = tokens.text)
                            }
                            Text(
                                text = "$totalParts parts",
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = tokens.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { totalParts = (totalParts + 1).coerceAtMost(20) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.background)
                                    .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                                    .testTag("split_parts_plus")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus de parts", tint = tokens.text)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Articles à encaisser", fontSize = 13.sp, color = tokens.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                table.it.forEach { (pidStr, q) -> selectedItemQtys[pidStr] = q }
                            }) {
                                Text("Tout", fontSize = 12.sp)
                            }
                            TextButton(onClick = { selectedItemQtys.clear() }) {
                                Text("Rien", fontSize = 12.sp)
                            }
                        }
                    }
                    table.it.forEach { (pidStr, availQ) ->
                        val prod = BizzlyCalculator.productByIdStr(data, pidStr) ?: return@forEach
                        val selQ = (selectedItemQtys[pidStr] ?: 0.0).coerceIn(0.0, availQ)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.n, fontWeight = FontWeight.Medium, color = tokens.text, fontSize = 13.5.sp)
                                Text(
                                    text = "Dispo ${BizzlyCalculator.formatQty(availQ)} · ${BizzlyCalculator.formatFcfa(prod.pv)}",
                                    fontSize = 11.5.sp,
                                    color = tokens.muted
                                )
                            }
                            IconButton(
                                onClick = {
                                    val next = (selQ - 1.0).coerceAtLeast(0.0)
                                    if (next > 0.0) selectedItemQtys[pidStr] = next else selectedItemQtys.remove(pidStr)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("split_item_minus_$pidStr")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Retirer", tint = tokens.muted)
                            }
                            Text(
                                text = BizzlyCalculator.formatQty(selQ),
                                fontWeight = FontWeight.Bold,
                                color = if (selQ > 0.0) tokens.accentReadable else tokens.text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(28.dp)
                            )
                            IconButton(
                                onClick = {
                                    val next = (selQ + 1.0).coerceAtMost(availQ)
                                    if (next > 0.0) selectedItemQtys[pidStr] = next
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("split_item_plus_$pidStr")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Sélectionner", tint = tokens.accentReadable)
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.border)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Montant de ce règlement", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tokens.text)
                    Text(
                        text = BizzlyCalculator.formatFcfa(amountToPay),
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = tokens.accentReadable
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PAYMENT_MODES.forEach { mode ->
                        val selected = payMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                payMode = mode
                                cashGivenText = ""
                                mixedAmt1Text = ""
                                errorMsg = ""
                            },
                            label = { Text(mode, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primary,
                                selectedLabelColor = tokens.onPrimary
                            ),
                            modifier = Modifier.testTag("split_mode_$mode")
                        )
                    }
                }

                if (payMode == "Mixte" && amountToPay > 0.0) {
                    MixedPaymentEditor(
                        totalDue = amountToPay,
                        mode1 = mixedM1,
                        amount1Text = mixedAmt1Text,
                        mode2 = mixedM2,
                        onMode1Change = { m ->
                            if (mixedM2 == m) mixedM2 = if (m == "Espèces") "Mobile Money" else "Espèces"
                            mixedM1 = m
                            cashGivenText = ""
                        },
                        onAmount1Change = { mixedAmt1Text = it },
                        onMode2Change = { m ->
                            if (mixedM1 == m) mixedM1 = if (m == "Espèces") "Mobile Money" else "Espèces"
                            mixedM2 = m
                            cashGivenText = ""
                        },
                        cashGivenText = cashGivenText,
                        onCashGivenChange = { cashGivenText = it },
                        testTagPrefix = "split_mixed"
                    )
                } else if (payMode == "Espèces" && amountToPay > 0.0) {
                    OutlinedTextField(
                        value = cashGivenText,
                        onValueChange = { cashGivenText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Espèces reçues (optionnel)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("split_cash_input")
                    )
                    val given = cashGivenText.toDoubleOrNull() ?: 0.0
                    if (given > 0.0) {
                        val ok = given >= amountToPay
                        Text(
                            text = if (ok) {
                                "À rendre : ${BizzlyCalculator.formatFcfa(given - amountToPay)}"
                            } else {
                                "Il manque : ${BizzlyCalculator.formatFcfa(abs(given - amountToPay))}"
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ok) tokens.ok else tokens.ko
                        )
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = tokens.ko, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (splitTab == 1 && !selectedAny) {
                        errorMsg = "Sélectionnez au moins un article à encaisser."
                        return@Button
                    }
                    if (amountToPay <= 0.0 && !itemsClosesTable) {
                        errorMsg = "Montant nul."
                        return@Button
                    }
                    val payments = if (payMode == "Mixte") {
                        val amt1 = mixedAmt1Text.toDoubleOrNull() ?: 0.0
                        if (amt1 <= 0.0 || amt1 >= amountToPay) {
                            errorMsg = "En mode Mixte, le 1er montant doit être entre 1 F et ${BizzlyCalculator.formatFcfa(amountToPay - 1)}."
                            return@Button
                        }
                        listOf(
                            PaymentPart(mixedM1, amt1),
                            PaymentPart(mixedM2, (amountToPay - amt1).coerceAtLeast(0.0))
                        )
                    } else {
                        listOf(PaymentPart(payMode, amountToPay))
                    }
                    val cashLeg = payments.filter { it.mode == "Espèces" }.sumOf { it.amount }
                    val given = if (cashLeg > 0.0) cashGivenText.toDoubleOrNull() ?: 0.0 else 0.0
                    if (given > 0.0 && given < cashLeg) {
                        errorMsg = "Montant en espèces insuffisant."
                        return@Button
                    }

                    if (splitTab == 0) {
                        onPayEqual(safeTotalParts, payments, given)
                    } else {
                        onPayByItems(selectedItemQtys.toMap(), payments, given)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier.testTag("split_confirm_btn")
            ) {
                Text("Encaisser ${BizzlyCalculator.formatFcfa(amountToPay)}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = tokens.muted)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterPickerDialog(
    selectedAddress: String?,
    selectedName: String?,
    paperWidthMm: Int,
    pairedPrinters: List<BluetoothPrinterDevice>,
    printerBusy: Boolean,
    onRefresh: () -> Unit,
    onSelectPrinter: (BluetoothPrinterDevice) -> Unit,
    onClearPrinter: () -> Unit,
    onSelectWidthMm: (Int) -> Unit,
    onTestPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    var hasBtPerm by remember { mutableStateOf(EscPosPrinter.hasBluetoothPermission(context)) }

    val btPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBtPerm = EscPosPrinter.hasBluetoothPermission(context)
        if (hasBtPerm) onRefresh()
    }

    LaunchedEffect(hasBtPerm) {
        if (hasBtPerm) onRefresh()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = tokens.accentReadable)
                Text(
                    text = "Imprimante thermique Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.text
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Format du papier (ESC/POS)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrinterPaperWidth.entries.forEach { width ->
                        FilterChip(
                            selected = paperWidthMm == width.mm,
                            onClick = { onSelectWidthMm(width.mm) },
                            label = { Text(width.label, fontSize = 12.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primary,
                                selectedLabelColor = tokens.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("printer_width_${width.mm}")
                        )
                    }
                }

                HorizontalDivider(color = tokens.border)

                if (!hasBtPerm) {
                    Text(
                        text = "Autorisez la connexion Bluetooth pour détecter les imprimantes thermiques jumelées sur ce téléphone.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                    Button(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH,
                                    Manifest.permission.BLUETOOTH_ADMIN
                                )
                            }
                            btPermLauncher.launch(perms)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("printer_grant_bt_btn")
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autoriser Bluetooth")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Appareils jumelés", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Actualiser")
                        }
                    }

                    if (!EscPosPrinter.isBluetoothEnabled(context)) {
                        Text(
                            text = "Le Bluetooth est désactivé sur ce téléphone.",
                            fontSize = 12.5.sp,
                            color = tokens.warn
                        )
                    }

                    if (pairedPrinters.isEmpty()) {
                        Text(
                            text = "Aucune imprimante jumelée trouvée. Associez d’abord l’imprimante dans les paramètres Bluetooth Android.",
                            fontSize = 12.5.sp,
                            color = tokens.muted
                        )
                    } else {
                        pairedPrinters.forEach { dev ->
                            val isSelected = selectedAddress == dev.address
                            Surface(
                                onClick = { onSelectPrinter(dev) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) tokens.tint else tokens.background,
                                border = BorderStroke(1.dp, if (isSelected) tokens.primary else tokens.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("printer_dev_${dev.address}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(dev.address, fontSize = 11.5.sp, color = tokens.muted)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Sélectionnée", tint = tokens.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (!selectedAddress.isNullOrBlank()) {
                        HorizontalDivider(color = tokens.border)
                        Text(
                            text = "Sélectionnée : ${selectedName ?: selectedAddress}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.accentReadable
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onTestPrint,
                                enabled = !printerBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.primary,
                                    contentColor = tokens.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("printer_test_btn")
                            ) {
                                Text(if (printerBusy) "Impression…" else "Tester")
                            }
                            OutlinedButton(
                                onClick = onClearPrinter,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("printer_clear_btn")
                            ) {
                                Text("Retirer", color = tokens.ko)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = tokens.accentReadable, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
