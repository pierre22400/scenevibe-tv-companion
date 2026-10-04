package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Characterizes independent identity, pairing, credential and cache lifetimes using the
 * current storage seams. Only synthetic tokens are used and assertions never print them.
 */
public final class M4PhaseAIdentityTest {
    /** Keep installation and pairing preferences distinct, like the actual application. */
    private static final class Device {
        final Map<String,String> installation=new HashMap<>(),pairing=new HashMap<>();
        final M4PhaseAFixtures.Credentials metadata=new M4PhaseAFixtures.Credentials();
        final SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        final CloudDeviceCredentials credentials=new CloudDeviceCredentials(metadata,secrets);
        final InstallationIdentity.Storage installationStorage=new InstallationIdentity.Storage() {
            /** Read the stable installation preference. */
            @Override public String get(String key) {return installation.get(key);}
            /** Persist only within the separate installation preference file. */
            @Override public void put(String key,String value) {installation.put(key,value);}
        };
        final PairingPolicy.Storage pairingStorage=new PairingPolicy.Storage() {
            /** Read a pairing preference without consulting cache state. */
            @Override public String get(String key) {return pairing.get(key);}
            /** Persist the independent pairing preference. */
            @Override public void put(String key,String value) {pairing.put(key,value);}
            /** Remove a pairing key only on an explicit pairing operation. */
            @Override public void remove(String key) {pairing.remove(key);}
        };

        /** Seed a qualified pre-existing device, never a first-run identity generation. */
        Device() {
            installation.put("installationId","qualified-installation-id");
            pairing.put("deviceId","qualified-lan-id");pairing.put("token","synthetic-pairing-token");
            assertTrue(credentials.persistActivation("qualified-cloud-id","synthetic-cloud-token",
                    "synthetic-activation-id","synthetic-activation-secret","123456"));
            assertTrue(credentials.confirmClaimed());
        }

        /** Recreate all policy wrappers and verify stable stores after cache transitions. */
        void assertPreserved(boolean connected) {
            InstallationIdentity identity=new InstallationIdentity(installationStorage,
                    ()->pairing.get("deviceId"),new SecureRandom());
            PairingPolicy policy=new PairingPolicy(()->0L,pairingStorage,new SecureRandom());
            CloudDeviceCredentials recreated=new CloudDeviceCredentials(metadata,secrets);
            assertTrue("installation id preserved","qualified-installation-id".equals(identity.installationId()));
            assertTrue("pairing id preserved","qualified-lan-id".equals(policy.deviceId()));
            assertEquals(PairingPolicy.Status.PAIRED,policy.status());
            assertTrue("pairing authorization preserved",policy.authorized("Bearer synthetic-pairing-token"));
            assertTrue("cloud identity preserved","qualified-cloud-id".equals(recreated.cloudDeviceId()));
            assertTrue("cloud secret preserved","synthetic-cloud-token".equals(recreated.deviceToken()));
            assertEquals(connected,recreated.connected());
            assertFalse(metadata.values.containsKey("deviceToken"));
            assertFalse(metadata.values.containsKey("activationSecret"));
        }
    }

    /** Manifest restore, replacement, redelivery and process recreation cannot alter device stores. */
    @Test public void installationAndProcessRecreationPreserveIdentityPairingAndCredentials() throws Exception {
        Device device=new Device();
        Map<String,String> installation=new HashMap<>(device.installation),pairing=new HashMap<>(device.pairing);
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        assertEquals(13,runtime.restore());
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,14)));
        assertTrue(runtime.apply(M4PhaseAFixtures.envelope(0,14)));
        assertTrue(runtime.cache.markAcknowledged(14));
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(runtime.memory);
        assertEquals(14,recreated.restore());assertEquals(14,recreated.cache.acknowledged());
        device.assertPreserved(true);
        assertTrue("installation preference unchanged",installation.equals(device.installation));
        assertTrue("pairing preferences unchanged",pairing.equals(device.pairing));
    }

    /** Cache corruption clears its own tuple and cannot reset installation or either pairing. */
    @Test public void corruptTrackCacheDoesNotResetIdentityPairingOrCredentials() throws Exception {
        Device device=new Device();
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        memory.values.put("manifest","{invalid-json");
        assertEquals(0,new M4PhaseAFixtures.Runtime(memory).restore());
        assertTrue(memory.values.isEmpty());assertEquals(1,memory.clears);
        device.assertPreserved(true);
    }

    /** Disconnect is an explicit credential operation that still preserves identity and package cache. */
    @Test public void disconnectPreservesPairingAndRestorableManifestedCache() throws Exception {
        Device device=new Device();
        M4PhaseAFixtures.Memory memory=M4PhaseAFixtures.cacheFixture(true);
        Map<String,String> before=new HashMap<>(memory.values);
        device.credentials.disconnect();
        device.assertPreserved(false);
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(memory);
        assertEquals(13,recreated.restore());assertEquals(13,recreated.cache.acknowledged());
        assertTrue("disconnect leaves cache bytes",before.equals(memory.values));
        assertFalse(recreated.controller.hasVisibleScene());
        recreated.due();assertEquals(1,recreated.sink.shows);
    }
}
