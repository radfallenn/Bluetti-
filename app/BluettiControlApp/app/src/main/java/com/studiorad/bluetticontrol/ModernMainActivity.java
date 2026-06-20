package com.studiorad.bluetticontrol;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModernMainActivity extends MainActivity {
    int modernBlue = Color.rgb(18, 102, 210);
    int modernDark = Color.rgb(18, 28, 45);
    int modernMuted = Color.rgb(92, 108, 130);
    int modernBg = Color.rgb(244, 247, 252);

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(modernBg);
        getWindow().setNavigationBarColor(modernBg);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    int dp2(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp2(radiusDp));
        return g;
    }

    GradientDrawable strokeShape(int color, int strokeColor, int radiusDp) {
        GradientDrawable g = shape(color, radiusDp);
        g.setStroke(dp2(1), strokeColor);
        return g;
    }

    @Override
    TextView tv(String t, int sp, int c) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(c == Color.DKGRAY ? modernMuted : c);
        v.setPadding(dp2(18), dp2(8), dp2(18), dp2(8));
        if (sp >= 22) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    @Override
    Button btn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp2(54));
        b.setPadding(dp2(10), dp2(8), dp2(10), dp2(8));

        int color = modernBlue;
        String label = t.toLowerCase();
        if (label.contains("off") || label.contains("desconectar") || label.contains("limpar") || label.contains("parar")) {
            color = Color.rgb(210, 49, 72);
        } else if (label.contains("on") || label.contains("conectar") || label.contains("salvar") || label.contains("adicionar")) {
            color = Color.rgb(0, 135, 86);
        } else if (label.contains("reconectar") || label.contains("testar")) {
            color = Color.rgb(92, 72, 230);
        }
        b.setBackground(shape(color, 16));
        return b;
    }

    @Override
    TextView metric(String title) {
        TextView v = tv(title + ": --", 17, modernDark);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setBackground(strokeShape(Color.WHITE, Color.rgb(226, 233, 244), 16));
        v.setPadding(dp2(18), dp2(16), dp2(18), dp2(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp2(6), 0, dp2(6));
        v.setLayoutParams(lp);
        return v;
    }

    @Override
    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp2(16), dp2(16), dp2(16), dp2(16));
        c.setBackground(strokeShape(Color.WHITE, Color.rgb(226, 233, 244), 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp2(12), dp2(8), dp2(12), dp2(8));
        c.setLayoutParams(lp);
        return c;
    }
}
