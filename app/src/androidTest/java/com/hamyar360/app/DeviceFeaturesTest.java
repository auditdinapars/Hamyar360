package com.hamyar360.app;

import android.content.*;
import android.graphics.*;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;

@RunWith(AndroidJUnit4.class)
public class DeviceFeaturesTest {
  @Test public void bundledOcrReadsDateAndAmountWithoutNetwork()throws Exception{
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    // Fixed, visually reviewed receipt screenshot; avoids depending on emulator font rendering.
    ByteArrayOutputStream out=new ByteArrayOutputStream();try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("ocr-receipt.png")){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
    String text=OfflineOcr.read(context,"data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
    String diagnostic=text.replace('\n','|');assertTrue(diagnostic,text.contains("1405"));assertTrue(diagnostic,text.contains("250000"));assertTrue(diagnostic,text.contains("مبلغ"));
  }
  @Test public void appAcceptsTextAndImageShares(){
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    for(String type:new String[]{"text/plain","image/jpeg"})assertNotNull(c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_SEND).setType(type).setPackage(c.getPackageName()),0));
  }
}
