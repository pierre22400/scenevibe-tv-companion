package com.scenevibe.tvcompanionpoc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import com.scenevibe.tvcompanionpoc.presentation.TvOffer;
import com.scenevibe.tvcompanionpoc.presentation.TvPreparation;
import com.scenevibe.tvcompanionpoc.presentation.TvPresentationSource;
import com.scenevibe.tvcompanionpoc.presentation.TvSourceFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/**
 * Actual Android source/cache + native PostgreSQL production HTTP handlers, API31/35.
 * Human claims are explicitly synthetic. The loopback transport exists only in tests;
 * it cannot be selected by the APK. No Sony, Clerk account or video is contacted.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31,35}, qualifiers = "w960dp-h540dp-land-xhdpi", shadows = TvFirst008JourneyTest.SourceShadow.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvFirst008JourneyTest {
    private Context context;
    private TvFirst008State state;
    private Loopback port;
    private TvFirst008Source source;
    private SecretStore.InMemorySecretStore secrets;
    private java.util.concurrent.ExecutorService ownedWorker;
    private ActivityController<MainActivity> activity;
    private static TvPresentationSource injected;
    /** Require a real local native peer; absence is a failure, never a skipped qualification. */
    @Before public void setup() {
        String peer = System.getenv("SCENEVIBE_TVFIRST008_TEST_PEER");
        assertNotNull("Run through the native PostgreSQL peer gate", peer);
        assertTrue(peer.matches("http://127\\.0\\.0\\.1:[0-9]+"));
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("tvfirst008_identity",0).edit().clear().commit();
        secrets = new SecretStore.InMemorySecretStore();
        state = new TvFirst008State(context.getSharedPreferences("tvfirst008_identity",0), secrets);
        port = new Loopback(peer); source = compose(); injected = source; source.resume();
        idle();
    }
    /** Release all Activity/source tasks; no foreground runtime owner exists. */
    @After public void cleanup() {
        if (activity != null) activity.pause().stop().destroy();
        if (source != null) source.close(); injected = null;
    }
    /** Recreate the real production source over the same durable state and test-only transport. */
    private TvFirst008Source compose() { ownedWorker=Executors.newSingleThreadExecutor(); return new TvFirst008Source(context, state, port, "https://qualification.invalid", ownedWorker); }
    /** Wait for bounded asynchronous work while only draining already-due Android UI callbacks. */
    private void until(BooleanSupplier condition) {
        for (int count=0; count<600; count++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(10); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        fail("Timed out: " + source.accountStage() + " / " + source.accountMessage());
    }
    /** Wait until source work has completed, without advancing delayed polling timers. */
    private void idle() { until(() -> !source.busy()); }
    /** Simulate a human's explicit second-screen consent; the production claim service still authorizes it. */
    private void claim(boolean recover, JSONObject identity) throws Exception {
        String verification = source.verification();
        String code = verification.substring(verification.lastIndexOf("Code : ")+7).trim();
        JSONObject command = new JSONObject().put("userCode", code).put("recover", recover);
        if (recover) command.put("deviceId", identity.getString("deviceId")).put("expectedGeneration", identity.getString("generation"));
        port.raw("POST", "/_test/claim", null, command);
    }
    /** Pair explicitly, verify grant and list the real synthetic account-owned metadata page. */
    private TvOffer paired() throws Exception {
        source.beginPairing(false); idle(); assertEquals(TvAccountSource.AccountStage.CODE, source.accountStage());
        claim(false, null); source.refresh(); idle();
        assertEquals(source.accountMessage(), TvAccountSource.AccountStage.ASSOCIATED, source.accountStage());
        assertEquals(20, source.offers().size()); assertTrue(source.hasMore());
        return source.offers().get(0);
    }
    /** Native API journey yields matching current cache evidence, without historical ACK or MEDIA. */
    @Test public void completeJourneyAndRestartConfirmOnlyDurableCache() throws Exception {
        TvOffer offer = paired(); assertEquals("Non renseigné", offer.title); assertEquals("Non renseigné", offer.channel);
        source.inspect(offer.id); idle(); source.select(offer.id); idle();
        TvPreparation prepared = source.preparation(offer.id);
        assertTrue(source.accountMessage(), prepared.readyFor(offer.id)); assertFalse(prepared.mediaAdmittedFor(offer.id));
        assertNull(prepared.evidence.acknowledged); assertNotNull(prepared.evidence.cacheReceipt);
        source.close(); source=compose(); source.resume(); idle();
        assertTrue(source.preparation(offer.id).readyFor(offer.id));
        source.loadMore(); idle(); assertEquals(24, source.offers().size()); assertFalse(source.hasMore());
        JSONObject id = state.read("identity");
        InstallationStore read = new InstallationStore(new AndroidInstallationBackend(context, "tvfirst008_cache_" + id.getString("generation").replace("-","")));
        assertEquals(0, read.read().acknowledgedRevision());
    }
    /** Consent expiry removes READY locally without a new request, reset or automatic recovery. */
    @Test public void consentDeadlineInvalidatesReadyWithoutNetworkFallback() throws Exception {
        TvOffer offer=paired();source.select(offer.id);idle();assertTrue(source.preparation(offer.id).readyFor(offer.id));
        int adds=port.adds,receipts=port.receipts;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofDays(8));
        assertFalse(source.preparation(offer.id).readyFor(offer.id));assertEquals(TvAccountSource.AccountStage.ERROR,source.accountStage());
        assertEquals(adds,port.adds);assertEquals(receipts,port.receipts);assertNotNull(state.read("identity"));
    }
    /** A lost assignment reply retries the same intention and gets the same revision. */
    @Test public void ambiguousReplyRetainsIdempotentIntentionWithoutAutomaticResend() throws Exception {
        TvOffer offer = paired(); port.loseAdd = true;
        source.select(offer.id); idle(); assertFalse(source.preparation(offer.id).readyFor(offer.id));
        JSONObject pending = state.read("pending"); assertNotNull(pending); assertEquals(1, port.adds);
        source.refresh(); idle(); assertEquals(1, port.adds);
        source.select(offer.id); idle(); assertTrue(source.preparation(offer.id).readyFor(offer.id));
        assertEquals(2, port.adds); assertEquals(pending.getString("intentionId"), state.read("binding").getString("intentionId"));
        assertEquals(1, state.read("binding").getLong("revision"));
    }
    /** A callback arriving after pause cannot write cache or send a cache receipt. */
    @Test public void lateDownloadAfterPauseHasNoCacheOrReceiptAuthority() throws Exception {
        TvOffer offer = paired(); port.blockDownload = true;
        source.select(offer.id); assertTrue(port.entered.await(15, TimeUnit.SECONDS));
        source.pause(); port.release.countDown();
        assertTrue(port.completed.await(15, TimeUnit.SECONDS));
        ownedWorker.submit(() -> {}).get(15, TimeUnit.SECONDS);
        assertEquals(0, port.receipts);
        TvFirst008Wire.Binding binding = new TvFirst008Wire.Binding(state.read("binding"));
        assertFalse(new TvFirst008Cache(context, binding.generation).matches(binding));
        assertFalse(source.preparation(offer.id).readyFor(offer.id));
    }
    /** A forged 200 receipt cannot grant READY, even when durable cache preparation succeeded. */
    @Test public void corruptReceiptAndDeletedLocalCacheNeverBecomeReady() throws Exception {
        TvOffer offer = paired(); port.alterReceipt = true; source.select(offer.id); idle();
        assertFalse(source.preparation(offer.id).readyFor(offer.id)); assertNull(state.read("receipt"));
        port.alterReceipt = false; source.refresh(); idle(); assertTrue(source.preparation(offer.id).readyFor(offer.id));
        JSONObject id = state.read("identity");
        context.getSharedPreferences("tvfirst008_cache_" + id.getString("generation").replace("-",""),0).edit().clear().commit();
        source.pause(); source.resume(); idle();
        assertFalse(source.preparation(offer.id).readyFor(offer.id));
    }
    /** Recovery retains the owned device but invalidates old generation cache/receipt evidence. */
    @Test public void explicitRecoveryRotatesAssociationWithoutDuplicatingOrReusingCacheProof() throws Exception {
        TvOffer offer = paired(); source.select(offer.id); idle(); assertTrue(source.preparation(offer.id).readyFor(offer.id));
        JSONObject old = state.read("identity");
        source.beginPairing(true); assertTrue(source.offers().isEmpty()); assertFalse(source.preparation(offer.id).readyFor(offer.id));
        idle(); claim(true, old); source.refresh(); idle();
        JSONObject recovered = state.read("identity");
        assertEquals(old.getString("deviceId"), recovered.getString("deviceId")); assertNotEquals(old.getString("generation"), recovered.getString("generation"));
        assertFalse(source.preparation(offer.id).readyFor(offer.id)); assertNull(state.read("binding")); assertNull(state.read("receipt"));
    }
    /** Actual six-screen Activity keeps DPAD focus and requires a native confirmation before add. */
    @Test public void nativeActivityRemoteNavigationAndConsentReachCacheConfirmation() throws Exception {
        TvOffer offer = paired();
        context.getSharedPreferences("cloud_identity",0).edit().putString("deviceToken","sentinel-do-not-migrate").commit();
        activity = Robolectric.buildActivity(MainActivity.class).setup().visible(); idle();
        View root = activity.get().findViewById(android.R.id.content);
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); root.layout(0,0,1920,1080);
        View nav = root.findViewWithTag("nav-TRACKS"); assertNotNull(nav); nav.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); root.layout(0,0,1920,1080);
        nav = root.findViewWithTag("nav-TRACKS"); nav.requestFocus();
        nav.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        View focused = root.findFocus(); assertNotNull(focused); assertTrue(String.valueOf(focused.getTag()).startsWith("card-entry-"));
        focused.performClick(); idle();
        View primary = root.findViewWithTag("track-primary"); assertNotNull(primary); assertEquals(0,port.adds); primary.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog); assertEquals(0,port.adds);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick(); idle();
        assertTrue(source.preparation(offer.id).readyFor(offer.id));
        assertTrue(text(root).contains("Cache confirmé")); assertFalse(text(root).contains("open a streaming"));
        assertEquals("sentinel-do-not-migrate", context.getSharedPreferences("cloud_identity",0).getString("deviceToken",null));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }
    /** Extract visible text for assertion without reading any preference or credential. */
    private static String text(View view) {
        StringBuilder value = new StringBuilder(view instanceof TextView ? ((TextView)view).getText() : "");
        if (view instanceof ViewGroup) for (int index=0;index<((ViewGroup)view).getChildCount();index++) value.append('\n').append(text(((ViewGroup)view).getChildAt(index)));
        return value.toString();
    }
    /** Replace only the build composition during the test, never via a production injection hook. */
    @Implements(value=TvSourceFactory.class,isInAndroidSdk=false)
    public static final class SourceShadow {
        /** Compose the actual source under test inside the actual MainActivity. */
        @Implementation public static TvPresentationSource create(Context context) { return injected; }
    }
    /** Loopback-only transport is a JVM fixture, absent from production DEX. */
    static final class Loopback implements TvFirst008HttpPort {
        final String origin;
        volatile int adds,receipts;
        volatile boolean loseAdd,blockDownload,alterReceipt;
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),completed=new CountDownLatch(1);
        /** Bind only the peer address already checked by setup. */
        Loopback(String origin) { this.origin=origin; }
        /** Intercept deterministic network faults around the real server request. */
        @Override public JSONObject request(String method,String path,String token,JSONObject body) throws Exception {
            if (path.contains("/track-intentions")) adds++;
            if (path.contains("/ack?")) receipts++;
            JSONObject response=raw(method,path,token,body);
            if (path.contains("/track-intentions") && loseAdd) {loseAdd=false;throw new java.io.IOException("SYNTHETIC_LOST_REPLY");}
            if (path.contains("/assignment?") && blockDownload) {entered.countDown(); if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("BLOCK_TIMEOUT");completed.countDown();}
            if (path.contains("/ack?") && alterReceipt) response.put("generation",UUID.randomUUID().toString());
            return response;
        }
        /** Perform a real local HTTP exchange to production handlers over native PostgreSQL. */
        JSONObject raw(String method,String path,String token,JSONObject body) throws Exception {
            HttpURLConnection connection=(HttpURLConnection)URI.create(origin+path).toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setRequestMethod(method);
                if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
                if(body!=null){connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);try(java.io.OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
                int code=connection.getResponseCode(); if(code<200||code>=300)throw new TvFirst008Http.Failure(code);
                try(InputStream input=connection.getInputStream()){return new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
            } finally {connection.disconnect();}
        }
    }
}
