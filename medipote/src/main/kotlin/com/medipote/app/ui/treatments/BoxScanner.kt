package com.medipote.app.ui.treatments

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Reads the code of a medicine box with Google's code scanner, which runs in Google Play services:
 * the app asks for no camera permission and no Internet access, and nothing is sent anywhere.
 */
object BoxScanner {
    fun scan(context: Context, onCode: (String) -> Unit, onFail: () -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_EAN_13)
            .build()
        runCatching {
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { code -> onCode(code.rawValue.orEmpty()) }
                .addOnFailureListener { onFail() }
        }.onFailure { onFail() }
    }
}
