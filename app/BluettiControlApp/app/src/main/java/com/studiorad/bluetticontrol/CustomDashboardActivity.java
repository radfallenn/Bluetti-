package com.studiorad.bluetticontrol;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Map;

public class CustomDashboardActivity extends LastDeviceActivity {
    TextView proBattery, proInput, proOutput, proDc, proFooterBattery, proFooterInput, proFooterOutput, proUpdated, bleDebug;
    CombinedGraph proGraph;
    long proLastGraph = 0;
    int rxCount = 0;

    @Override public void onCreate(Bundle b) { super.onCreate(b); }

    @Override void buildUi() {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg2); root.setPadding(dp(14), dp(10), dp(14), dp(90)); sv.addView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.addView(tv("Bluetti Control", 24, navy), new LinearLayout.LayoutParams(0, -2, 1)); Button cfg = btn("Config."); header.addView(cfg, new LinearLayout.LayoutParams(dp(110), dp(54))); root.addView(header);

        LinearLayout hero = card(); hero.setOrientation(LinearLayout.VERTICAL); heroName = tv("BLUETTI", 24, navy); status = pill("● Conectado", green2); deviceTitle = tv("Dispositivo: --", 14, muted); heroSoc = tv("--%", 38, green2); heroSoc.setGravity(Gravity.RIGHT); hero.addView(heroName); hero.addView(status); hero.addView(deviceTitle); hero.addView(heroSoc); root.addView(hero);

        LinearLayout grid = card(); grid.addView(tv("Resumo", 20, navy)); two(grid, metricBox("🔋", "Bateria", "--%", green2), metricBox("🔌", "Entrada", "-- W", blue2)); two(grid, metricBox("⚡", "Saída", "-- W", orange2), metricBox("🌡", "DC", "-- W", android.graphics.Color.rgb(155,70,220))); root.addView(grid);

        LinearLayout chart = card(); LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(tv("Bateria & Consumo", 20, navy), new LinearLayout.LayoutParams(0, -2, 1)); row.addView(btn("30s"), new LinearLayout.LayoutParams(dp(92), dp(52))); chart.addView(row); chart.addView(tv("━ Bateria (%)      ━ Consumo (W)", 13, muted)); proGraph = new CombinedGraph(this); chart.addView(proGraph, new LinearLayout.LayoutParams(-1, dp(280)));
        LinearLayout foot = new LinearLayout(this); foot.setOrientation(LinearLayout.HORIZONTAL); proFooterBattery = footerBox("Bateria Atual", "--%", navy); proFooterInput = footerBox("Entrada Total", "-- W", blue2); proFooterOutput = footerBox("Saída Total", "-- W", orange2); foot.addView(proFooterBattery, new LinearLayout.LayoutParams(0, dp(88), 1)); foot.addView(proFooterInput, new LinearLayout.LayoutParams(0, dp(88), 1)); foot.addView(proFooterOutput, new LinearLayout.LayoutParams(0, dp(88), 1)); chart.addView(foot); proUpdated = tv("Atualizado há 30 segundos", 12, muted); proUpdated.setGravity(Gravity.CENTER); chart.addView(proUpdated); root.addView(chart);

        LinearLayout ctrl = card(); ctrl.addView(tv("Controle", 20, navy)); Button acOn = control("AC\nLIGAR", green2), acOff = control("AC\nDESLIGAR", red2); Button dcOn = control("DC\nLIGAR", green2), dcOff = control("DC\nDESLIGAR", red2); two(ctrl, acOn, acOff); two(ctrl, dcOn, dcOff); root.addView(ctrl);

        LinearLayout debugCard = card(); debugCard.addView(tv("BLE Debug AC180P", 20, navy)); bleDebug = tv("Aguardando pacotes RX...", 12, muted); debugCard.addView(bleDebug); Button clearDebug = btn("Limpar debug"); clearDebug.setOnClickListener(v -> { rxCount = 0; bleDebug.setText("Debug limpo. Aguardando RX..."); }); debugCard.addView(clearDebug); root.addView(debugCard);

        LinearLayout batSection = card(); batSection.addView(tv("Baterias", 20, navy)); addBatteryWindows(batSection); root.addView(batSection);
        LinearLayout actions = card(); actions.addView(tv("Conexão", 20, navy)); Button scan = btn("Buscar"), stop = btn("Parar"), connect = btn("Conectar"), disconnect = btn("Desconectar"), refresh = btn("Atualizar"), reconnect = btn("Reconectar"); two(actions, scan, stop); two(actions, connect, disconnect); two(actions, refresh, reconnect); root.addView(actions);

