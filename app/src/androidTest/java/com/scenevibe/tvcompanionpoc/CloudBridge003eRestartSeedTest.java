package com.scenevibe.tvcompanionpoc;

import android.os.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

/**
 * Première phase persistante de qualification. Le cache est effectivement commis
 * sur Android et reste présent après la fin de l'instrumentation. La phase Read
 * exige ensuite un autre PID après force-stop du seul package de qualification.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudBridge003eRestartSeedTest {
    /** Persist exact cache coordinates, without a credential, ACK, renderer or admission. */
    @Test public void commitBeforeARealApplicationProcessRestart() throws Exception {
        var fixture=new Native003eFixture();var prepared=fixture.download();assertEquals(19,prepared.track.calendar.events().size());
        JSONObject data=new JSONObject().put("root",fixture.root.getName()).put("namespace",fixture.namespace).put("binding",prepared.binding.json())
            .put("seedPid",Process.myPid()).put("runtimeSha256",MultimediaDeliveryProfile.hash(prepared.profile.runtimeBytes))
            .put("manifestSha256",MultimediaDeliveryProfile.hash(prepared.profile.manifestBytes));
        File record=new File(fixture.target.getFilesDir(),"cloud003e-restart.json");Files.write(record.toPath(),data.toString().getBytes(StandardCharsets.UTF_8));
        assertTrue(record.isFile());assertEquals(0,fixture.store.read().acknowledgedRevision());
    }
}
