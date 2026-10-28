package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.io.File;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * v22 on a disposable TV emulator: real encrypted state, private files, decoder,
 * Activity lifecycle and OverlayService. Only provider responses and the OWN
 * MediaSession are fixtures. No Prime process, Sony, Cloud resource or installed
 * user data is touched. The workflow refuses every non-emulator target.
 */
@RunWith(AndroidJUnit4.class)
public final class RecoveryCacheAndroidTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private Native003eFixture fixture;
    private Context target;
    private TvFirst008State state;
    private TvFirst008Source source;
    private MultimediaPrivateCache.Prepared prepared;
    private MainActivity activity;
    private MediaSession media;
    private volatile long revision;
    private volatile CountDownLatch block;
    private volatile int requests;
    private volatile Instant consentExpiry;

    /** Seed the exact existing 19-event profile through the actual serial worker and Android bridge. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.ADSYNC_NONDESTRUCTIVE && BuildConfig.CLOUD_ORIGIN.isEmpty());
        fixture=new Native003eFixture();target=fixture.target;
        // This identity belongs only to the disposable emulator application.
        assertTrue(target.getSharedPreferences("tvfirst008_identity",0).edit().clear().commit());
        state=new TvFirst008State(target);
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("deviceToken","QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ"));
        revision=fixture.binding.revision-1;
        source=new TvFirst008Source(target,state,this::response,"https://owned-fixture.invalid",Executors.newSingleThreadExecutor());
        main(source::resume);await(()->!source.busy() && source.accountStage()==com.scenevibe.tvcompanionpoc.presentation.TvAccountSource.AccountStage.ASSOCIATED,"Association fixture not read",15000);
        AtomicReference<MultimediaPrivateCache.Prepared> result=new AtomicReference<>();
        main(()->source.downloadMultimedia(fixture.binding.publication,result::set,fixture.port));
        await(()->result.get()!=null && !source.busy(),"Actual Android download failed",20000);prepared=result.get();revision=prepared.binding.revision;
        assertEquals(19,prepared.track.calendar.events().size());assertEquals("Content-addressed transfer fetches each distinct resource once",prepared.profile.resources.stream().map(resource->resource.sha256).distinct().count(),fixture.port.audioRequests);
    }

    /** Stop only this test's service, worker, session and generation-owned files. */
    @After public void after() throws Exception {
        if(block!=null)block.countDown();
        main(()->{TvFirst009TextServiceOwner.requestStop(target);if(media!=null)media.release();CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);if(activity!=null)activity.finish();if(source!=null)source.close();});
        if(fixture!=null){Native003eFixture.remove(new File(target.getFilesDir(),"multimedia003d/"+fixture.generation.replace("-","")));fixture.cleanup();}
    }

    /** Return bounded synthetic grants, never a real provider, token, or renewed running reference. */
    private JSONObject response(String method,String path,String token,JSONObject body) throws Exception {
        requests++;CountDownLatch waiting=block;if(waiting!=null && !waiting.await(8,TimeUnit.SECONDS))throw new IllegalStateException("OWN_RESPONSE_BLOCKED");
        if(path.contains("/association"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("accountAssociated",true).put("rendering","FORBIDDEN")
                .put("consentExpiresAt",(consentExpiry==null?Instant.now().plusSeconds(3600):consentExpiry).toString()).put("currentRevision",revision);
        if(path.contains("/library"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("items",new JSONArray()).put("next",JSONObject.NULL);
        throw new IllegalArgumentException("OWN_RESPONSE_ROUTE_REFUSED");
    }
    /** Run the true main Looper; no Robolectric shadow is involved. */
    private void main(Runnable work){instrument.runOnMainSync(work);}
    /** Await real asynchronous work with a bounded native failure. */
    private void await(BooleanSupplier check,String message,long timeout) throws Exception {long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(check.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}fail(message+"; service="+TvFirst009TextServiceOwner.status().code+"; source="+source.accountStage());}
    /** Obtain a permit only through the current associated source, never through reflection. */
    private TvFirst008Source.MultimediaReference reference(){AtomicReference<TvFirst008Source.MultimediaReference> ref=new AtomicReference<>();main(()->ref.set(source.confirmedMultimediaReference(prepared)));assertNotNull(ref.get());return ref.get();}
    /** Find a real own-application widget, with no inspection of a third-party UI. */
    private View find(View root,String text,boolean button){if(root instanceof TextView && ((TextView)root).getText().toString().contains(text) && (!button || root instanceof Button))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),text,button);if(found!=null)return found;}return null;}
    /** Launch/recreate the ordinary Companion; its unset origin cannot perform a provider request. */
    private void launch(){activity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}

    /** A real CONNECTING presentation change cannot revoke an immutable, still-valid running consent. */
    @Test public void activityRefreshDoesNotInvalidateRunningAuthority() throws Exception {
        var ref=reference();assertTrue(ref.current(SystemClock.elapsedRealtime()));int downloads=fixture.port.audioRequests;
        block=new CountDownLatch(1);main(()->{source.pause();source.resume();});
        assertTrue(source.busy());assertTrue("Existing authority must survive UI CONNECTING",ref.current(SystemClock.elapsedRealtime()));
        main(()->assertNull("CONNECTING may not mint a NEW authority",source.confirmedMultimediaReference(prepared)));
        assertEquals("CONSENT_EXPIRED",ref.invalidReason(Long.MAX_VALUE));assertFalse(ref.current(Long.MAX_VALUE));
        block.countDown();await(()->!source.busy(),"Read-only verification did not finish",15000);
        assertTrue(ref.current(SystemClock.elapsedRealtime()));assertEquals(downloads,fixture.port.audioRequests);
    }
    /** Actual receipt revocation stays closed while the exact inert 19-event cache survives without HTTP. */
    @Test public void revocationNeverDeletesCacheOrMakesAnAdmission() throws Exception {
        var ref=reference();state.write("multimediaReceipt",null);
        assertEquals("CACHE_RECEIPT_INVALID",ref.invalidReason(SystemClock.elapsedRealtime()));assertFalse(ref.current(SystemClock.elapsedRealtime()));
        int before=requests;var restored=MultimediaAndroidBridge.restoreCache(target,source);assertNotNull(restored);
        assertEquals(16,restored.track.originalManifest.scenes.size());assertEquals(3,restored.track.audio.size());assertEquals(19,restored.track.calendar.events().size());
        assertEquals(MultimediaDeliveryProfile.hash(prepared.profile.runtimeBytes),MultimediaDeliveryProfile.hash(restored.profile.runtimeBytes));
        for(var resource:prepared.profile.resources)assertEquals(resource.sha256,MultimediaDeliveryProfile.hash(java.nio.file.Files.readAllBytes(restored.resource(resource.id).toPath())));
        assertEquals(before,requests);assertNull(CloudMultimediaAdmission.admit(ref,true));
    }
    /** Let an actual monotonic consent expire, then explicitly re-verify and re-admit the same inert cache. */
    @Test public void actualConsentExpiryNeedsFreshConfirmationButNoDownload() throws Exception {
        consentExpiry=Instant.now().plusMillis(2500);main(()->{source.pause();source.resume();});
        await(()->!source.busy() && source.accountStage()==com.scenevibe.tvcompanionpoc.presentation.TvAccountSource.AccountStage.ASSOCIATED,"Short real consent not obtained",10000);
        var expired=reference();assertTrue(expired.current(SystemClock.elapsedRealtime()));
        await(()->!expired.current(SystemClock.elapsedRealtime()),"Real consent did not expire",7000);assertEquals("CONSENT_EXPIRED",expired.invalidReason(SystemClock.elapsedRealtime()));
        int downloads=fixture.port.audioRequests;assertNotNull(MultimediaAndroidBridge.restoreCache(target,source));assertNull(CloudMultimediaAdmission.admit(expired,true));
        consentExpiry=Instant.now().plusSeconds(3600);main(source::refresh);
        await(()->!source.busy() && source.accountStage()==com.scenevibe.tvcompanionpoc.presentation.TvAccountSource.AccountStage.ASSOCIATED,"Explicit new association verification failed",10000);
        var renewed=reference();assertNull(CloudMultimediaAdmission.admit(renewed,false));assertNotNull(CloudMultimediaAdmission.admit(renewed,true));assertEquals(downloads,fixture.port.audioRequests);assertFalse(TvFirst009TextServiceOwner.multimediaActive());
    }
    /** Native Activity destruction and return automatically re-read cache without download or activation. */
    @Test public void actualActivityRecreationRestoresInertCacheAndPreferences() throws Exception {
        int downloads=fixture.port.audioRequests;launch();
        main(()->{View settings=find(activity.getWindow().getDecorView(),"Réglages",true);assertNotNull(settings);settings.performClick();});
        await(()->find(activity.getWindow().getDecorView(),"Cache privé vérifié : 16 TEXT + 3 AUDIO",false)!=null,"UI did not restore durable cache",20000);
        main(()->{TvFirst009TextServiceOwner.setMultimediaTextEnabled(false);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);TvFirst009TextServiceOwner.setAdSyncEnabled(true);activity.finish();});instrument.waitForIdleSync();
        launch();main(()->{View settings=find(activity.getWindow().getDecorView(),"Réglages",true);assertNotNull(settings);settings.performClick();});
        await(()->find(activity.getWindow().getDecorView(),"Cache privé vérifié : 16 TEXT + 3 AUDIO",false)!=null,"Recreated Activity lost cache presentation",20000);
        assertFalse(TvFirst009TextServiceOwner.multimediaTextEnabled());assertTrue(TvFirst009TextServiceOwner.multimediaAudioEnabled());assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());assertEquals(downloads,fixture.port.audioRequests);
    }
    /** Build a genuine native public session for this app's debug-only admission target, never Prime. */
    private void metadata(){media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
            .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());}
    /** Publish only the owned fixture's playback facts, leaving the application's probe in charge of time. */
    private void playback(int state,long position){media.setPlaybackState(new PlaybackState.Builder().setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());}
    /** Recover automatically from missing metadata, then traverse all original TEXT/MP3 via the real service with AD-SYNC ON. */
    @Test public void actualServiceRecoversAndRendersAllNineteenEvents() throws Exception {
        UiDevice remote=UiDevice.getInstance(instrument);remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());launch();
        main(()->{CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);TvFirst009TextServiceOwner.setAdSyncEnabled(true);
            media=new MediaSession(target,"SceneVibe OWN v22 recovery fixture");metadata();playback(PlaybackState.STATE_PAUSED,0);media.setActive(true);});
        await(()->NotificationAccess.isGranted(target),"Emulator notification listener unavailable",10000);
        main(()->assertTrue("Explicit current admission rejected",TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,prepared,true)));
        await(()->TvFirst009TextServiceOwner.multimediaActive(),"Actual OverlayService did not start",15000);
        main(()->{playback(PlaybackState.STATE_PLAYING,0);media.setMetadata(null);});
        await(()->TvFirst009TextServiceOwner.adSyncStatus().contains("MEDIA_IDENTITY_INVALID"),"Metadata absence was not held",10000);
        main(this::metadata);await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Two compatible captures did not auto-recover",10000);
        assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());assertTrue(TvFirst009TextServiceOwner.multimediaActive());
        int texts=0,audios=0;for(SceneEvent event:prepared.track.calendar.events()){
            long before=Math.max(0,event.startMs()-1000);main(()->playback(PlaybackState.STATE_PAUSED,before));
            await(()->"MEDIA_PAUSED".equals(TvFirst009TextServiceOwner.status().code) && TvFirst009TextServiceOwner.status().positionMs==before,"Paused native coordinate missing",10000);
            main(()->playback(PlaybackState.STATE_PLAYING,before));
            if(prepared.track.audio.containsKey(event.eventId())){final int expected=++audios;await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=expected,"MP3 not natively STARTED",12000);}
            else{final int expected=++texts;await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=expected,"TEXT not rendered by service",12000);}
        }
        assertEquals(16,texts);assertEquals(3,audios);assertEquals(3,TvFirst009TextServiceOwner.multimediaAudioPreparations());assertTrue(TvFirst009TextServiceOwner.adSyncEnabled());
        main(()->{assertTrue(TvFirst009TextServiceOwner.setDeveloperMode(target,true));assertTrue(TvFirst009TextServiceOwner.setLocalControlEnabled(true));});
        String instructions=TvFirst009TextServiceOwner.localControlInfo();var address=java.util.regex.Pattern.compile("http://[^:]+:([0-9]+)").matcher(instructions);assertTrue(address.find());int port=Integer.parseInt(address.group(1));
        String token=LocalControlTestClient.send(port,"/pair",null,new JSONObject().put("code",LocalControlTestClient.code(instructions)),null).json().getString("token");
        local(port,token,1,"text","false");local(port,token,2,"audio","false");local(port,token,3,"adsync","false");
        assertFalse(TvFirst009TextServiceOwner.multimediaTextEnabled());assertFalse(TvFirst009TextServiceOwner.multimediaAudioEnabled());
        var offCalibration=local(port,token,4,"calibrate","28:28");assertEquals(AdSyncController.Calibration.SYNC_DISABLED.message,offCalibration.getString("result"));
        assertFalse(TvFirst009TextServiceOwner.adSyncStatus().contains("Suspension manuelle"));
        local(port,token,5,"suspend","");local(port,token,6,"adsync","true");
        Thread.sleep(600);assertTrue(TvFirst009TextServiceOwner.adSyncStatus().contains("Suspension manuelle"));assertEquals(3,TvFirst009TextServiceOwner.multimediaAudioPreparations());
        local(port,token,7,"resume","");await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Explicit local resume did not recover",10000);
        local(port,token,8,"text","true");local(port,token,9,"audio","true");
        assertEquals(AdSyncController.Calibration.APPLIED.message,local(port,token,10,"calibrate","28:28").getString("result"));
        await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Local calibration did not recover",10000);
        assertEquals(3,TvFirst009TextServiceOwner.multimediaAudioPreparations());
        main(()->TvFirst009TextServiceOwner.setLocalControlEnabled(false));assertTrue(TvFirst009TextServiceOwner.multimediaActive());
        var proof=new JSONObject().put("scope","REAL_ANDROID_SERVICE_OWN_PUBLIC_MEDIASESSION_PROVIDER_FIXTURE_NOT_PRIME_SONY_OR_CLOUD")
                .put("textWindowsObserved",TvFirst009TextServiceOwner.multimediaTextRequested()).put("nativeMp3Started",TvFirst009TextServiceOwner.multimediaAudioStarted())
                .put("nativePreparationsAfterRecovery",TvFirst009TextServiceOwner.multimediaAudioPreparations()).put("adSyncPreferenceStillOn",TvFirst009TextServiceOwner.adSyncEnabled())
                .put("localTcpCommandsOnActiveService",true).put("cacheEvents",prepared.track.calendar.events().size());
        java.nio.file.Files.write(new File(target.getFilesDir(),"tvux002-service-proof.json").toPath(),proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

    }

    /** Reproduce the v22 late-start/rewind loss with AD-SYNC OFF, then revoke the actual emulator overlay permission. */
    @Test public void runtime003OffRewindRestoresTextAndPermissionRecoveryKeepsAdmission() throws Exception {
        UiDevice remote=UiDevice.getInstance(instrument);remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());launch();
        main(()->{CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);TvFirst009TextServiceOwner.setAdSyncEnabled(false);
            media=new MediaSession(target,"OWN runtime003 coordinates");metadata();playback(PlaybackState.STATE_PLAYING,43788);media.setActive(true);});
        await(()->NotificationAccess.isGranted(target),"Own MEDIA listener missing",10000);
        main(()->assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,prepared,true)));
        await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Compatible MEDIA not delivered",10000);
        assertEquals("No TEXT is scheduled in the initial expired window",0,TvFirst009TextServiceOwner.status().renderRequests);
        assertTrue("The actual Columbo calendar has a second TEXT at 52.988s before the first MP3",TvFirst009TextServiceOwner.multimediaAvailability().contains("00:52"));
        main(()->media.setMetadata(null));await(()->"MEDIA_IDENTITY_INVALID".equals(TvFirst009TextServiceOwner.status().code),"Identity refusal not observed",10000);
        int downloads=fixture.port.audioRequests;
        main(()->{metadata();playback(PlaybackState.STATE_PLAYING,30009);});
        await(()->TvFirst009TextServiceOwner.multimediaTextRequested()==1,"Original 32.988s TEXT not attached after interrupted rewind",12000);
        assertEquals(1,TvFirst009TextServiceOwner.status().renderRequests);assertFalse(TvFirst009TextServiceOwner.adSyncEnabled());
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW ignore");
        await(()->"OVERLAY_PERMISSION_REQUIRED".equals(TvFirst009TextServiceOwner.status().code),"Actual OS permission loss not held",10000);
        assertFalse(android.provider.Settings.canDrawOverlays(target));assertTrue("Permission loss must not destroy valid admission",TvFirst009TextServiceOwner.multimediaActive());
        int requested=TvFirst009TextServiceOwner.multimediaTextRequested(),preparations=TvFirst009TextServiceOwner.multimediaAudioPreparations();Thread.sleep(700);
        assertEquals(preparations,TvFirst009TextServiceOwner.multimediaAudioPreparations());
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Restored real permission did not recover",10000);
        assertTrue(TvFirst009TextServiceOwner.multimediaActive());assertEquals(downloads,fixture.port.audioRequests);assertEquals(requested,TvFirst009TextServiceOwner.multimediaTextRequested());
        main(()->playback(PlaybackState.STATE_PLAYING,-1));await(()->"MEDIA_CLOCK_INVALID".equals(TvFirst009TextServiceOwner.status().code),"Invalid position was accepted",10000);
        assertTrue(TvFirst009TextServiceOwner.multimediaActive());main(()->playback(PlaybackState.STATE_PLAYING,51000));
        await(()->TvFirst009TextServiceOwner.multimediaTextRequested()==2,"Future second TEXT did not recover",12000);
        var proof=new JSONObject().put("scope","REAL_ANDROID_SERVICE_OWN_MEDIA_NOT_PRIME_SONY")
                .put("adSyncOff",true).put("originalFirstTextRecovered",true).put("secondTextAttached",true)
                .put("realOverlayRevocationBlocked",true).put("admissionRetained",true).put("automaticPermissionRecovery",true)
                .put("downloadsAfterInitialCache",fixture.port.audioRequests-downloads).put("cacheEvents",prepared.track.calendar.events().size());
        java.nio.file.Files.write(new File(target.getFilesDir(),"tvux002-runtime003-proof.json").toPath(),proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Send a unique real local command to the active native service, never directly to its runtime. */
    private JSONObject local(int port,String token,long sequence,String command,String value) throws Exception {
        var reply=LocalControlTestClient.send(port,"/command",token,new JSONObject().put("seq",sequence).put("command",command).put("value",value),null);
        assertEquals(200,reply.status);return reply.json();
    }

}
