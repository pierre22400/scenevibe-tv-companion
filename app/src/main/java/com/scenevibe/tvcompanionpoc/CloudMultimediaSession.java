package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
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
    private boolean active=true;
    private long lastCapture=-1,position=-1;
    private String preparingResource;
    private String pendingScene;
    private int audioPreparations;

    /** Compose real application ports only after the owner's separate explicit admission. */
    static CloudMultimediaSession open(Context context,CloudMultimediaAdmission admission,TvFirst009TextRuntime.Observer observer) {
        if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || Looper.myLooper()!=Looper.getMainLooper()
                || admission==null || !admission.current() || !Settings.canDrawOverlays(context)
                || !NotificationAccess.isGranted(context))return null;
        try{return new CloudMultimediaSession(context,admission,observer);}catch(RuntimeException refused){return null;}
    }

    /** Reuse SceneRenderer and AndroidLocalAudioPort; preparation never starts a second video player. */
    private CloudMultimediaSession(Context context,CloudMultimediaAdmission admission,TvFirst009TextRuntime.Observer observer) {
        this.context=context;this.admission=admission;this.observer=observer;
        renderer=new SceneRenderer(context,id->null,()->main.post(this::close));
        audio=new AndroidLocalAudioPort(id->{
            if(!current())throw new java.io.IOException("CURRENT_ADMISSION_REQUIRED");
            try{preparingResource=id;audioPreparations++;return admission.reference.prepared.resource(id);}
            catch(Exception refused){throw new java.io.IOException("VERIFIED_RESOURCE_UNAVAILABLE");}
        },this::current);
        runtime=new StudioMultimediaRuntime(new SceneRuntimeController.SceneSink() {
            /** Permissions and the exact receipt remain necessary immediately before SHOW. */
            @Override public boolean preflight(OverlayManifest.Scene scene){return current() && renderer.preflight(scene);}
            /** Request the original TEXT window through the physically qualified renderer. */
            @Override public void show(OverlayManifest.Scene scene){if(current()){renderer.render(scene);pendingScene=scene.id;publish("TEXT_RENDER_REQUESTED");}}
            /** Expiration removes only SceneVibe's current visual window. */
            @Override public void hide(OverlayManifest.Scene scene){renderer.dismissNow();}
            /** Identity loss or teardown removes all SceneVibe visuals synchronously. */
            @Override public void hideAll(){renderer.dismissNow();}
        },audio);
        try{runtime.loadAdmittedCloud(admission);}catch(RuntimeException refused){audio.release();renderer.dismissNow();throw refused;}
        probe=new MediaSessionProbe(context,new MediaSessionProbe.Listener() {
            /** Route actual passive MediaSession observations to the already-qualified calendar. */
            @Override public void onSnapshot(MediaSessionProbe.Snapshot snapshot){observe(snapshot);}
            /** A disappearing session cannot leave either type of comment active. */
            @Override public void onUnavailable(){unavailable("SESSION_UNAVAILABLE");}
        });
        try{probe.start();main.postDelayed(watchdog,250);}
        catch(RuntimeException refused){close();throw refused;}
    }

    /** Check the same local capability and OS permissions; no network or implicit renewal exists here. */
    private boolean current(){return active && admission.current() && Settings.canDrawOverlays(context) && NotificationAccess.isGranted(context);}

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
        if(!usable){unavailable(selected!=null && !selected.eligible()?"MEDIA_IDENTITY_BLOCKED":"MEDIA_CLOCK_UNUSABLE");return;}
        position=snapshot.state==PlaybackState.STATE_PLAYING?selected.positionMs():snapshot.positionMs;
        try{runtime.observe(admission.activation,new MediaObservation(true,position,snapshot.state==PlaybackState.STATE_PLAYING));
            auditAudio();publish(snapshot.state==PlaybackState.STATE_PLAYING?"MEDIA_PLAYING":"MEDIA_PAUSED");}
        catch(RuntimeException refused){close();publish("RUNTIME_FAILED");}
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
    private void unavailable(String code){if(active){position=-1;runtime.unavailable();publish(code);}}

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
