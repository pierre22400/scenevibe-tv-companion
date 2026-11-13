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
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
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
    private static final String ORIGIN=BuildConfig.PUBLIC_VIEWER_001?BuildConfig.PUBLIC_VIEWER_ORIGIN:
            "https://scenevibe-autonomous001d-qual-preview.vercel.app";
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private final Context target=instrument.getTargetContext();
    private MainActivity mainActivity;
    private PublicPairingActivity activity;
    private MediaSession media;
    private String cookie="";
    private TvFirst008Source privateSource;
    private ManualNativeFixture privateFixture;

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
    @After public void after() throws Exception {main(()->{TvFirst009TextServiceOwner.requestStop(target);target.stopService(new Intent(target,PublicCompanionService.class));
        if(media!=null)media.release();CloudMultimediaAdmission.useOwnedMediaSessionFixture(false);if(activity!=null)activity.finish();if(mainActivity!=null)mainActivity.finish();});
        if(privateSource!=null)privateSource.close();
        if(privateFixture!=null)privateFixture.cleanup();
    }

    /** Qualifie le trajet complet code visible → HTTPS → cache natif → TEXT/AUDIO appliqués et restitués. */
    @Test public void realHttpsCodeCacheAndNativePlaybackWithoutAnotherApproval() throws Exception {
        if(BuildConfig.PUBLIC_VIEWER_001){realViewerWithoutActivity();return;}
        assertTrue(BuildConfig.DEBUG && BuildConfig.PUBLIC_AUTONOMOUS_001B);assertEquals(ORIGIN,BuildConfig.CLOUD_ORIGIN);
        UiDevice device=UiDevice.getInstance(instrument);assertEquals("1",device.executeShellCommand("getprop ro.kernel.qemu").trim());
        device.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        device.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        mainActivity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity=(PublicPairingActivity)instrument.startActivitySync(new Intent(target,PublicPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().code().matches("[0-9]{6}"),"Real HTTPS code unavailable",90000);
        String code=PublicCompanionService.current().code();
        AtomicBoolean shown=new AtomicBoolean();main(()->shown.set(code.contentEquals(((android.widget.TextView)activity.getWindow().getDecorView().findViewWithTag("public-tv-code")).getText())));
        await(()->{shown.set(code.contentEquals(((android.widget.TextView)activity.getWindow().getDecorView().findViewWithTag("public-tv-code")).getText()));return shown.get();},"Code not actually displayed",15000);
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

    /** Qualifie l'association, le cache automatique et le second envoi sans Activity visible. */
    private void realViewerWithoutActivity() throws Exception {
        assertTrue(BuildConfig.DEBUG && BuildConfig.COMPANION_MUTE_002);
        assertTrue("https://scenevibe-public-viewer001-qual.vercel.app".equals(ORIGIN)
                || "https://scenevibe-product-consolidation001q2-viewe-git-6ef46d-archc-ode.vercel.app".equals(ORIGIN));
        UiDevice device=UiDevice.getInstance(instrument);
        assertEquals("1",device.executeShellCommand("getprop ro.kernel.qemu").trim());
        device.executeShellCommand("appops set "+target.getPackageName()+" SYSTEM_ALERT_WINDOW allow");
        device.executeShellCommand("cmd notification allow_listener "+NotificationAccess.component(target).flattenToString());
        activity=(PublicPairingActivity)instrument.startActivitySync(new Intent(target,PublicPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->PublicCompanionService.current()!=null && PublicCompanionService.current().code().matches("[0-9]{6}"),
                "Code de qualification indisponible",90000);
        String code=PublicCompanionService.current().code();
        await(()->code.contentEquals(((android.widget.TextView)activity.getWindow().getDecorView()
                .findViewWithTag("public-tv-code")).getText()),"Code public non affiché",15000);
        boolean chromium=InstrumentationRegistry.getArguments().getString("q3Chromium","false").equals("true");
        String tv;
        if(chromium) {
            writeBridge("q3-code.json",new JSONObject().put("code",code));
            JSONObject browser=readBridge("q3-paired.json");
            tv=browser.getString("deviceId");
            cookie=browser.getString("cookie");
        } else {
            JSONObject claim=new JSONObject().put("userCode",code).put("consent",true);
            tv=web("/api/autonomous/viewer-pair",claim,200).getString("deviceId");
            web("/api/autonomous/viewer-pair",claim,404);
        }
        await(()->PublicCompanionService.current().viewerPreferences().initialized(),"Préférences initiales absentes",90000);
        PublicViewerPreferences prefs=PublicCompanionService.current().viewerPreferences();
        assertTrue(prefs.text());assertTrue(prefs.audio());
        main(()->activity.finish());device.pressHome();

        String track=InstrumentationRegistry.getArguments().getString("viewerTrackId");
        assertNotNull("Track créée dans le vrai Studio requise",track);
        JSONObject publication=web("/api/public/catalogue/tracks/"+track,null,200);
        assertTrue(publication.getJSONObject("track").getString("title").startsWith("Qualification spectateur 001 — Columbo"));
        JSONObject release=publication.getJSONObject("multimedia");
        main(()->{
            CloudMultimediaAdmission.useOwnedMediaSessionFixture(true);
            media=new MediaSession(target,"PUBLIC VIEWER OWNED MEDIA — NOT PRIME");
            media.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e")
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo — Eaux troubles")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build());
            playback(PlaybackState.STATE_PAUSED,0);media.setActive(true);
        });
        if(chromium){
            startPrivateSession();
            writeBridge("q3-background.json",new JSONObject().put("activityClosed",true).put("privateActive",true));
        }
        JSONObject request=chromium?readBridge("q3-command.json"):viewerSend(track,tv);
        if(chromium) {
            readBridge("q3-stop-private.json");
            assertTrue(TvFirst009TextServiceOwner.multimediaActive());
            assertNull(TvFirst009TextServiceOwner.publicCommand());
            PublicCloudClient.observeOwnedTransfer(()->{
                writeBridge("q3-first-resource-bytes.json",new JSONObject().put("received",true));
                readBridge("q3-network-cut.json");
                throw new java.net.SocketException("Q3_OWNED_NETWORK_INTERRUPTION");
            });
            main(()->TvFirst009TextServiceOwner.requestStop(target));
            await(()->!TvFirst009TextServiceOwner.multimediaActive(),"Session privée non arrêtée",15000);
        }
        JSONObject ready=waitViewerReady(track,tv,request.getString("commandId"),true);
        assertTrue(ready.getBoolean("text"));
        TvFirst008State identity=PublicCompanionOwner.identityState(target);
        PublicMultimediaCache.Prepared prepared=new PublicMultimediaCache(target,identity.read("identity").getString("generation")).read();
        assertEquals(release.getString("packageSha256"),prepared.binding.digest);
        assertEquals(request.getString("commandId"),prepared.binding.command);
        if(chromium)writeBridge("q3-cache.json",new JSONObject().put("commandId",prepared.binding.command)
                .put("revision",prepared.binding.revision).put("packageSha256",prepared.binding.digest));

        int texts=0,audios=0;
        for(SceneEvent event:prepared.track.calendar.events()){
            long before=Math.max(0,event.startMs()-1000);
            main(()->playback(PlaybackState.STATE_PAUSED,before));
            await(()->TvFirst009TextServiceOwner.status().positionMs==before,"Coordonnée MEDIA du banc absente",15000);
            main(()->playback(PlaybackState.STATE_PLAYING,before));
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
        JSONObject refusals=chromium?verifyHttpRefusals(identity,prepared.binding):new JSONObject();

        JSONObject proof=new JSONObject().put("scope","ANDROID_TV_EMULATOR_REAL_HTTPS_OWN_MEDIA_SESSION")
                .put("pairingCodes",1).put("manualApprovalsAfterPairing",0).put("companionActivityClosedBeforeSend",true)
                .put("textWindows",texts).put("audioStarted",audios).put("cacheSha256",prepared.binding.digest)
                .put("deviceRefusals",refusals).put("secondTrackText",true).put("secondTrackAudio",false).put("voluntaryStop",true);
        Files.write(new java.io.File(target.getFilesDir(),"public-viewer-native.json").toPath(),proof.toString().getBytes(StandardCharsets.UTF_8));
        // Cookie technique du seul banc : lu par run-as après reboot, jamais archivé.
        JSONObject boot=new JSONObject().put("cookie",cookie).put("deviceId",tv).put("trackId",track);
        Files.write(new java.io.File(target.getFilesDir(),"public-viewer-boot-private.json").toPath(),boot.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Exerce les gardes du vrai HTTPS en utilisant uniquement la possession du banc Android. */
    private JSONObject verifyHttpRefusals(TvFirst008State state,PublicCatalogueCandidate.Binding binding) throws Exception {
        JSONObject identity=state.read("identity");
        String token=state.token(identity);
        JSONObject request=new JSONObject().put("deviceId",identity.getString("deviceId"))
                .put("generation",identity.getString("generation")).put("trackId",binding.track)
                .put("revisionId",binding.revision).put("packageSha256","0".repeat(64));
        int fingerprint=deviceStatus("validate-cache",request,token);
        assertEquals(409,fingerprint);
        request.put("generation",java.util.UUID.randomUUID().toString());
        int generation=deviceStatus("validate-cache",request,token);
        assertTrue(generation==401 || generation==403 || generation==404);
        return new JSONObject().put("fingerprint",fingerprint).put("generation",generation);
    }

    /** Envoie une requête défensive bornée sans écrire le bearer dans les preuves. */
    private int deviceStatus(String action,JSONObject request,String token) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)URI.create(ORIGIN+"/api/autonomous/"+action).toURL().openConnection();
        try {
            connection.setConnectTimeout(15000);connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");
            connection.setRequestProperty("Origin",ORIGIN);connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("Authorization","Bearer "+token);
            connection.setDoOutput(true);
            try(var output=connection.getOutputStream()){output.write(request.toString().getBytes(StandardCharsets.UTF_8));}
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    /** Prépare un cache privé synthétique par les services existants avant l'envoi public HTTPS. */
    private void startPrivateSession() throws Exception {
        privateFixture=new ManualNativeFixture();
        TvFirst008State state=new TvFirst008State(target);
        String token="Q".repeat(43);
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",privateFixture.binding.device)
                .put("generation",privateFixture.generation).put("deviceToken",token));
        JSONObject identity=state.read("identity");
        assertTrue(target.getSharedPreferences("multimedia_user_preferences",0).edit()
                .putBoolean("text",true).putBoolean("audio",true).commit());
        privateSource=new TvFirst008Source(target,state,(method,path,bearer,body)->{
            assertEquals(token,bearer);
            if(path.contains("/association"))return new JSONObject(identity.toString()).put("profile",TvFirst008Wire.PROFILE)
                    .put("accountAssociated",true).put("rendering","FORBIDDEN")
                    .put("consentExpiresAt",java.time.Instant.now().plusSeconds(3600).toString())
                    .put("currentRevision",privateFixture.binding.revision-1);
            if(path.contains("/library"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE)
                    .put("items",new JSONArray()).put("next",JSONObject.NULL);
            throw new IllegalArgumentException("PRIVATE_FIXTURE_ROUTE");
        },"https://owned-fixture.invalid",java.util.concurrent.Executors.newSingleThreadExecutor());
        mainActivity=(MainActivity)instrument.startActivitySync(new Intent(target,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(privateSource::resume);
        await(()->privateSource.accountStage()==TvAccountSource.AccountStage.ASSOCIATED && !privateSource.busy(),
                "Q3 private association missing",30000);
        java.util.concurrent.atomic.AtomicReference<MultimediaPrivateCache.Prepared> installed=new java.util.concurrent.atomic.AtomicReference<>();
        main(()->privateSource.downloadMultimedia(privateFixture.binding.publication,installed::set,privateFixture.port));
        await(()->installed.get()!=null && !privateSource.busy(),"Q3 private cache missing",30000);
        main(()->assertTrue(TvFirst009TextServiceOwner.requestMultimediaStart(mainActivity,privateSource,installed.get(),true)));
        await(()->TvFirst009TextServiceOwner.multimediaActive() && TvFirst009TextServiceOwner.publicCommand()==null,
                "Q3 private MEDIA missing",30000);
        main(()->mainActivity.finish());
        UiDevice.getInstance(instrument).pressHome();
    }

    /** Transmet une observation uniquement au runner du banc possédé. */
    private void writeBridge(String name,JSONObject value) throws Exception {
        Files.write(new java.io.File(target.getFilesDir(),name).toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Attend le fichier atomiquement transféré par Chromium via ADB, sans journaliser son contenu. */
    private JSONObject readBridge(String name) throws Exception {
        java.io.File file=new java.io.File(target.getFilesDir(),name);
        long deadline=SystemClock.elapsedRealtime()+180000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if(file.isFile())return new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
            Thread.sleep(100);
        }
        throw new AssertionError("Q3_BROWSER_EVIDENCE_TIMEOUT: "+name);
    }

    /** Déclenche une seule distribution avec la capacité de ce navigateur de banc. */
    private JSONObject viewerSend(String track,String tv) throws Exception {
        return web("/api/autonomous/viewer-send",new JSONObject().put("trackId",track).put("deviceId",tv)
                .put("requestId",java.util.UUID.randomUUID().toString()),200);
    }

    /** Attend le reçu natif de cette commande, en refusant les succès d'une ancienne révision. */
    private JSONObject waitViewerReady(String track,String tv,String commandId,boolean audio) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+365000;
        while(SystemClock.elapsedRealtime()<deadline){
            try {
                JSONObject command=web("/api/autonomous/viewer-status",new JSONObject().put("trackId",track).put("deviceId",tv),200)
                        .optJSONObject("command");
                if(command!=null && commandId.equals(command.optString("commandId")) && "ready".equals(command.optString("state"))
                        && command.getBoolean("audio")==audio)return command;
            } catch(java.io.IOException offline) {
                // Le navigateur et le Companion attendent le rétablissement du réseau du banc.
            }
            Thread.sleep(2500);
        }
        throw new AssertionError("Reçu automatique indisponible. Récepteur: "
                +(PublicCompanionService.current()==null?"absent":PublicCompanionService.current().status()));
    }
}
