package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The encryption boundary for the two durable cloud SECRETS (the cloud deviceToken and
 * the temporary activationSecret). Everything routed through a SecretStore is encrypted
 * at rest with a symmetric key that never leaves the platform keystore; the ciphertext
 * and its per-record IV are the only values that touch app-private SharedPreferences.
 *
 * <p>The production {@link AndroidKeyStoreSecretStore} uses {@code AES/GCM/NoPadding}
 * with a {@link KeyGenParameterSpec} key generated inside the AndroidKeyStore. The key is
 * non-exportable and requires NO biometric and NO user PIN, so the app can decrypt the
 * credential unattended after an unattended reboot (autostart). This class deliberately
 * does NOT use EncryptedSharedPreferences.
 *
 * <p>Fail-closed contract: on ANY keystore or cipher problem (missing key, corrupt
 * ciphertext, GCM tag mismatch, provider error) a read raises {@link Unavailable} rather
 * than returning plaintext, an empty value, or silently regenerating the key. Callers map
 * {@link Unavailable} to the {@code CREDENTIAL_UNAVAILABLE} diagnostic state; they must
 * never fall back to plaintext, never mint a new TV, and never auto-erase the identity.
 * No secret value is ever logged.
 */
interface SecretStore {
    /**
     * Signals that a secret could not be read or written through the encryption boundary.
     * It is the single fail-closed signal callers map to {@code CREDENTIAL_UNAVAILABLE};
     * its message never contains a secret value.
     */
    final class Unavailable extends RuntimeException {
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Encrypts {@code plaintext} and persists the ciphertext + IV under {@code name},
     * returning true only when the write is durable. Throws {@link Unavailable} when the
     * keystore/cipher cannot encrypt; a false/throw MUST leave any prior value untouched.
     */
    boolean put(String name, String plaintext);

    /**
     * Returns the decrypted secret for {@code name}, or null when nothing is stored.
     * Throws {@link Unavailable} (never returns plaintext) when a stored record cannot be
     * decrypted, i.e. keystore/cipher corruption.
     */
    String get(String name);

    /**
     * Removes the ciphertext + IV for {@code name}, returning true only when the removal is
     * durably committed. Never touches non-secret keys. A false return means the ciphertext
     * may still be present, so callers that depend on a critical secret being gone (reset,
     * claim transition) must treat false as a fail-closed condition rather than assuming
     * success.
     */
    boolean remove(String name);

    /** True when a ciphertext record exists for {@code name} regardless of decryptability. */
    boolean contains(String name);

    /**
     * The production SecretStore. Keeps a single AndroidKeyStore AES-256/GCM key whose
     * material never leaves the keystore, and stores each secret as base64 ciphertext plus
     * a separate base64 IV in an app-private SharedPreferences file. No user authentication
     * is required so the credential is readable on unattended boot.
     */
    final class AndroidKeyStoreSecretStore implements SecretStore {
        private static final String PROVIDER = "AndroidKeyStore";
        private static final String KEY_ALIAS = "scenevibe.cloud.secret.v1";
        private static final String TRANSFORMATION = "AES/GCM/NoPadding";
        private static final int GCM_TAG_BITS = 128;
        private static final String CIPHERTEXT_SUFFIX = ".ct";
        private static final String IV_SUFFIX = ".iv";
        private final SharedPreferences prefs;

        /** Uses a dedicated app-private file so cipher records never collide with plaintext keys. */
        AndroidKeyStoreSecretStore(Context context) {
            this.prefs = context.getApplicationContext()
                    .getSharedPreferences("cloud_secret", Context.MODE_PRIVATE);
        }

        @Override public boolean put(String name, String plaintext) {
            if (plaintext == null) throw new IllegalArgumentException("null secret");
            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, key());
                byte[] iv = cipher.getIV();
                byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
                Base64.Encoder encoder = Base64.getEncoder();
                // GCM never reuses an IV for a given key, so a fresh IV is written per record.
                return prefs.edit()
                        .putString(name + CIPHERTEXT_SUFFIX, encoder.encodeToString(ciphertext))
                        .putString(name + IV_SUFFIX, encoder.encodeToString(iv))
                        .commit();
            } catch (Exception error) {
                // Fail closed: surface unavailability without leaking the plaintext.
                throw new Unavailable("Secret could not be encrypted", error);
            }
        }

