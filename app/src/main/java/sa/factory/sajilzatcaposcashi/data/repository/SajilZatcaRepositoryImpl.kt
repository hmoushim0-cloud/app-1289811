package sa.factory.sajilzatcaposcashi.data.repository

import sa.factory.sajilzatcaposcashi.domain.repository.SajilZatcaRepository
import sa.factory.sajilzatcaposcashi.hardware.HardwareSensorDriver
import sa.factory.sajilzatcaposcashi.model.SajilZatcaItem
import sa.factory.sajilzatcaposcashi.model.SajilZatcaOrder
import sa.factory.sajilzatcaposcashi.model.SajilZatcaTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SajilZatcaRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : SajilZatcaRepository {

    private val _catalog = MutableStateFlow<List<SajilZatcaItem>>(listOf(
        SajilZatcaItem("1", "الباقة التنفيذية المعتمدة", "تكامل مباشر مع ESC/POS Thermal Receipt Printer + Barcode Scanner", 180.0, "معتمد", "الخدمات الرئيسية"),
        SajilZatcaItem("2", "خدمة المعالجة الفورية", "أداء أصيل بسرعة 60 إطار في الثانية", 95.0, "فوري", "المراقبة الحية"),
        SajilZatcaItem("3", "باقة الصيانة والدعم السحابي", "تشفير كامل للبيانات المحلية ومزامنة آمنة", 250.0, "شامل", "التقارير الضريبية")
    ))
    override val catalogFlow: Flow<List<SajilZatcaItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<SajilZatcaItem>>(emptyList())
    override val activeCart: StateFlow<List<SajilZatcaItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<SajilZatcaTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            SajilZatcaTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: SajilZatcaItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: SajilZatcaItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): SajilZatcaOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = SajilZatcaOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
