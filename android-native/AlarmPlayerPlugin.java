package com.proschedule.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import com.getcapacitor.JSObject;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Base64;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;

// Main bunyi guna volume ALARM (bukan volume media/notifikasi)
@CapacitorPlugin(name = "AlarmPlayer")
public class AlarmPlayerPlugin extends Plugin {
  private MediaPlayer mp;

  private void release() {
    if (mp != null) {
      try { mp.stop(); } catch (Exception e) { }
      try { mp.release(); } catch (Exception e) { }
      mp = null;
    }
  }

  private MediaPlayer newPlayer(boolean loop) {
    MediaPlayer m = new MediaPlayer();
    m.setAudioAttributes(new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build());
    m.setLooping(loop);
    return m;
  }

  private File fileFor(String id) {
    return new File(getContext().getFilesDir(), "snd_" + id.replaceAll("[^A-Za-z0-9_]", "_") + ".mp3");
  }

  @PluginMethod
  public void store(PluginCall call) {
    try {
      String id = call.getString("id");
      String data = call.getString("data");
      byte[] bytes = Base64.decode(data, Base64.DEFAULT);
      FileOutputStream os = new FileOutputStream(fileFor(id));
      os.write(bytes);
      os.close();
      call.resolve();
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void play(PluginCall call) {
    try {
      release();
      String id = call.getString("id");
      boolean loop = Boolean.TRUE.equals(call.getBoolean("loop", false));
      mp = newPlayer(loop);
      mp.setDataSource(fileFor(id).getAbsolutePath());
      mp.prepare();
      mp.start();
      call.resolve();
    } catch (Exception e) {
      release();
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void playRaw(PluginCall call) {
    try {
      release();
      String name = call.getString("name");
      boolean loop = Boolean.TRUE.equals(call.getBoolean("loop", false));
      int resId = getContext().getResources().getIdentifier(name, "raw", getContext().getPackageName());
      if (resId == 0) { call.reject("raw not found"); return; }
      AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(resId);
      mp = newPlayer(loop);
      mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      afd.close();
      mp.prepare();
      mp.start();
      call.resolve();
    } catch (Exception e) {
      release();
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  // ---- MP3 pengguna jadi bunyi notifikasi sebenar (berbunyi walaupun app ditutup) ----
  private String safe(String id) { return id.replaceAll("[^A-Za-z0-9_]", "_"); }

  @PluginMethod
  public void hasSound(PluginCall call) {
    boolean has = false;
    try {
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        has = nm.getNotificationChannel("ps3_" + call.getString("id")) != null;
      }
    } catch (Exception e) { }
    call.resolve(new JSObject().put("has", has));
  }

  // Salin MP3 ke MediaStore (Alarms/ProSchedule) supaya sistem boleh baca, kemudian cipta channel notifikasi
  @PluginMethod
  public void installSound(PluginCall call) {
    try {
      if (Build.VERSION.SDK_INT < 29) { call.reject("Perlu Android 10 ke atas"); return; }
      String id = call.getString("id");
      String name = call.getString("name", "MP3");
      File f = fileFor(id);
      if (!f.exists()) { call.reject("fail MP3 tiada"); return; }
      ContentResolver cr = getContext().getContentResolver();
      Uri coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      ContentValues v = new ContentValues();
      v.put(MediaStore.Audio.Media.DISPLAY_NAME, "proschedule_" + safe(id) + ".mp3");
      v.put(MediaStore.Audio.Media.TITLE, name);
      v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
      v.put(MediaStore.Audio.Media.RELATIVE_PATH, "Alarms/ProSchedule");
      v.put(MediaStore.Audio.Media.IS_ALARM, 1);
      v.put(MediaStore.Audio.Media.IS_PENDING, 1);
      Uri uri = cr.insert(coll, v);
      if (uri == null) { call.reject("gagal tulis MediaStore"); return; }
      OutputStream os = cr.openOutputStream(uri);
      FileInputStream in = new FileInputStream(f);
      byte[] buf = new byte[65536];
      int n;
      while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
      in.close();
      os.close();
      ContentValues done = new ContentValues();
      done.put(MediaStore.Audio.Media.IS_PENDING, 0);
      cr.update(uri, done, null, null);

      if (Build.VERSION.SDK_INT >= 26) {
        AudioAttributes aa = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("ps3_" + id, "ProSchedule - " + name, NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(uri, aa);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
      }
      call.resolve(new JSObject().put("uri", uri.toString()));
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void removeSound(PluginCall call) {
    try {
      String id = call.getString("id");
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.deleteNotificationChannel("ps3_" + id);
      }
      if (Build.VERSION.SDK_INT >= 29) {
        getContext().getContentResolver().delete(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Audio.Media.DISPLAY_NAME + " = ?",
            new String[] { "proschedule_" + safe(id) + ".mp3" });
      }
      fileFor(id).delete();
    } catch (Exception e) { }
    call.resolve();
  }

  @PluginMethod
  public void stop(PluginCall call) {
    release();
    call.resolve();
  }
}
