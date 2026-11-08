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

/** Première phase : vrai cache et credential chiffré conservés au-delà du processus Android. */
@RunWith(AndroidJUnit4.class)
public final class PublicRestartSeedTest {
    /** Persiste une révision publique et ses préférences, sans seconde saisie de code future. */
    @Test public void persistPublicCacheAndAssociationBeforeProcessRestart() throws Exception {
        PublicNativeFixture fixture=new PublicNativeFixture("public001b_restart");fixture.pair();fixture.download();
        fixture.state.write("publicPreferences",new JSONObject().put("commandId",fixture.command.getString("commandId")).put("text",false).put("audio",false));
        JSONObject proof=new JSONObject().put("seedPid",Process.myPid()).put("binding",fixture.cache().read().binding.json()).put("claimedCodes",fixture.claims);
        Files.write(new File(fixture.target.getFilesDir(),"public001b-restart.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(3,fixture.audioRequests);assertEquals(1,fixture.claims);fixture.close(false);
    }
}
