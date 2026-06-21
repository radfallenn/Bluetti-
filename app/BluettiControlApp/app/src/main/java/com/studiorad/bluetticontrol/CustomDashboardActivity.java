package com.studiorad.bluetticontrol;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class CustomDashboardActivity extends LastDeviceActivity {
    private int spacing = 12;
    private int inner = 16;
    private int buttonHeight = 58;
    private int textBoost = 0;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        loadCustomization();
        handler.postDelayed(this::addCustomizerPanel, 700);
        handler.postDelayed(this::applyCustomization, 900);
    }

    private void loadCustomization() {
        SharedPreferences p = getSharedPreferences("layout_custom", 0);
        spacing = p.getInt("spacing", 12);
        inner = p.getInt("inner", 16);
        buttonHeight = p.getInt("buttonHeight", 58);
        textBoost = p.getInt("textBoost", 0);
    }

    private void saveCustomization() {
        getSharedPreferences("layout_custom", 0).edit()
                .putInt("spacing", spacing)
                .putInt("inner", inner)
                .putInt("buttonHeight", buttonHeight)
                .putInt("textBoost", textBoost)
                .apply();
    }

    private void addCustomizerPanel() {
        if (root == null) return;
        LinearLayout panel = card();
        panel.addView(tv("Personalização", 22, navy));
        panel.addView(tv("Ajuste o layout do app do seu jeito.", 13, muted));
        panel.addView(slider("Espaçamento entre cards", 4, 32, spacing, v -> { spacing = v; saveCustomization(); applyCustomization(); }));
        panel.addView(slider("Espaçamento interno", 8, 34, inner, v -> { inner = v; saveCustomization(); applyCustomization(); }));
        panel.addView(slider("Altura dos botões", 44, 86, buttonHeight, v -> { buttonHeight = v; saveCustomization(); applyCustomization(); }));
        panel.addView(slider("Tamanho dos textos", 0, 8, textBoost, v -> { textBoost = v; saveCustomization(); applyCustomization(); }));
        Button reset = btn("Restaurar padrão");
        reset.setOnClickListener(v -> { spacing = 12; inner = 16; buttonHeight = 58; textBoost = 0; saveCustomization(); applyCustomization(); });
        panel.addView(reset);
        root.addView(panel);
    }

    interface OnValue { void set(int value); }

    private LinearLayout slider(String label, int min, int max, int value, OnValue onValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView title = tv(label + ": " + value, 14, navy);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress;
                title.setText(label + ": " + v);
                onValue.set(v);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(title);
        box.addView(seek);
        return box;
    }

    private void applyCustomization() {
        if (root == null) return;
        applyRecursive(root);
    }

    private void applyRecursive(View view) {
        if (view instanceof Button) {
            ((Button) view).setMinHeight(dp(buttonHeight));
            view.setPadding(dp(10), dp(8), dp(10), dp(8));
        } else if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (textBoost > 0) t.setTextSize(Math.max(11, t.getTextSize() / getResources().getDisplayMetrics().scaledDensity) + textBoost * 0.15f);
        } else if (view instanceof LinearLayout) {
            view.setPadding(dp(inner), dp(inner), dp(inner), dp(inner));
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.setMargins(dp(spacing), dp(spacing), dp(spacing), dp(spacing));
            view.setLayoutParams(mlp);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyRecursive(group.getChildAt(i));
        }
    }
}
