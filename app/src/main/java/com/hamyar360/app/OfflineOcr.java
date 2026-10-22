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
    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);TessBaseAPI tess=new TessBaseAPI();
    try{
      if(!tess.init(base.getAbsolutePath(),"fas+eng"))throw new IOException("init");
      tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);tess.setImage(bitmap);String text=tess.getUTF8Text();
      // A second Persian-only pass preserves short RTL lines on bilingual receipts.
      if(!tess.init(base.getAbsolutePath(),"fas"))throw new IOException("init Persian");
      tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);tess.setImage(bitmap);
      StringBuilder result=new StringBuilder(text);
      for(String line:tess.getUTF8Text().split("\\r?\\n")){line=line.trim();if(line.matches(".*[\\u0600-\\u06ff].*")&&!text.contains(line))result.append('\n').append(line);}
      if(!result.toString().matches("(?s).*[\\u0621-\\u06ff].*")){
        // Wide receipts can put an isolated Persian line in a different column.
        // Recognize detected horizontal text bands instead of discarding that column.
        tess.init(base.getAbsolutePath(),"fas+eng");tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);tess.setImage(bitmap);
        int width=bitmap.getWidth(),height=bitmap.getHeight(),start=-1,last=-1,left=width,right=0,bands=0;int[] row=new int[width];
        for(int y=0;y<=height;y++){
          int count=0,min=width,max=0;if(y<height){bitmap.getPixels(row,0,width,0,y,width,1);for(int x=0;x<width;x++){int pixel=row[x];if(Color.red(pixel)*299+Color.green(pixel)*587+Color.blue(pixel)*114<150000){count++;min=Math.min(min,x);max=x;}}}
          if(count>=3&&count<width*.9){if(start<0)start=y;last=y;left=Math.min(left,min);right=Math.max(right,max);}
          if(start>=0&&(y-last>10||y==height)){
            if(last-start>=10&&last-start<250&&bands++<40){int x=Math.max(0,left-12),top=Math.max(0,start-12),w=Math.min(width-x,right-left+25),h=Math.min(height-top,last-start+25);tess.setRectangle(x,top,w,h);String line=tess.getUTF8Text().trim();if(line.matches("(?s).*[\\u0621-\\u06ff].*")&&!result.toString().contains(line))result.append('\n').append(line);}
            start=-1;left=width;right=0;
          }
        }
      }
      return result.toString();
    }finally{tess.recycle();if(bitmap!=null)bitmap.recycle();}
  }
}
