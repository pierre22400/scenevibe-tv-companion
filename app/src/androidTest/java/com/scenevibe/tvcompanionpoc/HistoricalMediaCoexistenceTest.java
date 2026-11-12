package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Qualifie les deux vrais propriétaires Android, leurs caches et une session MEDIA
 * native. Seules les réponses du réseau appartiennent au banc de qualification.
 */
@RunWith(AndroidJUnit4.class)
public final class HistoricalMediaCoexistenceTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private MediaSession media;

    /** Attend un fait publié par le service sans modifier sa cadence de sondage. */
    private void await(BooleanSupplier predicate,String message) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+35_000;
        while(SystemClock.elapsedRealtime()<deadline){
            java.util.concurrent.atomic.AtomicBoolean observed=new java.util.concurrent.atomic.AtomicBoolean();
            instrument.runOnMainSync(()->observed.set(predicate.getAsBoolean()));
            if(observed.get())return;
            Thread.sleep(150);
        }
        PublicCompanionOwner owner=PublicCompanionService.current();
        fail(message+" · "+(owner==null?"SERVICE_ABSENT":owner.status()));
    }

    /** Utilise l'horloge de l'œuvre possédée par le banc et les vraies API Android MEDIA. */
    private void playback(int state,long position){
        media.setPlaybackState(new PlaybackState.Builder()
                .setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());
    }

    /** Le port de qualification sert les paquets exacts et mémorise les reçus du vrai service. */
    private static final class ControlledPublic implements PublicCloudClient.Transport {
        final PublicNativeFixture fixture;
        private byte[] packageBytes;
        private JSONObject command;
        volatile String waiting="";
        volatile String armed="";
        volatile int transfers;
        volatile boolean lastText,lastAudio;

        /** Réutilise le credential historique et les actifs immuables de la fixture public existante. */
        ControlledPublic(PublicNativeFixture fixture,JSONObject identity,String token) throws Exception {
            this.fixture=fixture;
            fixture.device=identity.getString("deviceId");
            fixture.generation=identity.getString("generation");
            fixture.token=token;
            fixture.claimed=true;
        }

        /** Émet une nouvelle commande pour la même Track et un binding de révision différent. */
        synchronized String send(boolean revision) throws Exception {
            JSONObject payload=new JSONObject(new String(fixture.profile,StandardCharsets.UTF_8));
            JSONObject release=payload.getJSONObject("identity");
            if(revision)release.put("revisionId","pc3-revision-"+UUID.randomUUID().toString().replace("-",""));
            packageBytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            String id=UUID.randomUUID().toString();
            command=new JSONObject().put("profile","scenevibe.public-tv-command.v1").put("commandId",id)
                    .put("deviceId",fixture.device).put("generation",fixture.generation).put("trackId",release.getString("trackId"))
                    .put("revisionId",release.getString("revisionId")).put("packageSha256",MultimediaDeliveryProfile.hash(packageBytes))
                    .put("state","requested").put("text",false).put("audio",false).put("version",0)
                    .put("expiresAt",Instant.now().plusSeconds(900).toString());
            waiting="";
            return id;
        }

        /** Autorise uniquement la possession attendue et les opérations fermées du récepteur. */
        @Override public synchronized byte[] request(String action,JSONObject body,String token) throws Exception {
            assertTrue("POSSESSION_HISTORIQUE_MODIFIÉE",fixture.token.equals(token));
            assertEquals(fixture.device,body.getString("deviceId"));
            assertEquals(fixture.generation,body.getString("generation"));
            if("association".equals(action))return fixture.request(action,body,token);
            if("poll".equals(action))return bytes(new JSONObject().put("mediaWaitSupported",true)
                    .put("commands",command==null?new JSONArray():new JSONArray().put(new JSONObject(command.toString())))
                    .put("automaticCommandIds",command==null?new JSONArray():new JSONArray().put(command.getString("commandId"))));
            if("validate-cache".equals(action))return bytes(new JSONObject(body.toString()).put("status","VALIDATED"));
            assertNotNull(command);
            assertEquals(command.getString("commandId"),body.getString("commandId"));
            if("package".equals(action)){transfers++;return packageBytes.clone();}
            if("resource".equals(action))return fixture.resources.get(fixture.resourceIds.get(body.getString("resourceId"))).clone();
            assertEquals(command.getString("revisionId"),body.getString("revisionId"));
            assertEquals(command.getString("packageSha256"),body.getString("packageSha256"));
            assertEquals(command.getInt("version"),body.getInt("expectedVersion"));
            if("media-wait".equals(action)){
                waiting=body.getString("waitReason");
                return bytes(new JSONObject().put("status","WAITING").put("commandId",body.getString("commandId")).put("waitReason",waiting));
            }
            assertEquals("receipt",action);
            String state=body.getString("state");
            command.put("state",state).put("version",command.getInt("version")+1);
            if("armed".equals(state)){
                lastText=body.getBoolean("text");lastAudio=body.getBoolean("audio");
                command.put("text",lastText).put("audio",lastAudio);
                armed=command.getString("commandId");
            }
            return bytes(command);
        }

        /** Encode les projections fermées du port de test. */
        private static byte[] bytes(JSONObject value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    }

    /** Préserve la session privée, diffère une révision publique puis reprend sans action TV. */
    @Test public void privateSessionAndPinnedPublicRevisionStayIsolated() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.CLOUD_ORIGIN.isEmpty());
        var target=instrument.getTargetContext();
        UiDevice remote=UiDevice.getInstance(instrument);
        assertEquals("1",remote.executeShellCommand("getprop ro.kernel.qemu").trim());
        remote.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        ManualNativeFixture privateFixture=new ManualNativeFixture();
        PublicNativeFixture publicFixture=new PublicNativeFixture();
        TvFirst008State state=new TvFirst008State(target);
        String token="Q".repeat(43);
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",privateFixture.binding.device)
                .put("generation",privateFixture.generation).put("deviceToken",token));
        JSONObject identity=state.read("identity");
        var privatePrefs=target.getSharedPreferences("multimedia_user_preferences",0);
        assertTrue(privatePrefs.edit().putBoolean("text",true).putBoolean("audio",true).putBoolean("adsync",false).commit());
        PublicViewerPreferences viewerPrefs=new PublicViewerPreferences(target);
        viewerPrefs.initialize(null);
        assertTrue(viewerPrefs.setAudio(false));
        ControlledPublic cloud=new ControlledPublic(publicFixture,identity,token);
        PublicCompanionOwner owner=PublicCompanionOwner.ownedHistoricalFixture(target,state,cloud);
        TvFirst008Source source=new TvFirst008Source(target,state,(method,path,bearer,body)->{
            assertTrue(token.equals(bearer));
            if(path.contains("/association"))return new JSONObject(identity.toString()).put("profile",TvFirst008Wire.PROFILE)
                    .put("accountAssociated",true).put("rendering","FORBIDDEN")
                    .put("consentExpiresAt",Instant.now().plusSeconds(3600).toString())
                    .put("currentRevision",privateFixture.binding.revision-1);
            if(path.contains("/library"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE)
                    .put("items",new JSONArray()).put("next",JSONObject.NULL);
            throw new IllegalArgumentException("PRIVATE_FIXTURE_ROUTE");
        },"https://owned-fixture.invalid",Executors.newSingleThreadExecutor());
        Activity activity=instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            instrument.runOnMainSync(source::resume);
            await(()->source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED && !source.busy(),"PRIVATE_ASSOCIATION_MISSING");
            AtomicReference<MultimediaPrivateCache.Prepared> installed=new AtomicReference<>();
            instrument.runOnMainSync(()->source.downloadMultimedia(privateFixture.binding.publication,installed::set,privateFixture.port));
            await(()->installed.get()!=null && !source.busy(),"PRIVATE_CACHE_MISSING");
            String privateDigest=installed.get().binding.sha256;
            instrument.runOnMainSync(()->{
                CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);
                media=new MediaSession(target,"001-C owned coexistence");
                media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                        .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
                playback(PlaybackState.STATE_PAUSED,30_000);media.setActive(true);
                assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(activity,source,installed.get(),true));
            });
            await(()->TvFirst009TextServiceOwner.multimediaActive() && TvFirst009TextServiceOwner.publicCommand()==null,"PRIVATE_MEDIA_MISSING");
            String first=cloud.send(false);
            instrument.runOnMainSync(()->{
                PublicCompanionService.useOwnedFixture(owner);
                activity.startForegroundService(new Intent(target,PublicCompanionService.class));
            });
            await(()->"private-media".equals(cloud.waiting),"PRIVATE_WAIT_RECEIPT_MISSING");
            assertEquals(0,cloud.transfers);
            assertNull(publicFixture.cache().read());
            assertEquals(privateDigest,MultimediaAndroidBridge.restoreCache(target,source).binding.sha256);
            assertEquals(identity.toString(),state.read("identity").toString());
            assertTrue(token.equals(state.token(identity)));
            assertTrue(privatePrefs.getBoolean("audio",false));
            assertFalse(viewerPrefs.audio());
            await(()->activity.hasWindowFocus() && TvFirst009TextServiceOwner.multimediaTrackPosition()==30_000,"PRIVATE_FOCUS_OR_CLOCK_CHANGED");
            try(var bench=new CompanionShortcutBench(instrument)){
                bench.systemShortcut();
                bench.await(TvFirst009TextServiceOwner::developerHasFocus,"PRIVATE_MUTE_WINDOW_MISSING",10000);
                bench.remote.pressBack();
                bench.await(activity::hasWindowFocus,"PRIVATE_MUTE_FOCUS_NOT_RESTORED",10000);
            }
            assertEquals(0,cloud.transfers);
            instrument.runOnMainSync(()->TvFirst009TextServiceOwner.requestStop(target));
            await(()->first.equals(cloud.armed),"PUBLIC_RESUME_NOT_ARMED");
            assertTrue(cloud.lastText);assertFalse(cloud.lastAudio);
            var old=publicFixture.cache().read().binding;
            instrument.runOnMainSync(()->playback(PlaybackState.STATE_PLAYING,30_000));
            await(()->first.equals(TvFirst009TextServiceOwner.publicCommand()) && !TvFirst009TextServiceOwner.publicMayYield(),"PUBLIC_MEDIA_NOT_PINNED");
            int transfers=cloud.transfers;
            String next=cloud.send(true);
            await(()->"public-media".equals(cloud.waiting),"PUBLIC_REVISION_WAIT_MISSING");
            assertEquals(transfers,cloud.transfers);
            assertEquals(old.digest,publicFixture.cache().read().binding.digest);
            await(()->first.equals(TvFirst009TextServiceOwner.publicCommand()) && activity.hasWindowFocus(),"PUBLIC_REVISION_INTERRUPTED_PLAYBACK");
            instrument.runOnMainSync(()->playback(PlaybackState.STATE_STOPPED,30_000));
            await(()->next.equals(cloud.armed),"PUBLIC_REVISION_NOT_RESUMED");
            assertNotEquals(old.revision,publicFixture.cache().read().binding.revision);
            assertEquals(privateDigest,MultimediaAndroidBridge.restoreCache(target,source).binding.sha256);
            assertTrue(privatePrefs.getBoolean("audio",false));assertFalse(viewerPrefs.audio());
            JSONObject proof=new JSONObject().put("privateWait",true).put("privateCacheAndKeystore",true)
                    .put("privateMuteAndFocus",true).put("publicResume",true).put("revisionPinned",true)
                    .put("revisionResume",true).put("privateAudio",true).put("publicAudio",false);
            Files.write(new File(target.getFilesDir(),"001c-coexistence.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
        }finally{
            instrument.runOnMainSync(()->{
                target.stopService(new Intent(target,PublicCompanionService.class));
                TvFirst009TextServiceOwner.requestStop(target);
                PublicCompanionService.useOwnedFixture(null);
                if(media!=null)media.release();
                CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);
                source.close();activity.finish();
            });
            owner.close();
        }
    }
}
