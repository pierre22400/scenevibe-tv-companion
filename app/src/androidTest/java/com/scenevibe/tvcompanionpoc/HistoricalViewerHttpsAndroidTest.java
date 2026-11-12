package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Test A sur Android TV. Le compte Web et le Companion utilisent le vrai
 * HTTPS. Le banc ne crée qu'une association historique avant le clic.
 */
@RunWith(AndroidJUnit4.class)
public final class HistoricalViewerHttpsAndroidTest {
    private static final String PROFILE="?profile="+TvFirst008Wire.PROFILE;
    private final android.app.Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrument.getTargetContext();

    /** Attend un état Android observable sans agir sur la session ni sur l'horloge MEDIA. */
    private void await(BooleanSupplier ready,String diagnostic,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){
            if(ready.getAsBoolean())return;
            Thread.sleep(250);
        }
        fail(diagnostic);
    }

    /** Exporte uniquement le fait de qualification, sans bearer, cookie ou hash de credential. */
    private void evidence(String name,JSONObject data) throws Exception {
        Files.write(new File(app.getFilesDir(),name).toPath(),data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Enregistre le seul grant initial via les routes historiques et le stockage Keystore normal. */
    private JSONObject initialAssociation(TvFirst008State state,TvFirst008Http http) throws Exception {
        JSONObject pending=state.challenge("new");
        String secret=state.challengeSecret(pending);
        JSONObject request=new JSONObject(pending.toString()).put("profile",TvFirst008Wire.PROFILE).put("challengeSecret",secret);
        JSONObject offered=http.request("POST","/api/v1/device-activations"+PROFILE,null,request);
        evidence("001c-code.json",new JSONObject().put("userCode",offered.getString("userCode")));
        long deadline=SystemClock.elapsedRealtime()+120_000;
        while(SystemClock.elapsedRealtime()<deadline){
            JSONObject result=http.request("GET","/api/v1/device-activations/"+pending.getString("challengeId")+PROFILE,secret,null);
            if("claimed".equals(result.optString("status"))){state.claimed(result);return state.read("identity");}
            Thread.sleep(1000);
        }
        throw new AssertionError("HISTORICAL_SETUP_TIMEOUT");
    }

    /** Vérifie que le vrai service télécharge et arme le cache pendant que l'Activity est fermée. */
    @Test public void historicalOnlyFromBrowserThroughService() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C);
        UiDevice remote=UiDevice.getInstance(instrument);
        assertEquals("1",remote.executeShellCommand("getprop ro.kernel.qemu").trim());
        remote.executeShellCommand("appops set "+app.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        remote.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(app).flattenToString());
        TvFirst008State state=new TvFirst008State(app);
        assertNull(state.read("identity"));
        assertNull(PublicCompanionOwner.identityState(app).read("identity"));
        JSONObject identity=initialAssociation(state,new TvFirst008Http(BuildConfig.CLOUD_ORIGIN));
        Activity initial=instrument.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            instrument.runOnMainSync(()->PublicCompanionService.resumeHistorical(initial));
            await(()->PublicCompanionService.current()!=null &&
                    PublicCompanionService.current().viewerPreferences().initialized(),"HISTORICAL_SERVICE_NOT_READY",90_000);
            instrument.runOnMainSync(initial::finish);
            remote.pressHome();
            await(()->initial.isFinishing() && !app.getPackageName().equals(remote.getCurrentPackageName()),
                    "COMPANION_ACTIVITY_STILL_VISIBLE",25_000);
            evidence("001c-background.json",new JSONObject().put("deviceId",identity.getString("deviceId"))
                    .put("activityClosed",true).put("publicIdentityPresent",false));
            await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().status().startsWith("Track prête"),
                    "NATIVE_HISTORICAL_READY_TIMEOUT",150_000);
            assertNull(PublicCompanionOwner.identityState(app).read("identity"));
            PublicMultimediaCache.Prepared prepared=new PublicMultimediaCache(app,identity.getString("generation")).read();
            assertNotNull(prepared);
            assertTrue(prepared.track.audio.size()>0);
            PublicViewerPreferences preferences=PublicCompanionService.current().viewerPreferences();
            assertTrue(preferences.text());
            assertTrue(preferences.audio());
            assertFalse(app.getPackageName().equals(remote.getCurrentPackageName()));
            evidence("001c-native.json",new JSONObject().put("scope","ANDROID_TV_EMULATOR_REAL_HTTPS")
                    .put("activityClosed",true).put("publicIdentityPresent",false).put("nativeCacheVerified",true)
                    .put("commandId",prepared.binding.command).put("revisionId",prepared.binding.revision)
                    .put("packageSha256",prepared.binding.digest).put("audioResources",prepared.track.audio.size())
                    .put("text",preferences.text()).put("audio",preferences.audio()));
        }finally{instrument.runOnMainSync(initial::finish);}
    }
}