        LinearLayout auto = card(); auto.addView(tv("Automações", 20, navy)); autoEnable = new android.widget.CheckBox(this); autoEnable.setText("Ativar automação local"); autoAc = new android.widget.CheckBox(this); autoAc.setText("Automatizar AC"); autoDc = new android.widget.CheckBox(this); autoDc.setText("Automatizar DC"); auto.addView(autoEnable); two(auto, autoAc, autoDc); lowPct = new android.widget.EditText(this); lowPct.setHint("Desligar abaixo de %"); lowPct.setInputType(2); highPct = new android.widget.EditText(this); highPct.setHint("Ligar acima de %"); highPct.setInputType(2); two(auto, lowPct, highPct); intervalSec = new android.widget.EditText(this); intervalSec.setHint("Intervalo automação"); intervalSec.setInputType(2); auto.addView(intervalSec); Button save = btn("Salvar"), test = btn("Testar"); two(auto, save, test); automationStatus = tv("Automação: desligada", 15, muted); auto.addView(automationStatus); root.addView(auto);

        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list); logBox = new LinearLayout(this); logBox.setOrientation(LinearLayout.VERTICAL);
        scan.setOnClickListener(v -> startScan()); stop.setOnClickListener(v -> stopScan()); connect.setOnClickListener(v -> connectSelected()); disconnect.setOnClickListener(v -> disconnect()); refresh.setOnClickListener(v -> readStatus()); reconnect.setOnClickListener(v -> { disconnect(); handler.postDelayed(this::connectSelected, 800); }); acOn.setOnClickListener(v -> writeRegister(3007, 1)); acOff.setOnClickListener(v -> writeRegister(3007, 0)); dcOn.setOnClickListener(v -> writeRegister(3008, 1)); dcOff.setOnClickListener(v -> writeRegister(3008, 0)); save.setOnClickListener(v -> { saveAutomation(); applyAutomationState(); }); test.setOnClickListener(v -> runAutomationCheck(true)); setContentView(sv);
    }

    private TextView metricBox(String icon, String label, String value, int color) { TextView v = tv(icon + "  " + label + "\n" + value, 18, color); v.setGravity(Gravity.CENTER_VERTICAL); v.setBackground(border(android.graphics.Color.WHITE, android.graphics.Color.rgb(226,233,244), 26)); v.setElevation(dp(4)); if (label.equals("Bateria")) proBattery = v; else if (label.equals("Entrada")) proInput = v; else if (label.equals("Saída")) proOutput = v; else proDc = v; return v; }
    private void addBatteryWindows(LinearLayout section) { String lastMac = getSharedPreferences("last_device", 0).getString("mac", ""); String lastName = getSharedPreferences("last_device", 0).getString("name", "Bluetti principal"); if (lastMac != null && lastMac.length() > 0) section.addView(batteryCard(lastName, lastMac)); Map<String, ?> all = getSharedPreferences("devices", 0).getAll(); for (String mac : all.keySet()) if (!mac.equals(lastMac)) section.addView(batteryCard(String.valueOf(all.get(mac)), mac)); if ((lastMac == null || lastMac.length() == 0) && all.isEmpty()) section.addView(tv("Nenhuma Bluetti salva ainda.", 14, muted)); }
    private LinearLayout batteryCard(String name, String mac) { LinearLayout box = card(); box.addView(tv(name, 18, navy)); box.addView(tv(mac, 13, muted)); Button use = btn("Usar esta bateria"); use.setOnClickListener(v -> { try { selected = bt.getRemoteDevice(mac); selectedName = name; if (deviceTitle != null) deviceTitle.setText("Dispositivo: " + name + " • " + mac); connectSelected(); } catch(Exception e){} }); box.addView(use); return box; }

    @Override void handleNotify(byte[] data) { debugRx(data); super.handleNotify(data); }
    private void debugRx(byte[] data) { if (bleDebug == null || data == null) return; rxCount++; String text = "RX #" + rxCount + "\nTamanho: " + data.length + " bytes\nHEX: " + bytesToHex(data) + "\n\n" + bleDebug.getText(); runOnUiThread(() -> bleDebug.setText(text.length() > 3000 ? text.substring(0, 3000) : text)); }

    @Override void parseRead(byte[] pkt) {
        if (pkt == null || pkt.length < 3) { debugRx(pkt); return; }
        int bc = pkt[2] & 255, words = bc / 2; int[] w = new int[Math.max(0, words)];
        for (int i=0;i<words && 4+i*2<pkt.length;i++) w[i] = ((pkt[3+i*2]&255)<<8) | (pkt[4+i*2]&255);
        final int fWords = words; final int[] fw = w;
        runOnUiThread(() -> {
            if (bleDebug != null) bleDebug.setText("Parse: words=" + fWords + " pkt=" + pkt.length + " bytes\n" + bleDebug.getText());
            if (fWords >= 8) { int aci=fw[1], aco=fw[2], dco=fw[3]; lastSoc=fw[7]; lastConsumption=aco+dco; heroSoc.setText(lastSoc + "%"); proBattery.setText("🔋  Bateria\n" + lastSoc + "%"); proInput.setText("🔌  Entrada\n" + aci + " W"); proOutput.setText("⚡  Saída\n" + aco + " W"); proDc.setText("🌡  DC\n" + dco + " W"); proFooterBattery.setText("Bateria Atual\n" + lastSoc + "%"); proFooterInput.setText("Entrada Total\n" + aci + " W"); proFooterOutput.setText("Saída Total\n" + lastConsumption + " W"); addHistory(comboBattery,lastSoc); addHistory(comboLoad,lastConsumption); long now=System.currentTimeMillis(); if(now-proLastGraph>=30000 || proLastGraph==0){ proLastGraph=now; proGraph.setData(comboBattery, comboLoad); } setStatus("Conectado", green); runAutomationCheck(false); }
            else if (fWords == 2) { acValue=fw[0]; dcValue=fw[1]; }
        });
    }
}
