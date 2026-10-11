package com.proschedule.app;

import android.content.res.AssetFileDescriptor;
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

  @PluginMethod
  public void stop(PluginCall call) {
    release();
    call.resolve();
  }
}
