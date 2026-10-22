package com.hamyar360.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;

public class ReminderReceiver extends BroadcastReceiver {
  static PendingIntent alarm(Context c,String id){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction("remind").setData(Uri.parse("hamyar://reminder/"+Uri.encode(id))),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
  static JSONArray saved(Context c){try{return new JSONArray(c.getSharedPreferences("reminders",0).getString("items","[]"));}catch(Exception e){return new JSONArray();}}
  static void sync(Context c,String json){try{JSONArray next=new JSONArray(json);if(next.length()>10000)return;AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);JSONArray previous=saved(c);for(int i=0;i<previous.length();i++){String id=previous.getJSONObject(i).getString("id");m.cancel(alarm(c,id));((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id,0);}c.getSharedPreferences("reminders",0).edit().putString("items",next.toString()).commit();schedule(c,next);}catch(Exception ignored){}}
  static void schedule(Context c,JSONArray a){AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);for(int i=0;i<a.length();i++)try{JSONObject x=a.getJSONObject(i);long t=x.getLong("time");if(t>System.currentTimeMillis())m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t,alarm(c,x.getString("id")));}catch(Exception ignored){}}
  @Override public void onReceive(Context c,Intent intent){if(!"remind".equals(intent.getAction())){schedule(c,saved(c));return;}String id=intent.getData()==null?"":intent.getData().getLastPathSegment();JSONArray a=saved(c);for(int i=0;i<a.length();i++)try{JSONObject x=a.getJSONObject(i);if(!id.equals(x.getString("id")))continue;NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel("tasks","یادآور کارها",NotificationManager.IMPORTANCE_HIGH));Intent open=new Intent(c,MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent p=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"tasks"):new Notification.Builder(c);n.notify(id,0,b.setSmallIcon(com.hamyar360.app.R.drawable.ic_notification).setContentTitle("یادآور همیار ۳۶۰").setContentText(x.getString("title")).setStyle(new Notification.BigTextStyle().bigText(x.getString("title"))).setContentIntent(p).setAutoCancel(true).setColor(0xffd95900).build());}catch(Exception ignored){}}
}
