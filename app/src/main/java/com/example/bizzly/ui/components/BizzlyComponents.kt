package com.example.bizzly.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BrandColors
import com.example.bizzly.model.BrandThemeProposal
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.SyncStatus
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.ConfirmDialogState
import com.example.bizzly.ui.viewmodel.PinOverlayState
import com.example.bizzly.ui.viewmodel.ReadyExport
import com.example.bizzly.util.ExportUtils
import kotlin.math.roundToInt

@Composable
fun BizzlyLogoLockup(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp
) {
    Image(
        painter = painterResource(
            id = if (isDark) R.drawable.bizzly_logo_dark else R.drawable.bizzly_logo_light
        ),
        contentDescription = "Bizzly",
        contentScale = ContentScale.FillHeight,
        modifier = modifier.height(height)
    )
}

@Composable
fun rememberDecodedBitmap(dataUri: String?): Bitmap? {
    return remember(dataUri) { ExportUtils.decodeDataUriBitmap(dataUri) }
}

@Composable
fun BusinessAvatarBox(
    account: BusinessAccount?,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    val bmp = rememberDecodedBitmap(account?.logo)
    if (bmp != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, tokens.border, RoundedCornerShape(12.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = account?.name ?: "Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        InitialAvatar(
            letter = account?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            size = size,
            modifier = modifier
        )
    }
}

@Composable
fun InitialAvatar(
    letter: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.tint),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.ifBlank { "?" },
            color = tokens.accentReadable,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.44f).sp
        )
    }
}

@Composable
fun BizzlyCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val shape = RoundedCornerShape(16.dp)
    val baseMod = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(tokens.card)
        .border(1.dp, borderColor ?: tokens.border, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(16.dp)

    Column(modifier = baseMod, content = content)
}

@Composable
fun BizzlyHeroHeader(
    subtitle: String,
    account: BusinessAccount?,
    roleLabel: String?,
    syncStatus: SyncStatus?,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onLockClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val tokens = LocalBizzlyTokens.current
    val shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, tokens.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(tokens.headerStart, tokens.headerEnd)
                    )
                )
        ) {
            // Subtle branded accent top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(tokens.accent)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (account != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            BusinessAvatarBox(account = account, size = 40.dp)
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.onHeader,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        BizzlyLogoLockup(isDark = true, height = 34.dp)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onLockClick != null) {
                            IconButton(
                                onClick = onLockClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tokens.headerControlBg)
                                    .border(1.dp, tokens.headerControlBorder, RoundedCornerShape(10.dp))
                                    .testTag("lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Verrouiller",
                                    tint = tokens.onHeader,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tokens.headerControlBg)
                                .border(1.dp, tokens.headerControlBorder, RoundedCornerShape(10.dp))
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Changer de thème",
                                tint = tokens.onHeader,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.onHeaderMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!roleLabel.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(tokens.accent)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = roleLabel,
                                    color = tokens.onAccent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (syncStatus != null && syncStatus != SyncStatus.OFF) {
                            SyncBadgeChip(syncStatus, onHeaderSurface = true)
                        }
                    }
                }

                if (extraContent != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    extraContent()
                }
            }
        }
    }
}

@Composable
fun SyncBadgeChip(status: SyncStatus, onHeaderSurface: Boolean = false) {
    val tokens = LocalBizzlyTokens.current
    val dotColor = when (status) {
        SyncStatus.OK -> tokens.ok
        SyncStatus.SYNCING -> tokens.warn
        SyncStatus.ERROR, SyncStatus.OFF -> tokens.ko
    }
    val bg = if (onHeaderSurface) tokens.headerControlBg else tokens.background
    val bd = if (onHeaderSurface) tokens.headerControlBorder else tokens.border
    val fg = if (onHeaderSurface) tokens.onHeader else tokens.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = status.chipLabel,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HeroPrimaryHighlight(
    pillText: String,
    bigValue: String,
    caption: String,
    valueColor: Color? = null
) {
    val tokens = LocalBizzlyTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(tokens.heroHighlightStart, tokens.heroHighlightEnd)
                )
            )
            .border(1.dp, tokens.accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(tokens.accent)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = pillText,
                color = tokens.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = bigValue,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = valueColor ?: tokens.onHeroHighlight
        )
        Text(
            text = caption,
            fontSize = 12.5.sp,
            color = tokens.onHeroHighlight.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun PinDotsRow(
    pinLength: Int,
    shakeTick: Int
) {
    val tokens = LocalBizzlyTokens.current
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            val pattern = listOf(-14f, 14f, -10f, 10f, -5f, 0f)
            pattern.forEach { target ->
                offsetX.animateTo(target, animationSpec = tween(45))
            }
        }
    }

    Row(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 4) {
            val filled = i < pinLength
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (filled) tokens.primary else Color.Transparent)
                    .border(2.dp, if (filled) tokens.primary else tokens.border, CircleShape)
            )
        }
    }
}

