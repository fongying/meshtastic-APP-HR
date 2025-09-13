package com.meshtastic.android.meshserviceexample.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

/**
 * Manages connection and data transfer with a BLE Heart Rate and Pulse Oximeter device.
 */
@SuppressLint("MissingPermission")
public class HrPlxBleClient {
    private static final String TAG = "HrPlxBleClient";

    private final Context context;
    private BluetoothGatt bluetoothGatt;
    private Listener listener;

    public interface Listener {
        void onConnectionStateChanged(int state);
        void onServicesDiscovered();
        void onCharacteristicRead(String uuid, byte[] value);
        void onCharacteristicChanged(String uuid, byte[] value);
        void onHeartRateReceived(int hr);
        void onSpo2Received(Integer spo2); // Must be implemented as per error log
    }

    public HrPlxBleClient(Context context) {
        this.context = context;
    }

    public void connect(BluetoothDevice device, Listener listener) {
        this.listener = listener;
        device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (listener != null) {
                listener.onConnectionStateChanged(newState);
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                bluetoothGatt = gatt;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.");
                if (listener != null) {
                    listener.onServicesDiscovered();
                }
                enableNotifications(gatt, BleUtils.HEART_RATE_SERVICE_UUID, BleUtils.HEART_RATE_MEASUREMENT_CHAR_UUID);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (listener != null) {
                listener.onCharacteristicChanged(characteristic.getUuid().toString(), characteristic.getValue());
            }

            if (BleUtils.HEART_RATE_MEASUREMENT_CHAR_UUID.equals(characteristic.getUuid())) {
                parseHeartRate(characteristic.getValue());
            }
        }
    };

    private void enableNotifications(BluetoothGatt gatt, UUID serviceUuid, UUID characteristicUuid) {
        BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) {
            Log.e(TAG, "Service not found: " + serviceUuid);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found: " + characteristicUuid);
            return;
        }

        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleUtils.CLIENT_CHARACTERISTIC_CONFIG_UUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
        Log.d(TAG, "Enabled notifications for " + characteristicUuid);
    }

    private void parseHeartRate(byte[] value) {
        if (value != null && value.length > 1) {
            int flag = value[0];
            int format = (flag & 0x01) == 1 ? BluetoothGattCharacteristic.FORMAT_UINT16 : BluetoothGattCharacteristic.FORMAT_UINT8;
            int hr = (format == BluetoothGattCharacteristic.FORMAT_UINT16) ? (value[1] & 0xFF) | ((value[2] & 0xFF) << 8) : value[1] & 0xFF;

            if (listener != null) {
                listener.onHeartRateReceived(hr);
            }
        }
    }
}
