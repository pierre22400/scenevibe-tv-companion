package com.scenevibe.tvcompanionpoc;

import android.os.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import static org.junit.Assert.*;

/**
 * Deuxième processus Android : le cache est reconstruit depuis les véritables
 * préférences et fichiers, puis les MP3 sont redécodés. Aucun serveur, jeton ou
 * reçu n'est restauré, et cette preuve ne vaut pas redémarrage physique Sony.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudBridge003eRestartReadTest {
    /** Verify a distinct process reads exactly the durable native cache left by Seed. */
    @Test public void restoreAfterTheRealCompanionProcessWasStopped() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();File record=new File(context.getFilesDir(),"cloud003e-restart.json");
        JSONObject data=new JSONObject(new String(Files.readAllBytes(record.toPath()),StandardCharsets.UTF_8));
        assertNotEquals("A process restart is mandatory",data.getInt("seedPid"),Process.myPid());
        assertTrue(data.getString("root").matches("cloud003e-[a-f0-9-]{36}"));
        File root=new File(context.getFilesDir(),data.getString("root"));
        var store=new InstallationStore(new AndroidInstallationBackend(context,data.getString("namespace")));
        var cache=new MultimediaPrivateCache(root,store,new AndroidStudioAudioInspector(context));var restored=cache.read();assertNotNull(restored);
        assertTrue(new MultimediaCloudClient.Binding(data.getJSONObject("binding")).same(restored.binding));
        assertEquals(16,restored.track.originalManifest.scenes.size());assertEquals(3,restored.track.audio.size());assertEquals(19,restored.track.calendar.events().size());
        assertEquals(data.getString("runtimeSha256"),MultimediaDeliveryProfile.hash(restored.profile.runtimeBytes));
        assertEquals(data.getString("manifestSha256"),MultimediaDeliveryProfile.hash(restored.profile.manifestBytes));assertEquals(0,store.read().acknowledgedRevision());
        JSONObject proof=new JSONObject().put("seedPid",data.getInt("seedPid")).put("restorePid",Process.myPid()).put("differentProcess",true)
            .put("text",16).put("audio",3).put("acknowledgedRevision",0).put("mediaAdmissionAuthorized",false);
        Files.write(new File(context.getFilesDir(),"cloud003e-restart-proof.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
    }
}
