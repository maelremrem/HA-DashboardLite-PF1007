package dev.maelab.haframe;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated image library with no paths or external URLs accepted from callers. */
final class PhotoLibrary {
    static final int MAX_PHOTOS = 100;
    static final int MAX_JPEG = 96000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static File folder(Context context) throws Exception {
        File pictures=context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if(pictures==null)throw new IllegalStateException("Pictures directory unavailable");
        File directory=new File(pictures,"HAFrame");
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IllegalStateException("Cannot create photo library");
        return directory;
    }

    static synchronized JSONArray list(Context context) throws Exception {
        JSONArray list=new JSONArray();
        File[] files=folder(context).listFiles();
        if(files==null)return list;
        java.util.Arrays.sort(files,new java.util.Comparator<File>() {
            @Override public int compare(File a,File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for(File file:files) {
            if(!file.isFile()||!file.getName().matches("photo_[0-9a-f]{16}\\.jpg"))continue;
            list.put(new JSONObject().put("id",file.getName()).put("bytes",file.length()));
            if(list.length()>=MAX_PHOTOS)break;
        }
        return list;
    }

    static synchronized String add(Context context,String base64) throws Exception {
        if(base64==null||base64.length()>MAX_JPEG*4/3+8||
            !base64.matches("[A-Za-z0-9+/]+={0,2}"))
            throw new IllegalArgumentException("Invalid JPEG payload");
        if(list(context).length()>=MAX_PHOTOS)
            throw new IllegalArgumentException("Photo library full");
        byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
        if(bytes.length<20||bytes.length>MAX_JPEG||
            bytes[0]!=(byte)0xFF||bytes[1]!=(byte)0xD8||
            bytes[bytes.length-2]!=(byte)0xFF||bytes[bytes.length-1]!=(byte)0xD9)
            throw new IllegalArgumentException("Invalid JPEG");
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||
            options.outWidth>2048||options.outHeight>2048)
            throw new IllegalArgumentException("JPEG dimensions out of bounds");
        String id=String.format(Locale.US,"photo_%016x.jpg",RANDOM.nextLong());
        File destination=new File(folder(context),id);
        File temporary=new File(folder(context),id+".tmp");
        try(FileOutputStream out=new FileOutputStream(temporary)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if(!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("Photo save failed");
        }
        return id;
    }

    static synchronized boolean delete(Context context,String id) throws Exception {
        if(id==null||!id.matches("photo_[0-9a-f]{16}\\.jpg"))return false;
        File directory=folder(context);
        File file=new File(directory,id);
        if(!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))
            return false;
        return file.exists()&&file.delete();
    }

    private PhotoLibrary() {}
}
