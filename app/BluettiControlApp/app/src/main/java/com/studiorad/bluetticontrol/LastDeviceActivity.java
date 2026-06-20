package com.studiorad.bluetticontrol;

import android.os.Bundle;

public class LastDeviceActivity extends AutoRefreshModernMainActivity {
    private boolean triedLast = false;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        handler.postDelayed(this::connectLastDevice, 2500);
    }

    private void connectLastDevice() {
        if (triedLast || ready || bt == null) return;
        triedLast = true;
        String mac = getSharedPreferences("last_device", 0).getString("mac", "");
        String name = getSharedPreferences("last_device", 0).getString("name", "Bluetti");
        if (mac == null || mac.length() < 17) return;
        try {
            selected = bt.getRemoteDevice(mac);
            selectedName = name;
            if (deviceTitle != null) deviceTitle.setText("Dispositivo: " + name + " • " + mac);
            connectSelected();
        } catch (Exception e) { }
    }

    @Override
    void connectSelected() {
        if (selected != null) {
            try {
                String name = selectedName;
                if (name == null || name.length() == 0) name = "Bluetti";
                getSharedPreferences("last_device", 0).edit()
                        .putString("mac", selected.getAddress())
                        .putString("name", name)
                        .apply();
            } catch (Exception e) { }
        }
        super.connectSelected();
    }
}
