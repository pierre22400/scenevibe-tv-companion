package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Banc Android TV relié au vrai Preview Q2. Les fichiers, la réception après
 * boot et les sorties natives sont observés sur l'émulateur et son propre MEDIA.
 * Aucun bearer TV ne sort du Keystore.
 */
@RunWith(AndroidJUnit4.class)
public final class StudioPrivateQ2HttpsTest {
    private static final String PROOF_MARKERS="/data/local/tmp/scenevibe-q2d-";
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrument.getTargetContext();

    /** Vérifie les preuves locales sur le Looper qui possède les services. */
    private void await(BooleanSupplier test,String label,long limit) throws Exception {
        long until=SystemClock.elapsedRealtime()+limit;
        while(SystemClock.elapsedRealtime()<until) {
            AtomicBoolean passed=new AtomicBoolean();
            instrument.runOnMainSync(()->passed.set(test.getAsBoolean()));
            if(passed.get())return;
            Thread.sleep(200);
        }
        fail(label+" status="+TvFirst009TextServiceOwner.status().code);
    }

    /** Crée le code comme le bouton Studio Q2, et remet seulement le code éphémère au banc. */
    @Test public void pair() throws Exception {
        HistoricalM5ContinuityTest.device(instrument);
        assertFalse(BuildConfig.STUDIO_PRIVATE_ORIGIN.isEmpty());
        assertEquals(40,target.getPackageManager().getPackageInfo(target.getPackageName(),0).getLongVersionCode());
        var activity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrument.runOnMainSync(()->PublicCompanionService.resumeStudio(activity,true));
            await(()->PublicCompanionService.studio()!=null && !PublicCompanionService.studio().source.busy(),"Q2_RECEIVER_MISSING",25_000);
            instrument.runOnMainSync(()->PublicCompanionService.studio().source.beginPairing(false));
            await(()->PublicCompanionService.studio().source.accountStage()==TvAccountSource.AccountStage.CODE,"Q2_ONE_CODE_MISSING",25_000);
            String display=PublicCompanionService.studio().source.verification();
            String code=display.substring(display.lastIndexOf("Code : ")+7).trim();
            assertTrue(code.matches("[0-9]{6}"));
            HistoricalM5ContinuityTest.report(instrument,"Q2_PAIR_CODE="+code);
            await(()->PublicCompanionService.studio().source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED,"Q2_ASSOCIATION_MISSING",115_000);
        } finally {instrument.runOnMainSync(activity::finish);}
    }

    /** Vérifie le vrai cache rechargé et exerce les deux sorties natives d'une révision. */
    @Test public void render() throws Exception {
        UiDevice remote=HistoricalM5ContinuityTest.device(instrument);
        var activity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        PublicNativeFixture publicFixture=null;
        try {
            int revision=Integer.parseInt(InstrumentationRegistry.getArguments().getString("revision"));
            await(()->{
                StudioPrivateReceiver receiver=PublicCompanionService.studio();
                return receiver!=null && receiver.source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED
                    && !receiver.source.busy() && receiver.prepared()!=null && receiver.prepared().track.revision==revision;
            },"Q2_STUDIO_REVISION_CACHE_MISSING",90_000);
            StudioPrivateReceiver receiver=PublicCompanionService.studio();
            var prepared=receiver.prepared();
            assertEquals(revision+1,prepared.track.originalManifest.scenes.size());
            assertEquals(1,prepared.track.audio.size());
            assertTrue(receiver.source.studioReceiptMatches(prepared.binding));
            AtomicReference<org.json.JSONObject> offered=new AtomicReference<>();
            instrument.runOnMainSync(()->receiver.source.listMultimedia(offered::set));
            await(()->offered.get()!=null && !receiver.source.busy(),"Q2_PRIVATE_LIBRARY_MISSING",20_000);
            boolean found=false;
            for(int index=0;index<offered.get().getJSONArray("items").length();index++) {
                var row=offered.get().getJSONArray("items").getJSONObject(index);
                if(prepared.binding.publication.equals(row.getString("publicationId")))found=true;
            }
            assertTrue("Cached publication absent from authorized private library",found);
            assertFalse(TvFirst009TextServiceOwner.multimediaActive());
            int replacement=Integer.parseInt(InstrumentationRegistry.getArguments().getString("replacement","0"));
            if(replacement>0) {
                publicFixture=new PublicNativeFixture();
                publicFixture.pair();
                publicFixture.download();
                assertTrue(publicFixture.cache().read().track.publicLibraryDetails().contains("3 TEXT · 3 AUDIO"));
                activateFromLibrary(remote,activity,prepared.track.title());
            }
            if(replacement==0)
                instrument.runOnMainSync(()->assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(activity,receiver.source,prepared,true)));
            await(TvFirst009TextServiceOwner::multimediaActive,"Q2_MEDIA_ADMISSION_MISSING",15_000);
            int texts=0,audios=0;
            for(var event:prepared.track.calendar.events()) {
                long before=Math.max(0,event.startMs()-1000);
                player(remote,before,false);
                await(()->TvFirst009TextServiceOwner.status().positionMs==before,"Q2_MEDIA_POSITION_MISSING",15_000);
                player(remote,before,true);
                if(prepared.track.audio.containsKey(event.eventId())) {
                    int count=++audios;
                    await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=count,"Q2_NATIVE_AUDIO_MISSING",15_000);
                } else {
                    int count=++texts;
                    await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=count,"Q2_NATIVE_TEXT_MISSING",15_000);
                }
            }
            assertEquals(revision+1,texts);
            assertEquals(1,audios);
            HistoricalM5ContinuityTest.report(instrument,"Q2D_ANDROID_TEXT_AUDIO_PASS revision="+revision+" text="+texts+" audio="+audios);
            if(replacement>0) verifyReplacement(remote,activity,receiver,prepared,replacement,publicFixture);
        } finally {
            instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.requestStop(target);activity.finish();});
            if(publicFixture!=null)publicFixture.close(true);
        }
    }

    /** Ouvre la vraie carte TV avant de confirmer l'activation locale. */
    private void activateFromLibrary(UiDevice remote,MainActivity activity,String title) throws Exception {
        View root=activity.getWindow().getDecorView();
        instrument.runOnMainSync(()->{
            Button tracks=root.findViewWithTag("nav-TRACKS");
            assertNotNull("Navigation privée manquante",tracks);
            assertEquals("Bibliothèque",tracks.getText().toString());
            assertTrue(tracks.requestFocus());
            assertTrue(tracks.performClick());
        });
        await(()->focusAndClick(root,"Charger mes Tracks privées Q2",false),"Q2_LIBRARY_BUTTON_MISSING",20_000);
        await(()->focusAndClick(root,title+" · révision ",true),"Q2_VERIFIED_LIBRARY_CARD_MISSING",30_000);
        var confirm=remote.wait(Until.findObject(By.text(Pattern.compile("Activer",Pattern.CASE_INSENSITIVE)).clickable(true)),12_000);
        assertNotNull("Confirmation d'activation manquante",confirm);
        assertFalse(TvFirst009TextServiceOwner.multimediaActive());
        confirm.click();
    }

    /** Demande le focus et l'action d'un bouton visible dans la vraie arborescence de l'Activity. */
    private static boolean focusAndClick(View view,String label,boolean cached) {
        if(view instanceof Button button && button.getText().toString().startsWith(label) && button.isEnabled() && button.isShown()) {
            if(cached)assertTrue(button.getText().toString().contains("Cache vérifié"));
            assertTrue(button.requestFocus());
            return button.performClick();
        }
        if(view instanceof ViewGroup group) {
            for(int index=0;index<group.getChildCount();index++)
                if(focusAndClick(group.getChildAt(index),label,cached))return true;
        }
        return false;
    }

    /** Démontre le différé côté MEDIA et la continuité des deux pointeurs, puis vérifie le refus d'un grant révoqué. */
    private void verifyReplacement(UiDevice remote,MainActivity activity,StudioPrivateReceiver receiver,
            MultimediaPrivateCache.Prepared previous,int revision,PublicNativeFixture publicFixture) throws Exception {
        var publicBefore=publicFixture.cache().read().binding;
        boolean text=TvFirst009TextServiceOwner.multimediaTextEnabled(),audio=TvFirst009TextServiceOwner.multimediaAudioEnabled();
        HistoricalM5ContinuityTest.report(instrument,"Q2D_ACTIVE_R2_READY");
        await(()->receiver.status().contains("r"+revision+" en attente"),"Q2_ACTIVE_REVISION_NOT_DEFERRED",75_000);
        assertTrue(TvFirst009TextServiceOwner.multimediaActive());
        assertTrue(previous.binding.same(MultimediaAndroidBridge.restoreCache(target,receiver.source).binding));
        assertTrue(publicBefore.same(publicFixture.cache().read().binding));
        HistoricalM5ContinuityTest.report(instrument,"Q2D_DEFERRED_R3_CACHE_R2_PRESERVED");
        waitMarker(remote,"deferred-checked");
        instrument.runOnMainSync(()->TvFirst009TextServiceOwner.requestStop(activity));
        await(()->!TvFirst009TextServiceOwner.controlBusy() && receiver.prepared()!=null
                && receiver.prepared().track.revision==revision,"Q2_DEFERRED_REVISION_NOT_RESUMED",105_000);
        var replaced=receiver.prepared();
        assertEquals(previous.track.trackId,replaced.track.trackId);
        assertFalse(previous.binding.sha256.equals(replaced.binding.sha256));
        assertTrue(publicBefore.same(publicFixture.cache().read().binding));
        assertEquals(1,cacheCount("public001b"));
        assertEquals(1,cacheCount("multimedia003d"));
        assertEquals(text,TvFirst009TextServiceOwner.multimediaTextEnabled());
        assertEquals(audio,TvFirst009TextServiceOwner.multimediaAudioEnabled());
        refuseCorruptedResource(receiver,replaced);
        HistoricalM5ContinuityTest.report(instrument,"Q2D_REPLACEMENT_AND_TWO_CACHES_PASS");

        var session=receiver.source.multimediaSession();
        String token=session.token();
        String url="/api/v1/devices/"+session.deviceId()+"/multimedia/library?profile="
                +MultimediaDeliveryProfile.PROFILE+"&generation="+session.generation();
        waitMarker(remote,"grant-revoked");
        try (var result=new MultimediaHttpsPort(BuildConfig.STUDIO_PRIVATE_ORIGIN).open("GET",url,token,null)) {
            assertEquals("Private library must reject the revoked device grant",403,result.status);
        }
        await(()->!receiver.source.busy(),"Q2_REVOKED_SOURCE_BUSY",25_000);
        instrument.runOnMainSync(receiver.source::refresh);
        await(()->receiver.source.accountStage()==TvAccountSource.AccountStage.ERROR,"Q2_REVOKED_GRANT_STILL_ACTIVE",25_000);
        instrument.runOnMainSync(()->assertFalse(TvFirst009TextServiceOwner.requestMultimediaStart(activity,receiver.source,replaced,true)));
        assertTrue(replaced.binding.same(MultimediaAndroidBridge.restoreCache(target,receiver.source).binding));
        HistoricalM5ContinuityTest.report(instrument,"Q2D_REVOKED_DEVICE_REFUSED_CACHE_PRESERVED");
    }

    /** Attend un point de rendez-vous sans écrire de token ou de métadonnée utilisateur. */
    private static void waitMarker(UiDevice remote,String name) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+60_000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if("ready".equals(remote.executeShellCommand("cat "+PROOF_MARKERS+name).trim()))return;
            Thread.sleep(200);
        }
        fail("Q2_HOST_PROOF_MARKER_MISSING "+name);
    }

    /** Compte les répertoires finaux natifs sans lire des fichiers d'une autre application. */
    private long cacheCount(String domain) throws Exception {
        try(var entries=Files.walk(new File(target.getFilesDir(),domain).toPath(),2)) {
            return entries.filter(Files::isDirectory)
                    .filter(path->MultimediaCacheDirectories.isSet(path.getFileName().toString())).count();
        }
    }

    /** Vérifie le refus d'un MP3 altéré dans le cache de qualification et restaure les octets dans tous les cas. */
    private void refuseCorruptedResource(StudioPrivateReceiver receiver,MultimediaPrivateCache.Prepared prepared) throws Exception {
        var path=prepared.resource(prepared.profile.resources.get(0).id).toPath();
        byte[] valid=Files.readAllBytes(path);
        byte[] changed=valid.clone();
        changed[0]^=1;
        try {
            Files.write(path,changed);
            try {
                MultimediaAndroidBridge.restoreCache(target,receiver.source);
                fail("Corrupted MP3 accepted");
            } catch(IllegalArgumentException refused) {
                assertNotNull(refused.getMessage());
            }
        } finally {Files.write(path,valid);}
        assertTrue(prepared.binding.same(MultimediaAndroidBridge.restoreCache(target,receiver.source).binding));
        HistoricalM5ContinuityTest.report(instrument,"Q2D_CORRUPTED_MP3_REFUSED");
    }

    /** Met à jour uniquement la session native du lecteur synthétique possédé par le banc. */
    private void player(UiDevice remote,long position,boolean playing) throws Exception {
        String result=remote.executeShellCommand("am start -W -n com.amazon.amazonvideo.livingroom/.OwnedPlayer"
                +" --ez studioQ2 true --el position "+position+" --ez playing "+playing);
        assertTrue("Owned player unavailable",result.contains("Status: ok"));
    }
}
