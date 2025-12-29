package com.example.uvccamerademo

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class UvcQrScanner(
    private val onScanned: (String) -> Unit
) {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient(options)
    private val active = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private val lastScanAt = AtomicLong(0L)
    @Volatile
    private var lastPayload: String? = null

    fun setActive(enabled: Boolean) {
        active.set(enabled)
        if (!enabled) {
            lastPayload = null
        }
    }

    fun onFrame(data: ByteArray, width: Int, height: Int, rotationDegrees: Int) {
        if (!active.get()) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastScanAt.get() < MIN_SCAN_INTERVAL_MS) {
            return
        }
        if (!processing.compareAndSet(false, true)) {
            return
        }
        lastScanAt.set(now)
        val expectedSize = width * height * 3 / 2
        if (data.size < expectedSize) {
            processing.set(false)
            return
        }
        val copy = data.copyOf(expectedSize)
        val image = InputImage.fromByteArray(
            copy,
            width,
            height,
            rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (!raw.isNullOrBlank() && active.get()) {
                    if (raw != lastPayload) {
                        lastPayload = raw
                        active.set(false)
                        onScanned(raw)
                    }
                }
            }
            .addOnCompleteListener {
                processing.set(false)
            }
    }

    fun release() {
        scanner.close()
    }

    companion object {
        private const val MIN_SCAN_INTERVAL_MS = 600L
    }
}
