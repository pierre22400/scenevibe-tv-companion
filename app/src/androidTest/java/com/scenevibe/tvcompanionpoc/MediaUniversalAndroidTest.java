package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Qualifie v41 sur Android TV réel émulé. Le fournisseur Prime et les offres
 * Cloud appartiennent au banc. Le cache, les permissions, le service, les
 * jetons MEDIA, les fenêtres et le lecteur MP3 sont ceux d'Android.
 */
@RunWith(AndroidJUnit4.class)
public final class MediaUniversalAndroidTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrument.getTargetContext();

    /** Un code supplémentaire confirme le même appareil sans écrire la possession Android. */
    @Test public void secondBrowserKeepsExistingDeviceCredential() throws Exception {
        PublicNativeFixture fixture=new PublicNativeFixture();
        try{
            fixture.pair();
            String identity=fixture.state.read("identity").toString();
            String challengeId=UUID.randomUUID().toString();
            String challengeSecret="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
            JSONObject pending=new JSONObject().put("challengeId",challengeId)
                    .put("challengeSecret",challengeSecret).put("mode","add-browser");
            PublicCloudClient client=new PublicCloudClient(fixture.state,(action,body,bearer)->{
                JSONObject response;
                if("challenge".equals(action)){
                    assertEquals(fixture.token,bearer);
                    assertEquals(fixture.generation,body.getString("generation"));
                    assertEquals(fixture.device,body.getString("deviceId"));
                    response=new JSONObject().put("challengeId",challengeId).put("userCode","123456");
                }else{
                    assertEquals("challenge-status",action);
                    assertEquals(challengeSecret,body.getString("challengeSecret"));
                    response=new JSONObject().put("status","claimed").put("deviceId",fixture.device).put("generation",fixture.generation);
                }
                return response.toString().getBytes(StandardCharsets.UTF_8);
            });
            assertEquals("123456",client.browserChallenge(pending).getString("userCode"));
            assertTrue(client.browserClaimed(pending));
            assertEquals(identity,fixture.state.read("identity").toString());
            assertEquals(fixture.token,fixture.state.token(fixture.state.read("identity")));
        }finally{fixture.close(true);}
    }

    /** Produit un paquet synthétique dont le seul identifiant d'œuvre est absent. */
    static byte[] unidentified(byte[] bytes,boolean studio) throws Exception {
        JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        JSONObject identity=envelope.getJSONObject("identity");
        JSONObject track=decoded(envelope,"trackBase64"),runtime=decoded(envelope,"runtimeBase64");
        track.getJSONObject("target").getJSONObject("program").put("programId","");
        runtime.getJSONObject("mediaIdentity").put("videoId","");
        if(studio){
            String id="studio-private:"+UUID.randomUUID();
            track.put("trackId",id);runtime.put("trackId",id);identity.put("trackId",id);
            JSONObject manifest=decoded(envelope,"manifestBase64");
            manifest.getJSONObject("source").put("sourceId",id);
            envelope.put("manifestSha256",encode(envelope,"manifestBase64",manifest));
        }
        envelope.put("runtimeSha256",encode(envelope,"runtimeBase64",runtime));
        identity.put("snapshotSha256",encode(envelope,"trackBase64",track));
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Décode la projection figée fournie par le banc. */
    private static JSONObject decoded(JSONObject envelope,String field) throws Exception {
        return new JSONObject(new String(Base64.getDecoder().decode(envelope.getString(field)),StandardCharsets.UTF_8));
    }

    /** Met à jour octets et empreinte de la seule projection synthétique. */
    private static String encode(JSONObject envelope,String field,JSONObject document) throws Exception {
        byte[] data=document.toString().getBytes(StandardCharsets.UTF_8);
        envelope.put(field,Base64.getEncoder().encodeToString(data));
        return MultimediaDeliveryProfile.hash(data);
    }

    /** Ouvre l'Activity d'admission réelle sur l'unique AVD jetable autorisé. */
    private DiagnosticsActivity launch() throws Exception {
        assertTrue(BuildConfig.MEDIA_UNIVERSAL);
        assertEquals(BuildConfig.VERSION_NAME.endsWith("-qr-browser-v42")?42:41,target.getPackageManager().getPackageInfo(target.getPackageName(),0).getLongVersionCode());
        assertTrue(android.provider.Settings.canDrawOverlays(target));
        assertTrue(NotificationAccess.isGranted(target));
        return (DiagnosticsActivity)instrument.startActivitySync(new Intent(target,DiagnosticsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    /** Attend les événements du Looper de production avec un délai borné. */
    private void await(BooleanSupplier ready,String message) throws Exception {
        long until=SystemClock.elapsedRealtime()+25_000L;
        while(SystemClock.elapsedRealtime()<until){
            AtomicBoolean ok=new AtomicBoolean();
            instrument.runOnMainSync(()->ok.set(ready.getAsBoolean()));
            if(ok.get())return;
            Thread.sleep(150);
        }
        fail(message+" code="+TvFirst009TextServiceOwner.status().code);
    }

    /** Pilote uniquement le lecteur synthétique séparé qui fournit son PlaybackState Android. */
    private static void media(UiDevice remote,long position,boolean playing,String identity,String flag) throws Exception {
        String command="am start -W -n com.amazon.amazonvideo.livingroom/.OwnedPlayer --el position "+position
                +" --ez playing "+playing+" --es identity "+identity;
        if(!flag.isEmpty())command+=" --ez "+flag+" true";
        assertTrue(remote.executeShellCommand(command).contains("Status: ok"));
    }

    /** Vérifie texte et MP3 avec une captation native dont les métadonnées sont absentes ou divergentes. */
    private void render(UiDevice remote,StudioMultimediaPackage prepared) throws Exception {
        int texts=0,audios=0;
        for(var event:prepared.calendar.events()){
            long before=Math.max(0,event.startMs()-1000);
            String identity=(texts+audios)%2==0?"missing":"different";
            media(remote,before,false,identity,"");
            await(()->TvFirst009TextServiceOwner.status().positionMs==before,"Position MEDIA en pause");
            media(remote,before,true,identity,"");
            if(prepared.audio.containsKey(event.eventId())){
                int expected=++audios;await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=expected,"AUDIO natif");
            }else {
                int expected=++texts;
                await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=expected
                        && TvFirst009TextServiceOwner.multimediaTextAttached(),"Fenêtre TEXT native");
            }
        }
        assertEquals(3,texts);
        assertEquals(3,audios);
    }

    /** Vérifie le vrai cache Q2, les protections d'horloge et le changement réel de jeton. */
    @Test public void privateQ2AndSessionContinuity() throws Exception {
        UiDevice remote=HistoricalM5ContinuityTest.device(instrument);
        ManualNativeFixture fixture=new ManualNativeFixture(true);
        TvFirst008State state=new TvFirst008State(target.getSharedPreferences("v41_owned_q2_"+UUID.randomUUID(),0),
                new SecretStore.AndroidKeyStoreSecretStore(target));
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("deviceToken","Q".repeat(43)));
        TvFirst008Source source=new TvFirst008Source(target,state,(method,path,token,body)->new JSONObject()
                .put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("accountAssociated",true).put("rendering","FORBIDDEN")
                .put("consentExpiresAt",Instant.now().plusSeconds(3600).toString())
                .put("currentRevision",fixture.binding.revision-1),BuildConfig.STUDIO_PRIVATE_ORIGIN,Executors.newSingleThreadExecutor());
        DiagnosticsActivity activity=launch();
        try{
            instrument.runOnMainSync(source::resume);
            await(()->!source.busy() && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED,"Compte Q2");
            AtomicReference<MultimediaPrivateCache.Prepared> cached=new AtomicReference<>();
            instrument.runOnMainSync(()->source.downloadMultimedia(fixture.binding.publication,cached::set,fixture.port));
            await(()->cached.get()!=null && !source.busy(),"Cache Q2");
            assertTrue(source.studioReceiptMatches(cached.get().binding));
            assertFalse(TvFirst009TextServiceOwner.multimediaActive());
            instrument.runOnMainSync(()->assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,cached.get(),true)));
            await(TvFirst009TextServiceOwner::multimediaActive,"Activation Q2");
            media(remote,0,true,"missing","invalidClock");
            await(()->"MEDIA_CLOCK_UNUSABLE".equals(TvFirst009TextServiceOwner.status().code),"Horloge invalide refusée");
            media(remote,0,true,"different","buffering");
            await(()->"MEDIA_BUFFERING".equals(TvFirst009TextServiceOwner.status().code),"Buffering suspendu");
            media(remote,0,false,"missing","");
            await(()->"MEDIA_PAUSED".equals(TvFirst009TextServiceOwner.status().code),"Pause sur le jeton d’origine");
            media(remote,0,false,"missing","park");
            await(()->"MEDIA_UNAVAILABLE".equals(TvFirst009TextServiceOwner.status().code),"Perte de session");
            render(remote,cached.get().track);
            media(remote,0,true,"missing","newSession");
            await(()->"MEDIA_RESELECT_REQUIRED".equals(TvFirst009TextServiceOwner.status().code),"Autre jeton MEDIA refusé");
            media(remote,2000,true,"different","");
            await(()->TvFirst009TextServiceOwner.status().positionMs<0,"Aucune reprise implicite");
            instrument.runOnMainSync(()->TvFirst009TextServiceOwner.requestStop(target));
            await(()->!TvFirst009TextServiceOwner.controlBusy(),"Arrêt explicite de l’ancienne sélection");
            instrument.runOnMainSync(()->assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,cached.get(),true)));
            await(()->"MEDIA_PLAYING".equals(TvFirst009TextServiceOwner.status().code),"Nouvelle sélection explicite");
            HistoricalM5ContinuityTest.report(instrument,"V41_Q2_PRIVATE_PASS 3_TEXT 3_AUDIO missing_id conflicting_metadata pause_resume buffering invalid_clock transient_loss new_session_hold explicit_reselection");
        }finally{
            instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.requestStop(target);source.close();activity.finish();});
            fixture.cleanup();
        }
    }

    /** Vérifie l'armement public après une commande Viewer et l'absence d'armement à sa seule ouverture. */
    @Test public void publicSelectionAndDeferredReplacement() throws Exception {
        UiDevice remote=HistoricalM5ContinuityTest.device(instrument);
        DiagnosticsActivity activity=launch();
        PublicNativeFixture fixture=new PublicNativeFixture();
        try{
            fixture.profile=unidentified(fixture.profile,false);
            fixture.automatic=true;
            fixture.pair();
            PublicViewerPreferences preferences=new PublicViewerPreferences(target);
            assertTrue(preferences.setText(true));
            assertTrue(preferences.setAudio(true));
            // Le lecteur a sa propre session active, mais aucune commande Viewer n'existe encore.
            media(remote,0,true,"missing","");
            fixture.owner().pollOnce();
            assertFalse(TvFirst009TextServiceOwner.multimediaActive());
            fixture.select();
            fixture.owner().pollOnce();
            fixture.owner().pollOnce();
            await(TvFirst009TextServiceOwner::multimediaActive,"Armement public automatique");
            fixture.owner().pollOnce();
            assertEquals(1,fixture.armReceipts);
            var cached=fixture.cache().read();
            render(remote,cached.track);
            fixture.select();
            fixture.owner().pollOnce();
            assertTrue(fixture.owner().library().stream().anyMatch(row->row.state==PublicLibraryEntry.State.WAITING));
            assertTrue(cached.binding.same(fixture.cache().read().binding));
            HistoricalM5ContinuityTest.report(instrument,"V41_PUBLIC_PASS 3_TEXT 3_AUDIO missing_id conflicting_metadata explicit_viewer_selection no_spontaneous_activation deferred_cache");
        }finally{
            instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.requestStop(target);activity.finish();});
            fixture.close(true);
        }
    }
}
