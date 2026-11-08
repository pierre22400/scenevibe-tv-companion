package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * ART/Android TV réel : association chiffrée, cache Android, MediaCodec,
 * OverlayService, WindowManager et notre MediaSession possédée. Le réseau est
 * une fixture synthétique ; aucun résultat Prime ou Sony n'est revendiqué.
 */
@RunWith(AndroidJUnit4.class)
public final class PublicAutonomousAndroidTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private PublicNativeFixture fixture;
    private MainActivity activity;
    private PublicPairingActivity pairingActivity;
    private MediaSession media;

    /** Refuse tout appareil physique et prépare uniquement un namespace synthétique neuf. */
    @Before public void before() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.PUBLIC_AUTONOMOUS_001B && BuildConfig.CLOUD_ORIGIN.isEmpty());
        assertEquals("1",UiDevice.getInstance(instrument).executeShellCommand("getprop ro.kernel.qemu").trim());
        fixture=new PublicNativeFixture();
    }
    /** Révoque les seuls propriétaires du banc avant nettoyage, sans cache ou identité privés. */
    @After public void after() throws Exception {
        main(()->{TvFirst009TextServiceOwner.requestStop(fixture.target);fixture.target.stopService(new Intent(fixture.target,PublicCompanionService.class));PublicCompanionService.useOwnedFixture(null);
            if(media!=null)media.release();CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);if(activity!=null)activity.finish();if(pairingActivity!=null)pairingActivity.finish();});
        fixture.close(true);
    }
    /** Utilise le Looper Android réel pour tous les effets et observations des services. */
    private void main(Runnable action){instrument.runOnMainSync(action);}
    /** Attend un fait observable avec un échec borné, sans avancer artificiellement l'horloge. */
    private void await(BooleanSupplier condition,String message,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}
        fail(message+"; state="+TvFirst009TextServiceOwner.status().code);
    }
    /** Autorise uniquement les permissions système de l'émulateur pour le banc possédé. */
    private void nativePermissions() throws Exception {
        UiDevice remote=UiDevice.getInstance(instrument);remote.executeShellCommand("appops set "+fixture.target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(fixture.target).flattenToString());
        activity=(MainActivity)instrument.startActivitySync(new Intent(fixture.target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->NotificationAccess.isGranted(fixture.target),"Native listener unavailable",10000);
    }
    /** Publie une vraie session de notre seule application, jamais une session Prime fabriquée. */
    private void session(String mediaId) {
        main(()->{
            CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);
            media=new MediaSession(fixture.target,"OWN PUBLIC 001B — NOT PRIME");
            media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,mediaId)
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            playback(PlaybackState.STATE_PAUSED,0);media.setActive(true);
        });
    }
    /** Le temps MEDIA est fourni par le système et la sonde de production. */
    private void playback(int state,long position){media.setPlaybackState(new PlaybackState.Builder().setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());}
    /** Applique les préférences Web automatiquement, sans aucun appel de validation TV. */
    private void arm() throws Exception {
        fixture.activate(true,true);fixture.owner().pollOnce();
        await(TvFirst009TextServiceOwner::multimediaActive,"Automatic public service not started",15000);
        fixture.owner().pollOnce();assertEquals("armed",fixture.command.getString("state"));
    }

    /** Le premier code accepté suffit ; faux code et rejeu ne créent aucune autre autorité. */
    @Test public void oneCodePairsWithoutSecondApprovalAndUsesAndroidKeyStore() throws Exception {
        fixture.owner().pollOnce();String code=fixture.owner().code();assertTrue(code.matches("[0-9]{6}"));
        assertTrue(fixture.owner().codeExpires()>System.currentTimeMillis());
        try{fixture.claim("000000");fail();}catch(IllegalArgumentException expected){assertEquals("CODE_REFUSED",expected.getMessage());}
        fixture.claim(code);fixture.owner().pollOnce();assertNotNull(fixture.state.read("identity"));assertEquals("",fixture.owner().code());
        try{fixture.claim(code);fail();}catch(IllegalArgumentException expected){assertEquals("CODE_REFUSED",expected.getMessage());}
        assertEquals(1,fixture.claims);
        String metadata=fixture.target.getSharedPreferences(fixture.namespace,0).getAll().toString();assertFalse(metadata.contains(fixture.token));
        assertEquals(fixture.token,fixture.state.token(fixture.state.read("identity")));
    }
    /** Affiche le vrai code dans l'Activity et associe en arrière-plan sans écran d'approbation TV. */
    @Test public void activityDisplaysCodeAndAcceptsPairingWithoutApprovalScreen() throws Exception {
        nativePermissions();main(()->PublicCompanionService.useOwnedFixture(fixture.owner()));
        pairingActivity=(PublicPairingActivity)instrument.startActivitySync(new Intent(fixture.target,PublicPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->((android.widget.TextView)pairingActivity.getWindow().getDecorView().findViewWithTag("public-tv-code")).getText().toString().matches("[0-9]{6}"),"TV code not rendered",15000);
        fixture.claim(fixture.owner().code());
        await(()->"TV associée".contentEquals(((android.widget.TextView)pairingActivity.getWindow().getDecorView().findViewWithTag("public-tv-code")).getText()),"Pair did not complete automatically",15000);
        assertEquals(1,fixture.claims);
        assertFalse(UiDevice.getInstance(instrument).hasObject(androidx.test.uiautomator.By.textContains("Autoriser cet appareil")));
    }
    /** L'expiration renouvelle le challenge non consommé automatiquement, sans compte humain. */
    @Test public void expiredUnclaimedCodeRenewsAutomatically() throws Exception {
        fixture.owner().pollOnce();String first=fixture.state.read("challenge").getString("challengeId");fixture.expired=true;
        fixture.owner().pollOnce();fixture.owner().pollOnce();assertNotEquals(first,fixture.state.read("challenge").getString("challengeId"));
        assertEquals(0,fixture.claims);assertNull(fixture.state.read("identity"));
    }
    /** Les trois MP3 sont décodés sur Android et le pointeur public est relu avant le reçu. */
    @Test public void completeNativeCacheReadbackKeepsPrivateStoreUntouched() throws Exception {
        String privateBefore=fixture.target.getSharedPreferences("tvfirst008_identity",0).getAll().toString();
        fixture.pair();fixture.download();PublicMultimediaCache.Prepared p=fixture.cache().read();
        assertEquals(6,p.track.calendar.events().size());assertEquals(3,p.track.audio.size());assertEquals(3,fixture.audioRequests);assertEquals(1,fixture.cacheReceipts);
        assertEquals(fixture.grantExpires,fixture.command.getString("expiresAt"));
        java.util.Set<String> hashes=new java.util.HashSet<>();
        for(MultimediaDeliveryProfile.Resource r:p.profile.resources){byte[] bytes=Files.readAllBytes(p.resource(r.id).toPath());hashes.add(MultimediaDeliveryProfile.hash(bytes));assertEquals(3000,new AndroidStudioAudioInspector(fixture.target).decodedDurationMs(bytes));}
        assertEquals(3,hashes.size());assertEquals(privateBefore,fixture.target.getSharedPreferences("tvfirst008_identity",0).getAll().toString());
        assertNull(TvFirst009TextServiceOwner.publicCommand());
    }
    /** Une ressource corrompue ou étrangère conserve le cache précédent et n'émet pas de succès. */
    @Test public void corruptedResourceRetainsPreviousCompleteCache() throws Exception {
        fixture.pair();fixture.download();PublicMultimediaCache.Prepared first=fixture.cache().read();fixture.select();fixture.corrupt=true;
        try{fixture.owner().pollOnce();fail();}catch(Exception expected){assertNotNull(expected);}
        assertTrue(first.binding.same(fixture.cache().read().binding));assertEquals(1,fixture.cacheReceipts);assertEquals("failed",fixture.command.getString("state"));
        JSONObject id=fixture.state.read("identity");
        try{fixture.request("resource",new JSONObject().put("deviceId",fixture.device).put("generation",fixture.generation).put("commandId",fixture.command.get("commandId")).put("resourceId","foreign"),fixture.token);fail();}
        catch(IllegalArgumentException expected){assertEquals("RESOURCE_REFUSED",expected.getMessage());}
    }
    /** Une interruption puis reprise ne publie jamais de paquet partiel. */
    @Test public void interruptedTransferCanRetryWithoutErasingCache() throws Exception {
        fixture.pair();fixture.download();PublicMultimediaCache.Prepared first=fixture.cache().read();fixture.select();fixture.truncated=true;
        try{fixture.owner().pollOnce();fail();}catch(Exception expected){assertNotNull(expected);}
        assertTrue(first.binding.same(fixture.cache().read().binding));fixture.truncated=false;fixture.select();fixture.owner().pollOnce();
        assertEquals(2,fixture.cacheReceipts);assertFalse(first.binding.same(fixture.cache().read().binding));
    }
    /** Une révision modifiée et une mauvaise génération sont refusées avant commit. */
    @Test public void foreignRevisionAndGenerationCannotEnterCache() throws Exception {
        fixture.pair();fixture.select();fixture.command.put("revisionId","foreign-revision");
        try{fixture.owner().pollOnce();fail();}catch(Exception expected){assertNotNull(expected);}
        assertNull(fixture.cache().read());assertEquals(0,fixture.cacheReceipts);
        try{fixture.request("poll",new JSONObject().put("deviceId",fixture.device).put("generation",java.util.UUID.randomUUID().toString()),fixture.token);fail();}
        catch(IllegalArgumentException expected){assertEquals("POSSESSION_REFUSED",expected.getMessage());}
    }
    /** Six effets réels sous notre MediaSession : aucune confirmation TV après les préférences Web. */
    @Test public void webActivationRendersThreeTextsAndStartsThreeMp3WithoutTvConfirmation() throws Exception {
        nativePermissions();fixture.pair();fixture.download();session("amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e");arm();
        PublicMultimediaCache.Prepared prepared=fixture.cache().read();int texts=0,audios=0,downloads=fixture.audioRequests;
        for(SceneEvent event:prepared.track.calendar.events()){
            long before=event.startMs()-1000;main(()->playback(PlaybackState.STATE_PAUSED,before));
            await(()->TvFirst009TextServiceOwner.status().positionMs==before,"Paused coordinate unavailable",10000);
            main(()->playback(PlaybackState.STATE_PLAYING,before));
            if(prepared.track.audio.containsKey(event.eventId())){int expected=++audios;await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=expected,"Public MP3 not STARTED",12000);}
            else{int expected=++texts;await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=expected,"Public TEXT not attached",12000);}
        }
        assertEquals(3,texts);assertEquals(3,audios);assertEquals(downloads,fixture.audioRequests);assertEquals(1,fixture.claims);
        JSONObject proof=new JSONObject().put("scope","OWN_ANDROID_MEDIA_SESSION_NOT_PRIME_SONY").put("textWindowsAttached",texts).put("nativeAudioResourcesStarted",audios)
                .put("associationCodesAccepted",fixture.claims).put("extraTvConfirmations",0).put("mediaIdentityGuard",true).put("privateIdentityUntouched",true);
        Files.write(new java.io.File(fixture.target.getFilesDir(),"public001b-native-proof.json").toPath(),proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.activate(false,false);fixture.owner().pollOnce();assertEquals("armed",fixture.command.getString("state"));
        assertFalse(TvFirst009TextServiceOwner.multimediaTextEnabled());assertFalse(TvFirst009TextServiceOwner.multimediaAudioEnabled());
        main(()->TvFirst009TextServiceOwner.setMultimediaTextEnabled(true));fixture.owner().pollOnce();
        assertTrue(fixture.command.getBoolean("text"));assertFalse(fixture.command.getBoolean("audio"));
    }
    /** Un titre identique ne peut contourner un mediaId contradictoire. */
    @Test public void incorrectMediaIdKeepsIdentityBlocked() throws Exception {
        nativePermissions();fixture.pair();fixture.download();session("another-episode");arm();main(()->playback(PlaybackState.STATE_PLAYING,19500));
        await(()->"MEDIA_IDENTITY_BLOCKED".equals(TvFirst009TextServiceOwner.status().code),"Public identity refusal missing",12000);
        assertEquals(0,TvFirst009TextServiceOwner.multimediaTextRequested());assertEquals(0,TvFirst009TextServiceOwner.multimediaAudioStarted());
    }
    /** Une révision demandée pendant la restitution garde le binding et le calendrier actifs. */
    @Test public void updateDuringActiveSessionRetainsInstalledRevision() throws Exception {
        nativePermissions();fixture.pair();fixture.download();session("amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e");arm();
        PublicMultimediaCache.Prepared before=fixture.cache().read();fixture.select();fixture.owner().pollOnce();
        assertEquals("requested",fixture.command.getString("state"));assertTrue(before.binding.same(fixture.cache().read().binding));
        assertEquals(before.binding.command,TvFirst009TextServiceOwner.publicCommand());assertEquals(1,fixture.cacheReceipts);
        main(()->playback(PlaybackState.STATE_STOPPED,0));await(TvFirst009TextServiceOwner::publicMediaEnded,"MEDIA stop not observed",10000);
        fixture.owner().pollOnce();await(()->!TvFirst009TextServiceOwner.controlBusy(),"Previous public session not retired",15000);
        fixture.owner().pollOnce();assertEquals(2,fixture.cacheReceipts);assertFalse(before.binding.same(fixture.cache().read().binding));
    }
}
