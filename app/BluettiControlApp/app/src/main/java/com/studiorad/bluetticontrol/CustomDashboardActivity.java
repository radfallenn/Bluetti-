package com.studiorad.bluetticontrol;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Map;

public class CustomDashboardActivity extends LastDeviceActivity {
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        handler.postDelayed(this::addBatteryWindows, 900);
    }

    private void addBatteryWindows() {
        if (root == null) return;
        LinearLayout section = card();
        section.addView(tv("Baterias", 22, navy));
        section.addView(tv("Cada Bluetti salva aparece em uma janela própria.", 13, muted));
        String lastMac = getSharedPreferences("last_device", 0).getString("mac", "");
        String lastName = getSharedPreferences("last_device", 0).getString("name", "Bluetti principal");
        if (lastMac != null && lastMac.length() > 0) section.addView(simpleBatteryCard(lastName, lastMac));
        Map<String, ?> all = getSharedPreferences("devices", 0).getAll();
        for (String mac : all.keySet()) {
            if (mac.equals(lastMac)) continue;
            section.addView(simpleBatteryCard(String.valueOf(all.get(mac)), mac));
        }
        root.addView(section, Math.min(3, root.getChildCount()));
    }

    private LinearLayout simpleBatteryCard(String name, String mac) {
        LinearLayout box = card();
        TextView title = tv(name, 18, navy);
        TextView sub = tv(mac, 13, muted);
        box.addView(title);
        box.addView(sub);
        return box;
    }
}
