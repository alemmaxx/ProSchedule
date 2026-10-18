package com.proschedule.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import java.lang.reflect.Field;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(AlarmPlayerPlugin.class);
    registerPlugin(CalendarSyncPlugin.class);
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_ALARM);
    handleStop(getIntent());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        createAlarmChannels();
      } catch (Exception e) {
        // jika gagal, JS akan cipta channel biasa sebagai fallback
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleStop(intent);
  }

  // Tekan notifikasi alarm MP3 -> hentikan bunyi
  private void handleStop(Intent i) {
    if (i != null && i.getBooleanExtra("ps_stop", false)) {
      try { stopService(new Intent(this, AlarmService.class)); } catch (Exception e) { }
      i.removeExtra("ps_stop");
    }
  }

  // Cipta channel notifikasi yang guna AudioAttributes USAGE_ALARM
  // supaya bunyi mengikut volume ALARM (bukan volume notifikasi).
  private void createAlarmChannels() throws Exception {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    AudioAttributes aa = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build();

    NotificationChannel def = new NotificationChannel("ps3_default", "ProSchedule", NotificationManager.IMPORTANCE_HIGH);
    def.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), aa);
    def.enableVibration(true);
    nm.createNotificationChannel(def);

    Class<?> raw = Class.forName(getPackageName() + ".R$raw");
    for (Field f : raw.getFields()) {
      String name = f.getName();
      if (!(name.startsWith("alarm_") || name.startsWith("c_"))) continue;
      int resId = f.getInt(null);
      Uri uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + resId);
      NotificationChannel ch = new NotificationChannel("ps3_" + name, "ProSchedule - " + name, NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(uri, aa);
      ch.enableVibration(true);
      nm.createNotificationChannel(ch);
    }
  }
}
