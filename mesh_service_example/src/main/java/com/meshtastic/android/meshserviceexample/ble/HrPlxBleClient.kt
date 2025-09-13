package com.meshtastic.android.meshserviceexample.ble

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import kotlin.math.min

class HrPlxBleClient(private val context: Context) {

    interface Listener {
        fun onConnectionChanged(connected: Boolean)
        fun onHr(hr: Int)
        fun onSpo2(spo2: Int?)
    }

    companion object {
        val UUID_HR_SERVICE: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val UUID_HR_MEAS:   UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

        // 可選：血氧（連續量測 0x2A5F）
        val UUID_PLX_SERVICE: UUID    = UUID.fromString("00001822-0000-1000-8000-00805F9B34FB")
        val UUID_PLX_CONTINUOUS: UUID = UUID.fromString("00002A5F-0000-1000-8000-00805F9B34FB")

        private val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val TAG = "HrPlxBleClient"
    }

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }

    var connectedAddress: String? = null
        private set

    private var gatt: BluetoothGatt? = null

    /** 連線：Java -> bleClient.connect(device, new Listener{...}); */
    fun connect(device: BluetoothDevice, l: Listener): Boolean {
        listener = l
        // 清理舊連線
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        connectedAddress = device.address

        gatt = device.connectGatt(context, /*autoConnect*/ false, gattCallback)
        return gatt != null
    }

    fun disconnect() {
        try {
            gatt?.services?.forEach { srv ->
                srv.characteristics?.forEach { ch ->
                    ch.getDescriptor(UUID_CCCD)?.let { d ->
                        try {
                            gatt?.setCharacteristicNotification(ch, false)
                            d.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                            gatt?.writeDescriptor(d)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        if (connectedAddress != null) {
            connectedAddress = null
            post { listener?.onConnectionChanged(false) }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt = g
                post { listener?.onConnectionChanged(true) }
                // 某些機型需要延遲後再 discover
                main.postDelayed({ g.discoverServices() }, 250)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                post { listener?.onConnectionChanged(false) }
                try { g.close() } catch (_: Exception) {}
                gatt = null
                connectedAddress = null
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            enableNotify(g, UUID_HR_SERVICE, UUID_HR_MEAS)
            // 如需 SpO2 再打開
            enableNotify(g, UUID_PLX_SERVICE, UUID_PLX_CONTINUOUS)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic
        ) {
            when (ch.uuid) {
                UUID_HR_MEAS -> parseHr(ch.value)?.let { bpm ->
                    post { listener?.onHr(bpm) }
                }
                UUID_PLX_CONTINUOUS -> {
                    val (spo2, pr) = parsePlx(ch.value)
                    post { listener?.onSpo2(spo2) }
                    // 如需要 PR 可擴充 callback
                }
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, srvUuid: UUID, chrUuid: UUID) {
        val c = g.getService(srvUuid)?.getCharacteristic(chrUuid) ?: return
        try {
            g.setCharacteristicNotification(c, true)
            val d = c.getDescriptor(UUID_CCCD) ?: return
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(d)
        } catch (e: Exception) {
            Log.w(TAG, "enableNotify failed: ${e.message}")
        }
    }

    /** Heart Rate Measurement (0x2A37) 解析 */
    private fun parseHr(data: ByteArray?): Int? {
        if (data == null || data.isEmpty()) return null
        val flags = data[0].toInt() and 0xFF
        val sixteenBit = (flags and 0x01) != 0
        return if (!sixteenBit) {
            if (data.size >= 2) data[1].toInt() and 0xFF else null
        } else {
            if (data.size >= 3) {
                ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            } else null
        }
    }

    /** 簡化版：PLX Continuous Measurement (0x2A5F) 只抓 SpO2/PR（若有）*/
    private fun parsePlx(data: ByteArray?): Pair<Int?, Int?> {
        if (data == null || data.size < 2) return null to null
        // 這個特徵的 flags/欄位較複雜，這裡示意性抓最常見順序（SpO2 1byte, PR 1byte）
        val spo2 = data[0].toInt() and 0xFF
        val pr   = data[1].toInt() and 0xFF
        return spo2 to pr
    }

    private fun post(block: () -> Unit) = main.post(block)
}