@Composable
fun PinNumericKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "x")
    )

    Column(
        modifier = modifier.widthIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(72.dp))
                    } else {
                        val isBackspace = key == "x"
                        Surface(
                            onClick = { onKey(key) },
                            shape = CircleShape,
                            color = tokens.card,
                            border = BorderStroke(1.dp, tokens.border),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("pin_key_$key")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBackspace) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Effacer",
                                        tint = tokens.text,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 24.sp,
                                        color = tokens.text
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinAuthorizationOverlay(
    overlay: PinOverlayState,
    onKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val title = when (val m = overlay.mode) {
        is com.example.bizzly.ui.viewmodel.PinPromptMode.Authorize -> m.title
        is com.example.bizzly.ui.viewmodel.PinPromptMode.SetNewPin -> m.title
    }
    Dialog(onDismissRequest = onDismiss) {
        BizzlyCard(modifier = Modifier.widthIn(max = 340.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.text,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = overlay.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.muted,
                    textAlign = TextAlign.Center
                )
                PinDotsRow(pinLength = overlay.pin.length, shakeTick = overlay.shakeTick)
                if (overlay.error.isNotBlank()) {
                    Text(
                        text = overlay.error,
                        color = tokens.ko,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                PinNumericKeypad(onKey = onKey)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pin_overlay_cancel")
                ) {
                    Text("Annuler", color = tokens.text)
                }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    state: ConfirmDialogState,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = "Confirmation",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.text
            )
        },
        text = {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.text
            )
        },
        confirmButton = {
            Button(
                onClick = state.onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isDestructive) tokens.ko else tokens.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_dialog_ok")
            ) {
                Text(state.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_dialog_cancel")
            ) {
                Text("Annuler", color = tokens.muted)
            }
        }
    )
}

@Composable
fun ExportReadyDialog(
    export: ReadyExport,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val sizeKb = ((export.bytes.size + 512) / 1024).coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        icon = {
            Icon(
                imageVector = if (export.savedToDownloads) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = null,
                tint = if (export.savedToDownloads) tokens.ok else tokens.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (export.savedToDownloads) "Fichier téléchargé" else "Fichier prêt",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = export.filename,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = tokens.text
                )
                Text(
                    text = if (export.savedToDownloads) {
                        "Enregistré dans Téléchargements/Bizzly ($sizeKb Ko)."
                    } else {
                        "Prêt à être enregistré dans votre téléphone ($sizeKb Ko)."
                    },
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (export.savedToDownloads) {
                    Button(
                        onClick = onOpen,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_dialog_open_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onSaveAs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_dialog_save_as_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = tokens.text,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer sous…", color = tokens.text)
                    }
                } else {
                    Button(
                        onClick = onSaveAs,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_dialog_save_as_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer dans les fichiers", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_dialog_open_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = tokens.text,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir", color = tokens.text)
                    }
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_dialog_share_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = tokens.text,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Partager (WhatsApp, e-mail, Drive)", color = tokens.text, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_dialog_close_btn")
            ) {
                Text("Fermer", color = tokens.muted)
            }
        }
    )
}

