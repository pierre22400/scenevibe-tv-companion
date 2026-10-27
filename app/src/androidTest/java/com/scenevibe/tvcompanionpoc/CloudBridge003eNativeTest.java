package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.MediaCodecList;
import android.system.Os;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Instrumentation ART/API31 : aucun shadow Robolectric n'est chargé. Les MP3 sont
 * décodés par MediaCodec Android, le cache publie réellement dans SharedPreferences
 * et l'ancien stockage Video/Banner reste distinct. Aucune entrée MEDIA n'est admise.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudBridge003eNativeTest {
    private Native003eFixture fixture;
    /** Create only disposable files inside the qualification application's private directory. */
    @Before public void before() throws Exception {fixture=new Native003eFixture();}
    /** Clean this fixture's generation without clearing any historical namespace. */
    @After public void after() throws Exception {if(fixture!=null)fixture.cleanup();}
    /** Verify actual platform MP3 decoder support and exact decoded lengths for all logical resources. */
    @Test public void mediaCodecDecodesAllThreeOriginalMp3Resources() throws Exception {
        boolean supported=false;
        for(var codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())if(!codec.isEncoder())
            for(String type:codec.getSupportedTypes())if("audio/mpeg".equals(type))supported=true;
        assertTrue(supported);assertEquals(3,fixture.profile.resources.size());
        AndroidStudioAudioInspector inspector=new AndroidStudioAudioInspector(fixture.target);
        for(var row:fixture.profile.resources)assertEquals(3000,inspector.decodedDurationMs(Native003eFixture.asset(row.sha256+".mp3")));
    }
    /** Exercise real Android preferences, files, readback and three distinct calendar events. */
    @Test public void actualSharedPreferencesCommitRestoresTheWholeVerifiedTrack() throws Exception {
        var prepared=fixture.download();var restored=fixture.reopen().read();assertTrue(prepared.binding.same(restored.binding));
        assertEquals(19,restored.track.calendar.events().size());assertEquals(16,restored.track.originalManifest.scenes.size());assertEquals(3,restored.track.audio.size());
        assertEquals(fixture.facts.getString("snapshotSha256"),restored.track.trackSha256);assertEquals(0,fixture.store.read().acknowledgedRevision());
        Set<Long> starts=new HashSet<>();for(var event:restored.track.audio.values()){starts.add(event.startMs());assertEquals(3000,event.durationMs());}
        assertEquals(Set.of(90_000L,300_000L,900_000L),starts);
    }
    /** Preserve exact original TEXT documents after the Android disk/preference boundary. */
    @Test public void originalTextBytesRemainCryptographicallyIdentical() throws Exception {
        var restored=fixture.download();assertArrayEquals(fixture.profile.runtimeBytes,restored.profile.runtimeBytes);assertArrayEquals(fixture.profile.manifestBytes,restored.profile.manifestBytes);
        assertEquals(fixture.facts.getString("runtimeSha256"),MultimediaDeliveryProfile.hash(restored.profile.runtimeBytes));
        assertEquals(fixture.facts.getString("manifestSha256"),MultimediaDeliveryProfile.hash(restored.profile.manifestBytes));
    }
    /** Verify one physical MP3/GET while every logical audio event remains present. */
    @Test public void androidCacheDeduplicatesOnlyPhysicalResources() throws Exception {
        var prepared=fixture.download();assertEquals(1,fixture.port.audioRequests);Set<String> files=new HashSet<>();
        for(var event:prepared.track.audio.values())files.add(prepared.resource(event.assetRef()).getCanonicalPath());assertEquals(1,files.size());assertEquals(3,prepared.track.audio.size());
    }
    /** Prepare the real native MediaPlayer adapter silently, without start, focus or MEDIA permission. */
    @Test public void nativeAudioPortCanPrepareEveryVerifiedMp3WithoutStarting() throws Exception {
        var prepared=fixture.download();AndroidLocalAudioPort port=new AndroidLocalAudioPort(id->{try{return prepared.resource(id);}catch(Exception failed){throw new java.io.IOException("VERIFIED_RESOURCE_REFUSED");}});
        try {for(var event:prepared.track.audio.values()){port.prepare(event.assetRef());assertEquals(LocalAudioPort.State.PREPARED,port.state());port.release();assertEquals(LocalAudioPort.State.IDLE,port.state());}}
        finally {port.release();}
    }
    /** Native files stay under target-private storage and are never world writable. */
    @Test public void cacheFilesAndPreferencesUsePrivateApplicationStorage() throws Exception {
        var prepared=fixture.download();assertTrue(fixture.root.getCanonicalPath().startsWith(fixture.target.getFilesDir().getCanonicalPath()+File.separator));
        File mp3=prepared.resource(prepared.track.audio.values().iterator().next().assetRef());assertEquals(0,Os.stat(mp3.getAbsolutePath()).st_mode&0002);
        File prefs=new File(fixture.target.getApplicationInfo().dataDir,"shared_prefs/"+fixture.namespace+".xml");assertTrue(prefs.isFile());assertEquals(0,Os.stat(prefs.getAbsolutePath()).st_mode&0007);
    }
    /** The dedicated cache namespace never rewrites old Video/Banner data or its ACK. */
    @Test public void historicalInstallationPreferencesRemainUntouched() throws Exception {
        var old=fixture.target.getSharedPreferences("cloud_track",Context.MODE_PRIVATE);Map<String,?> before=old.getAll();fixture.download();assertEquals(before,old.getAll());
        assertFalse(fixture.target.getSharedPreferences(fixture.namespace,Context.MODE_PRIVATE).getAll().isEmpty());
    }
    /** A partial MP3 cannot replace the former coherent Android preference pointer. */
    @Test public void truncatedResourcePreservesCommittedAndroidCache() throws Exception {refuseReplacement("truncate");}
    /** A changed MP3 must fail SHA-256 before the Android cache is published. */
    @Test public void corruptedResourcePreservesCommittedAndroidCache() throws Exception {refuseReplacement("corrupt");}
    /** A missing object never yields a successful Android cache receipt. */
    @Test public void missingResourcePreservesCommittedAndroidCache() throws Exception {refuseReplacement("missing");}
    /** Wrong MIME fails before a native decoder receives the bytes. */
    @Test public void wrongMimePreservesCommittedAndroidCache() throws Exception {refuseReplacement("mime");}
    /** Closing a generation during a resource read preserves the previous native cache. */
    @Test public void cancellationDuringReadPreservesThePreviousNativeCache() throws Exception {
        var before=fixture.download();fixture.replacement();fixture.port.onAudio=()->fixture.lease.live=false;
        boolean refused=false;try{fixture.download();}catch(Exception expected){refused=true;}assertTrue(refused);assertTrue(before.binding.same(fixture.reopen().read().binding));assertEquals(1,fixture.port.receipts);
    }
    /** Native readback refuses altered disk bytes even when preferences still contain a valid descriptor. */
    @Test public void nativeRestoreRejectsDiskCorruption() throws Exception {
        var prepared=fixture.download();File mp3=prepared.resource(prepared.track.audio.values().iterator().next().assetRef());byte[] bytes=Files.readAllBytes(mp3.toPath());bytes[100]^=255;Files.write(mp3.toPath(),bytes);
        boolean refused=false;try{fixture.reopen().read();}catch(Exception expected){refused=true;}assertTrue(refused);
    }
    /** Redelivery reuses verified native files without another physical MP3 transfer. */
    @Test public void nativeRedeliveryIsIdempotent() throws Exception {
        fixture.download();int before=fixture.port.audioRequests;fixture.download();assertEquals(before,fixture.port.audioRequests);assertEquals(0,fixture.store.read().acknowledgedRevision());
    }
    /** A native verified cache can offer its calendar without rendering or a MEDIA admission token. */
    @Test public void nativeVerifiedCacheCannotActivateMedia() throws Exception {
        var prepared=fixture.download();var audio=new AndroidLocalAudioPort(id->{throw new java.io.IOException("MEDIA_NOT_ADMITTED");});
        var runtime=new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** No scene is eligible in a cache-only qualification. */ @Override public boolean preflight(OverlayManifest.Scene scene){return false;}
            /** Any rendered scene is an explicit failure. */ @Override public void show(OverlayManifest.Scene scene){throw new AssertionError("MEDIA_NOT_ADMITTED");}
            /** No scene was started. */ @Override public void hide(OverlayManifest.Scene scene){}
            /** No overlay state exists. */ @Override public void hideAll(){}
        },audio);
        try {
            runtime.prepareFromVerifiedCloudCache(prepared);assertEquals(19,runtime.preparedCloudCalendar().events().size());
            runtime.observe("unadmitted",new com.scenevibe.tvcompanionpoc.calendar.MediaObservation(true,90_000,true));assertEquals(LocalAudioPort.State.IDLE,audio.state());
            boolean refused=false;try{runtime.requireNativeIdentityEvidence();}catch(IllegalStateException expected){refused=true;}assertTrue(refused);
        } finally {runtime.close();audio.release();}
    }
    /** Verify actual failures preserve the original descriptor and skip a replacement receipt. */
    private void refuseReplacement(String fault) throws Exception {
        var before=fixture.download();fixture.replacement();fixture.port.fault=fault;
        boolean refused=false;try{fixture.download();}catch(Exception expected){refused=true;}assertTrue(refused);assertTrue(before.binding.same(fixture.reopen().read().binding));assertEquals(1,fixture.port.receipts);
    }
}
