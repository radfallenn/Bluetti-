package com.studiorad.ac180pcontrol;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    static final String TARGET_MAC = "C7:A3:2B:21:77:90";
    static final UUID OLD_NOTIFY_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID OLD_WRITE_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    final Handler handler = new Handler(Looper.getMainLooper());
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothDevice selected;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic notifyChar;
    BluetoothGattCharacteristic writeChar;
    ScanCallback scanCallback;
    byte[] buffer = new byte[1024];
    int bufferLen = 0;

    LinearLayout root;
    TextView status, device, battery, input, output, acState, dcState, rxBox, logBox;

    int bg = Color.rgb(4,7,13), card = Color.rgb(12,18,29), text = Color.rgb(235,245,255), muted = Color.rgb(130,148,170);
    int cyan = Color.rgb(0,220,255), green = Color.rgb(0,255,153), red = Color.rgb(255,49,95), orange = Color.rgb(255,190,62), violet = Color.rgb(190,90,255);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        buildUi();
        requestPerms();
    }

    void requestPerms() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    boolean hasPerms() {
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    GradientDrawable fill(int c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(r)); return g; }
    View spacer(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    android.graphics.drawable.Drawable rgbFrame(int fillColor, int radius) {
        GradientDrawable outer = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{cyan, green, orange, red, violet, cyan}); outer.setCornerRadius(dp(radius));
        GradientDrawable inner = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{fillColor, Color.rgb(18,24,36), Color.rgb(8,11,18)}); inner.setCornerRadius(dp(radius - 1));
        LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{outer, inner}); layer.setLayerInset(1, dp(2), dp(2), dp(2), dp(2)); return layer;
    }
    android.graphics.drawable.Drawable rgbButton() {
        GradientDrawable outer = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{cyan, green, orange, red, violet}); outer.setCornerRadius(dp(22));
        GradientDrawable middle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.rgb(34,38,48), Color.rgb(8,11,18)}); middle.setCornerRadius(dp(21));
        GradientDrawable inner = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.rgb(37,42,54), Color.rgb(7,9,14)}); inner.setCornerRadius(dp(19));
        LayerDrawable layer = new LayerDrawable(new android.graphics.drawable.Drawable[]{outer, middle, inner}); layer.setLayerInset(1, dp(2), dp(2), dp(2), dp(2)); layer.setLayerInset(2, dp(4), dp(4), dp(4), dp(4)); return layer;
    }

    TextView tv(String s, int sp, int c) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(c); v.setPadding(dp(12), dp(6), dp(12), dp(6)); v.setShadowLayer(sp >= 16 ? 10 : 4, 0, 0, c); if (sp >= 18) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    LinearLayout cardBox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18), dp(16), dp(18), dp(16)); l.setBackground(rgbFrame(card, 28)); l.setElevation(dp(12)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(12), dp(8), dp(12), dp(8)); l.setLayoutParams(lp); return l; }
    Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(text); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setShadowLayer(10,0,0,text); b.setMinHeight(dp(64)); b.setBackground(rgbButton()); b.setElevation(dp(14)); return b; }
    TextView metric(String title, int color) { TextView v = tv(title + "\n--", 18, color); v.setGravity(Gravity.CENTER_VERTICAL); v.setBackground(rgbFrame(Color.rgb(18,25,38), 24)); v.setPadding(dp(16), dp(16), dp(16), dp(16)); return v; }
    void row(LinearLayout p, View a, View b) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1); lp1.setMargins(dp(6),dp(6),dp(6),dp(6)); LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1); lp2.setMargins(dp(6),dp(6),dp(6),dp(6)); r.addView(a, lp1); r.addView(b, lp2); p.addView(r); }

    void buildUi() {
        ScrollView sv = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); root.setPadding(dp(4), dp(14), dp(4), dp(28)); sv.addView(root);
        TextView title = tv("AC180P CONTROL", 28, text); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView subtitle = tv("Debug BLE dedicado para mapear a Bluetti AC180P", 13, muted); subtitle.setGravity(Gravity.CENTER); root.addView(subtitle);

        LinearLayout top = cardBox(); status = tv("● Aguardando", 16, cyan); device = tv("MAC: " + TARGET_MAC, 14, muted); top.addView(status); top.addView(device); root.addView(top);

        LinearLayout metrics = cardBox(); metrics.addView(tv("RESUMO", 12, muted)); battery = metric("▰ Bateria", green); input = metric("↯ Entrada", cyan); output = metric("⚡ Saída", orange); acState = metric("AC", violet); dcState = metric("DC", violet); row(metrics, battery, input); row(metrics, output, acState); row(metrics, dcState, metric("RX", cyan)); root.addView(metrics);

        LinearLayout controls = cardBox(); controls.addView(tv("CONTROLES", 12, muted)); Button scan = btn("Buscar AC180P"); Button connect = btn("Conectar MAC"); Button services = btn("Listar Serviços"); Button read = btn("Testar Leituras"); Button acOn = btn("AC LIGAR"); Button acOff = btn("AC DESLIGAR"); Button dcOn = btn("DC LIGAR"); Button dcOff = btn("DC DESLIGAR"); row(controls, scan, connect); row(controls, services, read); row(controls, acOn, acOff); row(controls, dcOn, dcOff); root.addView(controls);

        LinearLayout rx = cardBox(); rx.addView(tv("RX / TX HEX", 12, muted)); rxBox = tv("Aguardando pacotes...", 12, cyan); rx.addView(rxBox); root.addView(rx);
        LinearLayout logs = cardBox(); logs.addView(tv("LOG", 12, muted)); logBox = tv("", 12, muted); logs.addView(logBox); root.addView(logs);

        scan.setOnClickListener(v -> scan()); connect.setOnClickListener(v -> connectByMac()); services.setOnClickListener(v -> discoverFallbackChannels()); read.setOnClickListener(v -> testReads());
        acOn.setOnClickListener(v -> write(writeSingle(3007,1), "AC ON")); acOff.setOnClickListener(v -> write(writeSingle(3007,0), "AC OFF")); dcOn.setOnClickListener(v -> write(writeSingle(3008,1), "DC ON")); dcOff.setOnClickListener(v -> write(writeSingle(3008,0), "DC OFF"));
        setContentView(sv);
    }

    void setStatus(String s, int color) { runOnUiThread(() -> { status.setText("● " + s); status.setTextColor(color); status.setShadowLayer(10,0,0,color); }); }
    void log(String s) { runOnUiThread(() -> logBox.setText(new Date() + "\n" + s + "\n\n" + logBox.getText())); }
    void rx(String s) { runOnUiThread(() -> rxBox.setText(s + "\n\n" + rxBox.getText())); }

    void scan() {
        if (!hasPerms()) { requestPerms(); return; }
        if (scanner == null) { setStatus("Scanner indisponível", red); return; }
        setStatus("Escaneando", cyan);
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                try {
                    BluetoothDevice d = result.getDevice(); String mac = d.getAddress(); String name = d.getName();
                    if (TARGET_MAC.equalsIgnoreCase(mac) || (name != null && name.toUpperCase(Locale.ROOT).contains("AC180"))) {
                        selected = d; scanner.stopScan(this); setStatus("Encontrado", green); device.setText("Dispositivo: " + name + " • " + mac); log("Encontrado: " + name + " / " + mac);
                    }
                } catch (SecurityException e) { log("Permissão scan: " + e.getMessage()); }
            }
        };
        try { scanner.startScan(scanCallback); handler.postDelayed(() -> { try { if (scanCallback != null) scanner.stopScan(scanCallback); } catch(Exception ignored){} }, 12000); } catch(Exception e) { log("Erro scan: " + e.getMessage()); }
    }

    void connectByMac() {
        if (!hasPerms()) { requestPerms(); return; }
        try { selected = adapter.getRemoteDevice(TARGET_MAC); setStatus("Conectando", cyan); gatt = selected.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE); } catch(Exception e) { log("Erro conectar: " + e.getMessage()); }
    }

    final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            log("GATT state status=" + statusCode + " newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) { setStatus("Conectado", green); try { g.requestMtu(247); } catch(Exception ignored){} handler.postDelayed(g::discoverServices, 900); }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) setStatus("Desconectado", red);
        }
        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) { log("MTU=" + mtu + " status=" + status); try { g.discoverServices(); } catch(Exception ignored){} }
        @Override public void onServicesDiscovered(BluetoothGatt g, int statusCode) { log("Serviços descobertos status=" + statusCode); discoverFallbackChannels(); enableNotify(); }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) { log("Descriptor write status=" + status); setStatus("Notify ativo", green); }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { handleRx(c.getValue()); }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { handleRx(c.getValue()); }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) { log("Write status=" + status); }
    };

    void discoverFallbackChannels() {
        if (gatt == null) { log("GATT nulo"); return; }
        notifyChar = null; writeChar = null; StringBuilder sb = new StringBuilder();
        try {
            for (BluetoothGattService s : gatt.getServices()) {
                sb.append("S ").append(s.getUuid()).append("\n");
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    int p = c.getProperties(); String id = c.getUuid().toString().toLowerCase(Locale.ROOT); sb.append("  C ").append(c.getUuid()).append(" p=").append(p).append("\n");
                    if (OLD_NOTIFY_UUID.equals(c.getUuid())) notifyChar = c;
                    if (OLD_WRITE_UUID.equals(c.getUuid())) writeChar = c;
                    if (notifyChar == null && ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) notifyChar = c;
                    if (writeChar == null && ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) writeChar = c;
                }
            }
            log(sb.toString()); log("Notify=" + (notifyChar == null ? "null" : notifyChar.getUuid()) + " Write=" + (writeChar == null ? "null" : writeChar.getUuid()));
        } catch(Exception e) { log("Erro UUID: " + e.getMessage()); }
    }

    void enableNotify() {
        if (gatt == null || notifyChar == null) { setStatus("Notify não encontrado", red); return; }
        try { gatt.setCharacteristicNotification(notifyChar, true); BluetoothGattDescriptor d = notifyChar.getDescriptor(CCCD_UUID); if (d != null) { int p = notifyChar.getProperties(); d.setValue((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); gatt.writeDescriptor(d); } else setStatus("Notify sem CCCD", orange); } catch(Exception e) { log("Erro notify: " + e.getMessage()); }
    }

    void testReads() {
        write(readHolding(36,8), "READ 36/8"); handler.postDelayed(() -> write(readHolding(36,16), "READ 36/16"), 700); handler.postDelayed(() -> write(readHolding(0,32), "READ 0/32"), 1400); handler.postDelayed(() -> write(readHolding(100,32), "READ 100/32"), 2100); handler.postDelayed(() -> write(readHolding(3007,2), "READ 3007/2"), 2800);
    }

    void write(byte[] data, String label) {
        if (gatt == null || writeChar == null) { setStatus("Write não pronto", red); return; }
        try { writeChar.setValue(data); gatt.writeCharacteristic(writeChar); String h = hex(data); log("TX " + label + ": " + h); rx("TX " + label + "\n" + h); } catch(Exception e) { log("Erro TX: " + e.getMessage()); }
    }

    void handleRx(byte[] data) {
        if (data == null || data.length == 0) return; String h = hex(data); rx("RX len=" + data.length + "\n" + h); log("RX " + h);
        if (bufferLen + data.length > buffer.length) bufferLen = 0; System.arraycopy(data, 0, buffer, bufferLen, data.length); bufferLen += data.length; parseBuffer();
    }

    void parseBuffer() {
        byte[] cur = Arrays.copyOf(buffer, bufferLen);
        if (bufferLen >= 5 && cur[0] == 1 && cur[1] == 3) {
            int bc = cur[2] & 255, total = bc + 5; if (bufferLen >= total) { byte[] pkt = Arrays.copyOf(cur, total); bufferLen = 0; parseRead(pkt); }
        } else if (bufferLen >= 8 && cur[0] == 1 && cur[1] == 6) { bufferLen = 0; log("Comando aceito: " + hex(Arrays.copyOf(cur, 8))); }
        else if (bufferLen > 128) bufferLen = 0;
    }

    void parseRead(byte[] pkt) {
        try { int bc = pkt[2] & 255, words = bc / 2; List<Integer> w = new ArrayList<>(); for (int i=0;i<words && 4+i*2<pkt.length;i++) w.add(((pkt[3+i*2]&255)<<8)|(pkt[4+i*2]&255)); log("WORDS " + w); if (w.size() >= 8) { int dci=w.get(0), aci=w.get(1), aco=w.get(2), dco=w.get(3), soc=w.get(7); battery.setText("▰ Bateria\n" + soc + "%"); input.setText("↯ Entrada\n" + (aci+dci) + " W"); output.setText("⚡ Saída\n" + (aco+dco) + " W"); dcState.setText("DC\n" + dco + " W"); } else if (w.size() == 2) { acState.setText("AC\n" + (w.get(0)==1 ? "ON" : w.get(0)==0 ? "OFF" : w.get(0))); dcState.setText("DC\n" + (w.get(1)==1 ? "ON" : w.get(1)==0 ? "OFF" : w.get(1))); } } catch(Exception e) { log("Parse erro: " + e.getMessage()); }
    }

    byte[] readHolding(int address, int qty) { return withCrc(new byte[]{1,3,(byte)(address>>8),(byte)address,(byte)(qty>>8),(byte)qty}); }
    byte[] writeSingle(int address, int value) { return withCrc(new byte[]{1,6,(byte)(address>>8),(byte)address,(byte)(value>>8),(byte)value}); }
    byte[] withCrc(byte[] b) { int crc = crc16(b); byte[] o = Arrays.copyOf(b, b.length+2); o[o.length-2]=(byte)(crc&255); o[o.length-1]=(byte)((crc>>8)&255); return o; }
    int crc16(byte[] bs) { int crc=0xffff; for(byte b:bs){ crc^=(b&255); for(int i=0;i<8;i++) crc=(crc&1)!=0?(crc>>1)^0xA001:crc>>1; } return crc&0xffff; }
    String hex(byte[] b) { StringBuilder sb = new StringBuilder(); for(byte x:b) sb.append(String.format("%02X ", x)); return sb.toString().trim(); }
}
