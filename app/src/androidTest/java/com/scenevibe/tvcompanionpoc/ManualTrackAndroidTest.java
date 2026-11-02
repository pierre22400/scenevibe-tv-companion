package com.scenevibe.tvcompanionpoc;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.file.Files;
import java.util.HashSet;
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
    private ManualNativeFixture fixture;
    /** Construit seulement les ressources de cette instrumentation. */
    @Before public void before() throws Exception {fixture=new ManualNativeFixture();}
    /** Nettoie uniquement la génération synthétique de ce test. */
    @After public void after() throws Exception {if(fixture!=null)fixture.cleanup();}
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
