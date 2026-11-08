package com.scenevibe.tvcompanionpoc;

import android.os.Process;
import java.nio.file.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

/** Rejouée après force-stop puis reboot de l'émulateur ; aucun appareil physique n'est visé. */
@RunWith(AndroidJUnit4.class)
public final class PublicRestartReadTest {
    /** Relit le cache avec MediaCodec et la même possession, sans téléchargement ou nouveau code. */
    @Test public void restoreWithoutCodeAfterProcessAndAndroidEnvironmentRestart() throws Exception {
        PublicNativeFixture fixture=new PublicNativeFixture("public001b_restart");
        JSONObject seed=new JSONObject(new String(Files.readAllBytes(new File(fixture.target.getFilesDir(),"public001b-restart.json").toPath()),StandardCharsets.UTF_8));
        assertNotEquals(seed.getInt("seedPid"),Process.myPid());
        PublicMultimediaCache.Prepared p=fixture.cache().read();assertEquals(seed.getJSONObject("binding").getString("packageSha256"),p.binding.digest);
        fixture.owner().pollOnce();assertNotNull(fixture.state.read("identity"));assertEquals(0,fixture.audioRequests);assertEquals(0,fixture.claims);
        assertEquals(6,p.track.calendar.events().size());assertEquals(3,p.track.audio.size());
        JSONObject proof=new JSONObject().put("scope","OWN_ANDROID_RESTART_NOT_SONY").put("readPid",Process.myPid()).put("cacheEvents",6)
                .put("nativeDecodedAudio",3).put("newCodes",0).put("extraDownloads",0);
        Files.write(new File(fixture.target.getFilesDir(),"public001b-restart-proof.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));fixture.close(false);
    }
}
