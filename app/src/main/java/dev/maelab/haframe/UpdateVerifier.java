package dev.maelab.haframe;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Local-only, explicit APK installation after signature and version verification. */
final class UpdateVerifier {
    static final class Candidate {
        final File file;
        final String version;
        final String sha256;

        Candidate(File file, String version, String sha256) {
            this.file=file;
            this.version=version;
            this.sha256=sha256;
        }
    }

    static File directory(Context context) throws Exception {
        File base=context.getExternalFilesDir(null);
        if (base==null) throw new IllegalStateException("External app storage unavailable");
        File folder=new File(base,"updates");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create updates directory");
        return folder;
    }

    static Candidate verify(Context context) throws Exception {
        File apk=new File(directory(context),"candidate.apk");
        if (!apk.isFile() || apk.length()<1024 || apk.length()>48L*1024*1024) {
            throw new IllegalArgumentException("Missing or oversized candidate.apk");
        }
        PackageManager pm=context.getPackageManager();
        PackageInfo current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        PackageInfo update=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if (update==null || !context.getPackageName().equals(update.packageName) ||
            update.versionCode<=current.versionCode ||
            update.signatures==null || current.signatures==null ||
            update.signatures.length!=1 || current.signatures.length!=1 ||
            !Arrays.equals(update.signatures[0].toByteArray(),current.signatures[0].toByteArray())) {
            throw new SecurityException("APK package/version/signature mismatch");
        }
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try (FileInputStream input=new FileInputStream(apk)) {
            byte[] buffer=new byte[8192];int read;
            while ((read=input.read(buffer))>0) hash.update(buffer,0,read);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:hash.digest()) hex.append(String.format(Locale.US,"%02x",b&255));
        return new Candidate(apk,update.versionName,hex.toString());
    }

    static void launchInstaller(Activity activity,Candidate candidate) {
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",candidate.file);
        Intent install=new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri,"application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
    }

    private UpdateVerifier() {}
}
