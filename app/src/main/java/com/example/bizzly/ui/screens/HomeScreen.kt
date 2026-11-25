package com.example.bizzly.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.domain.ProductSalesItem
import com.example.bizzly.domain.StockAlertLevel
import com.example.bizzly.model.BizzlyPermissions
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.components.HeroPrimaryHighlight
import com.example.bizzly.ui.components.InitialAvatar
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val chartPalette = listOf(
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFF59E0B),
    Color(0xFFFF6B6B),
    Color(0xFF7C3AED),
    Color(0xFF06B6D4)
)

@Composable
fun HomeScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val data = state.businessData
    val summary = BizzlyCalculator.calculateSummary(data)
    val today = BizzlyCalculator.todayIso()
    val todayRev = BizzlyCalculator.dayTotal(data, today)
    val roleLabel = state.activeProfile?.let { p ->
        val r = BizzlyRole.fromKey(p.role)
        if (p.name == r.label) r.label else "${p.name} · ${r.label}"
    }
    val lowStock = BizzlyCalculator.lowStockProducts(data)

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Bonjour · ${BizzlyCalculator.formatDateFr(today)}",
            account = state.activeAccount,
            roleLabel = roleLabel,
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            HeroPrimaryHighlight(
                pillText = "Reste à verser",
                bigValue = BizzlyCalculator.formatFcfa(summary.remainingToRemit),
                caption = "Recettes − dépenses − bons − déjà versé",
                valueColor = if (summary.remainingToRemit < 0) Color(0xFFFFD6D6) else tokens.onPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Today's sales card
            val prevDays = data.sv.keys.sorted().filter { it < today && BizzlyCalculator.dayTotal(data, it) > 0.0 }
            val prevRev = prevDays.lastOrNull()?.let { BizzlyCalculator.dayTotal(data, it) } ?: 0.0
            val deltaPct = if (prevRev > 0.0 && todayRev > 0.0) {
                (((todayRev / prevRev) - 1.0) * 100.0).roundToInt()
            } else null

            BizzlyCard(
                onClick = { viewModel.selectTab(BizzlyTab.SALES) },
                modifier = Modifier.testTag("home_today_sales_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ventes d’aujourd’hui",
                            fontSize = 13.sp,
                            color = tokens.muted
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = BizzlyCalculator.formatFcfa(todayRev),
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = tokens.text
                            )
                            if (deltaPct != null) {
                                val positive = todayRev >= prevRev
                                val badgeBg = if (positive) tokens.ok.copy(alpha = 0.14f) else tokens.ko.copy(alpha = 0.14f)
                                val badgeFg = if (positive) tokens.ok else tokens.ko
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${if (positive) "▲" else "▼"} ${abs(deltaPct)} %",
                                        color = badgeFg,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Ventes",
                        tint = tokens.accentReadable,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // 2x2 KPI grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    icon = Icons.Default.Download,
                    color = tokens.primary,
                    label = "Recettes",
                    value = BizzlyCalculator.formatFcfa(summary.recordedRevenue),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    icon = Icons.AutoMirrored.Filled.Send,
                    color = Color(0xFF16A34A),
                    label = "Versé",
                    value = BizzlyCalculator.formatFcfa(summary.remitted),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    color = tokens.accent,
                    label = "Dépenses",
                    value = BizzlyCalculator.formatFcfa(summary.expenses),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    icon = Icons.Default.Groups,
                    color = tokens.warn,
                    label = "Bons du personnel",
                    value = BizzlyCalculator.formatFcfa(summary.vouchers),
                    modifier = Modifier.weight(1f)
                )
            }

            // Low stock alert card
            if (lowStock.isNotEmpty()) {
                val hasCritical = lowStock.any { it.second.level == StockAlertLevel.CRITICAL }
                BizzlyCard(
                    borderColor = if (hasCritical) tokens.ko else tokens.warn,
                    onClick = { viewModel.selectTab(BizzlyTab.STOCK) },
                    modifier = Modifier.testTag("home_low_stock_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(if (hasCritical) tokens.ko else tokens.warn)
                        )
                        Text(
                            text = "Risque de rupture : ${lowStock.size} produit${if (lowStock.size > 1) "s" else ""}",
                            fontWeight = FontWeight.Bold,
                            color = tokens.text
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lowStock.take(6).joinToString(" · ") { (p, info) ->
                            "${p.n} (${BizzlyCalculator.formatQty(info.current)})"
                        } + if (lowStock.size > 6) " …" else "",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                }
            }

            // Empty catalog CTA or Backup reminder card
            if (viewModel.can(BizzlyPermissions.BILAN)) {
                if (data.prod.isEmpty()) {
                    BizzlyCard(borderColor = tokens.accentReadable) {
                        Text("Commencez par vos produits", fontWeight = FontWeight.Bold, color = tokens.text)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ajoutez les boissons et plats que vous vendez, avec leurs prix.",
                            fontSize = 13.sp,
                            color = tokens.muted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.selectTab(BizzlyTab.PROD) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.primary,
                                contentColor = tokens.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("home_add_products_cta")
                        ) {
                            Text("Ajouter mes produits", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (data.log.isNotEmpty()) {
                    val daysSinceBackup = if (data.lbk.isNotBlank()) {
                        runCatching {
                            ChronoUnit.DAYS.between(LocalDate.parse(data.lbk), LocalDate.now()).toInt()
                        }.getOrDefault(999)
                    } else 999
                    if (daysSinceBackup >= 3) {
                        BizzlyCard(borderColor = tokens.accentReadable) {
                            Text("Sauvegarde conseillée", fontWeight = FontWeight.Bold, color = tokens.text)
                            Spacer(modifier = Modifier.height(4.dp))
                            val prefix = if (data.lbk.isNotBlank()) {
                                "Dernière sauvegarde il y a $daysSinceBackup jours."
                            } else {
                                "Aucune sauvegarde faite sur cet appareil."
                            }
                            Text(
                                text = "$prefix Envoyez-la sur WhatsApp, par e-mail ou sur Drive pour ne rien perdre.",
                                fontSize = 13.sp,
                                color = tokens.muted
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = viewModel::exportBackupFile,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.primary,
                                    contentColor = tokens.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_backup_cta")
                            ) {
                                Text("Envoyer la sauvegarde", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Stock anomaly / gap cards
            if (summary.anomalies.isNotEmpty()) {
                BizzlyCard(borderColor = tokens.ko) {
                    Text(
                        text = "⚠ Stock fin trop élevé pour : ${summary.anomalies.joinToString(", ")}. Vérifiez la saisie.",
                        color = tokens.ko,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
            if (summary.stockGap != 0.0) {
                val isShortage = summary.stockGap > 0.0
                BizzlyCard(borderColor = if (isShortage) tokens.ko else tokens.ok) {
                    Text(
                        text = (if (isShortage) "⚠ Manquant en stock : " else "Excédent en stock : ") +
                            BizzlyCalculator.formatFcfa(abs(summary.stockGap)),
                        color = if (isShortage) tokens.ko else tokens.ok,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Comptage réel comparé aux ventes notées",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                }
            }

            // Performance chart
            SectionHeading("Performance des ventes")
            BizzlyCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 J", 30 to "30 J", 0 to "Tout").forEach { (days, label) ->
                        val selected = state.chartRangeDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) tokens.primary else tokens.background)
                                .border(
                                    1.dp,
                                    if (selected) tokens.primary else tokens.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setChartRangeDays(days) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("chart_range_$days")
                        ) {
                            Text(
                                text = label,
                                color = if (selected) tokens.onPrimary else tokens.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val dates = data.sv.keys.sorted()
                    .filter { BizzlyCalculator.dayTotal(data, it) > 0.0 }
                    .let { list -> if (state.chartRangeDays > 0) list.takeLast(state.chartRangeDays) else list }
                if (dates.size < 2) {
                    Text(
                        text = "Notez les ventes d’au moins 2 jours pour voir la courbe.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                } else {
                    val points = dates.map {
                        BizzlyCalculator.formatDateFr(it).take(5) to BizzlyCalculator.dayTotal(data, it)
                    }
                    SalesSplineAreaChart(points = points)
                }
            }

            // Top products
            SectionHeading("Produits les plus vendus")
            BizzlyCard {
                if (summary.topSales.isEmpty()) {
                    Text(
                        text = "Saisissez des ventes pour voir les statistiques.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                } else {
                    val totalSum = summary.topSales.sumOf { it.amount }.coerceAtLeast(1.0)
                    val top6 = summary.topSales.take(6)
                    top6.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InitialAvatar(
                                letter = item.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                size = 40.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${BizzlyCalculator.formatQty(item.quantity)} vendus",
                                    fontSize = 12.5.sp,
                                    color = tokens.muted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = BizzlyCalculator.formatFcfa(item.amount),
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.text
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(tokens.ok.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${((item.amount / totalSum) * 100).roundToInt()} %",
                                        color = tokens.ok,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (idx < top6.lastIndex) {
                            HorizontalDivider(color = tokens.border)
                        }
                    }
                }
            }

            // Sales distribution donut
            SectionHeading("Répartition des ventes")
            BizzlyCard {
                if (summary.topSales.isEmpty()) {
                    Text(
                        text = "Aucune vente pour l’instant.",
                        fontSize = 13.sp,
                        color = tokens.muted
                    )
                } else {
                    SalesDonutChart(items = summary.topSales)
                }
            }

            // Recent activity feed
            SectionHeading("Activité récente")
            BizzlyCard {
                if (data.log.isEmpty()) {
                    Text("Aucune opération enregistrée.", fontSize = 13.sp, color = tokens.muted)
                } else {
                    val logs = data.log.take(6)
                    logs.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.m,
                                fontSize = 13.5.sp,
                                color = tokens.text,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = BizzlyCalculator.formatDateTimeFr(entry.t).takeLast(5),
                                fontSize = 12.sp,
                                color = tokens.muted
                            )
                        }
                        if (idx < logs.lastIndex) {
                            HorizontalDivider(color = tokens.border)
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::closePeriod,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("close_period_btn")
            ) {
                Text("Clôturer la période et recommencer", fontWeight = FontWeight.SemiBold)
            }

            BizzlySignatureFooter()
        }
    }
}

@Composable
fun SectionHeading(text: String) {
    val tokens = LocalBizzlyTokens.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = tokens.text,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

@Composable
private fun KpiCard(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    BizzlyCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = tokens.muted
        )
        Text(
            text = value,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tokens.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SalesSplineAreaChart(points: List<Pair<String, Double>>) {
    val tokens = LocalBizzlyTokens.current
    val density = LocalDensity.current
    val maxVal = max(1.0, points.maxOf { it.second } * 1.15)
    val prColor = tokens.primary
    val mutedArgb = tokens.muted.toArgb()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(165.dp)
    ) {
        val padX = with(density) { 14.dp.toPx() }
        val topPad = with(density) { 42.dp.toPx() }
        val bottomPad = with(density) { 24.dp.toPx() }
        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(20f)
        val chartW = (size.width - 2f * padX).coerceAtLeast(20f)
        val baseY = size.height - bottomPad

        val coords = points.mapIndexed { idx, (_, v) ->
            val x = padX + idx * chartW / (points.size - 1).coerceAtLeast(1)
            val y = baseY - ((v / maxVal).toFloat() * chartH)
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) {
                val a = coords[i - 1]
                val b = coords[i]
                val cx = (a.x + b.x) / 2f
                cubicTo(cx, a.y, cx, b.y, b.x, b.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(coords.last().x, baseY)
            lineTo(coords.first().x, baseY)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(prColor.copy(alpha = 0.45f), Color.Transparent),
                startY = topPad,
                endY = baseY
            )
        )
        drawPath(
            path = linePath,
            color = prColor,
            style = Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
        )

        val lastPt = coords.last()
        drawLine(
            color = prColor.copy(alpha = 0.6f),
            start = lastPt,
            end = Offset(lastPt.x, baseY),
            strokeWidth = with(density) { 1.2.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )
        drawCircle(
            color = prColor.copy(alpha = 0.28f),
            radius = with(density) { 9.dp.toPx() },
            center = lastPt
        )
        drawCircle(
            color = Color.White,
            radius = with(density) { 4.5.dp.toPx() },
            center = lastPt
        )

        // Callout tooltip above last point
        val boxW = with(density) { 104.dp.toPx() }
        val boxH = with(density) { 34.dp.toPx() }
        val bx = (lastPt.x - boxW / 2f).coerceIn(2f, size.width - boxW - 2f)
        val by = (lastPt.y - boxH - with(density) { 8.dp.toPx() }).coerceAtLeast(2f)
        drawRoundRect(
            color = Color(0xFF0A1F44),
            topLeft = Offset(bx, by),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
        )

        val nc = drawContext.canvas.nativeCanvas
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF94A3B8).toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = with(density) { 12.sp.toPx() }
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        nc.drawText(points.last().first, bx + boxW / 2f, by + with(density) { 13.dp.toPx() }, datePaint)
        nc.drawText(
            BizzlyCalculator.formatFcfa(points.last().second),
            bx + boxW / 2f,
            by + with(density) { 27.dp.toPx() },
            valPaint
        )

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedArgb
            textSize = with(density) { 10.5.sp.toPx() }
            textAlign = Paint.Align.LEFT
        }
        nc.drawText(points.first().first, padX, size.height - 4f, axisPaint)
        axisPaint.textAlign = Paint.Align.RIGHT
        nc.drawText(points.last().first, size.width - padX, size.height - 4f, axisPaint)
    }
}

@Composable
private fun SalesDonutChart(items: List<ProductSalesItem>) {
    val tokens = LocalBizzlyTokens.current
    val total = items.sumOf { it.amount }.coerceAtLeast(1.0)
    val top5 = items.take(5)
    val restAmt = total - top5.sumOf { it.amount }
    val groups = if (restAmt > 0.5) {
        top5 + ProductSalesItem(-1L, "Autres", restAmt, 0.0)
    } else top5

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(136.dp)) {
                val strokeW = 24.dp.toPx()
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                val topLeft = Offset(strokeW / 2f, strokeW / 2f)
                drawArc(
                    color = tokens.border,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW)
                )
                var startAngle = -90f
                groups.forEachIndexed { idx, g ->
                    val sweep = ((g.amount / total) * 360.0).toFloat()
                    drawArc(
                        color = chartPalette[idx % chartPalette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW)
                    )
                    startAngle += sweep
                }
            }
            Text(
                text = "${groups.size} groupes",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = tokens.text
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEachIndexed { idx, g ->
                val pct = ((g.amount / total) * 100).roundToInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(chartPalette[idx % chartPalette.size])
                    )
                    Text(
                        text = "${g.label} $pct%",
                        fontSize = 12.5.sp,
                        color = tokens.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
