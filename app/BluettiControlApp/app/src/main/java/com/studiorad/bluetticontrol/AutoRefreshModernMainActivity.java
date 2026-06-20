package com.studiorad.bluetticontrol;

import android.os.Bundle;

public class AutoRefreshModernMainActivity extends ModernMainActivity {
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
