package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Actual Android SecretStore AES/GCM/prefs path with a synthetic JCA KeyStore provider.
 * Key generation counts prove read failure cannot regenerate a key. Hardware-backed
 * AndroidKeyStore/lockscreen behavior remains a separate physical qualification.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public final class TvFirst008IdentityTest {
    private Context context;
    /** Bind an isolated synthetic provider; no owner key or real credential is accessed. */
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication(); FakeKeys.values.clear(); FakeGenerator.generated = 0;
        Security.removeProvider("AndroidKeyStore");
        Security.addProvider(new Provider("AndroidKeyStore", 1.0, "Synthetic qualification provider") {
            { put("KeyStore.AndroidKeyStore", FakeKeys.class.getName()); put("KeyGenerator.AES", FakeGenerator.class.getName()); }
        });
        context.getSharedPreferences("cloud_secret", 0).edit().clear().commit();
        context.getSharedPreferences("tvfirst008_identity", 0).edit().clear().commit();
    }
    /** Avoid leaking the fake provider across independent JVM cases. */
    @After public void cleanup() { Security.removeProvider("AndroidKeyStore"); }
    /** Actual cipher bytes are encrypted, survive recreated state, and never enter metadata prefs. */
    @Test public void encryptedChallengeAndClaimSurviveRestartWithoutLegacyMigration() throws Exception {
        context.getSharedPreferences("cloud_identity", 0).edit().putString("deviceToken", "historical-sentinel").commit();
        TvFirst008State state = new TvFirst008State(context);
        JSONObject challenge = state.challenge("recover"); String secret = state.challengeSecret(challenge);
        assertEquals(1, FakeGenerator.generated);
        assertFalse(context.getSharedPreferences("cloud_secret", 0).getAll().toString().contains(secret));
        assertFalse(context.getSharedPreferences("tvfirst008_identity", 0).getAll().toString().contains(secret));
        TvFirst008State restarted = new TvFirst008State(context);
        assertEquals(challenge.toString(), restarted.challenge("recover").toString());
        assertEquals(secret, restarted.challengeSecret(challenge));
        JSONObject claimed = claim(); state.claimed(claimed);
        assertEquals(claimed.getString("deviceToken"), new TvFirst008State(context).token(state.read("identity")));
        assertEquals("historical-sentinel", context.getSharedPreferences("cloud_identity", 0).getString("deviceToken", null));
        assertEquals(1, FakeGenerator.generated); assertNull(state.read("challenge"));
    }
    /** Missing key, partial ciphertext and GCM authentication failure never produce a replacement key. */
    @Test public void actualSecretReadsFailClosedWithoutKeyGeneration() {
        SecretStore store = new SecretStore.AndroidKeyStoreSecretStore(context);
        assertNull(store.get("absent")); assertEquals(0, FakeGenerator.generated);
        assertTrue(store.put("test", "synthetic-credential")); assertEquals(1, FakeGenerator.generated);
        FakeKeys.values.clear();
        assertThrows(SecretStore.Unavailable.class, () -> store.get("test")); assertEquals(1, FakeGenerator.generated);
        context.getSharedPreferences("cloud_secret", 0).edit().putString("partial.ct", "bad").commit();
        assertThrows(SecretStore.Unavailable.class, () -> store.get("partial")); assertEquals(1, FakeGenerator.generated);
        assertTrue(store.put("another", "value")); int generated = FakeGenerator.generated;
        context.getSharedPreferences("cloud_secret", 0).edit().putString("another.ct", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==").commit();
        assertThrows(SecretStore.Unavailable.class, () -> store.get("another")); assertEquals(generated, FakeGenerator.generated);
    }
    /** Network retries reuse intention id and precondition instead of allocating a fresh revision. */
    @Test public void pendingIntentionIsDurableAndCannotSilentlySwitchTracks() throws Exception {
        TvFirst008State state = new TvFirst008State(context); String track = UUID.randomUUID().toString(), generation = UUID.randomUUID().toString();
        JSONObject first = state.pending(track, 12, generation);
        assertEquals(first.toString(), new TvFirst008State(context).pending(track, 99, generation).toString());
        assertThrows(IllegalStateException.class, () -> state.pending(UUID.randomUUID().toString(), 12, generation));
        assertEquals(12, first.getLong("expectedRevision"));
    }
    /** A failed metadata commit cannot publish authority even if Android changed its in-memory map. */
    @Test public void failedCommitMasksSubsequentReadsAndNoIdentityIsAssumed() throws Exception {
        SharedPreferences real = context.getSharedPreferences("tvfirst008_identity", 0);
        SharedPreferences failure = (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
            if (!method.getName().equals("edit")) return method.invoke(real, args);
            SharedPreferences.Editor editor = real.edit();
            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (p, m, a) -> {
                if (m.getName().equals("commit")) { editor.commit(); return false; }
                Object result = m.invoke(editor, a); return result == editor ? p : result;
            });
        });
        TvFirst008State state = new TvFirst008State(failure, new SecretStore.AndroidKeyStoreSecretStore(context));
        assertThrows(SecretStore.Unavailable.class, () -> state.write("identity", new JSONObject()));
        assertThrows(SecretStore.Unavailable.class, () -> state.read("identity"));
        assertThrows(SecretStore.Unavailable.class, () -> new TvFirst008State(failure, new SecretStore.AndroidKeyStoreSecretStore(context)).read("identity"));
    }
    /** Build only a synthetic claimed result, never a real account credential. */
    private static JSONObject claim() throws Exception {
        return new JSONObject().put("profile", TvFirst008Wire.PROFILE).put("deviceId", UUID.randomUUID().toString())
                .put("generation", UUID.randomUUID().toString()).put("deviceToken", "a".repeat(43));
    }

    /** Minimal JCA storage, preserving the actual production KeyStore API calls. */
    public static final class FakeKeys extends KeyStoreSpi {
        static final Map<String,Key> values = new HashMap<>();
        /** JCA reflection requires a public empty constructor. */
        public FakeKeys() {}
        /** AndroidKeyStore accepts null protection for unattended secret keys. */
        @Override public KeyStore.Entry engineGetEntry(String alias, KeyStore.ProtectionParameter protection) {
            Key key = values.get(alias); return key == null ? null : new KeyStore.SecretKeyEntry((SecretKey) key);
        }
        /** Return only synthetic test keys. */
        @Override public Key engineGetKey(String alias, char[] password) { return values.get(alias); }
        /** Secret keys have no certificate chain. */
        @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
        /** Secret keys have no certificate. */
        @Override public Certificate engineGetCertificate(String alias) { return null; }
        /** Return a bounded synthetic timestamp. */
        @Override public Date engineGetCreationDate(String alias) { return new Date(0); }
        /** Store only test key material. */
        @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) { values.put(alias, key); }
        /** Encoded private keys are outside this test. */
        @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) { throw new UnsupportedOperationException(); }
        /** Certificates are outside this test. */
        @Override public void engineSetCertificateEntry(String alias, Certificate certificate) { throw new UnsupportedOperationException(); }
        /** Simulate key loss explicitly. */
        @Override public void engineDeleteEntry(String alias) { values.remove(alias); }
        /** Enumerate synthetic aliases only. */
        @Override public Enumeration<String> engineAliases() { return Collections.enumeration(values.keySet()); }
        /** Preserve absence semantics. */
        @Override public boolean engineContainsAlias(String alias) { return values.containsKey(alias); }
        /** Return current synthetic entry count. */
        @Override public int engineSize() { return values.size(); }
        /** Every present entry is a secret key. */
        @Override public boolean engineIsKeyEntry(String alias) { return values.containsKey(alias); }
        /** No certificate-only entry exists. */
        @Override public boolean engineIsCertificateEntry(String alias) { return false; }
        /** No certificate alias exists. */
        @Override public String engineGetCertificateAlias(Certificate cert) { return null; }
        /** Persistence belongs to the production encrypted prefs, not the synthetic provider. */
        @Override public void engineStore(OutputStream output, char[] password) {}
        /** Loading cannot generate keys. */
        @Override public void engineLoad(InputStream input, char[] password) {}
    }
    /** Count actual production KeyGenerator calls while supplying a valid AES/GCM test key. */
    public static final class FakeGenerator extends KeyGeneratorSpi {
        static int generated;
        /** JCA reflection requires a public empty constructor. */
        public FakeGenerator() {}
        /** Retain no caller randomness. */
        @Override protected void engineInit(SecureRandom random) {}
        /** Android key policy is outside this logical provider test. */
        @Override protected void engineInit(AlgorithmParameterSpec spec, SecureRandom random) {}
        /** All generated fixture keys use AES256. */
        @Override protected void engineInit(int size, SecureRandom random) {}
        /** Generate a real software AES key and save it under the production alias. */
        @Override protected SecretKey engineGenerateKey() {
            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES", "SunJCE"); generator.init(256);
                SecretKey key = generator.generateKey(); generated++; FakeKeys.values.put("scenevibe.cloud.secret.v1", key); return key;
            } catch (Exception failed) { throw new IllegalStateException("SYNTHETIC_KEY_FAILURE"); }
        }
    }
}
