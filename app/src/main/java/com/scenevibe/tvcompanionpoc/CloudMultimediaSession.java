package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.util.HashSet;
import java.util.Set;

/**
 * Session multimédia du service OverlayService existant. Une seule sonde passive
 * fournit la position au calendrier M5 déjà utilisé par TEXT et AUDIO. Les MP3
 * proviennent uniquement du cache vérifié. Aucun transport vidéo, focus audio,
 * renouvellement réseau ou permis persistant n'est créé par cette composition.
 */
final class CloudMultimediaSession {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private final CloudMultimediaAdmission admission;
    private final TvFirst009TextRuntime.Observer observer;
    private final SceneRenderer renderer;
    private final AndroidLocalAudioPort audio;
    private final StudioMultimediaRuntime runtime;
    private final MediaSessionProbe probe;
    private final Set<String> shown=new HashSet<>(),started=new HashSet<>();
    private final Runnable watchdog=this::watch;
    private final AdSyncController adSync=new AdSyncController();
    private AdSyncController.Sample lastAdSample;
    private long lastAdLog=-1;
    private String lastAdState="";
    private boolean active=true;
    private long lastCapture=-1,position=-1;
    private String preparingResource;
    private String pendingScene;
    private int audioPreparations;

    /** Compose real application ports only after the owner's separate explicit admission. */
    static CloudMultimediaSession open(Context context,CloudMultimediaAdmission admission,TvFirst009TextRuntime.Observer observer,
            boolean textEnabled,boolean audioEnabled) {
        if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || Looper.myLooper()!=Looper.getMainLooper()
                || admission==null || !admission.current() || !Settings.canDrawOverlays(context)
                || !NotificationAccess.isGranted(context))return null;
        try{return new CloudMultimediaSession(context,admission,observer,textEnabled,audioEnabled);}catch(RuntimeException refused){return null;}
    }

    /** Reuse SceneRenderer and AndroidLocalAudioPort; preparation never starts a second video player. */
    private CloudMultimediaSession(Context context,CloudMultimediaAdmission admission,TvFirst009TextRuntime.Observer observer,
            boolean textEnabled,boolean audioEnabled) {
        this.context=context;this.admission=admission;this.observer=observer;
        renderer=new SceneRenderer(context,id->null,()->main.post(this::close));
        audio=new AndroidLocalAudioPort(id->{
            if(!current())throw new java.io.IOException("CURRENT_ADMISSION_REQUIRED");
            try{preparingResource=id;audioPreparations++;return admission.reference.prepared.resource(id);}
            catch(Exception refused){throw new java.io.IOException("VERIFIED_RESOURCE_UNAVAILABLE");}
        },effect->admission.reference.whileCurrent(SystemClock.elapsedRealtime(),()->{if(effectsAllowed())effect.run();}));
        runtime=new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** Permissions and the exact receipt remain necessary immediately before SHOW. */
            @Override public boolean preflight(OverlayManifest.Scene scene){return effectsAllowed() && renderer.preflight(scene);}
            /** Request the original TEXT window through the physically qualified renderer. */
            @Override public void show(OverlayManifest.Scene scene){admission.reference.whileCurrent(SystemClock.elapsedRealtime(),()->{
                if(effectsAllowed()){renderer.render(scene);pendingScene=scene.id;publish("TEXT_RENDER_REQUESTED");}});}
            /** Expiration removes only SceneVibe's current visual window. */
            @Override public void hide(OverlayManifest.Scene scene){renderer.dismissNow();}
            /** Identity loss or teardown removes all SceneVibe visuals synchronously. */
            @Override public void hideAll(){renderer.dismissNow();}
        },audio);
        try{runtime.loadAdmittedCloud(admission);}catch(RuntimeException refused){audio.release();renderer.dismissNow();throw refused;}
        // The probe may synchronously report an already-playing session. Apply the
        // owner's switches first, so an AUDIO OFF start cannot emit even briefly.
        runtime.setTextEnabled(textEnabled);runtime.setAudioEnabled(audioEnabled);
        adSync.setEnabled(BuildConfig.ADSYNC_PROTOTYPE && TvFirst009TextServiceOwner.adSyncEnabled());
        probe=new MediaSessionProbe(context,new MediaSessionProbe.Listener() {
            /** Route actual passive MediaSession observations to the already-qualified calendar. */
            @Override public void onSnapshot(MediaSessionProbe.Snapshot snapshot){observe(snapshot);}
            /** A disappearing session cannot leave either type of comment active. */
            @Override public void onUnavailable(){unavailable("SESSION_UNAVAILABLE");}
        },null,250L,BuildConfig.TVUX_CONTROL_PANEL?snapshot->MediaIdentityMatcher.matches(admission.identity,snapshot):null);
        try{probe.start();main.postDelayed(watchdog,250);}
        catch(RuntimeException refused){close();throw refused;}
    }

    /** Check the same local capability and OS permissions; no network or implicit renewal exists here. */
    private boolean current(){return active && admission.current() && Settings.canDrawOverlays(context) && NotificationAccess.isGranted(context);}

    /** Recheck the interruption gate at every delayed native effect, without retiring the admission itself. */
    private boolean effectsAllowed(){return current() && (!adSync.enabled() || adSync.status().allow);}

    /** Apply the historical native matcher and its established content-time policy without retiming events. */
    private void observe(MediaSessionProbe.Snapshot snapshot) {
        if(!active)return;
        if(!current()){close();publish("CACHE_OR_CONSENT_INVALID");return;}
        lastCapture=SystemClock.elapsedRealtime();
        MediaObservation selected=VideoMediaObservationAdapter.observe(admission.identity,snapshot);
        boolean usable=snapshot!=null && selected!=null && selected.eligible() && snapshot.positionMs>=0
                && snapshot.updateAgeMs>=0 && Float.isFinite(snapshot.speed)
                && (snapshot.state==PlaybackState.STATE_PLAYING || snapshot.state==PlaybackState.STATE_PAUSED)
                && (snapshot.state!=PlaybackState.STATE_PLAYING || snapshot.speed>0f);
        long selectedPosition=usable?(snapshot.state==PlaybackState.STATE_PLAYING?selected.positionMs():snapshot.positionMs):-1;
        if(adSync.enabled() || BuildConfig.TVUX_DEVELOPER_OVERLAY) {
            lastAdSample=snapshot==null?null:new AdSyncController.Sample(snapshot.sessionKey,usable,
                    snapshot.state==PlaybackState.STATE_PLAYING,snapshot.positionMs,selectedPosition,
                    snapshot.updatedAtMs,snapshot.capturedAtMs,snapshot.speed,snapshot.durationMs,
                    selected!=null && selected.eligible());
        }
        if(adSync.enabled()) {
            AdSyncController.Decision decision=adSync.observe(lastAdSample,SystemClock.elapsedRealtime());logAdSync(decision);
            if(!decision.allow){runtime.suspendComments();position=-1;publish("ADSYNC_"+decision.state.name());return;}
            selectedPosition=decision.contentMs;
        }
        if(!usable){unavailable(selected!=null && !selected.eligible()?"MEDIA_IDENTITY_BLOCKED":"MEDIA_CLOCK_UNUSABLE");return;}
        position=selectedPosition;
        try{runtime.observe(admission.activation,new MediaObservation(true,position,snapshot.state==PlaybackState.STATE_PLAYING));
            auditAudio();publish(snapshot.state==PlaybackState.STATE_PLAYING?"MEDIA_PLAYING":"MEDIA_PAUSED");}
        catch(RuntimeException refused){close();publish("RUNTIME_FAILED");}
    }

    /** Log finite temporal facts only; no metadata, session token, credential or alleged ad classification. */
    private void logAdSync(AdSyncController.Decision decision) {
        long now=SystemClock.elapsedRealtime();String state=decision.state.name()+":"+decision.reason.name();
        if(state.equals(lastAdState) && lastAdLog>=0 && now-lastAdLog<1000)return;
        lastAdState=state;lastAdLog=now;
        AdSyncController.Sample sample=lastAdSample;
        Log.i("SceneVibeAdSync","state="+decision.state+" reason="+decision.reason+" adSignal=UNAVAILABLE"
                +" rawMs="+(sample==null?-1:sample.rawMs)+" selectedMs="+(sample==null?-1:sample.selectedMs)
                +" contentMs="+decision.contentMs+" offsetMs="+decision.offsetMs
                +" playback="+(sample==null?"UNAVAILABLE":sample.playing?"PLAYING":"PAUSED")
                +" anchorAgeMs="+(sample==null?-1:sample.capturedMs-sample.anchorMs));
    }

    /** Enable only the experimental local guard; disabling restores the v17 observation route. */
    void setAdSyncEnabled(boolean enabled){if(active){adSync.setEnabled(BuildConfig.ADSYNC_PROTOTYPE && enabled);logAdSync(adSync.status());}}
    /** Immediately stop only SceneVibe visuals/audio, retaining the admitted cache and calendar. */
    void suspendComments(){if(active && adSync.enabled()){adSync.suspend();runtime.suspendComments();logAdSync(adSync.status());}}
    /** Ask for fresh compatible observations; this never sends PLAY/SEEK to the streaming application. */
    void resumeComments(){if(active && adSync.enabled()){adSync.requestResume();runtime.suspendComments();position=-1;
        probe.captureNow();logAdSync(adSync.status());}}
    /** Confirm a local film-time offset without writing the track or granting a new native permit. */
    boolean calibrateComments(long contentMs,boolean confirmedSeek){
        return calibrateCommentsResult(contentMs,confirmedSeek)==AdSyncController.Calibration.APPLIED;
    }
    /** Refresh MEDIA behind the closed effect gate, then return the exact local calibration outcome. */
    AdSyncController.Calibration calibrateCommentsResult(long contentMs,boolean confirmedSeek) {
        if(!active || !current())return AdSyncController.Calibration.COMPANION_INACTIVE;
        adSync.suspend();runtime.suspendComments();position=-1;
        probe.captureNow();
        AdSyncController.Calibration result=adSync.calibrateResult(contentMs,SystemClock.elapsedRealtime());
        if(result==AdSyncController.Calibration.APPLIED && confirmedSeek)runtime.confirmSeek();
        logAdSync(adSync.status());return result;
    }
    /** Expose only the bounded controller state for the existing Settings screen. */
    String adSyncStatus(){AdSyncController.Decision d=adSync.status();if(!adSync.enabled())return "AD-SYNC désactivé · comportement de référence";
        if(!d.allow)return "Commentaires suspendus · "+d.reason+" · reprise/recalage à confirmer";
        if(position<0)return "Confirmation MEDIA attendue · aucun commentaire relancé";
        return (lastAdSample!=null && !lastAdSample.playing?"Film en pause · commentaires en attente":"Suivi MEDIA compatible")
                +" · "+d.reason+" · publicité non observable";}

    /** A prepared Developer window waits for the already-admitted track to be PLAYING, even if its comments are held. */
    boolean developerMediaReady(long after) {
        return current() && developerMediaReady(lastAdSample,SystemClock.elapsedRealtime(),after);
    }
    /** Qualify only a compatible current PLAYING capture obtained after this explicit handoff. */
    static boolean developerMediaReady(AdSyncController.Sample sample,long now,long after) {
        return sample!=null && sample.valid() && sample.playing && sample.capturedMs>=after
                && now>=sample.capturedMs && now-sample.capturedMs<=2500;
    }
    /** Developer-only numeric observations; no identity, token, title or second clock is exposed. */
    String temporalDiagnostics() {
        AdSyncController.Sample sample=lastAdSample;
        if(sample==null)return "Aucune capture MEDIA actuelle.";
        long age=Math.max(0,SystemClock.elapsedRealtime()-sample.capturedMs);
        return "MEDIA brut "+sample.rawMs+" ms · suivi "+sample.selectedMs+" ms · local "+position+" ms\n"
                +"Âge capture "+age+" ms · âge ancre "+(sample.capturedMs-sample.anchorMs)+" ms";
    }

    /** Diagnose genuine native STARTED state, including a start deferred until seek completion. */
    private void auditAudio(){if(preparingResource!=null && audio.state()==LocalAudioPort.State.STARTED && started.add(preparingResource))publish("AUDIO_RESOURCE_STARTED");}

    /** Bound stale captures and expired consent while keeping media time exclusively probe-owned. */
    private void watch(){
        if(!active)return;
        if(!current()){close();publish("CACHE_OR_CONSENT_INVALID");return;}
        if(lastCapture<0 || SystemClock.elapsedRealtime()-lastCapture>2500)unavailable("CAPTURE_TIMEOUT");
        auditAudio();if(pendingScene!=null && renderer.isWindowAttached())shown.add(pendingScene);
        if(active)main.postDelayed(watchdog,250);
    }

    /** Loss of media eligibility stops both renderers without preserving an audible stale window. */
    private void unavailable(String code){if(active){position=-1;
        if(adSync.enabled()){adSync.unavailable();lastAdSample=null;runtime.suspendComments();logAdSync(adSync.status());}
        else {if(BuildConfig.TVUX_DEVELOPER_OVERLAY)lastAdSample=null;runtime.unavailable();}publish(code);}}

    /** Diagnostics never expose credentials, editorial text or native MediaController objects. */
    private void publish(String code){if(observer!=null)observer.changed(code,position,active);}

    /** A TEXT switch affects only the visual regie and keeps the shared calendar intact. */
    void setTextEnabled(boolean enabled){if(active)runtime.setTextEnabled(enabled);}
    /** An AUDIO switch immediately stops only the local MP3 port. */
    void setAudioEnabled(boolean enabled){if(active)runtime.setAudioEnabled(enabled);}
    /** Return observed distinct resources rather than equating a scheduler request with native STARTED. */
    int audioResourcesStarted(){auditAudio();return started.size();}
    /** Return distinct original TEXT scenes requested through this service session. */
    int textWindowsRequested(){return shown.size();}
    /** Count real local resource preparations separately from playback resumes or duplicate observations. */
    int audioPreparations(){return audioPreparations;}
    /** Expose the current native local-player state without exposing a MediaPlayer or external transport. */
    String audioState(){return audio.state().name();}
    /** Let the existing service retire itself when its admitted lifetime ends. */
    boolean isActive(){return active;}

    /** Invalidate callbacks before releasing the same probe, regie, native player and windows. */
    void close(){
        if(!active)return;active=false;main.removeCallbacks(watchdog);
        probe.stop();runtime.close();audio.release();renderer.dismissNow();publish("STOPPED");
    }
}
