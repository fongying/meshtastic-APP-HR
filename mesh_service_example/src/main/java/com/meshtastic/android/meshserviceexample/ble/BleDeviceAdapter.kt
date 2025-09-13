package com.meshtastic.android.meshserviceexample.ble

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meshtastic.android.meshserviceexample.R
import java.util.*

/** Java 友善的單一方法介面（Java 可用 lambda，且不需回傳值） */
fun interface OnItemClick { fun onClick(item: BleDeviceItem) }

/** 清單元素（公開 device，Java 會自動看到 getDevice()） */
data class BleDeviceItem(
    val device: BluetoothDevice,
    var name: String?,
    var rssi: Int,
    var connected: Boolean = false,
    var hr: Int? = null
) {
    val address: String get() = device.address
}

class BleDeviceAdapter(
    private val devices: MutableList<BleDeviceItem>,
    private val onItemClick: OnItemClick
) : RecyclerView.Adapter<BleDeviceAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView   = v.findViewById(R.id.nameText)
        val tvAddr: TextView   = v.findViewById(R.id.addrText)
        val tvState: TextView  = v.findViewById(R.id.stateText)
        val btnConnect: Button = v.findViewById(R.id.connectBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = devices[pos]
        h.tvName.text  = it.name ?: "Unknown"
        h.tvAddr.text  = it.address
        h.tvState.text = if (it.connected) "Connected${it.hr?.let { hr -> " · HR: $hr bpm" } ?: ""}" else "Disconnected"
        h.btnConnect.text = if (it.connected) "Disconnect" else "Connect"
        h.btnConnect.setOnClickListener { _ -> onItemClick.onClick(it) }
    }

    /** 新增或更新掃描結果 */
    fun addOrUpdate(result: BleDeviceItem) {
        val idx = devices.indexOfFirst { it.address.equals(result.address, ignoreCase = true) }
        if (idx >= 0) {
            val old = devices[idx]
            old.name = result.name ?: old.name
            old.rssi = result.rssi
            notifyItemChanged(idx)
        } else {
            devices.add(result)
            devices.sortWith(
                compareByDescending<BleDeviceItem> { it.connected }
                    .thenByDescending { it.rssi }
                    .thenBy { it.name ?: "zzz" }
            )
            notifyDataSetChanged()
        }
    }

    /** 下拉刷新前清空列表 */
    fun resetForRescan() {
        devices.clear()
        notifyDataSetChanged()
    }

    /** 標記連線狀態（用地址） */
    fun markConnected(addr: String?, connected: Boolean) {
        if (addr.isNullOrEmpty()) return
        val i = devices.indexOfFirst { it.address.equals(addr, ignoreCase = true) }
        if (i >= 0) {
            devices[i].connected = connected
            notifyItemChanged(i)
        }
    }

    /** 更新 HR（用地址） */
    fun updateHr(addr: String?, hr: Int) {
        if (addr.isNullOrEmpty()) return
        val i = devices.indexOfFirst { it.address.equals(addr, ignoreCase = true) }
        if (i >= 0) {
            devices[i].hr = hr
            notifyItemChanged(i)
        }
    }
}
