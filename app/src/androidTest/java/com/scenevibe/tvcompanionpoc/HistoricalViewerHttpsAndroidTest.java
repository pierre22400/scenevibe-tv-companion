package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Test A sur Android TV. Le compte Web et le Companion utilisent le vrai
 * HTTPS. Le banc ne crée qu'une association historique avant le clic.
 */
@RunWith(AndroidJUnit4.class)
public final class HistoricalViewerHttpsAndroidTest {
    private static final String PROFILE="?profile="+TvFirst008Wire.PROFILE;
    private final android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrument.getTargetContext();

    /** Attend un état Android observable sans agir sur la session ni sur l'horloge MEDIA. */
    private void await(BooleanSupplier ready,String diagnostic,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){
            if(ready.getAsBoolean())return;
            Thread.sleep(250);
        }
        fail(diagnostic);
    }

    /** Exporte uniquement le fait de qualification, sans bearer, cookie ou hash de credential. */
    private void evidence(String name,JSONObject data) throws Exception {
        Files.write(new File(app.getFilesDir(),name).toPath(),data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Enregistre le seul grant initial via les routes historiques et le stockage Keystore normal. */
    private JSONObject initialAssociation(TvFirst008State state,TvFirst008Http http) throws Exception {
        JSONObject pending=state.challenge("new");
        String secret=state.challengeSecret(pending);
        JSONObject request=new JSONObject(pending.toString()).put("profile",TvFirst008Wire.PROFILE).put("challengeSecret",secret);
        JSONObject offered=http.request("POST","/api/v1/device-activations"+PROFILE,null,request);
        evidence("001c-code.json",new JSONObject().put("userCode",offered.getString("userCode")));
        long deadline=SystemClock.elapsedRealtime()+120_000;
        while(SystemClock.elapsedRealtime()<deadline){
            JSONObject result=http.request("GET","/api/v1/device-activations/"+pending.getString("challengeId")+PROFILE,secret,null);
            if("claimed".equals(result.optString("status"))){state.claimed(result);return state.read("identity");}
            Thread.sleep(1000);
        }
        throw new AssertionError("HISTORICAL_SETUP_TIMEOUT");
    }

    /** Positionne uniquement l'œuvre appartenant au banc Android. */
    private void playback(MediaSession media,int state,long position){
        media.setPlaybackState(new PlaybackState.Builder()
                .setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());
    }

    /** Qualifie les sorties natives et le rappel de raccourci sur une session MEDIA du banc. */
    private JSONObject mediaRegression(PublicMultimediaCache.Prepared prepared) throws Exception {
        final MediaSession[] owned={null};
        instrument.runOnMainSync(()->{
            owned[0]=new MediaSession(app,"SCENEVIBE 001-C OWNED MEDIA");
            owned[0].setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            playback(owned[0],PlaybackState.STATE_PAUSED,0);owned[0].setActive(true);
        });
        int texts=0,audios=0;
        try{
            for(SceneEvent event:prepared.track.calendar.events()){
                long before=Math.max(0,event.startMs()-1000);
                instrument.runOnMainSync(()->playback(owned[0],PlaybackState.STATE_PAUSED,before));
                await(()->TvFirst009TextServiceOwner.status().positionMs==before,"OWN_MEDIA_POSITION_MISSING",15000);
                instrument.runOnMainSync(()->playback(owned[0],PlaybackState.STATE_PLAYING,before));
                if(prepared.track.audio.containsKey(event.eventId())){
                    int count=++audios;
                    await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=count,"OWN_MEDIA_AUDIO_MISSING",15000);
                }else{
                    int count=++texts;
                    await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=count,"OWN_MEDIA_TEXT_MISSING",15000);
                }
            }
            assertTrue(texts>0 && audios>0);
            instrument.runOnMainSync(()->playback(owned[0],PlaybackState.STATE_PAUSED,30_000));
            await(()->TvFirst009TextServiceOwner.status().positionMs==30_000,"MUTE_BASE_POSITION_MISSING",15000);
            Activity viewer=instrument.startActivitySync(new Intent(app,DeveloperViewerFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try(var bench=new CompanionShortcutBench(instrument)){
                bench.systemShortcut();
                bench.await(TvFirst009TextServiceOwner::developerHasFocus,"NATIVE_SHORTCUT_WINDOW_MISSING",10000);
                bench.remote.pressBack();
                bench.await(viewer::hasWindowFocus,"NATIVE_SHORTCUT_FOCUS_NOT_RESTORED",10000);
            }finally{instrument.runOnMainSync(viewer::finish);}
            instrument.runOnMainSync(()->TvFirst009TextServiceOwner.setMultimediaAudioEnabled(false));
            return new JSONObject().put("textWindows",texts).put("audioStarted",audios)
                    .put("androidShortcutCallback",true).put("backAndFocus",true);
        }finally{instrument.runOnMainSync(()->{playback(owned[0],PlaybackState.STATE_STOPPED,30_000);owned[0].release();});}
    }

    /** Vérifie que le vrai service télécharge et arme le cache pendant que l'Activity est fermée. */
    @Test public void historicalOnlyFromBrowserThroughService() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C);
        UiDevice remote=UiDevice.getInstance(instrument);
        assertEquals("1",remote.executeShellCommand("getprop ro.kernel.qemu").trim());
        remote.executeShellCommand("appops set "+app.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(app).flattenToString());
        instrument.runOnMainSync(()->CloudMultimediaAdmission.useOwnedMediaSessionFixture(true));
        TvFirst008State state=new TvFirst008State(app);
        assertNull(state.read("identity"));
        assertNull(PublicCompanionOwner.identityState(app).read("identity"));
        JSONObject identity=initialAssociation(state,new TvFirst008Http(BuildConfig.CLOUD_ORIGIN));
        Activity initial=instrument.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            instrument.runOnMainSync(()->PublicCompanionService.resumeHistorical(initial));
            await(()->PublicCompanionService.current()!=null &&
                    PublicCompanionService.current().viewerPreferences().initialized(),"HISTORICAL_SERVICE_NOT_READY",90_000);
            instrument.runOnMainSync(initial::finish);
            remote.pressHome();
            await(()->initial.isFinishing() && !app.getPackageName().equals(remote.getCurrentPackageName()),
                    "COMPANION_ACTIVITY_STILL_VISIBLE",25_000);
            evidence("001c-background.json",new JSONObject().put("deviceId",identity.getString("deviceId"))
                    .put("activityClosed",true).put("publicIdentityPresent",false));
            await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().status().startsWith("Track prête"),
                    "NATIVE_HISTORICAL_READY_TIMEOUT",150_000);
            assertNull(PublicCompanionOwner.identityState(app).read("identity"));
            PublicMultimediaCache.Prepared prepared=new PublicMultimediaCache(app,identity.getString("generation")).read();
            assertNotNull(prepared);
            assertTrue(prepared.track.audio.size()>0);
            PublicViewerPreferences preferences=PublicCompanionService.current().viewerPreferences();
            assertTrue(preferences.text());
            assertTrue(preferences.audio());
            assertFalse(app.getPackageName().equals(remote.getCurrentPackageName()));
            evidence("001c-native.json",new JSONObject().put("scope","ANDROID_TV_EMULATOR_REAL_HTTPS")
                    .put("activityClosed",true).put("publicIdentityPresent",false).put("nativeCacheVerified",true)
                    .put("commandId",prepared.binding.command).put("revisionId",prepared.binding.revision)
                    .put("packageSha256",prepared.binding.digest).put("audioResources",prepared.track.audio.size())
                    .put("text",preferences.text()).put("audio",preferences.audio()));
            await(()->new File(app.getFilesDir(),"001c-browser-ack").exists(),"FIRST_BROWSER_PROOF_MISSING",35_000);
            JSONObject media=mediaRegression(prepared);
            assertFalse(new PublicViewerPreferences(app).audio());
            evidence("001c-media.json",media.put("persistedText",new PublicViewerPreferences(app).text()).put("persistedAudio",false));
        }finally{instrument.runOnMainSync(()->{initial.finish();CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);});}
    }
}
