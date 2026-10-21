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
    private static final String EXTRA="text_qualification_request";
    private static final String DIGEST="4b3208760f8356d2b00aa223f5103f9d3a1ff2d7b2429ce58c1cb414bf2e818c";
    private static final String CHANNEL="scenevibe_text_qualification";
    private static final String TAG="SceneVibeText009";
    private static final int NOTIFICATION=1009;
    private static Pending pending;
    private static long nextOwner,liveOwner;
    private static Status latest=new Status("STOPPED",-1,false,0,-1);
    private final OverlayService service;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final long owner=++nextOwner;
    private TvFirst009TextSession session;
    private boolean foreground,closing;
    private Runnable watch;

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
                case "CAPTURE_TIMEOUT": case "RUNTIME_FAILED": return true;
                default: return false;
            }
        }
    }

    /** A five-second, one-use Activity-to-service handoff, never serialized into private storage. */
    private static final class Pending {
        final String nonce=UUID.randomUUID().toString();
        final long deadline=SystemClock.elapsedRealtime()+5000;
        final TvFirst008Source.ConfirmedCacheReference reference;
        /** Capture only the existing confirmed cache authority from the user's explicit action. */
        Pending(TvFirst008Source.ConfirmedCacheReference reference) { this.reference=reference; }
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

    /** Enter foreground promptly, reusing the historical Android service/notification mechanism. */
    @android.annotation.SuppressLint("ForegroundServiceType")
    void create() {
        try {
            NotificationManager manager=service.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"SceneVibe TEXT — qualification",NotificationManager.IMPORTANCE_LOW));
            PendingIntent open=PendingIntent.getActivity(service,0,new Intent(service,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop=PendingIntent.getService(service,9,new Intent(service,OverlayService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(service,CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("SceneVibe TV — TEXT").setContentText("Qualification sans publicité · arrêt dans SceneVibe TV")
                    .setContentIntent(open).addAction(new Notification.Action.Builder(null,"Arrêter TEXT",stop).build()).setOngoing(true).build();
            service.startForeground(NOTIFICATION,notification);foreground=true;
        } catch (RuntimeException failed) { fail("FOREGROUND_SERVICE_FAILED"); }
    }

    /** Reject null, boot, replacement, legacy, replayed and expired requests; never restore a permit from disk. */
    int start(Intent intent) {
        if (intent!=null && STOP.equals(intent.getAction())) {
            record("STOPPED",-1,false);destroy();service.stopSelf();return Service.START_NOT_STICKY;
        }
        if (!foreground || intent==null || !START.equals(intent.getAction()) || session!=null) {
            fail("EXPLICIT_START_REQUIRED");return Service.START_NOT_STICKY;
        }
        Pending request=pending;pending=null;
        if (request==null || !request.nonce.equals(intent.getStringExtra(EXTRA))
                || SystemClock.elapsedRealtime()>=request.deadline || !request.reference.isCurrent(SystemClock.elapsedRealtime())
                || !DIGEST.equals(request.reference.binding.digest)) {
            fail("START_PROOF_INVALID");return Service.START_NOT_STICKY;
        }
        liveOwner=owner;latest=new Status("WAITING_MEDIA",-1,true,0,-1);
        session=TvFirst009TextSession.openConfirmed(service,request.reference,
                TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION,this::observed);
        if (session==null) { fail("CACHE_OR_PERMISSION_UNAVAILABLE");return Service.START_NOT_STICKY; }
        watch=new Runnable() {
            /** Retire notification/probe after permission loss or self-closing consent/cache authority. */
            @Override public void run() {
                if (closing || liveOwner!=owner || session==null) return;
                if (!Settings.canDrawOverlays(service) || !NotificationAccess.isGranted(service)) { fail("PERMISSION_REQUIRED");return; }
                if (!session.isActive()) { destroy();service.stopSelf();return; }
                main.postDelayed(this,250);
            }
        };
        main.postDelayed(watch,250);
        return Service.START_NOT_STICKY;
    }

    /** Accept only this service lifetime's bounded observation, never an old instance's callback. */
    private void observed(String code,long position,boolean active) {
        if (!closing && liveOwner==owner) record(code,position,active);
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
        if (watch!=null) main.removeCallbacks(watch);
        if (session!=null) { session.close();session=null; }
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
            case "MEDIA_IDENTITY_BLOCKED": explanation="Épisode non reconnu : métadonnées absentes ou différentes. Aucun commentaire.";break;
            case "POSITION_UNKNOWN": explanation="Position MEDIA inconnue. Aucun commentaire.";break;
            case "MEDIA_CLOCK_UNUSABLE": explanation="Horloge MEDIA inutilisable. Aucun commentaire.";break;
            case "CACHE_OR_CONSENT_INVALID": explanation="Consentement ou preuve de cache expiré/invalide. Session fermée.";break;
            case "COLUMBO_CACHE_NOT_READY": explanation="Retrouve Columbo et son cache confirmé dans Mes Tracks. Aucune affectation automatique.";break;
            case "PERMISSION_REQUIRED": explanation="Autorisations de superposition et d’accès MEDIA nécessaires, à accorder manuellement.";break;
            case "MEDIA_PLAYING": explanation="Épisode reconnu par le contrat historique. Lecture MEDIA suivie.";break;
            case "MEDIA_PAUSED": explanation="Lecture en pause : commentaires gelés.";break;
            case "WAITING_MEDIA": case "SESSION_UNAVAILABLE": case "CAPTURE_TIMEOUT": explanation="En attente d’une session MEDIA reconnue. Aucun commentaire sans identité et position.";break;
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

    /** Express monotonic observation age without inventing a wall-clock date or provider timestamp. */
    private static String age(long at,long now) { return at<0 || now<at?"UNKNOWN":"il y a "+(now-at)+" ms"; }
}
