package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Actual Android TV public-controller read/callback experiment, own publisher only.
 * The fixture publishes the standardized ad key deliberately: success proves
 * framework accessibility, not publication, timing or reliability in Prime/Sony.
 */
@RunWith(AndroidJUnit4.class)
public final class AdDetectionAndroidTest {
    private Context target;
    private MediaSession media;
    private AdObservationProbe sensor;

    /** Execute controller registration, reads and teardown on the real main Looper. */
    private void main(Runnable work){InstrumentationRegistry.getInstrumentation().runOnMainSync(work);}
    /** Create an owned publisher without Cloud resources or another video player. */
    @Before public void before(){target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        main(()->{media=new MediaSession(target,"OWN public advertisement contract");sensor=new AdObservationProbe(target.getPackageName(),snapshot->true);});}
    /** Unregister all experiment callbacks before releasing the own session. */
    @After public void after(){main(()->{sensor.close();media.release();});}
    /** Publish a numeric metadata contract or explicitly omit the key. */
    private void publish(Long marker,int state,long position){main(()->{MediaMetadata.Builder metadata=new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE,"OWN_SYNTHETIC_METADATA_NOT_PRIME").putLong(MediaMetadata.METADATA_KEY_DURATION,1_000_000);
        if(marker!=null)metadata.putLong(AdObservationProbe.AD_KEY,marker);media.setMetadata(metadata.build());
        media.setPlaybackState(new PlaybackState.Builder().setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());});}
    /** Read actual framework-returned fields, without injecting a candidate Sample directly. */
    private JSONObject capture(){AtomicReference<JSONObject> status=new AtomicReference<>();main(()->{
        MediaController controller=media.getController();PlaybackState state=controller.getPlaybackState();long now=SystemClock.elapsedRealtime();
        long age=Math.max(0,now-state.getLastPositionUpdateTime());
        MediaSessionProbe.Snapshot snapshot=new MediaSessionProbe.Snapshot(target.getPackageName(),state.getState(),
            state.getState()==PlaybackState.STATE_PLAYING?"PLAYING":"PAUSED",state.getPosition(),state.getPosition(),state.getPlaybackSpeed(),age,
            "","","",1_000_000,media.getSessionToken(),now,state.getLastPositionUpdateTime());
        sensor.onController(controller,snapshot,controller.getMetadata(),state);sensor.onSelection(snapshot);status.set(sensor.status());});return status.get();}
    /** Two independent reads are necessary even for an explicit publisher flag. */
    private JSONObject pair() throws Exception {capture();Thread.sleep(275);return capture();}

    /** Absence, SEEK and pauses do not produce candidate AD_START/AD_END on the real framework. */
    @Test public void ordinaryPlaybackSeeksAndPauseHaveNoAdvertisingEdges() throws Exception {
        for(long position:new long[]{1000,900000,90000}){publish(null,PlaybackState.STATE_PLAYING,position);assertEquals("UNCERTAIN",pair().getString("candidate"));}
        publish(null,PlaybackState.STATE_PAUSED,90000);JSONObject result=pair();assertEquals(0,result.getInt("adStarts"));assertEquals(0,result.getInt("adEnds"));}
    /** The own publisher can expose both distinct boundaries through a public metadata flag. */
    @Test public void ownExplicitMetadataProducesStartThenEndAndCallbacks() throws Exception {
        publish(0L,PlaybackState.STATE_PLAYING,1000);assertEquals("CONTENT_CONFIRMED",pair().getString("candidate"));
        publish(1L,PlaybackState.STATE_PLAYING,1250);JSONObject start=pair();assertEquals("AD_CONFIRMED",start.getString("candidate"));assertEquals(1,start.getInt("adStarts"));
        publish(0L,PlaybackState.STATE_PLAYING,2000);JSONObject end=pair();assertEquals("CONTENT_CONFIRMED",end.getString("candidate"));assertEquals(1,end.getInt("adEnds"));
        assertTrue("Public metadata callback not received",end.getInt("metadataCallbacks")>0);
        JSONObject proof=new JSONObject().put("scope","ANDROID_TV_31_OWN_PUBLIC_METADATA_NOT_PRIME_SONY")
            .put("standardAdKeyExplicitlyPublishedByFixture",true).put("adStart",start).put("adEnd",end).put("noCommentaryGate",true);
        java.nio.file.Files.write(new File(target.getFilesDir(),"tvux002-ad-detection-proof.json").toPath(),proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    /** Arbitrary extras or custom events named AD_START are not owned/public advertising semantics. */
    @Test public void extrasAndPrivateEventCannotConfirmAnAdvertisement() throws Exception {
        publish(null,PlaybackState.STATE_PLAYING,1000);capture();
        main(()->{Bundle extras=new Bundle();extras.putLong(AdObservationProbe.AD_KEY,1);media.setExtras(extras);media.sendSessionEvent("AD_START",extras);});
        Thread.sleep(300);JSONObject result=pair();assertEquals("UNCERTAIN",result.getString("candidate"));assertEquals(0,result.getInt("adStarts"));
        assertTrue(result.getInt("extrasCallbacks")>0);assertTrue(result.getInt("ignoredCustomEvents")>0);}
    /** Phone annotations provide ground truth labels only; they cannot change the candidate state. */
    @Test public void annotationsAreSeparateFromAutomaticEvidence() throws Exception {
        publish(null,PlaybackState.STATE_PLAYING,900000);capture();main(()->{sensor.annotate("AD_VISIBLE");sensor.annotate("CONTENT_VISIBLE");});
        JSONObject result=pair();assertEquals("UNCERTAIN",result.getString("candidate"));assertEquals(0,result.getInt("adStarts"));assertEquals(2,result.getInt("annotations"));}
    /** Disable really unregisters and ignores late callbacks; no candidate survives as enabled protection. */
    @Test public void disabledSensorCannotKeepReadingOrAcceptingAnnotations() throws Exception {
        publish(1L,PlaybackState.STATE_PLAYING,1000);pair();main(sensor::close);publish(0L,PlaybackState.STATE_PLAYING,2000);
        Thread.sleep(300);AtomicReference<JSONObject> state=new AtomicReference<>();main(()->{state.set(sensor.status());
            try{sensor.annotate("AD_VISIBLE");fail("Disabled annotation accepted");}catch(IllegalStateException expected){assertEquals("OBSERVATION_DISABLED",expected.getMessage());}});
        assertFalse(state.get().getBoolean("enabled"));assertEquals(0,state.get().getInt("sessionCount"));}
}
