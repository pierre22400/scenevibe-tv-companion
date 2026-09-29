package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * JVM tests for the two distinct cloud operations (user section 14 / 18.E / 18.F):
 *
 * <ul>
 *   <li><b>Disconnect Cloud</b> (normal UI): stops local cloud use and clears the activation
 *       temporaries, but KEEPS the durable deviceToken + cloudDeviceId, the local
 *       InstallationIdentity and the FinalTrack cache (revision + ACK).</li>
 *   <li><b>Reset SceneVibe Cloud connection</b> (Diagnostics-only, exceptional): deletes
 *       deviceToken, cloudDeviceId, activationId, activationSecret, userCode AND the cache,
 *       and sets cloud state to disconnected - but MUST leave the InstallationIdentity
 *       identical (same value before and after).</li>
 * </ul>
 *
 * All boundaries are the injectable Storage / SecretStore seams, so the whole thing runs on
 * the pure JVM with in-memory fakes and no Android runtime.
 */
public final class CloudResetTest {

    /** In-memory non-secret cloud_identity store; secrets live in the InMemorySecretStore. */
    private static final class CloudMemory implements CloudDeviceCredentials.Storage {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Boolean> flags=new HashMap<>();
        @Override public String getString(String key){return values.get(key);}
        @Override public boolean getFlag(String key){return flags.getOrDefault(key,false);}
        @Override public void putFlag(String key,boolean value){flags.put(key,value);}
        @Override public boolean persistActivation(String cloudDeviceId,String deviceToken,
                String activationId,String activationSecret,String userCode) {
            values.put("cloudDeviceId",cloudDeviceId);values.remove("deviceToken");
            values.put("activationId",activationId);values.remove("activationSecret");
            values.put("userCode",userCode);return true;
        }
        @Override public boolean confirmClaimed() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",true);return true;
        }
        @Override public void clearActivationTemporaries() {
            values.remove("activationId");values.remove("userCode");
        }
        @Override public void disconnect() {
            values.remove("activationId");values.remove("userCode");
            flags.put("connected",false);
        }
        @Override public void reset() {values.clear();flags.clear();}
        @Override public void removeLegacyPlaintext(String key) {values.remove(key);}
    }

    /** In-memory cloud_track cache store mirroring the production SharedPreferences batches. */
    private static final class TrackMemory implements CloudTrackRepository.Storage {
        final Map<String,String> values=new HashMap<>();
        @Override public String get(String key){return values.get(key);}
        @Override public boolean save(long revision,String json) {
            values.put("revision",String.valueOf(revision));values.put("runtime",json);return true;
        }
        @Override public boolean saveAck(long revision){values.put("ackRevision",String.valueOf(revision));return true;}
        @Override public void clear(){values.clear();}
    }

    /** In-memory 'installation' store; a Cloud reset must never touch this. */
    private static final class InstallMemory implements InstallationIdentity.Storage {
        final Map<String,String> values=new HashMap<>();
        @Override public String get(String key){return values.get(key);}
        @Override public void put(String key,String value){values.put(key,value);}
    }

    private static MediaSyncedTrackScheduler scheduler() {
        return new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            @Override public void onRender(ScheduledTrack.Event event) {}
            @Override public void onPlayback(boolean playing,boolean freeze) {}
        });
    }

    /** Listener that records the events it observes so a test can assert scheduler activity. */
    private static final class RecordingListener implements MediaSyncedTrackScheduler.Listener {
        int renders;
        int eligibleTrue;
        int eligibleFalse;
        String lastRenderedId;
        @Override public void onRender(ScheduledTrack.Event event) {renders++;lastRenderedId=event.id;}
        @Override public void onPlayback(boolean playing,boolean freeze) {}
        @Override public void onEligibility(boolean eligible) {
            if(eligible)eligibleTrue++;else eligibleFalse++;
        }
    }

    /**
     * A passive playback snapshot matching the cached track's exact media identity (same
     * targetPackage + prime_video platform + videoId). Constructor order mirrors
     * MediaSessionProbe.Snapshot: package, state, stateName, positionMs, estimatedPositionMs,
     * speed, updateAgeMs, mediaId, title, subtitle, durationMs.
     */
    private static MediaSessionProbe.Snapshot matchingSnapshot(long positionMs) {
        return new MediaSessionProbe.Snapshot(
                "com.amazon.amazonvideo.livingroom",
                android.media.session.PlaybackState.STATE_PLAYING,"PLAYING",
                positionMs,positionMs,1.0f,0L,
                "video-1","Columbo — Eaux troubles","",5884768L);
    }

    /** One text-only valid runtime track. */
    private static String track(String id) {
        return "{\"type\":\"scenevibe.track.v1\",\"trackId\":\""+id+"\",\"targetPackage\":\"com.amazon.amazonvideo.livingroom\",\"mediaIdentity\":{\"platform\":\"prime_video\",\"videoId\":\"video-1\",\"title\":\"Columbo — Eaux troubles\",\"durationMs\":5884768},\"pauseFreezesDisplay\":true,\"comments\":[{\"id\":\"c1\",\"text\":\"Hello\",\"startMs\":1000,\"durationMs\":6000}]}";
    }

    private static CloudDeviceCredentials credentials(CloudMemory memory,SecretStore secrets) {
        return new CloudDeviceCredentials(memory,secrets);
    }

    /** Disconnect keeps deviceToken + cloudDeviceId + InstallationIdentity + cache intact. */
    @Test public void disconnectKeepsCredentialsIdentityAndCache() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();

        InstallMemory install=new InstallMemory();
        String installationBefore=new InstallationIdentity(install,()->null,new SecureRandom()).installationId();

        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);
        assertTrue(cache.install(2,track("cached"),scheduler()));
        assertTrue(cache.markAcknowledged(2));

        identity.disconnect();

        // Durable cloud credential survives disconnect for later reconnection.
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertFalse(identity.connected());
        // Cache is untouched by disconnect.
        assertEquals(2,cache.revision());
        assertEquals(2,cache.acknowledged());
        assertEquals("cached",cache.cachedTrackId());
        // InstallationIdentity is unchanged.
        assertEquals(installationBefore,
                new InstallationIdentity(install,()->null,new SecureRandom()).installationId());
    }

    /**
     * Reset Cloud deletes deviceToken/cloudDeviceId/activationId/activationSecret/userCode and
     * the cache, sets state to disconnected, but leaves the InstallationIdentity IDENTICAL.
     */
    @Test public void resetDeletesCloudStateButKeepsInstallationIdentity() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        // Before reset every cloud value is present.
        assertEquals("device-token",identity.deviceToken());
        assertEquals("cloud-uuid",identity.cloudDeviceId());
        assertEquals("act-1",identity.activationId());
        assertEquals("secret",identity.activationSecret());
        assertEquals("123456",identity.userCode());

        InstallMemory install=new InstallMemory();
        String installationBefore=new InstallationIdentity(install,()->null,new SecureRandom()).installationId();
        assertNotNull(installationBefore);

        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);
        assertTrue(cache.install(2,track("cached"),scheduler()));
        assertTrue(cache.markAcknowledged(2));

        // The exceptional Reset: wipe both secrets + cloud identity + cache.
        identity.reset();
        cache.clear();

        // Every cloud value is gone and the state is disconnected.
        assertNull(identity.deviceToken());
        assertNull(identity.cloudDeviceId());
        assertNull(identity.activationId());
        assertNull(identity.activationSecret());
        assertNull(identity.userCode());
        assertFalse(identity.connected());
        assertFalse(identity.credentialUnavailable());
        // No secret ciphertext record remains in the SecretStore either.
        assertFalse(secrets.contains("deviceToken"));
        assertFalse(secrets.contains("activationSecret"));
        // The cache (revision + ACK + trackId) is gone.
        assertEquals(0,cache.revision());
        assertEquals(0,cache.acknowledged());
        assertNull(cache.cachedTrackId());

        // The InstallationIdentity store was never touched: same value, byte for byte.
        String installationAfter=new InstallationIdentity(install,()->null,new SecureRandom()).installationId();
        assertEquals("Reset Cloud MUST preserve the local InstallationIdentity",
                installationBefore,installationAfter);
        assertEquals(1,install.values.size());
        assertEquals(installationBefore,install.values.get(InstallationIdentity.KEY_INSTALLATION_ID));
    }

    /**
     * Correction 3: an already-loaded/active track must NOT stay active after the coordinated
     * reset. This loads a track into a real MediaSyncedTrackScheduler, drives it to an active
     * (eligible, rendering) state via a matching passive snapshot, then performs the reset
     * boundary (scheduler.clear() + identity.reset() + cache.clear()). After reset the same
     * matching snapshot produces NO further render or eligibility, proving the scheduler no
     * longer holds the track and the cache is empty. Uses only public scheduler API.
     */
    @Test public void loadedTrackIsNotActiveAfterReset() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();

        RecordingListener recorder=new RecordingListener();
        MediaSyncedTrackScheduler live=new MediaSyncedTrackScheduler(recorder);
        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);

        // Install and load the track into the live scheduler, then make it active: a matching
        // snapshot anchors the clock (positionMs beyond the first comment at 1000ms => render).
        assertTrue(cache.install(2,track("active"),live));
        live.onPlaybackSnapshot(matchingSnapshot(2000));
        assertTrue("track should be eligible while media matches",recorder.eligibleTrue>=1);
        assertTrue("the loaded comment should have rendered",recorder.renders>=1);
        assertEquals("c1",recorder.lastRenderedId);

        int rendersBeforeReset=recorder.renders;

        // The coordinated reset boundary (same work CloudControlClient.reset() runs on io):
        // clear the scheduler, wipe the identity, delete the cache.
        live.clear();
        identity.reset();
        cache.clear();

        // The cache is empty and no cloud credential remains.
        assertEquals(0,cache.revision());
        assertEquals(0,cache.acknowledged());
        assertNull(cache.cachedTrackId());
        assertNull(identity.deviceToken());

        // Feeding the SAME matching snapshot after reset must NOT render or re-eligibilize:
        // the scheduler no longer holds the track (clear() dropped it).
        int eligibleTrueBefore=recorder.eligibleTrue;
        live.onPlaybackSnapshot(matchingSnapshot(3000));
        live.onPlaybackSnapshot(matchingSnapshot(4000));
        assertEquals("no comment may render after reset",rendersBeforeReset,recorder.renders);
        assertEquals("no new eligibility after reset",eligibleTrueBefore,recorder.eligibleTrue);
    }

    /**
     * Cache revision + ACK state are cleared ONLY by an explicit reset: disconnect leaves them,
     * and they survive across a credentials disconnect until reset() + clear() is called.
     */
    @Test public void cacheRevisionAndAckClearedOnlyByExplicitReset() throws Exception {
        CloudMemory cloud=new CloudMemory();SecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials identity=credentials(cloud,secrets);
        identity.persistActivation("cloud-uuid","device-token","act-1","secret","123456");
        identity.confirmClaimed();

        TrackMemory trackMemory=new TrackMemory();
        CloudTrackRepository cache=new CloudTrackRepository(trackMemory);
        assertTrue(cache.install(3,track("cached"),scheduler()));
        assertTrue(cache.markAcknowledged(3));

        // Disconnect does not clear the cache.
        identity.disconnect();
        assertEquals(3,cache.revision());
        assertEquals(3,cache.acknowledged());

        // Clearing expired activation temporaries also does not clear the cache.
        identity.clearExpiredActivation();
        assertEquals(3,cache.revision());
        assertEquals(3,cache.acknowledged());

        // Only the explicit reset flow clears the cache.
        identity.reset();
        cache.clear();
        assertEquals(0,cache.revision());
        assertEquals(0,cache.acknowledged());
    }
}
