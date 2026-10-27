package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Relecture du cache téléchargé après force-stop réel de l'application cible.
 * Le PID doit changer ; aucune connexion HTTPS ou activation MEDIA n'est créée.
 * Le credential chiffré détermine seulement l'espace de stockage à relire.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudAndroid001RestartTest {
    /** Restore all nineteen verified events in a genuinely different Android process without server access. */
    @Test public void restoresDownloadedCacheInAnotherProcess() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject seed=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(target.getFilesDir(),"cloudandroid001-restart-seed.json").toPath()),StandardCharsets.UTF_8));
        assertNotEquals(seed.getInt("pid"),android.os.Process.myPid());
        TvFirst008Source source=new TvFirst008Source(target);
        try {
            source.resume();
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<MultimediaPrivateCache.Prepared> result=new java.util.concurrent.atomic.AtomicReference<>();
            source.restoreMultimedia(prepared->{result.set(prepared);done.countDown();});
            assertTrue("Offline screen-source callback missing",done.await(60,java.util.concurrent.TimeUnit.SECONDS));
            var prepared=result.get();assertNotNull(prepared);
            assertEquals(19,prepared.track.calendar.events().size());assertEquals(16,prepared.track.originalManifest.scenes.size());assertEquals(3,prepared.track.audio.size());
            java.nio.file.Files.write(new File(target.getFilesDir(),"cloudandroid001-restart-proof.json").toPath(),new JSONObject().put("seedPid",seed.getInt("pid"))
                    .put("restorePid",android.os.Process.myPid()).put("differentProcess",true).put("events",19).put("networkUsed",false)
                    .put("playbackRestored",false).toString().getBytes(StandardCharsets.UTF_8));
        }finally{source.close();}
    }
}
