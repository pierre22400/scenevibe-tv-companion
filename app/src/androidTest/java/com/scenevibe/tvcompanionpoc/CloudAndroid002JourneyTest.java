package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

/**
 * Parcours natif 002 par les VRAIS boutons du Companion, HTTPS public de staging,
 * AndroidKeyStore, cache privé et OverlayService. La source de temps est une
 * MediaSession Android appartenant à SceneVibe, explicitement identifiée comme
 * fixture. Aucun processus Prime, flux tiers ou faux package Prime n'est utilisé.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class CloudAndroid002JourneyTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Context target;
    private MainActivity activity;
    private MediaSession fixture;
    private MultimediaPrivateCache.Prepared prepared;
    private JSONObject config;

    /** Launch the actual application without writing credentials or manufacturing a cache/permit. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.CLOUD_MULTIMEDIA_SERVICE);
        target=instrumentation.getTargetContext();
        try(var input=instrumentation.getContext().getAssets().open("cloudandroid002/actor.local.json")){
            var output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int length;
            while((length=input.read(buffer))!=-1)output.write(buffer,0,length);
            config=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
        }
        assertEquals(BuildConfig.CLOUD_ORIGIN,config.getString("origin"));
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->CloudMultimediaAdmission.useOwnedMediaSessionFixture(true));
        main(()->{TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);});
    }

    /** Stop the real service and release only the test's own MediaSession and Activity. */
    @After public void after(){main(()->{TvFirst009TextServiceOwner.requestStop(target);if(fixture!=null)fixture.release();
        CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);if(activity!=null)activity.finish();});}

    /** Execute the genuine Android owner Looper, never a Robolectric shadow or direct runtime observation. */
    private void main(Runnable work){instrumentation.runOnMainSync(work);}

    /** Await the real UI/service, failing promptly on its explicit refusal instead of waiting through a known error. */
    private void await(BooleanSupplier check,String message,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){AtomicBoolean ready=new AtomicBoolean(),refused=new AtomicBoolean();
            main(()->{ready.set(check.getAsBoolean());refused.set(!ready.get() &&
                    (displayed("Connexion ou preuve de cache indisponible") || displayed("Service indisponible. Tu peux réessayer")));});
            if(ready.get())return;if(refused.get())throw new AssertionError(message+"; actual UI reports a closed network/cache refusal; service="+TvFirst009TextServiceOwner.status().code);
            Thread.sleep(100);}
        throw new AssertionError(message+"; service="+TvFirst009TextServiceOwner.status().code);
    }

    /** Locate only an actual visible widget from the current Activity tree. */
    private View find(View root,java.util.function.Predicate<View> match){
        if(root.isShown() && match.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),match);if(result!=null)return result;}}
        return null;
    }

    /** Press a real enabled button, scrolling and focusing it through the existing Android interface. */
    private void click(String prefix) throws Exception {
        await(()->find(activity.getWindow().getDecorView(),view->view instanceof Button && ((Button)view).getText().toString().startsWith(prefix) && view.isEnabled())!=null,
                "Missing enabled UI action: "+prefix,120000);
        main(()->{View button=find(activity.getWindow().getDecorView(),view->view instanceof Button && ((Button)view).getText().toString().startsWith(prefix) && view.isEnabled());
            button.requestRectangleOnScreen(new android.graphics.Rect(0,0,button.getWidth(),button.getHeight()),true);button.requestFocus();assertTrue(button.performClick());});
    }

    /** Read a real UI label; count assertions are taken from verified callback results. */
    private boolean displayed(String fragment){return find(activity.getWindow().getDecorView(),view->view instanceof TextView && ((TextView)view).getText().toString().contains(fragment))!=null;}

    /** Confirm the actual foreground AlertDialog rather than calling its callback or service directly. */
    private void confirm() throws Exception {await(()->activity.hasWindowFocus()==false,"Dialog did not open",10000);
        UiDevice device=UiDevice.getInstance(instrumentation);assertTrue(device.findObject(new androidx.test.uiautomator.UiSelector().resourceId("android:id/button1")).click());}

    /** Claim the displayed challenge with a signed, expiring isolated actor; Android retains the possession secret itself. */
    private int claim(String code,String bearer,String origin) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new java.net.URL(BuildConfig.CLOUD_ORIGIN+"/api/v1/device-activations/claim?profile=tvfirst008-cache-v1").openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(60000);connection.setRequestMethod("POST");connection.setDoOutput(true);
        connection.setRequestProperty("content-type","application/json");connection.setRequestProperty("authorization","Bearer "+bearer);connection.setRequestProperty("origin",origin);
        byte[] body=new JSONObject().put("profile","tvfirst008-cache-v1").put("userCode",code).put("consent",true).toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);try(var output=connection.getOutputStream()){output.write(body);}int status=connection.getResponseCode();connection.disconnect();return status;
    }

    /** Enter the existing Settings page and re-read local data without making it an activation. */
    private void restore() throws Exception {
        click("Réglages");click("Relire le cache multimédia local");
        await(()->displayed("Cache privé vérifié : 16 TEXT + 3 AUDIO"),"Offline/private cache readback missing",120000);
        TvFirst008Source source=new TvFirst008Source(target);try{prepared=MultimediaAndroidBridge.restoreCache(target,source);}finally{source.close();}
        assertNotNull(prepared);assertEquals(19,prepared.track.calendar.events().size());
    }

    /** Publish a genuine Android MediaSession for this app's explicit fixture-only admission target. */
    private void media() throws Exception {
        main(()->{fixture=new MediaSession(target,"SceneVibe002 OWN fixture — never Prime");
            fixture.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,0));fixture.setActive(true);});
        click("Activer TEXT + AUDIO depuis le cache");confirm();
        await(()->TvFirst009TextServiceOwner.status().active,"Real OverlayService did not activate",15000);
    }

    /** Build only the owned fixture's native playback state; the application still reads it through MediaSessionProbe. */
    private PlaybackState state(int value,long position){return new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SEEK_TO)
            .setState(value,position,value==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build();}

    /** Pause at the original coordinate and return the elapsed time immediately before requesting native playback. */
    private long arrive(long start) throws Exception {
        long before=Math.max(0,start-1000);main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,before)));
        await(()->"MEDIA_PAUSED".equals(TvFirst009TextServiceOwner.status().code) && TvFirst009TextServiceOwner.status().positionMs==before,"Native paused observation missing",10000);
        long requested=SystemClock.elapsedRealtime();main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PLAYING,before)));return requested;
    }

    /** Prove selection, actual HTTPS pairing/download and all nineteen events through the real background service. */
    @Test public void a_selectionDownloadAndService() throws Exception {
        click("Mes Tracks");click("Nouvelle association");
        await(()->activity.findViewById(android.R.id.content).findViewWithTag("account-human-code")!=null,"Pairing UI code missing",30000);
        AtomicReference<String> code=new AtomicReference<>();main(()->{TextView label=activity.findViewById(android.R.id.content).findViewWithTag("account-human-code");
            var match=java.util.regex.Pattern.compile("\\b[0-9]{6}\\b").matcher(label.getText());assertTrue(match.find());code.set(match.group());});
        assertEquals(403,claim(code.get(),config.getString("actorToken"),"https://wrong-origin.invalid"));
        assertEquals(401,claim(code.get(),"not-a-signed-identity",BuildConfig.CLOUD_ORIGIN));
        assertEquals(200,claim(code.get(),config.getString("actorToken"),BuildConfig.CLOUD_ORIGIN));
        click("Vérifier la confirmation");await(()->displayed("Compte associé"),"Association not confirmed by Android",30000);
        click("Réglages");click("Charger le catalogue multimédia privé");click("Télécharger : Columbo");long downloadStart=SystemClock.elapsedRealtime();confirm();
        await(()->displayed("Cache privé vérifié : 16 TEXT + 3 AUDIO"),"Staging download/cache not verified",300000);
        long downloadMs=SystemClock.elapsedRealtime()-downloadStart;
        assertFalse(TvFirst009TextServiceOwner.status().active);restore();media();
        UiDevice.getInstance(instrumentation).pressHome();int texts=0,audios=0;org.json.JSONArray audioObservations=new org.json.JSONArray();
        for(SceneEvent event:prepared.track.calendar.events()){
            long playbackRequested=arrive(event.startMs());
            if(prepared.track.audio.containsKey(event.eventId())){final int count=++audios;
                await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=count,"Native MP3 resource not STARTED",10000);
                audioObservations.put(new JSONObject().put("eventId",event.eventId()).put("scheduledStartMs",event.startMs())
                        .put("observedStartedAfterPlayingRequestMs",SystemClock.elapsedRealtime()-playbackRequested)
                        .put("scheduledAfterPlayingRequestMs",1000));
            }else{final int count=++texts;await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=count,"Real TEXT window not attached",10000);
                if(count==1)UiDevice.getInstance(instrumentation).takeScreenshot(new File(target.getFilesDir(),"cloudandroid002-screen.png"));}
        }
        assertEquals(16,texts);assertEquals(3,audios);
        JSONObject proof=new JSONObject().put("events",19).put("textWindowsAttached",texts).put("nativeAudioResourcesStarted",audios)
                .put("service","OverlayService").put("clock","OWN_ANDROID_MEDIASESSION_FIXTURE").put("httpsStaging",BuildConfig.CLOUD_ORIGIN)
                .put("sonyTested",false).put("primeTested",false).put("pid",android.os.Process.myPid());
        proof.put("downloadAndAndroidVerificationMs",downloadMs);
        proof.put("binding",prepared.binding.json()).put("audioObservations",audioObservations)
                .put("audioObservationMethod","Elapsed time to the test observing distinct native STARTED; includes polling, not acoustic latency");
        Files.write(new File(target.getFilesDir(),"cloudandroid002-journey.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Verify real MediaSession pause/resume/seek, duplicate snapshots and independent UI switches on native MP3 playback. */
    @Test public void b_controlsAndIndependentSwitches() throws Exception {
        restore();media();arrive(90000);await(()->"STARTED".equals(TvFirst009TextServiceOwner.multimediaAudioState()),"MP3 not playing",10000);
        AtomicReference<Integer> preparations=new AtomicReference<>();main(()->preparations.set(TvFirst009TextServiceOwner.multimediaAudioPreparations()));
        main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,90500)));
        await(()->"PAUSED".equals(TvFirst009TextServiceOwner.multimediaAudioState()),"Native MP3 did not pause",10000);
        // Seek while paused so the one-second passive sampler cannot let a
        // genuine three-second MP3 finish before the next controlled sample.
        main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,91500)));
        await(()->TvFirst009TextServiceOwner.status().positionMs==91500,"Forward seek missing",10000);
        main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,91000)));
        await(()->"MEDIA_PAUSED".equals(TvFirst009TextServiceOwner.status().code) && TvFirst009TextServiceOwner.status().positionMs==91000,"Backward seek missing",10000);
        // A repeated paused snapshot must not prepare a second native player.
        main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PAUSED,91000)));Thread.sleep(1100);
        main(()->assertEquals((int)preparations.get(),TvFirst009TextServiceOwner.multimediaAudioPreparations()));
        main(()->fixture.setPlaybackState(state(PlaybackState.STATE_PLAYING,91000)));
        await(()->"STARTED".equals(TvFirst009TextServiceOwner.multimediaAudioState()),"Native MP3 did not resume",10000);
        click("Commentaires audio : ON");assertEquals("IDLE",TvFirst009TextServiceOwner.multimediaAudioState());
        arrive(300000);Thread.sleep(1500);main(()->assertEquals((int)preparations.get(),TvFirst009TextServiceOwner.multimediaAudioPreparations()));
        arrive(306665);await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>0,"TEXT was incorrectly disabled by AUDIO OFF",10000);
        click("Commentaires écrits : ON");click("Commentaires audio : OFF");arrive(900000);
        await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=2,"AUDIO was incorrectly disabled by TEXT OFF",10000);
        Files.write(new File(target.getFilesDir(),"cloudandroid002-controls.json").toPath(),new JSONObject().put("pause",true).put("resume",true)
                .put("seekForward",true).put("seekBackward",true).put("duplicatePrepare",false).put("independentSwitches",true).toString().getBytes(StandardCharsets.UTF_8));
    }

    /** A forged/restarted service Intent cannot manufacture the one-use UI handoff or restore playback. */
    @Test public void c_noPermitFromCacheOrIntent() throws Exception {
        restore();assertFalse(TvFirst009TextServiceOwner.status().active);
        main(()->activity.startForegroundService(new Intent(activity,OverlayService.class).setAction(TvFirst009TextServiceOwner.MULTIMEDIA_START)
                .putExtra("text_qualification_request","forged")));
        await(()->"START_PROOF_INVALID".equals(TvFirst009TextServiceOwner.status().code) && !TvFirst009TextServiceOwner.status().active
                && !TvFirst009TextServiceOwner.controlBusy(),"Forged service request retained ownership",15000);
        assertNull(CloudMultimediaAdmission.admit(null,true));assertNull(CloudMultimediaAdmission.admit(null,false));
    }

    /** A newly confirmed selection invalidates the running permit before provider I/O and never auto-starts its replacement. */
    @Test public void d_newSelectionRetiresOldActivation() throws Exception {
        restore();media();arrive(90000);await(()->"STARTED".equals(TvFirst009TextServiceOwner.multimediaAudioState()),"Initial MP3 missing",10000);
        click("Charger le catalogue multimédia privé");click("Télécharger : Columbo");confirm();
        await(()->!TvFirst009TextServiceOwner.status().active,"Previous activation survived a new confirmed intention",15000);
        await(()->displayed("Cache privé vérifié : 16 TEXT + 3 AUDIO"),"Replacement cache not verified",300000);
        assertFalse(TvFirst009TextServiceOwner.status().active);
        JSONObject replacement=new JSONObject().put("oldActivationRetired",true).put("eventsRetained",19).put("replacementAutoStarted",false);
        TvFirst008Source readback=new TvFirst008Source(target);
        try{MultimediaPrivateCache.Prepared actual=MultimediaAndroidBridge.restoreCache(target,readback);assertNotNull(actual);
            replacement.put("binding",actual.binding.json());}finally{readback.close();}
        Files.write(new File(target.getFilesDir(),"cloudandroid002-replacement.json").toPath(),replacement.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** An already-playing native session cannot outrun switches disabled before explicit activation. */
    @Test public void e_disabledSwitchesApplyBeforeFirstSnapshot() throws Exception {
        restore();click("Commentaires écrits : ON");click("Commentaires audio : ON");
        main(()->{fixture=new MediaSession(target,"SceneVibe002 OWN preexisting session");
            fixture.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            fixture.setPlaybackState(state(PlaybackState.STATE_PLAYING,90000));fixture.setActive(true);});
        click("Activer TEXT + AUDIO depuis le cache");confirm();
        await(()->TvFirst009TextServiceOwner.status().active,"Service refused disabled-switch activation",15000);
        Thread.sleep(1500);main(()->{assertEquals(0,TvFirst009TextServiceOwner.multimediaAudioPreparations());
            assertEquals(0,TvFirst009TextServiceOwner.multimediaAudioStarted());assertEquals(0,TvFirst009TextServiceOwner.multimediaTextRequested());});
    }
}
