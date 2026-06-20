package com.studiorad.bluetticontrol;

import android.os.Bundle;

public class AutoRefreshModernMainActivity extends ModernMainActivity {
    private long lastVisualChartRefresh = 0;

    private final Runnable fiveSecondRefresh = new Runnable() {
        @Override
        public void run() {
            if (ready) {
                readStatus();
                handler.postDelayed(this, 5000);
            }
        }
    };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    void readStatus() {
        super.readStatus();
    }

    @Override
    void parseRead(byte[] pkt) {
        long now = System.currentTimeMillis();
        boolean allowChart = now - lastVisualChartRefresh >= 30000 || lastVisualChartRefresh == 0;
        if (allowChart) {
            lastVisualChartRefresh = now;
            super.parseRead(pkt);
        } else {
            int oldBatterySize = batteryHistory.size();
            int oldConsumptionSize = consumptionHistory.size();
            super.parseRead(pkt);
            while (batteryHistory.size() > oldBatterySize) batteryHistory.remove(batteryHistory.size() - 1);
            while (consumptionHistory.size() > oldConsumptionSize) consumptionHistory.remove(consumptionHistory.size() - 1);
            if (batteryGraph != null) batteryGraph.setValues(batteryHistory);
            if (consumptionGraph != null) consumptionGraph.setValues(consumptionHistory);
        }
    }

    @Override
    void applyAutomationState() {
        super.applyAutomationState();
        handler.removeCallbacks(fiveSecondRefresh);
        if (ready) handler.postDelayed(fiveSecondRefresh, 5000);
    }

    @Override
    void disconnect() {
        handler.removeCallbacks(fiveSecondRefresh);
        super.disconnect();
    }
}
