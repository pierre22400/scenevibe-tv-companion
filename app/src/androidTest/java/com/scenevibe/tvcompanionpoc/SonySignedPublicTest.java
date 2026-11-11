package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Vérifie la distribution Preview HTTPS et les effets Android après la mise à jour signée.
 * Le lecteur MEDIA est un programme local réservé à l’émulateur. Le banc conserve
 * la configuration Android et les données privées installées sous v32.
 */
@RunWith(AndroidJUnit4.class)
public final class SonySignedPublicTest {
    private static final String ORIGIN = "https://scenevibe-public-viewer001-qual.vercel.app";
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrument.getTargetContext();
    private PublicPairingActivity activity;
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
    /** Arrête les seuls services de cet émulateur, sans effacer de cache ni de données TV. */
    @After public void after() {
        main(() -> {
            TvFirst009TextServiceOwner.requestStop(target);
            target.stopService(new Intent(target, PublicCompanionService.class));
            if (activity != null) activity.finish();
        });
    }

    /** Qualifie le premier et le deuxième envoi HTTPS sur la v34 de livraison. */
    @Test public void realViewerWithoutActivity() throws Exception {
        UiDevice device = SonySignedContinuityTest.device(instrument);
        assertEquals(34, target.getPackageManager().getPackageInfo(target.getPackageName(), 0).getLongVersionCode());
        assertEquals(0, target.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE);
        assertTrue(android.provider.Settings.canDrawOverlays(target));
        assertTrue(NotificationAccess.isGranted(target));
        activity=(PublicPairingActivity)instrument.startActivitySync(new Intent(target,PublicPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().code().matches("[0-9]{6}"),
                "Code de qualification indisponible",90000);
        String code=PublicCompanionService.current().code();
        await(()->code.contentEquals(((android.widget.TextView)activity.getWindow().getDecorView()
                .findViewWithTag("public-tv-code")).getText()),"Code public non affiché",15000);
        JSONObject claim=new JSONObject().put("userCode",code).put("consent",true);
        String tv=web("/api/autonomous/viewer-pair",claim,200).getString("deviceId");
        await(()->PublicCompanionService.current().viewerPreferences().initialized(),"Préférences initiales absentes",90000);
        PublicViewerPreferences prefs=PublicCompanionService.current().viewerPreferences();
        assertTrue(prefs.text());assertTrue(prefs.audio());
        web("/api/autonomous/viewer-pair",claim,404);
        main(()->activity.finish());device.pressHome();

        String track=InstrumentationRegistry.getArguments().getString("viewerTrackId");
        assertNotNull("Track créée dans le vrai Studio requise",track);
        JSONObject publication=web("/api/public/catalogue/tracks/"+track,null,200);
        assertTrue(publication.getJSONObject("track").getString("title").startsWith("Qualification spectateur 001 — Columbo"));
        JSONObject release=publication.getJSONObject("multimedia");
        SonySignedContinuityTest.player(device, 0, false);
        JSONObject request=viewerSend(track,tv);
        JSONObject ready=waitViewerReady(track,tv,request.getString("commandId"),true);
        assertTrue(ready.getBoolean("text"));
        TvFirst008State identity=PublicCompanionOwner.identityState(target);
        PublicMultimediaCache.Prepared prepared=new PublicMultimediaCache(target,identity.read("identity").getString("generation")).read();
        assertEquals(release.getString("packageSha256"),prepared.binding.digest);
        assertEquals(request.getString("commandId"),prepared.binding.command);

        int texts=0,audios=0;
        for(SceneEvent event:prepared.track.calendar.events()){
            long before=Math.max(0,event.startMs()-1000);
            SonySignedContinuityTest.player(device, before, false);
            await(()->TvFirst009TextServiceOwner.status().positionMs==before,"Coordonnée MEDIA du banc absente",15000);
            SonySignedContinuityTest.player(device, before, true);
            if(prepared.track.audio.containsKey(event.eventId())){
                int count=++audios;
                await(()->TvFirst009TextServiceOwner.multimediaAudioStarted()>=count,"MP3 vérifié non démarré",15000);
            }else{
                int count=++texts;
                await(()->TvFirst009TextServiceOwner.multimediaTextRequested()>=count,"Fenêtre TEXT absente",15000);
            }
        }
        assertEquals(release.getInt("textCount"),texts);assertEquals(release.getInt("audioCount"),audios);
        main(()->TvFirst009TextServiceOwner.setMultimediaAudioEnabled(false));
        waitViewerReady(track,tv,request.getString("commandId"),false);
        main(()->TvFirst009TextServiceOwner.requestStop(target));
        await(()->!TvFirst009TextServiceOwner.multimediaActive(),"Arrêt volontaire non appliqué",15000);

        String second=null;
        JSONArray items=web("/api/public/catalogue/tracks?limit=100",null,200).getJSONArray("items");
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i), description=item.optJSONObject("multimedia");
            String id=item.getJSONObject("identity").getString("trackId");
            if(!track.equals(id) && description!=null && description.getInt("audioCount")>0 && description.getInt("textCount")>0){
                second=id;break;
            }
        }
        assertNotNull("Seconde Track publique de qualification absente",second);
        JSONObject next=viewerSend(second,tv);
        assertTrue(waitViewerReady(second,tv,next.getString("commandId"),false).getBoolean("text"));
        assertFalse(new PublicViewerPreferences(target).audio());
        assertEquals(tv,identity.read("identity").getString("deviceId"));

        System.out.println("SONY001A_SIGNED_PUBLIC_PASS code=1 additional_approval=0 cache="
                + prepared.binding.digest + " TEXT=" + texts + " AUDIO=" + audios + " second_track_audio_disabled");
    }

    /** Déclenche une seule distribution avec la capacité de ce navigateur de banc. */
    private JSONObject viewerSend(String track,String tv) throws Exception {
        return web("/api/autonomous/viewer-send",new JSONObject().put("trackId",track).put("deviceId",tv)
                .put("requestId",java.util.UUID.randomUUID().toString()),200);
    }

    /** Attend le reçu natif de cette commande, en refusant les succès d'une ancienne révision. */
    private JSONObject waitViewerReady(String track,String tv,String commandId,boolean audio) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+180000;
        while(SystemClock.elapsedRealtime()<deadline){
            JSONObject command=web("/api/autonomous/viewer-status",new JSONObject().put("trackId",track).put("deviceId",tv),200)
                    .optJSONObject("command");
            if(command!=null && commandId.equals(command.optString("commandId")) && "ready".equals(command.optString("state"))
                    && command.getBoolean("audio")==audio)return command;
            Thread.sleep(2500);
        }
        throw new AssertionError("Reçu automatique indisponible. Récepteur: "
                +(PublicCompanionService.current()==null?"absent":PublicCompanionService.current().status()));
    }
}
