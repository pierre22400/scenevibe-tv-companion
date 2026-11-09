package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Android TV émulé + véritable Cloud HTTPS 001-D : zéro injection de transport.
 * Le code est affiché par l'Activity et consommé par un client Web sans compte.
 * Les fichiers, credentials, MediaCodec et services Android sont réels.
 * Seule la MediaSession d'œuvre appartient explicitement au banc, jamais à Prime.
 */
@RunWith(AndroidJUnit4.class)
public final class PublicHostedAndroidTest {
    private static final String ORIGIN="https://scenevibe-autonomous001d-qual-preview.vercel.app";
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrument.getTargetContext();
    private MainActivity mainActivity;
    private PublicPairingActivity activity;
    private MediaSession media;
    private String cookie="";

    /** Exécute les effets d'interface sur le vrai Looper de l'application. */
    private void main(Runnable action){instrument.runOnMainSync(action);}
    /** Attend un fait Android borné ; aucune horloge MEDIA n'est avancée dans ce helper. */
    private void await(BooleanSupplier condition,String message,long duration) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+duration;
        while(SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();main(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(100);}
        fail(message+"; owner="+(PublicCompanionService.current()==null?"none":PublicCompanionService.current().status()));
    }
    /** Client Web HTTPS natif du banc : TLS par défaut, origine figée, aucun secret opérateur. */
    private JSONObject web(String path,JSONObject body,int expected) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)URI.create(ORIGIN+path).toURL().openConnection();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(45000);
            connection.setRequestProperty("Origin",ORIGIN);connection.setRequestProperty("Accept-Encoding","identity");
            if(!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
            if(body!=null){connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
                try(java.io.OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            assertEquals("Unexpected HTTP status for "+path,expected,connection.getResponseCode());
            String set=connection.getHeaderField("Set-Cookie");if(set!=null)cookie=set.split(";",2)[0];
            try(InputStream input=expected==200?connection.getInputStream():connection.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;){if(bytes.size()+n>2_000_000)throw new IllegalArgumentException("OWN_HTTP_BUDGET");bytes.write(buffer,0,n);}
                return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
    /** Lit la seule commande Web créée pour cette génération par le banc courant. */
    private JSONObject command() throws Exception {return web("/api/autonomous/status",new JSONObject(),200).getJSONArray("commands").getJSONObject(0);}
    /** Les coordonnées MEDIA sont publiées par notre session possédée, sans application tierce. */
    private void playback(int state,long position){media.setPlaybackState(new PlaybackState.Builder().setState(state,position,state==PlaybackState.STATE_PLAYING?1f:0f,SystemClock.elapsedRealtime()).build());}

    /** Arrête les seuls services de cet émulateur, sans effacer de cache ni de données TV. */
    @After public void after(){main(()->{TvFirst009TextServiceOwner.requestStop(target);target.stopService(new Intent(target,PublicCompanionService.class));
        if(media!=null)media.release();CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);if(activity!=null)activity.finish();if(mainActivity!=null)mainActivity.finish();});}

    /** Qualifie le trajet complet code visible → HTTPS → cache natif → TEXT/AUDIO appliqués et restitués. */
    @Test public void realHttpsCodeCacheAndNativePlaybackWithoutAnotherApproval() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.PUBLIC_AUTONOMOUS_001B);assertEquals(ORIGIN,BuildConfig.CLOUD_ORIGIN);
        UiDevice device=UiDevice.getInstance(instrument);assertEquals("1",device.executeShellCommand("getprop ro.kernel.qemu").trim());
        device.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        device.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        mainActivity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity=(PublicPairingActivity)instrument.startActivitySync(new Intent(target,PublicPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().code().matches("[0-9]{6}"),"Real HTTPS code unavailable",90000);
        String code=PublicCompanionService.current().code();
        AtomicBoolean shown=new AtomicBoolean();main(()->shown.set(code.contentEquals(((android.widget.TextView)activity.getWindow().getDecorView().findViewWithTag("public-tv-code")).getText())));
        assertTrue("Code not actually displayed",shown.get());
        web("/api/autonomous/pair",new JSONObject().put("userCode",code).put("consent",true),200);
        await(()->PublicCompanionService.current().code().isEmpty(),"Code not consumed automatically",90000);
        web("/api/autonomous/pair",new JSONObject().put("userCode",code).put("consent",true),404);
        JSONArray tracks=web("/api/public/catalogue/tracks",null,200).getJSONArray("items");String track=null;
        for(int i=0;i<tracks.length();i++){JSONObject page=tracks.getJSONObject(i),m=page.getJSONObject("multimedia");if(m.getInt("textCount")==3 && m.getInt("audioCount")==3)track=page.getJSONObject("identity").getString("trackId");}
        assertNotNull("Stable synthetic 3 TEXT + 3 AUDIO Track unavailable",track);
        JSONObject request=web("/api/autonomous/request",new JSONObject().put("trackId",track).put("requestId",java.util.UUID.randomUUID().toString()),200);
        long deadline=SystemClock.elapsedRealtime()+120000;JSONObject cached=null;
        while(SystemClock.elapsedRealtime()<deadline){JSONObject value=command();if(value.getString("state").equals("armed")){cached=value;break;}Thread.sleep(1500);}
        assertNotNull("Native cache/default preference receipt not confirmed",cached);
        TvFirst008State state=new TvFirst008State(target.getSharedPreferences("public001b_identity",0),new SecretStore.AndroidKeyStoreSecretStore(target));
        PublicMultimediaCache.Prepared prepared=new PublicMultimediaCache(target,state.read("identity").getString("generation")).read();
        assertEquals(request.getString("packageSha256"),prepared.binding.digest);assertEquals(6,prepared.track.calendar.events().size());assertEquals(3,prepared.track.audio.size());
        main(()->{CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);media=new MediaSession(target,"OWN REAL HTTPS CLOUD001D — NOT PRIME");
            media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());playback(PlaybackState.STATE_PAUSED,0);media.setActive(true);});
        web("/api/autonomous/activate",new JSONObject().put("commandId",cached.getString("commandId")).put("text",true).put("audio",true).put("expectedVersion",cached.getLong("version")),200);
        await(TvFirst009TextServiceOwner::multimediaActive,"Real HTTPS activation did not open native service",90000);
        deadline=SystemClock.elapsedRealtime()+60000;boolean armed=false;
        while(SystemClock.elapsedRealtime()<deadline){JSONObject value=command();if(value.getString("state").equals("armed") && value.getBoolean("text") && value.getBoolean("audio")){armed=true;break;}Thread.sleep(1000);}
        assertTrue("Native preference application not acknowledged",armed);
        int texts=0,audios=0;
        for(SceneEvent event:prepared.track.calendar.events()){
            long before=event.startMs()-1000;main(()->playback(PlaybackState.STATE_PAUSED,before));
            await(()->TvFirst009TextServiceOwner.status().positionMs==before,"MEDIA paused coordinate unavailable",15000);main(()->playback(PlaybackState.STATE_PLAYING,before));
            if(prepared.track.audio.containsKey(event.eventId())){int count=++audios;await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=count,"Real Cloud MP3 not STARTED",15000);}
            else{int count=++texts;await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=count,"Real Cloud TEXT window not attached",15000);}
        }
        assertEquals(3,texts);assertEquals(3,audios);
        JSONObject proof=new JSONObject().put("scope","ANDROID_TV_EMULATOR_REAL_HTTPS_OWN_MEDIA_SESSION_NOT_SONY_PRIME").put("origin",ORIGIN)
            .put("packageSha256",prepared.binding.digest).put("codeAccepted",1).put("extraApplicationApprovals",0).put("textWindowsAttached",texts).put("audioResourcesStarted",audios)
            .put("nativeCacheReadback",true).put("tls","ANDROID_DEFAULT_TRUST_NO_OVERRIDE");
        Files.write(new java.io.File(target.getFilesDir(),"public001d-https-proof.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
    }
}
