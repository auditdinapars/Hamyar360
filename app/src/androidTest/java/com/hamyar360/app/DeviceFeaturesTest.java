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
    Bitmap b=Bitmap.createBitmap(1500,480,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);android.text.TextPaint p=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(65);
    String sample="Invoice 1405/08/15\nAmount 250000 RIAL\nمبلغ پرداخت";
    android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(sample,0,sample.length(),p,1360).setLineSpacing(25,1).build();
    canvas.save();canvas.translate(70,40);layout.draw(canvas);canvas.restore();
    ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();try(java.io.OutputStream fixture=context.openFileOutput("ocr-test.png",Context.MODE_PRIVATE)){fixture.write(out.toByteArray());}
    String text=OfflineOcr.read(context,"data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
    String diagnostic=text.replace('\n','|');assertTrue(diagnostic,text.contains("1405"));assertTrue(diagnostic,text.contains("250000"));assertTrue(diagnostic,text.contains("مبلغ"));
  }
  @Test public void appAcceptsTextAndImageShares(){
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    for(String type:new String[]{"text/plain","image/jpeg"})assertNotNull(c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_SEND).setType(type).setPackage(c.getPackageName()),0));
  }
}
