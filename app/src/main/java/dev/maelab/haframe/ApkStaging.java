package dev.maelab.haframe;

import android.content.Context;
import android.os.SystemClock;
import android.os.StatFs;
import android.util.Base64;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** A single expiring, sequential APK transfer; verifies SHA256 and installed signer. */
final class ApkStaging {
    private static final int MAX_BYTES = 48 * 1024 * 1024;
    private static final int MAX_CHUNK = 60000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String session = "";
    private static String expectedHash = "";
    private static long expires;
    private static int total;
    private static int received;
    private static int sequence;

    static synchronized String begin(Context context,int bytes,String sha256) throws Exception {
        if (bytes < 1024 || bytes > MAX_BYTES ||
            sha256 == null || !sha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid update request");
        File directory = UpdateVerifier.directory(context);
        StatFs fs=new StatFs(directory.getAbsolutePath());
        if (fs.getAvailableBytes() < bytes + 12L*1024*1024)
            throw new IllegalStateException("Not enough free storage");
        new File(directory,"candidate.apk.part").delete();
        session=String.format(Locale.US,"%016x%016x",RANDOM.nextLong(),RANDOM.nextLong());
        expectedHash=sha256;
        total=bytes;
        received=0;
        sequence=0;
        expires=SystemClock.elapsedRealtime()+30*60*1000L;
        return session;
    }

    private static void check(String id) {
        if (!session.equals(id) || id.isEmpty() ||
            SystemClock.elapsedRealtime()>expires)
            throw new SecurityException("Expired APK transfer");
    }

    static synchronized int chunk(Context context,String id,int part,String encoded)
        throws Exception {
        check(id);
        if (part!=sequence || encoded==null || encoded.length()>MAX_CHUNK*4/3+8 ||
            !encoded.matches("[A-Za-z0-9+/]+={0,2}"))
            throw new IllegalArgumentException("Invalid APK chunk");
        byte[] decoded=Base64.decode(encoded,Base64.DEFAULT);
        if (decoded.length<1 || decoded.length>MAX_CHUNK || received+decoded.length>total)
            throw new IllegalArgumentException("APK transfer size invalid");
        File file=new File(UpdateVerifier.directory(context),"candidate.apk.part");
        try(FileOutputStream out=new FileOutputStream(file,true)) {
            out.write(decoded);
        }
        received+=decoded.length;
        sequence++;
        return received;
    }

    static synchronized UpdateVerifier.Candidate finish(Context context,String id)
        throws Exception {
        check(id);
        if (received!=total) throw new IllegalArgumentException("Incomplete APK transfer");
        File directory=UpdateVerifier.directory(context);
        File staged=new File(directory,"candidate.apk.part");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(FileInputStream input=new FileInputStream(staged)) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1) sha.update(buffer,0,n);
        }
        StringBuilder result=new StringBuilder();
        for(byte b:sha.digest())result.append(String.format(Locale.US,"%02x",b&255));
        if(!result.toString().equals(expectedHash)) {
            staged.delete();
            session="";
            throw new SecurityException("APK checksum mismatch");
        }
        File dest=new File(directory,"candidate.apk");
        if(dest.exists()&&!dest.delete())throw new IllegalStateException("Old update locked");
        if(!staged.renameTo(dest))throw new IllegalStateException("Cannot stage APK");
        session="";
        try {
            return UpdateVerifier.verify(context);
        } catch(Exception rejected) {
            dest.delete();
            throw rejected;
        }
    }

    private ApkStaging() {}
}
