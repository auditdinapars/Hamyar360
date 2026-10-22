package com.hamyar360.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> uploadCallback;
  private static final int FILE_PICK = 911;
  private static final int SAVE_BACKUP = 912;
  private String backupJson;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    web = new WebView(this);
    setContentView(web);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    web.setWebViewClient(new WebViewClient());
    web.setBackgroundColor(android.graphics.Color.WHITE);
    web.addJavascriptInterface(new Object() {
      @JavascriptInterface public void saveBackup(String json) {
        runOnUiThread(() -> {
          backupJson = json;
          Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setType("application/json");
          intent.putExtra(Intent.EXTRA_TITLE, "hamyar360-backup.json");
          startActivityForResult(intent, SAVE_BACKUP);
        });
      }
    }, "HamyarAndroid");
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        uploadCallback = callback;
        try { startActivityForResult(params.createIntent(), FILE_PICK); return true; }
        catch (Exception ex) { uploadCallback = null; callback.onReceiveValue(null); return false; }
      }
    });
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == SAVE_BACKUP) {
      if (resultCode == RESULT_OK && data != null && data.getData() != null && backupJson != null) {
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          if (out != null) out.write(backupJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
      }
      backupJson = null;
    }
    if (requestCode == FILE_PICK && uploadCallback != null) {
      uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
      uploadCallback = null;
    }
  }
  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
  @Override protected void onDestroy() { if(web != null) web.destroy(); super.onDestroy(); }
}
