package com.studiorad.ac180pcontrol;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String TARGET_MAC = "C7:A3:2B:21:77:90";
    static final UUID OLD_NOTIFY_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID OLD_WRITE_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID FF03_UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb");
    static final UUID FF04_UUID = UUID.fromString("0000ff04-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    final Handler handler = new Handler(Looper.getMainLooper());
    BluetoothAdapter adapter; BluetoothLeScanner scanner; BluetoothGatt gatt; BluetoothGattCharacteristic notifyChar, writeChar, ff03Char, ff04Char; ScanCallback scanCallback;
    int rxCount=0, txCount=0; String lastRx="--", lastTx="--", lastAction="START"; final ArrayList<String> captures = new ArrayList<>();

    LinearLayout root; TextView status, device, battery, input, output, acState, dcState, rxInfo, rxBox, logBox;
    int bg=Color.rgb(4,7,13), card=Color.rgb(12,18,29), text=Color.rgb(235,245,255), muted=Color.rgb(130,148,170);
    int cyan=Color.rgb(0,220,255), green=Color.rgb(0,255,153), red=Color.rgb(255,49,95), orange=Color.rgb(255,190,62), violet=Color.rgb(190,90,255);

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg); BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE); adapter=m==null?null:m.getAdapter(); scanner=adapter==null?null:adapter.getBluetoothLeScanner(); buildUi(); requestPerms(); }
    void requestPerms(){ if(Build.VERSION.SDK_INT>=31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},1); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1); }
    boolean hasPerms(){ if(Build.VERSION.SDK_INT>=31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    android.graphics.drawable.Drawable rgbFrame(int fillColor,int radius){ GradientDrawable outer=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{cyan,green,orange,red,violet,cyan}); outer.setCornerRadius(dp(radius)); GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fillColor,Color.rgb(18,24,36),Color.rgb(8,11,18)}); inner.setCornerRadius(dp(radius-1)); LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,inner}); layer.setLayerInset(1,dp(2),dp(2),dp(2),dp(2)); return layer; }
    android.graphics.drawable.Drawable rgbButton(){ GradientDrawable outer=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{cyan,green,orange,red,violet}); outer.setCornerRadius(dp(22)); GradientDrawable middle=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,38,48),Color.rgb(8,11,18)}); middle.setCornerRadius(dp(21)); GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(37,42,54),Color.rgb(7,9,14)}); inner.setCornerRadius(dp(19)); LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,middle,inner}); layer.setLayerInset(1,dp(2),dp(2),dp(2),dp(2)); layer.setLayerInset(2,dp(4),dp(4),dp(4),dp(4)); return layer; }
    TextView tv(String s,int sp,int c){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(c); v.setPadding(dp(12),dp(6),dp(12),dp(6)); v.setShadowLayer(sp>=16?10:4,0,0,c); if(sp>=18)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v; }
    LinearLayout cardBox(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(16),dp(18),dp(16)); l.setBackground(rgbFrame(card,28)); l.setElevation(dp(12)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(12),dp(8),dp(12),dp(8)); l.setLayoutParams(lp); return l; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(text); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setShadowLayer(10,0,0,text); b.setMinHeight(dp(64)); b.setBackground(rgbButton()); b.setElevation(dp(14)); return b; }
    TextView metric(String title,int color){ TextView v=tv(title+"\n--",17,color); v.setGravity(Gravity.CENTER_VERTICAL); v.setBackground(rgbFrame(Color.rgb(18,25,38),24)); v.setPadding(dp(16),dp(16),dp(16),dp(16)); return v; }
    void row(LinearLayout p,View a,View b){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(0,-2,1); lp1.setMargins(dp(6),dp(6),dp(6),dp(6)); LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1); lp2.setMargins(dp(6),dp(6),dp(6),dp(6)); r.addView(a,lp1); r.addView(b,lp2); p.addView(r); }

    void buildUi(){
        ScrollView sv=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); root.setPadding(dp(4),dp(14),dp(4),dp(28)); sv.addView(root);
        TextView title=tv("AC180P ENGINEERING",27,text); title.setGravity(Gravity.CENTER); root.addView(title); TextView sub=tv("Captura real dos pacotes 2A 2A para mapear o protocolo",13,muted); sub.setGravity(Gravity.CENTER); root.addView(sub);
        LinearLayout top=cardBox(); status=tv("● Aguardando",16,cyan); device=tv("MAC: "+TARGET_MAC,14,muted); top.addView(status); top.addView(device); root.addView(top);
        LinearLayout metrics=cardBox(); metrics.addView(tv("PAINEL",12,muted)); battery=metric("▰ Bateria",green); input=metric("↯ Entrada",cyan); output=metric("⚡ Saída",orange); acState=metric("AC",violet); dcState=metric("DC",violet); rxInfo=metric("RX",cyan); row(metrics,battery,input); row(metrics,output,acState); row(metrics,dcState,rxInfo); root.addView(metrics);
        LinearLayout controls=cardBox(); controls.addView(tv("CONTROLES",12,muted)); Button scan=btn("Buscar AC180P"), connect=btn("Conectar MAC"), services=btn("Listar Serviços"), eng=btn("Modo Engenharia"), markAcOn=btn("Marcar AC ON"), markAcOff=btn("Marcar AC OFF"), markDcOn=btn("Marcar DC ON"), markDcOff=btn("Marcar DC OFF"), pulse=btn("Testar FF03/FF04"), clear=btn("Limpar Captura"); row(controls,scan,connect); row(controls,services,eng); row(controls,markAcOn,markAcOff); row(controls,markDcOn,markDcOff); row(controls,pulse,clear); root.addView(controls);
        LinearLayout rx=cardBox(); rx.addView(tv("PACOTES 2A 2A / JSON",12,muted)); rxBox=tv("Aguardando pacotes...",12,cyan); rx.addView(rxBox); root.addView(rx);
        LinearLayout logs=cardBox(); logs.addView(tv("LOG",12,muted)); logBox=tv("",12,muted); logs.addView(logBox); root.addView(logs);
        scan.setOnClickListener(v->scan()); connect.setOnClickListener(v->connectByMac()); services.setOnClickListener(v->discoverFallbackChannels()); eng.setOnClickListener(v->startEngineeringMode());
        markAcOn.setOnClickListener(v->mark("AC_ON")); markAcOff.setOnClickListener(v->mark("AC_OFF")); markDcOn.setOnClickListener(v->mark("DC_ON")); markDcOff.setOnClickListener(v->mark("DC_OFF")); pulse.setOnClickListener(v->testAltChannels()); clear.setOnClickListener(v->{captures.clear();rxCount=0;txCount=0;rxBox.setText("Captura limpa.");updateRxInfo();});
        setContentView(sv);
    }

    void setStatus(String s,int color){runOnUiThread(()->{status.setText("● "+s);status.setTextColor(color);status.setShadowLayer(10,0,0,color);});}
    void log(String s){runOnUiThread(()->logBox.setText(new Date()+"\n"+s+"\n\n"+logBox.getText()));}
    void rx(String s){runOnUiThread(()->rxBox.setText(s+"\n\n"+rxBox.getText()));}
    void updateRxInfo(){runOnUiThread(()->rxInfo.setText("RX\n"+rxCount+" pkts"));}
    void mark(String action){ lastAction=action; log("MARK "+action); rx("MARK "+action+" - agora altere no app oficial ou pressione comandos"); }

    void scan(){ if(!hasPerms()){requestPerms();return;} if(scanner==null){setStatus("Scanner indisponível",red);return;} setStatus("Escaneando",cyan); scanCallback=new ScanCallback(){@Override public void onScanResult(int t,ScanResult r){try{BluetoothDevice d=r.getDevice();String mac=d.getAddress();String name=d.getName();if(TARGET_MAC.equalsIgnoreCase(mac)||(name!=null&&name.toUpperCase(Locale.ROOT).contains("AC180"))){selected=d;scanner.stopScan(this);setStatus("Encontrado",green);device.setText("Dispositivo: "+name+" • "+mac);log("Encontrado: "+name+" / "+mac);}}catch(Exception e){log("Scan: "+e.getMessage());}}}; try{scanner.startScan(scanCallback);handler.postDelayed(()->{try{if(scanCallback!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){}},12000);}catch(Exception e){log("Erro scan: "+e.getMessage());}}
    void connectByMac(){ if(!hasPerms()){requestPerms();return;} try{selected=adapter.getRemoteDevice(TARGET_MAC);setStatus("Conectando",cyan);gatt=selected.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);}catch(Exception e){log("Erro conectar: "+e.getMessage());}}

    final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int statusCode,int newState){log("GATT state status="+statusCode+" newState="+newState); if(newState==BluetoothProfile.STATE_CONNECTED){setStatus("Conectado",green);try{g.requestMtu(517);}catch(Exception ignored){}handler.postDelayed(g::discoverServices,900);}else if(newState==BluetoothProfile.STATE_DISCONNECTED)setStatus("Desconectado",red);} 
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){log("MTU="+mtu+" status="+status);try{g.discoverServices();}catch(Exception ignored){}}
        @Override public void onServicesDiscovered(BluetoothGatt g,int statusCode){log("Serviços descobertos status="+statusCode);discoverFallbackChannels();enableNotify();}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){log("Descriptor write status="+status);setStatus("Notify ativo",green);startEngineeringMode();}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleRx(c.getValue());}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handleRx(c.getValue());}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){log("Write status="+status+" char="+c.getUuid());}
    };

    void discoverFallbackChannels(){ if(gatt==null){log("GATT nulo");return;} notifyChar=null;writeChar=null;ff03Char=null;ff04Char=null;StringBuilder sb=new StringBuilder();try{for(BluetoothGattService s:gatt.getServices()){sb.append("S ").append(s.getUuid()).append("\n");for(BluetoothGattCharacteristic c:s.getCharacteristics()){int p=c.getProperties();sb.append("  C ").append(c.getUuid()).append(" p=").append(p).append("\n"); if(OLD_NOTIFY_UUID.equals(c.getUuid()))notifyChar=c; if(OLD_WRITE_UUID.equals(c.getUuid()))writeChar=c; if(FF03_UUID.equals(c.getUuid()))ff03Char=c; if(FF04_UUID.equals(c.getUuid()))ff04Char=c; if(notifyChar==null&&((p&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0||(p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0))notifyChar=c; if(writeChar==null&&((p&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0||(p&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0))writeChar=c;}}log(sb.toString());log("Notify="+(notifyChar==null?"null":notifyChar.getUuid())+" Write="+(writeChar==null?"null":writeChar.getUuid())+" FF03="+(ff03Char==null?"null":ff03Char.getUuid())+" FF04="+(ff04Char==null?"null":ff04Char.getUuid()));}catch(Exception e){log("Erro UUID: "+e.getMessage());}}
    void enableNotify(){ if(gatt==null||notifyChar==null){setStatus("Notify não encontrado",red);return;} try{gatt.setCharacteristicNotification(notifyChar,true);BluetoothGattDescriptor d=notifyChar.getDescriptor(CCCD_UUID);if(d!=null){int p=notifyChar.getProperties();d.setValue((p&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);gatt.writeDescriptor(d);}else setStatus("Notify sem CCCD",orange);}catch(Exception e){log("Erro notify: "+e.getMessage());}}

    void startEngineeringMode(){ lastAction="ENGINEERING_IDLE"; setStatus("Engenharia ativa",green); log("Modo Engenharia: capturando pacotes 2A 2A. Use Marcar AC/DC antes de mudar algo no app oficial."); rx("MODO ENGENHARIA ATIVO\nCapturando pacotes 2A 2A"); }
    void testAltChannels(){ writeTo(writeChar,new byte[]{0x2A,0x2A,0x01,0x00},"FF02 PROBE 2A2A0100"); handler.postDelayed(()->writeTo(ff03Char,new byte[]{0x2A,0x2A,0x01,0x00},"FF03 PROBE"),700); handler.postDelayed(()->writeTo(ff04Char,new byte[]{0x2A,0x2A,0x01,0x00},"FF04 PROBE"),1400); }
    void writeTo(BluetoothGattCharacteristic c,byte[] data,String label){ if(gatt==null||c==null){log(label+": canal nulo");return;} try{txCount++;lastTx=hex(data);c.setValue(data);gatt.writeCharacteristic(c);rx("TX #"+txCount+" "+label+"\n"+lastTx);log("TX "+label+": "+lastTx);}catch(Exception e){log("Erro TX "+label+": "+e.getMessage());}}

    void handleRx(byte[] data){ if(data==null||data.length==0)return; rxCount++; lastRx=hex(data); long ts=System.currentTimeMillis(); String type=(data.length>=2&&(data[0]&255)==0x2A&&(data[1]&255)==0x2A)?"AC180P_2A2A":"RAW"; String json="{\"t\":"+ts+",\"type\":\""+type+"\",\"action\":\""+lastAction+"\",\"len\":"+data.length+",\"hex\":\""+lastRx.replace(" ","")+"\"}"; captures.add(0,json); while(captures.size()>160)captures.remove(captures.size()-1); rx("RX #"+rxCount+" "+type+" len="+data.length+" action="+lastAction+"\n"+lastRx+"\nJSON: "+json); log("RX "+lastRx); updateRxInfo(); parseEngineering(data); }
    void parseEngineering(byte[] d){ if(d.length==10&&(d[0]&255)==0x2A&&(d[1]&255)==0x2A){ int b2=d[2]&255,b3=d[3]&255,b4=d[4]&255,b5=d[5]&255,b6=d[6]&255,b7=d[7]&255,b8=d[8]&255,b9=d[9]&255; battery.setText("▰ Bateria\nAguardando mapa"); input.setText("↯ Entrada\n2A pkt"); output.setText("⚡ Saída\n"+String.format(Locale.ROOT,"%02X%02X",b4,b5)); acState.setText("AC\n"+String.format(Locale.ROOT,"%02X %02X",b6,b7)); dcState.setText("DC\n"+String.format(Locale.ROOT,"%02X %02X",b8,b9)); }}

    String hex(byte[] b){StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format(Locale.ROOT,"%02X ",x));return sb.toString().trim();}
}
