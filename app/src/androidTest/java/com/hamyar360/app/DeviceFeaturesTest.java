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
    Bitmap b=Bitmap.createBitmap(1500,480,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextSize(65);
    canvas.drawText("Invoice 1405/08/15",70,120,p);canvas.drawText("Amount 250000 RIAL",70,230,p);canvas.drawText("مبلغ پرداخت",70,350,p);
    ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();
    String text=OfflineOcr.read(context,"data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
    assertTrue(text,text.contains("1405"));assertTrue(text,text.contains("250000"));assertTrue(text,text.contains("مبلغ"));
  }
  @Test public void appAcceptsTextAndImageShares(){
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    for(String type:new String[]{"text/plain","image/jpeg"})assertNotNull(c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_SEND).setType(type).setPackage(c.getPackageName()),0));
  }
}
