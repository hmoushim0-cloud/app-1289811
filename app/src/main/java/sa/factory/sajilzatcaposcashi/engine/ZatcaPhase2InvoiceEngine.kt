package sa.factory.sajilzatcaposcashi.engine

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

data class ZatcaInvoiceInput(
    val invoiceNumber: String,
    val sellerName: String,
    val vatRegistrationNumber: String,
    val invoiceDate: Date,
    val subtotalSar: Double,
    val vatRate: Double = 0.15,
    val previousInvoiceHash: String = "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMjRiNWVhODBlODQzNzcwOWZlZjU4Njg2MmQ3NGU1MQ=="
)

data class ZatcaInvoiceResult(
    val invoiceNumber: String,
    val icv: Long,
    val sellerName: String,
    val vatNumber: String,
    val formattedTimestamp: String,
    val subtotalSar: Double,
    val vatSar: Double,
    val totalSar: Double,
    val invoiceHashSha256: String,
    val tlvBase64Qr: String,
    val previousInvoiceHash: String
)

class ZatcaPhase2InvoiceEngine {

    private var invoiceCounter: Long = 0
    private var lastInvoiceHash: String = "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMjRiNWVhODBlODQzNzcwOWZlZjU4Njg2MmQ3NGU1MQ=="

    fun validateVatNumber(vatNumber: String): Boolean {
        val clean = vatNumber.trim()
        // Saudi VAT number: 15 digits, starts with 3 and ends with 3
        return clean.length == 15 && clean.startsWith("3") && clean.endsWith("3") && clean.all { it.isDigit() }
    }

    fun computeSha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun buildTlvBytes(tag: Int, value: String): ByteArray {
        val valBytes = value.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(tag)
        out.write(valBytes.size)
        out.write(valBytes)
        return out.toByteArray()
    }

    fun generateTlvBase64(
        sellerName: String,
        vatNumber: String,
        isoTimestamp: String,
        totalWithVat: String,
        vatAmount: String,
        invoiceHash: String,
        signature: String = "MEQCIQD4YjXgS2q0n1W...ZATCA_SIMULATED_ECDSA_SIG"
    ): String {
        val out = ByteArrayOutputStream()
        out.write(buildTlvBytes(1, sellerName))
        out.write(buildTlvBytes(2, vatNumber))
        out.write(buildTlvBytes(3, isoTimestamp))
        out.write(buildTlvBytes(4, totalWithVat))
        out.write(buildTlvBytes(5, vatAmount))
        out.write(buildTlvBytes(6, invoiceHash))
        out.write(buildTlvBytes(7, signature))

        val fullBytes = out.toByteArray()
        return Base64.getEncoder().encodeToString(fullBytes)
    }

    fun processInvoice(input: ZatcaInvoiceInput): ZatcaInvoiceResult {
        require(validateVatNumber(input.vatRegistrationNumber)) { "Invalid Saudi ZATCA VAT Number format: ${input.vatRegistrationNumber}" }

        invoiceCounter++
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestampStr = sdf.format(input.invoiceDate)

        val vatSar = input.subtotalSar * input.vatRate
        val totalSar = input.subtotalSar + vatSar

        val totalStr = String.format(Locale.US, "%.2f", totalSar)
        val vatStr = String.format(Locale.US, "%.2f", vatSar)

        val rawInvoiceCanonical = "${input.invoiceNumber}|$timestampStr|${input.sellerName}|${input.vatRegistrationNumber}|$totalStr|$vatStr|$lastInvoiceHash"
        val invoiceHash = computeSha256(rawInvoiceCanonical)

        val tlvQr = generateTlvBase64(
            sellerName = input.sellerName,
            vatNumber = input.vatRegistrationNumber,
            isoTimestamp = timestampStr,
            totalWithVat = totalStr,
            vatAmount = vatStr,
            invoiceHash = invoiceHash
        )

        val previousHash = lastInvoiceHash
        lastInvoiceHash = invoiceHash

        return ZatcaInvoiceResult(
            invoiceNumber = input.invoiceNumber,
            icv = invoiceCounter,
            sellerName = input.sellerName,
            vatNumber = input.vatRegistrationNumber,
            formattedTimestamp = timestampStr,
            subtotalSar = input.subtotalSar,
            vatSar = vatSar,
            totalSar = totalSar,
            invoiceHashSha256 = invoiceHash,
            tlvBase64Qr = tlvQr,
            previousInvoiceHash = previousHash
        )
    }

    fun getCurrentIcv(): Long = invoiceCounter
    fun getLastInvoiceHash(): String = lastInvoiceHash
}
