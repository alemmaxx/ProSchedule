package com.proschedule.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import java.io.File;

// Main MP3 pengguna dari awal hingga habis, berulang sehingga ditekan Berhenti (atau 10 minit)
public class AlarmService extends Service {
  private static final String CH = "ps_ring";
  private static final int NID = 7001;
  private MediaPlayer mp;
  private PowerManager.WakeLock wl;
  private final Handler h = new Handler(Looper.getMainLooper());
  private final Runnable autoStop = new Runnable() { @Override public void run() { stopSelf(); } };

  @Override public IBinder onBind(Intent i) { return null; }

  private int imm() { return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0; }

  @Override
  public int onStartCommand(Intent in, int flags, int startId) {
    if (in != null && "ps.stop".equals(in.getAction())) { stopSelf(); return START_NOT_STICKY; }
    String title = in != null && in.getStringExtra("title") != null ? in.getStringExtra("title") : "ProSchedule";
    String body = in != null && in.getStringExtra("body") != null ? in.getStringExtra("body") : "";
    String sound = in != null && in.getStringExtra("sound") != null ? in.getStringExtra("sound") : "";

    if (Build.VERSION.SDK_INT >= 26) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel ch = new NotificationChannel(CH, "ProSchedule - Alarm MP3", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(null, null);
      ch.enableVibration(true);
      nm.createNotificationChannel(ch);
    }
    Intent open = new Intent(this, MainActivity.class);
    open.putExtra("ps_stop", true);
    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pOpen = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | imm());
    Intent stop = new Intent(this, AlarmService.class).setAction("ps.stop");
    PendingIntent pStop = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | imm());

    Notification n = new NotificationCompat.Builder(this, CH)
        .setSmallIcon(R.drawable.ic_stat_ps)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setOngoing(true)
        .setContentIntent(pOpen)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Berhenti", pStop)
        .build();
    if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    else startForeground(NID, n);

    play(sound);
    h.removeCallbacks(autoStop);
    h.postDelayed(autoStop, 10 * 60 * 1000L);
    return START_NOT_STICKY;
  }

  private void play(String sound) {
    release();
    try {
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "proschedule:alarm");
      wl.acquire(10 * 60 * 1000L);
    } catch (Exception e) { }
    try {
      mp = new MediaPlayer();
      mp.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build());
      File f = new File(getFilesDir(), "snd_" + sound.replaceAll("[^A-Za-z0-9_]", "_") + ".mp3");
      if (f.exists()) mp.setDataSource(f.getAbsolutePath());
      else {
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        mp.setDataSource(this, d);
      }
      mp.setLooping(true);
      mp.prepare();
      mp.start();
    } catch (Exception e) {
      release();
    }
  }

  private void release() {
    if (mp != null) {
      try { mp.stop(); } catch (Exception e) { }
      try { mp.release(); } catch (Exception e) { }
      mp = null;
    }
    if (wl != null) {
      try { if (wl.isHeld()) wl.release(); } catch (Exception e) { }
      wl = null;
    }
  }

  @Override
  public void onDestroy() {
    h.removeCallbacks(autoStop);
    release();
    super.onDestroy();
  }
}
