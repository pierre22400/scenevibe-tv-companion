package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.presentation.TvShellController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * 009-004 real Activity -> OverlayService -> passive native probe -> exact confirmed
 * Columbo cache -> unchanged M5 and SceneRenderer. Only Android framework session
 * getters are controlled by Robolectric. This is software evidence, never Sony evidence.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35},qualifiers="w1920dp-h1080dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvFirst009SonyLifecycleTest {
    private Context context;
    private TvFirst008Source source;
    private TvFirst008State state;
    private TvFirst008Wire.Envelope envelope;
    private MediaSession publisher;
    private MediaController media;
    private ServiceController<OverlayService> serviceController;
    private OverlayService service;
    private ActivityController<MainActivity> activity;
    private ActivityController<android.app.Activity> requester;
    private int httpCalls;
    private boolean deny;

    /** Use the accepted real bytes with a synthetic credential and an in-memory HTTP boundary. */
    @Before public void prepare() throws Exception {
        assertTrue(BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION);
        context=RuntimeEnvironment.getApplication();
        requester=Robolectric.buildActivity(android.app.Activity.class).setup();
        ReflectionHelpers.setStaticField(TvFirst009TextServiceOwner.class,"pending",null);
        ReflectionHelpers.setStaticField(TvFirst009TextServiceOwner.class,"liveOwner",0L);
        ReflectionHelpers.setStaticField(TvFirst009TextServiceOwner.class,"latest",new TvFirst009TextServiceOwner.Status("STOPPED",-1,false,0,-1));
        String fixture=System.getProperty("scenevibe009.fixture");assertNotNull(fixture);
        JSONObject raw=new JSONObject(new String(Files.readAllBytes(Path.of(fixture)),java.nio.charset.StandardCharsets.UTF_8));
        envelope=new TvFirst008Wire.Envelope(raw,new TvFirst008Wire.Binding(raw));
        SharedPreferences prefs=context.getSharedPreferences("tvfirst009004_qualification",0);prefs.edit().clear().commit();
        state=new TvFirst008State(prefs,new MemorySecrets());state.claimed(envelope.binding.json().put("deviceToken","Q".repeat(43)));
        state.write("binding",envelope.binding.json());state.write("receipt",envelope.binding.json());
        assertTrue(new TvFirst008Cache(context,envelope.binding.generation).prepare(envelope));
        TvFirst008HttpPort http=(method,path,token,body)->{
            httpCalls++;
            if(path.contains("/association?")) {
                if(deny)throw new TvFirst008Http.Failure(403);
                return envelope.binding.json().put("accountAssociated",true).put("rendering","FORBIDDEN")
                    .put("currentRevision",envelope.binding.revision).put("consentExpiresAt",Instant.now().plusSeconds(86400).toString());
            }
            if(path.contains("/library?"))return new JSONObject().put("items",new JSONArray().put(new JSONObject()
                .put("id",envelope.binding.track).put("eligibility","REQUIRES_VALIDATION").put("title","Columbo — Eaux troubles").put("filmTitle","Columbo — Eaux troubles"))).put("next",JSONObject.NULL);
            if(path.contains("/assignment-status?"))return envelope.binding.json().put("status","CACHE_CONFIRMED");
            throw new AssertionError("No fallback HTTP route is permitted");
        };
        source=new TvFirst008Source(context,state,http,"https://qualification.invalid",new Direct());
        source.resume();shadowOf(Looper.getMainLooper()).idle();assertNotNull(source.confirmedCacheReference());
        ShadowSettings.setCanDrawOverlays(true);grantMedia(true);
        publisher=new MediaSession(context,"CONTROLLED_ANDROID_GETTERS_NOT_SONY");
        media=new MediaController(context,publisher.getSessionToken());
        shadowOf(media).setPackageName("com.amazon.amazonvideo.livingroom");identity(true);playback(0,PlaybackState.STATE_PLAYING);
        shadowOf(context.getSystemService(MediaSessionManager.class)).addController(media);
    }

    /** Release only the simulated lifetimes, so no queued callback can contaminate another test. */
    @After public void release() {
        if(serviceController!=null)serviceController.destroy();
        if(activity!=null)activity.close();
        if(requester!=null)requester.close();
        if(source!=null)source.close();
        if(publisher!=null)publisher.release();
    }

    /** Exact merged variant enables only explicit service permissions and keeps boot disabled. */
    @Test public void manifestAndVariantAreUnambiguous() throws Exception {
        assertEquals(14,BuildConfig.VERSION_CODE);assertFalse(BuildConfig.DEBUG);assertFalse(BuildConfig.ENABLE_LAN_DEV);
        assertTrue(BuildConfig.TVFIRST_DELIVERY_ONLY);assertTrue(BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION);
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.MATCH_DISABLED_COMPONENTS);
        assertEquals(new HashSet<>(Arrays.asList("android.permission.INTERNET","android.permission.SYSTEM_ALERT_WINDOW","android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_SPECIAL_USE")),new HashSet<>(Arrays.asList(info.requestedPermissions)));
        for(android.content.pm.ServiceInfo s:info.services){assertTrue(s.enabled);assertFalse(s.exported);}
        for(android.content.pm.ActivityInfo r:info.receivers)assertFalse(r.enabled);
        assertEquals("SceneVibe TV",context.getApplicationInfo().loadLabel(context.getPackageManager()));
    }

    /** Boot and replacement cannot construct any credential/service authority. */
    @Test public void bootAndReplacementNeverStart() {
        Map<String,?> before=new HashMap<>(context.getSharedPreferences("cloud_track",0).getAll());
        for(String action:new String[]{Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED})new BootReceiver().onReceive(context,new Intent(action));
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        assertEquals(before,context.getSharedPreferences("cloud_track",0).getAll());
    }

    /** Cache confirmation and a full launcher lifecycle alone never mint a TEXT permit. */
    @Test public void launcherAndCacheNeverAutomaticallyActivate() {
        openActivity();assertFalse(TvFirst009TextServiceOwner.status().active);
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        activity.pause().stop().destroy();activity=null;
        assertFalse(TvFirst009TextServiceOwner.status().active);
    }

    /** Framework restarts and old Cloud/boot actions never restore a persisted activation. */
    @Test public void nullBootCloudAndForgedStartAreNotSticky() {
        for(String action:new String[]{null,OverlayService.ACTION_BOOT_PREPARE,OverlayService.ACTION_CLOUD_CONNECT,TvFirst009TextServiceOwner.START}){
            serviceController=Robolectric.buildService(OverlayService.class).create();service=serviceController.get();
            assertEquals(Service.START_NOT_STICKY,service.onStartCommand(action==null?null:new Intent().setAction(action),0,1));
            assertNull(ownerSession());assertTrue(shadowOf(service).isStoppedBySelf());assertLegacyInert();
            serviceController.destroy();serviceController=null;
        }
    }

    /** D-pad activation passes one confirmed reference; Activity destruction does not stop the service. */
    @Test public void explicitRemoteButtonSurvivesBackgroundAndShowsOriginalFirstText() {
        openActivity();View start=findText(activity.get().findViewById(android.R.id.content),"Activer les commentaires TEXT");assertNotNull(start);
        assertTrue(start.isFocusable());start.requestFocus();assertTrue(start.hasFocus());
        start.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        start.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
        shadowOf(Looper.getMainLooper()).idle();
        Intent requested=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(requested);
        int calls=httpCalls;Map<String,?> metadata=new HashMap<>(context.getSharedPreferences("tvfirst009004_qualification",0).getAll());
        source.pause();activity.pause().stop().destroy();activity=null;
        launch(requested);assertTrue(TvFirst009TextServiceOwner.status().active);assertLegacyInert();
        firstWindow();assertNotNull(root());
        String actual=TvFirst009TextCache.load(context,referenceFromRuntime()).video.track.comments.get(0).text;
        assertTrue(text(root()).contains(actual));assertEquals(1,TvFirst009TextServiceOwner.status().renderRequests);
        assertEquals(32988,TvFirst009TextServiceOwner.status().lastRequestedMs);
        assertEquals(calls,httpCalls);assertEquals(metadata,context.getSharedPreferences("tvfirst009004_qualification",0).getAll());
        assertEquals(0,store().read().acknowledgedRevision());assertEquals(1009,shadowOf(service).getLastForegroundNotificationId());
    }

    /** An activation is one-use, so delivery replay cannot create a second drawing owner. */
    @Test public void duplicateRequestAndReplayCannotReactivate() {
        Intent request=request();assertFalse(TvFirst009TextServiceOwner.requestStart(requester.get(),source));
        launch(request);assertTrue(TvFirst009TextServiceOwner.status().active);
        service.onStartCommand(request,0,2);assertFalse(TvFirst009TextServiceOwner.status().active);assertNull(root());
    }

    /** Expired Activity handoffs fail before constructing a probe or rendering anything. */
    @Test public void expiredStartRequestIsRejected() {
        Intent request=request();ShadowSystemClock.advanceBy(Duration.ofSeconds(6));launch(request);
        assertFalse(TvFirst009TextServiceOwner.status().active);assertEquals("START_PROOF_INVALID",TvFirst009TextServiceOwner.status().code);assertNull(ownerSession());
    }

    /** Missing Android permissions refuse the button without generating any foreground intent. */
    @Test public void permissionDeniedCannotStart() {
        ShadowSettings.setCanDrawOverlays(false);assertFalse(TvFirst009TextServiceOwner.requestStart(requester.get(),source));
        ShadowSettings.setCanDrawOverlays(true);grantMedia(false);assertFalse(TvFirst009TextServiceOwner.requestStart(requester.get(),source));
        assertEquals("PERMISSION_REQUIRED",TvFirst009TextServiceOwner.status().code);assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    /** Unknown or mismatched episode metadata never bypasses the unchanged matcher. */
    @Test public void wrongIdentityRemovesVisibleTextAndExplainsRefusal() {
        launch(request());firstWindow();assertNotNull(root());identity(false);sample(34000,PlaybackState.STATE_PLAYING);
        assertNull(root());assertEquals("MEDIA_IDENTITY_BLOCKED",TvFirst009TextServiceOwner.status().code);
        assertTrue(TvFirst009TextServiceOwner.describe().contains("Épisode non reconnu"));
    }

    /** A negative native position closes a visible scene rather than freezing stale eligibility. */
    @Test public void unknownPositionRemovesVisibleText() {
        launch(request());firstWindow();sample(-1,PlaybackState.STATE_PLAYING);assertNull(root());
        assertEquals("POSITION_UNKNOWN",TvFirst009TextServiceOwner.status().code);
    }

    /** Loss of Android sessions removes the renderer immediately without starting a fallback. */
    @Test public void missingNativeSessionRemovesVisibleText() {
        launch(request());firstWindow();shadowOf(context.getSystemService(MediaSessionManager.class)).clearControllers();probeSample();
        assertNull(root());assertEquals("SESSION_UNAVAILABLE",TvFirst009TextServiceOwner.status().code);assertLegacyInert();
    }

    /** Original PAUSED/speed=1 behavior freezes a card; its original endpoint removes it after resume. */
    @Test public void pauseAndOriginalWindowExpiryArePreserved() {
        launch(request());firstWindow();sample(33988,PlaybackState.STATE_PAUSED);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertNotNull(root());
        sample(34988,PlaybackState.STATE_PLAYING);sample(40988,PlaybackState.STATE_PLAYING);assertNull(root());
    }

    /** Permission revocation closes the exact service lifetime; cache and credentials are not cleared. */
    @Test public void permissionLossStopsForegroundWithoutDataLoss() {
        launch(request());firstWindow();ShadowSettings.setCanDrawOverlays(false);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertFalse(TvFirst009TextServiceOwner.status().active);assertTrue(shadowOf(service).isStoppedBySelf());assertTrue(shadowOf(service).isForegroundStopped());
        assertNotNull(state.read("receipt"));assertNotNull(store().read().snapshot());
    }

    /** Expired consent closes even without a new MEDIA callback and retains only durable cache bytes. */
    @Test public void consentExpiryStopsWithoutCloudMutation() {
        launch(request());firstWindow();int calls=httpCalls;TvFirst009TextSession captured=ownerSession();
        ShadowSystemClock.advanceBy(Duration.ofDays(8));shadowOf(Looper.getMainLooper()).idle();
        assertFalse(captured.isActive());assertFalse(TvFirst009TextServiceOwner.status().active);assertTrue(shadowOf(service).isStoppedBySelf());
        assertEquals("CACHE_OR_CONSENT_INVALID",TvFirst009TextServiceOwner.status().code);assertEquals(calls,httpCalls);assertNotNull(state.read("receipt"));
    }

    /** A known account denial on the same confirmed source invalidates the captured reference. */
    @Test public void serverDenialRevokesCurrentReference() {
        launch(request());firstWindow();deny=true;source.resume();shadowOf(Looper.getMainLooper()).idle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertFalse(TvFirst009TextServiceOwner.status().active);assertTrue(shadowOf(service).isStoppedBySelf());
    }

    /** The explicit stop control and an old captured callback cannot resurrect a view. */
    @Test public void stopNotificationAndLateCallbacksCannotResurrect() {
        launch(request());firstWindow();TvFirst009TextSession session=ownerSession();TvFirst009TextRuntime runtime=ReflectionHelpers.getField(session,"runtime");
        MediaSessionProbe.Listener old=runtime.listener();SceneRenderer renderer=ReflectionHelpers.getField(session,"renderer");
        Notification notification=shadowOf(service).getLastForegroundNotification();assertNotNull(notification.actions);assertEquals("Arrêter TEXT",notification.actions[0].title);
        service.onStartCommand(new Intent().setAction(TvFirst009TextServiceOwner.STOP),0,2);
        old.onSnapshot(new MediaSessionProbe.Snapshot("com.amazon.amazonvideo.livingroom",PlaybackState.STATE_PLAYING,"PLAYING",32988,32988,1,0,"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e","","",5884768));
        assertNull(ReflectionHelpers.getField(renderer,"root"));assertFalse(TvFirst009TextServiceOwner.status().active);assertTrue(shadowOf(service).isForegroundStopped());
    }

    /** A changed receipt cannot be silently repaired or replaced by an embedded synthetic Track. */
    @Test public void missingReceiptRefusesStartWithoutRepair() {
        state.write("receipt",null);assertFalse(TvFirst009TextServiceOwner.requestStart(requester.get(),source));
        assertEquals("COLUMBO_CACHE_NOT_READY",TvFirst009TextServiceOwner.status().code);assertNull(state.read("receipt"));
    }

    /** The new diagnostics screen is read-only and contains no reset, private token or synthetic secret. */
    @Test public void diagnosticsStayBoundedAndReadOnly() {
        try(ActivityController<DiagnosticsActivity> screen=Robolectric.buildActivity(DiagnosticsActivity.class).setup()) {
            String copy=text(screen.get().findViewById(android.R.id.content));assertTrue(copy.contains("TEXT"));assertFalse(copy.contains("Reset SceneVibe Cloud"));assertFalse(copy.contains("Q".repeat(43)));
        }
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    /** The visible TV stop button requests only this service's termination, with cache intact. */
    @Test public void explicitTvStopButtonRequestsTermination() {
        openActivity();launch(request());firstWindow();
        View stop=findText(activity.get().findViewById(android.R.id.content),"Arrêter les commentaires TEXT");assertNotNull(stop);stop.performClick();
        assertEquals("STOP_REQUESTED",TvFirst009TextServiceOwner.status().code);
        serviceController.destroy();serviceController=null;assertNull(root());
        assertNotNull(state.read("receipt"));assertFalse(TvFirst009TextServiceOwner.status().active);
    }

    /** An old service's diagnostic writer cannot change a newer explicitly started lifetime. */
    @Test public void retiredServiceWriterCannotAffectNewOwner() {
        launch(request());Object old=ReflectionHelpers.getField(service,"textQualification");
        serviceController.destroy();serviceController=null;launch(request());
        TvFirst009TextServiceOwner.Status before=TvFirst009TextServiceOwner.status();
        ReflectionHelpers.callInstanceMethod(old,"observed",ReflectionHelpers.ClassParameter.from(String.class,"TEXT_RENDER_REQUESTED"),ReflectionHelpers.ClassParameter.from(long.class,32988L),ReflectionHelpers.ClassParameter.from(boolean.class,true));
        assertSame(before,TvFirst009TextServiceOwner.status());assertTrue(TvFirst009TextServiceOwner.status().active);assertNull(root());
    }

    /** A real capture lapse is a current refusal; a fresh sample recovers without erasing incident history. */
    @Test public void diagnosticTimeoutRecoversWithoutStaleCurrentError() {
        launch(request());firstWindow();
        MediaSessionProbe probe=ReflectionHelpers.getField(ownerSession(),"probe");probe.stop();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("CAPTURE_TIMEOUT",TvFirst009TextServiceOwner.status().code);assertNull(root());
        assertTrue(TvFirst009TextServiceOwner.status().active);
        long renders=TvFirst009TextServiceOwner.status().renderRequests;
        sample(50000,PlaybackState.STATE_PLAYING);
        TvFirst009TextServiceOwner.Status status=TvFirst009TextServiceOwner.status();
        assertEquals("MEDIA_PLAYING",status.code);assertEquals("CAPTURE_TIMEOUT",status.lastIssue);
        assertEquals(renders,status.renderRequests);assertTrue(status.lastIssueAtMs>=0);
        String copy=TvFirst009TextServiceOwner.describe();
        assertTrue(copy.contains("État courant : MEDIA_PLAYING"));
        assertTrue(copy.contains("Dernier incident mémorisé : CAPTURE_TIMEOUT"));
    }

    /** A render request and subsequent PLAYING observation retain distinct facts and timestamps. */
    @Test public void diagnosticRenderEventDoesNotReplaceMediaState() {
        launch(request());firstWindow();
        TvFirst009TextServiceOwner.Status status=TvFirst009TextServiceOwner.status();
        assertEquals("MEDIA_PLAYING",status.code);assertEquals(1,status.renderRequests);
        assertTrue(status.lastRenderAtMs>=0);assertEquals(32988,status.lastRequestedMs);
        assertEquals("NONE",status.lastIssue);
        sample(33988,PlaybackState.STATE_PAUSED);
        assertEquals("MEDIA_PAUSED",TvFirst009TextServiceOwner.status().code);
        assertEquals(status.lastRenderAtMs,TvFirst009TextServiceOwner.status().lastRenderAtMs);
        assertEquals(1,TvFirst009TextServiceOwner.status().renderRequests);
        assertTrue(TvFirst009TextServiceOwner.describe().contains("ne prouve pas l’affichage"));
    }

    /** Session-local counters and incident history cannot leak into the next explicitly armed session. */
    @Test public void diagnosticNewSessionResetsHistoryAndCounters() {
        launch(request());firstWindow();identity(false);sample(34000,PlaybackState.STATE_PLAYING);
        assertEquals("MEDIA_IDENTITY_BLOCKED",TvFirst009TextServiceOwner.status().lastIssue);
        serviceController.destroy();serviceController=null;identity(true);playback(0,PlaybackState.STATE_PLAYING);
        launch(request());
        assertEquals(0,TvFirst009TextServiceOwner.status().renderRequests);
        assertEquals(-1,TvFirst009TextServiceOwner.status().lastRenderAtMs);
        assertEquals("NONE",TvFirst009TextServiceOwner.status().lastIssue);
    }

    /** The same visible detailed screen follows current service facts without a refresh click or HTTP call. */
    @Test public void diagnosticVisibleScreenRefreshesAndPauseRetiresTimer() {
        launch(request());firstWindow();int calls=httpCalls;
        try(ActivityController<DiagnosticsActivity> screen=Robolectric.buildActivity(DiagnosticsActivity.class).setup()) {
            View view=screen.get().findViewById(android.R.id.content);
            assertTrue(text(view).contains("État courant : MEDIA_PLAYING"));
            identity(false);sample(34000,PlaybackState.STATE_PLAYING);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
            assertTrue(text(view).contains("État courant : MEDIA_IDENTITY_BLOCKED"));
            screen.pause();String frozen=text(view);
            identity(true);sample(50000,PlaybackState.STATE_PLAYING);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
            assertEquals(frozen,text(view));
            screen.resume();assertTrue(text(view).contains("État courant : MEDIA_PLAYING"));
            assertTrue(text(view).contains("Dernier incident mémorisé : MEDIA_IDENTITY_BLOCKED"));
        }
        assertEquals(calls,httpCalls);assertTrue(TvFirst009TextServiceOwner.status().active);
    }

    /** D-pad ON/OFF reuses existing service commands and never clears confirmed cache or credentials. */
    @Test public void diagnosticRemoteOnOffReflectsCurrentLifetimeWithoutFocusTheft() {
        openActivity();View view=activity.get().findViewById(android.R.id.content);
        View start=findText(view,"Activer les commentaires TEXT"),stop=findText(view,"Arrêter les commentaires TEXT");
        assertTrue(start.isEnabled());assertFalse(stop.isEnabled());start.requestFocus();
        start.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        start.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
        shadowOf(Looper.getMainLooper()).idle();
        Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(intent);launch(intent);
        firstWindow();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(start.isEnabled());assertTrue(stop.isEnabled());stop.requestFocus();assertTrue(stop.hasFocus());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));assertTrue(stop.hasFocus());
        stop.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        stop.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals("STOP_REQUESTED",TvFirst009TextServiceOwner.status().code);
        serviceController.destroy();serviceController=null;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(start.isEnabled());assertFalse(stop.isEnabled());assertNull(root());
        assertNotNull(state.read("receipt"));assertNotNull(source.confirmedCacheReference());
    }

    /** Cancelling an unconsumed ON handoff prevents a delayed start from arming anything. */
    @Test public void diagnosticPendingOnCanBeCancelledWithoutFallback() {
        Intent delayed=request();assertTrue(TvFirst009TextServiceOwner.controlBusy());
        TvFirst009TextServiceOwner.requestStop(requester.get());
        assertFalse(TvFirst009TextServiceOwner.controlBusy());launch(delayed);
        assertFalse(TvFirst009TextServiceOwner.status().active);assertNull(ownerSession());
        assertEquals("START_PROOF_INVALID",TvFirst009TextServiceOwner.status().code);
        assertNotNull(state.read("receipt"));assertLegacyInert();
    }

    /** Polling a stopped profile's diagnostics never starts a service or grants a TEXT permit. */
    @Test public void diagnosticPollingCannotActivateStoppedText() {
        try(ActivityController<DiagnosticsActivity> screen=Robolectric.buildActivity(DiagnosticsActivity.class).setup()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
            assertTrue(text(screen.get().findViewById(android.R.id.content)).contains("TEXT : OFF"));
        }
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        assertFalse(TvFirst009TextServiceOwner.status().active);
    }

    /** Incidents and render observations are deliberately process-local and never alter durable state. */
    @Test public void diagnosticHistoryIsNotPersistedOrSecretBearing() {
        Map<String,?> before=new HashMap<>(context.getSharedPreferences("tvfirst009004_qualification",0).getAll());
        launch(request());firstWindow();identity(false);sample(34000,PlaybackState.STATE_PLAYING);
        assertEquals(before,context.getSharedPreferences("tvfirst009004_qualification",0).getAll());
        String copy=TvFirst009TextServiceOwner.describe();
        assertFalse(copy.contains("Q".repeat(43)));assertFalse(copy.contains(envelope.binding.generation));
        assertFalse(copy.contains(envelope.binding.device));
        ReflectionHelpers.setStaticField(TvFirst009TextServiceOwner.class,"latest",new TvFirst009TextServiceOwner.Status("STOPPED",-1,false,0,-1));
        assertEquals("NONE",TvFirst009TextServiceOwner.status().lastIssue);
        assertEquals(0,TvFirst009TextServiceOwner.status().renderRequests);
    }

    /** Request via the public explicit Activity entry only, then inspect the actual Android intent. */
    private Intent request() { assertTrue(TvFirst009TextServiceOwner.requestStart(requester.get(),source));Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(intent);return intent; }
    /** Android ServiceController creates the actual service without replacing its owner implementation. */
    private void launch(Intent intent) {serviceController=Robolectric.buildService(OverlayService.class).create();service=serviceController.get();assertEquals(Service.START_NOT_STICKY,service.onStartCommand(intent,0,1));}
    /** Install only the in-memory source transport seam before MainActivity's ordinary resume. */
    private void openActivity() {
        activity=Robolectric.buildActivity(MainActivity.class).create();TvShellController shell=ReflectionHelpers.getField(activity.get(),"presentation");
        ((TvFirst008Source)shell.sourceForLocalQualification()).close();ReflectionHelpers.setField(shell,"source",source);
        activity.start().resume().visible();shadowOf(Looper.getMainLooper()).idle();
        View settings=findText(activity.get().findViewById(android.R.id.content),activity.get().getString(R.string.tv_settings));assertNotNull(settings);settings.performClick();shadowOf(Looper.getMainLooper()).idle();
    }
    /** Supply published native metadata at the simulator boundary; no matcher stub is installed. */
    private void identity(boolean match) {shadowOf(media).setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,match?"amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e":"").putString(MediaMetadata.METADATA_KEY_TITLE,match?"Columbo":"PrimeVideo").putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,match?"Eaux troubles":"").putLong(MediaMetadata.METADATA_KEY_DURATION,5884768).build());}
    /** PAUSED intentionally retains speed=1, as the historical native provider did. */
    private void playback(long at,int state) {if(SystemClock.elapsedRealtime()==0)ShadowSystemClock.advanceBy(Duration.ofMillis(1));shadowOf(media).setPlaybackState(new PlaybackState.Builder().setState(state,at,1f,SystemClock.elapsedRealtime()).build());}
    /** Execute the real passive probe reader on the owner's main thread. */
    private void probeSample() {MediaSessionProbe probe=ReflectionHelpers.getField(ownerSession(),"probe");ReflectionHelpers.callInstanceMethod(probe,"sample");}
    /** Deliver a controlled Android getter sample while keeping the original extrapolation code. */
    private void sample(long at,int state) {playback(at,state);probeSample();}
    /** Approach the unchanged first comment without a synthetic seek into its middle. */
    private void firstWindow() {for(long at=30000;at<=32000;at+=1000)sample(at,PlaybackState.STATE_PLAYING);sample(32988,PlaybackState.STATE_PLAYING);}
    /** Read the actual service-owned composition, not a surrogate scene sink. */
    private TvFirst009TextSession ownerSession() {Object owner=ReflectionHelpers.getField(service,"textQualification");return owner==null?null:ReflectionHelpers.getField(owner,"session");}
    /** Access the captured authority solely for exact original-text verification. */
    private TvFirst008Source.ConfirmedCacheReference referenceFromRuntime() {TvFirst009TextRuntime runtime=ReflectionHelpers.getField(ownerSession(),"runtime");TvFirst009TextCache.Prepared cache=ReflectionHelpers.getField(runtime,"cache");return cache.reference;}
    /** Inspect the real Android renderer-owned overlay root. */
    private View root() {TvFirst009TextSession session=ownerSession();if(session==null)return null;SceneRenderer renderer=ReflectionHelpers.getField(session,"renderer");return ReflectionHelpers.getField(renderer,"root");}
    /** Use exactly the 008 generation-specific durable installation backend. */
    private InstallationStore store() {return new InstallationStore(new AndroidInstallationBackend(context,"tvfirst008_cache_"+envelope.binding.generation.replace("-","")));}
    /** Only simulated Android permission state is changed, never a device permission. */
    private void grantMedia(boolean grant) {Settings.Secure.putString(context.getContentResolver(),"enabled_notification_listeners",grant?NotificationAccess.component(context).flattenToString():"");}
    /** Assert legacy Cloud, installation and renderer owners remain unconstructed in the new branch. */
    private void assertLegacyInert() {for(String field:new String[]{"mediaSessionProbe","cloudClient","installationStore","packageInstaller","videoRuntimePorts","trackScheduler","commentaryServer","sceneController","renderer","sceneRenderer"})assertNull(field,ReflectionHelpers.getField(service,field));}
    /** Inspect only native visible text, never app preferences or a third-party programme image. */
    private static String text(View view) {if(view==null)return "";StringBuilder value=new StringBuilder(view instanceof TextView?((TextView)view).getText():"");if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)value.append('\n').append(text(((ViewGroup)view).getChildAt(i)));return value.toString();}
    /** Locate the real native navigation button by its resource-localized label. */
    private static View findText(View view,String wanted) {if(view instanceof android.widget.Button&&wanted.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findText(((ViewGroup)view).getChildAt(i),wanted);if(found!=null)return found;}return null;}
    /** Store only synthetic test credentials in memory. */
    private static final class MemorySecrets implements SecretStore {
        private final Map<String,String> values=new HashMap<>();
        /** Persist a synthetic fixture secret only. */ @Override public boolean put(String key,String value){values.put(key,value);return true;}
        /** Read a synthetic fixture secret only. */ @Override public String get(String key){return values.get(key);}
        /** Remove a synthetic fixture secret only. */ @Override public boolean remove(String key){values.remove(key);return true;}
        /** Check a synthetic fixture secret only. */ @Override public boolean contains(String key){return values.containsKey(key);}
    }
    /** Execute the existing source worker synchronously; main-thread callbacks remain real and queued. */
    private static final class Direct extends AbstractExecutorService {
        private boolean closed;
        /** Close only the test executor. */ @Override public void shutdown(){closed=true;}
        /** No unfinished work exists. */ @Override public List<Runnable> shutdownNow(){closed=true;return Collections.emptyList();}
        /** Report local executor lifetime. */ @Override public boolean isShutdown(){return closed;}
        /** Synchronous work is already finished. */ @Override public boolean isTerminated(){return closed;}
        /** No retry or recovery is performed. */ @Override public boolean awaitTermination(long timeout,TimeUnit unit){return closed;}
        /** Execute one local fixture operation. */ @Override public void execute(Runnable operation){if(closed)throw new IllegalStateException("CLOSED");operation.run();}
    }
}
