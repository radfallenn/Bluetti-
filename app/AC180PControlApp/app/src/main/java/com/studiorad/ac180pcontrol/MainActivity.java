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
    static final UUID FF01_NOTIFY = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    static final UUID FF02_WRITE = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    final Handler handler = new Handler(Looper.getMainLooper());
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothDevice selected;
    BluetoothGatt gatt;
    BluetoothGattCharacteristic notifyChar, writeChar;
    ScanCallback scanCallback;
    boolean connecting=false, connected=false, ready=false, monitor=false, commandBusy=false;
    int rxCount=0, txCount=0, stable2A=0;
    String lastRx="--", lastTx="--", lastAction="START";
    final ArrayList<String> captures = new ArrayList<>();

    LinearLayout root; TextView status, device, battery, input, output, acState, dcState, rxInfo, rxBox, logBox;
    int bg=Color.rgb(4,7,13), card=Color.rgb(12,18,29), text=Color.rgb(235,245,255), muted=Color.rgb(130,148,170);
    int cyan=Color.rgb(0,220,255), green=Color.rgb(0,255,153), red=Color.rgb(255,49,95), orange=Color.rgb(255,190,62), violet=Color.rgb(190,90,255);

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg); BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE); adapter=m==null?null:m.getAdapter(); scanner=adapter==null?null:adapter.getBluetoothLeScanner(); buildUi(); requestPerms(); }
    @Override protected void onDestroy(){super.onDestroy(); safeCloseGatt();}
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
        TextView title=tv("AC180P DRIVER",28,text); title.setGravity(Gravity.CENTER); root.addView(title); TextView sub=tv("Modo seguro: FF01 Notify + FF02 Write, sem probes agressivos",13,muted); sub.setGravity(Gravity.CENTER); root.addView(sub);
        LinearLayout top=cardBox(); status=tv("● Aguardando",16,cyan); device=tv("MAC: "+TARGET_MAC,14,muted); top.addView(status); top.addView(device); root.addView(top);
        LinearLayout metrics=cardBox(); metrics.addView(tv("PAINEL",12,muted)); battery=metric("▰ Bateria",green); input=metric("↯ Entrada",cyan); output=metric("⚡ Saída",orange); acState=metric("AC",violet); dcState=metric("DC",violet); rxInfo=metric("RX",cyan); row(metrics,battery,input); row(metrics,output,acState); row(metrics,dcState,rxInfo); root.addView(metrics);
        LinearLayout controls=cardBox(); controls.addView(tv("CONTROLES",12,muted)); Button scan=btn("Buscar"), connect=btn("Conectar Seguro"), services=btn("Canais"), read=btn("Ler Status"), monitorBtn=btn("Monitor ON/OFF"), reconnect=btn("Reconectar"), markAcOn=btn("Marcar AC ON"), markAcOff=btn("Marcar AC OFF"), markDcOn=btn("Marcar DC ON"), markDcOff=btn("Marcar DC OFF"), clear=btn("Limpar Captura"); row(controls,scan,connect); row(controls,services,read); row(controls,monitorBtn,reconnect); row(controls,markAcOn,markAcOff); row(controls,markDcOn,markDcOff); controls.addView(clear); root.addView(controls);
        LinearLayout rx=cardBox(); rx.addView(tv("PACOTES 2A 2A / JSON",12,muted)); rxBox=tv("Aguardando pacotes...",12,cyan); rx.addView(rxBox); root.addView(rx);
        LinearLayout logs=cardBox(); logs.addView(tv("LOG",12,muted)); logBox=tv("",12,muted); logs.addView(logBox); root.addView(logs);
        scan.setOnClickListener(v->scan()); connect.setOnClickListener(v->connectByMac()); services.setOnClickListener(v->discoverChannels()); read.setOnClickListener(v->readSequenceSafe()); monitorBtn.setOnClickListener(v->{monitor=!monitor; log("Monitor="+monitor); if(monitor) monitorLoop();}); reconnect.setOnClickListener(v->reconnectSafe());
        markAcOn.setOnClickListener(v->mark("AC_ON")); markAcOff.setOnClickListener(v->mark("AC_OFF")); markDcOn.setOnClickListener(v->mark("DC_ON")); markDcOff.setOnClickListener(v->mark("DC_OFF")); clear.setOnClickListener(v->{captures.clear();rxCount=0;txCount=0;stable2A=0;rxBox.setText("Captura limpa.");updateRxInfo();});
        setContentView(sv);
    }

    void setStatus(String s,int color){runOnUiThread(()->{status.setText("● "+s);status.setTextColor(color);status.setShadowLayer(10,0,0,color);});}
    void log(String s){runOnUiThread(()->logBox.setText(new Date()+"\n"+s+"\n\n"+logBox.getText()));}
    void rx(String s){runOnUiThread(()->rxBox.setText(s+"\n\n"+rxBox.getText()));}
    void updateRxInfo(){runOnUiThread(()->rxInfo.setText("RX\n"+rxCount+" pkts"));}
    void mark(String action){ lastAction=action; log("MARK "+action); rx("MARK "+action+" - mude o estado no app oficial e capture os pacotes"); }

    void scan(){ if(!hasPerms()){requestPerms();return;} if(scanner==null){setStatus("Scanner indisponível",red);return;} setStatus("Escaneando",cyan); scanCallback=new ScanCallback(){@Override public void onScanResult(int t,ScanResult r){try{BluetoothDevice d=r.getDevice();String mac=d.getAddress();String name=d.getName();if(TARGET_MAC.equalsIgnoreCase(mac)||(name!=null&&name.toUpperCase(Locale.ROOT).contains("AC180"))){selected=d;scanner.stopScan(this);setStatus("Encontrado",green);device.setText("Dispositivo: "+name+" • "+mac);log("Encontrado: "+name+" / "+mac);}}catch(Exception e){log("Scan: "+e.getMessage());}}}; try{scanner.startScan(scanCallback);handler.postDelayed(()->{try{if(scanCallback!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){}},12000);}catch(Exception e){log("Erro scan: "+e.getMessage());}}
    void connectByMac(){ if(!hasPerms()){requestPerms();return;} if(connecting){log("Conexão já em andamento");return;} safeCloseGatt(); try{selected=adapter.getRemoteDevice(TARGET_MAC);connecting=true;setStatus("Conectando",cyan);gatt=selected.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);}catch(Exception e){connecting=false;log("Erro conectar: "+e.getMessage());}}
    void reconnectSafe(){monitor=false; safeCloseGatt(); handler.postDelayed(this::connectByMac,1200);}
    void safeCloseGatt(){try{if(gatt!=null){gatt.disconnect();gatt.close();}}catch(Exception ignored){} gatt=null; notifyChar=null; writeChar=null; connected=false; ready=false; connecting=false; commandBusy=false;}

    final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int statusCode,int newState){log("GATT state status="+statusCode+" newState="+newState); if(newState==BluetoothProfile.STATE_CONNECTED){gatt=g;connected=true;connecting=false;setStatus("Conectado",green);try{g.requestMtu(247);}catch(Exception ignored){}handler.postDelayed(()->{try{if(gatt!=null)gatt.discoverServices();}catch(Exception e){log("discover: "+e.getMessage());}},900);}else if(newState==BluetoothProfile.STATE_DISCONNECTED){connected=false;ready=false;connecting=false;setStatus("Desconectado",red); if(statusCode==19||statusCode==133){log("GATT instável, feche o app oficial e use Reconectar");}}} 
        @Override public void onMtuChanged(BluetoothGatt g,int mtu,int status){log("MTU="+mtu+" status="+status);handler.postDelayed(()->{try{if(gatt!=null)gatt.discoverServices();}catch(Exception ignored){}},400);}
        @Override public void onServicesDiscovered(BluetoothGatt g,int statusCode){log("Serviços descobertos status="+statusCode);discoverChannels();handler.postDelayed(MainActivity.this::enableNotify,400);}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){log("Descriptor write status="+status);ready=status==0 && writeChar!=null;setStatus(ready?"Pronto":"Notify erro",ready?green:red); if(ready) startSafeCapture();}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){handleRx(c.getValue());}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handleRx(c.getValue());}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){commandBusy=false;log("Write status="+status+" char="+c.getUuid());}
    };

    void discoverChannels(){ if(gatt==null){log("GATT nulo");return;} notifyChar=null;writeChar=null;StringBuilder sb=new StringBuilder();try{for(BluetoothGattService s:gatt.getServices()){sb.append("S ").append(s.getUuid()).append("\n");for(BluetoothGattCharacteristic c:s.getCharacteristics()){int p=c.getProperties();sb.append("  C ").append(c.getUuid()).append(" p=").append(p).append("\n"); if(FF01_NOTIFY.equals(c.getUuid()))notifyChar=c; if(FF02_WRITE.equals(c.getUuid()))writeChar=c;}}log(sb.toString());log("Notify="+(notifyChar==null?"null":notifyChar.getUuid())+" Write="+(writeChar==null?"null":writeChar.getUuid()));}catch(Exception e){log("Erro UUID: "+e.getMessage());}}
    void enableNotify(){ if(gatt==null||notifyChar==null){setStatus("Notify não encontrado",red);return;} try{gatt.setCharacteristicNotification(notifyChar,true);BluetoothGattDescriptor d=notifyChar.getDescriptor(CCCD_UUID);if(d!=null){d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);gatt.writeDescriptor(d);}else{ready=writeChar!=null;setStatus(ready?"Pronto":"Sem CCCD",ready?green:orange);}}catch(Exception e){log("Erro notify: "+e.getMessage());}}

    void startSafeCapture(){ lastAction="SAFE_IDLE"; log("Driver seguro ativo: somente FF01/FF02. Pacotes 2A2A serão capturados sem probes agressivos."); rx("DRIVER SEGURO ATIVO\nAguardando pacotes 2A 2A"); }
    void readSequenceSafe(){ if(!ready){log("Leitura ignorada: canal não pronto");return;} lastAction="READ_SAFE"; writeModbus(0,32,"READ 0/32"); handler.postDelayed(()->writeModbus(36,16,"READ 36/16"),1600); handler.postDelayed(()->writeModbus(100,32,"READ 100/32"),3200); handler.postDelayed(()->writeModbus(3007,2,"READ 3007/2"),4800); }
    void monitorLoop(){ if(!monitor)return; if(ready) readSequenceSafe(); handler.postDelayed(this::monitorLoop,9000); }
    void writeModbus(int addr,int qty,String label){ byte[] b=new byte[]{1,3,(byte)(addr>>8),(byte)addr,(byte)(qty>>8),(byte)qty}; writeTo(writeChar,withCrc(b),label); }
    void writeTo(BluetoothGattCharacteristic c,byte[] data,String label){ if(gatt==null||c==null||!connected){log(label+": canal não pronto");return;} if(commandBusy){log(label+": aguardando comando anterior");return;} try{commandBusy=true;txCount++;lastTx=hex(data);c.setValue(data);gatt.writeCharacteristic(c);rx("TX #"+txCount+" "+label+"\n"+lastTx);log("TX "+label+": "+lastTx);}catch(Exception e){commandBusy=false;log("Erro TX "+label+": "+e.getMessage());}}

    void handleRx(byte[] data){ if(data==null||data.length==0)return; rxCount++; lastRx=hex(data); long ts=System.currentTimeMillis(); boolean is2A=data.length>=2&&(data[0]&255)==0x2A&&(data[1]&255)==0x2A; if(is2A) stable2A++; String type=is2A?"AC180P_2A2A":"RAW"; String json="{\"t\":"+ts+",\"type\":\""+type+"\",\"action\":\""+lastAction+"\",\"len\":"+data.length+",\"hex\":\""+lastRx.replace(" ","")+"\"}"; captures.add(0,json); while(captures.size()>160)captures.remove(captures.size()-1); rx("RX #"+rxCount+" "+type+" len="+data.length+" action="+lastAction+"\n"+lastRx+"\nJSON: "+json); log("RX "+lastRx); updateRxInfo(); parseEngineering(data); }
    void parseEngineering(byte[] d){ if(d.length==10&&(d[0]&255)==0x2A&&(d[1]&255)==0x2A){ int b4=d[4]&255,b5=d[5]&255,b6=d[6]&255,b7=d[7]&255,b8=d[8]&255,b9=d[9]&255; battery.setText("▰ Bateria\n2A ativo"); input.setText("↯ Entrada\n"+stable2A+" pkts"); output.setText("⚡ Saída\n"+String.format(Locale.ROOT,"%02X%02X",b4,b5)); acState.setText("AC\n"+String.format(Locale.ROOT,"%02X %02X",b6,b7)); dcState.setText("DC\n"+String.format(Locale.ROOT,"%02X %02X",b8,b9)); }}

    byte[] withCrc(byte[] body){int crc=0xffff;for(byte bb:body){crc^=(bb&255);for(int i=0;i<8;i++)crc=(crc&1)!=0?(crc>>1)^0xA001:crc>>1;}byte[] out=Arrays.copyOf(body,body.length+2);out[out.length-2]=(byte)(crc&255);out[out.length-1]=(byte)((crc>>8)&255);return out;}
    String hex(byte[] b){StringBuilder sb=new StringBuilder();for(byte x:b)sb.append(String.format(Locale.ROOT,"%02X ",x));return sb.toString().trim();}
}
