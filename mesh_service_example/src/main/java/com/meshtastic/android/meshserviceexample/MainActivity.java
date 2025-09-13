/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.meshtastic.android.meshserviceexample;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.widget.Toast;
import android.location.LocationManager;

import android.view.View;
import android.widget.ProgressBar;
import android.bluetooth.le.ScanResult;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.bluetooth.BluetoothDevice;

import com.meshtastic.android.meshserviceexample.ble.BleScanner;
import com.meshtastic.android.meshserviceexample.ble.HrPlxBleClient;
import com.meshtastic.android.meshserviceexample.ble.BleDeviceAdapter;
import com.meshtastic.android.meshserviceexample.ble.BleDeviceItem;
import com.meshtastic.android.meshserviceexample.ble.BleUtils;

import java.nio.charset.StandardCharsets;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;   // 建議一併匯入

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.MessageStatus;
import com.geeksville.mesh.NodeInfo;
import com.geeksville.mesh.Portnums;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;

import java.nio.charset.StandardCharsets;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MeshServiceExample";
    private IMeshService meshService;
    private ServiceConnection serviceConnection;
    private boolean isMeshServiceBound = false;

    private RecyclerView bleRecycler;
    private TextView hrTextView;
    private BleScanner bleScanner;
    private HrPlxBleClient bleClient;
    private BleDeviceAdapter bleAdapter;
    private String currentAddr = null;
    private ProgressBar scanProgress;

    private TextView mainTextView;
    private ImageView statusImageView;
    private Button sendBtn, sosBtn;

    private void ensureBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            for (String p : perms) {
                if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, perms, 1001);
                    return;
                }
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, 1002);
            }
        }
    }

    // Handle the received broadcast
    // handle node changed
    // handle position app data
    private final BroadcastReceiver meshtasticReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case "com.geeksville.mesh.NODE_CHANGE":
                    // handle node changed
                    try {
                        NodeInfo ni = intent.getParcelableExtra("com.geeksville.mesh.NodeInfo");
                        Log.d(TAG, "NodeInfo: " + ni);
                        mainTextView.setText("NodeInfo: " + ni);
                    } catch (Exception e) {
                        Log.e(TAG, "onReceive: " + e.getMessage());
                        return;
                    }
                    break;
                case "com.geeksville.mesh.MESSAGE_STATUS":
                    int id = intent.getIntExtra("com.geeksville.mesh.PacketId", 0);
                    MessageStatus status = intent.getParcelableExtra("com.geeksville.mesh.Status");
                    Log.d(TAG, "Message Status ID: " + id + " Status: " + status);
                    break;
                case "com.geeksville.mesh.MESH_CONNECTED": {
                    String extraConnected = intent.getStringExtra("com.geeksville.mesh.Connected");
                    boolean connected = "connected".equalsIgnoreCase(extraConnected);
                    Log.d(TAG, "Received ACTION_MESH_CONNECTED: " + extraConnected);
                    if (connected) {
                        runOnUiThread(() ->
                                statusImageView.setBackgroundColor(
                                        getResources().getColor(android.R.color.holo_green_light)
                                )
                        );
                    }
                    break;
                }
                case "com.geeksville.mesh.MESH_DISCONNECTED": {
                    String extraDisconnected = intent.getStringExtra("com.geeksville.mesh.Disconnected");
                    boolean disconnected = "disconnected".equalsIgnoreCase(extraDisconnected);
                    Log.d(TAG, "Received ACTION_MESH_DISCONNECTED: " + extraDisconnected);
                    if (disconnected) {
                        runOnUiThread(() ->
                                statusImageView.setBackgroundColor(
                                        getResources().getColor(android.R.color.holo_red_light)
                                )
                        );
                    }
                    break;
                }
                case "com.geeksville.mesh.RECEIVED.POSITION_APP": {
                    // handle position app data
                    try {
                        NodeInfo ni = intent.getParcelableExtra("com.geeksville.mesh.NodeInfo");
                        Log.d(TAG, "Position App NodeInfo: " + ni);
                        mainTextView.setText("Position App NodeInfo: " + ni);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    break;
                }
                case "com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP": {
                    byte[] bytes = intent.getByteArrayExtra("com.geeksville.mesh.Payload");
                    if (bytes != null) {
                        String msg = new String(bytes, StandardCharsets.UTF_8);
                        Log.d(TAG, "TEXT_MESSAGE: " + msg);
                        mainTextView.setText("TEXT: " + msg);
                    }
                    break;
                }
                case "com.geeksville.mesh.RECEIVED.ALERT_APP": {
                    byte[] bytes = intent.getByteArrayExtra("com.geeksville.mesh.Payload");
                    String alertText = (bytes != null) ? new String(bytes, StandardCharsets.UTF_8) : "(no payload)";
                    Log.w(TAG, "ALERT: " + alertText);
                    // TODO: 在這裡彈出對話框/震動/顯示紅色提示
                    break;
                }
                default:
                    Log.w(TAG, "Unknown action: " + action);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mainTextView   = findViewById(R.id.mainTextView);
        statusImageView= findViewById(R.id.statusImageView);
        sendBtn        = findViewById(R.id.sendBtn);
        sosBtn         = findViewById(R.id.sosBtn);
        hrTextView  = findViewById(R.id.hrTextView);
        bleRecycler = findViewById(R.id.bleRecycler);
        scanProgress = findViewById(R.id.scanProgress);
        SwipeRefreshLayout swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            bleAdapter.resetForRescan();
            scanProgress.setVisibility(View.VISIBLE);

            // 重新掃描（等價改法）
            final long DURATION_MS = 8_000L;

// 開始前先把 UI 設為「掃描中」
            swipe.setRefreshing(true);
            scanProgress.setVisibility(View.VISIBLE);

// 呼叫 BleScanner：只有一個 onFound 回呼 + 時長
            bleScanner.start(result -> {
                // ScanResult -> BleDeviceItem
                BleDeviceItem item = BleUtils.toItem(result);
                runOnUiThread(() -> bleAdapter.addOrUpdate(item));
            }, DURATION_MS);

// 在 duration 結束後，主動把 UI 復位（相當於原本 scanning=false）
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                swipe.setRefreshing(false);
                scanProgress.setVisibility(View.GONE);
            }, DURATION_MS);
        });



        // 先建立掃描/連線物件
        bleScanner = new BleScanner(this);
        bleClient  = new HrPlxBleClient(this);

