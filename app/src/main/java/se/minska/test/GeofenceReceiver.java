package se.minska.test;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        GeofencingEvent event=GeofencingEvent.fromIntent(intent);
        if(event==null || event.hasError()) return;
        int transition=event.getGeofenceTransition();
        if(transition!=Geofence.GEOFENCE_TRANSITION_ENTER && transition!=Geofence.GEOFENCE_TRANSITION_DWELL) return;

        boolean reached=context.getSharedPreferences("minska_prefs",Context.MODE_PRIVATE).getBoolean("any_reached",false);
        if(!reached) return;

        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("minska","MINSKA påminnelser",NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(c);
        }

        if(Build.VERSION.SDK_INT>=33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;

        NotificationCompat.Builder b=new NotificationCompat.Builder(context,"minska")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MINSKA")
                .setContentText("Du är nära din sparade butik och har redan nått minst ett av dagens mål.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManagerCompat.from(context).notify(4001,b.build());
    }
}
