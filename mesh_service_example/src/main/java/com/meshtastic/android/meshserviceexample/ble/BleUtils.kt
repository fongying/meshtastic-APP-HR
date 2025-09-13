package com.meshtastic.android.meshserviceexample.ble

import android.bluetooth.le.ScanResult

object BleUtils {
    @JvmStatic
    fun toItem(result: ScanResult): BleDeviceItem {
        val dev  = result.device
        val name = result.scanRecord?.deviceName ?: dev.name
        return BleDeviceItem(
            device = dev,
            name   = name,
            rssi   = result.rssi
        )
    }
}