// 建立 RecyclerView
        bleRecycler.setLayoutManager(new LinearLayoutManager(this));
        bleRecycler.setItemAnimator(null); // 取消預設動畫，避免殘影

// 建立 Adapter：點擊項目時做連線/斷線
        // 建立 adapter（注意：第二個參數是 SAM 介面 OnItemClick）
        // 建立 RecyclerView + Adapter
        bleAdapter = new BleDeviceAdapter(new java.util.ArrayList<>(), item -> {
            if (!item.getConnected()) {
                bleClient.connect(item.getDevice(), new HrPlxBleClient.Listener() {
                    @Override public void onConnectionChanged(boolean connected) {
                        runOnUiThread(() -> bleAdapter.markConnected(item.getAddress(), connected));
                    }
                    @Override public void onHr(int hr) {
                        runOnUiThread(() -> bleAdapter.updateHr(item.getAddress(), hr));
                    }
                    @Override public void onSpo2(Integer spo2) { }
                });
            } else {
                bleClient.disconnect();
            }
        });
        bleRecycler.setLayoutManager(new LinearLayoutManager(this));
        bleRecycler.setAdapter(bleAdapter);
        bleRecycler.setItemAnimator(null); // 避免殘影

        sendBtn.setOnClickListener(v -> {
            if (meshService != null) {
                try {
                    byte[] bytes = "[Test]Hello from MeshServiceExample by Meshtastic-HR Project".getBytes(StandardCharsets.UTF_8);
                    DataPacket dataPacket = new DataPacket(DataPacket.ID_BROADCAST, bytes, Portnums.PortNum.TEXT_MESSAGE_APP_VALUE, DataPacket.ID_LOCAL, System.currentTimeMillis(), 0, MessageStatus.UNKNOWN, 3, 0, true);
                    meshService.send(dataPacket);
                    Log.d(TAG, "Message sent successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send message", e);
                }
            } else {
                Log.w(TAG, "MeshService is not bound, cannot send message");
            }
        });


        Button sosButton = findViewById(R.id.sosBtn);
        sosButton.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Send SOS")
                    .setMessage("確定要發送 SOS 嗎？")
                    .setPositiveButton("發送", (d, w) -> sendSOS("button"))
                    .setNegativeButton("取消", null)
                    .show();
            return true; // 長按觸發
        });


        // Now you can call methods on meshService
        serviceConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                meshService = IMeshService.Stub.asInterface(service);
                Log.i(TAG, "Connected to MeshService");
                isMeshServiceBound = true;
                // 在主執行緒更新 UI（保險）
                runOnUiThread(() ->
                        statusImageView.setBackgroundColor(
                                getResources().getColor(android.R.color.holo_green_light)
                        )
                );
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                meshService = null;
                isMeshServiceBound = false;
            }
        };



        IntentFilter filter = new IntentFilter();
        filter.addAction("com.geeksville.mesh.NODE_CHANGE");
        filter.addAction("com.geeksville.mesh.RECEIVED.NODEINFO_APP");
        filter.addAction("com.geeksville.mesh.RECEIVED.POSITION_APP");
        filter.addAction("com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP");
        filter.addAction("com.geeksville.mesh.RECEIVED.ALERT_APP");
        filter.addAction("com.geeksville.mesh.MESSAGE_STATUS");
        filter.addAction("com.geeksville.mesh.MESH_CONNECTED");
        filter.addAction("com.geeksville.mesh.MESH_DISCONNECTED");
        registerReceiver(meshtasticReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Registered meshtasticPacketReceiver");

//        while (!bindMeshService()) {
//            try {
//                // Wait for the service to bind
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                Log.e(TAG, "Binding interrupted", e);
//                break;
//            }
//        }
    }

    private void sendVitalsJson(int hr, int spo2) {
        if (meshService == null) return;
        try {
            String json = "{\"type\":\"vitals\",\"hr\":" + hr + ",\"spo2\":" + spo2 + ",\"ts\":" + System.currentTimeMillis() + "}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            // hopLimit=3, channel=0, wantAck=true 可按需調整
            DataPacket pkt = new DataPacket(
                    DataPacket.ID_BROADCAST,
                    bytes,
                    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    null,
                    System.currentTimeMillis(),
                    0,
                    MessageStatus.UNKNOWN,
                    3,
                    0,
                    true
            );
            meshService.send(pkt);
            Log.d(TAG, "Vitals sent: " + json);
        } catch (Exception e) {
            Log.e(TAG, "sendVitalsJson failed", e);
        }
    }

    private void sendSOS(String note /* 可選: 可放位置/原因/優先度 */) {
        if (meshService == null) return;
        try {
            String payload = (note == null || note.isEmpty()) ? "[Test]SOS" : ("SOS: " + note);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

            DataPacket pkt = new DataPacket(
                    DataPacket.ID_BROADCAST,
                    bytes,
                    Portnums.PortNum.ALERT_APP_VALUE,  // 11
                    null,
                    System.currentTimeMillis(),
                    0,
                    MessageStatus.UNKNOWN,
                    3,
                    0,
                    true
            );
            meshService.send(pkt);
            Log.w(TAG, "SOS sent: " + payload);
        } catch (Exception e) {
            Log.e(TAG, "sendSOS failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(meshtasticReceiver); } catch (Exception ignore) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensureBlePermissions();
        bindMeshService();

        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter ba = bm != null ? bm.getAdapter() : null;

        if (ba == null) {
            Toast.makeText(this, "本裝置沒有藍牙硬體（模擬器/部分平板）", Toast.LENGTH_LONG).show();
            return;
        }
        if (!ba.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
            boolean need = false;
            for (String p : perms) {
                if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) { need = true; break; }
            }
            if (need) { ActivityCompat.requestPermissions(this, perms, 1001); return; }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, 1002);
                return;
            }
            // <= Android 11 必須「系統定位」也打開
            LocationManager lm =
                    (LocationManager) getSystemService(Context.LOCATION_SERVICE);  // ← 用 Context 前綴
            boolean locationOn = lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            if (!locationOn) {
                Toast.makeText(this, "請打開裝置的定位（Android 11 及更早版本掃描需要）", Toast.LENGTH_LONG).show();
                return;
            }
        }
        scanProgress.setVisibility(View.VISIBLE);
        bleScanner.start(result -> {
            bleAdapter.addOrUpdate(BleUtils.toItem(result));
            // ... 其他 UI 更新
        }, 8000);


        // 10 秒仍無結果 → 提示
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (bleAdapter.getItemCount() == 0) {
                scanProgress.setVisibility(View.GONE);
                Toast.makeText(this, "掃描不到裝置：請確認旁邊真的有 BLE 裝置在廣播", Toast.LENGTH_SHORT).show();
            }
        }, 5000L);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindMeshService();
        bleScanner.stop();
        bleClient.disconnect();
    }


    private boolean bindMeshService() {
        try {
            Log.i(TAG, "Attempting to bind to Mesh Service...");
            Intent intent = new Intent("com.geeksville.mesh.Service");
            intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService");
            return bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind", e);
        }
        return false;
    }

    private void unbindMeshService() {
        if (isMeshServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "MeshService not registered or already unbound: " + e.getMessage());
            }
            isMeshServiceBound = false;
            meshService = null;
        }
    }

}