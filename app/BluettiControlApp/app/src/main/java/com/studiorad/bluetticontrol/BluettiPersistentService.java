package com.studiorad.bluetticontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.Arrays;
import java.util.UUID;

public class BluettiPersistentService extends Service {
    public static final String CHANNEL_ID = "bluetti_persistent";
    public static final int NOTIFICATION_ID = 7621;
    static final UUID NOTIFY_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID WRITE_UUID  = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private boolean ready = false;
    private int lastBattery = -1;
    private int lastLoad = 0;

    private final Runnable keepAlive = new Runnable() {
        @Override public void run() {
            if (ready) readStatus(); else connectLast();
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Bluetti Control ativo", "Mantendo conexão Bluetooth"));
        handler.postDelayed(keepAlive, 1500);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Bluetti Control ativo", statusText()));
        handler.removeCallbacks(keepAlive);
        handler.postDelayed(keepAlive, 1000);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(keepAlive);
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void connectLast() {
        if (gatt != null) return;
        String mac = getSharedPreferences("last_device", 0).getString("mac", "");
        if (mac == null || mac.length() < 17) return;
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) return;
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            gatt = device.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE);
        } catch (Exception ignored) {}
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                try { g.requestMtu(247); g.discoverServices(); } catch (Exception ignored) {}
                updateNotification("Conectado, carregando serviços");
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false; writeChar = null;
                try { g.close(); } catch (Exception ignored) {}
                gatt = null;
                updateNotification("Desconectado, tentando reconectar");
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattCharacteristic notify = findChar(g, NOTIFY_UUID);
            writeChar = findChar(g, WRITE_UUID);
            if (notify != null && writeChar != null) enableNotify(g, notify);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            ready = true; readStatus(); updateNotification("Conectado em segundo plano");
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { parse(c.getValue()); }
    };

    private BluetoothGattCharacteristic findChar(BluetoothGatt g, UUID uuid) {
        for (BluetoothGattService s : g.getServices()) for (BluetoothGattCharacteristic c : s.getCharacteristics()) if (uuid.equals(c.getUuid())) return c;
        return null;
    }

    private void enableNotify(BluetoothGatt g, BluetoothGattCharacteristic c) {
        try {
            g.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCCD_UUID);
            if (d != null) { d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(d); }
        } catch (Exception ignored) {}
    }

    private void readStatus() {
        if (gatt == null || writeChar == null) return;
        send(new byte[]{1,3,0,36,0,8});
    }

    private void send(byte[] body) {
        try { writeChar.setValue(withCrc(body)); gatt.writeCharacteristic(writeChar); } catch (Exception ignored) {}
    }

    private void parse(byte[] pkt) {
        if (pkt == null || pkt.length < 19 || pkt[0] != 1 || pkt[1] != 3) return;
        try {
            int acOut = ((pkt[7] & 255) << 8) | (pkt[8] & 255);
            int dcOut = ((pkt[9] & 255) << 8) | (pkt[10] & 255);
            lastLoad = acOut + dcOut;
            lastBattery = ((pkt[17] & 255) << 8) | (pkt[18] & 255);
            updateNotification(statusText());
        } catch (Exception ignored) {}
    }

    private String statusText() {
        if (lastBattery >= 0) return "Bateria " + lastBattery + "% • Consumo " + lastLoad + " W";
        return ready ? "Conectado em segundo plano" : "Monitoramento persistente habilitado";
    }

    private byte[] withCrc(byte[] body) {
        int crc = crc16(body); byte[] out = Arrays.copyOf(body, body.length + 2);
        out[out.length - 2] = (byte)(crc & 255); out[out.length - 1] = (byte)((crc >> 8) & 255); return out;
    }
    private int crc16(byte[] bytes) {
        int crc = 0xffff; for (byte b : bytes) { crc ^= b & 255; for (int i=0;i<8;i++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xA001 : crc >> 1; } return crc & 0xffff;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bluetti Control", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém o monitoramento da Bluetti ativo");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(String text) {
        try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification("Bluetti Control ativo", text)); } catch (Exception ignored) {}
    }

    private Notification buildNotification(String title, String text) {
        Intent openIntent = new Intent(this, LastDeviceActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(pi).setOngoing(true).setShowWhen(false);
        return b.build();
    }
}
