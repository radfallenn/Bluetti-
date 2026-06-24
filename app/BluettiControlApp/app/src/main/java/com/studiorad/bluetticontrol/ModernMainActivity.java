package com.studiorad.bluetticontrol;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class ModernMainActivity extends MainActivity {
    int bg2=Color.rgb(4,7,13), cardBg=Color.rgb(13,18,28), cardBg2=Color.rgb(18,25,38), navy=Color.rgb(235,245,255), muted=Color.rgb(132,148,168);
    int green2=Color.rgb(0,255,153), blue2=Color.rgb(0,190,255), red2=Color.rgb(255,49,95), orange2=Color.rgb(255,190,62), violet2=Color.rgb(190,90,255);
    TextView heroName, heroSoc, heroTime, statBattery, statInput, statOutput, statDc;
    TextView udmAutonomy, udmCoach, udmAlerts, udmHistory, udmWeb;
    int gridColumns=2, cardMinHeight=112, cardSpacing=6, fontBoost=0, cardRadius=28;

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(bg2); getWindow().setNavigationBarColor(bg2); if(android.os.Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(0); loadLayoutPrefs(); try{ if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(new android.content.Intent(this,BluettiPersistentService.class)); else startService(new android.content.Intent(this,BluettiPersistentService.class)); }catch(Exception e){} }
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    void loadLayoutPrefs(){android.content.SharedPreferences p=getSharedPreferences("layout_udm",0);gridColumns=p.getInt("cols",2);cardMinHeight=p.getInt("height",112);cardSpacing=p.getInt("spacing",6);fontBoost=p.getInt("font",0);cardRadius=p.getInt("radius",28);} 
    void saveLayoutPrefs(){getSharedPreferences("layout_udm",0).edit().putInt("cols",gridColumns).putInt("height",cardMinHeight).putInt("spacing",cardSpacing).putInt("font",fontBoost).putInt("radius",cardRadius).apply();}
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable neonBg(int fill,int stroke,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,Color.rgb(20,29,44)});g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    android.graphics.drawable.Drawable rgbFrame(int fill,int radius){
        GradientDrawable outer=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blue2,green2,orange2,red2,violet2,blue2});
        outer.setCornerRadius(dp(radius));
        GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,Color.rgb(18,24,36),Color.rgb(8,11,18)});
        inner.setCornerRadius(dp(Math.max(1,radius-1)));
        LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,inner});
        layer.setLayerInset(1,dp(2),dp(2),dp(2),dp(2));
        return layer;
    }
    android.graphics.drawable.Drawable rgbButton(){
        GradientDrawable outer=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blue2,green2,orange2,red2});
        outer.setCornerRadius(dp(20));
        GradientDrawable middle=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,38,48),Color.rgb(8,11,18)});
        middle.setCornerRadius(dp(19));
        GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(35,39,50),Color.rgb(7,9,14)});
        inner.setCornerRadius(dp(17));
        LayerDrawable layer=new LayerDrawable(new android.graphics.drawable.Drawable[]{outer,middle,inner});
        layer.setLayerInset(1,dp(2),dp(2),dp(2),dp(2));
        layer.setLayerInset(2,dp(4),dp(4),dp(4),dp(4));
        return layer;
    }

    @Override TextView tv(String t,int sp,int c){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp+fontBoost);v.setTextColor(c==Color.DKGRAY?muted:c);v.setShadowLayer(sp>=16?10:4,0,0,c);v.setPadding(dp(14),dp(6),dp(14),dp(6));if(sp>=18)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setSingleLine(false);return v;}
    @Override Button btn(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14+fontBoost);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setShadowLayer(10,0,0,Color.WHITE);b.setMinHeight(dp(62));b.setElevation(dp(14));b.setBackground(rgbButton());b.setPadding(dp(10),dp(10),dp(10),dp(10));return b;}
    @Override TextView metric(String title){TextView v=tv(title+": --",16,navy);v.setBackground(rgbFrame(cardBg2,cardRadius));v.setElevation(dp(12));v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setMinHeight(dp(cardMinHeight));return v;}
    @Override LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(rgbFrame(cardBg,cardRadius));c.setElevation(dp(12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(14),dp(10),dp(14),dp(10));c.setLayoutParams(lp);return c;}
    TextView pill(String text,int color){TextView v=tv(text,12,color);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setBackground(neonBg(Color.rgb(8,18,20),color,999));v.setPadding(dp(12),dp(6),dp(12),dp(6));return v;}
    void two(LinearLayout p,View a,View b){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(0,-2,1);lp1.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1);lp2.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));r.addView(a,lp1);r.addView(b,lp2);p.addView(r);} 
    void gridAdd(LinearLayout p, View... views){LinearLayout row=null;for(int i=0;i<views.length;i++){if(i%gridColumns==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);p.addView(row);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(cardSpacing),dp(cardSpacing),dp(cardSpacing),dp(cardSpacing));row.addView(views[i],lp);} }

    @Override void buildUi(){
        loadLayoutPrefs();
        ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg2);root.setPadding(dp(2),dp(10),dp(2),dp(24));sv.addView(root);
        LinearLayout topbar=new LinearLayout(this);topbar.setGravity(Gravity.CENTER_VERTICAL);topbar.setPadding(dp(10),dp(4),dp(10),dp(4));topbar.addView(tv("☰",28,blue2),new LinearLayout.LayoutParams(0,-2,1));TextView logo=tv("BLUETTI UDM CONTROL",24,navy);logo.setGravity(Gravity.CENTER);topbar.addView(logo,new LinearLayout.LayoutParams(0,-2,3));TextView menu=tv("⚙",24,violet2);menu.setGravity(Gravity.RIGHT);topbar.addView(menu,new LinearLayout.LayoutParams(0,-2,1));root.addView(topbar);

        LinearLayout hero=card();hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);heroName=tv("BLUETTI",24,navy);status=pill("● Aguardando",blue2);deviceTitle=tv("Dispositivo: --",14,muted);mid.addView(heroName);mid.addView(status);mid.addView(deviceTitle);hero.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.RIGHT);heroSoc=tv("--%",46,green2);heroSoc.setGravity(Gravity.RIGHT);heroTime=tv("--\nTempo restante",12,muted);heroTime.setGravity(Gravity.RIGHT);right.addView(heroSoc);right.addView(heroTime);hero.addView(right,new LinearLayout.LayoutParams(dp(118),-2));root.addView(hero);

        LinearLayout statsCard=card();statsCard.addView(tv("RESUMO EM GRID",12,muted));gridAdd(statsCard,stat("▰","Bateria","--%",green2),stat("↯","Entrada","-- W",blue2),stat("⚡","Saída","-- W",orange2),stat("⏻","DC","-- W",violet2));root.addView(statsCard);
        battery=statBattery;acIn=statInput;acOut=statOutput;dcOut=statDc;dcIn=tv("Entrada DC: -- W",14,navy);acState=tv("AC --",14,navy);dcState=tv("DC --",14,navy);
        batteryGraph=new GraphView(this,"",100);batteryGraph.setVisibility(View.GONE);consumptionGraph=new GraphView(this,"",1000);consumptionGraph.setVisibility(View.GONE);showBatteryGraph=new CheckBox(this);showConsumptionGraph=new CheckBox(this);

        LinearLayout udm=card();udm.addView(tv("UDM INTELIGENTE",12,muted));udmAutonomy=udmBox("⏱ Autonomia","Aguardando dados",green2);udmCoach=udmBox("🧠 Coach","Sem histórico suficiente",blue2);udmAlerts=udmBox("🚨 Alarmes","Monitorando bateria",red2);udmHistory=udmBox("💾 Histórico","JSON local pronto",orange2);udmWeb=udmBox("🌐 Bridge","Raspberry/API opcional",violet2);gridAdd(udm,udmAutonomy,udmCoach,udmAlerts,udmHistory,udmWeb);root.addView(udm);

        LinearLayout layout=card();layout.addView(tv("GRADE E LAYOUT",12,muted));layout.addView(tv("Ajuste posição visual, tamanho dos cards, espaçamento, fonte e bordas.",13,muted));addSlider(layout,"Colunas do grid",1,3,gridColumns,v->{gridColumns=v;saveLayoutPrefs();buildUi();});addSlider(layout,"Altura dos cards",88,180,cardMinHeight,v->{cardMinHeight=v;saveLayoutPrefs();buildUi();});addSlider(layout,"Espaçamento",2,18,cardSpacing,v->{cardSpacing=v;saveLayoutPrefs();buildUi();});addSlider(layout,"Fonte",0,6,fontBoost,v->{fontBoost=v;saveLayoutPrefs();buildUi();});addSlider(layout,"Raio das bordas",14,42,cardRadius,v->{cardRadius=v;saveLayoutPrefs();buildUi();});Button reset=btn("Resetar layout");reset.setOnClickListener(v->{gridColumns=2;cardMinHeight=112;cardSpacing=6;fontBoost=0;cardRadius=28;saveLayoutPrefs();buildUi();});layout.addView(reset);root.addView(layout);

        LinearLayout ctrl=card();ctrl.addView(tv("CONTROLES",12,muted));Button acOn=control("AC\nLIGAR",green2),acOff=control("AC\nDESLIGAR",red2),dcOn=control("DC\nLIGAR",green2),dcOff=control("DC\nDESLIGAR",red2);gridAdd(ctrl,acOn,acOff,dcOn,dcOff);root.addView(ctrl);

        LinearLayout dev=card();dev.addView(tv("DISPOSITIVOS",12,muted));manualName=new EditText(this);manualName.setHint("Nome da Bluetti");manualName.setTextColor(navy);manualName.setHintTextColor(muted);manualName.setBackground(rgbFrame(Color.rgb(8,12,20),18));manualMac=new EditText(this);manualMac.setHint("MAC Bluetooth");manualMac.setTextColor(navy);manualMac.setHintTextColor(muted);manualMac.setBackground(rgbFrame(Color.rgb(8,12,20),18));dev.addView(manualName);dev.addView(manualMac);Button add=btn("Adicionar"),clear=btn("Limpar lista");two(dev,add,clear);savedBox=new LinearLayout(this);savedBox.setOrientation(LinearLayout.VERTICAL);dev.addView(savedBox);root.addView(dev);

        LinearLayout auto=card();auto.addView(tv("AUTOMAÇÕES",12,muted));autoEnable=new CheckBox(this);autoEnable.setText("Ativar automação local");autoEnable.setTextColor(navy);autoAc=new CheckBox(this);autoAc.setText("Automatizar AC");autoAc.setTextColor(navy);autoDc=new CheckBox(this);autoDc.setText("Automatizar DC");autoDc.setTextColor(navy);auto.addView(autoEnable);two(auto,autoAc,autoDc);lowPct=new EditText(this);lowPct.setHint("Desligar abaixo de %");lowPct.setTextColor(navy);lowPct.setHintTextColor(muted);lowPct.setInputType(2);lowPct.setBackground(rgbFrame(Color.rgb(8,12,20),18));highPct=new EditText(this);highPct.setHint("Ligar acima de %");highPct.setTextColor(navy);highPct.setHintTextColor(muted);highPct.setInputType(2);highPct.setBackground(rgbFrame(Color.rgb(8,12,20),18));two(auto,lowPct,highPct);intervalSec=new EditText(this);intervalSec.setHint("Intervalo automação");intervalSec.setTextColor(navy);intervalSec.setHintTextColor(muted);intervalSec.setInputType(2);intervalSec.setBackground(rgbFrame(Color.rgb(8,12,20),18));auto.addView(intervalSec);Button save=btn("Salvar"),test=btn("Testar agora");two(auto,save,test);automationStatus=tv("Automação: desligada",15,muted);auto.addView(automationStatus);root.addView(auto);

        LinearLayout conn=card();conn.addView(tv("CONEXÃO",12,muted));Button scan=btn("Buscar"),stop=btn("Parar"),connect=btn("Conectar"),disconnect=btn("Desconectar"),refresh=btn("Atualizar"),reconnect=btn("Reconectar");gridAdd(conn,scan,stop,connect,disconnect,refresh,reconnect);root.addView(conn);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);LinearLayout log=card();log.addView(tv("LOG",12,muted));logBox=new LinearLayout(this);logBox.setOrientation(LinearLayout.VERTICAL);log.addView(logBox);root.addView(log);
        scan.setOnClickListener(v->startScan());stop.setOnClickListener(v->stopScan());connect.setOnClickListener(v->connectSelected());disconnect.setOnClickListener(v->disconnect());refresh.setOnClickListener(v->readStatus());reconnect.setOnClickListener(v->{disconnect();handler.postDelayed(this::connectSelected,800);});acOn.setOnClickListener(v->writeRegister(3007,1));acOff.setOnClickListener(v->writeRegister(3007,0));dcOn.setOnClickListener(v->writeRegister(3008,1));dcOff.setOnClickListener(v->writeRegister(3008,0));add.setOnClickListener(v->addManualDevice());clear.setOnClickListener(v->{getSharedPreferences("devices",0).edit().clear().apply();renderSavedDevices();});save.setOnClickListener(v->{saveAutomation();applyAutomationState();});test.setOnClickListener(v->runAutomationCheck(true));setContentView(sv);
    }
    TextView stat(String icon,String label,String value,int color){TextView v=tv(icon+"  "+label+"\n"+value,18,color);v.setGravity(Gravity.CENTER_VERTICAL);v.setBackground(rgbFrame(cardBg2,cardRadius));v.setElevation(dp(12));v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setMinHeight(dp(cardMinHeight));if(label.equals("Bateria"))statBattery=v;else if(label.equals("Entrada"))statInput=v;else if(label.equals("Saída"))statOutput=v;else statDc=v;return v;}
    TextView udmBox(String title,String value,int color){TextView v=tv(title+"\n"+value,15,color);v.setGravity(Gravity.CENTER_VERTICAL);v.setBackground(rgbFrame(cardBg2,cardRadius));v.setElevation(dp(12));v.setMinHeight(dp(cardMinHeight));v.setPadding(dp(16),dp(14),dp(16),dp(14));return v;}
    Button control(String text,int color){Button b=btn(text);b.setTextColor(color);b.setTextSize(17+fontBoost);b.setMinHeight(dp(82));b.setShadowLayer(12,0,0,color);b.setBackground(rgbButton());return b;}
    interface SliderCallback{void onChange(int value);} 
    void addSlider(LinearLayout parent,String label,int min,int max,int value,SliderCallback cb){TextView l=tv(label+": "+value,13,muted);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(value-min);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){l.setText(label+": "+(min+p));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){cb.onChange(min+b.getProgress());}});parent.addView(l);parent.addView(s);} 

    void updateUdm(int soc,int inputW,int outputW,int dcW){if(udmAutonomy==null)return;int capacityWh=1152;int usableWh=Math.max(0,capacityWh*soc/100);String auto=outputW>0?formatMinutes((usableWh*60)/Math.max(1,outputW)):"Sem consumo";String charge=inputW>0?formatMinutes(((capacityWh-usableWh)*60)/Math.max(1,inputW)):"Sem carga";udmAutonomy.setText("⏱ Autonomia\nUso: "+auto+" • Carga: "+charge);String coach=outputW>600?"Consumo alto: reduza cargas AC":outputW>250?"Consumo médio estável":"Consumo econômico";udmCoach.setText("🧠 Coach\n"+coach);String alert=soc<=20?"Bateria baixa: "+soc+"%":inputW==0&&outputW>0?"Somente descarga":"Normal";udmAlerts.setText("🚨 Alarmes\n"+alert);udmHistory.setText("💾 Histórico\n{soc:"+soc+", in:"+inputW+", out:"+outputW+"}");udmWeb.setText("🌐 Bridge\nAPI pronta para Raspberry");heroTime.setText(auto+"\nTempo restante");}
    String formatMinutes(int min){if(min<=0)return "--";return (min/60)+"h "+(min%60)+"m";}

    @Override void parseRead(byte[] pkt){try{if(pkt==null||pkt.length<5)return;int bc=pkt[2]&255,words=bc/2;if(words<2)return;int[] w=new int[words];for(int i=0;i<words&&4+i*2<pkt.length;i++)w[i]=((pkt[3+i*2]&255)<<8)|(pkt[4+i*2]&255);runOnUiThread(()->{if(words>=8){int dci=w[0],aci=w[1],aco=w[2],dco=w[3];lastSoc=w[7];lastConsumption=aco+dco;int inputTotal=aci+dci;heroSoc.setText(lastSoc+"%");statBattery.setText("▰  Bateria\n"+lastSoc+"%");statInput.setText("↯  Entrada\n"+inputTotal+" W");statOutput.setText("⚡  Saída\n"+lastConsumption+" W");statDc.setText("⏻  DC\n"+dco+" W");updateUdm(lastSoc,inputTotal,lastConsumption,dco);setStatus("Conectado",green);runAutomationCheck(false);}else if(words==2){acValue=w[0];dcValue=w[1];}});}catch(Exception e){log("Parser seguro: "+e.getMessage());}}
}
