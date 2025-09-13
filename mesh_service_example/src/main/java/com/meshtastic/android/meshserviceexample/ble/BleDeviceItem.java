package com.meshtastic.android.meshserviceexample.ble;

import android.bluetooth.BluetoothDevice;
import java.util.Objects;

/**
 * Data class to hold information about a scanned BLE device.
 */
public class BleDeviceItem {
    private final BluetoothDevice device;
    private final String name;
    private final String address;
    private int rssi;
    private boolean isConnectable;
    private Integer batteryLevel;

    public BleDeviceItem(BluetoothDevice device, String name, int rssi, boolean isConnectable, Integer batteryLevel) {
        this.device = device;
        this.name = name;
        this.address = device.getAddress();
        this.rssi = rssi;
        this.isConnectable = isConnectable;
        this.batteryLevel = batteryLevel;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getRssi() {
        return rssi;
    }

    // Needed for equals check
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BleDeviceItem that = (BleDeviceItem) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
