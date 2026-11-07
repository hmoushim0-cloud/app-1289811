package sa.factory.sajilzatcaposcashi

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.sajilzatcaposcashi.data.repository.SajilZatcaRepositoryImpl
import sa.factory.sajilzatcaposcashi.engine.*
import sa.factory.sajilzatcaposcashi.hardware.HardwareSensorDriver
import sa.factory.sajilzatcaposcashi.model.SajilZatcaItem
import java.util.Date

class SajilZatcaRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = SajilZatcaRepositoryImpl(sensorDriver)

        val item1 = SajilZatcaItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = SajilZatcaItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testSaudiVatNumberValidation() {
        val engine = ZatcaPhase2InvoiceEngine()

        // Valid Saudi ZATCA VAT numbers (15 digits, starts and ends with 3)
        assertTrue(engine.validateVatNumber("310123456789003"))
        assertTrue(engine.validateVatNumber("300000000000003"))

        // Invalid VAT numbers (too short, wrong prefix/suffix, non-digits)
        assertFalse(engine.validateVatNumber("123456789012345"))
        assertFalse(engine.validateVatNumber("31012345678900"))
        assertFalse(engine.validateVatNumber("310123456789004"))
        assertFalse(engine.validateVatNumber("31012345678900A"))
    }

    @Test
    fun testZatcaSha256DigestCalculation() {
        val engine = ZatcaPhase2InvoiceEngine()
        val hash = engine.computeSha256("ZATCA_PHASE_2_TEST_CANONICAL_STRING")

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun testTlvTagByteEncoding() {
        val engine = ZatcaPhase2InvoiceEngine()
        val tagBytes = engine.buildTlvBytes(1, "سجل")

        // Tag 1 (1 byte), Length (1 byte = 6 UTF-8 bytes for 'سجل'), Value (6 bytes)
        assertEquals(1, tagBytes[0].toInt())
        assertEquals(6, tagBytes[1].toInt())
        assertEquals(8, tagBytes.size)
    }

    @Test
    fun testZatcaInvoiceProcessingAndTlvBase64() {
        val engine = ZatcaPhase2InvoiceEngine()
        val input = ZatcaInvoiceInput(
            invoiceNumber = "INV-2026-001",
            sellerName = "شركة سجل للحلول المالية",
            vatRegistrationNumber = "310123456789003",
            invoiceDate = Date(1730000000000L),
            subtotalSar = 1000.0
        )

        val result = engine.processInvoice(input)

        assertEquals("INV-2026-001", result.invoiceNumber)
        assertEquals(1L, result.icv)
        assertEquals(1000.0, result.subtotalSar, 0.01)
        assertEquals(150.0, result.vatSar, 0.01)
        assertEquals(1150.0, result.totalSar, 0.01)
        assertNotNull(result.tlvBase64Qr)
        assertTrue(result.tlvBase64Qr.isNotEmpty())
        assertEquals(64, result.invoiceHashSha256.length)
        assertEquals(result.invoiceHashSha256, engine.getLastInvoiceHash())
    }

    @Test
    fun testEscPosInitAndCutCommandGeneration() {
        assertEquals(0x1B.toByte(), EscPosThermalPrinterEngine.CMD_INIT[0])
        assertEquals(0x40.toByte(), EscPosThermalPrinterEngine.CMD_INIT[1])

        assertEquals(0x1D.toByte(), EscPosThermalPrinterEngine.CMD_FEED_AND_CUT[0])
        assertEquals(0x56.toByte(), EscPosThermalPrinterEngine.CMD_FEED_AND_CUT[1])
    }

    @Test
    fun testEscPosQrSymbolGeneration() {
        val engine = EscPosThermalPrinterEngine()
        val qrBytes = engine.buildQrCodeBytes("ZATCA_TLV_BASE64_SAMPLE")

        assertTrue(qrBytes.isNotEmpty())
        // Starts with Function 167 header
        assertEquals(0x1D.toByte(), qrBytes[0])
        assertEquals(0x28.toByte(), qrBytes[1])
        assertEquals(0x6B.toByte(), qrBytes[2])
    }

    @Test
    fun testZatcaReceiptByteCompilation() {
        val zatcaEngine = ZatcaPhase2InvoiceEngine()
        val printerEngine = EscPosThermalPrinterEngine()

        val invoice = zatcaEngine.processInvoice(
            ZatcaInvoiceInput(
                invoiceNumber = "INV-2026-002",
                sellerName = "شركة سجل للحلول المالية",
                vatRegistrationNumber = "310123456789003",
                invoiceDate = Date(),
                subtotalSar = 500.0
            )
        )

        val printJob = printerEngine.generateZatcaReceiptBytes(invoice)

        assertNotNull(printJob)
        assertTrue(printJob.byteCount > 100)
        assertEquals(PrinterPaperStatus.PAPER_PRESENT_OK, printJob.paperStatus)
        assertEquals(1, printerEngine.getTotalJobsPrinted())
    }

    @Test
    fun testPaperStatusTelemetry() {
        val engine = EscPosThermalPrinterEngine()
        assertEquals(PrinterPaperStatus.PAPER_PRESENT_OK, engine.getPaperStatus())

        engine.setPaperStatus(PrinterPaperStatus.PAPER_NEAR_END_WARNING)
        assertEquals(PrinterPaperStatus.PAPER_NEAR_END_WARNING, engine.getPaperStatus())
    }

    @Test
    fun testHardwareSensorDriverEmission() = runBlocking {
        val driver = HardwareSensorDriver()
        val frames = driver.observePeripheralStream().take(2).toList()

        assertEquals(2, frames.size)
        assertEquals("ESC/POS Thermal Receipt Printer + Barcode Scanner", frames[0].peripheralName)
        assertEquals("CONNECTING", frames[0].connectionState)
        assertEquals("ONLINE", frames[1].connectionState)
    }
}
