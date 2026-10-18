package com.proschedule.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

// Penjadual alarm native untuk bunyi MP3 pengguna (setAlarmClock = tepat masa, tembus Doze)
public class NativeAlarms {
  private static int imm() { return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0; }

  static void arm(Context c, JSONArray items, boolean replace) throws Exception {
    SharedPreferences sp = c.getSharedPreferences("ps_native", Context.MODE_PRIVATE);
    JSONArray old = new JSONArray(sp.getString("items", "[]"));
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    JSONArray keep = new JSONArray();
    if (replace) {
      for (int i = 0; i < old.length(); i++) cancel(c, am, old.getJSONObject(i).optInt("id"));
    } else {
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.getJSONObject(i);
        boolean dup = false;
        for (int j = 0; j < items.length(); j++) if (items.getJSONObject(j).optInt("id") == o.optInt("id")) dup = true;
        if (!dup) keep.put(o);
      }
    }
    long now = System.currentTimeMillis();
    int n = 0;
    for (int i = 0; i < items.length() && n < 300; i++) {
      JSONObject o = items.getJSONObject(i);
      if (o.optLong("at") <= now) continue;
      try { schedule(c, am, o); keep.put(o); n++; } catch (Exception e) { }
    }
    sp.edit().putString("items", keep.toString()).apply();
  }

  static void schedule(Context c, AlarmManager am, JSONObject o) {
    int id = o.optInt("id");
    Intent i = new Intent(c, AlarmReceiver.class);
    i.putExtra("title", o.optString("title"));
    i.putExtra("body", o.optString("body"));
    i.putExtra("sound", o.optString("sound"));
    PendingIntent pi = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | imm());
    PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), imm());
    am.setAlarmClock(new AlarmManager.AlarmClockInfo(o.optLong("at"), show), pi);
  }

  static void cancel(Context c, AlarmManager am, int id) {
    PendingIntent pi = PendingIntent.getBroadcast(c, id, new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_NO_CREATE | imm());
    if (pi != null) { am.cancel(pi); pi.cancel(); }
  }

  // Selepas reboot / kemas kini app: jadual semula alarm yang belum berlalu
  static void rearm(Context c) {
    try {
      SharedPreferences sp = c.getSharedPreferences("ps_native", Context.MODE_PRIVATE);
      JSONArray old = new JSONArray(sp.getString("items", "[]"));
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      long now = System.currentTimeMillis();
      JSONArray keep = new JSONArray();
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.getJSONObject(i);
        if (o.optLong("at") > now) { try { schedule(c, am, o); keep.put(o); } catch (Exception e) { } }
      }
      sp.edit().putString("items", keep.toString()).apply();
    } catch (Exception e) { }
  }
}
