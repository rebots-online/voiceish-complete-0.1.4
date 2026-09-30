package com.voiceish.sim;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class KeyStoreHelper {
    private static final String ALIAS = "voiceish-sim-pairing-wrap-v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static SecretKey wrappingKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    static synchronized byte[] get(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE);
        String encrypted = prefs.getString("ciphertext", null);
        if (encrypted == null) return rotate(context);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.DEFAULT)));
        byte[] key = cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT));
        if (key.length != 32) throw new IllegalStateException("Invalid pairing key");
        return key;
    }
    static synchronized byte[] rotate(Context context) throws Exception {
        byte[] key = new byte[32]; RANDOM.nextBytes(key);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, wrappingKey());
        byte[] ciphertext = cipher.doFinal(key);
        boolean stored = context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit()
            .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP)).commit();
        if (!stored) throw new IllegalStateException("Unable to save pairing key");
        return key;
    }
    static String base64(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
}
