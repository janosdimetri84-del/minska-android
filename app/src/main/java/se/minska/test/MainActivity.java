package se.minska.test;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MainActivity extends AppCompatActivity {

    int monster = 0;
    int soda = 0;
    int chocolate = 0;

    Button monsterButton;
    Button sodaButton;
    Button chocolateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        monsterButton = findViewById(R.id.monsterButton);
        sodaButton = findViewById(R.id.sodaButton);
        chocolateButton = findViewById(R.id.chocolateButton);

        Button addButton = findViewById(R.id.addButton);
        Button notificationButton = findViewById(R.id.notificationButton);
        Button gpsButton = findViewById(R.id.gpsButton);

        monsterButton.setOnClickListener(v -> {
            monster++;
            monsterButton.setText("Monster   " + monster + " / 1   +1");
        });

        sodaButton.setOnClickListener(v -> {
            soda += 250;
            sodaButton.setText("Läsk   " + soda + " / 500 ml   +250 ml");
        });

        chocolateButton.setOnClickListener(v -> {
            chocolate += 25;
            chocolateButton.setText("Choklad   " + chocolate + " / 100 g   +25 g");
        });

        addButton.setOnClickListener(v ->
                Toast.makeText(this,
                        "Egna mål byggs ut i nästa version",
                        Toast.LENGTH_SHORT).show());

        notificationButton.setOnClickListener(v -> sendTestNotification());

        gpsButton.setOnClickListener(v -> requestLocationPermission());

        createNotificationChannel();
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                100
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "minska",
                    "MINSKA påminnelser",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void sendTestNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101
            );

            return;
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "minska")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("MINSKA")
                        .setContentText(
                                "Du har redan nått dagens mål. Försök hoppa över nästa."
                        )
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(this)
                .notify(1, builder.build());
    }
}
