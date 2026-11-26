package com.example.bizzly

import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.data.local.InventoryStockStatus
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.domain.StockAlertLevel
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessData
import com.example.bizzly.model.CashMovement
import com.example.bizzly.model.OpenTable
import com.example.bizzly.model.PaymentPart
import com.example.bizzly.model.PrinterPaperWidth
import com.example.bizzly.model.Product
import com.example.bizzly.model.RestockOrderLine
import com.example.bizzly.security.PinSecurity
import com.example.bizzly.util.EscPosPrinter
import com.example.bizzly.util.ExportUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class BizzlyCoreTests {

    @Test
    fun pinHashingAndWeakPinCheck() {
        assertTrue(PinSecurity.isWeakPin("1234"))
        assertTrue(PinSecurity.isWeakPin("0000"))
        assertTrue(PinSecurity.isWeakPin("9999"))
        assertFalse(PinSecurity.isWeakPin("4829"))

        val (salt, hash) = PinSecurity.createPinHash("4829")
        assertEquals(64, hash.length)
        val computed = PinSecurity.hashPin(salt, "4829", 2)
        assertEquals(hash, computed)
        assertFalse(hash == PinSecurity.hashPin(salt, "4820", 2))
    }

    @Test
    fun stockAndCashSummaryCalculations() {
        val data = BusinessData(
            prod = listOf(
                Product(id = 1L, n = "CASTEL GM", pv = 750.0, d = 10.0, e = 5.0, f = 12.0),
                Product(id = 2L, n = "EAU MINERALE", pv = 500.0, d = 5.0, e = 0.0, f = null)
            ),
            sv = mapOf(
                "2026-11-25" to mapOf("1" to 4.0, "2" to 2.0)
            ),
            dep = listOf(
                CashMovement(d = "2026-11-25", w = "Glace", m = 1000.0),
                CashMovement(d = "2026-11-25", w = "Achat Castel", m = 1500.0, pid = 1L, q = 2.0)
            ),
            vers = listOf(
                CashMovement(d = "2026-11-25", w = "Patron", m = 1000.0)
            ),
            bons = listOf(
                CashMovement(d = "2026-11-25", w = "Serveur", m = 250.0)
            ),
            seuil = 10
        )

        val castelInfo = BizzlyCalculator.stockInfo(data, data.prod[0])
        assertEquals(13.0, castelInfo.theoretical, 0.01) // 10 + 5 + 2 - 4 = 13
        assertEquals(12.0, castelInfo.current, 0.01)
        assertEquals(5.0, castelInfo.soldEffective, 0.01)

        val eauInfo = BizzlyCalculator.stockInfo(data, data.prod[1])
        assertEquals(3.0, eauInfo.current, 0.01)
        assertEquals(StockAlertLevel.CRITICAL, eauInfo.level)

        val summary = BizzlyCalculator.calculateSummary(data)
        assertEquals(4000.0, summary.recordedRevenue, 0.01) // 4*750 + 2*500
        assertEquals(2500.0, summary.expenses, 0.01)
        assertEquals(1000.0, summary.remitted, 0.01)
        assertEquals(250.0, summary.vouchers, 0.01)
        assertEquals(250.0, summary.remainingToRemit, 0.01) // 4000 - 2500 - 250 - 1000
        assertEquals(750.0, summary.stockGap, 0.01) // (13 - 12) * 750
    }

    @Test
    fun excelExportGeneratesValidTenSheetXlsx() {
        val data = BusinessData(
            prod = listOf(
                Product(id = 1L, n = "CASTEL GM & Citron", pv = 750.0, d = 5.0, e = 2.0, f = 4.0)
            ),
            sv = mapOf("2026-11-25" to mapOf("1" to 3.0)),
            dep = listOf(CashMovement(d = "2026-11-25", w = "Glace <sac>", m = 500.0)),
            vers = listOf(CashMovement(d = "2026-11-25", w = "Patron", m = 1500.0)),
            bons = listOf(CashMovement(d = "2026-11-25", w = "Serveur \"A\"", m = 250.0))
        )
        val bytes = ExportUtils.generateExcelBytes(data)
        assertTrue(bytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        assertNotNull(entries["xl/workbook.xml"])
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet10.xml"))
        assertTrue(entries["xl/worksheets/sheet1.xml"]!!.contains("CASTEL GM &amp; Citron"))
        assertTrue(entries["xl/worksheets/sheet6.xml"]!!.contains("Glace &lt;sac&gt;"))
        assertTrue(entries["xl/worksheets/sheet8.xml"]!!.contains("Serveur &quot;A&quot;"))
    }

    @Test
    fun qrPayloadAndFilenameSanitization() {
        val data = BusinessData(
            prod = listOf(Product(id = 1L, n = "Café Crème", pv = 500.0))
        )
        val payload = ExportUtils.buildQrMenuPayload(null, data)
        assertTrue(payload.contains("Cafe Creme 500"))
        assertEquals("facture_bar_1_2026.pdf", ExportUtils.sanitizeFilename("facture/bar:1?2026.pdf"))
        assertEquals("facture_bar_table-1.pdf", ExportUtils.sanitizeFilename("facture/bar:table-1.pdf"))
    }

    @Test
    fun adminCredentialHashingAndVerification() {
        val (salt, hash) = PinSecurity.createCredentialHash("Kltur!2026")
        assertEquals(18, salt.length)
        assertEquals(64, hash.length)
        assertTrue(PinSecurity.verifyCredential(salt, hash, "Kltur!2026"))
        assertFalse(PinSecurity.verifyCredential(salt, hash, "kltur!2026"))
        assertFalse(PinSecurity.verifyCredential(salt, hash, ""))
        assertFalse(PinSecurity.verifyCredential("", hash, "Kltur!2026"))
    }

    @Test
    fun logoPixelExtractionProducesThreeDistinctHexProposals() {
        val hexRegex = Regex("^#[0-9A-F]{6}$")
        val red = 0xFFD92626.toInt()
        val navy = 0xFF0F1E36.toInt()
        val white = 0xFFFFFFFF.toInt()
        val transparent = 0x00000000

        val pixels = IntArray(400) { idx ->
            when {
                idx < 140 -> red
                idx < 240 -> navy
                idx < 320 -> white
                else -> transparent
            }
        }

        val proposals = BizzlyCalculator.extractBrandThemeProposalsFromPixels(pixels)
        assertEquals(3, proposals.size)
        proposals.forEach { proposal ->
            assertTrue(proposal.name.isNotBlank())
            assertTrue("Invalid primary ${proposal.brand.pr}", hexRegex.matches(proposal.brand.pr))
            assertTrue("Invalid secondary ${proposal.brand.sec}", hexRegex.matches(proposal.brand.sec))
            assertTrue("Invalid accent ${proposal.brand.ax}", hexRegex.matches(proposal.brand.ax))
        }
        val distinctPalettes = proposals.map { "${it.brand.pr}:${it.brand.sec}:${it.brand.ax}" }.toSet()
        assertEquals(3, distinctPalettes.size)

        // Pure white/transparent image yields empty proposals cleanly
        val emptyPixels = IntArray(64) { if (it % 2 == 0) white else transparent }
        assertTrue(BizzlyCalculator.extractBrandThemeProposalsFromPixels(emptyPixels).isEmpty())

        // Verify HSL -> RGB conversion produces valid red & green primaries
        assertEquals(Triple(255, 0, 0), BizzlyCalculator.hslToRgb(0f, 1f, 0.5f))
        assertEquals(Triple(0, 255, 0), BizzlyCalculator.hslToRgb(120f, 1f, 0.5f))
    }

    @Test
    fun inventoryItemThresholdAlerts() {
        val outOfStock = InventoryItemEntity(
            id = 1L,
            businessId = "b1",
            name = "GLAÇONS",
            quantity = 0.0,
            minThreshold = 10.0
        )
        assertEquals(InventoryStockStatus.OUT_OF_STOCK, outOfStock.stockStatus())
        assertTrue(outOfStock.isBelowThreshold)

        val lowStock = InventoryItemEntity(
            id = 2L,
            businessId = "b1",
            name = "CASTEL GM",
            quantity = 9.0,
            minThreshold = 10.0
        )
        assertEquals(InventoryStockStatus.LOW_STOCK, lowStock.stockStatus())
        assertTrue(lowStock.isBelowThreshold)

        val atThreshold = InventoryItemEntity(
            id = 3L,
            businessId = "b1",
            name = "MOCAF GM",
            quantity = 10.0,
            minThreshold = 10.0
        )
        assertEquals(InventoryStockStatus.HEALTHY, atThreshold.stockStatus())
        assertFalse(atThreshold.isBelowThreshold)
    }

    @Test
    fun tableBillingBreakdownWithCompedDiscountsAndPartialPayments() {
        val data = BusinessData(
            prod = listOf(
                Product(id = 1L, n = "CASTEL GM", pv = 1000.0),
                Product(id = 2L, n = "EAU", pv = 500.0)
            )
        )
        val tablePercent = OpenTable(
            sv = 1L,
            it = mapOf("1" to 4.0, "2" to 2.0), // gross = 4000 + 1000 = 5000
            comped = mapOf("1" to 1.0), // compedDiscount = 1000 -> afterComped = 4000
            discountType = "percent",
            discountValue = 10.0, // tableDiscount = 400 -> netTotal = 3600
            discountReason = "VIP",
            paidPartial = 1500.0
        )
        val b1 = BizzlyCalculator.calculateTableBreakdown(data, tablePercent)
        assertEquals(5000.0, b1.grossSubtotal, 0.01)
        assertEquals(1000.0, b1.compedDiscount, 0.01)
        assertEquals(4000.0, b1.afterComped, 0.01)
        assertEquals(400.0, b1.tableDiscount, 0.01)
        assertEquals(3600.0, b1.netTotal, 0.01)
        assertEquals(1500.0, b1.paidSoFar, 0.01)
        assertEquals(2100.0, b1.remainingDue, 0.01)

        val tableFixedOvercap = tablePercent.copy(
            discountType = "amount",
            discountValue = 9999.0,
            paidPartial = 0.0
        )
        val b2 = BizzlyCalculator.calculateTableBreakdown(data, tableFixedOvercap)
        assertEquals(4000.0, b2.tableDiscount, 0.01)
        assertEquals(0.0, b2.remainingDue, 0.01)
    }

    @Test
    fun suggestedRestockOrderQtyAndExcelExport() {
        assertEquals(15.0, BizzlyCalculator.suggestedRestockQty(currentQty = 5.0, minThreshold = 10.0), 0.01)
        assertEquals(20.0, BizzlyCalculator.suggestedRestockQty(currentQty = 0.0, minThreshold = 10.0), 0.01)
        assertEquals(10.0, BizzlyCalculator.suggestedRestockQty(currentQty = 10.0, minThreshold = 10.0), 0.01)
        assertEquals(1.0, BizzlyCalculator.suggestedRestockQty(currentQty = 25.0, minThreshold = 10.0), 0.01)

        val account = BusinessAccount(id = "acc1", ident = "bizzly-bar", name = "Bizzly Bar")
        val lines = listOf(
            RestockOrderLine(
                itemId = 1L,
                name = "CASTEL & Cie",
                sku = "CAS-01",
                category = "Bières",
                unit = "bouteille",
                currentQty = 4.0,
                minThreshold = 10.0,
                suggestedQty = 16.0,
                unitPrice = 600.0
            )
        )
        val bytes = ExportUtils.generateRestockOrderExcel(account, "Brasserie Locale", lines)
        assertTrue(bytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        val sheet1 = entries["xl/worksheets/sheet1.xml"]
        val sheet2 = entries["xl/worksheets/sheet2.xml"]
        assertNotNull(sheet1)
        assertNotNull(sheet2)
        assertTrue(sheet1!!.contains("CASTEL &amp; Cie"))
        assertTrue(sheet2!!.contains("Brasserie Locale"))
    }

    @Test
    fun escPosThermalReceiptAndKitchenTicketGeneration() {
        val account = BusinessAccount(id = "acc1", ident = "bar", name = "Café Central", tel = "+225 01020304")
        val data = BusinessData(
            prod = listOf(Product(id = 1L, n = "Café Crème", pv = 1000.0))
        )
        val table = OpenTable(
            sv = 0L,
            it = mapOf("1" to 3.0),
            comped = mapOf("1" to 1.0),
            discountType = "amount",
            discountValue = 200.0,
            discountReason = "Geste"
        )
        val receipt58 = EscPosPrinter.buildTableReceipt(
            account = account,
            data = data,
            tableNumber = 4,
            table = table,
            paymentLabel = "Mixte",
            payments = listOf(PaymentPart("Espèces", 1000.0), PaymentPart("Mobile Money", 800.0)),
            cashGiven = 1500.0,
            isProforma = false,
            width = PrinterPaperWidth.MM_58
        )
        val text58 = receipt58.toString(Charsets.US_ASCII)
        assertTrue(text58.contains("TICKET DE CAISSE - TABLE 4"))
        assertTrue(text58.contains("Cafe Creme"))
        assertTrue(text58.contains("dont 1 offert(s)"))
        assertTrue(text58.contains("Motif: Geste"))
        assertTrue(text58.contains("Paiement mixte"))

        val kitchen80 = EscPosPrinter.buildKitchenBarTicket(
            account = account,
            data = data,
            tableNumber = 4,
            table = table,
            width = PrinterPaperWidth.MM_80
        )
        val textKitchen = kitchen80.toString(Charsets.US_ASCII)
        assertTrue(textKitchen.contains("BON CUISINE / BAR"))
        assertTrue(textKitchen.contains("TABLE 4"))
        assertTrue(textKitchen.contains("3x Cafe Creme"))
    }

    @Test
    fun hashedAdminInstallCodeVerifiesWithoutPlaintextLiteral() {
        assertTrue(PinSecurity.verifyAdminInstallCode("SETUP-VBZR-9AF0-NW4M-DQG2"))
        assertTrue(PinSecurity.verifyAdminInstallCode("  setup-vbzr-9af0-nw4m-dqg2 "))
        assertFalse(PinSecurity.verifyAdminInstallCode("SETUP-0000-0000-0000-0000"))
        assertFalse(PinSecurity.verifyAdminInstallCode(""))
    }

    @Test
    fun xlsxExportNeutralizesSpreadsheetFormulaPayloads() {
        assertEquals("'=CMD|' /C calc'!A0", ExportUtils.sanitizeSpreadsheetCell("=CMD|' /C calc'!A0"))
        assertEquals("'+SUM(A1:A2)", ExportUtils.sanitizeSpreadsheetCell("+SUM(A1:A2)"))
        assertEquals("'-1+1", ExportUtils.sanitizeSpreadsheetCell("-1+1"))
        assertEquals("'@SUM(1,2)", ExportUtils.sanitizeSpreadsheetCell("@SUM(1,2)"))
        assertEquals("'\t=1+1", ExportUtils.sanitizeSpreadsheetCell("\t=1+1"))
        assertEquals("CASTEL GM", ExportUtils.sanitizeSpreadsheetCell("CASTEL GM"))

        val data = BusinessData(
            prod = listOf(Product(id = 1L, n = "=HYPERLINK(\"https://evil.test\")", pv = 500.0)),
            dep = listOf(CashMovement(d = "2026-11-26", w = "+1+1", m = 500.0))
        )
        val bytes = ExportUtils.generateExcelBytes(data)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        assertTrue(entries["xl/worksheets/sheet5.xml"]!!.contains("&apos;=HYPERLINK("))
        assertTrue(entries["xl/worksheets/sheet6.xml"]!!.contains("&apos;+1+1"))
    }

    @Test
    fun boundedStreamReaderRejectsOversizedBackup() {
        val small = ByteArrayInputStream("{\"bz\":2}".toByteArray(Charsets.UTF_8))
        assertEquals("{\"bz\":2}", com.example.bizzly.data.repository.BizzlyRepository.readStreamBoundedUtf8(small, 64))

        val huge = ByteArrayInputStream(ByteArray(128) { 'a'.code.toByte() })
        assertEquals(null, com.example.bizzly.data.repository.BizzlyRepository.readStreamBoundedUtf8(huge, 64))
    }

    @Test
    fun legalDocumentsProvideNonEmptySectionsForAllTabs() {
        com.example.bizzly.ui.components.LegalDocumentTab.entries.forEach { tab ->
            val sections = com.example.bizzly.ui.components.LegalDocumentsContent.sectionsFor(tab)
            assertTrue("Sections empty for $tab", sections.isNotEmpty())
            assertTrue(sections.all { it.heading.isNotBlank() && it.body.isNotBlank() })
            val joined = sections.joinToString("\n") { "${it.heading}\n${it.body}" }
            assertTrue(joined.contains("KLTUR COORPORATION"))
            assertTrue(joined.contains("Bizzly"))
        }
    }
}



