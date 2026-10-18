package com.proschedule.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent in) {
    try {
      Intent s = new Intent(c, AlarmService.class);
      s.putExtra("title", in.getStringExtra("title"));
      s.putExtra("body", in.getStringExtra("body"));
      s.putExtra("sound", in.getStringExtra("sound"));
      ContextCompat.startForegroundService(c, s);
    } catch (Exception e) { }
  }
}
