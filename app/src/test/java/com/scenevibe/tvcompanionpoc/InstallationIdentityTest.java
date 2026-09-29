package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * JVM tests for the stable local installation identity: stable generation, stable
 * re-read, EXACT legacy PairingPolicy deviceId migration, no re-mint once a legacy value
 * exists, no IP/network derivation, and strict separation from cloudDeviceId + deviceToken.
 */
public final class InstallationIdentityTest {
    /** In-memory store faithfully reproducing the 'installation' SharedPreferences file. */
    private static final class Memory implements InstallationIdentity.Storage {
        final Map<String,String> values=new HashMap<>();
        @Override public String get(String key){return values.get(key);}
        @Override public void put(String key,String value){values.put(key,value);}
    }

    /** No legacy value present. */
    private static InstallationIdentity.LegacyDeviceId none() {return () -> null;}

    /** A deterministic non-random source so a generated id would be predictable if used. */
    private static SecureRandom fixedRandom() {
        return new SecureRandom() {
            @Override public void nextBytes(byte[] bytes) {
                for (int i=0;i<bytes.length;i++) bytes[i]=(byte)(i+1);
            }
        };
    }

    /** With no stored value and no legacy value, a strong id is generated and persisted. */
    @Test public void generatesAndPersistsWhenEmpty() {
        Memory memory=new Memory();
        InstallationIdentity identity=new InstallationIdentity(memory,none(),new SecureRandom());
        String id=identity.installationId();
        assertNotNull(id);
        assertFalse(id.isEmpty());
        assertEquals(id,memory.values.get(InstallationIdentity.KEY_INSTALLATION_ID));
    }

    /** Re-reading the same store/instance returns the identical value (stable re-read). */
    @Test public void stableReReadAcrossInstances() {
        Memory memory=new Memory();
        String first=new InstallationIdentity(memory,none(),new SecureRandom()).installationId();
        String second=new InstallationIdentity(memory,none(),new SecureRandom()).installationId();
        assertEquals(first,second);
        assertEquals(first,new InstallationIdentity(memory,none(),new SecureRandom()).installationId());
    }

    /** A legacy PairingPolicy deviceId is migrated EXACTLY, byte for byte, into the new store. */
    @Test public void migratesLegacyDeviceIdExactly() {
        Memory memory=new Memory();
        String legacyValue="AbCd_12-legacyDeviceIdValue";
        InstallationIdentity identity=
                new InstallationIdentity(memory,() -> legacyValue,fixedRandom());
        String id=identity.installationId();
        assertEquals(legacyValue,id);
        assertEquals(legacyValue,memory.values.get(InstallationIdentity.KEY_INSTALLATION_ID));
    }

    /** When a legacy value exists no NEW identity is minted: the generator output is never used. */
    @Test public void noNewIdentityMintedWhenLegacyExists() {
        Memory memory=new Memory();
        String legacyValue="existing-0-7-1-install-id";
        // fixedRandom would produce a deterministic non-legacy value if generation ran.
        InstallationIdentity identity=
                new InstallationIdentity(memory,() -> legacyValue,fixedRandom());
        assertEquals(legacyValue,identity.installationId());
        // A stored new value takes precedence over the legacy value on subsequent reads,
        // and the migrated value is exactly the legacy value (never regenerated).
        assertEquals(legacyValue,new InstallationIdentity(memory,() -> "different-legacy",
                new SecureRandom()).installationId());
    }

    /** An existing 0.7.1 install (legacy value already migrated) never changes on update. */
    @Test public void existingInstallDoesNotChangeOnUpdate() {
        Memory memory=new Memory();
        String legacyValue="stable-0-7-1-id";
        String migrated=new InstallationIdentity(memory,() -> legacyValue,new SecureRandom()).installationId();
        // Simulate an app update: legacy source may even be gone, stored value must persist.
        String afterUpdate=new InstallationIdentity(memory,none(),new SecureRandom()).installationId();
        assertEquals(legacyValue,migrated);
        assertEquals(migrated,afterUpdate);
    }

    /**
     * The identity is never derived from any IP-like input: the class exposes no way to
     * pass network/device data, and the id depends only on the injected random source or
     * legacy value. Two independent empty stores yield different random ids (proving the
     * id is not a deterministic function of any host attribute).
     */
    @Test public void notDerivedFromIpOrHostAttributes() {
        String a=new InstallationIdentity(new Memory(),none(),new SecureRandom()).installationId();
        String b=new InstallationIdentity(new Memory(),none(),new SecureRandom()).installationId();
        assertNotEquals("Random ids from independent stores must differ",a,b);
    }

    /**
     * Separation from cloudDeviceId and deviceToken: the installation store only ever
     * holds the installationId key, and the id differs from sample cloud values.
     */
    @Test public void separatedFromCloudDeviceIdAndDeviceToken() {
        Memory memory=new Memory();
        String id=new InstallationIdentity(memory,none(),new SecureRandom()).installationId();
        assertEquals(1,memory.values.size());
        assertTrue(memory.values.containsKey(InstallationIdentity.KEY_INSTALLATION_ID));
        assertNull(memory.values.get("cloudDeviceId"));
        assertNull(memory.values.get("deviceToken"));
        assertNotEquals("cloud-uuid",id);
        assertNotEquals("device-token",id);
    }
}
