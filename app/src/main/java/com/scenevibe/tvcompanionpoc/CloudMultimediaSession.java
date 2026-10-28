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
    private final AdSyncController adSync=new AdSyncController(BuildConfig.ADSYNC_NONDESTRUCTIVE);
    private AdSyncController.Sample lastAdSample;
    private long lastAdLog=-1;
    private String lastAdState="";
    private boolean active=true;
    private long lastCapture=-1,position=-1;
    private String preparingResource;
    private String pendingScene;
    private int audioPreparations;
    private String lastCalendarState="";
    private String lastAudioState="";
    private boolean textEnabled,audioEnabled;
    private int nativeAudioStarts;
    private String closureReason="CALENDAR_EXPIRE_OR_REPLACE";

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
        this.textEnabled=textEnabled;this.audioEnabled=audioEnabled;
        renderer=new SceneRenderer(context,id->null,()->main.post(()->{
            if(BuildConfig.ADSYNC_NONDESTRUCTIVE)holdForPermissions();else close();
        }));
        audio=new AndroidLocalAudioPort(id->{
            if(!current())throw new java.io.IOException("CURRENT_ADMISSION_REQUIRED");
            try{preparingResource=id;audioPreparations++;if(!BuildConfig.ADSYNC_NONDESTRUCTIVE)trace("AUDIO_PREPARE_REQUESTED","count="+audioPreparations);return admission.reference.prepared.resource(id);}
            catch(Exception refused){throw new java.io.IOException("VERIFIED_RESOURCE_UNAVAILABLE");}
        },this::startAudible,BuildConfig.ADSYNC_NONDESTRUCTIVE?this::nativeAudio:null);
        runtime=new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** Permissions and the exact receipt remain necessary immediately before SHOW. */
            @Override public boolean preflight(OverlayManifest.Scene scene){boolean allowed=effectsAllowed() && renderer.preflight(scene);
                trace(allowed?"TEXT_EVENT_ELIGIBLE":"TEXT_RENDER_REFUSED",allowed?"":"guard="+effectRefusal());return allowed;}
            /** Request the original TEXT window through the physically qualified renderer. */
            @Override public void show(OverlayManifest.Scene scene){admission.reference.whileCurrent(SystemClock.elapsedRealtime(),()->{
                if(effectsAllowed()){publish("TEXT_RENDER_REQUESTED");
                    try{renderer.render(scene);}catch(RuntimeException refused){trace("TEXT_RENDER_FAILED",refused instanceof SecurityException?"guard=ANDROID_WINDOW_PERMISSION":"guard=ANDROID_WINDOW_OR_VIEW");throw refused;}
                    pendingScene=scene.id;
                    if(renderer.isWindowAttached()){shown.add(scene.id);trace("TEXT_WINDOW_ATTACHED","count="+shown.size());}
                    else trace("TEXT_WINDOW_NOT_ATTACHED","overlayGranted="+Settings.canDrawOverlays(context));}
                else trace("TEXT_RENDER_REFUSED","guard="+effectRefusal());});}
            /** Expiration removes only SceneVibe's current visual window. */
            @Override public void hide(OverlayManifest.Scene scene){removeTextWindow();}
            /** Identity loss or teardown removes all SceneVibe visuals synchronously. */
            @Override public void hideAll(){removeTextWindow();}
        },audio,BuildConfig.ADSYNC_NONDESTRUCTIVE,BuildConfig.ADSYNC_NONDESTRUCTIVE,
                diagnostic->trace(diagnostic.stage,diagnostic.toString()));
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
    private boolean effectsAllowed(){
        long now=SystemClock.elapsedRealtime();boolean fresh=lastAdSample!=null && now>=lastAdSample.capturedMs && now-lastAdSample.capturedMs<=2500;
        return current() && (BuildConfig.ADSYNC_NONDESTRUCTIVE?fresh && adSync.status().allow:(!adSync.enabled() || adSync.status().allow));
    }

    /** Apply the historical native matcher and its established content-time policy without retiming events. */
    private void observe(MediaSessionProbe.Snapshot snapshot) {
        if(!active)return;
        if(!admission.current()){retireInvalidAuthority();return;}
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && holdForPermissions())return;
        if(!current()){retireInvalidAuthority();return;}
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
        if(adSync.enabled() || BuildConfig.ADSYNC_NONDESTRUCTIVE) {
            AdSyncController.Decision decision=adSync.observe(lastAdSample,SystemClock.elapsedRealtime());logAdSync(decision);
            if(!decision.allow){suspendRuntime(decision.reason.name());position=-1;publish(BuildConfig.ADSYNC_NONDESTRUCTIVE?decision.reason.name():"ADSYNC_"+decision.state.name());auditCalendar();return;}
            selectedPosition=decision.contentMs;
        }
        if(!usable){unavailable(selected!=null && !selected.eligible()?"MEDIA_IDENTITY_BLOCKED":"MEDIA_CLOCK_UNUSABLE");return;}
        position=selectedPosition;
        try{if(BuildConfig.ADSYNC_NONDESTRUCTIVE)runtime.observe(admission.activation,new MediaObservation(true,position,snapshot.state==PlaybackState.STATE_PLAYING),
                    snapshot.sessionKey,snapshot.capturedAtMs,snapshot.speed);
            else runtime.observe(admission.activation,new MediaObservation(true,position,snapshot.state==PlaybackState.STATE_PLAYING));
            auditAudio();publish(snapshot.state==PlaybackState.STATE_PLAYING?"MEDIA_PLAYING":"MEDIA_PAUSED");auditCalendar();}
        catch(RuntimeException refused){close();publish("RUNTIME_FAILED");}
    }

    /** Log finite temporal facts only; no metadata, session token, credential or alleged ad classification. */
    private void logAdSync(AdSyncController.Decision decision) {
        long now=SystemClock.elapsedRealtime();String state=decision.state.name()+":"+decision.reason.name();
        if(state.equals(lastAdState) && (BuildConfig.ADSYNC_NONDESTRUCTIVE || lastAdLog>=0 && now-lastAdLog<1000))return;
        lastAdState=state;lastAdLog=now;
        AdSyncController.Sample sample=lastAdSample;
        Log.i("SceneVibeAdSync","state="+decision.state+" reason="+decision.reason+" adSignal=UNAVAILABLE"
                +" rawMs="+(sample==null?-1:sample.rawMs)+" selectedMs="+(sample==null?-1:sample.selectedMs)
                +" contentMs="+decision.contentMs+" offsetMs="+decision.offsetMs
                +" playback="+(sample==null?"UNAVAILABLE":sample.playing?"PLAYING":"PAUSED")
                +" anchorAgeMs="+(sample==null?-1:sample.capturedMs-sample.anchorMs)
                +(BuildConfig.ADSYNC_NONDESTRUCTIVE?" clock="+clockReason(sample)+" textOn="+textEnabled+" audioOn="+audioEnabled:""));
    }

    /** Enable only the experimental local guard; disabling restores the v17 observation route. */
    void setAdSyncEnabled(boolean enabled){if(active){boolean next=BuildConfig.ADSYNC_PROTOTYPE && enabled,changed=next!=adSync.enabled();adSync.setEnabled(next);
        if(changed && BuildConfig.ADSYNC_NONDESTRUCTIVE){runtime.policyChanged();trace("ADSYNC_MODE_CHANGED","enabled="+next+" calendarPreserved=true "+runtime.diagnostics());}
        logAdSync(adSync.status());}}
    /** Immediately stop only SceneVibe visuals/audio, retaining the admitted cache and calendar. */
    void suspendComments(){if(active && (adSync.enabled() || BuildConfig.ADSYNC_NONDESTRUCTIVE)){adSync.suspend();suspendRuntime("USER_SUSPENDED");logAdSync(adSync.status());}}
    /** Ask for fresh compatible observations; this never sends PLAY/SEEK to the streaming application. */
    void resumeComments(){if(active && (adSync.enabled() || BuildConfig.ADSYNC_NONDESTRUCTIVE)){adSync.requestResume();suspendRuntime("USER_RESUME_CONFIRMING");position=-1;
        probe.captureNow();logAdSync(adSync.status());}}
    /** Confirm a local film-time offset without writing the track or granting a new native permit. */
    boolean calibrateComments(long contentMs,boolean confirmedSeek){
        return calibrateCommentsResult(contentMs,confirmedSeek)==AdSyncController.Calibration.APPLIED;
    }
    /** Refresh MEDIA behind the closed effect gate, then return the exact local calibration outcome. */
    AdSyncController.Calibration calibrateCommentsResult(long contentMs,boolean confirmedSeek) {
        if(!active || !current())return AdSyncController.Calibration.COMPANION_INACTIVE;
        if(!adSync.enabled())return AdSyncController.Calibration.SYNC_DISABLED;
        adSync.suspend();suspendRuntime("USER_CALIBRATION");position=-1;
        probe.captureNow();
        AdSyncController.Calibration result=adSync.calibrateResult(contentMs,SystemClock.elapsedRealtime());
        if(result==AdSyncController.Calibration.APPLIED && confirmedSeek)runtime.confirmSeek();
        logAdSync(adSync.status());return result;
    }
    /** Expose only the bounded controller state for the existing Settings screen. */
    String adSyncStatus(){AdSyncController.Decision d=adSync.status();
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE){
            if(d.reason==AdSyncController.Reason.USER_SUSPENDED)return "Suspension manuelle conservée · Reprendre pour confirmer deux captures MEDIA";
            if(!d.allow)return "Commentaires retenus · "+d.reason+" · récupération MEDIA automatique, sauf suspension manuelle";
            return (adSync.enabled()?"AD-SYNC consultatif ON":"AD-SYNC OFF")+" · MEDIA compatible · publicité non observable";
        }
        if(!adSync.enabled())return "AD-SYNC désactivé · comportement de référence";
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
                +"Âge capture "+age+" ms · âge ancre "+(sample.capturedMs-sample.anchorMs)+" ms"
                +(BuildConfig.ADSYNC_NONDESTRUCTIVE?"\n"+availability()+"\nCalendrier : "+runtime.diagnostics():"");
    }

    /** Diagnose genuine native STARTED state, including a start deferred until seek completion. */
    private void auditAudio(){String state=audio.state().name();if(!state.equals(lastAudioState)){lastAudioState=state;trace("AUDIO_STATE","state="+state+" preparations="+audioPreparations);}
        if(!BuildConfig.ADSYNC_NONDESTRUCTIVE && preparingResource!=null && audio.state()==LocalAudioPort.State.STARTED && started.add(preparingResource)){
        publish("AUDIO_RESOURCE_STARTED");trace("AUDIO_NATIVE_STARTED","count="+started.size());}}

    /** Bound stale captures and expired consent while keeping media time exclusively probe-owned. */
    private void watch(){
        if(!active)return;
        if(!admission.current()){retireInvalidAuthority();return;}
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && holdForPermissions()){main.postDelayed(watchdog,250);return;}
        if(!current()){retireInvalidAuthority();return;}
        if(lastCapture<0 || SystemClock.elapsedRealtime()-lastCapture>2500)unavailable("CAPTURE_TIMEOUT");
        auditAudio();if(pendingScene!=null && renderer.isWindowAttached() && shown.add(pendingScene))trace("TEXT_WINDOW_ATTACHED","count="+shown.size());
        if(active)main.postDelayed(watchdog,250);
    }

    /** Loss of media eligibility stops both renderers without preserving an audible stale window. */
    private void unavailable(String code){if(active){position=-1;
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && holdForPermissions())return;
        if(adSync.enabled() || BuildConfig.ADSYNC_NONDESTRUCTIVE){adSync.unavailable();lastAdSample=null;suspendRuntime(code);logAdSync(adSync.status());}
        else {if(BuildConfig.TVUX_DEVELOPER_OVERLAY)lastAdSample=null;runtime.unavailable();}publish(BuildConfig.ADSYNC_NONDESTRUCTIVE && ("CAPTURE_TIMEOUT".equals(code) || "SESSION_UNAVAILABLE".equals(code))?"MEDIA_UNAVAILABLE":code);}}

    /** Diagnostics never expose credentials, editorial text or native MediaController objects. */
    private void publish(String code){if(observer!=null)observer.changed(code,position,active);}

    /** Identify the exact closed authority boundary without erasing any cached file or logging identities. */
    private void retireInvalidAuthority(){String reason=BuildConfig.ADSYNC_NONDESTRUCTIVE?admission.reference.invalidReason(SystemClock.elapsedRealtime()):"";
        if(reason.isEmpty())reason=!Settings.canDrawOverlays(context)?"OVERLAY_PERMISSION_REQUIRED":!NotificationAccess.isGranted(context)?"MEDIA_PERMISSION_REQUIRED":"CACHE_OR_CONSENT_INVALID";
        close();publish(BuildConfig.ADSYNC_NONDESTRUCTIVE?reason:"CACHE_OR_CONSENT_INVALID");}

    /** A TEXT switch affects only the visual regie and keeps the shared calendar intact. */
    void setTextEnabled(boolean enabled){textEnabled=enabled;if(active){runtime.setTextEnabled(enabled);auditCalendar();}}
    /** An AUDIO switch immediately stops only the local MP3 port. */
    void setAudioEnabled(boolean enabled){audioEnabled=enabled;if(active){runtime.setAudioEnabled(enabled);auditCalendar();}}
    /** Return observed distinct resources rather than equating a scheduler request with native STARTED. */
    int audioResourcesStarted(){auditAudio();return started.size();}
    /** Count actual native starts including qualified replay; distinct-resource count remains separate. */
    int audioPlaybackStarts(){return nativeAudioStarts;}
    /** Report actual current WindowManager attachment independently of cumulative distinct windows. */
    boolean textWindowAttached(){return renderer.isWindowAttached();}
    /** Read actual calendar counters without creating MEDIA eligibility or exposing an activation. */
    com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler.Diagnostics calendarDiagnostics(){return runtime.diagnostics();}
    /** Distinguish explicit user suspension from MEDIA uncertainty or a changed AD-SYNC preference. */
    boolean manuallySuspended(){return adSync.manuallySuspended();}
    /** Optional recovery for a lower NEW session; ordinary confirmed same-session rewinds need no user action. */
    String confirmReplay(){
        if(!active || !current())return "Relecture refusée : admission ou permissions indisponibles ; cache conservé.";
        probe.captureNow();
        if(adSync.manuallySuspended())return "Commentaires en pause volontaire : reprends avant de confirmer une relecture.";
        if(!effectsAllowed() || lastAdSample==null || !lastAdSample.valid())return "Relecture refusée : attends une identité et une position MEDIA compatibles.";
        runtime.confirmSeek();trace("REPLAY_EXPLICIT","calendarRetained=true");
        return "Relecture SceneVibe confirmée au temps MEDIA actuel ; aucun déplacement du lecteur.";
    }
    /** Observe the admitted audio event by ordinal, without exposing its resource identifier. */
    com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler.AudioDiagnostics audioDiagnostics(int ordinal){
        int index=0;for(String id:admission.reference.prepared.track.audio.keySet())if(++index==ordinal)return runtime.audioDiagnostics(id);return null;
    }

    /** Start only if the audible effect actually ran under the current receipt/permission/MEDIA fence. */
    private boolean startAudible(Runnable effect){boolean[] ran={false};admission.reference.whileCurrent(SystemClock.elapsedRealtime(),()->{
        if(effectsAllowed()){effect.run();ran[0]=true;}});return ran[0];}

    /** Correlate native acknowledgements by attempt; repeated resources are real new playbacks, never deduplicated diagnostics. */
    private void nativeAudio(long attempt,AndroidLocalAudioPort.Event event){
        String stage=event==AndroidLocalAudioPort.Event.STARTED?"AUDIO_RESOURCE_STARTED":"AUDIO_"+event.name();
        if(event==AndroidLocalAudioPort.Event.STARTED){nativeAudioStarts++;if(preparingResource!=null)started.add(preparingResource);publish("AUDIO_RESOURCE_STARTED");}
        runtime.nativeAudio(attempt,stage);
    }

    /** Preserve the cause of a visual/native removal without modifying the admitted calendar. */
    private void suspendRuntime(String reason){String previous=closureReason;closureReason=reason;
        try{runtime.suspendComments(reason);}finally{closureReason=previous;}}

    /** Remove the SceneVibe window only, recording why an actually attached TEXT disappeared. */
    private void removeTextWindow(){boolean attached=renderer.isWindowAttached();renderer.dismissNow();
        if(attached)trace("TEXT_WINDOW_REMOVED","reason="+closureReason);}
    /** Return distinct original TEXT scenes requested through this service session. */
    int textWindowsRequested(){return shown.size();}
    /** Count real local resource preparations separately from playback resumes or duplicate observations. */
    int audioPreparations(){return audioPreparations;}
    /** Expose the current native local-player state without exposing a MediaPlayer or external transport. */
    String audioState(){return audio.state().name();}
    /** Let the existing service retire itself when its admitted lifetime ends. */
    boolean isActive(){return active;}

    /** A missing OS permission blocks every effect, retaining only the still-current admission and cache. */
    private boolean holdForPermissions(){
        String code=!Settings.canDrawOverlays(context)?"OVERLAY_PERMISSION_REQUIRED":!NotificationAccess.isGranted(context)?"MEDIA_PERMISSION_REQUIRED":null;
        if(code==null)return false;
        suspendRuntime(code);adSync.unavailable();lastAdSample=null;position=-1;publish(code);auditCalendar();return true;
    }

    /** Explain the first closed native boundary without private metadata or a fallback permission. */
    private String effectRefusal(){
        if(!active)return "SERVICE_STOPPED";
        String invalid=admission.reference.invalidReason(SystemClock.elapsedRealtime());if(!invalid.isEmpty())return invalid;
        if(!Settings.canDrawOverlays(context))return "OVERLAY_PERMISSION_REQUIRED";
        if(!NotificationAccess.isGranted(context))return "MEDIA_PERMISSION_REQUIRED";
        if(lastAdSample==null)return "MEDIA_UNAVAILABLE";
        if(SystemClock.elapsedRealtime()-lastAdSample.capturedMs>2500)return "OBSERVATION_STALE";
        return adSync.status().allow?"RENDERER_PREFLIGHT":adSync.status().reason.name();
    }

    /** Distinguish the observed clock fault from an identity refusal or normal silence between events. */
    private static String clockReason(AdSyncController.Sample sample){
        if(sample==null)return "NO_CAPTURE";
        if(!sample.identityCompatible)return "IDENTITY_INCOMPATIBLE";
        if(sample.rawMs<0)return "RAW_POSITION_MISSING";
        if(sample.anchorMs<=0)return "ANCHOR_MISSING";
        if(sample.anchorMs>sample.capturedMs)return "ANCHOR_FUTURE";
        if(!Float.isFinite(sample.speed))return "SPEED_NONFINITE";
        if(sample.playing && sample.speed<=0)return "PLAYING_SPEED_INVALID";
        if(sample.durationMs < -1 || sample.durationMs>43_200_000L)return "DURATION_OUT_OF_RANGE";
        return sample.valid()?"VALID":"STATE_OR_POSITION_UNUSABLE";
    }

    /** Emit structural/calendar transitions only; a progressing numeric position alone does not generate a log. */
    private void auditCalendar(){
        if(!BuildConfig.ADSYNC_NONDESTRUCTIVE)return;
        var d=runtime.diagnostics();String state=d.total+":"+d.consumed+":"+d.future+":"+d.eligible+":"+d.windows+":"+d.textDue+":"+d.audioDue+":"+d.textRearmed+":"+d.textRestoreRequests+":"+d.interrupted+":"+d.replay+":"+textEnabled+":"+audioEnabled;
        if(!state.equals(lastCalendarState)){lastCalendarState=state;trace("CALENDAR_EVALUATED",d+" admission="+admission.current()+" textOn="+textEnabled+" audioOn="+audioEnabled);}
    }

    /** Bounded transition log, never content, tokens, session keys or platform commands. */
    private void trace(String code,String detail){if(BuildConfig.ADSYNC_NONDESTRUCTIVE)Log.i("SceneVibeRuntime","event="+code+(detail.isEmpty()?"":" "+detail));}

    /** Truthful runtime state for the restored track, panel and phone, independent of service existence. */
    String availability(){
        if(!active)return "Service arrêté";
        if(adSync.manuallySuspended())return "Commentaires en pause volontaire · préférences TEXT/AUDIO conservées";
        String refused=effectRefusal();
        if(!"RENDERER_PREFLIGHT".equals(refused))return "En attente du film · "+refused;
        if(lastAdSample!=null && !lastAdSample.playing)return "Film en pause · calendrier conservé";
        var d=runtime.diagnostics();
        return (textEnabled || audioEnabled?"Commentaires actifs":"Commentaires désactivés")+" · TEXT "+(textEnabled?"ON":"OFF")+" · AUDIO "+(audioEnabled?"ON":"OFF")
                +" · "+d.future+" événements futurs · prochain "+(d.nextStartMs<0?"aucun":AdSyncController.formatTime(d.nextStartMs))
                +" · demandes TEXT "+d.textDue+" · TEXT attachés (distincts) "+shown.size()+" · lectures MP3 démarrées "+nativeAudioStarts
                +("NEW_SESSION_REPLAY_UNCERTAIN".equals(d.replay)?" · nouvelle session en recul : relecture facultative dans les options téléphone":"");
    }

    /** Stable client vocabulary separates actual manual pause, waiting MEDIA and disabled channels. */
    String commentState(){if(!active)return "SERVICE_STOPPED";if(adSync.manuallySuspended())return "USER_PAUSED";
        if(!"RENDERER_PREFLIGHT".equals(effectRefusal()))return "MEDIA_WAIT";
        if(lastAdSample!=null && !lastAdSample.playing)return "MEDIA_PAUSED";
        return textEnabled || audioEnabled?"ACTIVE":"CHANNELS_OFF";}

    /** Invalidate callbacks before releasing the same probe, regie, native player and windows. */
    void close(){
        if(!active)return;active=false;main.removeCallbacks(watchdog);
        probe.stop();runtime.close();audio.release();renderer.dismissNow();publish("STOPPED");
    }
}
