package sa.factory.sajilzatcaposcashi.engine

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

enum class PrinterPaperStatus {
    PAPER_PRESENT_OK,
    PAPER_NEAR_END_WARNING,
    PAPER_OUT_OF_SUPPLY,
    PRINTER_HEAD_OVERHEAT
}

data class EscPosPrintJob(
    val jobId: String,
    val byteCount: Int,
    val paperStatus: PrinterPaperStatus,
    val rawBytes: ByteArray,
    val summary: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EscPosPrintJob
        return jobId == other.jobId && rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = jobId.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

class EscPosThermalPrinterEngine {

    companion object {
        val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
        val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
        val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
        val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
        val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
        val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
        val CMD_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x10) // GS V B 16
        val LF = byteArrayOf(0x0A) // Line Feed
    }

    private var currentPaperStatus: PrinterPaperStatus = PrinterPaperStatus.PAPER_PRESENT_OK
    private var totalJobsPrinted: Int = 0

    fun setPaperStatus(status: PrinterPaperStatus) {
        currentPaperStatus = status
    }

    fun getPaperStatus(): PrinterPaperStatus = currentPaperStatus

    fun buildQrCodeBytes(payload: String): ByteArray {
        val out = ByteArrayOutputStream()
        val dataBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val len = dataBytes.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()

        // 1. Function 167: Set QR Model 2
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // 2. Function 169: Set QR Module size (6 dots)
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))
        // 3. Function 180: Store data in symbol storage area
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(dataBytes)
        // 4. Function 181: Print symbol data
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))

        return out.toByteArray()
    }

    fun generateZatcaReceiptBytes(invoice: ZatcaInvoiceResult): EscPosPrintJob {
        val out = ByteArrayOutputStream()

        // Initialize printer
        out.write(CMD_INIT)

        // Center aligned header
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write("${invoice.sellerName}\n".toByteArray(StandardCharsets.UTF_8))
        out.write("فاتورة ضريبية مبسطة (ZATCA)\n".toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write("الرقم الضريبي: ${invoice.vatNumber}\n".toByteArray(StandardCharsets.UTF_8))
        out.write("رقم الفاتورة: ${invoice.invoiceNumber}  (ICV: #${invoice.icv})\n".toByteArray(StandardCharsets.UTF_8))
        out.write("التاريخ: ${invoice.formattedTimestamp}\n".toByteArray(StandardCharsets.UTF_8))
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))

        // Financial totals
        out.write(CMD_ALIGN_LEFT)
        out.write("المبلغ الخاضع للضريبة:  ${String.format("%.2f", invoice.subtotalSar)} SAR\n".toByteArray(StandardCharsets.UTF_8))
        out.write("ضريبة القيمة المضافة 15%: ${String.format("%.2f", invoice.vatSar)} SAR\n".toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_ON)
        out.write("الإجمالي شامل الضريبة:   ${String.format("%.2f", invoice.totalSar)} SAR\n".toByteArray(StandardCharsets.UTF_8))
        out.write(CMD_BOLD_OFF)
        out.write("------------------------------------------\n".toByteArray(StandardCharsets.UTF_8))

        // Print ZATCA TLV QR code
        out.write(CMD_ALIGN_CENTER)
        out.write(buildQrCodeBytes(invoice.tlvBase64Qr))
        out.write(LF)
        out.write(LF)

        // Cut paper
        out.write(CMD_FEED_AND_CUT)

        totalJobsPrinted++
        val bytes = out.toByteArray()
        val jobId = "PRN-${System.currentTimeMillis() % 100000}"
        val summary = "ESC/POS Job #$jobId: ${bytes.size} bytes | ZATCA QR Generated | Status: $currentPaperStatus"

        return EscPosPrintJob(
            jobId = jobId,
            byteCount = bytes.size,
            paperStatus = currentPaperStatus,
            rawBytes = bytes,
            summary = summary
        )
    }

    fun getTotalJobsPrinted(): Int = totalJobsPrinted
}