        @Override public String get(String name) {
            String ciphertext = prefs.getString(name + CIPHERTEXT_SUFFIX, null);
            String iv = prefs.getString(name + IV_SUFFIX, null);
            if (ciphertext == null || iv == null) return null;
            try {
                Base64.Decoder decoder = Base64.getDecoder();
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key(),
                        new GCMParameterSpec(GCM_TAG_BITS, decoder.decode(iv)));
                byte[] plaintext = cipher.doFinal(decoder.decode(ciphertext));
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (Exception error) {
                // Corruption / GCM tag mismatch / missing key: fail closed, never plaintext.
                throw new Unavailable("Secret could not be decrypted", error);
            }
        }

        @Override public boolean remove(String name) {
            return prefs.edit().remove(name + CIPHERTEXT_SUFFIX).remove(name + IV_SUFFIX).commit();
        }

        @Override public boolean contains(String name) {
            return prefs.contains(name + CIPHERTEXT_SUFFIX);
        }

        /** Loads the non-exportable AES key, generating it once on first use inside the keystore. */
        private SecretKey key() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER);
            keyStore.load(null);
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry)
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // No setUserAuthenticationRequired: unattended autostart must be able to
                    // decrypt the credential without biometric or PIN.
                    .build());
            return generator.generateKey();
        }
    }

    /**
     * In-memory SecretStore for pure-JVM tests. It reproduces the durability boolean and
     * the fail-closed contract without an Android runtime: callers can arm a write failure
     * (put returns false, prior value untouched) or corruption (get throws Unavailable).
     */
    final class InMemorySecretStore implements SecretStore {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();
        private final java.util.Set<String> corrupt = new java.util.HashSet<>();
        private final java.util.Set<String> writeFailNames = new java.util.HashSet<>();
        private final java.util.Set<String> removeFailNames = new java.util.HashSet<>();
        private boolean writesFail;
        private boolean removesFail;
        private int failWriteAfter = -1; // -1 disables; otherwise fail the Nth+1 write.
        private int writeCount;

        /** Simulates a keystore/commit failure: every subsequent put returns false. */
        void failWrites(boolean fail) { this.writesFail = fail; }

        /** Fails only the put for the named record; other records still write durably. */
        void failWrite(String name) { writeFailNames.add(name); }

        /**
         * Fails the (index)th put counted from now (0-based). Used to inject a
         * second-secret-write failure: {@code failWriteAtIndex(1)} lets the first put
         * succeed and makes the second return false with the prior value untouched.
         */
        void failWriteAtIndex(int index) { this.failWriteAfter = index; this.writeCount = 0; }

        /** Simulates a non-durable removal commit: every subsequent remove returns false. */
        void failRemoves(boolean fail) { this.removesFail = fail; }

        /** Fails only the remove for the named record; other removes still commit durably. */
        void failRemove(String name) { removeFailNames.add(name); }

        /** Marks a stored record as unreadable so get() fails closed like GCM corruption. */
        void corrupt(String name) { corrupt.add(name); }

        @Override public boolean put(String name, String plaintext) {
            if (plaintext == null) throw new IllegalArgumentException("null secret");
            boolean failThis = writesFail || writeFailNames.contains(name)
                    || (failWriteAfter >= 0 && writeCount == failWriteAfter);
            writeCount++;
            if (failThis) return false; // Prior value is deliberately left untouched.
            values.put(name, plaintext);
            corrupt.remove(name);
            return true;
        }

        @Override public String get(String name) {
            if (corrupt.contains(name)) throw new Unavailable("Secret could not be decrypted");
            return values.get(name);
        }

        @Override public boolean remove(String name) {
            if (removesFail || removeFailNames.contains(name)) return false; // Prior value untouched.
            values.remove(name);
            corrupt.remove(name);
            return true;
        }

        @Override public boolean contains(String name) {
            return values.containsKey(name) || corrupt.contains(name);
        }
    }
}
