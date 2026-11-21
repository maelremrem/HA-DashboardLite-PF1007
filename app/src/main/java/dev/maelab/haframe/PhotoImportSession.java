package dev.maelab.haframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.os.StatFs;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** Authenticated, bounded image album transfer; compresses photos on the device. */
final class PhotoImportSession {
    private static final int MAX_BYTES=6*1024*1024;
    private static final int MAX_CHUNK=60000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static String session="";
    private static String sha256="";
    private static int total,received,sequence;
    private static long expires;

    static synchronized String begin(Context context,int size,String digest) throws Exception {
        if(size<20||size>MAX_BYTES||digest==null||!digest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid photo source");
        File directory=PhotoLibrary.folder(context);
        if(new StatFs(directory.getAbsolutePath()).getAvailableBytes()<size+4L*1024*1024)
            throw new IllegalStateException("Not enough space for image");
        new File(directory,".incoming.part").delete();
        session=String.format(Locale.US,"%016x%016x",RANDOM.nextLong(),RANDOM.nextLong());
        sha256=digest;total=size;received=0;sequence=0;
        expires=SystemClock.elapsedRealtime()+600000L;
        return session;
    }

    private static void check(String id) {
        if(id==null||id.isEmpty()||!id.equals(session)||
            SystemClock.elapsedRealtime()>expires)
            throw new SecurityException("Invalid image session");
    }

    static synchronized int chunk(Context context,String id,int part,String payload) throws Exception {
        check(id);
        if(part!=sequence||payload==null||payload.length()>MAX_CHUNK*4/3+8||
            !payload.matches("[A-Za-z0-9+/]+={0,2}"))
            throw new IllegalArgumentException("Invalid image chunk");
        byte[] bytes=Base64.decode(payload,Base64.DEFAULT);
        if(bytes.length<1||bytes.length>MAX_CHUNK||received+bytes.length>total)
            throw new IllegalArgumentException("Image size mismatch");
        try(FileOutputStream output=new FileOutputStream(
            new File(PhotoLibrary.folder(context),".incoming.part"),true)) {
            output.write(bytes);
        }
        received+=bytes.length;
        sequence++;
        return received;
    }

    static synchronized String finish(Context context,String id) throws Exception {
        check(id);
        if(received!=total)throw new IllegalArgumentException("Incomplete photo");
        File source=new File(PhotoLibrary.folder(context),".incoming.part");
        session="";
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(FileInputStream input=new FileInputStream(source)) {
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1)sha.update(buffer,0,n);
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:sha.digest())hex.append(String.format(Locale.US,"%02x",b&255));
            if(!hex.toString().equals(sha256))
                throw new SecurityException("Photo SHA-256 mismatch");

            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(source.getAbsolutePath(),options);
            if(options.outWidth<1||options.outHeight<1||
                options.outWidth>16000||options.outHeight>16000)
                throw new IllegalArgumentException("Photo dimensions invalid");
            int sample=1;
            while(options.outWidth/sample>1280 || options.outHeight/sample>800)
                sample*=2;
            options.inJustDecodeBounds=false;
            options.inSampleSize=sample;
            options.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap bitmap=BitmapFactory.decodeFile(source.getAbsolutePath(),options);
            if(bitmap==null)throw new IllegalArgumentException("Cannot decode photo");
            try {
                for(int pass=0;pass<8;pass++) {
                    ByteArrayOutputStream jpeg=new ByteArrayOutputStream(96000);
                    bitmap.compress(Bitmap.CompressFormat.JPEG,Math.max(42,78-pass*5),jpeg);
                    if(jpeg.size()<=PhotoLibrary.MAX_JPEG)
                        return PhotoLibrary.addBytes(context,jpeg.toByteArray(),sha256);
                    int width=Math.max(1,Math.round(bitmap.getWidth()*.78f));
                    int height=Math.max(1,Math.round(bitmap.getHeight()*.78f));
                    Bitmap smaller=Bitmap.createScaledBitmap(bitmap,width,height,true);
                    if(smaller==bitmap)break;
                    bitmap.recycle();
                    bitmap=smaller;
                }
                throw new IllegalArgumentException("Photo cannot be compressed");
            } finally {bitmap.recycle();}
        } finally {source.delete();}
    }

    private PhotoImportSession() {}
}
