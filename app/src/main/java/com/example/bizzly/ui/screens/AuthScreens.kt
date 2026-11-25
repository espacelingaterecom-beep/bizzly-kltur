package com.example.bizzly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyLogoLockup
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.BrandColorSwatch
import com.example.bizzly.ui.components.BusinessAvatarBox
import com.example.bizzly.ui.components.InitialAvatar
import com.example.bizzly.ui.components.PinDotsRow
import com.example.bizzly.ui.components.PinNumericKeypad
import com.example.bizzly.ui.components.rememberDecodedBitmap
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.AuthMode
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel

@Composable
fun AuthFlowScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current

    BackHandler(enabled = state.authMode != AuthMode.PICK_BUSINESS && state.accounts.isNotEmpty()) {
        when (state.authMode) {
            AuthMode.PIN_LOGIN -> viewModel.navigateAuth(AuthMode.PICK_PROFILE)
            AuthMode.PICK_PROFILE,
            AuthMode.SIGNUP,
            AuthMode.LINK_PHONE,
            AuthMode.RESTORE_BACKUP -> viewModel.navigateAuth(AuthMode.PICK_BUSINESS)
            AuthMode.PICK_BUSINESS -> Unit
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackupFromAuth(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Auth top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BizzlyLogoLockup(isDark = state.isDarkTheme, height = 34.dp)
                IconButton(
                    onClick = viewModel::toggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.card)
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("auth_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Changer de thème",
                        tint = tokens.text
                    )
                }
            }

            when (state.authMode) {
                AuthMode.PICK_BUSINESS -> PickBusinessContent(
                    accounts = state.accounts,
                    onSelectBusiness = viewModel::selectBusiness,
                    onCreateNew = { viewModel.navigateAuth(AuthMode.SIGNUP) },
                    onLinkPhone = { viewModel.navigateAuth(AuthMode.LINK_PHONE) },
                    onRestoreBackup = { viewModel.navigateAuth(AuthMode.RESTORE_BACKUP) }
                )

                AuthMode.PICK_PROFILE -> {
                    val acc = state.accounts.firstOrNull { it.id == state.selectedBusinessId }
                    if (acc != null) {
                        PickProfileContent(
                            account = acc,
                            onSelectProfile = viewModel::selectProfile,
                            onSwitchBusiness = { viewModel.navigateAuth(AuthMode.PICK_BUSINESS) }
                        )
                    }
                }

                AuthMode.PIN_LOGIN -> {
                    val acc = state.accounts.firstOrNull { it.id == state.selectedBusinessId }
                    val prof = acc?.profiles?.firstOrNull { it.id == state.selectedProfileId }
                    if (acc != null && prof != null) {
                        PinLoginContent(
                            account = acc,
                            profileName = prof.name,
                            profileRoleLabel = BizzlyRole.fromKey(prof.role).label,
                            pin = state.loginPin,
                            error = state.loginError,
                            shakeTick = state.loginShakeTick,
                            onKey = viewModel::pressLoginPinKey,
                            onBack = { viewModel.navigateAuth(AuthMode.PICK_PROFILE) }
                        )
                    }
                }

                AuthMode.SIGNUP -> SignupContent(
                    state = state,
                    viewModel = viewModel
                )

                AuthMode.LINK_PHONE -> LinkPhoneContent(
                    code = state.linkCodeInput,
                    error = state.linkError,
                    busy = state.linkBusy,
                    hasExistingAccounts = state.accounts.isNotEmpty(),
                    onCodeChange = viewModel::updateLinkCode,
                    onSubmit = viewModel::submitLinkPhone,
                    onBack = {
                        viewModel.navigateAuth(
                            if (state.accounts.isNotEmpty()) AuthMode.PICK_BUSINESS else AuthMode.SIGNUP
                        )
                    }
                )

                AuthMode.RESTORE_BACKUP -> RestoreFromAuthContent(
                    hasExistingAccounts = state.accounts.isNotEmpty(),
                    onPickFile = { backupLauncher.launch(arrayOf("application/json", "*/*")) },
                    onBack = {
                        viewModel.navigateAuth(
                            if (state.accounts.isNotEmpty()) AuthMode.PICK_BUSINESS else AuthMode.SIGNUP
                        )
                    }
                )
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
private fun PickBusinessContent(
    accounts: List<BusinessAccount>,
    onSelectBusiness: (String) -> Unit,
    onCreateNew: () -> Unit,
    onLinkPhone: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Choisissez votre entreprise",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        accounts.forEach { acc ->
            Surface(
                onClick = { onSelectBusiness(acc.id) },
                shape = RoundedCornerShape(14.dp),
                color = tokens.card,
                border = BorderStroke(1.dp, tokens.border),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("pick_business_${acc.ident}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BusinessAvatarBox(account = acc, size = 46.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = acc.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = tokens.text
                        )
                        Text(
                            text = acc.ident,
                            fontSize = 13.sp,
                            color = tokens.muted
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onCreateNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("create_new_business_btn")
        ) {
            Text("+ Créer une nouvelle entreprise", color = tokens.text)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onLinkPhone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("link_phone_btn")
        ) {
            Text("Lier ce téléphone (code de synchronisation)", color = tokens.text)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRestoreBackup,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("restore_backup_auth_btn")
        ) {
            Text("Restaurer depuis une sauvegarde", color = tokens.text)
        }
    }
}

@Composable
private fun PickProfileContent(
    account: BusinessAccount,
    onSelectProfile: (Long) -> Unit,
    onSwitchBusiness: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val roleOrder = mapOf(
        BizzlyRole.GERANT.key to 0,
        BizzlyRole.CAISSE.key to 1,
        BizzlyRole.SERVEUR.key to 2
    )
    val sorted = account.profiles.sortedBy { roleOrder[it.role] ?: 9 }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BusinessAvatarBox(account = account, size = 84.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Qui êtes-vous ?",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        sorted.forEach { prof ->
            val hasPin = prof.hash.isNotBlank()
            val roleLabel = BizzlyRole.fromKey(prof.role).label
            Surface(
                onClick = { if (hasPin) onSelectProfile(prof.id) },
                enabled = hasPin,
                shape = RoundedCornerShape(14.dp),
                color = tokens.card.copy(alpha = if (hasPin) 1f else 0.55f),
                border = BorderStroke(1.dp, tokens.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("pick_profile_${prof.id}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    InitialAvatar(
                        letter = prof.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        size = 42.dp
                    )
                    Column {
                        Text(
                            text = prof.name,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text
                        )
                        Text(
                            text = if (hasPin) roleLabel else "$roleLabel · PIN à définir par le gérant",
                            fontSize = 13.sp,
                            color = tokens.muted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = onSwitchBusiness,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("switch_business_btn")
        ) {
            Text("← Changer d’entreprise", color = tokens.text)
        }
    }
}

@Composable
private fun PinLoginContent(
    account: BusinessAccount,
    profileName: String,
    profileRoleLabel: String,
    pin: String,
    error: String,
    shakeTick: Int,
    onKey: (String) -> Unit,
    onBack: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InitialAvatar(
            letter = profileName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            size = 56.dp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = profileName,
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text
        )
        Text(
            text = "$profileRoleLabel · ${account.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.muted
        )
        PinDotsRow(pinLength = pin.length, shakeTick = shakeTick)
        Text(
            text = error.ifBlank { "Entrez votre code PIN" },
            color = if (error.isNotBlank()) tokens.ko else tokens.muted,
            fontSize = 13.5.sp,
            fontWeight = if (error.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        PinNumericKeypad(onKey = onKey)
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pin_back_btn")
        ) {
            Text("← Retour", color = tokens.text)
        }
    }
}

@Composable
private fun SignupContent(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val s = state.signup
    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.pickSignupLogo(uri)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Créez l’espace de votre entreprise",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text
        )
        Text(
            text = if (state.pendingLegacyData != null) {
                "Vos données actuelles seront conservées et rattachées à cette entreprise."
            } else {
                "Votre logo et vos couleurs seront utilisés dans l’appli, sur les factures, le QR code et le menu."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.muted
        )

        BizzlyCard {
            Text("Logo de l’entreprise", fontSize = 13.sp, color = tokens.muted)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val bmp = rememberDecodedBitmap(s.logo)
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, tokens.border, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Aperçu logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    InitialAvatar(letter = "?", size = 72.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            logoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("signup_pick_logo_btn")
                    ) {
                        Text("Choisir un logo", color = tokens.text)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PNG ou JPG. Les couleurs sont reprises du logo.",
                        fontSize = 12.sp,
                        color = tokens.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Couleurs de l’entreprise", fontSize = 13.sp, color = tokens.muted)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandColorSwatch(
                    label = "Principale",
                    hex = s.brand.pr,
                    onColorSelected = { hex ->
                        viewModel.updateSignup { it.copy(brand = it.brand.copy(pr = hex)) }
                    },
                    modifier = Modifier.weight(1f)
                )
                BrandColorSwatch(
                    label = "Fond foncé",
                    hex = s.brand.sec,
                    onColorSelected = { hex ->
                        viewModel.updateSignup { it.copy(brand = it.brand.copy(sec = hex)) }
                    },
                    modifier = Modifier.weight(1f)
                )
                BrandColorSwatch(
                    label = "Accent",
                    hex = s.brand.ax,
                    onColorSelected = { hex ->
                        viewModel.updateSignup { it.copy(brand = it.brand.copy(ax = hex)) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        BizzlyCard {
            OutlinedTextField(
                value = s.name,
                onValueChange = { v ->
                    viewModel.updateSignup { cur ->
                        cur.copy(
                            name = v,
                            ident = if (cur.identEdited) cur.ident else BizzlyCalculator.slugify(v)
                        )
                    }
                },
                label = { Text("Nom de l’entreprise *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_name_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.ident,
                onValueChange = { v ->
                    viewModel.updateSignup { it.copy(ident = v.lowercase(), identEdited = true) }
                },
                label = { Text("Identifiant *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_ident_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.email,
                onValueChange = { v -> viewModel.updateSignup { it.copy(email = v) } },
                label = { Text("E-mail *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_email_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.tel,
                onValueChange = { v -> viewModel.updateSignup { it.copy(tel = v) } },
                label = { Text("Téléphone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_tel_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.addr,
                onValueChange = { v -> viewModel.updateSignup { it.copy(addr = v) } },
                label = { Text("Adresse (affichée sur les factures)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_addr_input")
            )
        }

        BizzlyCard {
            Text("Codes PIN (4 chiffres)", fontSize = 13.sp, color = tokens.muted)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.pin,
                onValueChange = { v ->
                    if (v.length <= 4 && v.all { it.isDigit() }) {
                        viewModel.updateSignup { it.copy(pin = v) }
                    }
                },
                label = { Text("PIN du gérant *") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_pin_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.pin2,
                onValueChange = { v ->
                    if (v.length <= 4 && v.all { it.isDigit() }) {
                        viewModel.updateSignup { it.copy(pin2 = v) }
                    }
                },
                label = { Text("Confirmer le PIN du gérant *") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_pin2_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = s.cpin,
                onValueChange = { v ->
                    if (v.length <= 4 && v.all { it.isDigit() }) {
                        viewModel.updateSignup { it.copy(cpin = v) }
                    }
                },
                label = { Text("PIN de la caisse (facultatif)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_cpin_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Les serveurs sont ajoutés ensuite par le gérant (Plus > Entreprise, profils et accès).",
                fontSize = 12.5.sp,
                color = tokens.muted
            )
        }

        if (s.error.isNotBlank()) {
            Text(
                text = s.error,
                color = tokens.ko,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = viewModel::createBusinessAccount,
            enabled = !state.loginBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.primary,
                contentColor = tokens.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("signup_submit_btn")
        ) {
            Text("Créer mon espace", fontWeight = FontWeight.SemiBold)
        }

        if (state.accounts.isNotEmpty()) {
            OutlinedButton(
                onClick = { viewModel.navigateAuth(AuthMode.PICK_BUSINESS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("signup_existing_btn")
            ) {
                Text("← J’ai déjà un espace", color = tokens.text)
            }
        }
        OutlinedButton(
            onClick = { viewModel.navigateAuth(AuthMode.LINK_PHONE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("signup_link_phone_btn")
        ) {
            Text("Lier ce téléphone (code de synchronisation)", color = tokens.text)
        }
        OutlinedButton(
            onClick = { viewModel.navigateAuth(AuthMode.RESTORE_BACKUP) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("signup_restore_btn")
        ) {
            Text("Restaurer depuis une sauvegarde", color = tokens.text)
        }
    }
}

@Composable
private fun LinkPhoneContent(
    code: String,
    error: String,
    busy: Boolean,
    hasExistingAccounts: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Lier ce téléphone",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text
        )
        BizzlyCard {
            Text(
                text = "Demandez le code de liaison au gérant (Plus > Entreprise, profils et accès > Code de liaison).",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Code") },
                placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link_code_input")
            )
            if (error.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(error, color = tokens.ko, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSubmit,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("link_phone_submit_btn")
            ) {
                Text(if (busy) "Connexion…" else "Lier ce téléphone", fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("link_phone_back_btn")
        ) {
            Text(if (hasExistingAccounts) "← Retour" else "← Retour", color = tokens.text)
        }
    }
}

@Composable
private fun RestoreFromAuthContent(
    hasExistingAccounts: Boolean,
    onPickFile: () -> Unit,
    onBack: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Restaurer une sauvegarde",
            style = MaterialTheme.typography.headlineMedium,
            color = tokens.text
        )
        BizzlyCard {
            Text(
                text = "Choisissez le fichier .json créé avec « Sauvegarder » (nouveau téléphone, ou après un effacement).",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPickFile,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("restore_pick_file_btn")
            ) {
                Text("Choisir le fichier", fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("restore_auth_back_btn")
        ) {
            Text("← Retour", color = tokens.text)
        }
    }
}
