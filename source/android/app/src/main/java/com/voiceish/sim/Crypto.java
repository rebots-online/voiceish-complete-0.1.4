package com.voiceish.sim;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** The byte-level protocol is also runnable on a desktop JVM for interoperability tests. */
public final class Crypto {
    private Crypto() { }
    public static byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext, String direction) throws Exception {
        return transform(Cipher.ENCRYPT_MODE, key, iv, plaintext, direction);
    }
    public static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, String direction) throws Exception {
        return transform(Cipher.DECRYPT_MODE, key, iv, ciphertext, direction);
    }
    private static byte[] transform(int mode, byte[] key, byte[] iv, byte[] input, String direction) throws Exception {
        if (key.length != 32 || iv.length != 12 || !("request".equals(direction) || "response".equals(direction)))
            throw new IllegalArgumentException("Invalid cryptographic parameters");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(("voiceish-sim:v1:" + direction).getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(input);
    }
}
