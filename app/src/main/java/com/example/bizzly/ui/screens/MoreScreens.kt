package com.example.bizzly.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.model.SyncStatus
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.BrandColorSwatch
import com.example.bizzly.ui.components.BusinessAvatarBox
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import com.example.bizzly.util.ExportUtils
import kotlin.math.max

@Composable
fun MoreMenuScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Plus d’options",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (viewModel.canTab(BizzlyTab.PROD)) {
                MoreOptionButton(Icons.Default.LocalOffer, "Produits et prix", "more_prod_btn") {
                    viewModel.selectTab(BizzlyTab.PROD)
                }
            }
            if (viewModel.canTab(BizzlyTab.SRV)) {
                MoreOptionButton(Icons.Default.Group, "Entreprise, profils et accès", "more_srv_btn") {
                    viewModel.selectTab(BizzlyTab.SRV)
                }
            }
            if (viewModel.canTab(BizzlyTab.QR)) {
                MoreOptionButton(Icons.Default.QrCode2, "QR codes et menu des tables", "more_qr_btn") {
                    viewModel.selectTab(BizzlyTab.QR)
                }
            }
            if (viewModel.canTab(BizzlyTab.HIST)) {
                MoreOptionButton(Icons.Default.Assessment, "Bilan, journal et sauvegarde", "more_hist_btn") {
                    viewModel.selectTab(BizzlyTab.HIST)
                }
            }

            MoreOptionButton(Icons.Default.Lock, "Verrouiller / changer de profil", "more_lock_btn") {
                viewModel.lockNow()
            }

            if (state.syncCreds != null && state.syncDead) {
                Button(
                    onClick = viewModel::openRekeyDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("more_rekey_btn")
                ) {
                    Text("Relier avec un nouveau code", fontWeight = FontWeight.SemiBold)
                }
            }

            MoreOptionButton(Icons.Default.Business, "Changer d’entreprise", "more_switch_biz_btn") {
                viewModel.switchBusiness()
            }

            BizzlySignatureFooter()
        }
    }

    if (state.rekeyDialogVisible) {
        RekeyDialog(state = state, viewModel = viewModel)
    }
}

