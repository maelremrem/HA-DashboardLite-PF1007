package dev.maelab.haframe;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.security.MessageDigest;

import org.json.JSONObject;

import java.util.Locale;

/** Safe, read-only device telemetry exposed to the paired Home Assistant integration. */
final class DeviceDiagnostics {
    private DeviceDiagnostics() {}

    static JSONObject snapshot(Context context) throws Exception {
        JSONObject data = new JSONObject();
        data.put("version", packageVersion(context));
        data.put("signing_cert_sha256", signingCertificate(context));
        data.put("android_version", Build.VERSION.RELEASE);
        data.put("uptime_seconds", SystemClock.elapsedRealtime() / 1000L);
        data.put("wifi_connected", false);
        data.put("wifi_ssid", JSONObject.NULL);
        data.put("wifi_ip", JSONObject.NULL);
        data.put("wifi_rssi_dbm", JSONObject.NULL);
        data.put("wifi_quality_percent", JSONObject.NULL);
        data.put("wifi_link_speed_mbps", JSONObject.NULL);
        data.put("wifi_frequency_mhz", JSONObject.NULL);

        try {
            WifiManager manager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (manager == null || !manager.isWifiEnabled()) return data;

            WifiInfo info = manager.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return data;
            data.put("wifi_connected", true);

            String ssid = info.getSSID();
            if (ssid != null && !ssid.isEmpty() && !ssid.toLowerCase(Locale.US).contains("unknown ssid")) {
                if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                data.put("wifi_ssid", ssid);
            }

            int ip = info.getIpAddress();
            if (ip != 0) {
                data.put("wifi_ip", String.format(Locale.US, "%d.%d.%d.%d",
                    ip & 255, (ip >> 8) & 255, (ip >> 16) & 255, (ip >> 24) & 255));
            }

            int rssi = info.getRssi();
            if (rssi > -127 && rssi < 0) {
                data.put("wifi_rssi_dbm", rssi);
                data.put("wifi_quality_percent", WifiManager.calculateSignalLevel(rssi, 101));
            }

            int speed = info.getLinkSpeed();
            if (speed > 0) data.put("wifi_link_speed_mbps", speed);

            int frequency = info.getFrequency();
            if (frequency > 0) data.put("wifi_frequency_mhz", frequency);
        } catch (SecurityException ignored) {
            // Some firmware revisions restrict Wi-Fi metadata.
        }
        return data;
    }

    private static String signingCertificate(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            if (info.signatures == null || info.signatures.length == 0) return "unknown";
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(info.signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 255));
            return hex.toString();
        } catch (Exception ignored) { return "unknown"; }
    }

    private static String packageVersion(Context context) {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
