package com.hamyar360.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/** One user-initiated recognition session. Audio is never saved by this app. */
final class VoiceInput {
  static final int MICROPHONE_PERMISSION=915;
  interface Listener { void result(String text); void error(String message); void finished(); }
  private final Activity activity;
  private final Listener listener;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private SpeechRecognizer recognizer;
  private AlertDialog dialog;
  private boolean active, destroyed;
  private int generation;
  private long started;
  private final Runnable retry=()->{if(active&&!destroyed)listen();};
  private final Runnable timeout=()->fail("پاسخی از سرویس گفتار نرسید؛ اتصال اینترنت و سرویس گفتار گوشی را بررسی کنید.");

  VoiceInput(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
  void start(){
    if(destroyed||active)return;
    active=true;started=android.os.SystemClock.elapsedRealtime();
    if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){listen();return;}
    boolean asked=activity.getPreferences(Activity.MODE_PRIVATE).getBoolean("microphoneAsked",false);
    if(asked&&!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
      finish();permissionHelp(false);return;
    }
    if(activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)){
      dialog=new AlertDialog.Builder(activity).setTitle("مجوز میکروفن همیار")
        .setMessage("برای تبدیل فرمان شما به متن، همیار به میکروفن نیاز دارد. فقط با زدن فرمان صوتی گوش می‌دهد و فایل صوتی ذخیره نمی‌کند. سرویس گفتار گوشی ممکن است صدا را برای تبدیل به متن از طریق اینترنت پردازش کند.")
        .setPositiveButton("ادامه",(d,w)->requestPermission())
        .setNegativeButton("انصراف",(d,w)->finish()).setOnCancelListener(d->finish()).show();
    }else requestPermission();
  }
  private void requestPermission(){
    activity.getPreferences(Activity.MODE_PRIVATE).edit().putBoolean("microphoneAsked",true).apply();
    activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);
  }
  void onPermissionResult(int requestCode,int[] results){
    if(requestCode!=MICROPHONE_PERMISSION||destroyed)return;
    if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){active=true;listen();}
    else{finish();permissionHelp(false);}
  }
  private void listen(){
    if(started==0)started=android.os.SystemClock.elapsedRealtime();
    if(recognizer!=null){++generation;recognizer.destroy();recognizer=null;}
    if(dialog!=null){dialog.dismiss();dialog=null;}
    if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){finish();permissionHelp(false);return;}
    if(!SpeechRecognizer.isRecognitionAvailable(activity)){fail("سرویس تشخیص گفتار روی گوشی فعال نیست؛ یک سرویس سازگار با فارسی را نصب یا فعال کنید.");return;}
    final int session=++generation;
    try{
      recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
      recognizer.setRecognitionListener(new RecognitionListener(){
        private boolean current(){return active&&!destroyed&&session==generation;}
        @Override public void onReadyForSpeech(Bundle params){if(current()&&dialog!=null)dialog.setMessage("حالا فارسی صحبت کنید؛ مثلاً فردا بیست میلیون تومان پرداخت کنم.");}
        @Override public void onBeginningOfSpeech(){}
        @Override public void onRmsChanged(float rms){}
        @Override public void onBufferReceived(byte[] buffer){}
        @Override public void onEndOfSpeech(){if(current()&&dialog!=null){dialog.setMessage("در حال تبدیل گفتار به متن…");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);}}
        @Override public void onError(int code){if(!current())return;if(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){boolean provider=activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;finish();permissionHelp(provider);}else if((code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)&&android.os.SystemClock.elapsedRealtime()-started<45000){if(dialog!=null)dialog.setMessage("هنوز گوش می‌دهم؛ فرمان خود را بیان کنید…");++generation;handler.removeCallbacks(timeout);handler.postDelayed(retry,500);}else fail(message(code));}
        @Override public void onResults(Bundle results){if(!current())return;ArrayList<String> texts=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String text=texts!=null&&!texts.isEmpty()?texts.get(0):null;if(text!=null&&!text.trim().isEmpty()){finish();listener.result(text);}else if(android.os.SystemClock.elapsedRealtime()-started<45000){++generation;handler.removeCallbacks(timeout);handler.postDelayed(retry,500);if(dialog!=null)dialog.setMessage("هنوز گوش می‌دهم؛ دوباره صحبت کنید…");}else fail("گفتار تشخیص داده نشد؛ دوباره فرمان صوتی را بزنید.");}
        @Override public void onPartialResults(Bundle results){}
        @Override public void onEvent(int type,Bundle params){}
      });
      dialog=new AlertDialog.Builder(activity).setTitle("فرمان صوتی فارسی")
        .setMessage("در حال آماده‌سازی میکروفن…")
        .setPositiveButton("پایان صحبت",null)
        .setNegativeButton("انصراف",(d,w)->finish()).setOnCancelListener(d->finish()).create();
      dialog.setCanceledOnTouchOutside(false);dialog.show();
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(recognizer!=null){recognizer.stopListening();dialog.setMessage("در حال تبدیل گفتار به متن…");v.setEnabled(false);}});
      Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fa-IR");
      intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
      intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
      intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,15000L);
      intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,4000L);
      intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,3000L);
      handler.postDelayed(timeout,45000);
      recognizer.startListening(intent);
    }catch(SecurityException ex){finish();permissionHelp(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);}
    catch(Exception ex){fail("شروع تشخیص گفتار ممکن نشد؛ سرویس گفتار گوشی را بررسی کنید.");}
  }
  private String message(int code){
    switch(code){
      case SpeechRecognizer.ERROR_AUDIO:return "میکروفن در دسترس نیست؛ کلید دسترسی میکروفن گوشی را روشن کنید و ضبط صدای برنامه‌های دیگر را ببندید.";
      case SpeechRecognizer.ERROR_NETWORK:case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "ارتباط سرویس گفتار برقرار نشد؛ اینترنت گوشی را بررسی و دوباره تلاش کنید.";
      case SpeechRecognizer.ERROR_NO_MATCH:case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "گفتار واضحی دریافت نشد؛ دوباره فرمان صوتی را بزنید و صحبت کنید.";
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "سرویس گفتار مشغول است؛ چند لحظه بعد دوباره تلاش کنید.";
      case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:return "زبان فارسی در سرویس گفتار فعلی آماده نیست؛ پشتیبانی یا بستهٔ زبان فارسی را در تنظیمات سرویس بررسی کنید.";
      default:return "سرویس گفتار پاسخ مناسبی نداد (کد "+code+")؛ دوباره تلاش کنید یا تنظیمات سرویس را بررسی کنید.";
    }
  }
  private void permissionHelp(boolean provider){
    if(destroyed||activity.isFinishing())return;
    String text=provider?"همیار مجوز میکروفن دارد، اما سرویس گفتار اجازهٔ دسترسی نمی‌دهد. در تنظیمات گوشی، برنامه‌ای را که پیام خطا نام برده است (مثلاً Google یا سرویس گفتار گوگل) باز کنید و مجوز میکروفن آن را بررسی کنید. کلید سراسری میکروفن گوشی نیز باید روشن باشد.":"برای فرمان صوتی، مجوز میکروفن همیار را روی «هنگام استفاده از برنامه» بگذارید. ثبت دستی همچنان در دسترس است.";
    listener.error(text);
    dialog=new AlertDialog.Builder(activity).setTitle(provider?"مجوز سرویس گفتار":"مجوز میکروفن همیار").setMessage(text)
      .setPositiveButton("بازکردن تنظیمات",(d,w)->{try{Intent intent=provider?new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName()));activity.startActivity(intent);}catch(Exception ex){listener.error("تنظیمات گوشی را دستی باز کنید و مجوز میکروفن را بررسی کنید.");}})
      .setNegativeButton("فعلاً نه",null).show();
  }
  private void fail(String message){if(!active)return;finish();listener.error(message);}
  private void finish(){
    active=false;++generation;handler.removeCallbacks(timeout);handler.removeCallbacks(retry);
    if(recognizer!=null){SpeechRecognizer old=recognizer;recognizer=null;try{old.cancel();}catch(Exception ignored){}old.destroy();}
    if(dialog!=null){dialog.dismiss();dialog=null;}
    listener.finished();
  }
  void onStop(){if(active&&recognizer!=null)finish();}
  boolean isActive(){return active;}
  void destroy(){destroyed=true;finish();}
}
