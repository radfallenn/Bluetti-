package com.studiorad.bluetticontrol;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ModernMainActivity extends MainActivity {
    int bg2=Color.rgb(238,242,247), navy=Color.rgb(26,28,30), muted=Color.rgb(100,116,139);
    int green2=Color.rgb(0,177,93), blue2=Color.rgb(77,142,254), red2=Color.rgb(225,29,72), orange2=Color.rgb(245,158,11), violet2=Color.rgb(155,81,224);
    TextView heroName, heroSoc, heroTime, statBattery, statInput, statOutput, statDc;

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(bg2); getWindow().setNavigationBarColor(bg2); if(android.os.Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); try{ if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(new android.content.Intent(this,BluettiPersistentService.class)); else startService(new android.content.Intent(this,BluettiPersistentService.class)); }catch(Exception e){} }
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable border(int color,int stroke,int radius){GradientDrawable g=shape(color,radius);g.setStroke(dp(1),stroke);return g;}

    @Override TextView tv(String t,int sp,int c){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(c==Color.DKGRAY?muted:c);v.setPadding(dp(14),dp(6),dp(14),dp(6));if(sp>=18)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setSingleLine(false);return v;}
    @Override Button btn(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(56));b.setElevation(dp(4));int c=blue2;String s=t.toLowerCase(Locale.ROOT);if(s.contains("off")||s.contains("deslig")||s.contains("parar")||s.contains("limpar")||s.contains("descon"))c=red2;else if(s.contains("on")||s.contains("ligar")||s.contains("conectar")||s.contains("salvar")||s.contains("adicionar")||s.contains("usar"))c=green2;else if(s.contains("recon")||s.contains("testar"))c=Color.rgb(95,75,230);b.setBackground(shape(c,16));return b;}
    @Override TextView metric(String title){TextView v=tv(title+": --",16,navy);v.setBackground(border(Color.WHITE,Color.rgb(220,227,236),28));v.setElevation(dp(4));v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),dp(16),dp(16),dp(16));return v;}
    @Override LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(border(Color.WHITE,Color.rgb(220,227,236),28));c.setElevation(dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(14),dp(10),dp(14),dp(10));c.setLayoutParams(lp);return c;}
    TextView pill(String text,int color){TextView v=tv(text,11,color);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setBackground(shape(color==red2?Color.rgb(255,226,226):Color.rgb(221,248,234),999));v.setPadding(dp(12),dp(5),dp(12),dp(5));return v;}
    void two(LinearLayout p,View a,View b){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(6),dp(6),dp(6),dp(6));r.addView(a,lp);r.addView(b,new LinearLayout.LayoutParams(0,-2,1));p.addView(r);} 

    @Override void buildUi(){
        ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg2);root.setPadding(dp(2),dp(10),dp(2),dp(24));sv.addView(root);
        LinearLayout topbar=new LinearLayout(this);topbar.setGravity(Gravity.CENTER_VERTICAL);topbar.setPadding(dp(10),dp(4),dp(10),dp(4));topbar.addView(tv("☰",28,muted),new LinearLayout.LayoutParams(0,-2,1));TextView logo=tv("BLUETTI",28,navy);logo.setGravity(Gravity.CENTER);topbar.addView(logo,new LinearLayout.LayoutParams(0,-2,3));TextView menu=tv("⚙",24,muted);menu.setGravity(Gravity.RIGHT);topbar.addView(menu,new LinearLayout.LayoutParams(0,-2,1));root.addView(topbar);

        LinearLayout hero=card();hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);heroName=tv("BLUETTI",24,navy);status=pill("● Aguardando",green2);deviceTitle=tv("Dispositivo: --",14,muted);mid.addView(heroName);mid.addView(status);mid.addView(deviceTitle);hero.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.RIGHT);heroSoc=tv("--%",44,green2);heroSoc.setGravity(Gravity.RIGHT);heroTime=tv("--\nTempo restante",12,muted);heroTime.setGravity(Gravity.RIGHT);right.addView(heroSoc);right.addView(heroTime);hero.addView(right,new LinearLayout.LayoutParams(dp(118),-2));root.addView(hero);

        LinearLayout statsCard=card();statsCard.addView(tv("RESUMO",12,muted));two(statsCard,stat("▰","Bateria","--%",green2),stat("↯","Entrada","-- W",blue2));two(statsCard,stat("⚡","Saída","-- W",orange2),stat("⏻","DC","-- W",violet2));root.addView(statsCard);
        battery=statBattery;acIn=statInput;acOut=statOutput;dcOut=statDc;dcIn=tv("Entrada DC: -- W",14,navy);acState=tv("AC --",14,navy);dcState=tv("DC --",14,navy);
        batteryGraph=new GraphView(this,"",100);batteryGraph.setVisibility(View.GONE);consumptionGraph=new GraphView(this,"",1000);consumptionGraph.setVisibility(View.GONE);showBatteryGraph=new CheckBox(this);showConsumptionGraph=new CheckBox(this);

        LinearLayout ctrl=card();ctrl.addView(tv("CONTROLE",12,muted));Button acOn=control("AC\nLIGAR",green2),acOff=control("AC\nDESLIGAR",red2),dcOn=control("DC\nLIGAR",green2),dcOff=control("DC\nDESLIGAR",red2);two(ctrl,acOn,acOff);two(ctrl,dcOn,dcOff);root.addView(ctrl);

        LinearLayout dev=card();dev.addView(tv("DISPOSITIVOS",12,muted));manualName=new EditText(this);manualName.setHint("Nome da Bluetti");manualMac=new EditText(this);manualMac.setHint("MAC Bluetooth");dev.addView(manualName);dev.addView(manualMac);Button add=btn("Adicionar"),clear=btn("Limpar lista");two(dev,add,clear);savedBox=new LinearLayout(this);savedBox.setOrientation(LinearLayout.VERTICAL);dev.addView(savedBox);root.addView(dev);

        LinearLayout auto=card();auto.addView(tv("AUTOMAÇÕES",12,muted));autoEnable=new CheckBox(this);autoEnable.setText("Ativar automação local");autoAc=new CheckBox(this);autoAc.setText("Automatizar AC");autoDc=new CheckBox(this);autoDc.setText("Automatizar DC");auto.addView(autoEnable);two(auto,autoAc,autoDc);lowPct=new EditText(this);lowPct.setHint("Desligar abaixo de %");lowPct.setInputType(2);highPct=new EditText(this);highPct.setHint("Ligar acima de %");highPct.setInputType(2);two(auto,lowPct,highPct);intervalSec=new EditText(this);intervalSec.setHint("Intervalo automação");intervalSec.setInputType(2);auto.addView(intervalSec);Button save=btn("Salvar"),test=btn("Testar agora");two(auto,save,test);automationStatus=tv("Automação: desligada",15,muted);auto.addView(automationStatus);root.addView(auto);

        LinearLayout conn=card();conn.addView(tv("CONEXÃO",12,muted));Button scan=btn("Buscar"),stop=btn("Parar"),connect=btn("Conectar"),disconnect=btn("Desconectar"),refresh=btn("Atualizar"),reconnect=btn("Reconectar");two(conn,scan,stop);two(conn,connect,disconnect);two(conn,refresh,reconnect);root.addView(conn);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);LinearLayout log=card();log.addView(tv("LOG",12,muted));logBox=new LinearLayout(this);logBox.setOrientation(LinearLayout.VERTICAL);log.addView(logBox);root.addView(log);
        scan.setOnClickListener(v->startScan());stop.setOnClickListener(v->stopScan());connect.setOnClickListener(v->connectSelected());disconnect.setOnClickListener(v->disconnect());refresh.setOnClickListener(v->readStatus());reconnect.setOnClickListener(v->{disconnect();handler.postDelayed(this::connectSelected,800);});acOn.setOnClickListener(v->writeRegister(3007,1));acOff.setOnClickListener(v->writeRegister(3007,0));dcOn.setOnClickListener(v->writeRegister(3008,1));dcOff.setOnClickListener(v->writeRegister(3008,0));add.setOnClickListener(v->addManualDevice());clear.setOnClickListener(v->{getSharedPreferences("devices",0).edit().clear().apply();renderSavedDevices();});save.setOnClickListener(v->{saveAutomation();applyAutomationState();});test.setOnClickListener(v->runAutomationCheck(true));setContentView(sv);
    }
    TextView stat(String icon,String label,String value,int color){TextView v=tv(icon+"  "+label+"\n"+value,18,color);v.setGravity(Gravity.CENTER_VERTICAL);v.setBackground(border(Color.WHITE,Color.rgb(220,227,236),28));v.setElevation(dp(4));v.setPadding(dp(16),dp(16),dp(16),dp(16));if(label.equals("Bateria"))statBattery=v;else if(label.equals("Entrada"))statInput=v;else if(label.equals("Saída"))statOutput=v;else statDc=v;return v;}
    Button control(String text,int color){Button b=btn(text);b.setTextColor(color);b.setTextSize(16);b.setMinHeight(dp(76));b.setBackground(border(color==green2?Color.rgb(221,248,234):Color.rgb(255,226,226),color,28));return b;}

    @Override void parseRead(byte[] pkt){try{if(pkt==null||pkt.length<5)return;int bc=pkt[2]&255,words=bc/2;if(words<2)return;int[] w=new int[words];for(int i=0;i<words&&4+i*2<pkt.length;i++)w[i]=((pkt[3+i*2]&255)<<8)|(pkt[4+i*2]&255);runOnUiThread(()->{if(words>=8){int dci=w[0],aci=w[1],aco=w[2],dco=w[3];lastSoc=w[7];lastConsumption=aco+dco;heroSoc.setText(lastSoc+"%");statBattery.setText("▰  Bateria\n"+lastSoc+"%");statInput.setText("↯  Entrada\n"+(aci+dci)+" W");statOutput.setText("⚡  Saída\n"+lastConsumption+" W");statDc.setText("⏻  DC\n"+dco+" W");setStatus("Conectado",green);runAutomationCheck(false);}else if(words==2){acValue=w[0];dcValue=w[1];}});}catch(Exception e){log("Parser seguro: "+e.getMessage());}}
}
