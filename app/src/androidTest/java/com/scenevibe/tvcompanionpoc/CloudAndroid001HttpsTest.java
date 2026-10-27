package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Téléchargement HTTPS réel depuis Android TV : client et cache de l'APK cible,
 * CA locale explicite (APK de tests seulement), hostname verifier Android intact.
 * Le serveur utilise les vrais services Cloud 003-I sur repositories mémoire et
 * ObjectStore HTTP local. Aucune preuve Neon/R2 réelle n'est déduite de ces cas.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudAndroid001HttpsTest {
    private Context target;
    private JSONObject config;
    private SSLContext tls;
    private MultimediaCloudClient.Session session;
    private MultimediaHttpsPort port;

    /** Load only this run's synthetic identity and explicit local CA from the instrumentation APK. */
    @Before public void before() throws Exception {
        target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(BuildConfig.CLOUD_MULTIMEDIA_EXPERIMENTAL);assertTrue(BuildConfig.DEBUG);
        config=new JSONObject(new String(asset("runtime-private.json"),StandardCharsets.UTF_8));
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null);
        trust.setCertificateEntry("local-qualification",CertificateFactory.getInstance("X.509").generateCertificate(
                new java.io.ByteArrayInputStream(asset("local-ca.pem"))));
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
        tls=SSLContext.getInstance("TLS");tls.init(null,factory.getTrustManagers(),null);
        session=lease(config.getString("token"));port=new MultimediaHttpsPort(config.getString("origin"),tls.getSocketFactory());
        fault("normal");
    }

    /** Read run-local instrumentation assets; no identity or test CA is bundled into the Companion APK. */
    private byte[] asset(String name) throws Exception {
        try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("cloudandroid001/"+name)) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)out.write(buffer,0,count);return out.toByteArray();
        }
    }

    /** Supply a current short-lived synthetic delegation while the real server verifies its device/account tuple. */
    private MultimediaCloudClient.Session lease(String token) {
        return new MultimediaCloudClient.Session() {
            /** Preserve the server-issued test device. */ @Override public String deviceId(){return config.optString("deviceId");}
            /** Preserve the server-issued test consent generation. */ @Override public String generation(){return config.optString("generation");}
            /** Send only the generated test bearer. */ @Override public String token(){return token;}
            /** The test owns this bounded execution lifetime. */ @Override public boolean current(){return true;}
        };
    }

    /** Use the same confirmed wire command and actual OS revision as the TV source. */
    private MultimediaCloudClient.Binding select(MultimediaCloudClient client) throws Exception {
        return client.select(new JSONObject().put("profile",MultimediaDeliveryProfile.PROFILE).put("generation",session.generation())
                .put("intentionId",UUID.randomUUID().toString()).put("publicationId",config.getString("publicationId"))
                .put("expectedRevision",client.currentRevision()).put("confirmed",true));
    }

    /** Allocate a cache exclusively in the installed target application's private directory. */
    private MultimediaPrivateCache cache(String name) throws Exception {
        String namespace="multimedia003d_cache_"+UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
        return new MultimediaPrivateCache(new File(target.getFilesDir(),"cloudandroid001/"+name),
                new InstallationStore(new AndroidInstallationBackend(target,namespace)),new AndroidStudioAudioInspector(target));
    }

    /** Change only the local loopback test proxy's response fault; it cannot alter any provider. */
    private void fault(String value) throws Exception {
        javax.net.ssl.HttpsURLConnection connection=(javax.net.ssl.HttpsURLConnection)new java.net.URL("https://localhost:8443/qualification-fault?mode="+value).openConnection();
        connection.setSSLSocketFactory(tls.getSocketFactory());connection.setConnectTimeout(15000);connection.setReadTimeout(60000);
        try{assertEquals(200,connection.getResponseCode());}finally{connection.disconnect();}
    }

    /** A local certificate is rejected by the production constructor's system trust store. */
    @Test public void untrustedCertificateIsRejected() throws Exception {
        boolean refused=false;try{new MultimediaCloudClient(session,new MultimediaHttpsPort(config.getString("origin"),(javax.net.ssl.SSLSocketFactory)javax.net.ssl.SSLSocketFactory.getDefault())).publications();}
        catch(javax.net.ssl.SSLHandshakeException expected){refused=true;}assertTrue(refused);
    }

    /** Trusting a CA does not disable native hostname verification. */
    @Test public void wrongHostnameIsRejected() throws Exception {
        boolean refused=false;try{new MultimediaCloudClient(session,new MultimediaHttpsPort("https://127.0.0.1:8443",tls.getSocketFactory())).publications();}
        catch(javax.net.ssl.SSLException expected){refused=true;}assertTrue(refused);
    }

    /** A bearer for another synthetic account cannot read this device's private catalogue. */
    @Test public void foreignCredentialIsRejected() throws Exception {
        boolean refused=false;try{new MultimediaCloudClient(lease(config.getString("foreignToken")),port).publications();}
        catch(IllegalArgumentException expected){refused=true;}assertTrue(refused);
    }

    /** Catalogue discovery runs on Android over TLS without assigning or granting any rendering authority. */
    @Test public void privateCatalogueContainsColumbo() throws Exception {
        JSONObject reply=new MultimediaCloudClient(session,port).publications();
        assertEquals(MultimediaDeliveryProfile.PROFILE,reply.getString("profile"));assertEquals(1,reply.getJSONArray("items").length());
        assertEquals(config.getString("publicationId"),reply.getJSONArray("items").getJSONObject(0).getString("publicationId"));
    }

    /** Prove actual HTTPS download, MP3 decode, Android commit/readback and a cache-only receipt. */
    @Test public void downloadsNineteenEventsAndRestoresAndroidCache() throws Exception {
        MultimediaCloudClient client=new MultimediaCloudClient(session,port);var binding=select(client);
        String namespace="download-"+UUID.randomUUID();var prepared=cache(namespace).download(client,binding);
        assertEquals(19,prepared.track.calendar.events().size());assertEquals(16,prepared.track.originalManifest.scenes.size());assertEquals(3,prepared.track.audio.size());
        for(var resource:prepared.profile.resources){assertEquals(48945,prepared.resource(resource.id).length());assertEquals(3000,resource.durationMs);}
        var restored=cache(namespace).read();assertNotNull(restored);assertTrue(binding.same(restored.binding));assertEquals(19,restored.track.calendar.events().size());
        writeProof("download-proof.json",new JSONObject().put("events",19).put("text",16).put("audio",3).put("packageSha256",binding.sha256)
                .put("https",true).put("androidCacheReadback",true).put("nativeMediaAdmission",false));
    }

    /** A real interrupted socket preserves the old committed set, and retry reuses the exact same intention. */
    @Test public void interruptedDownloadCanRetryWithoutLosingOldCache() throws Exception {
        MultimediaCloudClient client=new MultimediaCloudClient(session,port);String name="retry-"+UUID.randomUUID();var cache=cache(name);
        var original=cache.download(client,select(client));var replacement=select(client);fault("truncate");boolean refused=false;
        try{cache.download(client,replacement);}catch(Exception expected){refused=true;}finally{fault("normal");}
        assertTrue(refused);assertTrue(original.binding.same(cache.read().binding));
        assertTrue(replacement.same(cache.download(client,replacement).binding));
    }

    /** Corrupt MP3 bytes fail digest validation before replacing the existing Android cache pointer. */
    @Test public void corruptAudioCannotCommit() throws Exception {refusesFault("corrupt");}

    /** MIME confusion never reaches the native audio decoder or cache commit. */
    @Test public void wrongMimeCannotCommit() throws Exception {refusesFault("mime");}

    /** Redirects cannot send the device bearer to another origin. */
    @Test public void redirectCannotCommit() throws Exception {refusesFault("redirect");}

    /** Exercise a real transport fault using fresh cache metadata and assert there is no committed result. */
    private void refusesFault(String mode) throws Exception {
        MultimediaCloudClient client=new MultimediaCloudClient(session,port);var binding=select(client);var cache=cache(mode+UUID.randomUUID());
        fault(mode);boolean refused=false;try{cache.download(client,binding);}catch(Exception expected){refused=true;}finally{fault("normal");}
        assertTrue(refused);assertNull(cache.read());
    }

    /** Exercise the actual screen's source method on its genuine serial Android worker and encrypted identity. */
    @Test public void screenSourceDownloadsAndReopensVerifiedCache() throws Exception {
        target.getSharedPreferences("tvfirst008_identity",0).edit().clear().commit();TvFirst008State state=new TvFirst008State(target);
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",session.deviceId()).put("generation",session.generation()).put("deviceToken",session.token()));
        TvFirst008HttpPort grant=(method,path,token,body)->{
            if(path.contains("/association?"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",session.deviceId()).put("generation",session.generation())
                    .put("accountAssociated",true).put("rendering","FORBIDDEN").put("currentRevision",0).put("consentExpiresAt",Instant.now().plusSeconds(3600).toString());
            return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("items",new JSONArray()).put("next",JSONObject.NULL);
        };
        TvFirst008Source source=new TvFirst008Source(target,state,grant,"https://localhost",Executors.newSingleThreadExecutor());
        try {
            source.resume();long end=SystemClock.elapsedRealtime()+30000;while(source.busy() && SystemClock.elapsedRealtime()<end)Thread.sleep(20);
            assertEquals(TvAccountSource.AccountStage.ASSOCIATED,source.accountStage());
            CountDownLatch done=new CountDownLatch(1);AtomicReference<MultimediaPrivateCache.Prepared> result=new AtomicReference<>();
            source.downloadMultimedia(config.getString("publicationId"),prepared->{result.set(prepared);done.countDown();},port);
            assertTrue(source.accountMessage(),done.await(90,TimeUnit.SECONDS));assertEquals(19,result.get().track.calendar.events().size());
            assertNull(state.read("multimediaPending"));source.pause();
            var restored=MultimediaAndroidBridge.restoreCache(target,source);assertNotNull(restored);assertEquals(19,restored.track.calendar.events().size());
            JSONObject proof=new JSONObject().put("events",19).put("pid",android.os.Process.myPid()).put("generation",session.generation());
            writeProof("restart-seed.json",proof);
        }finally{source.close();}
    }

    /** Persist only sanitized actual assertions under the target app's private storage for adb run-as extraction. */
    private void writeProof(String name,JSONObject value) throws Exception {
        java.nio.file.Files.write(new File(target.getFilesDir(),"cloudandroid001-"+name).toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
    }
}
