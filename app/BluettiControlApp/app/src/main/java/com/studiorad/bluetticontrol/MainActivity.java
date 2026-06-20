package com.studiorad.bluetticontrol;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final UUID NOTIFY_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID WRITE_UUID  = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    LinearLayout list, logBox, dashboard;
    TextView status, deviceTitle, battery, acIn, dcIn, acOut, dcOut, acState, dcState, automationStatus;
    EditText lowPct, highPct, intervalSec;
    CheckBox autoEnable, autoAc, autoDc;
    BluetoothAdapter bt;
    BluetoothLeScanner scanner;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic writeChar, notifyChar;
    BluetoothDevice selected;
    final ArrayList<BluetoothDevice> found = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    final byte[] responseBuffer = new byte[512];
    int responseLen = 0, lastSoc = -1, acValue = -1, dcValue = -1;
    boolean scanning = false, ready = false, automationRunning = false, automationBusy = false;
    long lastAutomationAction = 0;
    int blue = Color.rgb(21,101,192), bg = Color.rgb(245,247,251);

    final Runnable automationLoop = new Runnable() {
        public void run() {
            if (!automationRunning) return;
            readStatus();
            handler.postDelayed(this, Math.max(15, getInt(intervalSec, 30)) * 1000L);
        }
    };

    public void onCreate(Bundle b) {
        super.onCreate(b);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bt = manager == null ? null : manager.getAdapter();
        buildUi();
        loadAutomation();
        requestBlePermissions();
    }

    TextView tv(String t, int sp, int c) { TextView v = new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(c); v.setPadding(20,10,20,6); return v; }
    TextView metric(String title) { TextView v = tv(title + ": --", 17, Color.rgb(20,30,45)); v.setBackgroundColor(Color.WHITE); return v; }
    Button btn(String t) { Button b = new Button(this); b.setText(t); b.setAllCaps(false); return b; }

    void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); sv.addView(root);
        root.addView(tv("Bluetti Control", 27, Color.rgb(20,30,45)));
        root.addView(tv("Direto por Bluetooth BLE: bateria, potência, AC/DC e automações locais.", 14, Color.DKGRAY));

        LinearLayout row = new LinearLayout(this); row.setPadding(10,8,10,8); row.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = btn("Buscar Bluetti"), stop = btn("Parar"); row.addView(scan, new LinearLayout.LayoutParams(0,-2,1)); row.addView(stop, new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        status = tv("Status: aguardando Bluetooth", 15, Color.DKGRAY); deviceTitle = tv("Dispositivo: nenhum", 16, Color.rgb(20,30,45)); root.addView(status); root.addView(deviceTitle);

        LinearLayout cmdRow = new LinearLayout(this); cmdRow.setPadding(10,8,10,8); cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = btn("Conectar"), disconnect = btn("Desconectar"); cmdRow.addView(connect, new LinearLayout.LayoutParams(0,-2,1)); cmdRow.addView(disconnect, new LinearLayout.LayoutParams(0,-2,1)); root.addView(cmdRow);

        LinearLayout actionRow = new LinearLayout(this); actionRow.setPadding(10,8,10,8); actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = btn("Atualizar dados"), reconnect = btn("Reconectar"); actionRow.addView(refresh, new LinearLayout.LayoutParams(0,-2,1)); actionRow.addView(reconnect, new LinearLayout.LayoutParams(0,-2,1)); root.addView(actionRow);

        dashboard = new LinearLayout(this); dashboard.setOrientation(LinearLayout.VERTICAL); dashboard.setPadding(12,8,12,8); root.addView(dashboard);
        battery = metric("Bateria"); acIn = metric("Entrada AC"); dcIn = metric("Entrada DC"); acOut = metric("Saída AC"); dcOut = metric("Saída DC"); acState = metric("Controle AC"); dcState = metric("Controle DC");
        dashboard.addView(battery); dashboard.addView(acIn); dashboard.addView(dcIn); dashboard.addView(acOut); dashboard.addView(dcOut); dashboard.addView(acState); dashboard.addView(dcState);

        LinearLayout powerRow = new LinearLayout(this); powerRow.setPadding(10,8,10,8); powerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button acOn = btn("AC ON"), acOff = btn("AC OFF"); powerRow.addView(acOn, new LinearLayout.LayoutParams(0,-2,1)); powerRow.addView(acOff, new LinearLayout.LayoutParams(0,-2,1)); root.addView(powerRow);
        LinearLayout powerRow2 = new LinearLayout(this); powerRow2.setPadding(10,0,10,8); powerRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button dcOn = btn("DC ON"), dcOff = btn("DC OFF"); powerRow2.addView(dcOn, new LinearLayout.LayoutParams(0,-2,1)); powerRow2.addView(dcOff, new LinearLayout.LayoutParams(0,-2,1)); root.addView(powerRow2);

        root.addView(tv("Automações", 22, Color.rgb(20,30,45)));
        root.addView(tv("Exemplo: abaixo de 20% desliga AC/DC. Ao chegar em 80% liga novamente. Use intervalo mínimo de 15s.", 13, Color.DKGRAY));
        autoEnable = new CheckBox(this); autoEnable.setText("Ativar automação local"); root.addView(autoEnable);
        autoAc = new CheckBox(this); autoAc.setText("Automatizar AC"); root.addView(autoAc);
        autoDc = new CheckBox(this); autoDc.setText("Automatizar DC"); root.addView(autoDc);
        lowPct = new EditText(this); lowPct.setHint("Desligar abaixo de %"); lowPct.setInputType(2); root.addView(lowPct);
        highPct = new EditText(this); highPct.setHint("Ligar acima de %"); highPct.setInputType(2); root.addView(highPct);
        intervalSec = new EditText(this); intervalSec.setHint("Intervalo de verificação em segundos"); intervalSec.setInputType(2); root.addView(intervalSec);
        LinearLayout autoRow = new LinearLayout(this); autoRow.setPadding(10,8,10,8); autoRow.setOrientation(LinearLayout.HORIZONTAL);
        Button saveAuto = btn("Salvar automação"), testAuto = btn("Testar agora"); autoRow.addView(saveAuto, new LinearLayout.LayoutParams(0,-2,1)); autoRow.addView(testAuto, new LinearLayout.LayoutParams(0,-2,1)); root.addView(autoRow);
        automationStatus = tv("Automação: desligada", 15, Color.DKGRAY); root.addView(automationStatus);

        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        root.addView(tv("Log", 18, Color.rgb(20,30,45))); logBox = new LinearLayout(this); logBox.setOrientation(LinearLayout.VERTICAL); root.addView(logBox);

        scan.setOnClickListener(v -> startScan()); stop.setOnClickListener(v -> stopScan()); connect.setOnClickListener(v -> connectSelected()); disconnect.setOnClickListener(v -> disconnect());
        refresh.setOnClickListener(v -> readStatus()); reconnect.setOnClickListener(v -> { disconnect(); handler.postDelayed(this::connectSelected, 800); });
        acOn.setOnClickListener(v -> writeRegister(3007,1)); acOff.setOnClickListener(v -> writeRegister(3007,0)); dcOn.setOnClickListener(v -> writeRegister(3008,1)); dcOff.setOnClickListener(v -> writeRegister(3008,0));
        saveAuto.setOnClickListener(v -> { saveAutomation(); applyAutomationState(); }); testAuto.setOnClickListener(v -> runAutomationCheck(true));
        setContentView(sv);
    }

    void loadAutomation() {
        SharedPreferences p = getSharedPreferences("auto",0);
        autoEnable.setChecked(p.getBoolean("enabled", false)); autoAc.setChecked(p.getBoolean("ac", true)); autoDc.setChecked(p.getBoolean("dc", true));
        lowPct.setText(String.valueOf(p.getInt("low", 20))); highPct.setText(String.valueOf(p.getInt("high", 80))); intervalSec.setText(String.valueOf(p.getInt("interval", 30)));
        applyAutomationState();
    }
    void saveAutomation() {
        int low = clamp(getInt(lowPct,20), 1, 99), high = clamp(getInt(highPct,80), 1, 100);
        if (high <= low) high = Math.min(100, low + 5);
        int interval = Math.max(15, getInt(intervalSec,30));
        lowPct.setText(String.valueOf(low)); highPct.setText(String.valueOf(high)); intervalSec.setText(String.valueOf(interval));
        getSharedPreferences("auto",0).edit().putBoolean("enabled", autoEnable.isChecked()).putBoolean("ac", autoAc.isChecked()).putBoolean("dc", autoDc.isChecked()).putInt("low", low).putInt("high", high).putInt("interval", interval).apply();
        log("Automação salva: OFF abaixo de " + low + "%, ON acima de " + high + "%");
    }
    void applyAutomationState() {
        automationRunning = autoEnable.isChecked(); handler.removeCallbacks(automationLoop);
        if (automationRunning) { automationStatus.setText("Automação: ativa"); automationStatus.setTextColor(Color.rgb(0,120,70)); handler.postDelayed(automationLoop, 1000); }
        else { automationStatus.setText("Automação: desligada"); automationStatus.setTextColor(Color.DKGRAY); }
    }

    void runAutomationCheck(boolean manual) {
        if (lastSoc < 0) { if (manual) log("Automação: bateria ainda não foi lida"); return; }
        int low = clamp(getInt(lowPct,20), 1, 99), high = clamp(getInt(highPct,80), 1, 100);
        long now = System.currentTimeMillis();
        if (!manual && now - lastAutomationAction < 15000) return;
        if (automationBusy) return;
        automationBusy = true;
        try {
            if (lastSoc <= low) {
                if (autoAc.isChecked() && acValue != 0) { log("Automação: bateria " + lastSoc + "% <= " + low + "%. Desligando AC."); writeRegister(3007,0); lastAutomationAction = now; }
                if (autoDc.isChecked() && dcValue != 0) { handler.postDelayed(() -> { log("Automação: desligando DC."); writeRegister(3008,0); }, 700); lastAutomationAction = now; }
            } else if (lastSoc >= high) {
                if (autoAc.isChecked() && acValue != 1) { log("Automação: bateria " + lastSoc + "% >= " + high + "%. Ligando AC."); writeRegister(3007,1); lastAutomationAction = now; }
                if (autoDc.isChecked() && dcValue != 1) { handler.postDelayed(() -> { log("Automação: ligando DC."); writeRegister(3008,1); }, 700); lastAutomationAction = now; }
            } else if (manual) log("Automação: sem ação. Bateria " + lastSoc + "% entre " + low + "% e " + high + "%.");
        } finally { handler.postDelayed(() -> automationBusy = false, 2500); }
    }

    int getInt(EditText e, int def) { try { return Integer.parseInt(e.getText().toString().trim()); } catch(Exception ex) { return def; } }
    int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    void requestBlePermissions() { if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10); }
    boolean hasBlePermission() { if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    void startScan() {
        if (bt == null || !bt.isEnabled()) { setStatus("Bluetooth desligado", Color.RED); return; }
        if (!hasBlePermission()) { requestBlePermissions(); setStatus("Permissão Bluetooth necessária", Color.RED); return; }
        scanner = bt.getBluetoothLeScanner(); if (scanner == null) { setStatus("Scanner BLE indisponível", Color.RED); return; }
        found.clear(); list.removeAllViews(); scanning = true; setStatus("Buscando Bluetti por 15 segundos...", blue); scanner.startScan(scanCallback); handler.postDelayed(this::stopScan, 15000);
    }
    void stopScan() { if (!scanning) return; scanning = false; try { if (scanner != null) scanner.stopScan(scanCallback); } catch (Exception ignored) {} setStatus("Busca finalizada: " + found.size() + " candidato(s)", Color.DKGRAY); }

    final ScanCallback scanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice(); String name = ""; try { name = d.getName(); } catch (SecurityException ignored) {}
            if (name == null) name = ""; String upper = name.toUpperCase(Locale.ROOT);
            boolean looksBluetti = upper.contains("BLUETTI") || upper.startsWith("AC") || upper.startsWith("EB") || upper.startsWith("EP") || upper.startsWith("PBOX") || upper.startsWith("EBOX") || upper.startsWith("EL");
            if (!looksBluetti) return; for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
            found.add(d); addDeviceCard(d, result.getRssi(), name);
        }
        public void onScanFailed(int errorCode) { setStatus("Erro no scan BLE: " + errorCode, Color.RED); }
    };

    void addDeviceCard(BluetoothDevice d, int rssi, String name) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(18,14,18,14); c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(14,10,14,10); list.addView(c, lp);
        c.addView(tv((name == null || name.isEmpty() ? "Bluetti sem nome" : name), 18, Color.rgb(20,30,45))); c.addView(tv(d.getAddress() + "  •  RSSI " + rssi + " dBm", 13, Color.DKGRAY));
        Button use = btn("Selecionar e conectar"); c.addView(use); use.setOnClickListener(v -> { selected = d; deviceTitle.setText("Dispositivo: " + (name == null || name.isEmpty() ? d.getAddress() : name)); stopScan(); connectSelected(); });
    }

    void connectSelected() { if (selected == null) { setStatus("Selecione uma Bluetti primeiro", Color.RED); return; } if (!hasBlePermission()) { requestBlePermissions(); return; } try { disconnect(); ready=false; setStatus("Conectando direto via BLE...", blue); gatt = selected.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE); } catch(SecurityException e) { setStatus("Permissão negada para conectar", Color.RED); } }
    void disconnect() { handler.removeCallbacks(automationLoop); automationRunning=false; try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch(Exception ignored) {} gatt=null; writeChar=null; notifyChar=null; ready=false; applyAutomationState(); }

    final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) { runOnUiThread(() -> { if (newState == BluetoothProfile.STATE_CONNECTED) { setStatus("Online direto BLE", Color.rgb(0,120,70)); log("Conectado. Status GATT: "+statusCode); try { g.requestMtu(247); g.discoverServices(); } catch(SecurityException ignored) {} } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { setStatus("Offline / desconectado", Color.RED); log("Desconectado. Status GATT: "+statusCode); ready=false; } }); }
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) { notifyChar=findChar(g,NOTIFY_UUID); writeChar=findChar(g,WRITE_UUID); runOnUiThread(() -> log("Serviços carregados. Notify="+(notifyChar!=null)+" Write="+(writeChar!=null))); if (notifyChar != null) enableNotify(g,notifyChar); else runOnUiThread(() -> setStatus("Serviço Bluetti FF01 não encontrado", Color.RED)); }
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int statusCode) { ready=true; runOnUiThread(() -> { setStatus("Online - pronto para ler", Color.rgb(0,120,70)); readStatus(); applyAutomationState(); }); }
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { handleNotify(c.getValue()); }
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) { handleNotify(c.getValue()); }
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int statusCode) { runOnUiThread(() -> log("Write status="+statusCode)); }
    };

    BluetoothGattCharacteristic findChar(BluetoothGatt g, UUID uuid) { for (BluetoothGattService s:g.getServices()) for (BluetoothGattCharacteristic c:s.getCharacteristics()) if (uuid.equals(c.getUuid())) return c; return null; }
    void enableNotify(BluetoothGatt g, BluetoothGattCharacteristic c) { try { g.setCharacteristicNotification(c,true); BluetoothGattDescriptor d=c.getDescriptor(CCCD_UUID); if (d!=null) { d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(d); } else { ready=true; readStatus(); } } catch(SecurityException e) { setStatus("Permissão negada em notification", Color.RED); } }

    void readStatus() { if (!ready || writeChar==null) { setStatus("Conectado, mas canal de dados não está pronto", Color.RED); return; } setStatus("Lendo bateria e potência...", blue); sendRead(36,8); handler.postDelayed(() -> sendRead(3007,2),700); }
    void sendRead(int address,int qty) { sendCommand(new byte[]{1,3,(byte)(address>>8),(byte)address,(byte)(qty>>8),(byte)qty}); }
    void writeRegister(int address,int value) { if (!ready) { setStatus("Conecte antes de enviar comando", Color.RED); return; } sendCommand(new byte[]{1,6,(byte)(address>>8),(byte)address,(byte)(value>>8),(byte)value}); handler.postDelayed(this::readStatus,900); }
    void sendCommand(byte[] body) { try { byte[] cmd=withCrc(body); responseLen=0; writeChar.setValue(cmd); gatt.writeCharacteristic(writeChar); log("TX "+bytesToHex(cmd)); } catch(SecurityException e) { setStatus("Permissão negada ao enviar", Color.RED); } catch(Exception e) { setStatus("Erro ao enviar: "+e.getMessage(), Color.RED); } }

    void handleNotify(byte[] data) {
        if (data==null||data.length==0) return; if (responseLen+data.length>responseBuffer.length) responseLen=0; System.arraycopy(data,0,responseBuffer,responseLen,data.length); responseLen+=data.length; byte[] current=Arrays.copyOf(responseBuffer,responseLen); runOnUiThread(() -> log("RX "+bytesToHex(data)));
        if (responseLen>=5 && current[0]==1 && current[1]==3) { int byteCount=current[2]&0xff; int total=byteCount+5; if (responseLen>=total) { byte[] pkt=Arrays.copyOf(current,total); responseLen=0; if (crcOk(pkt)) parseRead(pkt); else runOnUiThread(() -> log("CRC inválido")); } }
        else if (responseLen>=8 && current[0]==1 && current[1]==6) { byte[] pkt=Arrays.copyOf(current,8); responseLen=0; if (crcOk(pkt)) runOnUiThread(() -> { log("Comando aceito"); setStatus("Comando enviado", Color.rgb(0,120,70)); }); }
    }

    void parseRead(byte[] pkt) {
        int byteCount=pkt[2]&0xff, words=byteCount/2; int[] w=new int[words]; for(int i=0;i<words;i++) w[i]=((pkt[3+i*2]&0xff)<<8)|(pkt[4+i*2]&0xff);
        runOnUiThread(() -> {
            if (words==8) { int dcInput=w[0], acInput=w[1], acOutput=w[2], dcOutput=w[3]; lastSoc=w[7]; dcIn.setText("Entrada DC: "+dcInput+" W"); acIn.setText("Entrada AC: "+acInput+" W"); acOut.setText("Saída AC: "+acOutput+" W"); dcOut.setText("Saída DC: "+dcOutput+" W"); battery.setText("Bateria: "+lastSoc+"%"); setStatus("Dados atualizados", Color.rgb(0,120,70)); runAutomationCheck(false); }
            else if (words==2) { acValue=w[0]; dcValue=w[1]; acState.setText("Controle AC: "+(acValue==1?"Ligado":acValue==0?"Desligado":"valor "+acValue)); dcState.setText("Controle DC: "+(dcValue==1?"Ligado":dcValue==0?"Desligado":"valor "+dcValue)); }
        });
    }

    byte[] withCrc(byte[] body) { int crc=crc16(body); byte[] out=Arrays.copyOf(body,body.length+2); out[out.length-2]=(byte)(crc&0xff); out[out.length-1]=(byte)((crc>>8)&0xff); return out; }
    boolean crcOk(byte[] p) { if (p.length<3) return false; int got=(p[p.length-2]&0xff)|((p[p.length-1]&0xff)<<8); return got==crc16(Arrays.copyOf(p,p.length-2)); }
    int crc16(byte[] bytes) { int crc=0xffff; for(byte b:bytes){ crc^=(b&0xff); for(int i=0;i<8;i++) crc=(crc&1)!=0?(crc>>1)^0xA001:crc>>1; } return crc&0xffff; }
    void setStatus(String s,int color){ status.setText("Status: "+s); status.setTextColor(color); }
    void log(String s){ TextView v=tv(new Date()+"\n"+s,12,Color.DKGRAY); logBox.addView(v,0); }
    String bytesToHex(byte[] b){ if(b==null)return""; StringBuilder sb=new StringBuilder(); for(byte x:b) sb.append(String.format("%02X ",x)); return sb.toString().trim(); }
}
