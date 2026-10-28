package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Explicit 009-004 lifetime inside the existing OverlayService, isolated from its
 * historical Cloud/installer/ACK/legacy-renderer paths. A TV button hands off one
 * current 008 cache reference in memory; no persisted switch can restart TEXT.
 * Foreground notification ownership survives Activity pause. Native identity and
 * M5 still decide every comment; this owner has no player transport or new clock.
 */
final class TvFirst009TextServiceOwner {
    static final String START="com.scenevibe.tvcompanionpoc.TEXT_QUALIFICATION_START";
    static final String STOP="com.scenevibe.tvcompanionpoc.TEXT_QUALIFICATION_STOP";
    static final String MULTIMEDIA_START="com.scenevibe.tvcompanionpoc.CLOUD_MULTIMEDIA_START";
    private static final String EXTRA="text_qualification_request";
    private static final String DIGEST="4b3208760f8356d2b00aa223f5103f9d3a1ff2d7b2429ce58c1cb414bf2e818c";
    private static final String CHANNEL="scenevibe_text_qualification";
    private static final String TAG="SceneVibeText009";
    private static final int NOTIFICATION=1009;
    private static Pending pending;
    private static long nextOwner,liveOwner;
    private static TvFirst009TextServiceOwner liveInstance;
    private static boolean multimediaTextPreference=true,multimediaAudioPreference=true;
    private static boolean adSyncPreference;
    private static Status latest=new Status("STOPPED",-1,false,0,-1);
    private final OverlayService service;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final long owner=++nextOwner;
    private TvFirst009TextSession session;
    private CloudMultimediaSession multimedia;
    private boolean foreground,closing;
    private Runnable watch;
    private CompanionControlPanel controlPanel;
    private static Context preferenceContext;
    private LocalDeveloperServer localControls;
    private static final String SHOW_INTERACTIVE="com.scenevibe.tvcompanionpoc.SHOW_INTERACTIVE_CONTROLS";
    private static final DeveloperOverlayState developer=new DeveloperOverlayState();
    private static final String DEV_ENABLE="com.scenevibe.tvcompanionpoc.DEVELOPER_ENABLE";
    private static final String DEV_PREPARE="com.scenevibe.tvcompanionpoc.DEVELOPER_PREPARE";
    static final String SHOW_CONTROLS="com.scenevibe.tvcompanionpoc.SHOW_COMPANION_CONTROLS";

    /** Only sanitized process-local facts; a render request is not physical display evidence. */
    static final class Status {
        final String code;
        final long positionMs,renderRequests,lastRequestedMs;
        final boolean active;
        final String lastIssue;
        final long updatedAtMs,lastIssueAtMs,lastRenderAtMs;
        /** Keep bounded diagnostics without a Track payload, identifier, token or credential. */
        Status(String code,long positionMs,boolean active,long renderRequests,long lastRequestedMs) {
            this(code,positionMs,active,renderRequests,lastRequestedMs,"NONE",
                    SystemClock.elapsedRealtime(),-1,-1);
        }
        /** Separate current state from incident and render history in one immutable observation. */
        private Status(String code,long positionMs,boolean active,long renderRequests,long lastRequestedMs,
                String lastIssue,long updatedAtMs,long lastIssueAtMs,long lastRenderAtMs) {
            this.code=code;this.positionMs=positionMs;this.active=active;
            this.renderRequests=renderRequests;this.lastRequestedMs=lastRequestedMs;
            this.lastIssue=lastIssue;this.updatedAtMs=updatedAtMs;
            this.lastIssueAtMs=lastIssueAtMs;this.lastRenderAtMs=lastRenderAtMs;
        }
        /** Rendering is an event, never a replacement for the current MEDIA or lifetime state. */
        Status event(String event,long position,boolean running,long now) {
            if ("TEXT_RENDER_REQUESTED".equals(event))
                return new Status(code,positionMs,active,Math.min(10000,renderRequests+1),position,
                        lastIssue,updatedAtMs,lastIssueAtMs,now);
            boolean issue=isIssue(event);
            return new Status(event,position,running,renderRequests,lastRequestedMs,
                    issue?event:lastIssue,now,issue?now:lastIssueAtMs,lastRenderAtMs);
        }
        /** Classify only the closed diagnostic vocabulary; waiting, pause and stop are not errors. */
        private static boolean isIssue(String event) {
            switch(event) {
                case "PERMISSION_REQUIRED": case "COLUMBO_CACHE_NOT_READY": case "SERVICE_START_FAILED":
                case "FOREGROUND_SERVICE_FAILED": case "EXPLICIT_START_REQUIRED": case "START_PROOF_INVALID":
                case "CACHE_OR_PERMISSION_UNAVAILABLE": case "MEDIA_IDENTITY_BLOCKED": case "POSITION_UNKNOWN":
                case "MEDIA_CLOCK_UNUSABLE": case "CACHE_OR_CONSENT_INVALID": case "SESSION_UNAVAILABLE":
                case "CAPTURE_TIMEOUT": case "RUNTIME_FAILED": case "MEDIA_UNAVAILABLE": case "MEDIA_CLOCK_INVALID":
                case "MEDIA_IDENTITY_INVALID": case "CONSENT_CLOCK_INVALID": case "CONSENT_EXPIRED":
                case "AUTHORITY_SUPERSEDED": case "IDENTITY_CHANGED": case "REVISION_CHANGED":
                case "CACHE_BINDING_CHANGED": case "CACHE_RECEIPT_INVALID": case "CACHE_METADATA_UNAVAILABLE": return true;
                case "OVERLAY_PERMISSION_REQUIRED": case "MEDIA_PERMISSION_REQUIRED": return true;
                default: return false;
            }
        }
    }

