package com.meshtastic.android.meshserviceexample.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Collections;
import java.util.List;

/**
 * A simple BLE scanner class.
 */
@SuppressLint("MissingPermission")
public class BleScanner {
    private static final String TAG = "BleScanner";
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private boolean isScanning = false;

    public interface OnDeviceFoundListener {
        void onDeviceFound(ScanResult result);
    }

    public BleScanner(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startScan(OnDeviceFoundListener listener) {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress.");
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not available.");
            return;
        }

        // Filter for devices that advertise the Heart Rate Service
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleUtils.HEART_RATE_SERVICE_UUID))
                .build();
        List<ScanFilter> filters = Collections.singletonList(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                listener.onDeviceFound(result);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "Scan failed with error code: " + errorCode);
            }
        });
        isScanning = true;
        Log.d(TAG, "BLE scan started.");
    }

    public void stopScan() {
        if (!isScanning) {
            Log.w(TAG, "Scan is not in progress.");
            return;
        }
        bluetoothAdapter.getBluetoothLeScanner().stopScan(new ScanCallback() {});
        isScanning = false;
        Log.d(TAG, "BLE scan stopped.");
    }
}
