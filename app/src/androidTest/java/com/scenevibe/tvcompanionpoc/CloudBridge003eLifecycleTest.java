package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Véritable propriétaire Android TV-FIRST, Handler/lifecycle, SharedPreferences
 * et chiffrement AndroidKeyStore. Le grant distant est une réponse synthétique
 * de qualification ; aucune authentification humaine ou MEDIA n'est fabriquée.
 */
@RunWith(AndroidJUnit4.class)
public final class CloudBridge003eLifecycleTest {
    private Context context;private TvFirst008State state;private TvFirst008Source source;private String device,generation;
    private Native003eFixture multimedia;
    private java.util.concurrent.ExecutorService ownerWorker;
    /** Create a synthetic explicit association through the actual encrypted Android state owner. */
    @Before public void before() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".studiomultimedia001") || BuildConfig.CLOUD_MULTIMEDIA_EXPERIMENTAL);
        context.getSharedPreferences("tvfirst008_identity",0).edit().clear().commit();
        state=new TvFirst008State(context);device=UUID.randomUUID().toString();generation=UUID.randomUUID().toString();claim(generation);
        ownerWorker=Executors.newSingleThreadExecutor();source=new TvFirst008Source(context,state,this::grant,"https://qualification.invalid",ownerWorker);source.resume();await(()->!source.busy());
        assertEquals(source.accountMessage(),TvAccountSource.AccountStage.ASSOCIATED,source.accountStage());
    }
    /** Close the actual Activity-owned worker; do not change any real app or device. */
    @After public void after() throws Exception {
        if(source!=null)source.close();if(multimedia!=null)multimedia.cleanup();
        if(generation!=null) {
            Native003eFixture.remove(new java.io.File(context.getFilesDir(),"multimedia003d/"+generation.replace("-","")));
            context.getSharedPreferences("multimedia003d_cache_"+generation.replace("-",""),0).edit().clear().commit();
        }
    }
    /** Store the synthetic credential with the real Android Keystore instead of a memory secret mock. */
    private void claim(String nextGeneration) throws Exception {
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        state.claimed(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",device).put("generation",nextGeneration)
            .put("deviceToken",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));
    }
    /** Return only synthetic grant/library metadata needed by the existing association flow. */
    private JSONObject grant(String method,String path,String token,JSONObject body) throws Exception {
        if(path.contains("/association?"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",device)
            .put("generation",generation).put("accountAssociated",true).put("rendering","FORBIDDEN").put("currentRevision",0)
            .put("consentExpiresAt",Instant.now().plusSeconds(3600).toString());
        if(path.contains("/library?"))return new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("items",new JSONArray()).put("next",JSONObject.NULL);
        throw new IllegalArgumentException("UNEXPECTED_NATIVE_FIXTURE_ROUTE");
    }
    /** Wait for bounded actual Android worker completion without a Robolectric Looper shadow. */
    private void await(BooleanSupplier condition) throws Exception {
        long end=android.os.SystemClock.elapsedRealtime()+30_000;
        while(!condition.getAsBoolean() && android.os.SystemClock.elapsedRealtime()<end)Thread.sleep(20);
        assertTrue("Android owner did not complete",condition.getAsBoolean());
    }
    /** Production remains closed with no configured authenticated source. */
    @Test public void unassociatedSourceCannotCreateTheBridge() throws Exception {
        TvFirst008Source unconfigured=new TvFirst008Source(context);
        try {boolean refused=false;try{new MultimediaAndroidBridge(context,unconfigured,"https://qualification.invalid");}catch(IllegalStateException expected){refused=true;}assertTrue(refused);}
        finally {unconfigured.close();}
    }
    /** The genuine encrypted lease becomes invalid immediately on Activity pause. */
    @Test public void pauseInvalidatesTheCurrentLease() {var lease=source.multimediaSession();assertTrue(lease.current());source.pause();assertFalse(lease.current());}
    /** A later resume must never revive a lease captured before an earlier cancellation epoch. */
    @Test public void oldLeaseCannotReviveAfterPauseAndResume() throws Exception {
        var lease=source.multimediaSession();source.pause();assertFalse(lease.current());source.resume();await(()->!source.busy());
        assertEquals(TvAccountSource.AccountStage.ASSOCIATED,source.accountStage());assertFalse("Cancelled lease revived after Activity resume",lease.current());assertTrue(source.multimediaSession().current());
    }
    /** Credential rotation rejects a captured generation before any new bytes or receipt are accepted. */
    @Test public void generationChangeInvalidatesTheCapturedLease() throws Exception {
        var lease=source.multimediaSession();claim(UUID.randomUUID().toString());assertFalse(lease.current());
        boolean refused=false;try{lease.token();}catch(IllegalStateException expected){refused=true;}assertTrue(refused);
    }
    /** Closing the owner releases work and leaves the formerly valid lease permanently closed. */
    @Test public void ownerCloseCancelsLeases() {var lease=source.multimediaSession();source.close();assertFalse(lease.current());}
    /** The bridge is constructible from the real associated owner without a download or rendering. */
    @Test public void constructingAnAssociatedBridgeHasNoTransportSideEffect() throws Exception {
        var bridge=new MultimediaAndroidBridge(context,source,"https://qualification.invalid");assertNotNull(bridge);assertNull(state.read("multimediaPending"));
        assertEquals(TvAccountSource.AccountStage.ASSOCIATED,source.accountStage());
    }
    /** Source metadata retains one explicit retry identifier while keeping legacy pending data separate. */
    @Test public void multimediaIntentionIsDurableAndIndependentFromLegacyPending() throws Exception {
        String publication=UUID.randomUUID().toString();JSONObject first=source.pendingMultimedia(publication,0);
        JSONObject again=source.pendingMultimedia(publication,99);assertEquals(first.getString("intentionId"),again.getString("intentionId"));
        assertEquals(first.toString(),new TvFirst008State(context).read("multimediaPending").toString());assertNull(state.read("pending"));assertNull(state.read("binding"));assertNull(state.read("receipt"));
    }
    /** Cancellation epochs also invalidate a lease even when the Activity remains active. */
    @Test public void explicitCancellationInvalidatesTheCapturedEpoch() {
        var lease=source.multimediaSession();source.cancel("qualification-only");assertFalse(lease.current());
    }
    /** Compose the actual Android facade through its actual existing owner and serial worker. */
    @Test public void existingOwnerPreparesTheNativeCacheOnlyAfterExplicitAction() throws Exception {
        multimedia=ownerFixture();var runtime=runtime();java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        assertNull(runtime.preparedCloudCalendar());assertEquals(0,multimedia.port.audioRequests);
        source.prepareMultimedia(multimedia.binding.publication,runtime,done::countDown,multimedia.port);
        await(()->done.getCount()==0);assertEquals(19,runtime.preparedCloudCalendar().events().size());assertEquals(1,multimedia.port.audioRequests);
        assertNull(state.read("multimediaPending"));assertNull(state.read("binding"));assertNull(state.read("receipt"));
        var restored=MultimediaAndroidBridge.restoreInert(context,source,runtime);assertNotNull(restored);assertEquals(16,restored.track.originalManifest.scenes.size());
        boolean refused=false;try{runtime.requireNativeIdentityEvidence();}catch(IllegalStateException expected){refused=true;}assertTrue(refused);runtime.close();
    }
    /** A real source pause during native preparation suppresses both cache publication and the queued callback. */
    @Test public void ownerPauseDuringTransferCannotOfferTheRuntimeOrConfirmACache() throws Exception {
        multimedia=ownerFixture();var runtime=runtime();java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        multimedia.port.onAudio=source::pause;source.prepareMultimedia(multimedia.binding.publication,runtime,done::countDown,multimedia.port);
        ownerWorker.submit(()->{}).get(60,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1,done.getCount());assertNull(runtime.preparedCloudCalendar());assertEquals(0,multimedia.port.receipts);runtime.close();
    }
    /** Create only the native fixture network seam over the real owner's device/generation. */
    private Native003eFixture ownerFixture() throws Exception {
        var fixture=new Native003eFixture();fixture.binding=new MultimediaCloudClient.Binding(fixture.binding.json().put("deviceId",device).put("generation",generation));return fixture;
    }
    /** Bind real render ports without activating them; a prepared cache remains inert. */
    private StudioMultimediaRuntime runtime() {
        return new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** No scene can be rendered by this cache-only qualification. */ @Override public boolean preflight(OverlayManifest.Scene scene){return false;}
            /** A rendered scene would be an explicit test failure. */ @Override public void show(OverlayManifest.Scene scene){throw new AssertionError("MEDIA_NOT_ADMITTED");}
            /** No scene was shown. */ @Override public void hide(OverlayManifest.Scene scene){}
            /** No visual output is retained. */ @Override public void hideAll(){}
        },new com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort(id->{throw new java.io.IOException("MEDIA_NOT_ADMITTED");}));
    }
}
