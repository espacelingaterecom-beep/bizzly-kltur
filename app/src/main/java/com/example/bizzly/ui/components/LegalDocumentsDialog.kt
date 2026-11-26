package com.example.bizzly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily

enum class LegalDocumentTab(
    val shortLabel: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    PRIVACY("Confidentialité", R.string.legal_privacy_title, Icons.Default.Policy),
    TERMS("Conditions", R.string.legal_terms_title, Icons.Default.Description),
    LEGAL_NOTICE("Mentions", R.string.legal_notice_title, Icons.Default.Gavel)
}

data class LegalSection(
    val heading: String,
    val body: String
)

object LegalDocumentsContent {
    const val LAST_UPDATED = "Novembre 2026"

    fun sectionsFor(tab: LegalDocumentTab): List<LegalSection> = when (tab) {
        LegalDocumentTab.PRIVACY -> listOf(
            LegalSection(
                "1. Responsable du traitement",
                "L’application Bizzly est éditée par KLTUR COORPORATION (Jonathan Mambachaka). Les données saisies dans un espace d’établissement restent sous le contrôle du gérant de cet établissement."
            ),
            LegalSection(
                "2. Données collectées et saisies",
                "Bizzly enregistre uniquement les informations nécessaires à la gestion de votre établissement : nom commercial, identifiant, e-mail, téléphone, adresse de facturation, logo et couleurs, profils du personnel (nom, rôle, code PIN protégé par hachage salé PBKDF2-HMAC-SHA256), catalogue produits, niveaux de stock, ventes, factures de tables, remises, dépenses, versements, bons et journal d’activité."
            ),
            LegalSection(
                "3. Stockage local prioritaire",
                "Par défaut, vos données sont conservées localement sur votre appareil dans la base interne de l’application. La sauvegarde automatique système Android est désactivée (allowBackup=false) afin d’éviter toute copie non maîtrisée de vos données commerciales."
            ),
            LegalSection(
                "4. Synchronisation cloud optionnelle",
                "Lorsque vous activez la synchronisation multi-appareils ou reliez un téléphone avec votre clé secrète de 20 caractères, vos données sont transmises via HTTPS chiffré vers notre infrastructure cloud sécurisée (Supabase) pour synchroniser vos appareils et conserver des instantanés nocturnes sur 14 jours."
            ),
            LegalSection(
                "5. Autorisations de l’appareil",
                "• Sélecteur photo Android : utilisé uniquement pour importer votre logo sans accès général à vos photos ou fichiers.\n" +
                    "• Bluetooth (sans localisation) : utilisé uniquement pour détecter et imprimer sur votre imprimante thermique ESC/POS 58 mm ou 80 mm.\n" +
                    "• Notifications (facultatif) : sert à afficher les alertes locales de stock critique ou bas.\n" +
                    "Bizzly ne contient aucun SDK publicitaire, aucun traceur marketing et ne vend ni ne loue aucune donnée."
            ),
            LegalSection(
                "6. Sécurité & maîtrise de vos données",
                "L’accès est protégé par codes PIN individuels, limitation après 5 tentatives erronées et verrouillage automatique après 15 minutes d’inactivité. Vous pouvez à tout moment exporter vos données (JSON, Excel, PDF), désactiver la synchronisation cloud ou retirer l’entreprise de l’appareil."
            ),
            LegalSection(
                "7. Vos droits",
                "Vous disposez d’un droit d’accès, de rectification, de portabilité et d’effacement de vos données locales ou synchronisées. Vous pouvez exercer ces droits depuis l’application ou auprès de KLTUR COORPORATION."
            )
        )

        LegalDocumentTab.TERMS -> listOf(
            LegalSection(
                "1. Objet et acceptation",
                "Les présentes Conditions Générales d’Utilisation (CGU) encadrent l’usage de l’application mobile Bizzly éditée par KLTUR COORPORATION. L’utilisation de Bizzly ou la création d’un espace vaut acceptation pleine et entière des présentes conditions."
            ),
            LegalSection(
                "2. Usage professionnel",
                "Bizzly est un outil d’aide à la gestion destiné aux bars, restaurants et points de vente (prise de commandes, encaissements, partage d’addition, remises, gestion des stocks, impressions thermiques et bilans). L’utilisateur s’engage à en faire un usage licite et conforme à la réglementation commerciale et fiscale en vigueur."
            ),
            LegalSection(
                "3. Rôles, codes PIN et confidentialité d’accès",
                "Le Gérant administre les profils (Gérant, Caisse, Serveur), fixe et renouvelle les codes PIN et conserve confidentiellement sa clé de synchronisation à 20 caractères ainsi que ses sauvegardes JSON. Toute opération réalisée sous un profil authentifié est présumée effectuée par son titulaire."
            ),
            LegalSection(
                "4. Exactitude comptable et fiscale",
                "Les tickets de caisse, notes proforma, bilans PDF, classeurs Excel et valorisations de stock sont produits sur la base des prix, ventes, remises et comptages saisis par l’établissement. Ils constituent un outil interne de gestion et ne dispensent pas des obligations comptables, fiscales ou de facturation certifiée propres à votre juridiction."
            ),
            LegalSection(
                "5. Disponibilité et sauvegardes",
                "Bizzly fonctionne hors ligne par défaut et propose des sauvegardes manuelles (.json) ainsi qu’une synchronisation cloud optionnelle. Il appartient à l’établissement d’effectuer des sauvegardes régulières. KLTUR COORPORATION ne saurait être tenue responsable d’une perte de données liée à la perte de l’appareil sans sauvegarde ou à la divulgation d’un code d’accès."
            ),
            LegalSection(
                "6. Propriété intellectuelle et contenu",
                "L’application Bizzly, son code, son identité visuelle et ses modèles de documents restent la propriété exclusive de KLTUR COORPORATION. L’établissement conserve l’entière propriété de ses logos, menus, tarifs et journaux commerciaux."
            )
        )

        LegalDocumentTab.LEGAL_NOTICE -> listOf(
            LegalSection(
                "Éditeur de l’application",
                "KLTUR COORPORATION\nReprésentée par : Jonathan Mambachaka\nApplication : Bizzly — Gestion bar & restaurant\nVersion installée : ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            ),
            LegalSection(
                "Directeur de publication & conception",
                "Jonathan Mambachaka — Fondateur & Éditeur chez KLTUR COORPORATION."
            ),
            LegalSection(
                "Hébergement & services techniques",
                "• Données locales : stockées exclusivement sur l’appareil de l’utilisateur (base interne Android).\n" +
                    "• Synchronisation cloud optionnelle : infrastructure sécurisée HTTPS fournie via Supabase pour la sauvegarde nocturne et le partage multi-téléphones à l’initiative de l’utilisateur.\n" +
                    "• Portail d’administration : accessible via HTTPS uniquement pour les administrateurs autorisés."
            ),
            LegalSection(
                "Propriété intellectuelle",
                "La marque Bizzly, la signature KLTUR COORPORATION, les écrans, icônes, textes et algorithmes de calcul sont protégés par le droit de la propriété intellectuelle. Toute copie, rétro-ingénierie ou redistribution non autorisée est interdite."
            ),
            LegalSection(
                "Contact & réclamations",
                "Pour toute demande d’assistance, question juridique ou demande relative à la confidentialité des données, veuillez contacter KLTUR COORPORATION / Jonathan Mambachaka via votre canal de support officiel."
            )
        )
    }
}

@Composable
fun LegalDocumentsDialog(
    initialTab: LegalDocumentTab = LegalDocumentTab.PRIVACY,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(22.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.border),
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tokens.headerStart, tokens.headerEnd)
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tokens.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = tokens.onAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(id = R.string.legal_all_title),
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = tokens.onHeader,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Bizzly · KLTUR COORPORATION",
                                    fontSize = 12.sp,
                                    color = tokens.onHeaderMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tokens.headerControlBg)
                                .border(1.dp, tokens.headerControlBorder, RoundedCornerShape(12.dp))
                                .testTag("legal_dialog_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = tokens.onHeader
                            )
                        }
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegalDocumentTab.entries.forEach { tab ->
                        val active = tab == selectedTab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) tokens.primary else tokens.inputBg,
                            border = BorderStroke(1.dp, if (active) tokens.primary else tokens.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("legal_tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (active) tokens.onPrimary else tokens.accentReadable,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = tab.shortLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) tokens.onPrimary else tokens.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.border)

                // Document body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = selectedTab.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(tokens.tint)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = LegalDocumentsContent.LAST_UPDATED,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.accentReadable
                            )
                        }
                    }

                    LegalDocumentsContent.sectionsFor(selectedTab).forEach { section ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tokens.background)
                                .border(1.dp, tokens.border, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = section.heading,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = tokens.text
                            )
                            Text(
                                text = section.body,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = tokens.muted
                            )
                        }
                    }
                }

                HorizontalDivider(color = tokens.border)

                // Footer button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("legal_dialog_accept_btn")
                    ) {
                        Text("Fermer", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
