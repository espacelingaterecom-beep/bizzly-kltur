package com.example.bizzly.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BusinessAvatarBox
import com.example.bizzly.ui.components.bringIntoViewOnIme
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.AdminBusinessSummary
import com.example.bizzly.ui.viewmodel.AuthMode
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel

@Composable
fun AdminKlturScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    BackHandler { viewModel.closeAdminPanel() }

    val admin = state.admin
    if (!admin.isAuthenticated) {
        AdminLoginContent(
            state = state,
            viewModel = viewModel
        )
    } else {
        AdminDashboardContent(
            state = state,
            viewModel = viewModel
        )
    }
}

@Composable
private fun AdminLoginContent(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val admin = state.admin

    var installCodeInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.surfaceTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = tokens.accentReadable,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "Admin KLTUR COORPORATION",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.text
                )
                Text(
                    text = "Gestion multi-entreprises, Supabase & Netlify",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminTabPill(
                label = if (admin.needsSetup) "Configurer code" else "Code KLTUR",
                selected = admin.authTab == 0,
                onClick = { viewModel.setAdminAuthTab(0) },
                tag = "admin_auth_tab_local",
                modifier = Modifier.weight(1f)
            )
            AdminTabPill(
                label = "Compte Supabase",
                selected = admin.authTab == 1,
                onClick = { viewModel.setAdminAuthTab(1) },
                tag = "admin_auth_tab_supabase",
                modifier = Modifier.weight(1f)
            )
        }

        BizzlyCard {
            if (admin.authTab == 0) {
                if (admin.needsSetup) {
                    Text(
                        text = "Premier accès : Saisissez le code d'installation et choisissez votre clé d’administrateur.\n\n⚠️ Conservez votre clé d'administrateur dans un gestionnaire de mots de passe : elle ne peut pas être récupérée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.muted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = installCodeInput,
                        onValueChange = { installCodeInput = it.trim() },
                        label = { Text("Code d’installation (SETUP-...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("admin_install_code_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.take(32) },
                        label = { Text("Clé d’administrateur") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("admin_setup_code_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it.take(32) },
                        label = { Text("Confirmer la clé") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("admin_setup_confirm_input")
                    )
                    if (admin.error.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(admin.error, color = tokens.ko, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setupAdminPasscode(installCodeInput, codeInput, confirmInput) },
                        enabled = !admin.busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_setup_submit_btn")
                    ) {
                        Text(if (admin.busy) "Enregistrement…" else "Créer et ouvrir la console")
                    }
                } else {
                    Text(
                        text = "Connectez-vous en tant qu’administrateur KLTUR COORPORATION.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.muted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.take(32) },
                        label = { Text("Code administrateur KLTUR") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnIme()
                            .testTag("admin_login_code_input")
                    )
                    if (admin.error.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(admin.error, color = tokens.ko, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loginAdminPasscode(codeInput) },
                        enabled = !admin.busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_login_submit_btn")
                    ) {
                        Text(if (admin.busy) "Vérification…" else "Se connecter")
                    }
                }
            } else {
                Text(
                    text = "Connexion avec votre compte administrateur Supabase (e-mail et mot de passe).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("E-mail Supabase Admin") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("admin_supa_email_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnIme()
                        .testTag("admin_supa_pwd_input")
                )
                if (admin.error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(admin.error, color = tokens.ko, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.loginAdminSupabase(emailInput, passwordInput) },
                    enabled = !admin.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_supa_submit_btn")
                ) {
                    Text(if (admin.busy) "Connexion…" else "Connexion Supabase")
                }
            }
        }

        OutlinedButton(
            onClick = viewModel::closeAdminPanel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("admin_back_btn")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retour à l’application", color = tokens.text)
        }
    }
}

@Composable
private fun AdminDashboardContent(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val admin = state.admin
    var showLinkDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Admin top bar
        BizzlyCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Console KLTUR COORPORATION",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.text
                    )
                    Text(
                        text = admin.supabaseEmail?.let { "Supabase : $it" } ?: "Session administrateur active",
                        fontSize = 12.sp,
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_security_btn")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "Code admin", tint = tokens.text)
                    }
                    IconButton(
                        onClick = viewModel::logoutAdmin,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("admin_logout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Déconnexion", tint = tokens.ko)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTabPill(
                    label = "Entreprises (${admin.businesses.size})",
                    selected = admin.activeAdminTab == 0,
                    onClick = { viewModel.setAdminDashboardTab(0) },
                    tag = "admin_dash_tab_biz",
                    modifier = Modifier.weight(1f)
                )
                AdminTabPill(
                    label = "Portail Netlify",
                    selected = admin.activeAdminTab == 1,
                    onClick = { viewModel.setAdminDashboardTab(1) },
                    tag = "admin_dash_tab_netlify",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (admin.activeAdminTab == 0) {
            AdminBusinessesTab(
                state = state,
                viewModel = viewModel,
                onOpenLinkDialog = { showLinkDialog = true }
            )
        } else {
            AdminNetlifyPortalTab(
                netlifyUrl = admin.netlifyUrl,
                onSaveUrl = viewModel::saveNetlifyAdminUrl
            )
        }

        OutlinedButton(
            onClick = viewModel::closeAdminPanel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("admin_close_panel_btn")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.text)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Quitter le mode Admin", color = tokens.text)
        }
    }

    val selectedSummary = admin.businesses.firstOrNull { it.account.id == admin.selectedBusinessId }
    if (selectedSummary != null) {
        AdminBusinessManageDialog(
            summary = selectedSummary,
            snapshots = admin.snapshotsForBusiness?.takeIf { it.first == selectedSummary.account.id }?.second,
            busy = admin.busy,
            viewModel = viewModel,
            onDismiss = { viewModel.selectAdminBusiness(null) }
        )
    }

    if (showLinkDialog) {
        AdminLinkCloudBusinessDialog(
            busy = admin.busy,
            onSubmit = { code ->
                viewModel.adminLinkRemoteBusiness(code)
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false }
        )
    }

    if (showSecurityDialog) {
        AdminPasscodeSettingsDialog(
            hasExisting = !admin.needsSetup,
            onSave = { cur, next, conf ->
                viewModel.updateAdminPasscode(cur, next, conf)
                showSecurityDialog = false
            },
            onDismiss = { showSecurityDialog = false }
        )
    }
}

@Composable
private fun AdminBusinessesTab(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel,
    onOpenLinkDialog: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val admin = state.admin
    val totalProducts = admin.businesses.sumOf { it.productCount }
    val totalRevenue = admin.businesses.sumOf { it.recordedRevenue }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdminKpiMini("Entreprises", admin.businesses.size.toString(), Modifier.weight(1f))
        AdminKpiMini("Produits", totalProducts.toString(), Modifier.weight(1f))
        AdminKpiMini("Recettes", BizzlyCalculator.formatFcfa(totalRevenue), Modifier.weight(1.3f))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenLinkDialog,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("admin_link_cloud_btn")
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = tokens.accentReadable, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Importer cloud", color = tokens.text, fontSize = 12.5.sp)
        }
        OutlinedButton(
            onClick = viewModel::adminSyncAllBusinesses,
            enabled = !admin.busy,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("admin_sync_all_btn")
        ) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = tokens.accentReadable, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tout synchro", color = tokens.text, fontSize = 12.5.sp)
        }
    }

    Button(
        onClick = { viewModel.navigateAuth(AuthMode.SIGNUP) },
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.primary,
            contentColor = tokens.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("admin_create_biz_btn")
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Créer une nouvelle entreprise", fontWeight = FontWeight.SemiBold)
    }

    if (admin.businesses.isEmpty()) {
        BizzlyCard {
            Text(
                text = "Aucune entreprise sur cet appareil. Créez-en une ou importez un espace Supabase.",
                color = tokens.muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        admin.businesses.forEach { item ->
            AdminBusinessRowCard(
                item = item,
                onManage = { viewModel.selectAdminBusiness(item.account.id) },
                onOpen = { viewModel.adminOpenBusinessAsManager(item.account.id) }
            )
        }
    }

    if (admin.remoteSpaces.isNotEmpty()) {
        BizzlyCard {
            Text(
                text = "Espaces Supabase détectés (${admin.remoteSpaces.size})",
                fontWeight = FontWeight.Bold,
                color = tokens.text,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            admin.remoteSpaces.forEach { sp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sp.name, fontWeight = FontWeight.SemiBold, color = tokens.text, fontSize = 13.5.sp)
                        if (sp.syncKey.isNotBlank()) {
                            Text(sp.syncKey, color = tokens.muted, fontSize = 11.5.sp)
                        }
                    }
                    if (sp.syncKey.length >= 20) {
                        OutlinedButton(
                            onClick = { viewModel.adminLinkRemoteBusiness(sp.syncKey) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Importer", color = tokens.text, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBusinessRowCard(
    item: AdminBusinessSummary,
    onManage: () -> Unit,
    onOpen: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val acc = item.account
    BizzlyCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BusinessAvatarBox(account = acc, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = acc.name,
                    fontWeight = FontWeight.Bold,
                    color = tokens.text,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${acc.ident} · ${acc.profiles.size} profil(s) · ${item.productCount} prod.",
                    fontSize = 12.sp,
                    color = tokens.muted
                )
                Text(
                    text = "Recettes : ${BizzlyCalculator.formatFcfa(item.recordedRevenue)} · " +
                        if (item.syncCreds != null) "Cloud actif" else "Local",
                    fontSize = 12.sp,
                    color = if (item.syncCreds != null) tokens.ok else tokens.muted,
                    fontWeight = FontWeight.Medium
                )
            }
            BrandDotsMini(acc.brand.pr, acc.brand.sec, acc.brand.ax)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onManage,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("admin_manage_${acc.ident}")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = tokens.text, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gérer", color = tokens.text)
            }
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("admin_open_${acc.ident}")
            ) {
                Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ouvrir")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AdminNetlifyPortalTab(
    netlifyUrl: String,
    onSaveUrl: (String) -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    var urlInput by remember(netlifyUrl) { mutableStateOf(netlifyUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BizzlyCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = tokens.accentReadable)
            Text(
                text = "Page Admin Netlify intégrée",
                fontWeight = FontWeight.Bold,
                color = tokens.text
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Renseignez l’adresse HTTPS de votre page administrateur Netlify pour la piloter directement ici.",
            fontSize = 12.5.sp,
            color = tokens.muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("URL HTTPS Netlify Admin") },
            placeholder = { Text("https://votre-page-admin.netlify.app") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnIme()
                .testTag("admin_netlify_url_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSaveUrl(urlInput) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("admin_netlify_save_btn")
            ) {
                Text("Charger / Enregistrer")
            }
            if (netlifyUrl.isNotBlank()) {
                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("admin_netlify_reload_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = tokens.text)
                }
                IconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(netlifyUrl)))
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("admin_netlify_external_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Navigateur", tint = tokens.text)
                }
            }
        }
    }

    if (netlifyUrl.startsWith("https://", ignoreCase = true)) {
        val allowedHost = remember(netlifyUrl) {
            runCatching { Uri.parse(netlifyUrl).host?.lowercase() }.getOrNull()
        }
        DisposableEffect(netlifyUrl) {
            onDispose {
                webViewRef?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    destroy()
                }
                webViewRef = null
            }
        }
        BizzlyCard {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = tokens.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.saveFormData = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return true
                                if (!targetUri.scheme.equals("https", ignoreCase = true)) return true
                                val targetHost = targetUri.host?.lowercase() ?: return true
                                val expected = allowedHost ?: return true
                                val sameOrSub = targetHost == expected || targetHost.endsWith(".$expected")
                                return !sameOrSub
                            }
                        }
                        loadUrl(netlifyUrl)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                    if (wv.url != netlifyUrl && netlifyUrl.startsWith("https://", ignoreCase = true)) {
                        wv.loadUrl(netlifyUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun AdminTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) tokens.primary else tokens.inputBg,
        border = BorderStroke(1.dp, if (selected) tokens.primary else tokens.border),
        modifier = modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) tokens.onPrimary else tokens.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminKpiMini(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.card)
            .border(1.dp, tokens.border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(label, fontSize = 11.5.sp, color = tokens.muted)
        Text(
            text = value,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = tokens.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BrandDotsMini(pr: String, sec: String, ax: String) {
    val tokens = LocalBizzlyTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(pr, sec, ax).forEach { hex ->
            val rgb = BizzlyCalculator.hexToRgb(hex)
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color(rgb.first, rgb.second, rgb.third))
                    .border(0.5.dp, tokens.border, CircleShape)
            )
        }
    }
}
