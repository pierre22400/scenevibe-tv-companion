package com.scenevibe.tvcompanionpoc;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.time.Instant;
import java.util.HashSet;
import java.util.concurrent.Executors;
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
 * ART/Android TV : cache disque et SharedPreferences réels, MediaCodec réel,
 * trois MP3 distincts. Le réseau de fixture est synthétique et n'autorise aucun
 * lecteur Prime, compte fournisseur ou activation Sony.
 */
@RunWith(AndroidJUnit4.class)
public final class ManualTrackAndroidTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private ManualNativeFixture fixture;
    private TvFirst008Source source;
    private MainActivity activity;
    private MediaSession media;
    private volatile long currentRevision;
    /** Construit seulement les ressources de cette instrumentation. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.REAL_TRACK_E2E_001 && BuildConfig.CLOUD_ORIGIN.isEmpty());
        assertEquals("Emulator-only qualification", "1", UiDevice.getInstance(instrument).executeShellCommand("getprop ro.kernel.qemu").trim());
        fixture=new ManualNativeFixture();
    }
    /** Nettoie uniquement la génération synthétique de ce test. */
    @After public void after() throws Exception {
        if(fixture==null)return;
        main(()->{
            TvFirst009TextServiceOwner.requestStop(fixture.target);
            if(media!=null)media.release();
            CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);
            if(activity!=null)activity.finish();
            if(source!=null)source.close();
        });
        ManualNativeFixture.remove(new File(fixture.target.getFilesDir(),"multimedia003d/"+fixture.generation.replace("-","")));
        fixture.cleanup();
    }

    /** Utilise le véritable Looper propriétaire Android, jamais une horloge injectée au calendrier. */
    private void main(Runnable work){instrument.runOnMainSync(work);}

    /** Attend les effets réellement publiés par le worker ou le service, avec un échec borné. */
    private void await(BooleanSupplier condition,String message,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline){
            AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(condition.getAsBoolean()));
            if(ready.get())return;Thread.sleep(50);
        }
        fail(message+"; service="+TvFirst009TextServiceOwner.status().code);
    }

    /** Répond seulement aux coordonnées de consentement du compte synthétique du banc. */
    private JSONObject association(String method,String path,String token,JSONObject body) throws Exception {
        if(path.contains("/association"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE)
                .put("deviceId",fixture.binding.device).put("generation",fixture.generation)
                .put("accountAssociated",true).put("rendering","FORBIDDEN")
                .put("consentExpiresAt",Instant.now().plusSeconds(3600).toString()).put("currentRevision",currentRevision);
        if(path.contains("/library"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE)
                .put("items",new JSONArray()).put("next",JSONObject.NULL);
        throw new IllegalArgumentException("MANUAL_OWNED_ASSOCIATION_ROUTE_REFUSED");
    }

    /** Publie le temps de notre MediaSession ; la sonde de production reste responsable de sa lecture. */
    private void playback(int state,long position){
        media.setPlaybackState(new PlaybackState.Builder()
                .setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());
    }

    /** Exerce la nouvelle admission et les six effets via le vrai service et les décodeurs Android. */
    @Test public void actualManualAdmissionStartsThreeTextsAndThreeAudioResources() throws Exception {
        var target=fixture.target;
        TvFirst008State state=new TvFirst008State(target.getSharedPreferences("realtrack001_identity_"+fixture.generation,0),
                new SecretStore.AndroidKeyStoreSecretStore(target));
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("deviceToken","QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ"));
        currentRevision=fixture.binding.revision-1;
        source=new TvFirst008Source(target,state,this::association,"https://owned-fixture.invalid",Executors.newSingleThreadExecutor());
        main(source::resume);
        await(()->!source.busy() && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED,"Current fixture grant missing",15000);
        AtomicReference<MultimediaPrivateCache.Prepared> downloaded=new AtomicReference<>();
        main(()->source.downloadMultimedia(fixture.binding.publication,downloaded::set,fixture.port));
        await(()->downloaded.get()!=null && !source.busy(),"Manual cache download not confirmed",20000);
        var prepared=downloaded.get();currentRevision=prepared.binding.revision;
        assertEquals(6,prepared.track.calendar.events().size());assertFalse(CloudMultimediaAdmission.mapped(prepared.profile));
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());
        AtomicReference<TvFirst008Source.MultimediaReference> reference=new AtomicReference<>();
        main(()->reference.set(source.confirmedMultimediaReference(prepared)));
        assertNotNull(reference.get());assertNull(CloudMultimediaAdmission.admit(reference.get(),false));

        UiDevice remote=UiDevice.getInstance(instrument);
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        activity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        main(()->{
            CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);
            TvFirst009TextServiceOwner.setMultimediaTextEnabled(true);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(true);
            TvFirst009TextServiceOwner.setAdSyncEnabled(true);
            media=new MediaSession(target,"SceneVibe REAL TRACK 001 OWN public session, never Prime");
            media.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            playback(PlaybackState.STATE_PAUSED,0);media.setActive(true);
        });
        await(()->NotificationAccess.isGranted(target),"Owned native MEDIA listener missing",10000);
        main(()->assertTrue("Manual reference admission refused",TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,prepared,true)));
        await(TvFirst009TextServiceOwner::multimediaActive,"Manual OverlayService did not start",15000);
        int texts=0,audios=0,downloads=fixture.port.audioRequests;
        for(SceneEvent event:prepared.track.calendar.events()){
            long before=event.startMs()-1000;main(()->playback(PlaybackState.STATE_PAUSED,before));
            await(()->"MEDIA_PAUSED".equals(TvFirst009TextServiceOwner.status().code)
                    && TvFirst009TextServiceOwner.status().positionMs==before,"Owned paused coordinate missing",10000);
            main(()->playback(PlaybackState.STATE_PLAYING,before));
            if(prepared.track.audio.containsKey(event.eventId())){
                int expected=++audios;await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=expected,"Manual MP3 not natively STARTED",12000);
            }else{
                int expected=++texts;await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=expected,"Manual TEXT not attached",12000);
            }
        }
        assertEquals(3,texts);assertEquals(3,audios);
        assertEquals(3,TvFirst009TextServiceOwner.multimediaTextRequested());assertEquals(3,TvFirst009TextServiceOwner.multimediaAudioStarted());
        assertEquals(3,TvFirst009TextServiceOwner.multimediaAudioPreparations());assertEquals(downloads,fixture.port.audioRequests);
        assertEquals(0,fixture.store.read().acknowledgedRevision());
        JSONObject proof=new JSONObject().put("scope","ACTUAL_MANUAL_ADMISSION_AND_ANDROID_SERVICE_OWN_MEDIA_NOT_PRIME_SONY")
                .put("events",6).put("textWindowsAttached",texts).put("nativeAudioResourcesStarted",audios)
                .put("extraDownloads",fixture.port.audioRequests-downloads).put("historicalAck",0)
                .put("admissionNeedsOwnerConsent",true).put("historicalCorpusMapping",false);
        Files.write(new File(target.getFilesDir(),"realtrack001-service-proof.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
        if(BuildConfig.COMPANION_MUTE_002){CompanionShortcutOpenChecks.exercise(instrument,media);CompanionMuteNativeChecks.exercise(instrument,media,"private");}
    }
    /** Décode et relit les six événements après une publication atomique Android. */
    @Test public void nativeDecodeAndOfflineReadbackPreserveAllSixEvents() throws Exception {
        var prepared=fixture.download();var restored=fixture.reopen().read();
        assertTrue(BuildConfig.REAL_TRACK_E2E_001);assertTrue(ManualTrackIdentity.matches(restored.profile));
        assertEquals(6,restored.track.calendar.events().size());assertEquals(3,restored.track.audio.size());
        assertEquals(3,restored.track.originalManifest.scenes.size());assertEquals(3,fixture.port.audioRequests);
        assertEquals(1,fixture.port.receipts);assertEquals(0,fixture.store.read().acknowledgedRevision());
        var hashes=new HashSet<String>();
        for(var row:restored.profile.resources){byte[] bytes=Files.readAllBytes(restored.resource(row.id).toPath());
            assertEquals(row.sha256,MultimediaDeliveryProfile.hash(bytes));hashes.add(row.sha256);
            assertEquals(3000,new AndroidStudioAudioInspector(fixture.target).decodedDurationMs(bytes));}
        assertEquals(3,hashes.size());assertArrayEquals(prepared.profile.runtimeBytes,restored.profile.runtimeBytes);
        assertArrayEquals(prepared.profile.manifestBytes,restored.profile.manifestBytes);
    }
    /** Une panne à mi-transfert conserve le cache précédent intégral et son reçu. */
    @Test public void interruptedReplacementKeepsThePreviousCompleteCache() throws Exception {
        var first=fixture.download();fixture.replacement();fixture.port.fault="truncate";
        try{fixture.download();fail("Truncated replacement accepted");}catch(Exception expected){assertNotNull(expected);}
        var restored=fixture.reopen().read();assertTrue(first.binding.same(restored.binding));assertEquals(6,restored.track.calendar.events().size());
        fixture.port.fault="";var retried=fixture.download();assertEquals(first.binding.revision+1,retried.binding.revision);
        assertEquals(6,retried.track.calendar.events().size());
    }
}