    /** A five-second, one-use Activity-to-service handoff, never serialized into private storage. */
    private static final class Pending {
        final String nonce=UUID.randomUUID().toString();
        final long deadline=SystemClock.elapsedRealtime()+5000;
        final TvFirst008Source.ConfirmedCacheReference reference;
        final CloudMultimediaAdmission multimedia;
        /** Capture only the existing confirmed cache authority from the user's explicit action. */
        Pending(TvFirst008Source.ConfirmedCacheReference reference) { this.reference=reference;this.multimedia=null; }
        /** Carry a current explicit multimedia permit in memory for the same five-second handoff. */
        Pending(CloudMultimediaAdmission multimedia) {this.reference=null;this.multimedia=multimedia;}
    }

    /** Bind the existing service; construction alone creates no probe, cache or renderer. */
    TvFirst009TextServiceOwner(OverlayService service) { this.service=service; }

    /** Return process-local diagnostics without starting work or reading application secrets. */
    static Status status() { return latest; }

    /** Report only a current one-use handoff or service lifetime, without starting or restoring it. */
    static boolean controlBusy() {
        return liveOwner!=0 || pending!=null && SystemClock.elapsedRealtime()<pending.deadline;
    }

    /** Explicit TV button only: check permissions/readiness before requesting the existing foreground service. */
    static boolean requestStart(Activity activity,TvFirst008Source source) {
        if (!BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION || Looper.myLooper()!=Looper.getMainLooper()) return false;
        if (pending!=null && SystemClock.elapsedRealtime()>=pending.deadline) pending=null;
        if (liveOwner!=0 || pending!=null) return false;
        if (!Settings.canDrawOverlays(activity) || !NotificationAccess.isGranted(activity)) {
            record("PERMISSION_REQUIRED",-1,false);return false;
        }
        TvFirst008Source.ConfirmedCacheReference reference=source==null?null:source.confirmedCacheReference();
        if (reference==null || !DIGEST.equals(reference.binding.digest)) {
            record("COLUMBO_CACHE_NOT_READY",-1,false);return false;
        }
        Pending request=new Pending(reference);pending=request;
        record("START_REQUESTED",-1,false);
        try {
            activity.startForegroundService(new Intent(activity,OverlayService.class).setAction(START).putExtra(EXTRA,request.nonce));
            return true;
        } catch (RuntimeException refused) {
            pending=null;record("SERVICE_START_FAILED",-1,false);return false;
        }
    }

    /** Stop only this app's existing service; never clear credentials, cache or a Cloud association. */
    static void requestStop(Context context) {
        if (!BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION) return;
        pending=null;
        record("STOP_REQUESTED",latest.positionMs,false);
        context.stopService(new Intent(context,OverlayService.class));
        if (liveOwner==0) record("STOPPED",-1,false);
    }

