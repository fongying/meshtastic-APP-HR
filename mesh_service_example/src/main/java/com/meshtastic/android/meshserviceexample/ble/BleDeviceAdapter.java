package com.meshtastic.android.meshserviceexample.ble;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.meshtastic.android.meshserviceexample.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying scanned BLE devices in a RecyclerView.
 */
public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder> {

    private final List<BleDeviceItem> deviceList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(BleDeviceItem device);
    }

    public BleDeviceAdapter(List<BleDeviceItem> deviceList, OnItemClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ble_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BleDeviceItem deviceItem = deviceList.get(position);
        holder.bind(deviceItem, listener);
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addDevice(BleDeviceItem device) {
        if (!deviceList.contains(device)) {
            deviceList.add(device);
            notifyDataSetChanged(); // Simple notification for this example
        }
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final TextView deviceName;
        private final TextView deviceAddress;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);
            deviceAddress = itemView.findViewById(R.id.device_address);
        }

        public void bind(final BleDeviceItem deviceItem, final OnItemClickListener listener) {
            deviceName.setText(deviceItem.getName());
            deviceAddress.setText(deviceItem.getAddress());
            itemView.setOnClickListener(v -> listener.onItemClick(deviceItem));
        }
    }
}
