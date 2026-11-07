# تطبيق سجل زاتكا - نقطة بيع إلكترونية معتمدة (`sajil-zatca-pos-cashier-1289811`)
> منظومة نقاط البيع وإصدار الفواتير الضريبية المبسطة المتوافقة مع متطلبات هيئة الزكاة والضريبة والجمارك (ZATCA Phase 2) وطابعات ESC/POS الحرارية

- **Project ID**: `#1289811`
- **Commercial Archetype**: `pos_cashier`
- **Bound Hardware Peripheral**: `ESC/POS Thermal Receipt Printer + Barcode Scanner`
- **Package Name**: `sa.factory.sajilzatcaposcashi`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Clean Vector/ASCII)

---

## [Sidekick Autonomous Update]

### 1. Summary of Completed Enhancements
- **Resolved Kotlin Template Hazards**: Extracted fallback peripheral strings in Compose screens to prevent Kotlin single-quote interpolation compiler failures.
- **Modernized Compose Components**: Upgraded deprecated dividers to `HorizontalDivider` and added missing Material3 text formatting imports (`FontWeight`, `sp`).
- **Clean Invariant Enforcement**: Stripped unicode checkmarks and non-standard symbols in compliance with the Zero-Emoji enterprise standard.
- **Relocated Test Suite**: Moved regression and engine tests into `app/src/test/java/sa/factory/sajilzatcaposcashi/` for full Gradle JUnit runner integration.
- **Embedded Gradle Wrapper**: Bundled official Gradle wrapper binaries and comprehensive `.gitignore` rules for autonomous CI/CD compatibility.

### 2. Architecture & Domain Engines
- **ZatcaPhase2InvoiceEngine (`sa.factory.sajilzatcaposcashi.engine.ZatcaPhase2InvoiceEngine`)**:
  - Implements authentic ZATCA Phase 2 TLV (Tag-Length-Value) Base64 cryptographic QR code generator.
  - Verifies Saudi 15-digit VAT registration numbers starting and ending with 3.
  - Computes SHA-256 canonical invoice digests, sequential Invoice Counter Values (ICV), and blockchain-style Previous Invoice Hash (PIH) chaining.
- **EscPosThermalPrinterEngine (`sa.factory.sajilzatcaposcashi.engine.EscPosThermalPrinterEngine`)**:
  - Generates authentic ESC/POS binary protocols for 58mm/80mm thermal receipt printers (`ESC @`, `ESC a`, `GS !`, `GS V`).
  - Implements native ESC/POS 2D QR barcode symbols (Functions 167, 169, 180, 181).
  - Emits real-time paper status sensor telemetry (`PAPER_PRESENT_OK`, `PAPER_NEAR_END_WARNING`, `PAPER_OUT_OF_SUPPLY`).
- **HardwareSensorDriver Telemetry Integration**:
  - Emits continuous real-time state frames combining printer paper state, jobs processed, ICV counts, and TLV QR byte lengths.

### 3. Step-by-Step Build and Test Instructions
```bash
# Run comprehensive unit and regression test suite
./gradlew test

# Build debug APK for cloud test harness deployment
./gradlew assembleDebug
```