    /** Explicit local confirmation only; cache restoration and receipt callbacks never call this method. */
    static boolean requestMultimediaStart(Activity activity,TvFirst008Source source,MultimediaPrivateCache.Prepared prepared,
            boolean ownerConfirmedReferenceEdition) {
        if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || Looper.myLooper()!=Looper.getMainLooper() || controlBusy())return false;
        if(!Settings.canDrawOverlays(activity) || !NotificationAccess.isGranted(activity)){
            record("PERMISSION_REQUIRED",-1,false);return false;
        }
        TvFirst008Source.MultimediaReference reference=source==null?null:source.confirmedMultimediaReference(prepared);
        if(reference==null){record("CACHE_OR_CONSENT_REQUIRED",-1,false);return false;}
        CloudMultimediaAdmission admitted=CloudMultimediaAdmission.admit(reference,ownerConfirmedReferenceEdition);
        if(admitted==null){record("MEDIA_IDENTITY_BLOCKED",-1,false);return false;}
        Pending request=new Pending(admitted);pending=request;record("START_REQUESTED",-1,false);
        try{activity.startForegroundService(new Intent(activity,OverlayService.class).setAction(MULTIMEDIA_START).putExtra(EXTRA,request.nonce));return true;}
        catch(RuntimeException refused){pending=null;record("SERVICE_START_FAILED",-1,false);return false;}
    }

    /** Read the same requested switches from either surface; opening a view never resets them. */
    static boolean multimediaTextEnabled(){return multimediaTextPreference;}
    /** AUDIO state belongs to the service owner, not a second Settings/panel preference. */
    static boolean multimediaAudioEnabled(){return multimediaAudioPreference;}

    /** Reuse a system-owned PendingIntent to the non-exported service, without launching MainActivity. */
    static PendingIntent panelIntent(Context context) {
        return PendingIntent.getService(context,190,new Intent(context,OverlayService.class).setAction(BuildConfig.ADSYNC_NONDESTRUCTIVE?SHOW_INTERACTIVE:SHOW_CONTROLS),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    /** Explicit application preview only; cross-app invocation uses the notification's same service capability. */
    static boolean requestControlPanel(Context context) {
        if(!BuildConfig.TVUX_CONTROL_PANEL || !Settings.canDrawOverlays(context))return false;
        try{context.startForegroundService(new Intent(context,OverlayService.class).setAction(SHOW_CONTROLS));return true;}
        catch(RuntimeException refused){return false;}
    }

    /** Mode activation never arms a track or opens a focusable window. */
    static boolean setDeveloperMode(Context context,boolean enabled) {
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY)return false;
        if(!enabled){setLocalControlEnabled(false);if(liveInstance!=null && liveInstance.controlPanel!=null)liveInstance.controlPanel.close();developer.disable();return true;}
        developer.enable();
        try{context.startForegroundService(new Intent(context,OverlayService.class).setAction(DEV_ENABLE));return true;}
        catch(RuntimeException refused){developer.disable();return false;}
    }

    /** Read the one service-owned mode, distinct from visible-window and media activation states. */
    static DeveloperOverlayState.Stage developerStage(){developer.expire(SystemClock.elapsedRealtime());return developer.stage();}
    /** Attach only a passive window; the normal application must retain D-pad focus. */
    static boolean prepareDeveloperPanel(Context context) {
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY || !developer.enabled() || !Settings.canDrawOverlays(context))return false;
        try{context.startForegroundService(new Intent(context,OverlayService.class).setAction(DEV_PREPARE));return true;}
        catch(RuntimeException refused){return false;}
    }
    /** Issue a one-use handoff only for a genuinely attached prepared window. */
    static String beginDeveloperHandoff() {
        return beginDeveloperHandoff(false);
    }
    /** Opt in to input focus only for the explicitly labelled experiment, not the normal viewer launch. */
    static String beginDeveloperHandoff(boolean interactive) {
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY || liveInstance==null || liveInstance.controlPanel==null
                || !liveInstance.controlPanel.isPrepared())return null;
        return developer.begin(SystemClock.elapsedRealtime(),interactive);
    }
    /** A failed normal Activity launch never causes delayed focus acquisition. */
    static void cancelDeveloperHandoff(String ticket){developer.cancel(ticket);}
    /** The source Activity calls this only from onStop after a successful explicit launch. */
    static boolean completeDeveloperHandoff(String ticket) {
        long now=SystemClock.elapsedRealtime();
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY || !developer.accepts(ticket,now) || liveInstance==null || liveInstance.controlPanel==null)return false;
        if(liveInstance.multimedia!=null && liveInstance.multimedia.isActive()){
            if(!developer.waitForPlayer(ticket,now))return false;
            liveInstance.showDeveloperWhenMediaReady();return true;
        }
        if(!liveInstance.controlPanel.presentPrepared(developer.focusRequested())){developer.closed();return false;}
        return developer.commit(ticket,now);
    }
    /** Returning to the normal application parks its service window instead of obstructing the Activity. */
    static void parkDeveloperPanel() {
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY && liveInstance!=null && liveInstance.controlPanel!=null && liveInstance.controlPanel.isOpen()){
            liveInstance.controlPanel.standby();if(developer.enabled() && liveInstance.controlPanel.isPrepared())developer.prepared();
        }
    }
    /** Closing does not disable the mode or change any commentary preference. */
    static void closeDeveloperPanel(){if(liveInstance!=null && liveInstance.controlPanel!=null)liveInstance.controlPanel.close();}
    /** Expose actual OS focus separately from the requested interactive stage. */
    static boolean developerHasFocus(){return liveInstance!=null && liveInstance.controlPanel!=null && liveInstance.controlPanel.hasInputFocus();}
    /** Read only the current service window identity, with no capability to a platform player. */
    static Object developerWindowIdentity(){return liveInstance==null || liveInstance.controlPanel==null?null:liveInstance.controlPanel.windowIdentity();}
    /** Give actionable UI status without treating mode activation as multimedia or display proof. */
    static String developerStatus() {
        switch(developerStage()) {
            case OFF:return "Mode Développeur désactivé.";
            case ENABLED:return "Mode actif · panneau fermé. Reviens ici pour le rouvrir.";
            case PREPARED:return "Panneau préparé sans focus. Ouvre Prime avec le bouton ci-dessous.";
            case HANDOFF:return "Ouverture du lecteur demandée · commandes encore non interactives.";
            case WAITING_PLAYER:return "Touches libres · lance le film dans Prime. Attente du média compatible en lecture.";
            case MONITORING:return "Affichage passif · touches au lecteur. Commandes dans l’onglet Développeur.";
            case INTERACTIVE:return "Commandes : "+developerFocusStatus()+" · Back ou × ferme uniquement SceneVibe.";
            default:return "État Développeur indisponible.";
        }
    }
    /** Read observed window ownership, not the requested Developer UI stage. */
    static String developerFocusStatus(){return liveInstance==null || liveInstance.controlPanel==null?DeveloperFocusState.State.CLOSED.name():liveInstance.controlPanel.focusState().name();}
    /** Read only events delivered to the existing service control root, not keys owned by another application. */
    static int developerReceivedKeys(){return liveInstance==null || liveInstance.controlPanel==null?0:liveInstance.controlPanel.receivedKeys();}
    /** Explicit TV opt-in only; this local listener grants no Cloud, MEDIA or playback admission. */
    static boolean setLocalControlEnabled(boolean enabled){
        if(!BuildConfig.ADSYNC_NONDESTRUCTIVE || liveInstance==null)return false;
        TvFirst009TextServiceOwner owner=liveInstance;
        if(owner.localControls!=null){owner.localControls.close();owner.localControls=null;}
        if(!enabled)return true;if(!developer.enabled() || !owner.foreground)return false;
        try{owner.localControls=new LocalDeveloperServer(owner::localCommand,filename->{
            try(java.io.InputStream input=owner.service.getAssets().open("local-controls/"+filename);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;){output.write(buffer,0,n);if(output.size()>65536)throw new java.io.IOException("PAGE_BUDGET");}return output.toByteArray();}
        });owner.localControls.start();return true;}catch(Exception refused){if(owner.localControls!=null)owner.localControls.close();owner.localControls=null;return false;}
    }
    /** Show private pairing instructions only in SceneVibe's own foreground interface, never in logs. */
    static String localControlInfo(){return liveInstance==null || liveInstance.localControls==null?"Commandes locales arrêtées.":liveInstance.localControls.accessInfo();}
    /** Read listener lifetime independently of service, playback and panel focus. */
    static boolean localControlEnabled(){return liveInstance!=null && liveInstance.localControls!=null && liveInstance.localControls.isLive();}
    /** Transfer a bounded local request to the same main-thread command owner, with no delayed action after timeout. */
    private JSONObject localCommand(String command,String value) throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicBoolean pending=new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<JSONObject> answer=new java.util.concurrent.atomic.AtomicReference<>();
        LocalDeveloperServer origin=localControls;
        main.post(()->{if(!pending.compareAndSet(true,false)){done.countDown();return;}
            try{if(closing || localControls!=origin || origin==null || !origin.isLive())return;answer.set(localCommandNow(command,value));}
            catch(Exception refused){try{answer.set(localStatus().put("result","Commande refusée ; état SceneVibe conservé."));}catch(Exception unavailable){/* No fictional OFF snapshot is returned. */}}finally{done.countDown();}});
        if(!done.await(2,java.util.concurrent.TimeUnit.SECONDS)){pending.set(false);throw new IllegalStateException("LOCAL_COMMAND_TIMEOUT");}
        if(answer.get()==null)throw new IllegalStateException("LOCAL_COMMAND_REFUSED");return answer.get();
    }
    /** A closed allowlist controls only SceneVibe preferences/effects and passive window visibility. */
    private JSONObject localCommandNow(String command,String value) throws Exception {
        CompanionControlPanel.Commands controls=panelCommands();
        String feedback=null;
        switch(command){
            case "status":break;
            case "text":case "audio":case "adsync":
                if(!"true".equals(value) && !"false".equals(value))throw new IllegalArgumentException("BOOLEAN_REQUIRED");
                boolean on=Boolean.parseBoolean(value);if("text".equals(command))controls.text(on);else if("audio".equals(command))controls.audio(on);else controls.adSync(on);
                feedback=command.toUpperCase(java.util.Locale.ROOT)+" : "+(on?"ON":"OFF")+" · préférence enregistrée, calendrier conservé.";break;
            case "suspend":controls.suspend();feedback=multimediaActive()?"Pause volontaire demandée ; préférences conservées.":"Service arrêté ; aucune session suspendue.";break;
            case "resume":controls.resume();feedback=multimediaActive()?"Reprise demandée ; les captures MEDIA compatibles restent nécessaires.":"Service arrêté ; aucune admission créée.";break;
            case "replay":return localStatus().put("result",multimediaActive()?multimedia.confirmReplay():"Service arrêté ; relecture indisponible.");
            case "calibrate":try{AdSyncController.Calibration calibrated=controls.calibrate(AdSyncController.parseFilmTime(value),false);return localStatus().put("result",calibrated.message);}
                catch(IllegalArgumentException invalid){return localStatus().put("result",AdSyncController.Calibration.FORMAT_INVALID.message);}
            case "panel":
                if("close".equals(value))closeDeveloperPanel();
                else if("show".equals(value)){ensureControlPanel();if(controlPanel.prepare()){developer.prepared();if(controlPanel.presentPrepared(false))developer.shown();}}
                else throw new IllegalArgumentException("PASSIVE_PANEL_ACTION_REQUIRED");break;
            default:throw new IllegalArgumentException("LOCAL_COMMAND_REFUSED");
        }
        JSONObject result=localStatus();return feedback==null?result:result.put("result",feedback);
    }
    /** Return only numeric/state projections, never programme content, identity, code or credential. */
    private JSONObject localStatus() throws Exception {
        return new JSONObject().put("text",multimediaTextEnabled()).put("audio",multimediaAudioEnabled()).put("adsync",adSyncEnabled())
                .put("active",multimediaActive()).put("suspended",multimediaActive() && multimedia.manuallySuspended())
                .put("commentState",multimediaActive()?multimedia.commentState():"SERVICE_STOPPED")
                .put("focus",developerFocusStatus()).put("sync",adSyncStatus()).put("runtime",multimediaAvailability()).put("access","PHONE_LOCAL_NOT_SONY_DPAD");
    }
    /** Restore only requested channel preferences; no cache, consent, mode or playback permit is persisted here. */
    static void initMultimediaPreferences(Context context){
        if(!BuildConfig.ADSYNC_NONDESTRUCTIVE || preferenceContext!=null)return;
        preferenceContext=context.getApplicationContext();android.content.SharedPreferences prefs=preferenceContext.getSharedPreferences("multimedia_user_preferences",0);
        multimediaTextPreference=prefs.getBoolean("text",true);multimediaAudioPreference=prefs.getBoolean("audio",true);adSyncPreference=prefs.getBoolean("adsync",false);
    }
    /** Persist a user preference without extending consent or logging a value from private content. */
    private static void savePreference(String key,boolean value){if(BuildConfig.ADSYNC_NONDESTRUCTIVE && preferenceContext!=null
            && !preferenceContext.getSharedPreferences("multimedia_user_preferences",0).edit().putBoolean(key,value).commit())Log.i("SceneVibeCache","event=PREFERENCE_WRITE_FAILED");}
    /** Use only the existing admitted public MEDIA observation, never a foreground-app spy or timer. */
    private void showDeveloperWhenMediaReady() {
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY || !developer.awaitingPlayer(SystemClock.elapsedRealtime())
                || multimedia==null || !multimedia.developerMediaReady(developer.playerWaitStarted()) || controlPanel==null)return;
        if(controlPanel.presentPrepared(developer.focusRequested()))developer.shown();else developer.closed();
    }
    /** Retain one window component inside this existing service, with UI-only lifecycle callbacks. */
    private void ensureControlPanel() {
        if(controlPanel==null)controlPanel=new CompanionControlPanel(service,panelCommands());
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY)controlPanel.onLifecycle(developer::closed,developer::prepared);
    }

    /** Project the current multimedia owner; no cache or playback permit is created by these controls. */
    static CompanionControlPanel.Commands panelCommands() {
        return new CompanionControlPanel.Commands() {
            /** Read actual service state each time, including changes made by the application. */
            @Override public CompanionControlPanel.Snapshot snapshot(){return new CompanionControlPanel.Snapshot(multimediaActive(),
                    multimediaTextEnabled(),multimediaAudioEnabled(),adSyncEnabled(),BuildConfig.ADSYNC_PROTOTYPE,adSyncStatus()+(BuildConfig.ADSYNC_NONDESTRUCTIVE?" · accès "+developerFocusStatus():""),
                    liveInstance==null || liveInstance.multimedia==null?"Aucune capture MEDIA active.":liveInstance.multimedia.temporalDiagnostics());}
            /** Route TEXT through the one existing owner. */
            @Override public void text(boolean enabled){setMultimediaTextEnabled(enabled);}
            /** Route AUDIO through the one existing owner. */
            @Override public void audio(boolean enabled){setMultimediaAudioEnabled(enabled);}
            /** Route AD-SYNC through the same guard, without activating comments. */
            @Override public void adSync(boolean enabled){setAdSyncEnabled(enabled);}
            /** This explicit action also opts into the local interruption guard. */
            @Override public void suspend(){if(multimediaActive()){if(!BuildConfig.ADSYNC_NONDESTRUCTIVE && !adSyncEnabled())setAdSyncEnabled(true);suspendMultimediaComments();}}
            /** Preserve an already-enabled guard rather than clearing its offset or consumed identities. */
            @Override public void resume(){if(multimediaActive()){if(!BuildConfig.ADSYNC_NONDESTRUCTIVE && !adSyncEnabled())setAdSyncEnabled(true);resumeMultimediaComments();}}
            /** Refresh and diagnose using the actual live service, without a new admission. */
            @Override public AdSyncController.Calibration calibrate(long time,boolean seek){return calibrateMultimediaResult(time,seek);}
        };
    }

    /** Explain absent authority separately from an invalid/freshness/offset MEDIA refusal. */
    static AdSyncController.Calibration calibrateMultimediaResult(long time,boolean seek) {
        if(!multimediaActive())return AdSyncController.Calibration.COMPANION_INACTIVE;
        if(BuildConfig.TVUX_CONTROL_PANEL && !BuildConfig.ADSYNC_NONDESTRUCTIVE && !adSyncEnabled())setAdSyncEnabled(true);
        return liveInstance.multimedia.calibrateCommentsResult(time,seek);
    }

    /** Route a visual preference only to the current multimedia lifetime. */
    static void setMultimediaTextEnabled(boolean enabled){multimediaTextPreference=enabled;savePreference("text",enabled);if(liveInstance!=null && liveInstance.multimedia!=null)liveInstance.multimedia.setTextEnabled(enabled);}
    /** Route an audio preference only to SceneVibe's current local MP3 port. */
    static void setMultimediaAudioEnabled(boolean enabled){multimediaAudioPreference=enabled;savePreference("audio",enabled);if(liveInstance!=null && liveInstance.multimedia!=null)liveInstance.multimedia.setAudioEnabled(enabled);}
    /** Expose actual distinct native STARTED resources as bounded process-local diagnostics. */
    static int multimediaAudioStarted(){return liveInstance==null || liveInstance.multimedia==null?0:liveInstance.multimedia.audioResourcesStarted();}
    /** Expose original TEXT windows requested by the real service, without claiming physical display. */
    static int multimediaTextRequested(){return liveInstance==null || liveInstance.multimedia==null?0:liveInstance.multimedia.textWindowsRequested();}
    /** Report native preparation count for diagnostics; a resumed MP3 should not be prepared again. */
    static int multimediaAudioPreparations(){return liveInstance==null || liveInstance.multimedia==null?0:liveInstance.multimedia.audioPreparations();}
    /** Actual native STARTED count includes separate qualified replays of the same logical resource. */
    static int multimediaAudioPlaybackStarts(){return liveInstance==null || liveInstance.multimedia==null?0:liveInstance.multimedia.audioPlaybackStarts();}
    /** Current TEXT attachment is distinct from the historical count of original scenes observed. */
    static boolean multimediaTextAttached(){return liveInstance!=null && liveInstance.multimedia!=null && liveInstance.multimedia.textWindowAttached();}
    /** Pure diagnostic projection of the one service-owned calendar. */
    static com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler.Diagnostics multimediaCalendarDiagnostics(){return liveInstance==null || liveInstance.multimedia==null?null:liveInstance.multimedia.calendarDiagnostics();}
    /** Native outcome by public AUDIO ordinal, never by a sensitive resource identifier. */
    static com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler.AudioDiagnostics multimediaAudioDiagnostics(int ordinal){return liveInstance==null || liveInstance.multimedia==null?null:liveInstance.multimedia.audioDiagnostics(ordinal);}
    /** Report only the local comment-player lifecycle, never the platform player's transport object. */
    static String multimediaAudioState(){return liveInstance==null || liveInstance.multimedia==null?"IDLE":liveInstance.multimedia.audioState();}

    /** Return whether a multimedia service already owns a current admitted lifetime. */
    static boolean multimediaActive(){return liveInstance!=null && liveInstance.multimedia!=null && liveInstance.multimedia.isActive();}
    /** Read only the explicit process-local AD-SYNC opt-in; it never arms a service. */
    static boolean adSyncEnabled(){return BuildConfig.ADSYNC_PROTOTYPE && adSyncPreference;}
    /** Route a local guard toggle through the same current multimedia service owner. */
    static void setAdSyncEnabled(boolean enabled){adSyncPreference=BuildConfig.ADSYNC_PROTOTYPE && enabled;
        savePreference("adsync",adSyncPreference);
        if(liveInstance!=null && liveInstance.multimedia!=null)liveInstance.multimedia.setAdSyncEnabled(adSyncPreference);}
    /** Suspend only existing admitted SceneVibe effects; absent service means a harmless no-op. */
    static void suspendMultimediaComments(){if(liveInstance!=null && liveInstance.multimedia!=null)liveInstance.multimedia.suspendComments();}
    /** Resume remains conditional on fresh public MEDIA facts, never a newly granted activation. */
    static void resumeMultimediaComments(){if(liveInstance!=null && liveInstance.multimedia!=null)liveInstance.multimedia.resumeComments();}
    /** Forward a user-confirmed film position without creating a Cloud or service capability. */
    static boolean calibrateMultimediaComments(long contentMs){return calibrateMultimediaComments(contentMs,false);}
    /** Only an explicit local SEEK confirmation may request rearming of consumed comments. */
    static boolean calibrateMultimediaComments(long contentMs,boolean confirmedSeek){return calibrateMultimediaResult(contentMs,confirmedSeek)==AdSyncController.Calibration.APPLIED;}
    /** Read only finite diagnostics from the current service lifetime. */
    static String adSyncStatus(){return liveInstance==null || liveInstance.multimedia==null?"Service inactif · aucune détection publicitaire":liveInstance.multimedia.adSyncStatus();}

    /** Enter foreground promptly, reusing the historical Android service/notification mechanism. */
    @android.annotation.SuppressLint("ForegroundServiceType")
    void create() {
        initMultimediaPreferences(service);
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY)liveInstance=this;
        try {
            NotificationManager manager=service.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"SceneVibe TEXT — qualification",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(service,0,new Intent(service,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(service,9,new Intent(service,OverlayService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=new Notification.Builder(service,CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(BuildConfig.CLOUD_MULTIMEDIA_SERVICE?"SceneVibe TV — TEXT + AUDIO":"SceneVibe TV — TEXT").setContentText("Qualification sans publicité · arrêt dans SceneVibe TV")
                    .setContentIntent(open).addAction(new Notification.Action.Builder(null,"Arrêter TEXT",stop).build()).setOngoing(true);
            if(BuildConfig.ADSYNC_PROTOTYPE) {
                PendingIntent suspend=PendingIntent.getService(service,18,new Intent(service,OverlayService.class).setAction("SCENEVIBE_COMMENT_SUSPEND"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                PendingIntent resume=PendingIntent.getService(service,19,new Intent(service,OverlayService.class).setAction("SCENEVIBE_COMMENT_RESUME"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(new Notification.Action.Builder(null,"Suspendre commentaires",suspend).build())
                    .addAction(new Notification.Action.Builder(null,"Reprendre commentaires",resume).build());
            }
            Notification notification=builder.build();
            if(BuildConfig.TVUX_CONTROL_PANEL) {
                PendingIntent panel=panelIntent(service);
                notification=CompanionNotification.create(service,CHANNEL,panel,stop);
            }
            service.startForeground(NOTIFICATION,notification);foreground=true;
        } catch (RuntimeException failed) { fail("FOREGROUND_SERVICE_FAILED"); }
    }

    /** Reject null, boot, replacement, legacy, replayed and expired requests; never restore a permit from disk. */
    int start(Intent intent) {
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && intent!=null && SHOW_INTERACTIVE.equals(intent.getAction())){
            if(foreground && developer.enabled() && Settings.canDrawOverlays(service)){
                ensureControlPanel();if(controlPanel.prepare()){developer.prepared();String ticket=developer.begin(SystemClock.elapsedRealtime(),true);
                    if(ticket!=null && controlPanel.activatePrepared())developer.commit(ticket,SystemClock.elapsedRealtime());}
            }
            return Service.START_NOT_STICKY;
        }
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY && intent!=null && (DEV_ENABLE.equals(intent.getAction()) || DEV_PREPARE.equals(intent.getAction()))) {
            if(foreground && developer.enabled() && DEV_PREPARE.equals(intent.getAction())) {
                ensureControlPanel();if(controlPanel.prepare())developer.prepared();
            }
            return Service.START_NOT_STICKY;
        }
        if(BuildConfig.TVUX_CONTROL_PANEL && intent!=null && SHOW_CONTROLS.equals(intent.getAction())) {
            if(foreground && Settings.canDrawOverlays(service)) {
                // This UI-only service lifetime grants no multimedia admission.
                if(BuildConfig.TVUX_DEVELOPER_OVERLAY && !developer.enabled())return Service.START_NOT_STICKY;
                ensureControlPanel();
                if(BuildConfig.TVUX_DEVELOPER_OVERLAY){
                    if(controlPanel.prepare()){developer.prepared();if(controlPanel.presentPrepared(false))developer.shown();}
                }else controlPanel.open();
            }
            return Service.START_NOT_STICKY;
        }
        if (intent!=null && STOP.equals(intent.getAction())) {
            record("STOPPED",-1,false);destroy();service.stopSelf();return Service.START_NOT_STICKY;
        }
        if(BuildConfig.ADSYNC_PROTOTYPE && intent!=null && ("SCENEVIBE_COMMENT_SUSPEND".equals(intent.getAction())
                || "SCENEVIBE_COMMENT_RESUME".equals(intent.getAction()))) {
            if(multimedia!=null && liveOwner==owner) {
                if("SCENEVIBE_COMMENT_SUSPEND".equals(intent.getAction()))multimedia.suspendComments();
                else multimedia.resumeComments();
            } else {destroy();service.stopSelf();}
            return Service.START_NOT_STICKY;
        }
        boolean multimediaRequest=BuildConfig.CLOUD_MULTIMEDIA_SERVICE && intent!=null && MULTIMEDIA_START.equals(intent.getAction());
        if (!foreground || intent==null || (!START.equals(intent.getAction()) && !multimediaRequest) || session!=null || multimedia!=null) {
            fail("EXPLICIT_START_REQUIRED");return Service.START_NOT_STICKY;
        }
        Pending request=pending;pending=null;
        if (request==null || !request.nonce.equals(intent.getStringExtra(EXTRA))
                || SystemClock.elapsedRealtime()>=request.deadline
                || (multimediaRequest?(request.multimedia==null || !request.multimedia.current()):
                    (request.reference==null || !request.reference.isCurrent(SystemClock.elapsedRealtime()) || !DIGEST.equals(request.reference.binding.digest)))) {
            fail("START_PROOF_INVALID");return Service.START_NOT_STICKY;
        }
        liveOwner=owner;liveInstance=this;latest=new Status("WAITING_MEDIA",-1,true,0,-1);
        if(multimediaRequest)multimedia=CloudMultimediaSession.open(service,request.multimedia,this::observed,
                multimediaTextPreference,multimediaAudioPreference);
        else session=TvFirst009TextSession.openConfirmed(service,request.reference,
                    TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION,this::observed);
        if (session==null && multimedia==null) { fail("CACHE_OR_PERMISSION_UNAVAILABLE");return Service.START_NOT_STICKY; }
        watch=new Runnable() {
            /** Retire notification/probe after permission loss or self-closing consent/cache authority. */
            @Override public void run() {
                if (closing || liveOwner!=owner || (session==null && multimedia==null)) return;
                if ((!BuildConfig.ADSYNC_NONDESTRUCTIVE || multimedia==null)
                        && (!Settings.canDrawOverlays(service) || !NotificationAccess.isGranted(service))) { fail("PERMISSION_REQUIRED");return; }
                if (!(session!=null?session.isActive():multimedia.isActive())) { destroy();service.stopSelf();return; }
                main.postDelayed(this,250);
            }
        };
        main.postDelayed(watch,250);
        return Service.START_NOT_STICKY;
    }

    /** Accept only this service lifetime's bounded observation, never an old instance's callback. */
    private void observed(String code,long position,boolean active) {
        if (!closing && liveOwner==owner){record(code,position,active);showDeveloperWhenMediaReady();}
    }

    /** Keep errors actionable without logging an identity, payload, credential or exception. */
    private static void record(String code,long position,boolean active) {
        Status next=latest.event(code,position,active,SystemClock.elapsedRealtime());
        if (!code.equals(latest.code) || "TEXT_RENDER_REQUESTED".equals(code))
            Log.i(TAG,"code="+code+" positionMs="+position+" renderRequests="+next.renderRequests);
        latest=next;
    }

    /** A failed explicit request has no fallback to historical boot, Cloud, installation or renderer owners. */
    private void fail(String code) {
        pending=null;
        if (liveOwner==0 || liveOwner==owner) record(code,-1,false);
        destroy();service.stopSelf();
    }

    /** Revoke callback ownership before releasing the probe, views, timers and foreground notification. */
    void destroy() {
        if (closing) return;
        closing=true;
        if(localControls!=null){localControls.close();localControls=null;}
        if(controlPanel!=null){controlPanel.close();controlPanel=null;}
        if (watch!=null) main.removeCallbacks(watch);
        if (session!=null) { session.close();session=null; }
        if (multimedia!=null) { multimedia.close();multimedia=null; }
        if (liveInstance==this){liveInstance=null;if(BuildConfig.TVUX_DEVELOPER_OVERLAY)developer.disable();}
        if (liveOwner==owner) {
            if (latest.active) record("STOPPED",-1,false);
            liveOwner=0;
        }
        foreground=false;service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
    }

    /** Give TV users a clear refusal while keeping raw programme/identity values out of the UI. */
    static String describe() {
        Status current=latest;
        String explanation;
        switch(current.code) {
            case "CACHE_OR_CONSENT_REQUIRED": explanation="Vérifie le compte et la révision du cache avant une nouvelle admission.";break;
            case "MEDIA_IDENTITY_INVALID": case "MEDIA_IDENTITY_BLOCKED": explanation="Épisode non reconnu : métadonnées absentes ou différentes. Aucun commentaire.";break;
            case "POSITION_UNKNOWN": explanation="Position MEDIA inconnue. Aucun commentaire.";break;
            case "MEDIA_CLOCK_INVALID": case "MEDIA_CLOCK_UNUSABLE": explanation="Horloge MEDIA inutilisable. Aucun commentaire.";break;
            case "CONSENT_EXPIRED": explanation="Consentement réellement expiré. Cache conservé ; nouvelle vérification et confirmation nécessaires.";break;
            case "CONSENT_CLOCK_INVALID": case "AUTHORITY_SUPERSEDED": case "IDENTITY_CHANGED": case "REVISION_CHANGED":
            case "CACHE_BINDING_CHANGED": case "CACHE_RECEIPT_INVALID": case "CACHE_METADATA_UNAVAILABLE":
                explanation="Autorité de restitution invalidée : "+current.code+". Fichiers conservés ; vérifie l’association puis confirme la restitution.";break;
            case "USER_SUSPENDED": explanation="Commentaires suspendus volontairement. Reprendre conserve la track et attend deux captures MEDIA compatibles.";break;
            case "CONFIRMATION_REQUIRED": explanation="Confirmation de deux captures MEDIA compatibles en cours. Aucune publicité établie.";break;
            case "CACHE_OR_CONSENT_INVALID": explanation="Consentement ou preuve de cache expiré/invalide. Session fermée.";break;
            case "COLUMBO_CACHE_NOT_READY": explanation="Retrouve Columbo et son cache confirmé dans Mes Tracks. Aucune affectation automatique.";break;
            case "PERMISSION_REQUIRED": explanation="Autorisations de superposition et d’accès MEDIA nécessaires, à accorder manuellement.";break;
            case "OVERLAY_PERMISSION_REQUIRED": explanation="Superposition refusée par Android. Effets bloqués ; admission et cache conservés tant qu’ils restent valides.";break;
            case "MEDIA_PERMISSION_REQUIRED": explanation="Accès MEDIA refusé par Android. Effets bloqués ; admission et cache conservés tant qu’ils restent valides.";break;
            case "MEDIA_PLAYING": explanation="Épisode reconnu par le contrat historique. Lecture MEDIA suivie.";break;
            case "MEDIA_PAUSED": explanation="Lecture en pause : commentaires gelés.";break;
            case "MEDIA_UNAVAILABLE": case "SESSION_CHANGED": case "CLOCK_JUMP": case "FRESH_POSITION_FROZEN": case "OBSERVATION_STALE": case "WAITING_MEDIA": case "SESSION_UNAVAILABLE": case "CAPTURE_TIMEOUT": explanation="En attente d’une session MEDIA reconnue. Aucun commentaire sans identité et position.";break;
            case "START_REQUESTED": explanation="Démarrage TEXT demandé volontairement.";break;
            case "STOPPED": case "STOP_REQUESTED": explanation="Restitution TEXT arrêtée.";break;
            default: explanation="Qualification TEXT indisponible ; consulte le code diagnostic. Aucun secours automatique.";
        }
        long now=SystemClock.elapsedRealtime();
        return explanation+"\nÉtat courant : "+current.code+" · observé "+age(current.updatedAtMs,now)
                +"\nTEXT : "+(current.active?"ON — session armée (sans garantie d’affichage)":controlBusy()?"transition demandée":"OFF")
                +"\nDernier incident mémorisé : "+current.lastIssue+(current.lastIssueAtMs<0?"":" · "+age(current.lastIssueAtMs,now))
                +"\nDernier événement de rendu : "+(current.lastRenderAtMs<0?"NONE":"TEXT_RENDER_REQUESTED · "+age(current.lastRenderAtMs,now))
                +"\nRequêtes de rendu — session courante ou dernière session : "+current.renderRequests
                +"\nPosition de la dernière demande (ms) : "+current.lastRequestedMs
                +"\nUne demande de rendu ne prouve pas l’affichage. Historique en mémoire, remis à zéro à la prochaine session.";
    }

    /** Service existence is not evidence of a currently compatible film or an event due now. */
    static String multimediaAvailability(){if(liveInstance!=null && liveInstance.multimedia!=null)return liveInstance.multimedia.availability();
        return "CONSENT_EXPIRED".equals(latest.code)?"Autorisation expirée · cache conservé":"Service arrêté · "+latest.code;}

    /** Express monotonic observation age without inventing a wall-clock date or provider timestamp. */
    private static String age(long at,long now) { return at<0 || now<at?"UNKNOWN":"il y a "+(now-at)+" ms"; }
}