@Composable
fun BrandColorSwatch(
    label: String,
    hex: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    var showPicker by remember { mutableStateOf(false) }
    val rgb = BizzlyCalculator.hexToRgb(hex)
    val swatchColor = Color(rgb.first, rgb.second, rgb.third)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.inputBg)
            .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
            .clickable { showPicker = true }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(swatchColor)
                .border(1.dp, tokens.border, RoundedCornerShape(7.dp))
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = tokens.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showPicker) {
        ColorPickerDialog(
            title = "Couleur : $label",
            initialHex = hex,
            onConfirm = { picked ->
                onColorSelected(picked)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val initialRgb = remember(initialHex) { BizzlyCalculator.hexToRgb(initialHex) }
    var r by remember(initialHex) { mutableIntStateOf(initialRgb.first) }
    var g by remember(initialHex) { mutableIntStateOf(initialRgb.second) }
    var b by remember(initialHex) { mutableIntStateOf(initialRgb.third) }
    val presets = listOf(
        "#2563EB", "#0A1F44", "#FF6B6B", "#16A34A",
        "#F59E0B", "#9333EA", "#0891B2", "#DC2626",
        "#1E293B", "#DB2777"
    )
    val curHex = BizzlyCalculator.rgbToHex(r, g, b)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(r, g, b))
                            .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = curHex,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.take(5).forEach { pHex ->
                        val prgb = BizzlyCalculator.hexToRgb(pHex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(prgb.first, prgb.second, prgb.third))
                                .clickable {
                                    r = prgb.first; g = prgb.second; b = prgb.third
                                }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.drop(5).forEach { pHex ->
                        val prgb = BizzlyCalculator.hexToRgb(pHex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(prgb.first, prgb.second, prgb.third))
                                .clickable {
                                    r = prgb.first; g = prgb.second; b = prgb.third
                                }
                        )
                    }
                }
                Text("Rouge ($r)", fontSize = 12.sp, color = tokens.muted)
                Slider(value = r.toFloat(), onValueChange = { r = it.roundToInt() }, valueRange = 0f..255f)
                Text("Vert ($g)", fontSize = 12.sp, color = tokens.muted)
                Slider(value = g.toFloat(), onValueChange = { g = it.roundToInt() }, valueRange = 0f..255f)
                Text("Bleu ($b)", fontSize = 12.sp, color = tokens.muted)
                Slider(value = b.toFloat(), onValueChange = { b = it.roundToInt() }, valueRange = 0f..255f)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(curHex) }) {
                Text("Appliquer")
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
fun LogoThemeProposalsSection(
    proposals: List<BrandThemeProposal>,
    currentBrand: BrandColors,
    onSelectProposal: (BrandColors) -> Unit,
    modifier: Modifier = Modifier
) {
    if (proposals.isEmpty()) return
    val tokens = LocalBizzlyTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = tokens.accentReadable,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Thèmes proposés d’après votre logo",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.text
            )
        }
        proposals.forEachIndexed { idx, proposal ->
            val selected = proposal.brand == currentBrand
            val prRgb = BizzlyCalculator.hexToRgb(proposal.brand.pr)
            val secRgb = BizzlyCalculator.hexToRgb(proposal.brand.sec)
            val axRgb = BizzlyCalculator.hexToRgb(proposal.brand.ax)
            val prC = Color(prRgb.first, prRgb.second, prRgb.third)
            val secC = Color(secRgb.first, secRgb.second, secRgb.third)
            val axC = Color(axRgb.first, axRgb.second, axRgb.third)

            Surface(
                onClick = { onSelectProposal(proposal.brand) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) tokens.surfaceTint else tokens.inputBg,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) tokens.primary else tokens.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logo_theme_proposal_$idx")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(prC)
                                .border(1.dp, tokens.border, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(secC)
                                .border(1.dp, tokens.border, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(axC)
                                .border(1.dp, tokens.border, CircleShape)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = proposal.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.text
                        )
                        Text(
                            text = proposal.subtitle,
                            fontSize = 11.5.sp,
                            color = tokens.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Sélectionné",
                            tint = tokens.accentReadable,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BizzlySignatureFooter(
    onAdminClick: (() -> Unit)? = null
) {
    val tokens = LocalBizzlyTokens.current
    var tapCount by remember { mutableIntStateOf(0) }
    var activeLegalTab by remember { mutableStateOf<LegalDocumentTab?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    tapCount++
                    if (tapCount >= 5) {
                        tapCount = 0
                        onAdminClick?.invoke()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FAIT PAR",
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = tokens.muted.copy(alpha = 0.8f)
            )
            Text(
                text = stringResource(id = R.string.signature_brand),
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                color = tokens.text
            )
            Text(
                text = stringResource(id = R.string.signature_author),
                fontSize = 12.sp,
                color = tokens.muted
            )
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = { activeLegalTab = LegalDocumentTab.PRIVACY },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("footer_legal_privacy")
            ) {
                Text(
                    text = "Confidentialité",
                    fontSize = 11.5.sp,
                    color = tokens.accentReadable
                )
            }
            TextButton(
                onClick = { activeLegalTab = LegalDocumentTab.TERMS },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("footer_legal_terms")
            ) {
                Text(
                    text = "Conditions d’utilisation",
                    fontSize = 11.5.sp,
                    color = tokens.accentReadable
                )
            }
            TextButton(
                onClick = { activeLegalTab = LegalDocumentTab.LEGAL_NOTICE },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("footer_legal_notice")
            ) {
                Text(
                    text = "Mentions légales",
                    fontSize = 11.5.sp,
                    color = tokens.accentReadable
                )
            }
        }
    }

    if (activeLegalTab != null) {
        LegalDocumentsDialog(
            initialTab = activeLegalTab!!,
            onDismiss = { activeLegalTab = null }
        )
    }
}

fun Modifier.bringIntoViewOnIme(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(isFocused, imeBottom) {
        if (isFocused && imeBottom > 0) {
            val extraPx = with(density) { 20.dp.toPx() }
            val rect = if (fieldSize.width > 0 && fieldSize.height > 0) {
                Rect(
                    left = 0f,
                    top = -extraPx,
                    right = fieldSize.width.toFloat(),
                    bottom = fieldSize.height.toFloat() + extraPx
                )
            } else null
            requester.bringIntoView(rect)
        }
    }

    this
        .bringIntoViewRequester(requester)
        .onSizeChanged { fieldSize = it }
        .onFocusChanged { isFocused = it.isFocused }
}

