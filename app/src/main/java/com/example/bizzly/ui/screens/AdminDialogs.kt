package com.example.bizzly.ui.screens

import com.example.bizzly.util.ExportUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.data.sync.SyncSnapshotSummary
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.viewmodel.AdminBusinessSummary
import com.example.bizzly.ui.viewmodel.BizzlyViewModel

@Composable
fun AdminBusinessManageDialog(
    summary: AdminBusinessSummary,
    snapshots: List<SyncSnapshotSummary>?,
    busy: Boolean,
    viewModel: BizzlyViewModel,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val context = LocalContext.current
    val acc = summary.account

    var nameText by remember(acc.id) { mutableStateOf(acc.name) }
    var emailText by remember(acc.id) { mutableStateOf(acc.email) }
    var telText by remember(acc.id) { mutableStateOf(acc.tel) }
    var addrText by remember(acc.id) { mutableStateOf(acc.addr) }
    var tablesText by remember(acc.id, summary.tablesCount) { mutableStateOf(summary.tablesCount.toString()) }
    var pinResetTargetId by remember { mutableStateOf<Long?>(null) }
    var pinResetValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = "Gérer : ${acc.name}",
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
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emailText,
                    onValueChange = { emailText = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telText,
                    onValueChange = { telText = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = addrText,
                    onValueChange = { addrText = it },
                    label = { Text("Adresse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tablesText,
                    onValueChange = { tablesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nombre de tables") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.adminUpdateBusinessInfo(
                            businessId = acc.id,
                            name = nameText,
                            email = emailText,
                            tel = telText,
                            addr = addrText,
                            tablesCount = tablesText.toIntOrNull() ?: 20
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Enregistrer les modifications")
                }

                HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 4.dp))
                Text("Profils & réinitialisation PIN", fontWeight = FontWeight.Bold, color = tokens.text, fontSize = 13.5.sp)
                acc.profiles.forEach { prof ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prof.name, fontWeight = FontWeight.Medium, color = tokens.text, fontSize = 13.sp)
                            Text(BizzlyRole.fromKey(prof.role).label, color = tokens.muted, fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                pinResetTargetId = prof.id
                                pinResetValue = ""
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Reset PIN", color = tokens.text, fontSize = 12.sp)
                        }
                    }
                    if (pinResetTargetId == prof.id) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = pinResetValue,
                                onValueChange = { v ->
                                    if (v.length <= 4 && v.all { it.isDigit() }) pinResetValue = v
                                },
                                label = { Text("Nouveau PIN (4 ch.)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    viewModel.adminResetProfilePin(acc.id, prof.id, pinResetValue)
                                    pinResetTargetId = null
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 4.dp))
                Text("Synchronisation Supabase", fontWeight = FontWeight.Bold, color = tokens.text, fontSize = 13.5.sp)
                val creds = summary.syncCreds
                if (creds == null) {
                    OutlinedButton(
                        onClick = { viewModel.adminEnableSyncForBusiness(acc.id) },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Activer le cloud Supabase", color = tokens.text)
                    }
                } else {
                    val formattedKey = creds.key.chunked(5).joinToString("-")
                    Text(
                        text = formattedKey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.accentReadable
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                ExportUtils.copySensitiveToClipboard(context, "Code Bizzly", formattedKey)
                                viewModel.showToast("Code copié.")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Copier code", color = tokens.text, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.adminSyncBusinessNow(acc.id) },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Synchro", color = tokens.text, fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.adminFetchSnapshots(acc.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Voir copies cloud (14 jours)", color = tokens.text, fontSize = 12.5.sp)
                    }
                }

                if (snapshots != null) {
                    Text("Sauvegardes cloud :", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = tokens.text)
                    if (snapshots.isEmpty()) {
                        Text("Aucune copie disponible.", fontSize = 12.sp, color = tokens.muted)
                    } else {
                        snapshots.forEach { s ->
                            OutlinedButton(
                                onClick = { viewModel.adminRestoreSnapshot(acc.id, s.day) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${BizzlyCalculator.formatDateFr(s.day)} (${s.n} élém.) · Restaurer",
                                    color = tokens.text,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 4.dp))
                Text("Exports & maintenance", fontWeight = FontWeight.Bold, color = tokens.text, fontSize = 13.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.adminExportExcel(acc.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Excel", color = tokens.text, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.adminExportBilanPdf(acc.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Bilan PDF", color = tokens.text, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.adminExportBackup(acc.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Sauvegarde complète (.json)", color = tokens.text, fontSize = 12.5.sp)
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.adminDeleteBusiness(acc.id)
                    },
                    border = BorderStroke(1.dp, tokens.ko),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Supprimer de cet appareil", color = tokens.ko, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = tokens.text)
            }
        }
    )
}

@Composable
fun AdminLinkCloudBusinessDialog(
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = { Text("Importer une entreprise Supabase", style = MaterialTheme.typography.titleMedium, color = tokens.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Saisissez le code de synchronisation à 20 caractères de l’entreprise à importer dans votre console Admin.",
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_link_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(code) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                )
            ) {
                Text("Importer")
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
fun AdminPasscodeSettingsDialog(
    hasExisting: Boolean,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = { Text("Code Admin KLTUR", style = MaterialTheme.typography.titleMedium, color = tokens.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasExisting) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it.take(32) },
                        label = { Text("Code actuel") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it.take(32) },
                    label = { Text("Nouveau code (4–32 car.)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(32) },
                    label = { Text("Confirmer le code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(current, next, confirm) }) {
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
