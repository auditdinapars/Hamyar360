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
      @JavascriptInterface public void ready(){runOnUiThread(()->{pageReady=true;if(pendingEvent!=null){String event=pendingEvent;pendingEvent=null;send(event);}});}
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
  }
  private boolean beginExternal(){if(externalBusy){error("ابتدا عملیات جاری را تمام کنید");return false;}externalBusy=true;return true;}
  private void launchVoice(){if(beginExternal())voiceInput.start();}
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);voiceInput.onPermissionResult(request,results);}
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
  private void cleanCamera(){if(cameraUri!=null)revokeUriPermission(cameraUri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);if(cameraFile!=null)cameraFile.delete();cameraFile=null;cameraUri=null;}
  private void error(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();event("error","message",text);}
  private void event(String type,String key,String value){try{JSONObject event=new JSONObject();event.put("type",type);if(key!=null)event.put(key,value);send(event.toString());}catch(Exception ignored){}}
  private void send(String json){if(!pageReady){pendingEvent=json;return;}if(web!=null&&!isDestroyed())web.evaluateJavascript("window.onNativeResult("+json+");",null);}
  @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(cameraFile!=null)state.putString("cameraFile",cameraFile.getPath());if(cameraUri!=null)state.putString("cameraUri",cameraUri.toString());state.putString("pendingEvent",pendingEvent);state.putBoolean("externalBusy",externalBusy && (voiceInput==null || !voiceInput.isActive()));}
  @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("(()=>{const d=Array.from(document.querySelectorAll('dialog[open]')).pop();if(d){if(d.id==='editor')closeEditor();else d.close();return true;}if(active!=='home'){show('home');return true;}return false;})()",handled->{if("false".equals(handled))MainActivity.super.onBackPressed();});else super.onBackPressed();}
  @Override protected void onDestroy(){if(voiceInput!=null)voiceInput.destroy();if(uploadCallback!=null){uploadCallback.onReceiveValue(null);uploadCallback=null;}worker.shutdown();if(web!=null){web.removeJavascriptInterface("HamyarAndroid");web.destroy();web=null;}super.onDestroy();}
}
