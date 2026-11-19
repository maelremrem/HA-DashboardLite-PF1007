package dev.maelab.haframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class TokenVault {
    private static final String ALIAS = "ha_frame_token_v1";
    private static final String PREFS = "secrets";

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }

    static boolean hasToken(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("token");
    }

    static void save(Context context, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String result = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(bytes, Base64.NO_WRAP);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("token", result).apply();
    }

    static String load(Context context) throws Exception {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = p.getString("token", "");
        if (stored.isEmpty()) return "";
        String[] chunks = stored.split(":", 2);
        if (chunks.length != 2) throw new IllegalStateException("Token illisible");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
            new GCMParameterSpec(128, Base64.decode(chunks[0], Base64.NO_WRAP)));
        byte[] plain = cipher.doFinal(Base64.decode(chunks[1], Base64.NO_WRAP));
        return new String(plain, StandardCharsets.UTF_8);
    }
}
