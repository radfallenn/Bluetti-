package com.studiorad.bluetticontrol;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.graphics.Color;
import android.os.Bundle;
import java.util.Arrays;
import java.util.UUID;

public class AC180PActivity extends LastDeviceActivity {
    BluetoothGattCharacteristic fallbackNotify;
    BluetoothGattCharacteristic fallbackWrite;
    long lastRawLog = 0;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        log("Modo AC180P ativo: busca automática de canal BLE Notify/Write.");
    }

    @Override
    BluetoothGattCharacteristic findChar(BluetoothGatt g, UUID uuid) {
        BluetoothGattCharacteristic exact = super.findChar(g, uuid);
        if (exact != null) return exact;
        scanFallbackChannels(g);
        if (NOTIFY_UUID.equals(uuid)) return fallbackNotify;
        if (WRITE_UUID.equals(uuid)) return fallbackWrite;
        return null;
    }

    void scanFallbackChannels(BluetoothGatt g) {
        fallbackNotify = null;
        fallbackWrite = null;
        StringBuilder sb = new StringBuilder("AC180P UUIDs encontrados:\n");
        try {
            for (BluetoothGattService s : g.getServices()) {
                sb.append("S ").append(s.getUuid()).append("\n");
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    int p = c.getProperties();
                    sb.append("  C ").append(c.getUuid()).append(" p=").append(p).append("\n");
                    boolean canNotify = (p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                    boolean canWrite = (p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
                    String id = c.getUuid().toString().toLowerCase();
                    if (canNotify && (fallbackNotify == null || id.contains("ff01") || id.contains("ffe1"))) fallbackNotify = c;
                    if (canWrite && (fallbackWrite == null || id.contains("ff02") || id.contains("ffe2"))) fallbackWrite = c;
                }
            }
            log(sb.toString());
            log("Fallback AC180P Notify=" + (fallbackNotify == null ? "null" : fallbackNotify.getUuid()) + " Write=" + (fallbackWrite == null ? "null" : fallbackWrite.getUuid()));
        } catch (Exception e) {
            log("Erro ao mapear canais AC180P: " + e.getMessage());
        }
    }

    @Override
    void enableNotify(BluetoothGatt g, BluetoothGattCharacteristic c) {
        try {
            g.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCCD_UUID);
            if (d != null) {
                int p = c.getProperties();
                if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) d.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                else d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(d);
            } else {
                ready = true;
                setStatus("Online AC180P - pronto", green);
                readStatus();
                applyAutomationState();
            }
        } catch (Exception e) {
            setStatus("Erro Notify AC180P: " + e.getMessage(), Color.RED);
        }
    }

    @Override
    void readStatus() {
        if (!ready || writeChar == null) {
            setStatus("Canal AC180P não pronto", Color.RED);
            return;
        }
        setStatus("Lendo AC180P...", blue);
        try {
            sendRead(36, 16);
            handler.postDelayed(() -> sendRead(0, 32), 650);
            handler.postDelayed(() -> sendRead(100, 32), 1300);
            handler.postDelayed(() -> sendRead(3007, 2), 1950);
        } catch (Exception e) {
            log("Erro leitura AC180P: " + e.getMessage());
        }
    }

    @Override
    void handleNotify(byte[] data) {
        if (data != null && data.length > 0) {
            long now = System.currentTimeMillis();
            if (now - lastRawLog > 1200) {
                lastRawLog = now;
                log("RX AC180P len=" + data.length + " HEX=" + bytesToHex(data));
            }
        }
        try {
            super.handleNotify(data);
        } catch (Exception e) {
            log("Parser ignorou RX AC180P: " + e.getMessage());
        }
    }

    @Override
    void parseRead(byte[] pkt) {
        try {
            if (pkt == null || pkt.length < 5) return;
            int bc = pkt[2] & 255;
            int words = bc / 2;
            if (words <= 0) return;
            int[] w = new int[words];
            for (int i = 0; i < words && 4 + i * 2 < pkt.length; i++) w[i] = ((pkt[3 + i * 2] & 255) << 8) | (pkt[4 + i * 2] & 255);
            log("Parse AC180P words=" + words + " data=" + Arrays.toString(w));
            super.parseRead(pkt);
        } catch (Exception e) {
            log("Parse AC180P seguro: " + e.getMessage());
        }
    }
}
