package com.meshtastic.android.meshserviceexample.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class BleScanner(private val context: Context) {

    // 放在 class BleScanner{} 內
    @JvmOverloads
    fun startScan(onFound: OnFound, durationMs: Long = 10_000L) = start(onFound, durationMs)

    fun interface OnFound { fun onDevice(result: ScanResult) }

    private val manager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var cb: ScanCallback? = null

    @JvmOverloads
    fun start(onFound: OnFound, durationMs: Long = 10_000L) {
        if (cb != null) return

        val filters: List<ScanFilter> = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onFound.onDevice(result)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onFound.onDevice(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.w("BleScanner", "scan failed: $errorCode")
            }
        }

        scanner?.startScan(filters, settings, cb)
        Handler(Looper.getMainLooper()).postDelayed({ stop() }, durationMs)
    }

    fun stop() {
        cb?.let { scanner?.stopScan(it) }
        cb = null
    }
}
