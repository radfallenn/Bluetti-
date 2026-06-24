package com.studiorad.bluetticontrol;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ModernMainActivity extends MainActivity {
    int bg2=Color.rgb(234,240,247), cardBg=Color.rgb(238,244,250), cardBg2=Color.rgb(241,246,251), navy=Color.rgb(16,28,45), muted=Color.rgb(134,148,164);
    int green2=Color.rgb(0,150,92), blue2=Color.rgb(47,121,201), red2=Color.rgb(201,42,70), orange2=Color.rgb(220,145,22), violet2=Color.rgb(125,80,170);
    TextView heroName, heroSoc, heroTime, statBattery, statInput, statOutput, statDc;
    int cardRadius=34, cardSpacing=8, cardMinHeight=112;

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(bg2); getWindow().setNavigationBarColor(bg2); if(android.os.Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR); try{ if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(new android.content.Intent(this,BluettiPersistentService.class)); else startService(new android.content.Intent(this,BluettiPersistentService.class)); }catch(Exception e){} }
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 

    GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    android.graphics.drawable.Drawable sunkenFrame(int fill,int radius){
        GradientDrawable dark=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(164,178,192),Color.rgb(225,233,242),Color.WHITE}); dark.setCornerRadius(dp(radius));
        GradientDrawable light=new GradientDrawable(GradientDrawable.Orientation.BR_TL,new int[]{Color.WHITE,Color.rgb(238,244,250),Color.rgb(178,190,204)}); light.setCornerRadius(dp(radius-2));
        GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,Color.rgb(246,250,253),Color.rgb(226,235,244)}); inner.setCornerRadius(dp(radius-5));
        LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{dark,light,inner});
        layer.setLayerInset(1,dp(5),dp(5),dp(5),dp(5));
        layer.setLayerInset(2,dp(11),dp(11),dp(11),dp(11));
        return layer;
    }
    android.graphics.drawable.Drawable insetField(){
        GradientDrawable top=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(165,181,196),Color.rgb(238,244,250),Color.WHITE}); top.setCornerRadius(dp(22));
        GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(214,225,235),Color.rgb(250,253,255),Color.rgb(232,240,247)}); inner.setCornerRadius(dp(20));
        LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{top,inner});
        layer.setLayerInset(1,dp(3),dp(5),dp(3),dp(3));
        return layer;
    }
    android.graphics.drawable.Drawable softButton(){
        GradientDrawable shade=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(170,184,198),Color.rgb(248,252,255)});shade.setCornerRadius(dp(24));
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.WHITE,Color.rgb(232,240,248),Color.rgb(250,253,255)});face.setCornerRadius(dp(22));
        LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{shade,face});layer.setLayerInset(1,dp(6),dp(6),dp(6),dp(6));return layer;
    }

    @Override TextView tv(String t,int sp,int c){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(c==Color.DKGRAY?muted:c);v.setPadding(dp(14),dp(6),dp(14),dp(6));if(sp>=18)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setSingleLine(false);return v;}
    @Override Button btn(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextColor(navy);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(62));b.setElevation(dp(16));b.setBackground(softButton());b.setPadding(dp(10),dp(10),dp(10),dp(10));return b;}
    @Override TextView metric(String title){TextView v=tv(title+": --",16,navy);v.setBackground(insetField());v.setElevation(dp(4));v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setMinHeight(dp(cardMinHeight));return v;}
    @Override LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(22),dp(22),dp(22),dp(22));c.setBackground(sunkenFrame(cardBg,cardRadius));c.setElevation(dp(18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(16),dp(12),dp(16),dp(12));c.setLayoutParams(lp);return c;}
    TextView pill(String text,int color){TextView v=tv(text,12,color);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setBackground(insetField());v.setPadding(dp(12),dp(8),dp(12),dp(8));return v;}
    void two(LinearLayout p,View a,View b){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(0,-2,1);lp1.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1);lp2.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));r.addView(a,lp1);r.addView(b,lp2);p.addView(r);} 
    void gridAdd(LinearLayout p, View... views){LinearLayout row=null;for(int i=0;i<views.length;i++){if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);p.addView(row);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));row.addView(views[i],lp);} }

    @Override void buildUi(){
        ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg2);root.setPadding(dp(2),dp(14),dp(2),dp(28));sv.addView(root);
        LinearLayout topbar=new LinearLayout(this);topbar.setGravity(Gravity.CENTER_VERTICAL);topbar.setPadding(dp(16),dp(8),dp(16),dp(8));topbar.addView(tv("☰",28,navy),new LinearLayout.LayoutParams(0,-2,1));TextView logo=tv("BLUETTI CONTROL",24,navy);logo.setGravity(Gravity.CENTER);topbar.addView(logo,new LinearLayout.LayoutParams(0,-2,3));TextView menu=tv("⚙",24,muted);menu.setGravity(Gravity.RIGHT);topbar.addView(menu,new LinearLayout.LayoutParams(0,-2,1));root.addView(topbar);

        LinearLayout hero=card();hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);heroName=tv("BLUETTI",24,navy);status=pill("Status: Aguardando",green2);deviceTitle=tv("Dispositivo: --",14,muted);mid.addView(heroName);mid.addView(status);mid.addView(deviceTitle);hero.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.RIGHT);heroSoc=tv("--%",42,green2);heroSoc.setGravity(Gravity.RIGHT);heroTime=tv("--\nTempo restante",12,muted);heroTime.setGravity(Gravity.RIGHT);right.addView(heroSoc);right.addView(heroTime);hero.addView(right,new LinearLayout.LayoutParams(dp(126),-2));root.addView(hero);

        LinearLayout statsCard=card();statsCard.addView(tv("RESUMO",12,muted));gridAdd(statsCard,stat("▰","Bateria","--%",green2),stat("↯","Entrada","-- W",blue2),stat("⚡","Saída","-- W",orange2),stat("⏻","DC","-- W",violet2));root.addView(statsCard);
        battery=statBattery;acIn=statInput;acOut=statOutput;dcOut=statDc;dcIn=tv("Entrada DC: -- W",14,navy);acState=tv("AC --",14,navy);dcState=tv("DC --",14,navy);
        batteryGraph=new GraphView(this,"",100);batteryGraph.setVisibility(View.GONE);consumptionGraph=new GraphView(this,"",1000);consumptionGraph.setVisibility(View.GONE);showBatteryGraph=new CheckBox(this);showConsumptionGraph=new CheckBox(this);

        LinearLayout ctrl=card();ctrl.addView(tv("CONTROLES",12,muted));Button acOn=control("AC\nLIGAR",green2),acOff=control("AC\nDESLIGAR",red2),dcOn=control("DC\nLIGAR",green2),dcOff=control("DC\nDESLIGAR",red2);gridAdd(ctrl,acOn,acOff,dcOn,dcOff);root.addView(ctrl);

        LinearLayout dev=card();dev.addView(tv("DISPOSITIVOS",12,muted));manualName=new EditText(this);manualName.setHint("Nome da Bluetti");manualName.setTextColor(navy);manualName.setHintTextColor(muted);manualName.setTextSize(18);manualName.setBackground(insetField());manualMac=new EditText(this);manualMac.setHint("MAC Bluetooth");manualMac.setTextColor(navy);manualMac.setHintTextColor(muted);manualMac.setTextSize(18);manualMac.setBackground(insetField());dev.addView(manualName);dev.addView(manualMac);Button add=btn("Adicionar"),clear=btn("Limpar lista");two(dev,add,clear);savedBox=new LinearLayout(this);savedBox.setOrientation(LinearLayout.VERTICAL);dev.addView(savedBox);root.addView(dev);

        LinearLayout auto=card();auto.addView(tv("AUTOMAÇÕES",12,muted));autoEnable=new CheckBox(this);autoEnable.setText("Ativar automação local");autoEnable.setTextColor(navy);autoAc=new CheckBox(this);autoAc.setText("Automatizar AC");autoAc.setTextColor(navy);autoDc=new CheckBox(this);autoDc.setText("Automatizar DC");autoDc.setTextColor(navy);auto.addView(autoEnable);two(auto,autoAc,autoDc);lowPct=new EditText(this);lowPct.setHint("Desligar abaixo de %");lowPct.setTextColor(navy);lowPct.setHintTextColor(muted);lowPct.setInputType(2);lowPct.setBackground(insetField());highPct=new EditText(this);highPct.setHint("Ligar acima de %");highPct.setTextColor(navy);highPct.setHintTextColor(muted);highPct.setInputType(2);highPct.setBackground(insetField());two(auto,lowPct,highPct);intervalSec=new EditText(this);intervalSec.setHint("Intervalo automação");intervalSec.setTextColor(navy);intervalSec.setHintTextColor(muted);intervalSec.setInputType(2);intervalSec.setBackground(insetField());auto.addView(intervalSec);Button save=btn("Salvar"),test=btn("Testar agora");two(auto,save,test);automationStatus=tv("Automação: desligada",15,muted);auto.addView(automationStatus);root.addView(auto);

        LinearLayout conn=card();conn.addView(tv("CONEXÃO",12,muted));Button scan=btn("Buscar"),stop=btn("Parar"),connect=btn("Conectar"),disconnect=btn("Desconectar"),refresh=btn("Atualizar"),reconnect=btn("Reconectar");gridAdd(conn,scan,stop,connect,disconnect,refresh,reconnect);root.addView(conn);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);LinearLayout log=card();log.addView(tv("LOG",12,muted));logBox=new LinearLayout(this);logBox.setOrientation(LinearLayout.VERTICAL);log.addView(logBox);root.addView(log);
        scan.setOnClickListener(v->startScan());stop.setOnClickListener(v->stopScan());connect.setOnClickListener(v->connectSelected());disconnect.setOnClickListener(v->disconnect());refresh.setOnClickListener(v->readStatus());reconnect.setOnClickListener(v->{disconnect();handler.postDelayed(this::connectSelected,800);});acOn.setOnClickListener(v->writeRegister(3007,1));acOff.setOnClickListener(v->writeRegister(3007,0));dcOn.setOnClickListener(v->writeRegister(3008,1));dcOff.setOnClickListener(v->writeRegister(3008,0));add.setOnClickListener(v->addManualDevice());clear.setOnClickListener(v->{getSharedPreferences("devices",0).edit().clear().apply();renderSavedDevices();});save.setOnClickListener(v->{saveAutomation();applyAutomationState();});test.setOnClickListener(v->runAutomationCheck(true));setContentView(sv);
    }
    TextView stat(String icon,String label,String value,int color){TextView v=tv(icon+"  "+label+"\n"+value,18,color);v.setGravity(Gravity.CENTER_VERTICAL);v.setBackground(insetField());v.setElevation(dp(5));v.setPadding(dp(18),dp(16),dp(18),dp(16));v.setMinHeight(dp(cardMinHeight));if(label.equals("Bateria"))statBattery=v;else if(label.equals("Entrada"))statInput=v;else if(label.equals("Saída"))statOutput=v;else statDc=v;return v;}
    Button control(String text,int color){Button b=btn(text);b.setTextColor(color);b.setTextSize(17);b.setMinHeight(dp(88));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(softButton());return b;}

    @Override void parseRead(byte[] pkt){try{if(pkt==null||pkt.length<5)return;int bc=pkt[2]&255,words=bc/2;if(words<2)return;int[] w=new int[words];for(int i=0;i<words&&4+i*2<pkt.length;i++)w[i]=((pkt[3+i*2]&255)<<8)|(pkt[4+i*2]&255);runOnUiThread(()->{if(words>=8){int dci=w[0],aci=w[1],aco=w[2],dco=w[3];lastSoc=w[7];lastConsumption=aco+dco;int inputTotal=aci+dci;heroSoc.setText(lastSoc+"%");statBattery.setText("▰  Bateria\n"+lastSoc+"%");statInput.setText("↯  Entrada\n"+inputTotal+" W");statOutput.setText("⚡  Saída\n"+lastConsumption+" W");statDc.setText("⏻  DC\n"+dco+" W");heroTime.setText((lastConsumption>0?formatMinutes((1152*lastSoc/100*60)/Math.max(1,lastConsumption)):"Sem consumo")+"\nTempo restante");setStatus("Conectado",green);runAutomationCheck(false);}else if(words==2){acValue=w[0];dcValue=w[1];}});}catch(Exception e){log("Parser seguro: "+e.getMessage());}}
    String formatMinutes(int min){if(min<=0)return "--";return (min/60)+"h "+(min%60)+"m";}
}
