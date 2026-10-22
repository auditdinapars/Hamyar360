package com.hamyar360.app;

import android.content.Context;
import android.graphics.*;
import android.util.Base64;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;

final class OfflineOcr {
  static String read(Context context,String data)throws Exception{
    if(data==null||data.length()>2000000||!data.startsWith("data:image/"))throw new IOException("image");
    File base=new File(context.getFilesDir(),"ocr"),dir=new File(base,"tessdata");if(!dir.exists()&&!dir.mkdirs())throw new IOException("directory");
    for(String lang:new String[]{"fas","eng"}){File f=new File(dir,lang+".traineddata");if(!f.exists()){File temp=new File(dir,lang+".tmp");try(InputStream in=context.getAssets().open("tessdata/"+lang+".traineddata");OutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}if(!temp.renameTo(f))throw new IOException("model");}}
    byte[] bytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);
    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<1||options.outHeight<1)throw new IOException("image");options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2400)options.inSampleSize*=2;options.inJustDecodeBounds=false;
    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);TessBaseAPI tess=new TessBaseAPI();try{if(!tess.init(base.getAbsolutePath(),"fas+eng"))throw new IOException("init");tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);tess.setImage(bitmap);return tess.getUTF8Text();}finally{tess.recycle();if(bitmap!=null)bitmap.recycle();}
  }
}
