package com.studiorad.bluetticontrol;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout list, logBox;
    TextView status, deviceTitle;
    BluetoothAdapter bt;
    BluetoothLeScanner scanner;
    BluetoothGatt gatt;
    BluetoothDevice selected;
    final ArrayList<BluetoothDevice> found = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    boolean scanning = false;
    int blue = Color.rgb(21,101,192), bg = Color.rgb(245,247,251);

    public void onCreate(Bundle b) {
        super.onCreate(b);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bt = manager == null ? null : manager.getAdapter();
        buildUi();
        requestBlePermissions();
    }

    TextView tv(String t, int sp, int c) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(c);
        v.setPadding(20, 10, 20, 6);
        return v;
    }

    Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        return b;
    }

    void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        sv.addView(root);

        root.addView(tv("Bluetti Control", 27, Color.rgb(20,30,45)));
        root.addView(tv("Modo direto Bluetooth BLE: buscar, conectar, diagnosticar e preparar leitura/comandos locais.", 14, Color.DKGRAY));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(10, 8, 10, 8);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = btn("Buscar Bluetti");
        Button stop = btn("Parar");
        row.addView(scan, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);

        status = tv("Status: aguardando Bluetooth", 15, Color.DKGRAY);
        deviceTitle = tv("Dispositivo: nenhum", 16, Color.rgb(20,30,45));
        root.addView(status);
        root.addView(deviceTitle);

        LinearLayout cmdRow = new LinearLayout(this);
        cmdRow.setPadding(10, 8, 10, 8);
        cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = btn("Conectar");
        Button disconnect = btn("Desconectar");
        cmdRow.addView(connect, new LinearLayout.LayoutParams(0, -2, 1));
        cmdRow.addView(disconnect, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(cmdRow);

        LinearLayout bluettiCmd = new LinearLayout(this);
        bluettiCmd.setPadding(10, 8, 10, 8);
        bluettiCmd.setOrientation(LinearLayout.HORIZONTAL);
        Button discover = btn("Ler serviços");
        Button reconnect = btn("Reconectar");
        bluettiCmd.addView(discover, new LinearLayout.LayoutParams(0, -2, 1));
        bluettiCmd.addView(reconnect, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bluettiCmd);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        root.addView(tv("Log", 18, Color.rgb(20,30,45)));
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(logBox);

        scan.setOnClickListener(v -> startScan());
        stop.setOnClickListener(v -> stopScan());
        connect.setOnClickListener(v -> connectSelected());
        disconnect.setOnClickListener(v -> disconnect());
        discover.setOnClickListener(v -> discoverServices());
        reconnect.setOnClickListener(v -> { disconnect(); handler.postDelayed(this::connectSelected, 800); });
        setContentView(sv);
    }

    void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
        }
    }

    boolean hasBlePermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void startScan() {
        if (bt == null || !bt.isEnabled()) { setStatus("Bluetooth desligado", Color.RED); return; }
        if (!hasBlePermission()) { requestBlePermissions(); setStatus("Permissão Bluetooth necessária", Color.RED); return; }
        scanner = bt.getBluetoothLeScanner();
        if (scanner == null) { setStatus("Scanner BLE indisponível", Color.RED); return; }
        found.clear();
        list.removeAllViews();
        scanning = true;
        setStatus("Buscando Bluetti por 15 segundos...", blue);
        scanner.startScan(scanCallback);
        handler.postDelayed(this::stopScan, 15000);
    }

    void stopScan() {
        if (!scanning) return;
        scanning = false;
        try { if (scanner != null) scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        setStatus("Busca finalizada: " + found.size() + " candidato(s)", Color.DKGRAY);
    }

    final ScanCallback scanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = "";
            try { name = d.getName(); } catch (SecurityException ignored) {}
            if (name == null) name = "";
            String upper = name.toUpperCase(Locale.ROOT);
            boolean looksBluetti = upper.contains("BLUETTI") || upper.startsWith("AC") || upper.startsWith("EB") || upper.startsWith("EP") || upper.startsWith("PBOX") || upper.startsWith("EBOX");
            if (!looksBluetti) return;
            for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
            found.add(d);
            addDeviceCard(d, result.getRssi(), name);
        }
        public void onScanFailed(int errorCode) { setStatus("Erro no scan BLE: " + errorCode, Color.RED); }
    };

    void addDeviceCard(BluetoothDevice d, int rssi, String name) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(18, 14, 18, 14);
        c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(14, 10, 14, 10);
        list.addView(c, lp);
        c.addView(tv((name == null || name.isEmpty() ? "Bluetti sem nome" : name), 18, Color.rgb(20,30,45)));
        c.addView(tv(d.getAddress() + "  •  RSSI " + rssi + " dBm", 13, Color.DKGRAY));
        Button use = btn("Selecionar e conectar");
        c.addView(use);
        use.setOnClickListener(v -> { selected = d; deviceTitle.setText("Dispositivo: " + (name == null || name.isEmpty() ? d.getAddress() : name)); stopScan(); connectSelected(); });
    }

    void connectSelected() {
        if (selected == null) { setStatus("Selecione uma Bluetti primeiro", Color.RED); return; }
        if (!hasBlePermission()) { requestBlePermissions(); return; }
        try {
            disconnect();
            setStatus("Conectando direto via BLE...", blue);
            gatt = selected.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) { setStatus("Permissão negada para conectar", Color.RED); }
    }

    void disconnect() {
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Exception ignored) {}
        gatt = null;
    }

    void discoverServices() {
        if (gatt == null) { setStatus("Não conectado", Color.RED); return; }
        try { gatt.discoverServices(); setStatus("Lendo serviços BLE...", blue); } catch (SecurityException e) { setStatus("Permissão negada", Color.RED); }
    }

    final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            runOnUiThread(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    setStatus("Online direto BLE", Color.rgb(0,120,70));
                    log("Conectado. Status GATT: " + statusCode);
                    try { g.discoverServices(); } catch (SecurityException ignored) {}
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    setStatus("Offline / desconectado", Color.RED);
                    log("Desconectado. Status GATT: " + statusCode);
                }
            });
        }
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            runOnUiThread(() -> {
                log("Serviços encontrados. Status: " + statusCode);
                for (BluetoothGattService s : g.getServices()) {
                    log("Serviço: " + s.getUuid());
                    for (BluetoothGattCharacteristic ch : s.getCharacteristics()) {
                        log("  Char: " + ch.getUuid() + " props=" + ch.getProperties());
                    }
                }
                setStatus("Online direto BLE - serviços carregados", Color.rgb(0,120,70));
            });
        }
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            runOnUiThread(() -> log("Notify " + c.getUuid() + ": " + bytesToHex(c.getValue())));
        }
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            runOnUiThread(() -> log("Read " + c.getUuid() + " status=" + statusCode + " data=" + bytesToHex(c.getValue())));
        }
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) {
            runOnUiThread(() -> log("Write " + c.getUuid() + " status=" + statusCode));
        }
    };

    void setStatus(String s, int color) { status.setText("Status: " + s); status.setTextColor(color); }
    void log(String s) { TextView v = tv(new Date() + "\n" + s, 12, Color.DKGRAY); logBox.addView(v, 0); }
    String bytesToHex(byte[] b) { if (b == null) return ""; StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02X ", x)); return sb.toString().trim(); }
}
