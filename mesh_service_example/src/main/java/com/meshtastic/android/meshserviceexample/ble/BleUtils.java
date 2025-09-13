package com.meshtastic.android.meshserviceexample.ble;

import java.util.UUID;

/**
 * Utility object for BLE related constants.
 */
public class BleUtils {
    // Service UUIDs
    public static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    public static final UUID PULSE_OXIMETER_SERVICE_UUID = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb");

    // Characteristic UUIDs
    public static final UUID HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    public static final UUID PULSE_OXIMETER_CONTINUOUS_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A5F-0000-1000-8000-00805f9b34fb");

    // Descriptor UUIDs
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BleUtils() {
        // Private constructor to prevent instantiation
    }
}
