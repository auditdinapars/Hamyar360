package com.hamyar360.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private static final int FILE_PICK=911, SAVE_DOCUMENT=912, PHOTO=914;
  private static final String HOME="file:///android_asset/index.html";
  private WebView web;
  private VoiceInput voiceInput;
  private ValueCallback<Uri[]> uploadCallback;
  private File cameraFile;
  private Uri cameraUri;
  private String pendingEvent;
  private final java.util.ArrayList<String> events=new java.util.ArrayList<>();
  private boolean pageReady, externalBusy;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();

  @SuppressLint("SetJavaScriptEnabled")
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    if(state!=null){String path=state.getString("cameraFile");if(path!=null)cameraFile=new File(path);String uri=state.getString("cameraUri");if(uri!=null)cameraUri=Uri.parse(uri);pendingEvent=state.getString("pendingEvent");externalBusy=state.getBoolean("externalBusy");}
    voiceInput=new VoiceInput(this,new VoiceInput.Listener(){
      @Override public void result(String text){event("voice","text",text);}
      @Override public void error(String message){MainActivity.this.error(message);}
      @Override public void finished(){externalBusy=false;}
    });
    web=new WebView(this);setContentView(web);
    web.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
    web.requestApplyInsets();
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(true);settings.setAllowContentAccess(true);
    settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !HOME.equals(request.getUrl().toString());}
      @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !HOME.equals(url);}
    });
    web.addJavascriptInterface(new Object(){
      @JavascriptInterface public void ready(){runOnUiThread(()->{pageReady=true;deliverInbox();for(String e:events)send(e);events.clear();if(pendingEvent!=null){String event=pendingEvent;pendingEvent=null;send(event);}});}
      @JavascriptInterface public void readPhoto(String data){worker.execute(()->{try{String text=OfflineOcr.read(MainActivity.this,data);runOnUiThread(()->event("ocr","text",text));}catch(Throwable ex){runOnUiThread(()->event("ocrError","message","خواندن عکس ممکن نشد؛ عکس واضح‌تر و مستقیم بگیرید."));}});}
      @JavascriptInterface public void syncReminders(String json){worker.execute(()->ReminderReceiver.sync(MainActivity.this,json));}
      @JavascriptInterface public void requestNotifications(){runOnUiThread(()->{if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},916);});}
      @JavascriptInterface public void ackInbox(String ids){worker.execute(()->{try{org.json.JSONArray ack=new org.json.JSONArray(ids),a=inbox(),keep=new org.json.JSONArray();java.util.HashSet<String> remove=new java.util.HashSet<>();for(int i=0;i<ack.length();i++)remove.add(ack.getString(i));for(int i=0;i<a.length();i++)if(!remove.contains(a.getJSONObject(i).getString("id")))keep.put(a.getJSONObject(i));saveInbox(keep);}catch(Exception ignored){}});}
      @JavascriptInterface public void startVoice(){runOnUiThread(()->launchVoice());}
      @JavascriptInterface public void takePhoto(){runOnUiThread(()->launchPhoto());}
      @JavascriptInterface public void saveDocument(String text,String mime,String name){runOnUiThread(()->saveText(text,mime,name));}
    },"HamyarAndroid");
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("تأیید",(d,w)->result.confirm()).setNegativeButton("انصراف",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
      @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("باشه",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
      @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(uploadCallback!=null)uploadCallback.onReceiveValue(null);uploadCallback=callback;
        try{startActivityForResult(params.createIntent(),FILE_PICK);return true;}catch(Exception ex){uploadCallback=null;error("برنامهٔ انتخاب فایل در دسترس نیست");return false;}
      }
    });
    web.loadUrl(HOME);
    if(state==null)acceptShare(getIntent());
  }
  private boolean beginExternal(){if(externalBusy){error("ابتدا عملیات جاری را تمام کنید");return false;}externalBusy=true;return true;}
  private void launchVoice(){if(beginExternal())voiceInput.start();}
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);voiceInput.onPermissionResult(request,results);if(request==916&&(results.length==0||results[0]!=android.content.pm.PackageManager.PERMISSION_GRANTED))error("اجازهٔ اعلان داده نشد؛ یادآور ذخیره شده اما اعلان نمایش داده نمی‌شود. در تنظیمات گوشی اعلان‌های همیار را فعال کنید.");}
  @Override protected void onStop(){voiceInput.onStop();super.onStop();}
  private void launchPhoto(){
    if(!beginExternal())return;
    try{
      File dir=new File(getCacheDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new Exception("directory");
      cameraFile=File.createTempFile("capture-",".jpg",dir);
      cameraUri=FileProvider.getUriForFile(this,getPackageName()+".files",cameraFile);
      Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);intent.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
      intent.setClipData(ClipData.newRawUri("photo",cameraUri));intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivityForResult(intent,PHOTO);
    }catch(Exception ex){externalBusy=false;cleanCamera();error("بازکردن دوربین ممکن نشد؛ وجود برنامهٔ دوربین و فضای خالی را بررسی کنید.");}
  }
  private File exportFile(){return new File(getCacheDir(),"pending-export.txt");}
  private void saveText(String text,String mime,String name){
    if(!beginExternal())return;
    if(text==null||text.length()>60*1024*1024){externalBusy=false;error("حجم خروجی بیش از حد مجاز است");return;}
    final boolean csv="text/csv".equals(mime);
    worker.execute(()->{try{try(OutputStream out=new java.io.FileOutputStream(exportFile())){out.write(text.getBytes(StandardCharsets.UTF_8));}
      runOnUiThread(()->{try{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(csv?"text/csv":"application/json");intent.putExtra(Intent.EXTRA_TITLE,csv?"hamyar360-report.csv":"hamyar360-backup.json");startActivityForResult(intent,SAVE_DOCUMENT);}catch(Exception ex){externalBusy=false;exportFile().delete();error("ذخیرهٔ فایل در این گوشی ممکن نشد");}});
    }catch(Exception ex){runOnUiThread(()->{externalBusy=false;error("آماده‌سازی فایل ناموفق بود");});}});
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==FILE_PICK&&uploadCallback!=null){uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));uploadCallback=null;return;}
    if(request==PHOTO){if(result==RESULT_OK&&cameraFile!=null){final File captured=cameraFile;worker.execute(()->{try{String encoded=encodePhoto(captured);runOnUiThread(()->{externalBusy=false;cleanCamera();event("photo","data",encoded);});}catch(Exception ex){runOnUiThread(()->{externalBusy=false;cleanCamera();error("خواندن عکس ممکن نشد؛ دوباره عکس بگیرید.");});}});}else{externalBusy=false;cleanCamera();event("cancel",null,null);}}
    if(request==SAVE_DOCUMENT){if(result==RESULT_OK&&data!=null&&data.getData()!=null){final Uri dest=data.getData();worker.execute(()->{try(java.io.InputStream in=new java.io.FileInputStream(exportFile());OutputStream out=getContentResolver().openOutputStream(dest,"wt")){if(out==null)throw new Exception("no output");byte[] buffer=new byte[8192];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);runOnUiThread(()->event("saved",null,null));}catch(Exception ex){runOnUiThread(()->error("ذخیرهٔ فایل ناموفق بود؛ دوباره تلاش کنید."));}finally{exportFile().delete();runOnUiThread(()->externalBusy=false);}});}else{externalBusy=false;exportFile().delete();event("cancel",null,null);}}
  }
  private String encodePhoto(File file)throws Exception{
    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
    if(options.outWidth<=0||options.outHeight<=0)throw new Exception("invalid image");options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
    options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap==null)throw new Exception("decode");
    int orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix matrix=new Matrix();
    switch(orientation){case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.setScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_180:matrix.setRotate(180);break;case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.setScale(1,-1);break;case ExifInterface.ORIENTATION_TRANSPOSE:matrix.setRotate(90);matrix.postScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_90:matrix.setRotate(90);break;case ExifInterface.ORIENTATION_TRANSVERSE:matrix.setRotate(-90);matrix.postScale(-1,1);break;case ExifInterface.ORIENTATION_ROTATE_270:matrix.setRotate(-90);break;default:break;}
    if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
    ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);bitmap.recycle();
    if(out.size()>1400000)throw new Exception("image too large");return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptShare(intent);}
  private String readInboxFile()throws Exception{try(java.io.InputStream in=new java.io.FileInputStream(new File(getFilesDir(),"inbox.json"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}}
  private org.json.JSONArray inbox(){try{return new org.json.JSONArray(readInboxFile());}catch(Exception e){return new org.json.JSONArray();}}
  private void saveInbox(org.json.JSONArray a)throws Exception{android.util.AtomicFile f=new android.util.AtomicFile(new File(getFilesDir(),"inbox.json"));java.io.FileOutputStream out=f.startWrite();try{out.write(a.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception ex){f.failWrite(out);throw ex;}}
  private void deliverInbox(){worker.execute(()->{try{org.json.JSONObject e=new org.json.JSONObject();e.put("type","inbox");org.json.JSONArray a=inbox();if(a.length()==0)return;e.put("items",a);runOnUiThread(()->send(e.toString()));}catch(Exception ignored){}});}
  private void acceptShare(Intent intent){if(intent==null||(!Intent.ACTION_SEND.equals(intent.getAction())&&!Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())))return;
    final String text=String.valueOf(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)==null?"":intent.getCharSequenceExtra(Intent.EXTRA_TEXT));final java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();
    if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){java.util.ArrayList<Uri> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(list!=null)uris.addAll(list);}else{Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null)uris.add(uri);}
    intent.setAction(Intent.ACTION_MAIN);
    worker.execute(()->{try{org.json.JSONArray a=inbox();if(a.length()+Math.max(1,uris.size())>1000||uris.size()>10)throw new Exception("limit");for(int i=0;i<Math.max(1,uris.size());i++){String photo="";if(!uris.isEmpty()){Uri uri=uris.get(i);if(!"content".equals(uri.getScheme()))throw new Exception("uri");File f=File.createTempFile("share-",".jpg",getCacheDir());try{try(java.io.InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new java.io.FileOutputStream(f)){if(in==null)throw new Exception("stream");byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>25*1024*1024)throw new Exception("size");out.write(buf,0,n);}}photo=encodePhoto(f);}finally{f.delete();}}
      org.json.JSONObject x=new org.json.JSONObject();x.put("id",java.util.UUID.randomUUID().toString());x.put("text",text.substring(0,Math.min(text.length(),20000)));x.put("photo",photo);x.put("receivedAt",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",java.util.Locale.US).format(new java.util.Date()));a.put(x);}saveInbox(a);runOnUiThread(()->{if(pageReady)deliverInbox();});}catch(Exception ex){runOnUiThread(()->error("دریافت پیام ناموفق بود؛ متن یا حداکثر ۱۰ عکس با حجم کمتر از ۲۵ مگابایت به اشتراک بگذارید."));}});
  }
  private void cleanCamera(){if(cameraUri!=null)revokeUriPermission(cameraUri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);if(cameraFile!=null)cameraFile.delete();cameraFile=null;cameraUri=null;}
  private void error(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();event("error","message",text);}
  private void event(String type,String key,String value){try{JSONObject event=new JSONObject();event.put("type",type);if(key!=null)event.put(key,value);send(event.toString());}catch(Exception ignored){}}
  private void send(String json){if(!pageReady){events.add(json);return;}if(web!=null&&!isDestroyed())web.evaluateJavascript("window.onNativeResult("+json+");",null);}
  @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(cameraFile!=null)state.putString("cameraFile",cameraFile.getPath());if(cameraUri!=null)state.putString("cameraUri",cameraUri.toString());state.putString("pendingEvent",pendingEvent);state.putBoolean("externalBusy",externalBusy && (voiceInput==null || !voiceInput.isActive()));}
  @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("(()=>{const d=Array.from(document.querySelectorAll('dialog[open]')).pop();if(d){if(d.id==='editor')closeEditor();else d.close();return true;}if(active!=='home'){show('home');return true;}return false;})()",handled->{if("false".equals(handled))MainActivity.super.onBackPressed();});else super.onBackPressed();}
  @Override protected void onDestroy(){if(voiceInput!=null)voiceInput.destroy();if(uploadCallback!=null){uploadCallback.onReceiveValue(null);uploadCallback=null;}worker.shutdown();if(web!=null){web.removeJavascriptInterface("HamyarAndroid");web.destroy();web=null;}super.onDestroy();}
}
