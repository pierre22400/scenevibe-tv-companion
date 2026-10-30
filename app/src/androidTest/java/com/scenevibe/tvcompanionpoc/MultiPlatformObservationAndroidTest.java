package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Compatibility;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Platform;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android read callbacks from OWN publishers; no official Netflix/Disney+ app or physical Sony is used. */
@RunWith(AndroidJUnit4.class)
public final class MultiPlatformObservationAndroidTest {
    private Context target;
    private MainActivity application;
    private final List<MediaSession> publishers=new ArrayList<>();
    private MultiPlatformAdObservationProbe observer;
    private final AtomicInteger transports=new AtomicInteger();
    /** Run callback registration and service state on the actual Android main Looper. */
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    /** Refuse every release/provider build before enabling the explicit own-publisher fixture. */
    @Before public void before(){assertTrue(BuildConfig.DEBUG && BuildConfig.CLOUD_ORIGIN.isEmpty());target=InstrumentationRegistry.getInstrumentation().getTargetContext();}
    /** Release only this test's own publisher callbacks and SceneVibe service; never erase cache. */
    @After public void after(){main(()->{if(observer!=null)observer.close();for(MediaSession session:publishers)session.release();TvFirst009TextServiceOwner.requestStop(target);if(application!=null)application.finish();});}
    /** Create one OWN MediaSession and count every forbidden target transport invocation. */
    private MediaSession publisher(){AtomicReference<MediaSession> result=new AtomicReference<>();main(()->{
        MediaSession session=new MediaSession(target,"OWN synthetic multi-platform scalar publisher");session.setCallback(new MediaSession.Callback(){
            /** Count a forbidden PLAY on this test-owned target. */ @Override public void onPlay(){transports.incrementAndGet();}
            /** Count a forbidden PAUSE on this test-owned target. */ @Override public void onPause(){transports.incrementAndGet();}
            /** Count a forbidden SEEK on this test-owned target. */ @Override public void onSeekTo(long position){transports.incrementAndGet();}
            /** Count a forbidden STOP on this test-owned target. */ @Override public void onStop(){transports.incrementAndGet();}
        });session.setActive(true);publishers.add(session);result.set(session);});return result.get();}
    /** Publish only owned synthetic metadata, including deliberately private strings that must not be exported. */
    private void publish(MediaSession session,Long marker,int state,long position){main(()->{
        MediaMetadata.Builder metadata=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"PRIVATE_FIXTURE_TITLE")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"PRIVATE_FIXTURE_ID").putString("private.SECRET_KEY","PRIVATE_FIXTURE_VALUE");
        if(marker!=null)metadata.putLong(AdObservationProbe.AD_KEY,marker);session.setMetadata(metadata.build());
        session.setPlaybackState(new PlaybackState.Builder().setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());});}
    /** Feed genuine framework-returned fields to the collector, with no invented official package name. */
    private JSONObject capture(List<MediaSession> sessions){AtomicReference<JSONObject> result=new AtomicReference<>();main(()->{
        observer.onBatch(sessions.size());MediaSessionProbe.Snapshot selected=null;
        for(MediaSession session:sessions){MediaController controller=session.getController();PlaybackState state=controller.getPlaybackState();MediaMetadata metadata=controller.getMetadata();long now=SystemClock.elapsedRealtime();
            long age=state==null?-1:Math.max(0,now-state.getLastPositionUpdateTime());
            var snapshot=new MediaSessionProbe.Snapshot(controller.getPackageName(),state==null?0:state.getState(),"OWN_PUBLIC_STATE",state==null?-1:state.getPosition(),
                    state==null?-1:state.getPosition(),state==null?0f:state.getPlaybackSpeed(),age,"PRIVATE_FIXTURE_ID","PRIVATE_FIXTURE_TITLE","",-1,
                    session.getSessionToken(),now,state==null?-1:state.getLastPositionUpdateTime());
            observer.onController(controller,snapshot,metadata,state);selected=snapshot;
        }observer.onSelection(selected);result.set(observer.status());});return result.get();}
    /** Persist only closed proof facts, never a session token or programme payload. */
    private void proof(String name,JSONObject result) throws Exception {Files.write(new File(target.getFilesDir(),"tvux002-multi-"+name+"-proof.json").toPath(),result.toString().getBytes(StandardCharsets.UTF_8));}
    /** Test real callbacks using an explicitly owned publisher alias, without claiming an official platform result. */
    private void campaign(Platform platform) throws Exception {MediaSession source=publisher();main(()->observer=MultiPlatformAdObservationProbe.ownedFixture(platform));
        publish(source,null,PlaybackState.STATE_PLAYING,1000);assertEquals("ABSENT",capture(List.of(source)).getJSONArray("samples").getJSONObject(0).getString("marker"));
        publish(source,1L,PlaybackState.STATE_PLAYING,2000);Thread.sleep(300);JSONObject ad=capture(List.of(source));
        assertEquals("AD",ad.getJSONArray("samples").getJSONObject(0).getString("marker"));assertTrue(ad.getJSONArray("samples").getJSONObject(0).getInt("metadataCallbacks")>0);
        publish(source,null,PlaybackState.STATE_BUFFERING,2000);assertEquals("ABSENT",capture(List.of(source)).getJSONArray("samples").getJSONObject(0).getString("marker"));
        main(()->observer.annotate("AD_VISIBLE"));JSONObject status=capture(List.of(source));assertEquals(1,status.getInt("annotations"));assertEquals("INSUFFICIENT_EVIDENCE",status.getString("conclusion"));
        assertEquals("OWN_FIXTURE_NOT_REAL_PLATFORM",status.getString("publisherScope"));assertFalse(status.toString().contains("PRIVATE_FIXTURE"));assertEquals(0,transports.get());
        proof(platform.name().toLowerCase(java.util.Locale.ROOT),new JSONObject().put("scope","OWN_ANDROID_PUBLIC_CALLBACKS_NOT_OFFICIAL_PLATFORM_OR_SONY")
                .put("campaign",platform.name()).put("ad",ad).put("returnedAbsent",status).put("targetTransportCalls",transports.get()));}
    /** Netflix's synthetic journal cannot establish physical Netflix publication. */
    @Test public void netflixOwnedCallbackFixture() throws Exception {campaign(Platform.NETFLIX);}
    /** Disney+ is qualified independently from the Netflix fixture. */
    @Test public void disneyOwnedCallbackFixture() throws Exception {campaign(Platform.DISNEY_PLUS);}
    /** Production exact-package filtering refuses a real controller owned by SceneVibe, even with AD present. */
    @Test public void officialPackageFilterCannotBeSpoofedByFixture() throws Exception {MediaSession source=publisher();publish(source,1L,PlaybackState.STATE_PLAYING,1000);
        for(Platform platform:Platform.values()){main(()->{if(observer!=null)observer.close();observer=new MultiPlatformAdObservationProbe(platform,snapshot->Compatibility.NOT_EVALUATED);});
            JSONObject status=capture(List.of(source));assertEquals(0,status.getInt("targetSessionCount"));assertEquals("NONE",status.getString("observedPlatform"));assertEquals(1,status.getInt("activeSessionCount"));}assertEquals(0,transports.get());}
    /** Concurrent owned controllers retain distinct session ordinals and an explicit ambiguity status. */
    @Test public void multipleSessionsAreNotCollapsed() throws Exception {MediaSession first=publisher(),second=publisher();main(()->observer=MultiPlatformAdObservationProbe.ownedFixture(Platform.NETFLIX));
        publish(first,1L,PlaybackState.STATE_PLAYING,1000);publish(second,null,PlaybackState.STATE_PAUSED,2000);JSONObject status=capture(List.of(first,second));
        assertEquals(2,status.getJSONArray("samples").length());assertEquals("MULTIPLE_TARGET_SESSIONS",status.getString("selection"));assertEquals(0,transports.get());}
    /** Loss/recovery and OFF use real callback teardown, without converting loss into advertising completion. */
    @Test public void unavailableRecoveryAndDisableReleaseCallbacks() throws Exception {MediaSession source=publisher();main(()->observer=MultiPlatformAdObservationProbe.ownedFixture(Platform.DISNEY_PLUS));
        publish(source,1L,PlaybackState.STATE_PLAYING,1000);capture(List.of(source));main(observer::onUnavailable);assertEquals(0,capture(List.of()).getInt("targetSessionCount"));
        publish(source,null,PlaybackState.STATE_PLAYING,2000);assertEquals(1,capture(List.of(source)).getInt("targetSessionCount"));main(observer::close);
        publish(source,1L,PlaybackState.STATE_PLAYING,3000);JSONObject status=capture(List.of(source));assertFalse(status.getBoolean("enabled"));assertEquals(0,status.getJSONArray("samples").length());assertEquals(0,transports.get());}
    /** Real Android state callbacks expose pause/resume and session replacement without any target transport. */
    @Test public void publicTransitionsRemainReadOnly() throws Exception {
        MediaSession first=publisher();main(()->observer=MultiPlatformAdObservationProbe.ownedFixture(Platform.NETFLIX));
        publish(first,null,PlaybackState.STATE_PAUSED,10000);capture(List.of(first));Thread.sleep(50);
        publish(first,null,PlaybackState.STATE_PLAYING,10000);Thread.sleep(100);
        JSONObject resumed=capture(List.of(first));JSONObject sample=resumed.getJSONArray("samples").getJSONObject(0);
        assertTrue(sample.getInt("playbackCallbacks")>0);assertTrue(sample.getJSONArray("changes").toString().contains("PLAYBACK_RESUMED"));
        assertEquals("STATE_CHANGED",sample.getJSONObject("comparison").getString("reason"));
        MediaSession second=publisher();publish(second,null,PlaybackState.STATE_PLAYING,500);
        JSONObject replacement=capture(List.of(second));assertEquals("FIRST_SESSION_SAMPLE",replacement.getJSONArray("samples").getJSONObject(0).getJSONObject("comparison").getString("reason"));
        assertEquals(0,transports.get());proof("transitions",new JSONObject().put("scope","OWN_ANDROID_CALLBACKS_NOT_OFFICIAL_PLATFORM_OR_SONY").put("resumed",resumed).put("replacement",replacement).put("targetTransportCalls",transports.get()));
    }
    /** Framework metadata distinguishes unavailable data, absent keys, explicit zero/nonzero and wrong types. */
    @Test public void publicMarkerTypesAreNotCoerced() throws Exception {
        MediaSession source=publisher();main(()->observer=MultiPlatformAdObservationProbe.ownedFixture(Platform.DISNEY_PLUS));
        publish(source,null,PlaybackState.STATE_PLAYING,1000);capture(List.of(source));
        main(()->source.setMetadata(null));assertEquals("UNAVAILABLE",capture(List.of(source)).getJSONArray("samples").getJSONObject(0).getString("markerStatus"));
        for(long value:new long[]{0,1}){publish(source,value,PlaybackState.STATE_PLAYING,2000);
            assertEquals(value==0?"CONTENT":"AD",capture(List.of(source)).getJSONArray("samples").getJSONObject(0).getString("markerStatus"));}
        main(()->source.setMetadata(new MediaMetadata.Builder().putString(AdObservationProbe.AD_KEY,"PRIVATE_WRONG_TYPE").build()));
        JSONObject invalid=capture(List.of(source));assertEquals("INVALID",invalid.getJSONArray("samples").getJSONObject(0).getString("markerStatus"));
        assertFalse(invalid.toString().contains("PRIVATE_WRONG_TYPE"));assertEquals(0,transports.get());
        proof("types",new JSONObject().put("scope","OWN_ANDROID_METADATA_TYPES_NOT_OFFICIAL_PLATFORM_OR_SONY").put("invalid",invalid).put("targetTransportCalls",transports.get()));
    }
    /** The existing foreground service can observe without a track, Cloud admission, or a second service. */
    @Test public void idleServicePhoneCampaignsDoNotActivateTrack() throws Exception {
        application=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        long permissionDeadline=SystemClock.elapsedRealtime()+10000;
        while(!NotificationAccess.isGranted(target) && SystemClock.elapsedRealtime()<permissionDeadline)Thread.sleep(50);
        assertTrue("Fixture MEDIA permission was not actually granted by Android",NotificationAccess.isGranted(target));
        main(()->assertTrue(TvFirst009TextServiceOwner.setDeveloperMode(target,true)));
        long end=SystemClock.elapsedRealtime()+10000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<end){AtomicReference<Boolean> value=new AtomicReference<>(false);main(()->value.set(TvFirst009TextServiceOwner.setLocalControlEnabled(true)));if(value.get()){ready=true;break;}Thread.sleep(50);}assertTrue(ready);
        String instructions=TvFirst009TextServiceOwner.localControlInfo();var address=java.util.regex.Pattern.compile("http://[^:]+:([0-9]+)").matcher(instructions);assertTrue(address.find());int port=Integer.parseInt(address.group(1));
        String token=LocalControlTestClient.send(port,"/pair",null,new JSONObject().put("code",LocalControlTestClient.code(instructions)),null).json().getString("token");
        long sequence=0;for(Platform platform:Platform.values()){
            var started=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multiobserve").put("value",platform.name()),null).json();
            assertTrue("Campaign activation refused: "+started,started.getJSONObject("multiObservation").getBoolean("enabled"));assertFalse(started.getBoolean("active"));assertFalse(started.getJSONObject("adProtection").getBoolean("enabled"));
            var marked=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multimark").put("value","AD_VISIBLE"),null).json();
            assertEquals(1,marked.getJSONObject("multiObservation").getInt("annotations"));assertFalse(marked.getBoolean("active"));
            var other=platform==Platform.NETFLIX?Platform.DISNEY_PLUS:Platform.NETFLIX;
            var refused=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multiobserve").put("value",other.name()),null).json();
            assertEquals("CAMPAIGN_STOP_REQUIRED",refused.getString("diagnosticRefusal"));
            assertEquals(platform.name(),refused.getJSONObject("multiObservation").getString("platform"));
            LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multiobserve").put("value","OFF"),null);
            var noCampaign=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multimark").put("value","AD_VISIBLE"),null).json();
            assertEquals("OBSERVATION_DISABLED",noCampaign.getString("diagnosticRefusal"));
        }
        var stopped=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",++sequence).put("command","multiobserve").put("value","OFF"),null).json();assertFalse(stopped.getJSONObject("multiObservation").getBoolean("enabled"));assertFalse(TvFirst009TextServiceOwner.multimediaActive());
        proof("idle-service",new JSONObject().put("scope","REAL_ANDROID_SERVICE_PHONE_OWN_EMULATOR_NOT_SONY_OR_CLOUD").put("noTrackAdmission",true).put("platformCampaignsSeparated",true).put("primeProtectionNotActivated",true));
    }
}
