package com.meshtastic.android.meshserviceexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.MeshUser;
import com.geeksville.mesh.MessageStatus;
import com.geeksville.mesh.MyNodeInfo;
import com.geeksville.mesh.Portnums;
import com.meshtastic.android.meshserviceexample.ble.BleDeviceAdapter;
import com.meshtastic.android.meshserviceexample.ble.BleDeviceItem;
import com.meshtastic.android.meshserviceexample.ble.BleScanner;
import com.meshtastic.android.meshserviceexample.ble.HrPlxBleClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements HrPlxBleClient.Listener {

    private static final String TAG = "Meshtastic-HR-Example";
    private IMeshService meshService;
    private TextView serviceStatus, nodeInfo, lastMessage, hrValue;
    private Button sendBtn, sosBtn, testBtn1, testBtn2;
    private Spinner intervalSpinner;

    private RecyclerView bleList;
    private BleScanner bleScanner;
    private BleDeviceAdapter bleDeviceAdapter;
    private HrPlxBleClient hrPlxBleClient;

    // State Management
    private final AtomicInteger currentHr = new AtomicInteger(0);
    private final AtomicBoolean sosState = new AtomicBoolean(false);

    // Timer for automatic broadcasting
    private final Handler broadcastHandler = new Handler(Looper.getMainLooper());
    private Runnable broadcastRunnable;
    private long broadcastIntervalMillis = 60000; // Default 1 minute

    // SOS Long Press Handling
    private final Handler sosHandler = new Handler(Looper.getMainLooper());
    private Runnable sosRunnable;
    private static final long LONG_PRESS_DURATION = 3000; // 3 seconds

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                result.forEach((permission, granted) -> {
                    if (!granted) {
                        Log.e(TAG, "Permission denied: " + permission);
                        Toast.makeText(this, "Bluetooth permission is required to scan devices", Toast.LENGTH_SHORT).show();
                    }
                });
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceStatus = findViewById(R.id.serviceStatus);
        nodeInfo = findViewById(R.id.nodeInfo);
        lastMessage = findViewById(R.id.lastMessage);
        sendBtn = findViewById(R.id.sendBtn);
        sosBtn = findViewById(R.id.sosBtn);
        testBtn1 = findViewById(R.id.testBtn1);
        testBtn2 = findViewById(R.id.testBtn2);
        intervalSpinner = findViewById(R.id.intervalSpinner);
        hrValue = findViewById(R.id.hr_value);
        bleList = findViewById(R.id.ble_list);

        bleList.setLayoutManager(new LinearLayoutManager(this));

        hrPlxBleClient = new HrPlxBleClient(this);

        bleDeviceAdapter = new BleDeviceAdapter(new ArrayList<>(), deviceItem -> {
            Log.d(TAG, "Device selected: " + deviceItem.getName());
            Toast.makeText(this, "Connecting to " + deviceItem.getName(), Toast.LENGTH_SHORT).show();
            // Call connect with the BluetoothDevice and the Listener (this activity)
            hrPlxBleClient.connect(deviceItem.getDevice(), this);
        });
        bleList.setAdapter(bleDeviceAdapter);

        bleScanner = new BleScanner(this);

        setupButtons();
        setupSpinner();
        setupBroadcastTimer();

        checkPermissionsAndStartScan();

        bindService(new Intent("com.geeksville.mesh.service.BIND"), serviceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.geeksville.mesh.NODE_CHANGE");
        filter.addAction("com.geeksville.mesh.TEXT_MESSAGE");
        registerReceiver(broadcastReceiver, filter);
    }

    //region HrPlxBleClient.Listener Implementation
    @Override
    public void onConnectionStateChanged(int state) {
        Log.d(TAG, "BLE Connection state changed to: " + state);
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "BLE Services discovered");
    }

    @Override
    public void onCharacteristicRead(String uuid, byte[] value) {
        Log.d(TAG, "BLE Characteristic read: " + uuid);
    }

    @Override
    public void onCharacteristicChanged(String uuid, byte[] value) {
        Log.d(TAG, "BLE Characteristic changed: " + uuid);
    }

    @Override
    public void onHeartRateReceived(int hr) {
        Log.d(TAG, "Heart rate received: " + hr);
        currentHr.set(hr);
        runOnUiThread(() -> hrValue.setText(String.valueOf(hr)));
    }

    // FIX 1: Add missing onSpo2 method to satisfy the Listener interface
    @Override
    public void onSpo2Received(Integer spo2) {
        Log.d(TAG, "SPO2 received: " + spo2);
        // We don't have a UI for this yet, just logging.
    }
    //endregion


    @SuppressLint("ClickableViewAccessibility")
    private void setupButtons() {
        sendBtn.setOnClickListener(v -> {
            if (meshService != null) {
                try {
                    byte[] bytes = "[Test]Hello from MeshServiceExample by Meshtastic-HR Project".getBytes(StandardCharsets.UTF_8);
                    DataPacket dataPacket = new DataPacket(DataPacket.ID_BROADCAST, bytes, Portnums.PortNum.TEXT_MESSAGE_APP_VALUE, DataPacket.ID_LOCAL, System.currentTimeMillis(), 0, MessageStatus.UNKNOWN, 3, 0, true);
                    meshService.send(dataPacket);
                    Log.d(TAG, "Test message sent");
                    Toast.makeText(this, "Test message sent", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send message", e);
                }
            } else {
                Log.w(TAG, "MeshService is not bound, cannot send message");
            }
        });

        // SOS Button with Long Press implementation
        sosRunnable = () -> {
            boolean newSosState = !sosState.get();
            sosState.set(newSosState);
            Log.d(TAG, "SOS Long Press successful, new state: " + newSosState);
            vibrate();
            updateSosButtonUI();
            sendTelemetryMessage(currentHr.get(), newSosState);
            resetBroadcastTimer();
        };

        sosBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sosHandler.postDelayed(sosRunnable, LONG_PRESS_DURATION);
                    v.setPressed(true);
                    return true; // We've handled the event
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sosHandler.removeCallbacks(sosRunnable);
                    v.setPressed(false);
                    return true; // We've handled the event
            }
            return false;
        });


        testBtn1.setOnClickListener(v -> {
            Log.d(TAG, "Test button 1 pressed");
            sendTelemetryMessage(29, true);
            resetBroadcastTimer();
        });

        testBtn2.setOnClickListener(v -> {
            Log.d(TAG, "Test button 2 pressed");
            sendTelemetryMessage(38, false);
            resetBroadcastTimer();
        });
    }

    private void setupSpinner() {
        List<Integer> intervals = Arrays.asList(1, 2, 3, 4, 5, 10, 15);
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, intervals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        intervalSpinner.setAdapter(adapter);

        intervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int minutes = (int) parent.getItemAtPosition(position);
                broadcastIntervalMillis = (long) minutes * 60 * 1000;
                Log.d(TAG, "Broadcast interval set to " + minutes + " minutes (" + broadcastIntervalMillis + "ms)");
                resetBroadcastTimer();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupBroadcastTimer() {
        broadcastRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Auto broadcast timer triggered");
                sendTelemetryMessage(currentHr.get(), sosState.get());
                // Schedule the next run
                broadcastHandler.postDelayed(this, broadcastIntervalMillis);
            }
        };
    }

    private void resetBroadcastTimer() {
        // Do not reset the timer if the service is not yet connected
        if (meshService == null) return;

        Log.d(TAG, "Resetting broadcast timer, will trigger in " + broadcastIntervalMillis + "ms");
        broadcastHandler.removeCallbacks(broadcastRunnable);
        broadcastHandler.postDelayed(broadcastRunnable, broadcastIntervalMillis);
    }

    private void updateSosButtonUI() {
        if (sosState.get()) {
            sosBtn.setText("SOS (Current Status: ON)");
            sosBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else {
            sosBtn.setText("SOS (Current Status: OFF)");
            sosBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
    }


    private void sendTelemetryMessage(int hr, boolean sos) {
        if (meshService == null) {
            Log.w(TAG, "MeshService is not bound, cannot send telemetry message");
            Toast.makeText(this, "Error: Mesh service not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String deviceId = String.valueOf(meshService.getMyNodeInfo().getMyNodeNum());

            JSONObject payload = new JSONObject();
            payload.put("v", 1);
            payload.put("device_id", deviceId);
            payload.put("ts", getISO8601UTCString());
            payload.put("battery", getBatteryLevel());
            if (hr > 0) { // Only include hr if it's a valid reading
                payload.put("hr", hr);
            }
            payload.put("sos", sos);

            String jsonString = payload.toString();
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

            // Using TEXT_MESSAGE_APP_VALUE as per previous logic for broadcasting text
            DataPacket dataPacket = new DataPacket(DataPacket.ID_BROADCAST, bytes, Portnums.PortNum.TEXT_MESSAGE_APP_VALUE, DataPacket.ID_LOCAL, System.currentTimeMillis(), 0, MessageStatus.UNKNOWN, 3, 0, true);
            meshService.send(dataPacket);

            Log.d(TAG, "Telemetry message broadcasted: " + jsonString);
            Toast.makeText(this, "Telemetry message sent", Toast.LENGTH_SHORT).show();

        } catch (RemoteException e) {
            Log.e(TAG, "Failed to communicate with MeshService", e);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON", e);
        }
    }

    private String getISO8601UTCString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private int getBatteryLevel() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, iFilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float) scale;
            return (int) (batteryPct * 100);
        }
        return -1; // Indicate error or unavailability
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //deprecated in API 26
                v.vibrate(500);
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
        unregisterReceiver(broadcastReceiver);
        if (hrPlxBleClient != null) {
            hrPlxBleClient.disconnect();
        }
        broadcastHandler.removeCallbacks(broadcastRunnable); // Stop timer
        sosHandler.removeCallbacks(sosRunnable); // Clean up SOS handler
    }

    @SuppressLint("MissingPermission")
    private void checkPermissionsAndStartScan() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        // Add VIBRATE permission for haptic feedback
        permissionsToRequest.add(Manifest.permission.VIBRATE);

        List<String> permissionsNotGranted = new ArrayList<>();
        for (String permission : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNotGranted.add(permission);
            }
        }

        if (!permissionsNotGranted.isEmpty()) {
            requestPermissionLauncher.launch(permissionsNotGranted.toArray(new String[0]));
        } else {
            // Call startScan with the required callback for ScanResult
            bleScanner.startScan(scanResult -> runOnUiThread(() -> {
                BluetoothDevice device = scanResult.getDevice();
                // FIX 4: Correctly create BleDeviceItem with all required parameters
                String name = device.getName() != null ? device.getName() : "Unknown Device";
                bleDeviceAdapter.addDevice(new BleDeviceItem(device, name, scanResult.getRssi(), scanResult.isConnectable(), null));
            }));
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            meshService = IMeshService.Stub.asInterface(service);
            serviceStatus.setText("Service Status: Connected");
            updateNodeInfo();
            resetBroadcastTimer(); // Start the timer once the service is connected
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            meshService = null;
            serviceStatus.setText("Service Status: Disconnected");
            broadcastHandler.removeCallbacks(broadcastRunnable); // Stop timer
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case "com.geeksville.mesh.NODE_CHANGE":
                    updateNodeInfo();
                    break;
                case "com.geeksville.mesh.TEXT_MESSAGE":
                    String message = intent.getStringExtra("message");
                    String from = intent.getStringExtra("from");
                    Log.d(TAG, "Received message from " + from + ": " + message);
                    lastMessage.setText(from + ": " + message);
                    break;
            }
        }
    };

    private void updateNodeInfo() {
        if (meshService != null) {
            try {
                MyNodeInfo myInfo = meshService.getMyNodeInfo();
                if (myInfo != null) {
                    // FIX 5: Use getLongName() from MyNodeInfo object if getUser() is not available
                    String longName = myInfo.getLongName();
                    if (longName == null || longName.isEmpty()) {
                        longName = "N/A";
                    }

                    String infoText = String.format("ID: !%s, Name: %s",
                            Integer.toHexString(myInfo.getMyNodeNum()),
                            longName);
                    nodeInfo.setText(infoText);
                } else {
                    nodeInfo.setText("Node Info: Failed to get");
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                nodeInfo.setText("Node Info: Error getting info");
            }
        }
    }
}