@Composable
private fun MoreOptionButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tokens.border),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tokens.accentReadable)
            Text(label, color = tokens.text, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ProductsScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Produits et prix de vente",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.text)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizzlyCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouveau produit") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("prod_new_name_input")
                    )
                    OutlinedTextField(
                        value = newPrice,
                        onValueChange = { newPrice = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Prix de vente") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("prod_new_price_input")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.addProduct(newName, newPrice)
                        if (newName.trim().isNotEmpty()) {
                            newName = ""
                            newPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("prod_add_btn")
                ) {
                    Text("Ajouter le produit", fontWeight = FontWeight.SemiBold)
                }
            }

            if (data.prod.isEmpty()) {
                BizzlyCard {
                    Text("Votre catalogue est vide", fontWeight = FontWeight.Bold, color = tokens.text)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ajoutez vos produits ci-dessus, ou chargez une liste d’exemple à modifier.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = viewModel::loadSampleCatalog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("prod_load_sample_btn")
                    ) {
                        Text("Charger une liste d’exemple", color = tokens.text)
                    }
                }
            } else {
                BizzlyCard {
                    data.prod.forEachIndexed { idx, prod ->
                        var priceText by remember(prod.id, prod.pv) {
                            mutableStateOf(if (prod.pv % 1.0 == 0.0) prod.pv.toLong().toString() else prod.pv.toString())
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = prod.n,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .width(100.dp)
                                    .onFocusChanged { focus ->
                                        if (!focus.isFocused && (priceText.toDoubleOrNull() ?: 0.0) != prod.pv) {
                                            viewModel.updateProductField(prod.id, "pv", priceText)
                                        }
                                    }
                                    .testTag("prod_price_${prod.id}")
                            )
                            IconButton(
                                onClick = { viewModel.deleteProduct(prod.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("prod_del_${prod.id}")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = tokens.ko)
                            }
                        }
                        if (idx < data.prod.lastIndex) HorizontalDivider(color = tokens.border)
                    }
                }
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
fun EnterpriseScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    val acc = state.activeAccount ?: return
    val data = state.businessData

    var nameText by remember(acc.id, acc.name) { mutableStateOf(acc.name) }
    var emailText by remember(acc.id, acc.email) { mutableStateOf(acc.email) }
    var telText by remember(acc.id, acc.tel) { mutableStateOf(acc.tel) }
    var addrText by remember(acc.id, acc.addr) { mutableStateOf(acc.addr) }
    var newProfName by remember { mutableStateOf("") }
    var newProfRole by remember { mutableStateOf(BizzlyRole.SERVEUR.key) }
    var newRoleExpanded by remember { mutableStateOf(false) }
    var tablesText by remember(data.nt) { mutableStateOf(data.nt.toString()) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.pickAccountLogo(uri)
    }

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Entreprise, profils et accès",
            account = acc,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.text)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Identity
            BizzlyCard {
                Text("Identité de l’entreprise", fontSize = 13.sp, color = tokens.muted)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BusinessAvatarBox(account = acc, size = 64.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ent_pick_logo_btn")
                        ) {
                            Text(if (acc.logo.isNotBlank()) "Changer le logo" else "Ajouter un logo", color = tokens.text)
                        }
                        if (acc.logo.isNotBlank()) {
                            OutlinedButton(
                                onClick = viewModel::removeAccountLogo,
                                border = BorderStroke(1.dp, tokens.ko),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Retirer le logo", color = tokens.ko)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && nameText != acc.name) viewModel.updateBusinessField("name", nameText) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emailText,
                    onValueChange = { emailText = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && emailText != acc.email) viewModel.updateBusinessField("email", emailText) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = telText,
                    onValueChange = { telText = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && telText != acc.tel) viewModel.updateBusinessField("tel", telText) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = addrText,
                    onValueChange = { addrText = it },
                    label = { Text("Adresse (affichée sur les factures)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && addrText != acc.addr) viewModel.updateBusinessField("addr", addrText) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Identifiant : ${acc.ident}", fontSize = 12.5.sp, color = tokens.muted)
            }

            // Colors
            BizzlyCard {
                Text("Couleurs", fontSize = 13.sp, color = tokens.muted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandColorSwatch(
                        label = "Principale",
                        hex = acc.brand.pr,
                        onColorSelected = { viewModel.updateBrandColor("pr", it) },
                        modifier = Modifier.weight(1f)
                    )
                    BrandColorSwatch(
                        label = "Fond foncé",
                        hex = acc.brand.sec,
                        onColorSelected = { viewModel.updateBrandColor("sec", it) },
                        modifier = Modifier.weight(1f)
                    )
                    BrandColorSwatch(
                        label = "Accent",
                        hex = acc.brand.ax,
                        onColorSelected = { viewModel.updateBrandColor("ax", it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::autoPaletteFromLogo,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Reprendre du logo", color = tokens.text, fontSize = 12.5.sp)
                    }
                    OutlinedButton(
                        onClick = viewModel::resetBrandToBizzly,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Couleurs Bizzly", color = tokens.text, fontSize = 12.5.sp)
                    }
                }
            }

            // Profiles & PINs
            SectionHeading("Profils et codes PIN")
            acc.profiles.forEach { p ->
                var profNameText by remember(p.id, p.name) { mutableStateOf(p.name) }
                var roleExpanded by remember { mutableStateOf(false) }
                val paidByServer = data.paid.filter { it.sv == p.id }
                val serverTotal = paidByServer.sumOf { it.tot }

                BizzlyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = profNameText,
                            onValueChange = { profNameText = it },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (!it.isFocused && profNameText != p.name) viewModel.renameProfile(p.id, profNameText) }
                        )
                        IconButton(
                            onClick = { viewModel.deleteProfile(p.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = tokens.ko)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { roleExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(BizzlyRole.fromKey(p.role).label, color = tokens.text)
                            }
                            DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                                BizzlyRole.entries.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role.label) },
                                        onClick = {
                                            roleExpanded = false
                                            viewModel.changeProfileRole(p.id, role.key)
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.promptSetProfilePin(p.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("prof_pin_btn_${p.id}")
                        ) {
                            Text(if (p.hash.isNotBlank()) "Changer le PIN" else "Définir le PIN", color = tokens.text)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val statusLine = (if (p.hash.isNotBlank()) "PIN défini" else "PIN manquant : ne peut pas se connecter") +
                        if (p.role == BizzlyRole.SERVEUR.key) {
                            " · ${paidByServer.size} tables encaissées · ${BizzlyCalculator.formatFcfa(serverTotal)}"
                        } else ""
                    Text(
                        text = statusLine,
                        fontSize = 12.5.sp,
                        color = if (p.hash.isBlank()) tokens.ko else tokens.muted
                    )
                }
            }

            // Add profile card
            BizzlyCard {
                Text("Ajouter un profil", fontSize = 13.sp, color = tokens.muted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newProfName,
                        onValueChange = { newProfName = it },
                        label = { Text("Prénom ou nom") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("ent_new_prof_name")
                    )
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedButton(
                            onClick = { newRoleExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(BizzlyRole.fromKey(newProfRole).label, color = tokens.text)
                        }
                        DropdownMenu(expanded = newRoleExpanded, onDismissRequest = { newRoleExpanded = false }) {
                            BizzlyRole.entries.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role.label) },
                                    onClick = {
                                        newRoleExpanded = false
                                        newProfRole = role.key
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.addProfile(newProfName, newProfRole)
                        if (newProfName.trim().isNotEmpty()) newProfName = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ent_add_prof_btn")
                ) {
                    Text("Ajouter", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Serveur : prend les commandes seulement. Caisse : encaisse, stock, dépenses, bilan. Gérant : tout, y compris les profils. Verrouillage automatique après 15 min d’inactivité.",
                    fontSize = 12.5.sp,
                    color = tokens.muted
                )
            }

            // Table count
            BizzlyCard {
                Text("Nombre de tables", fontSize = 13.sp, color = tokens.muted)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tablesText,
                        onValueChange = { tablesText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ent_tables_count_input")
                    )
                    Button(
                        onClick = { viewModel.updateTableCount(tablesText) },
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Enregistrer")
                    }
                }
            }

            // Cloud Sync card
            BizzlyCard {
                Text("Synchronisation entre téléphones", fontSize = 13.sp, color = tokens.muted)
                if (state.syncCreds == null) {
                    Text("Désactivée", fontWeight = FontWeight.Bold, color = tokens.text)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Les serveurs prennent les commandes sur leur téléphone, la caisse voit les tables en direct, et les données sont copiées en ligne (instantané chaque nuit pendant 14 jours).",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::enableCloudSync,
                        enabled = !state.syncBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sync_enable_btn")
                    ) {
                        Text("Activer la synchronisation", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    val lastTime = if (state.lastSyncMs > 0L) {
                        " · dernière synchro ${BizzlyCalculator.formatDateTimeFr(state.lastSyncMs).takeLast(5)}"
                    } else ""
                    Text(
                        text = "Activée · ${state.syncStatus.detailLabel}$lastTime",
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                    if (state.syncError.isNotBlank()) {
                        Text(state.syncError, color = tokens.ko, fontSize = 12.5.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::triggerManualSync,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_now_btn")
                        ) {
                            Text("Synchroniser", color = tokens.text)
                        }
                        OutlinedButton(
                            onClick = viewModel::showSyncCodeDialog,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sync_code_btn")
                        ) {
                            Text("Code de liaison", color = tokens.text)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::openCloudSnapshots,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Sauvegardes en ligne (14 jours)", color = tokens.text)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::rotateCloudSyncKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Changer le code (déconnecte les autres téléphones)", color = tokens.text, fontSize = 12.5.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::disableCloudSync,
                        border = BorderStroke(1.dp, tokens.ko),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Désactiver sur ce téléphone", color = tokens.ko)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sur chaque autre téléphone : écran de connexion > « Lier ce téléphone », puis le code. Gardez le code secret.",
                        fontSize = 12.sp,
                        color = tokens.muted
                    )
                }
                if (state.syncMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(state.syncMessage, color = tokens.ko, fontSize = 13.sp)
                }
            }

            OutlinedButton(
                onClick = viewModel::lockNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verrouiller maintenant", color = tokens.text)
            }

            OutlinedButton(
                onClick = viewModel::removeBusinessFromDevice,
                border = BorderStroke(1.dp, tokens.ko),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Retirer cette entreprise de l’appareil", color = tokens.ko)
            }

            BizzlySignatureFooter()
        }
    }

    if (state.syncCodeDialogVisible) {
        val codeFormatted = viewModel.formattedSyncKey().orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissSyncCodeDialog,
            containerColor = tokens.card,
            title = { Text("Code de liaison", style = MaterialTheme.typography.titleMedium, color = tokens.text) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sur l’autre téléphone : « Lier ce téléphone », puis saisissez ce code.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = codeFormatted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = tokens.text,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                ExportUtils.shareText(
                                    context,
                                    "Code Bizzly pour ${acc.name} : $codeFormatted",
                                    "Partager le code"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Partager")
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Code Bizzly", codeFormatted))
                                viewModel.showToast("Code copié.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copier", color = tokens.text)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSyncCodeDialog) {
                    Text("Fermer", color = tokens.text)
                }
            }
        )
    }

    if (state.snapshotsDialog != null) {
        val snaps = state.snapshotsDialog
        AlertDialog(
            onDismissRequest = viewModel::dismissSnapshotsDialog,
            containerColor = tokens.card,
            title = { Text("Sauvegardes en ligne", style = MaterialTheme.typography.titleMedium, color = tokens.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Une copie complète est prise chaque nuit (14 jours conservés). Restaurer remplace les données de tous les téléphones liés.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                    if (snaps.isEmpty()) {
                        Text(
                            text = "Aucune copie pour l’instant : la première est prise cette nuit.",
                            fontSize = 13.sp,
                            color = tokens.muted
                        )
                    } else {
                        snaps.forEach { snap ->
                            OutlinedButton(
                                onClick = { viewModel.restoreCloudSnapshot(snap.day) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${BizzlyCalculator.formatDateFr(snap.day)} · ${snap.n} éléments",
                                    color = tokens.text
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSnapshotsDialog) {
                    Text("Fermer", color = tokens.text)
                }
            }
        )
    }
}

@Composable
private fun RekeyDialog(state: BizzlyUiState, viewModel: BizzlyViewModel) {
    val tokens = LocalBizzlyTokens.current
    AlertDialog(
        onDismissRequest = viewModel::dismissRekeyDialog,
        containerColor = tokens.card,
        title = { Text("Nouveau code de liaison", style = MaterialTheme.typography.titleMedium, color = tokens.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saisissez le nouveau code donné par le gérant.", fontSize = 13.sp, color = tokens.muted)
                OutlinedTextField(
                    value = state.rekeyCodeInput,
                    onValueChange = viewModel::updateRekeyInput,
                    placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.rekeyError.isNotBlank()) {
                    Text(state.rekeyError, color = tokens.ko, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = viewModel::submitRekey, enabled = !state.rekeyBusy) {
                Text("Relier")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissRekeyDialog) {
                Text("Fermer", color = tokens.muted)
            }
        }
    )
}

@Composable
fun QrMenuScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val payload = remember(state.activeAccount?.name, data.prod) {
        ExportUtils.buildQrMenuPayload(state.activeAccount, data)
    }
    val qrBitmap = remember(payload) {
        ExportUtils.generateQrBitmap(payload, 640)
    }

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "QR code du menu",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.text)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BizzlyCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Un seul QR code pour toutes les tables. Le client le scanne avec l’appareil photo de son téléphone : le menu s’affiche, sans internet.",
                        fontSize = 13.5.sp,
                        color = tokens.muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR code du menu",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "Menu trop long pour un QR code.",
                            color = tokens.ko,
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::exportQrMenuPdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("qr_download_pdf_btn")
                    ) {
                        Text("Télécharger le menu en PDF (QR + menu)", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Si vous changez un prix ou un produit, téléchargez et imprimez de nouveau le PDF.",
                        fontSize = 12.5.sp,
                        color = tokens.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
            BizzlySignatureFooter()
        }
    }
}

@Composable
fun HistoryScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    val data = state.businessData

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val pair = viewModel.buildBackupJsonAndMarkSaved() ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pair.second.toByteArray())
                }
            }.onSuccess {
                viewModel.showToast("Sauvegarde enregistrée.")
            }.onFailure {
                viewModel.showToast("Impossible d’enregistrer la sauvegarde.")
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreBackupInSession(uri)
    }

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Bilan et sauvegarde",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.text)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading("Ventes attendues et versements")
            BizzlyCard {
                val recent = data.hist.take(8).reversed()
                if (recent.isEmpty()) {
                    Text("Clôturez une première période pour voir le graphique.", fontSize = 13.sp, color = tokens.muted)
                } else {
                    ClosedPeriodsBarChart(periods = recent)
                }
            }

            SectionHeading("Périodes clôturées")
            BizzlyCard {
                if (data.hist.isEmpty()) {
                    Text("Aucune période clôturée.", fontSize = 13.sp, color = tokens.muted)
                } else {
                    val cumGap = data.hist.sumOf { it.rest }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cumul manquant / excédent", fontWeight = FontWeight.Bold, color = tokens.text)
                        Text(
                            text = BizzlyCalculator.formatFcfa(cumGap),
                            fontWeight = FontWeight.Bold,
                            color = if (cumGap > 0) tokens.ko else tokens.ok
                        )
                    }
                    HorizontalDivider(color = tokens.border)
                    data.hist.forEachIndexed { idx, h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(BizzlyCalculator.formatDateFr(h.d), color = tokens.text)
                                Text(
                                    text = "recettes ${BizzlyCalculator.formatFcfa(h.att)} · versé ${BizzlyCalculator.formatFcfa(h.ver)}",
                                    fontSize = 12.5.sp,
                                    color = tokens.muted
                                )
                            }
                            Text(
                                text = BizzlyCalculator.formatFcfa(h.rest),
                                fontWeight = FontWeight.Bold,
                                color = if (h.rest > 0) tokens.ko else tokens.ok
                            )
                        }
                        if (idx < data.hist.lastIndex) HorizontalDivider(color = tokens.border)
                    }
                }
            }

            SectionHeading("Journal des opérations")
            BizzlyCard {
                if (data.log.isEmpty()) {
                    Text("Aucune opération enregistrée.", fontSize = 13.sp, color = tokens.muted)
                } else {
                    val items = data.log.take(40)
                    items.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.m, fontSize = 13.sp, color = tokens.text, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(BizzlyCalculator.formatDateTimeFr(entry.t).takeLast(5), fontSize = 12.sp, color = tokens.muted)
                        }
                        if (idx < items.lastIndex) HorizontalDivider(color = tokens.border)
                    }
                }
            }

            SectionHeading("Exporter")
            BizzlyCard {
                Text(
                    text = "Téléchargez le bilan financier en PDF ou un classeur Excel complet (ventes, factures, inventaire, dépenses, versements, bons et journal).",
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = viewModel::exportExcelFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_excel_btn")
                ) {
                    Text("Exporter en Excel", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::exportBilanPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_bilan_pdf_btn")
                ) {
                    Text("Télécharger le bilan en PDF", color = tokens.text, fontWeight = FontWeight.SemiBold)
                }
            }

            SectionHeading("Sauvegarde")
            BizzlyCard {
                Text(
                    text = "Enregistrez vos données dans un fichier, puis rechargez-le sur un autre appareil.",
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val slug = BizzlyCalculator.slugify(state.activeAccount?.ident ?: "bizzly")
                        createBackupLauncher.launch("$slug-sauvegarde-${BizzlyCalculator.todayIso()}.json")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.accent,
                        contentColor = tokens.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_backup_btn")
                ) {
                    Text("Sauvegarder", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::exportBackupFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_backup_btn")
                ) {
                    Text("Envoyer la sauvegarde (WhatsApp, e-mail, Drive)", color = tokens.text, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("restore_backup_session_btn")
                ) {
                    Text("Restaurer une sauvegarde", color = tokens.text)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::resetBusinessData,
                    border = BorderStroke(1.dp, tokens.ko),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reset_all_data_btn")
                ) {
                    Text("Tout réinitialiser", color = tokens.ko)
                }
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
private fun ClosedPeriodsBarChart(periods: List<com.example.bizzly.model.ClosedPeriod>) {
    val tokens = LocalBizzlyTokens.current
    val density = LocalDensity.current
    val maxVal = max(1.0, periods.maxOf { max(it.att, it.ver) })
    val mutedArgb = tokens.muted.toArgb()

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            val bottomPad = with(density) { 20.dp.toPx() }
            val chartH = (size.height - bottomPad - 8f).coerceAtLeast(10f)
            val groupW = size.width / periods.size.coerceAtLeast(1)
            val barW = groupW * 0.34f
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mutedArgb
                textSize = with(density) { 10.sp.toPx() }
                textAlign = Paint.Align.CENTER
            }

            periods.forEachIndexed { idx, p ->
                val x0 = idx * groupW + groupW * 0.12f
                val hAtt = ((p.att / maxVal).toFloat() * chartH).coerceAtLeast(2f)
                val hVer = ((p.ver / maxVal).toFloat() * chartH).coerceAtLeast(2f)
                drawRoundRect(
                    color = tokens.primary,
                    topLeft = Offset(x0, chartH - hAtt + 4f),
                    size = Size(barW, hAtt),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = tokens.accent,
                    topLeft = Offset(x0 + barW + 4f, chartH - hVer + 4f),
                    size = Size(barW, hVer),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    BizzlyCalculator.formatDateFr(p.d).take(5),
                    x0 + barW,
                    size.height - 4f,
                    labelPaint
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tokens.primary))
                Text("Recettes", fontSize = 12.sp, color = tokens.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tokens.accent))
                Text("Versé", fontSize = 12.sp, color = tokens.muted)
            }
        }
    }
}
