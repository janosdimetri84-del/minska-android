package se.minska.test;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private LinearLayout content;
    private SharedPreferences prefs;
    private final ArrayList<Habit> habits = new ArrayList<>();
    private FusedLocationProviderClient fused;
    private GeofencingClient geofencing;
    private PendingIntent geofencePendingIntent;

    private static final int BG = Color.rgb(8,19,29);
    private static final int PANEL = Color.rgb(16,30,42);
    private static final int PANEL2 = Color.rgb(21,38,52);
    private static final int TEXT = Color.rgb(247,250,252);
    private static final int MUTED = Color.rgb(169,181,190);
    private static final int GREEN = Color.rgb(105,209,92);
    private static final int YELLOW = Color.rgb(246,195,68);
    private static final int RED = Color.rgb(255,107,107);

    static class Habit {
        String id, name, unit, category;
        double baseline, current, dayLimit, step;
        boolean blocked;
        Habit(String id, String name, String unit, String category, double baseline, double current, double dayLimit, double step, boolean blocked) {
            this.id=id; this.name=name; this.unit=unit; this.category=category;
            this.baseline=baseline; this.current=current; this.dayLimit=dayLimit; this.step=step; this.blocked=blocked;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        content=findViewById(R.id.content);
        prefs=getSharedPreferences("minska_prefs", MODE_PRIVATE);
        fused=LocationServices.getFusedLocationProviderClient(this);
        geofencing=LocationServices.getGeofencingClient(this);
        seedAndLoad();
        createNotificationChannel();

        findViewById(R.id.navToday).setOnClickListener(v->showToday());
        findViewById(R.id.navPlan).setOnClickListener(v->showPlan());
        findViewById(R.id.navAdd).setOnClickListener(v->showAdd());
        findViewById(R.id.navProgress).setOnClickListener(v->showProgress());
        findViewById(R.id.navSettings).setOnClickListener(v->showSettings());
        showToday();
    }

    private void seedAndLoad() {
        String raw=prefs.getString("habits_json", "");
        if(raw.isEmpty()){
            habits.add(new Habit("monster","Monster","st","Koffein",2,0,1,1,false));
            habits.add(new Habit("lask","Läsk","ml","Socker/mat",500,0,500,250,false));
            habits.add(new Habit("choklad","Choklad","g","Socker/mat",150,0,100,25,false));
            saveHabits();
        } else loadHabits(raw);
    }

    private void loadHabits(String raw){
        habits.clear();
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                habits.add(new Habit(
                    o.getString("id"),o.getString("name"),o.getString("unit"),o.getString("category"),
                    o.getDouble("baseline"),o.getDouble("current"),o.getDouble("dayLimit"),o.getDouble("step"),
                    o.optBoolean("blocked",false)
                ));
            }
        }catch(Exception e){ prefs.edit().remove("habits_json").apply(); seedAndLoad(); }
    }

    private void saveHabits(){
        try{
            JSONArray a=new JSONArray();
            boolean anyReached=false;
            for(Habit h:habits){
                JSONObject o=new JSONObject();
                o.put("id",h.id); o.put("name",h.name); o.put("unit",h.unit); o.put("category",h.category);
                o.put("baseline",h.baseline); o.put("current",h.current); o.put("dayLimit",h.dayLimit); o.put("step",h.step); o.put("blocked",h.blocked);
                a.put(o);
                if(!h.blocked && h.current>=h.dayLimit) anyReached=true;
            }
            prefs.edit().putString("habits_json",a.toString()).putBoolean("any_reached",anyReached).apply();
            saveTodayHistory();
        }catch(Exception ignored){}
    }

    private void saveTodayHistory(){
        try{
            JSONObject day=new JSONObject();
            for(Habit h:habits){
                JSONObject v=new JSONObject();
                v.put("current",h.current); v.put("limit",h.dayLimit); v.put("blocked",h.blocked);
                day.put(h.id,v);
            }
            prefs.edit().putString("history_"+dateKey(0),day.toString()).apply();
        }catch(Exception ignored){}
    }

    private String dateKey(int daysBack){
        Calendar c=Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR,-daysBack);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime());
    }

    private void clearContent(){ content.removeAllViews(); }

    private TextView title(String s){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(TEXT); t.setTextSize(30); t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,0,0,8); return t;
    }

    private TextView text(String s,int color,int sp){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(sp); t.setPadding(0,4,0,4); return t;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable g=new GradientDrawable(); g.setColor(PANEL); g.setCornerRadius(dp(18)); c.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(10),0,0); c.setLayoutParams(lp);
        return c;
    }

    private Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54)); lp.setMargins(0,dp(8),0,0); b.setLayoutParams(lp); return b;
    }

    private void showToday(){
        clearContent();
        content.addView(title("Idag"));
        content.addView(text(new SimpleDateFormat("EEEE d MMMM",new Locale("sv","SE")).format(new Date()),MUTED,15));

        int ok=0; for(Habit h:habits) if(h.blocked || h.current<=h.dayLimit) ok++;
        content.addView(text(ok==habits.size() ? "🔥 Dagens mål håller" : "⚠ "+ok+" av "+habits.size()+" mål inom gränsen", ok==habits.size()?YELLOW:RED,15));

        for(Habit h:habits) addHabitCard(h);

        Button crave=btn("♡ Jag är sugen – hjälp mig nu");
        crave.setOnClickListener(v->showCravingHelp());
        content.addView(crave);
    }

    private void addHabitCard(Habit h){
        LinearLayout c=card();
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView n=text(iconFor(h.category)+"  "+h.name,TEXT,19); n.setTypeface(null,Typeface.BOLD);
        row.addView(n,new LinearLayout.LayoutParams(0,-2,1));

        Button minus=new Button(this); minus.setText("−"); minus.setTextSize(20);
        Button plus=new Button(this); plus.setText("+"); plus.setTextSize(20);
        row.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(48)));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(54),dp(48)); pp.setMargins(dp(6),0,0,0); row.addView(plus,pp);
        c.addView(row);

        TextView val=text(fmt(h.current)+" / "+fmt(h.dayLimit)+" "+h.unit,TEXT,27); val.setTypeface(null,Typeface.BOLD); c.addView(val);
        c.addView(text(categoryText(h), h.blocked?RED:MUTED,13));

        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        p.setMax(1000); p.setProgress((int)Math.min(1000, Math.round(1000*h.current/Math.max(1,h.dayLimit))));
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(8)); pl.setMargins(0,dp(8),0,0); p.setLayoutParams(pl); c.addView(p);

        minus.setOnClickListener(v->{ h.current=Math.max(0,h.current-h.step); saveHabits(); showToday(); });
        plus.setOnClickListener(v->{ h.current+=h.step; saveHabits(); showToday(); });
        c.setOnLongClickListener(v->{ editHabitDialog(h); return true; });
        content.addView(c);
    }

    private String iconFor(String cat){
        if(cat.equals("Koffein")) return "⚡";
        if(cat.equals("Nikotin")) return "◉";
        if(cat.equals("Socker/mat")) return "🍫";
        if(cat.equals("Risk – vårdkontakt")) return "⚠";
        return "●";
    }

    private String categoryText(Habit h){
        if(h.blocked) return "RÖD • appen gör ingen egen nedtrappningsplan";
        if(h.category.equals("Koffein")||h.category.equals("Nikotin")) return "GUL • gradvis nedtrappning • dagens max "+fmt(h.dayLimit)+" "+h.unit;
        return "GRÖN • dagens max "+fmt(h.dayLimit)+" "+h.unit;
    }

    private void showPlan(){
        clearContent(); content.addView(title("4-veckorsplan"));
        content.addView(text("Regelbaserad testplan – AI är inte ansluten.",MUTED,14));
        for(Habit h:habits){
            LinearLayout c=card(); c.addView(text(h.name,TEXT,20));
            if(h.blocked){
                c.addView(text("⚠ Ingen automatisk plan. Alkohol i hög mängd och vissa läkemedel kan kräva professionell bedömning.",RED,14));
            }else{
                double[] f = h.category.equals("Koffein")||h.category.equals("Nikotin")
                        ? new double[]{0.75,0.60,0.45,0.30}
                        : new double[]{0.75,0.55,0.40,0.25};
                for(int i=0;i<4;i++) c.addView(text("Vecka "+(i+1)+": max "+fmt(roundToStep(h.baseline*f[i],h.step))+" "+h.unit+" per dag", i==0?YELLOW:TEXT,15));
            }
            content.addView(c);
        }
    }

    private void showAdd(){
        clearContent(); content.addView(title("Lägg till ny vana"));
        content.addView(text("Här används fasta säkerhetsregler. AI kopplas på senare.",MUTED,14));

        EditText name=input("Vad vill du minska? t.ex. kaffe");
        EditText amount=input("Nuvarande mängd per dag");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText unit=input("Enhet, t.ex. st, ml eller g");

        Spinner cat=new Spinner(this);
        String[] cats={"Socker/mat","Koffein","Nikotin","Övrigt","Risk – vårdkontakt"};
        cat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats));
        content.addView(name); content.addView(amount); content.addView(unit);
        content.addView(text("Kategori",TEXT,15)); content.addView(cat);

        Button add=btn("Skapa mål och plan");
        add.setOnClickListener(v->{
            String n=name.getText().toString().trim(), u=unit.getText().toString().trim(), a=amount.getText().toString().trim();
            if(n.isEmpty()||u.isEmpty()||a.isEmpty()){ toast("Fyll i namn, mängd och enhet"); return; }
            try{
                double base=Double.parseDouble(a.replace(",","."));
                String category=(String)cat.getSelectedItem();
                boolean blocked=category.equals("Risk – vårdkontakt");
                double limit=blocked?base:roundToStep(base*0.75, sensibleStep(base));
                double step=sensibleStep(base);
                habits.add(new Habit(UUID.randomUUID().toString(),n,u,category,base,0,limit,step,blocked));
                saveHabits(); showToday();
            }catch(Exception e){ toast("Kontrollera mängden"); }
        });
        content.addView(add);
    }

    private EditText input(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56)); lp.setMargins(0,dp(8),0,0); e.setLayoutParams(lp); return e;
    }

    private double sensibleStep(double base){
        if(base<=5) return 1;
        if(base<=50) return 5;
        if(base<=250) return 25;
        if(base<=1000) return 100;
        return Math.max(1,Math.round(base/10.0));
    }

    private double roundToStep(double x,double step){ return Math.max(step,Math.round(x/step)*step); }

    private void editHabitDialog(Habit h){
        String[] choices={"Ändra dagens gräns","Återställ dagens mängd","Ta bort målet"};
        new AlertDialog.Builder(this).setTitle(h.name).setItems(choices,(d,which)->{
            if(which==0){
                EditText e=input("Ny dagsgräns"); e.setText(fmt(h.dayLimit));
                new AlertDialog.Builder(this).setTitle("Dagens gräns").setView(e).setPositiveButton("Spara",(x,y)->{
                    try{h.dayLimit=Double.parseDouble(e.getText().toString().replace(",","."));saveHabits();showToday();}catch(Exception ex){toast("Ogiltigt värde");}
                }).setNegativeButton("Avbryt",null).show();
            }else if(which==1){ h.current=0; saveHabits(); showToday();
            }else{
                new AlertDialog.Builder(this).setTitle("Ta bort "+h.name+"?").setPositiveButton("Ta bort",(x,y)->{habits.remove(h);saveHabits();showToday();}).setNegativeButton("Avbryt",null).show();
            }
        }).show();
    }

    private void showProgress(){
        clearContent(); content.addView(title("Framsteg"));
        int daysWithData=0, successful=0;
        for(int d=0;d<7;d++){
            String raw=prefs.getString("history_"+dateKey(d),"");
            if(raw.isEmpty()) continue;
            daysWithData++;
            try{
                JSONObject o=new JSONObject(raw); boolean ok=true;
                Iterator<String> keys=o.keys();
                while(keys.hasNext()){
                    JSONObject v=o.getJSONObject(keys.next());
                    if(!v.optBoolean("blocked",false) && v.getDouble("current")>v.getDouble("limit")) ok=false;
                }
                if(ok) successful++;
            }catch(Exception ignored){}
        }
        LinearLayout summary=card();
        summary.addView(text("Den här veckan",TEXT,20));
        summary.addView(text(successful+" av "+Math.max(1,daysWithData)+" registrerade dagar inom mål",GREEN,22));
        summary.addView(text("Historiken sparas lokalt på telefonen.",MUTED,13));
        content.addView(summary);

        for(Habit h:habits){
            LinearLayout c=card(); c.addView(text(h.name,TEXT,19));
            double reduction = h.baseline<=0 ? 0 : Math.max(0,100-(100*h.dayLimit/h.baseline));
            c.addView(text("Startnivå: "+fmt(h.baseline)+" "+h.unit,MUTED,14));
            c.addView(text("Nuvarande dagsgräns: "+fmt(h.dayLimit)+" "+h.unit,TEXT,15));
            c.addView(text("Planerad minskning: "+Math.round(reduction)+" %",GREEN,15));
            content.addView(c);
        }
    }

    private void showCravingHelp(){
        clearContent(); content.addView(title("Jag är sugen"));
        content.addView(text("Snabb hjälp utan AI – välj situation.",MUTED,14));
        String[] options={"Jag är i en butik","Jag är trött","Jag är stressad","Jag är med kompisar"};
        for(String s:options){
            Button b=btn(s); b.setOnClickListener(v->showRuleAdvice(s)); content.addView(b);
        }
        Button back=btn("Tillbaka till Idag"); back.setOnClickListener(v->showToday()); content.addView(back);
    }

    private void showRuleAdvice(String situation){
        clearContent(); content.addView(title("Förslag för dig"));
        boolean reached=prefs.getBoolean("any_reached",false);
        content.addView(text(reached ? "Du har redan nått minst ett av dagens mål." : "Du har fortfarande utrymme inom dagens mål.", reached?YELLOW:GREEN,17));
        LinearLayout c=card();
        if(situation.contains("butik")){
            c.addView(text("• Vänta 10 minuter innan du bestämmer dig.",TEXT,15));
            c.addView(text("• Ta vatten eller något som inte finns på din minska-lista.",TEXT,15));
            c.addView(text("• Kontrollera Idag-skärmen innan du köper.",TEXT,15));
        }else if(situation.contains("trött")){
            c.addView(text("• Drick vatten och ät något vanligt om du inte har ätit.",TEXT,15));
            c.addView(text("• Om det gäller koffein: följ planen i stället för att ta extra impulsivt.",TEXT,15));
        }else{
            c.addView(text("• Vänta en kort stund och bestäm i förväg vad du tänker ta.",TEXT,15));
            c.addView(text("• Ett avsteg förstör inte planen – registrera korrekt och fortsätt.",TEXT,15));
        }
        content.addView(c);
        Button back=btn("Tillbaka"); back.setOnClickListener(v->showToday()); content.addView(back);
    }

    private void showSettings(){
        clearContent(); content.addView(title("Inställningar"));
        content.addView(text("GPS-påminnelser",TEXT,20));
        content.addView(text("Spara den plats du står på som en butik. När Android registrerar att du kommer tillbaka kan MINSKA visa en notis om ett dagsmål redan är nått.",MUTED,14));

        Button perms=btn("1. Ge plats- och notisbehörighet");
        perms.setOnClickListener(v->requestPermissionsForGps());
        content.addView(perms);

        Button save=btn("2. Spara min nuvarande plats som butik");
        save.setOnClickListener(v->saveCurrentPlaceAsGeofence());
        content.addView(save);

        if(Build.VERSION.SDK_INT>=29){
            Button bg=btn("3. Tillåt plats i bakgrunden i Android-inställningar");
            bg.setOnClickListener(v->{
                Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName()));
                startActivity(i);
            });
            content.addView(bg);
        }

        Button test=btn("Testa notis nu");
        test.setOnClickListener(v->sendNotification("Testnotis","MINSKA-notiser fungerar."));
        content.addView(test);

        Button reset=btn("Nollställ dagens registrering");
        reset.setOnClickListener(v->{
            for(Habit h:habits) h.current=0;
            saveHabits(); toast("Dagens värden nollställda"); showToday();
        });
        content.addView(reset);

        content.addView(text("AI: inte ansluten i testversion 0.3.",YELLOW,14));
    }

    private void requestPermissionsForGps(){
        ArrayList<String> req=new ArrayList<>();
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33 && ActivityCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) req.add(Manifest.permission.POST_NOTIFICATIONS);
        if(req.isEmpty()) toast("Grundbehörigheter är redan godkända");
        else ActivityCompat.requestPermissions(this,req.toArray(new String[0]),200);
    }

    private PendingIntent geofencePI(){
        if(geofencePendingIntent!=null) return geofencePendingIntent;
        Intent i=new Intent(this,GeofenceReceiver.class);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31) flags|=PendingIntent.FLAG_MUTABLE;
        geofencePendingIntent=PendingIntent.getBroadcast(this,0,i,flags);
        return geofencePendingIntent;
    }

    private void saveCurrentPlaceAsGeofence(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            toast("Ge platsbehörighet först"); requestPermissionsForGps(); return;
        }
        fused.getLastLocation().addOnSuccessListener(loc->{
            if(loc==null){ toast("Kunde inte läsa plats. Slå på GPS och försök igen."); return; }
            addGeofence(loc);
        }).addOnFailureListener(e->toast("Platsfel: "+e.getMessage()));
    }

    private void addGeofence(Location loc){
        Geofence fence=new Geofence.Builder()
                .setRequestId("saved_store")
                .setCircularRegion(loc.getLatitude(),loc.getLongitude(),150)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER|Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(20000)
                .build();

        GeofencingRequest req=new GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofence(fence).build();

        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        geofencing.removeGeofences(geofencePI()).addOnCompleteListener(x->
            geofencing.addGeofences(req,geofencePI())
                .addOnSuccessListener(v->{prefs.edit().putBoolean("geofence_saved",true).apply();toast("Platsen sparad som butik (150 m).");})
                .addOnFailureListener(e->toast("Kunde inte aktivera platsregel. Kontrollera bakgrundsplats."))
        );
    }

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("minska","MINSKA påminnelser",NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private void sendNotification(String title,String body){
        if(Build.VERSION.SDK_INT>=33 && ActivityCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},201); return;
        }
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,"minska")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
                .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(this).notify((int)(System.currentTimeMillis()%100000),b.build());
    }

    private String fmt(double d){ return Math.abs(d-Math.rint(d))<0.0001 ? String.valueOf((long)d) : String.format(Locale.US,"%.1f",d); }
    private int dp(int x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
