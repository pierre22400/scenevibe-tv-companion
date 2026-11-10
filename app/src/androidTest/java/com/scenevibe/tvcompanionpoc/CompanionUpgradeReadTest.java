package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Verify the actual nondebuggable signed v32 preserves v31 data and permissions.
 * No permission grant, re-pairing, settings intent or fallback is permitted here.
 */
@RunWith(AndroidJUnit4.class)
public final class CompanionUpgradeReadTest {
    /** Reopen all durable v31 resources after the standard M5 update, without configuration commands. */
    @Test public void signedV32RetainsV31DataAndPermissions() throws Exception {
        var instrument=InstrumentationRegistry.getInstrumentation();Context target=instrument.getTargetContext();
        assertEquals("1",UiDevice.getInstance(instrument).executeShellCommand("getprop ro.kernel.qemu").trim());
        var pkg=target.getPackageManager().getPackageInfo(target.getPackageName(),0);
        assertEquals(32,pkg.getLongVersionCode());assertTrue(pkg.versionName.contains("companion-mute-integration002-v32"));
        assertEquals(0,target.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE);
        assertTrue(android.provider.Settings.canDrawOverlays(target));assertTrue(NotificationAccess.isGranted(target));
        JSONObject proof=new JSONObject(new String(Files.readAllBytes(new File(target.getFilesDir(),"mute002-upgrade-seed.json").toPath()),StandardCharsets.UTF_8));
        TvFirst008State state=new TvFirst008State(target.getSharedPreferences("mute002_upgrade_identity",0),new SecretStore.AndroidKeyStoreSecretStore(target));
        assertEquals(proof.getString("device"),state.read("identity").getString("deviceId"));
        assertEquals(proof.getString("generation"),state.read("identity").getString("generation"));
        assertEquals("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ",state.token(state.read("identity")));
        var cache=new MultimediaPrivateCache(new File(proof.getString("root")),new InstallationStore(new AndroidInstallationBackend(target,proof.getString("namespace"))),new AndroidStudioAudioInspector(target));
        var restored=cache.read();assertNotNull(restored);assertEquals(proof.getString("digest"),restored.binding.sha256);
        assertEquals(3,restored.track.originalManifest.scenes.size());assertEquals(3,restored.track.audio.size());
        for(var row:restored.profile.resources)assertEquals(row.sha256,MultimediaDeliveryProfile.hash(Files.readAllBytes(restored.resource(row.id).toPath())));
        var preferences=target.getSharedPreferences("multimedia_user_preferences",0);assertFalse(preferences.getBoolean("text",true));assertTrue(preferences.getBoolean("audio",false));assertFalse(preferences.getBoolean("adsync",true));
        System.out.println("MUTE002_SIGNED_V31_TO_V32_PASS; PRIVATE_CACHE_3_TEXT_3_AUDIO; ENCRYPTED_ASSOCIATION; PREFERENCES; ZERO_NEW_PERMISSION_OR_SETUP");
    }
}
