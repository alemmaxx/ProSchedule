package com.proschedule.app;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.HashSet;
import java.util.TimeZone;
import org.json.JSONArray;

// Sync dua hala dengan kalendar telefon (Google Calendar sync sendiri jika akaun
// Google sudah login dalam telefon):
//  - upsert/remove : tulis jadual ProSchedule ke kalendar
//  - listEvents    : baca jadual dari kalendar (termasuk yang berulang)
//  - refresh       : minta Android sync kalendar dengan Google sekarang
//  - event "calendarChanged" bila kalendar telefon berubah (semasa app hidup)
@CapacitorPlugin(
    name = "CalendarSync",
    permissions = {
      @Permission(alias = "calendar", strings = { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR })
    }
)
public class CalendarSyncPlugin extends Plugin {

  @Override
  public void load() {
    try {
      getContext().getContentResolver().registerContentObserver(
          CalendarContract.Events.CONTENT_URI,
          true,
          new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
              notifyListeners("calendarChanged", new JSObject());
            }
          });
    } catch (Exception e) {
      // abaikan
    }
  }

  private boolean has() {
    return getPermissionState("calendar") == PermissionState.GRANTED;
  }

  @PluginMethod
  public void requestAccess(PluginCall call) {
    if (has()) {
      call.resolve(new JSObject().put("granted", true));
      return;
    }
    requestPermissionForAlias("calendar", call, "accessCallback");
  }

  @PermissionCallback
  private void accessCallback(PluginCall call) {
    call.resolve(new JSObject().put("granted", has()));
  }

  @PluginMethod
  public void listCalendars(PluginCall call) {
    if (!has()) {
      call.reject("no permission");
      return;
    }
    try {
      boolean all = Boolean.TRUE.equals(call.getBoolean("all", false));
      JSArray arr = new JSArray();
      String[] proj = {
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME
      };
      String sel = all
          ? CalendarContract.Calendars.VISIBLE + " = 1"
          : CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= " + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
      Cursor c = getContext().getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, sel, null, null);
      while (c != null && c.moveToNext()) {
        JSObject o = new JSObject();
        o.put("id", c.getLong(0));
        o.put("name", c.getString(1));
        o.put("account", c.getString(2));
        arr.put(o);
      }
      if (c != null) c.close();
      call.resolve(new JSObject().put("calendars", arr));
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void upsert(PluginCall call) {
    if (!has()) {
      call.reject("no permission");
      return;
    }
    try {
      ContentResolver cr = getContext().getContentResolver();
      JSObject d = call.getData();
      long calId = d.optLong("calId", -1);
      long start = d.optLong("start", 0);
      long end = d.optLong("end", start + 3600000L);
      long eventId = d.optLong("eventId", -1);

      ContentValues v = new ContentValues();
      v.put(CalendarContract.Events.CALENDAR_ID, calId);
      v.put(CalendarContract.Events.TITLE, d.optString("title", ""));
      v.put(CalendarContract.Events.DESCRIPTION, d.optString("desc", ""));
      v.put(CalendarContract.Events.DTSTART, start);
      v.put(CalendarContract.Events.DTEND, end);
      v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

      long id = eventId;
      if (eventId > 0) {
        int n = cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), v, null, null);
        if (n == 0) eventId = -1;
      }
      if (eventId <= 0) {
        Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v);
        id = Long.parseLong(u.getLastPathSegment());
      }
      call.resolve(new JSObject().put("eventId", id));
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  @PluginMethod
  public void remove(PluginCall call) {
    if (!has()) {
      call.reject("no permission");
      return;
    }
    try {
      long eventId = call.getData().optLong("eventId", -1);
      if (eventId > 0) {
        getContext().getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
      }
      call.resolve();
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  // Baca semua instance jadual (termasuk berulang) dalam julat masa
  @PluginMethod
  public void listEvents(PluginCall call) {
    if (!has()) {
      call.reject("no permission");
      return;
    }
    try {
      JSObject d = call.getData();
      long start = d.optLong("start", 0);
      long end = d.optLong("end", 0);
      HashSet<Long> want = new HashSet<>();
      JSONArray ids = d.optJSONArray("calIds");
      if (ids != null) {
        for (int i = 0; i < ids.length(); i++) want.add(ids.optLong(i));
      }
      Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
      ContentUris.appendId(b, start);
      ContentUris.appendId(b, end);
      String[] proj = {
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.DESCRIPTION
      };
      Cursor c = getContext().getContentResolver().query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC");
      JSArray arr = new JSArray();
      int n = 0;
      while (c != null && c.moveToNext() && n < 1500) {
        long calId = c.getLong(5);
        if (!want.isEmpty() && !want.contains(calId)) continue;
        JSObject o = new JSObject();
        o.put("eventId", c.getLong(0));
        o.put("begin", c.getLong(1));
        o.put("end", c.getLong(2));
        o.put("title", c.getString(3));
        o.put("allDay", c.getInt(4) == 1);
        o.put("calId", calId);
        o.put("desc", c.getString(6));
        arr.put(o);
        n++;
      }
      if (c != null) c.close();
      call.resolve(new JSObject().put("events", arr));
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }

  // Minta Android sync kalendar dengan akaun Google sekarang
  @PluginMethod
  public void refresh(PluginCall call) {
    try {
      if (has()) {
        Cursor c = getContext().getContentResolver().query(
            CalendarContract.Calendars.CONTENT_URI,
            new String[] { CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE },
            null, null, null);
        HashSet<String> seen = new HashSet<>();
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        while (c != null && c.moveToNext()) {
          String name = c.getString(0);
          String type = c.getString(1);
          if (name == null || type == null || "LOCAL".equals(type)) continue;
          if (!seen.add(name + "|" + type)) continue;
          ContentResolver.requestSync(new Account(name, type), CalendarContract.AUTHORITY, extras);
        }
        if (c != null) c.close();
      }
    } catch (Exception e) {
      // abaikan
    }
    call.resolve();
  }

  // Buka skrin akaun Android: add=true -> login/tambah akaun Google (pilih akaun atau masuk manual),
  // add=false -> senarai akaun dalam telefon
  @PluginMethod
  public void openAccounts(PluginCall call) {
    try {
      boolean add = Boolean.TRUE.equals(call.getBoolean("add", true));
      Intent i;
      if (add) {
        i = new Intent(Settings.ACTION_ADD_ACCOUNT);
        i.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] { "com.google" });
      } else {
        i = new Intent(Settings.ACTION_SYNC_SETTINGS);
      }
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
      call.resolve();
    } catch (Exception e) {
      call.reject(String.valueOf(e.getMessage()));
    }
  }
}
