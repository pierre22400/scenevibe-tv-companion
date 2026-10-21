package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * Real 008 durable cache/source -> unchanged M4 restoration/M5 -> real SceneRenderer.
 * Native MediaController inputs are deterministic samples, not physical Prime evidence.
 * The required private fixture is the 16-comment accepted Columbo distribution, unchanged.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31,35})
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvFirst009TextIntegrationTest {
    private Context context;
    private TvFirst008State state;
    private TvFirst008Source source;
    private TvFirst008Wire.Envelope envelope;
    private TvFirst008Source.ConfirmedCacheReference reference;
    private TvFirst009TextCache.Prepared prepared;
    private TvFirst009TextRuntime runtime;
    private CountingSecrets secrets;
    private Drawing drawing;
    private long now;
    private int requests;
    private boolean denyAssociation;

    /** Exercise the actual confirmed-source restoration, without a live service or credential. */
    @Before public void setUp() throws Exception {
        assertTrue(BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION);
        String fixture=System.getProperty("scenevibe009.fixture");
        assertNotNull("Required real Columbo fixture must never silently skip",fixture);
        JSONObject raw=new JSONObject(new String(Files.readAllBytes(Path.of(fixture)),StandardCharsets.UTF_8));
        envelope=new TvFirst008Wire.Envelope(raw,new TvFirst008Wire.Binding(raw));
        assertEquals("4b3208760f8356d2b00aa223f5103f9d3a1ff2d7b2429ce58c1cb414bf2e818c",envelope.binding.digest);
        context=RuntimeEnvironment.getApplication();
        SharedPreferences prefs=context.getSharedPreferences("tvfirst009_qualification_metadata",0);
        prefs.edit().clear().commit();
        secrets=new CountingSecrets(); state=new TvFirst008State(prefs,secrets);
        state.claimed(envelope.binding.json().put("deviceToken","Q".repeat(43)));
        state.write("binding",envelope.binding.json()); state.write("receipt",envelope.binding.json());
        assertTrue(new TvFirst008Cache(context,envelope.binding.generation).prepare(envelope));
        TvFirst008HttpPort http=(method,path,token,body) -> {
            requests++;
            if (path.contains("/association?")) {
                if (denyAssociation) throw new TvFirst008Http.Failure(403);
                return envelope.binding.json().put("accountAssociated",true).put("rendering","FORBIDDEN")
                        .put("currentRevision",envelope.binding.revision)
                        .put("consentExpiresAt",Instant.now().plusSeconds(86400).toString());
            }
            if (path.contains("/library?")) return new JSONObject().put("items",new JSONArray().put(
                    new JSONObject().put("id",envelope.binding.track).put("eligibility","REQUIRES_VALIDATION")
                            .put("title","Columbo — Eaux troubles").put("filmTitle","Columbo — Eaux troubles")))
                    .put("next",JSONObject.NULL);
            if (path.contains("/assignment-status?")) return envelope.binding.json().put("status","CACHE_CONFIRMED");
            throw new AssertionError("Unexpected HTTP route; no automatic fallback is allowed");
        };
        source=new TvFirst008Source(context,state,http,"https://qualification.invalid",new DirectExecutor());
        source.resume(); shadowOf(Looper.getMainLooper()).idle();
        assertTrue(source.preparation("entry-"+envelope.binding.track).readyFor("entry-"+envelope.binding.track));
        now=SystemClock.elapsedRealtime(); reference=source.confirmedCacheReference(); assertNotNull(reference);
        prepared=TvFirst009TextCache.load(store(),reference,now);
        assertEquals(16,prepared.video.track.comments.size());
        ShadowSettings.setCanDrawOverlays(true); drawing=new Drawing();
        runtime=new TvFirst009TextRuntime(new Handler(Looper.getMainLooper()),()->now,drawing);
    }

    /** End every local task/view without touching its durable cache or historical authorities. */
    @After public void tearDown() { if (runtime!=null) runtime.close(); if (source!=null) source.close(); }

    /** All sixteen original texts reach real Android views once, with exact bytes and zero authority writes. */
    @Test public void allSixteenActualCommentsReachExistingRendererWithoutMutation() {
        Map<String,?> metadata=new HashMap<>(context.getSharedPreferences("tvfirst009_qualification_metadata",0).getAll());
        Map<String,byte[]> artifacts=store().read().snapshot().canonical().artifacts();
        int calls=secrets.calls, http=requests;
        assertTrue(start());
        long end=prepared.video.track.comments.get(15).startMs+prepared.video.track.comments.get(15).durationMs;
        for (long position=0;position<=end+1000;position+=1000) sample(position,true);
        assertEquals(16,drawing.shown.size()); assertEquals(16,new java.util.HashSet<>(drawing.shown).size());
        assertNull(drawing.root()); assertEquals(http,requests); assertEquals(calls,secrets.calls);
        assertEquals(metadata,context.getSharedPreferences("tvfirst009_qualification_metadata",0).getAll());
        assertEquals(0,store().read().acknowledgedRevision());
        for (String key:artifacts.keySet()) assertArrayEquals(artifacts.get(key),store().read().snapshot().canonical().artifact(key));
    }

    /** PAUSED is authoritative even when Prime declares speed=1; no wall-time expiry is added. */
    @Test public void pauseFreezesVisibleSceneAndPlayResumesExpiry() {
        assertTrue(start()); ScheduledTrack.Event event=first(); sample(event.startMs,true);
        sample(event.startMs+1000,false); assertNotNull(drawing.root());
        for(int i=0;i<10;i++) { now+=1000; sample(event.startMs+1000,false); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); }
        assertNotNull(drawing.root()); assertEquals(1,drawing.shown.size());
        for(long at=event.startMs+2000;at<=event.startMs+event.durationMs;at+=1000) sample(at,true);
        assertNull(drawing.root());
    }

    /** Existing M5 rearming and no-burst forward consumption remain the temporal authority. */
    @Test public void backSeekRearmsAndForwardSeekCannotBurstOrRenderLandingEvent() {
        assertTrue(start()); ScheduledTrack.Event event=first(); sample(event.startMs,true); sample(event.startMs+1000,true);
        sample(event.startMs-4000,true); sample(event.startMs-1000,true); sample(event.startMs,true);
        assertEquals(2,drawing.shown.size());
        sample(prepared.video.track.comments.get(5).startMs,true); assertEquals(2,drawing.shown.size()); assertNull(drawing.root());
        sample(prepared.video.track.comments.get(5).startMs+1000,true); assertEquals(2,drawing.shown.size());
    }

    /** Episode and application loss close the same visible scene immediately. */
    @Test public void wrongEpisodeGenericDescriptionAndWrongPackageNeverKeepDrawing() {
        assertTrue(start()); sample(first().startMs,true); assertNotNull(drawing.root());
        runtime.listener().onSnapshot(snapshot(first().startMs,true,"com.amazon.amazonvideo.livingroom","","Columbo","Le Chant du cygne",0));
        assertNull(drawing.root());
        runtime.listener().onSnapshot(snapshot(first().startMs,true,"com.amazon.amazonvideo.livingroom","","PrimeVideo","",0));
        assertEquals(1,drawing.shown.size());
        runtime.listener().onSnapshot(snapshot(first().startMs,true,"com.netflix.ninja",prepared.video.track.mediaIdentity.videoId,"","",0));
        assertEquals(1,drawing.shown.size());
    }

    /** Fix the diagnosed unknown-position gap outside the frozen M5 core. */
    @Test public void unknownPositionRevokesInsteadOfRetainingFrozenEligibility() {
        assertTrue(start()); sample(first().startMs,true); assertNotNull(drawing.root());
        runtime.listener().onSnapshot(snapshot(-1,true,prepared.video.track.targetPackage,prepared.video.track.mediaIdentity.videoId,"","",0));
        assertNull(drawing.root());
    }

    /** Fresh captures with an old provider anchor remain valid, matching the actual Sony observations. */
    @Test public void oldProviderAnchorIsNotMistakenForMissingCapture() {
        assertTrue(start());
        runtime.listener().onSnapshot(snapshot(first().startMs,true,prepared.video.track.targetPackage,prepared.video.track.mediaIdentity.videoId,"","",110000));
        assertEquals(1,drawing.shown.size()); assertNotNull(drawing.root());
    }

    /** Missing captures close a paused card even though its editorial position is frozen. */
    @Test public void watchdogClosesMissingCaptureWithoutNeedingAnotherCallback() {
        assertTrue(start()); sample(first().startMs,true); sample(first().startMs,false);
        now+=2501; shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertNull(drawing.root());
    }

    /** Old callbacks cannot show a scene or refresh a new activation's watchdog. */
    @Test public void queuedOldActivationCannotResurrectOrRefreshReplacement() {
        assertTrue(start()); MediaSessionProbe.Listener old=runtime.listener(); runtime.close(); assertTrue(start());
        old.onSnapshot(valid(first().startMs,true)); assertEquals(0,drawing.shown.size());
        sample(first().startMs,true); now+=2501; old.onSnapshot(valid(first().startMs+1000,true));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300)); assertNull(drawing.root());
    }

    /** Activity pause retains already-confirmed ownership; cancel revokes it without a server fallback. */
    @Test public void sourcePauseKeepsCapturedCacheButCancelInvalidatesIt() {
        source.pause(); assertNull(source.confirmedCacheReference()); assertTrue(reference.isCurrent(now));
        assertTrue(start()); sample(first().startMs,true); int http=requests;
        source.cancel("entry-"+envelope.binding.track); sample(first().startMs+1000,true);
        assertNull(drawing.root()); assertEquals(http,requests); assertNull(runtime.listener());
    }

    /** Known consent failure and monotonic expiry cannot renew themselves from cached metadata. */
    @Test public void knownRevocationAndExpiryCloseBeforeFurtherDisplay() {
        assertTrue(start()); sample(first().startMs,true); denyAssociation=true; source.refresh();
        sample(first().startMs+1000,true); assertNull(drawing.root()); assertFalse(reference.isCurrent(now));
        assertFalse(reference.isCurrent(now+8L*86400000));
    }

    /** Generation substitution invalidates the whole artifact binding. */
    @Test public void generationChangeClosesAndNeverReadsSecretsOrWritesCache() throws Exception {
        assertTrue(start()); sample(first().startMs,true); int calls=secrets.calls;
        state.write("identity",new JSONObject().put("deviceId",envelope.binding.device).put("generation","00000000-0000-4000-8000-000000000009"));
        sample(first().startMs+1000,true); assertNull(drawing.root()); assertEquals(calls,secrets.calls);
        assertEquals(0,store().read().acknowledgedRevision());
    }

    /** An expired captured grant cannot be extended by captures or keep a visible scene alive. */
    @Test public void monotonicConsentExpiryClosesTheActualRuntime() {
        assertTrue(start()); sample(first().startMs,true); MediaSessionProbe.Listener old=runtime.listener();
        now+=8L*86400000; old.onSnapshot(valid(first().startMs+1000,true));
        assertNull(drawing.root()); assertNull(runtime.listener()); assertFalse(reference.isCurrent(now));
        assertFalse(start());
    }

    /** Missing or foreign receipts fail before reusing any durable runtime payload. */
    @Test public void receiptSubstitutionPreventsRestorationAndRendering() throws Exception {
        JSONObject wrong=envelope.binding.json().put("digest","0".repeat(64));
        state.write("receipt",wrong);
        assertThrows(IllegalArgumentException.class,()->TvFirst009TextCache.load(store(),reference,now));
        assertFalse(start()); assertEquals(0,drawing.shown.size());
    }

    /** Even a small paused seek outside the exact display window cannot leave an expired card visible. */
    @Test public void expiredWindowAndPausedPositionDiscontinuityNeverRemainVisible() {
        assertTrue(start()); ScheduledTrack.Event event=first(); sample(event.startMs+event.durationMs+1,true);
        assertEquals(0,drawing.shown.size()); runtime.close(); assertTrue(start());
        sample(event.startMs,true);
        for(long at=event.startMs+1000;at<event.startMs+event.durationMs;at+=1000) sample(at,false);
        assertNotNull(drawing.root()); sample(event.startMs+event.durationMs,false); assertNull(drawing.root());
    }

    /** Reproduce M5's short expired DUE, then verify the new boundary refuses it before any SHOW. */
    @Test public void syntheticShortWindowDueThenExpireIsFilteredWithoutChangingM5() {
        ScheduledTrack.Event synthetic=new ScheduledTrack.Event("synthetic-short","historical synthetic fixture",10000,1000,null);
        final int[] due={0}, admitted={0};
        MediaCalendarScheduler scheduler=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
            /** No visual permission is inferred from this synthetic temporal test. */
            @Override public void onEligibility(String token,boolean eligible) {}
            /** No playback implementation is replaced by this synthetic seam. */
            @Override public void onPlayback(String token,boolean playing,boolean freeze) {}
            /** Test the actual shared DUE guard against a demonstrated core boundary. */
            @Override public void onDue(String token,String id) { due[0]++; if(TvFirst009TextRuntime.withinWindow(synthetic,11500))admitted[0]++; }
            /** Retain the scheduler's expiry callback without producing a view. */
            @Override public void onExpire(String token,String id) {}
        });
        scheduler.load(new MediaCalendar(Collections.singletonList(new SceneEvent("synthetic-short",10000,1000)),true),"synthetic");
        scheduler.onObservation(new MediaObservation(true,11500,true));
        assertEquals(1,due[0]); assertEquals(0,admitted[0]);
    }

    /** Unknown ad/content-time ends the explicitly ad-free session; it never shifts canonical timings. */
    @Test public void unknownTimelineAndClosedOwnerDecisionNeverAuthorizeRendering() {
        assertFalse(runtime.start(prepared,TvFirst009TextRuntime.Decision.CLOSED)); assertEquals(0,drawing.shown.size());
        assertTrue(start()); MediaSessionProbe.Listener old=runtime.listener(); sample(first().startMs,true);
        runtime.timelineBecameUnknown(); old.onSnapshot(valid(first().startMs+1000,true)); assertNull(drawing.root());
        assertEquals(1,drawing.shown.size());
        assertTrue(start()); sample(first().startMs,true); old=runtime.listener();
        assertFalse(runtime.start(prepared,TvFirst009TextRuntime.Decision.CLOSED));
        old.onSnapshot(valid(first().startMs+1000,true)); assertNull(drawing.root());
    }

    /** Actual composition refuses missing Android permissions before starting any passive probe. */
    @Test public void sessionCompositionIsClosedWithoutOverlayAndNotificationAccess() {
        ShadowSettings.setCanDrawOverlays(false);
        assertNull(TvFirst009TextSession.open(context,source,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION));
        ShadowSettings.setCanDrawOverlays(true);
        assertNull(TvFirst009TextSession.open(context,source,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION));
        assertNull(TvFirst009TextSession.open(context,source,TvFirst009TextRuntime.Decision.CLOSED));
    }

    /** The explicit factory composes the actual passive probe and closes even with no active session. */
    @Test public void realCompositionStartsNoTransportAndClosesWithoutCacheOrHttpMutation() {
        Settings.Secure.putString(context.getContentResolver(),"enabled_notification_listeners",NotificationAccess.component(context).flattenToString());
        int http=requests,calls=secrets.calls;
        TvFirst009TextSession session=TvFirst009TextSession.open(context,source,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION);
        assertNotNull(session); session.close();
        assertEquals(http,requests); assertEquals(calls,secrets.calls); assertEquals(0,store().read().acknowledgedRevision());
    }

    /** Native metadata getters retain identity fields which an ADB description need not enumerate. */
    @Test public void originalNativeControllerReaderKeepsSeparateMetadataFields() {
        MediaSession session=new MediaSession(context,"SYNTHETIC_NATIVE_METADATA");
        try {
            MediaMetadata publishedMetadata=new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,prepared.video.track.mediaIdentity.videoId)
                    .putString(MediaMetadata.METADATA_KEY_TITLE,"Columbo")
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,"Eaux troubles")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,5884768L).build();
            PlaybackState publishedState=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,first().startMs,1f,SystemClock.elapsedRealtime()).build();
            MediaController controller=new MediaController(context,session.getSessionToken());
            // Robolectric's controller shadow does not mirror MediaSession setters through Binder.
            // Supply published framework getter values at that existing simulator boundary only.
            shadowOf(controller).setMetadata(publishedMetadata);
            shadowOf(controller).setPlaybackState(publishedState);
            shadowOf(controller).setPackageName(prepared.video.track.targetPackage);
            MediaSessionProbe probe=new MediaSessionProbe(context);
            MediaSessionProbe.Snapshot read=ReflectionHelpers.callInstanceMethod(probe,"snapshotController",
                    ReflectionHelpers.ClassParameter.from(int.class,0),ReflectionHelpers.ClassParameter.from(MediaController.class,controller));
            assertEquals(prepared.video.track.mediaIdentity.videoId,read.mediaId);
            assertEquals("Columbo",read.title); assertEquals("Eaux troubles",read.subtitle); assertEquals(5884768L,read.durationMs);
            assertTrue(MediaIdentityMatcher.matches(prepared.video.track,read));
        } finally { session.release(); }
    }

    /** Open the isolated software profile under its explicit local qualification decision. */
    private boolean start() { return runtime.start(prepared,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION); }
    /** Keep the original first editorial event, without retiming the real fixture. */
    private ScheduledTrack.Event first() { return prepared.video.track.comments.get(0); }
    /** Reopen the exact generation-specific Android backend used by 008. */
    private InstallationStore store() { return new InstallationStore(new AndroidInstallationBackend(context,"tvfirst008_cache_"+envelope.binding.generation.replace("-",""))); }
    /** Deliver one fresh native-equivalent capture on the owner thread. */
    private void sample(long position,boolean playing) { runtime.listener().onSnapshot(valid(position,playing)); }
    /** Bind the real projected identity to a controlled native position. */
    private MediaSessionProbe.Snapshot valid(long at,boolean playing) { return snapshot(at,playing,prepared.video.track.targetPackage,prepared.video.track.mediaIdentity.videoId,"","",0); }
    /** Retain the observed PAUSED/speed=1 combination in every pause test. */
    private MediaSessionProbe.Snapshot snapshot(long at,boolean playing,String pkg,String id,String title,String subtitle,long age) {
        return new MediaSessionProbe.Snapshot(pkg,playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,playing?"PLAYING":"PAUSED",at,at,1f,age,id,title,subtitle,-1);
    }

    /** Delegate drawing to the actual unchanged Android renderer and inspect its resulting text tree. */
    private final class Drawing implements SceneRuntimeController.SceneSink {
        final List<String> shown=new ArrayList<>();
        final SceneRenderer renderer=new SceneRenderer(context,asset->null,()->{throw new AssertionError("Unexpected permission loss");});
        /** The original renderer still owns asset and view preflight. */
        @Override public boolean preflight(OverlayManifest.Scene scene) { return Settings.canDrawOverlays(context)&&renderer.preflight(scene); }
        /** Verify actual cache text and non-interactive Android window flags after a real render. */
        @Override public void show(OverlayManifest.Scene scene) {
            renderer.render(scene); assertNotNull(root()); shown.add(scene.id);
            ScheduledTrack.Event event=prepared.video.eventsById.get(scene.id); assertNotNull(event);
            assertTrue("Exact original text must reach a native TextView",contains(root(),event.text));
            WindowManager.LayoutParams params=ReflectionHelpers.getField(renderer,"windowParams");
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,params.type);
            assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
            assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
        }
        /** No delayed exit may retain the old view after expiry. */
        @Override public void hide(OverlayManifest.Scene scene) { renderer.dismissNow(); }
        /** Ineligibility closes all existing views immediately. */
        @Override public void hideAll() { renderer.dismissNow(); }
        /** Inspect the real renderer-owned window rather than a replacement fake renderer. */
        View root() { return ReflectionHelpers.getField(renderer,"root"); }
        /** Search native group descendants without logging any editorial content. */
        boolean contains(View view,String text) {
            if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return true;
            if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;
            return false;
        }
    }

    /** Count synthetic-secret access; no real credential is loaded during qualification. */
    private static final class CountingSecrets implements SecretStore {
        final Map<String,String> values=new HashMap<>(); int calls;
        /** Count explicit synthetic credential writes. */
        @Override public boolean put(String key,String value) { calls++; values.put(key,value); return true; }
        /** Count explicit synthetic credential reads. */
        @Override public String get(String key) { calls++; return values.get(key); }
        /** Count explicit synthetic credential removals. */
        @Override public boolean remove(String key) { calls++; values.remove(key); return true; }
        /** Count explicit synthetic credential presence checks. */
        @Override public boolean contains(String key) { calls++; return values.containsKey(key); }
    }

    /** Run only the actual source's worker operations synchronously; Android callbacks remain queued. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean closed;
        /** End the synthetic executor without touching Android services. */
        @Override public void shutdown() { closed=true; }
        /** No unfinished work exists in this deterministic executor. */
        @Override public List<Runnable> shutdownNow() { closed=true; return Collections.emptyList(); }
        /** Report the local synthetic lifecycle only. */
        @Override public boolean isShutdown() { return closed; }
        /** Synchronous work is finished when closed. */
        @Override public boolean isTerminated() { return closed; }
        /** No wait or retry path exists here. */
        @Override public boolean awaitTermination(long timeout,TimeUnit unit) { return closed; }
        /** Execute exactly one operation with no implicit retry. */
        @Override public void execute(Runnable operation) { if(closed)throw new IllegalStateException("CLOSED"); operation.run(); }
    }
}
