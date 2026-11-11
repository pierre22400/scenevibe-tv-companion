package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Seed the frozen signed M5 v31 on a disposable emulator only. The real cache,
 * encrypted credential and historical preference store must survive install -r.
 */
@RunWith(AndroidJUnit4.class)
public final class CompanionUpgradeSeedTest {
    /** Persist only owned fixture data through v31's real Android classes before the update. */
    @Test public void seedFrozenSignedV31() throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();Context target=instrument.getTargetContext();
        assertEquals("1",UiDevice.getInstance(instrument).executeShellCommand("getprop ro.kernel.qemu").trim());
        assertEquals(31,target.getPackageManager().getPackageInfo(target.getPackageName(),0).getLongVersionCode());
        assertTrue(android.provider.Settings.canDrawOverlays(target));assertTrue(NotificationAccess.isGranted(target));
        ManualNativeFixture fixture=new ManualNativeFixture();var prepared=fixture.download();
        TvFirst008State state=new TvFirst008State(target.getSharedPreferences("mute002_upgrade_identity",0),new SecretStore.AndroidKeyStoreSecretStore(target));
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",fixture.binding.device)
                .put("generation",fixture.generation).put("deviceToken","QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ"));
        assertTrue(target.getSharedPreferences("multimedia_user_preferences",0).edit().putBoolean("text",false).putBoolean("audio",true).putBoolean("adsync",false).commit());
        JSONObject proof=new JSONObject().put("root",fixture.root.getAbsolutePath()).put("namespace",fixture.namespace)
                .put("device",fixture.binding.device).put("generation",fixture.generation).put("digest",prepared.binding.sha256)
                .put("seedVersion",31).put("textCount",prepared.track.originalManifest.scenes.size()).put("audioCount",prepared.track.audio.size());
        Files.write(new File(target.getFilesDir(),"mute002-upgrade-seed.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("MUTE002_SIGNED_V31_SEEDED; CACHE_3_TEXT_3_AUDIO; ENCRYPTED_CREDENTIAL; EXISTING_PERMISSIONS");
    }
}
