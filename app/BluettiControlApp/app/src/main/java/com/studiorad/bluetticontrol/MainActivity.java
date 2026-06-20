package com.studiorad.bluetticontrol;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final UUID NOTIFY_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID WRITE_UUID  = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    LinearLayout root, list, logBox, dashboard, savedBox;
    TextView status, deviceTitle, battery, acIn, dcIn, acOut, dcOut, acState, dcState, automationStatus;
    EditText lowPct, highPct, intervalSec, manualName, manualMac;
    CheckBox autoEnable, autoAc, autoDc, showBatteryGraph, showConsumptionGraph;
    GraphView batteryGraph, consumptionGraph;
    BluetoothAdapter bt;
    BluetoothLeScanner scanner;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic writeChar, notifyChar;
    BluetoothDevice selected;
    String selectedName = "";
    final ArrayList<BluetoothDevice> found = new ArrayList<>();
    final ArrayList<Integer> batteryHistory = new ArrayList<>();
    final ArrayList<Integer> consumptionHistory = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    final byte[] responseBuffer = new byte[512];
    int responseLen = 0, lastSoc = -1, lastConsumption = 0, acValue = -1, dcValue = -1;
    boolean scanning = false, ready = false, automationRunning = false, automationBusy = false;
    long lastAutomationAction = 0;
    int blue = Color.rgb(21,101,192), bg = Color.rgb(245,247,251), dark = Color.rgb(20,30,45), green = Color.rgb(0,120,70);

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
        loadChartPrefs();
        renderSavedDevices();
        requestBlePermissions();
    }

    TextView tv(String t, int sp, int c) { TextView v = new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(c); v.setPadding(20,10,20,6); return v; }
    Button btn(String t) { Button b = new Button(this); b.setText(t); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundColor(blue); return b; }
    TextView metric(String title) { TextView v = tv(title + ": --", 17, dark); v.setBackgroundColor(Color.WHITE); v.setPadding(24,18,24,18); return v; }
    LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(18,14,18,14); c.setBackgroundColor(Color.WHITE); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(14,10,14,10); c.setLayoutParams(lp); return c; }

    void buildUi() {
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); sv.addView(root);
        root.addView(tv("Bluetti Control", 28, dark));
        root.addView(tv("Dashboard direto Bluetooth BLE com cards, gráficos, múltiplas Bluetti e automações locais.", 14, Color.DKGRAY));

        LinearLayout top = card();
        status = tv("Status: aguardando Bluetooth", 15, Color.DKGRAY);
        deviceTitle = tv("Dispositivo: nenhum", 17, dark);
        top.addView(status); top.addView(deviceTitle); root.addView(top);

        LinearLayout row = new LinearLayout(this); row.setPadding(10,8,10,8); row.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = btn("Buscar Bluetti"), stop = btn("Parar"); row.addView(scan,new LinearLayout.LayoutParams(0,-2,1)); row.addView(stop,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        LinearLayout cmdRow = new LinearLayout(this); cmdRow.setPadding(10,8,10,8); cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = btn("Conectar"), disconnect = btn("Desconectar"); cmdRow.addView(connect,new LinearLayout.LayoutParams(0,-2,1)); cmdRow.addView(disconnect,new LinearLayout.LayoutParams(0,-2,1)); root.addView(cmdRow);
        LinearLayout actionRow = new LinearLayout(this); actionRow.setPadding(10,8,10,8); actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = btn("Atualizar"), reconnect = btn("Reconectar"); actionRow.addView(refresh,new LinearLayout.LayoutParams(0,-2,1)); actionRow.addView(reconnect,new LinearLayout.LayoutParams(0,-2,1)); root.addView(actionRow);

        dashboard = card(); dashboard.addView(tv("Resumo", 22, dark));
        battery = metric("Bateria"); acIn = metric("Entrada AC"); dcIn = metric("Entrada DC"); acOut = metric("Saída AC"); dcOut = metric("Saída DC"); acState = metric("Controle AC"); dcState = metric("Controle DC");
        dashboard.addView(battery); dashboard.addView(acIn); dashboard.addView(dcIn); dashboard.addView(acOut); dashboard.addView(dcOut); dashboard.addView(acState); dashboard.addView(dcState); root.addView(dashboard);

        LinearLayout powerRow = new LinearLayout(this); powerRow.setPadding(10,8,10,8); powerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button acOn=btn("AC ON"), acOff=btn("AC OFF"); powerRow.addView(acOn,new LinearLayout.LayoutParams(0,-2,1)); powerRow.addView(acOff,new LinearLayout.LayoutParams(0,-2,1)); root.addView(powerRow);
        LinearLayout powerRow2 = new LinearLayout(this); powerRow2.setPadding(10,0,10,8); powerRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button dcOn=btn("DC ON"), dcOff=btn("DC OFF"); powerRow2.addView(dcOn,new LinearLayout.LayoutParams(0,-2,1)); powerRow2.addView(dcOff,new LinearLayout.LayoutParams(0,-2,1)); root.addView(powerRow2);

        LinearLayout chartCard = card(); chartCard.addView(tv("Gráficos", 22, dark));
        showBatteryGraph = new CheckBox(this); showBatteryGraph.setText("Mostrar gráfico de bateria"); chartCard.addView(showBatteryGraph);
        batteryGraph = new GraphView(this, "Bateria %", 100); chartCard.addView(batteryGraph, new LinearLayout.LayoutParams(-1, 360));
        showConsumptionGraph = new CheckBox(this); showConsumptionGraph.setText("Mostrar gráfico de consumo"); chartCard.addView(showConsumptionGraph);
        consumptionGraph = new GraphView(this, "Consumo W", 1000); chartCard.addView(consumptionGraph, new LinearLayout.LayoutParams(-1, 360));
        root.addView(chartCard);

        LinearLayout devCard = card(); devCard.addView(tv("Minhas Bluetti", 22, dark));
        manualName = new EditText(this); manualName.setHint("Nome da Bluetti. Ex: EB3A Garagem"); devCard.addView(manualName);
        manualMac = new EditText(this); manualMac.setHint("MAC Bluetooth. Ex: AA:BB:CC:DD:EE:FF"); devCard.addView(manualMac);
        LinearLayout devRow = new LinearLayout(this); devRow.setOrientation(LinearLayout.HORIZONTAL);
        Button addManual = btn("Adicionar Bluetti"), clearSaved = btn("Limpar lista"); devRow.addView(addManual,new LinearLayout.LayoutParams(0,-2,1)); devRow.addView(clearSaved,new LinearLayout.LayoutParams(0,-2,1)); devCard.addView(devRow);
        savedBox = new LinearLayout(this); savedBox.setOrientation(LinearLayout.VERTICAL); devCard.addView(savedBox); root.addView(devCard);

        LinearLayout autoCard = card(); autoCard.addView(tv("Automações", 22, dark));
        autoCard.addView(tv("Desliga abaixo da porcentagem definida e liga novamente acima do limite alto. Possui proteção contra loop.", 13, Color.DKGRAY));
        autoEnable = new CheckBox(this); autoEnable.setText("Ativar automação local"); autoCard.addView(autoEnable);
        autoAc = new CheckBox(this); autoAc.setText("Automatizar AC"); autoCard.addView(autoAc);
        autoDc = new CheckBox(this); autoDc.setText("Automatizar DC"); autoCard.addView(autoDc);
        lowPct = new EditText(this); lowPct.setHint("Desligar abaixo de %"); lowPct.setInputType(2); autoCard.addView(lowPct);
        highPct = new EditText(this); highPct.setHint("Ligar acima de %"); highPct.setInputType(2); autoCard.addView(highPct);
        intervalSec = new EditText(this); intervalSec.setHint("Intervalo de verificação em segundos"); intervalSec.setInputType(2); autoCard.addView(intervalSec);
        LinearLayout autoRow = new LinearLayout(this); autoRow.setOrientation(LinearLayout.HORIZONTAL);
        Button saveAuto = btn("Salvar"), testAuto = btn("Testar agora"); autoRow.addView(saveAuto,new LinearLayout.LayoutParams(0,-2,1)); autoRow.addView(testAuto,new LinearLayout.LayoutParams(0,-2,1)); autoCard.addView(autoRow);
        automationStatus = tv("Automação: desligada", 15, Color.DKGRAY); autoCard.addView(automationStatus); root.addView(autoCard);

        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        LinearLayout logCard = card(); logCard.addView(tv("Log",18,dark)); logBox = new LinearLayout(this); logBox.setOrientation(LinearLayout.VERTICAL); logCard.addView(logBox); root.addView(logCard);

        scan.setOnClickListener(v->startScan()); stop.setOnClickListener(v->stopScan()); connect.setOnClickListener(v->connectSelected()); disconnect.setOnClickListener(v->disconnect());
        refresh.setOnClickListener(v->readStatus()); reconnect.setOnClickListener(v->{ disconnect(); handler.postDelayed(this::connectSelected,800); });
        acOn.setOnClickListener(v->writeRegister(3007,1)); acOff.setOnClickListener(v->writeRegister(3007,0)); dcOn.setOnClickListener(v->writeRegister(3008,1)); dcOff.setOnClickListener(v->writeRegister(3008,0));
        saveAuto.setOnClickListener(v->{ saveAutomation(); applyAutomationState(); }); testAuto.setOnClickListener(v->runAutomationCheck(true));
        addManual.setOnClickListener(v->addManualDevice()); clearSaved.setOnClickListener(v->{ getSharedPreferences("devices",0).edit().clear().apply(); renderSavedDevices(); });
        showBatteryGraph.setOnClickListener(v->{ saveChartPrefs(); updateChartsVisibility(); }); showConsumptionGraph.setOnClickListener(v->{ saveChartPrefs(); updateChartsVisibility(); });
        setContentView(sv);
    }

    void loadChartPrefs(){ SharedPreferences p=getSharedPreferences("charts",0); showBatteryGraph.setChecked(p.getBoolean("battery",true)); showConsumptionGraph.setChecked(p.getBoolean("consumption",true)); updateChartsVisibility(); }
    void saveChartPrefs(){ getSharedPreferences("charts",0).edit().putBoolean("battery",showBatteryGraph.isChecked()).putBoolean("consumption",showConsumptionGraph.isChecked()).apply(); }
    void updateChartsVisibility(){ batteryGraph.setVisibility(showBatteryGraph.isChecked()?android.view.View.VISIBLE:android.view.View.GONE); consumptionGraph.setVisibility(showConsumptionGraph.isChecked()?android.view.View.VISIBLE:android.view.View.GONE); }

    void addManualDevice(){ String n=manualName.getText().toString().trim(); String m=manualMac.getText().toString().trim().toUpperCase(Locale.ROOT); if(n.isEmpty()) n="Bluetti"; if(!m.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")){ log("MAC inválido"); return; } SharedPreferences p=getSharedPreferences("devices",0); p.edit().putString(m,n).apply(); manualName.setText(""); manualMac.setText(""); renderSavedDevices(); }
    void saveFoundDevice(String name,String mac){ if(mac==null||mac.isEmpty())return; if(name==null||name.isEmpty()) name="Bluetti"; getSharedPreferences("devices",0).edit().putString(mac,name).apply(); renderSavedDevices(); }
    void renderSavedDevices(){ if(savedBox==null)return; savedBox.removeAllViews(); Map<String,?> all=getSharedPreferences("devices",0).getAll(); if(all.isEmpty()){ savedBox.addView(tv("Nenhuma Bluetti salva ainda.",13,Color.DKGRAY)); return; } for(String mac: all.keySet()){ String name=String.valueOf(all.get(mac)); LinearLayout c=card(); c.addView(tv(name,17,dark)); c.addView(tv(mac,13,Color.DKGRAY)); Button b=btn("Usar esta Bluetti"); c.addView(b); savedBox.addView(c); b.setOnClickListener(v->{ selected=bt.getRemoteDevice(mac); selectedName=name; deviceTitle.setText("Dispositivo: "+name+" • "+mac); connectSelected(); }); } }

    void loadAutomation(){ SharedPreferences p=getSharedPreferences("auto",0); autoEnable.setChecked(p.getBoolean("enabled",false)); autoAc.setChecked(p.getBoolean("ac",true)); autoDc.setChecked(p.getBoolean("dc",true)); lowPct.setText(String.valueOf(p.getInt("low",20))); highPct.setText(String.valueOf(p.getInt("high",80))); intervalSec.setText(String.valueOf(p.getInt("interval",30))); applyAutomationState(); }
    void saveAutomation(){ int low=clamp(getInt(lowPct,20),1,99), high=clamp(getInt(highPct,80),1,100); if(high<=low) high=Math.min(100,low+5); int interval=Math.max(15,getInt(intervalSec,30)); lowPct.setText(String.valueOf(low)); highPct.setText(String.valueOf(high)); intervalSec.setText(String.valueOf(interval)); getSharedPreferences("auto",0).edit().putBoolean("enabled",autoEnable.isChecked()).putBoolean("ac",autoAc.isChecked()).putBoolean("dc",autoDc.isChecked()).putInt("low",low).putInt("high",high).putInt("interval",interval).apply(); log("Automação salva: OFF abaixo de "+low+"%, ON acima de "+high+"%"); }
    void applyAutomationState(){ automationRunning=autoEnable!=null&&autoEnable.isChecked(); handler.removeCallbacks(automationLoop); if(automationRunning){ automationStatus.setText("Automação: ativa"); automationStatus.setTextColor(green); handler.postDelayed(automationLoop,1000); } else if(automationStatus!=null){ automationStatus.setText("Automação: desligada"); automationStatus.setTextColor(Color.DKGRAY); } }
    void runAutomationCheck(boolean manual){ if(lastSoc<0){ if(manual)log("Automação: bateria ainda não foi lida"); return; } int low=clamp(getInt(lowPct,20),1,99), high=clamp(getInt(highPct,80),1,100); long now=System.currentTimeMillis(); if(!manual&&now-lastAutomationAction<15000)return; if(automationBusy)return; automationBusy=true; try{ if(lastSoc<=low){ if(autoAc.isChecked()&&acValue!=0){ log("Automação: bateria "+lastSoc+"%. Desligando AC."); writeRegister(3007,0); lastAutomationAction=now; } if(autoDc.isChecked()&&dcValue!=0){ handler.postDelayed(()->{ log("Automação: desligando DC."); writeRegister(3008,0); },700); lastAutomationAction=now; } } else if(lastSoc>=high){ if(autoAc.isChecked()&&acValue!=1){ log("Automação: bateria "+lastSoc+"%. Ligando AC."); writeRegister(3007,1); lastAutomationAction=now; } if(autoDc.isChecked()&&dcValue!=1){ handler.postDelayed(()->{ log("Automação: ligando DC."); writeRegister(3008,1); },700); lastAutomationAction=now; } } else if(manual) log("Automação: sem ação. Bateria "+lastSoc+"%."); } finally{ handler.postDelayed(()->automationBusy=false,2500); } }

    int getInt(EditText e,int def){ try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception ex){return def;} } int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    void requestBlePermissions(){ if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},10); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},10); }
    boolean hasBlePermission(){ if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }

    void startScan(){ if(bt==null||!bt.isEnabled()){setStatus("Bluetooth desligado",Color.RED);return;} if(!hasBlePermission()){requestBlePermissions();setStatus("Permissão Bluetooth necessária",Color.RED);return;} scanner=bt.getBluetoothLeScanner(); if(scanner==null){setStatus("Scanner BLE indisponível",Color.RED);return;} found.clear(); list.removeAllViews(); scanning=true; setStatus("Buscando Bluetti por 15 segundos...",blue); scanner.startScan(scanCallback); handler.postDelayed(this::stopScan,15000); }
    void stopScan(){ if(!scanning)return; scanning=false; try{if(scanner!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){} setStatus("Busca finalizada: "+found.size()+" candidato(s)",Color.DKGRAY); }
    final ScanCallback scanCallback=new ScanCallback(){ public void onScanResult(int type,ScanResult r){ BluetoothDevice d=r.getDevice(); String name=""; try{name=d.getName();}catch(SecurityException ignored){} if(name==null)name=""; String u=name.toUpperCase(Locale.ROOT); boolean ok=u.contains("BLUETTI")||u.startsWith("AC")||u.startsWith("EB")||u.startsWith("EP")||u.startsWith("PBOX")||u.startsWith("EBOX")||u.startsWith("EL"); if(!ok)return; for(BluetoothDevice x:found)if(x.getAddress().equals(d.getAddress()))return; found.add(d); addDeviceCard(d,r.getRssi(),name); } public void onScanFailed(int e){setStatus("Erro no scan BLE: "+e,Color.RED);} };
    void addDeviceCard(BluetoothDevice d,int rssi,String name){ LinearLayout c=card(); c.addView(tv(name==null||name.isEmpty()?"Bluetti sem nome":name,18,dark)); c.addView(tv(d.getAddress()+" • RSSI "+rssi+" dBm",13,Color.DKGRAY)); LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); Button use=btn("Conectar"), save=btn("Salvar"); row.addView(use,new LinearLayout.LayoutParams(0,-2,1)); row.addView(save,new LinearLayout.LayoutParams(0,-2,1)); c.addView(row); list.addView(c); use.setOnClickListener(v->{selected=d;selectedName=name;deviceTitle.setText("Dispositivo: "+(name==null||name.isEmpty()?d.getAddress():name));stopScan();connectSelected();}); save.setOnClickListener(v->saveFoundDevice(name,d.getAddress())); }

    void connectSelected(){ if(selected==null){setStatus("Selecione uma Bluetti primeiro",Color.RED);return;} if(!hasBlePermission()){requestBlePermissions();return;} try{disconnect();ready=false;setStatus("Conectando direto via BLE...",blue);gatt=selected.connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);}catch(SecurityException e){setStatus("Permissão negada para conectar",Color.RED);} }
    void disconnect(){ handler.removeCallbacks(automationLoop); try{if(gatt!=null){gatt.disconnect();gatt.close();}}catch(Exception ignored){} gatt=null;writeChar=null;notifyChar=null;ready=false;applyAutomationState(); }
    final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){ public void onConnectionStateChange(BluetoothGatt g,int code,int state){ runOnUiThread(()->{ if(state==BluetoothProfile.STATE_CONNECTED){setStatus("Online direto BLE",green);log("Conectado. GATT: "+code); try{g.requestMtu(247);g.discoverServices();}catch(SecurityException ignored){}} else if(state==BluetoothProfile.STATE_DISCONNECTED){setStatus("Offline / desconectado",Color.RED);log("Desconectado. GATT: "+code);ready=false;} }); } public void onServicesDiscovered(BluetoothGatt g,int code){ notifyChar=findChar(g,NOTIFY_UUID); writeChar=findChar(g,WRITE_UUID); runOnUiThread(()->log("Serviços Bluetti: Notify="+(notifyChar!=null)+" Write="+(writeChar!=null))); if(notifyChar!=null)enableNotify(g,notifyChar); else runOnUiThread(()->setStatus("Serviço Bluetti FF01 não encontrado",Color.RED)); } public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){ ready=true; runOnUiThread(()->{setStatus("Online - pronto",green);readStatus();applyAutomationState();}); } public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleNotify(c.getValue());} public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int code){handleNotify(c.getValue());} public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code){runOnUiThread(()->log("Write status="+code));} };
    BluetoothGattCharacteristic findChar(BluetoothGatt g,UUID uuid){ for(BluetoothGattService s:g.getServices())for(BluetoothGattCharacteristic c:s.getCharacteristics())if(uuid.equals(c.getUuid()))return c; return null; }
    void enableNotify(BluetoothGatt g,BluetoothGattCharacteristic c){ try{g.setCharacteristicNotification(c,true); BluetoothGattDescriptor d=c.getDescriptor(CCCD_UUID); if(d!=null){d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);g.writeDescriptor(d);}else{ready=true;readStatus();}}catch(SecurityException e){setStatus("Permissão negada em notification",Color.RED);} }
    void readStatus(){ if(!ready||writeChar==null){setStatus("Canal de dados não pronto",Color.RED);return;} setStatus("Lendo dados...",blue); sendRead(36,8); handler.postDelayed(()->sendRead(3007,2),700); }
    void sendRead(int a,int q){sendCommand(new byte[]{1,3,(byte)(a>>8),(byte)a,(byte)(q>>8),(byte)q});} void writeRegister(int a,int v){ if(!ready){setStatus("Conecte antes",Color.RED);return;} sendCommand(new byte[]{1,6,(byte)(a>>8),(byte)a,(byte)(v>>8),(byte)v}); handler.postDelayed(this::readStatus,900); }
    void sendCommand(byte[] body){ try{byte[] cmd=withCrc(body);responseLen=0;writeChar.setValue(cmd);gatt.writeCharacteristic(writeChar);log("TX "+bytesToHex(cmd));}catch(Exception e){setStatus("Erro ao enviar: "+e.getMessage(),Color.RED);} }
    void handleNotify(byte[] data){ if(data==null||data.length==0)return; if(responseLen+data.length>responseBuffer.length)responseLen=0; System.arraycopy(data,0,responseBuffer,responseLen,data.length); responseLen+=data.length; byte[] cur=Arrays.copyOf(responseBuffer,responseLen); if(responseLen>=5&&cur[0]==1&&cur[1]==3){int bc=cur[2]&0xff,total=bc+5;if(responseLen>=total){byte[] pkt=Arrays.copyOf(cur,total);responseLen=0;if(crcOk(pkt))parseRead(pkt);else log("CRC inválido");}} else if(responseLen>=8&&cur[0]==1&&cur[1]==6){byte[] pkt=Arrays.copyOf(cur,8);responseLen=0;if(crcOk(pkt))runOnUiThread(()->{log("Comando aceito");setStatus("Comando enviado",green);});} }
    void parseRead(byte[] pkt){ int bc=pkt[2]&0xff,words=bc/2; int[] w=new int[words]; for(int i=0;i<words;i++)w[i]=((pkt[3+i*2]&0xff)<<8)|(pkt[4+i*2]&0xff); runOnUiThread(()->{ if(words==8){ int dcInput=w[0],acInput=w[1],acOutput=w[2],dcOutput=w[3]; lastSoc=w[7]; lastConsumption=acOutput+dcOutput; dcIn.setText("Entrada DC: "+dcInput+" W"); acIn.setText("Entrada AC: "+acInput+" W"); acOut.setText("Saída AC: "+acOutput+" W"); dcOut.setText("Saída DC: "+dcOutput+" W"); battery.setText("Bateria: "+lastSoc+"%"); addHistory(batteryHistory,lastSoc); addHistory(consumptionHistory,lastConsumption); batteryGraph.setValues(batteryHistory); consumptionGraph.setMax(Math.max(1000,lastConsumption+200)); consumptionGraph.setValues(consumptionHistory); setStatus("Dados atualizados",green); runAutomationCheck(false); } else if(words==2){ acValue=w[0]; dcValue=w[1]; acState.setText("Controle AC: "+(acValue==1?"Ligado":acValue==0?"Desligado":"valor "+acValue)); dcState.setText("Controle DC: "+(dcValue==1?"Ligado":dcValue==0?"Desligado":"valor "+dcValue)); } }); }
    void addHistory(ArrayList<Integer> l,int v){ l.add(v); while(l.size()>30)l.remove(0); }
    byte[] withCrc(byte[] b){int crc=crc16(b);byte[] o=Arrays.copyOf(b,b.length+2);o[o.length-2]=(byte)(crc&0xff);o[o.length-1]=(byte)((crc>>8)&0xff);return o;} boolean crcOk(byte[] p){if(p.length<3)return false;int got=(p[p.length-2]&0xff)|((p[p.length-1]&0xff)<<8);return got==crc16(Arrays.copyOf(p,p.length-2));} int crc16(byte[] bs){int crc=0xffff;for(byte b:bs){crc^=(b&0xff);for(int i=0;i<8;i++)crc=(crc&1)!=0?(crc>>1)^0xA001:crc>>1;}return crc&0xffff;}
    void setStatus(String s,int color){status.setText("Status: "+s);status.setTextColor(color);} void log(String s){TextView v=tv(new Date()+"\n"+s,12,Color.DKGRAY);logBox.addView(v,0);} String bytesToHex(byte[] b){if(b==null)return"";StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format("%02X ",x));return sb.toString().trim();}

    public static class GraphView extends android.view.View { Paint p=new Paint(1), grid=new Paint(1), text=new Paint(1); ArrayList<Integer> values=new ArrayList<>(); String title; int max; public GraphView(Context c,String t,int m){super(c);title=t;max=m;grid.setColor(Color.rgb(225,231,240));grid.setStrokeWidth(2);p.setColor(Color.rgb(21,101,192));p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);text.setColor(Color.rgb(20,30,45));text.setTextSize(34);} public void setMax(int m){max=Math.max(1,m);} public void setValues(ArrayList<Integer> v){values=new ArrayList<>(v);invalidate();} protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Color.WHITE);c.drawText(title,24,44,text);for(int i=0;i<4;i++){float y=70+i*(h-100)/3f;c.drawLine(20,y,w-20,y,grid);} if(values.size()<2){c.drawText("Aguardando dados...",24,h/2,text);return;} float left=24,top=70,right=w-24,bottom=h-28;float step=(right-left)/(values.size()-1);Path path=new Path();for(int i=0;i<values.size();i++){float x=left+i*step;float y=bottom-(Math.min(max,values.get(i))/(float)max)*(bottom-top);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);c.drawText(String.valueOf(values.get(values.size()-1)),w-90,60,text);} }
}
