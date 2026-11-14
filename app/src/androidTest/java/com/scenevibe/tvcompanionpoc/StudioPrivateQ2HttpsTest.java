package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
        } finally {instrument.runOnMainSync(()->{TvFirst009TextServiceOwner.requestStop(target);activity.finish();});}
    }

    /** Met à jour uniquement la session native du lecteur synthétique possédé par le banc. */
    private void player(UiDevice remote,long position,boolean playing) throws Exception {
        String result=remote.executeShellCommand("am start -W -n com.amazon.amazonvideo.livingroom/.OwnedPlayer"
                +" --ez studioQ2 true --el position "+position+" --ez playing "+playing);
        assertTrue("Owned player unavailable",result.contains("Status: ok"));
    }
}
