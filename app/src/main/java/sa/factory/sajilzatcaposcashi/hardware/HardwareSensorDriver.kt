package sa.factory.sajilzatcaposcashi.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.sajilzatcaposcashi.engine.EscPosThermalPrinterEngine
import sa.factory.sajilzatcaposcashi.engine.ZatcaInvoiceInput
import sa.factory.sajilzatcaposcashi.engine.ZatcaPhase2InvoiceEngine
import java.util.Date

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    val zatcaEngine: ZatcaPhase2InvoiceEngine = ZatcaPhase2InvoiceEngine(),
    val printerEngine: EscPosThermalPrinterEngine = EscPosThermalPrinterEngine()
) {
    private val peripheral = "ESC/POS Thermal Receipt Printer + Barcode Scanner"

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "ESC/POS Controller: USB/Bluetooth Serial handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "ESC/POS Thermal Receipt Printer + Barcode Scanner: ONLINE | READY"))

        var cycle = 0
        while (true) {
            delay(1500)
            cycle++

            val invoice = zatcaEngine.processInvoice(
                ZatcaInvoiceInput(
                    invoiceNumber = "INV-2026-${1000 + cycle}",
                    sellerName = "شركة سجل للحلول الرقمية",
                    vatRegistrationNumber = "310123456789003",
                    invoiceDate = Date(),
                    subtotalSar = 150.0 + (cycle * 25.0)
                )
            )

            val printJob = printerEngine.generateZatcaReceiptBytes(invoice)
            val payload = "PRINTER: ${printJob.paperStatus} | JOBS: ${printerEngine.getTotalJobsPrinted()} | LAST_ICV: #${invoice.icv} [TOTAL: ${String.format("%.2f", invoice.totalSar)} SAR] | TLV_LEN: ${invoice.tlvBase64Qr.length}B"

            emit(HardwareTelemetryFrame(
                peripheralName = peripheral,
                connectionState = "ACTIVE_STREAMING",
                livePayload = payload
            ))
        }
    }
}
