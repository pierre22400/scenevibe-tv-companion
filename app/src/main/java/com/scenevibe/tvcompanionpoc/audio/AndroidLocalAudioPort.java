package com.scenevibe.tvcompanionpoc.audio;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import java.io.File;
import java.io.IOException;

/**
 * Local-file adaptation of the AUDIO 002 MediaPlayer port, with native offsets.
 * Every resource resolves through the verified private package cache. Preparation
 * remains silent; a pending seek completes before start. Player generations reject
 * callbacks from released instances. No focus, global volume or Prime transport
 * API is referenced. Native codec/seek accuracy still requires Android/Sony tests.
 */
public final class AndroidLocalAudioPort implements LocalAudioPort {
    /** Actual native lifecycle facts; STARTED never means an acoustic Sony test. */
    public enum Event { PREPARE_REQUESTED, PREPARED, STARTED, PAUSED, RESUMED, STOPPED, COMPLETED, START_REFUSED, ERROR }
    /** Optional diagnostic observer; it grants no playback authority and owns no clock. */
    public interface Observer { /** Correlate one native attempt without changing the selected resource. */ void changed(long attempt,Event event); }
    /** Resolve only a resource id already verified in the local package. */
    public interface Resources { File resolve(String id) throws IOException; }
    /** Execute one audible effect under the service's current cache/consent fence. */
    public interface StartGate { /** Return false when the current owner no longer authorizes the effect. */ boolean run(Runnable audibleEffect); }
    private final Resources resources;
    private final StartGate startGate;
    private final Observer observer;
    private long attempt;
    private boolean attemptPresent,startedOnce;
    private MediaPlayer player;
    private State state=State.IDLE;
    private float gain=1f;
    private long generation;
    private long requestedOffset;
    private boolean seekPending;
    private boolean startRequested;

    /** Bind the verified resolver without opening a file or player. */
    public AndroidLocalAudioPort(Resources resources) {
        this(resources,effect->{effect.run();return true;});
    }

    /** Recheck the owner's current admission before every synchronous or deferred audible start. */
    public AndroidLocalAudioPort(Resources resources,StartGate startGate) {
        this(resources,startGate,null);
    }

    /** Retain the existing guarded player and optionally acknowledge real preparation/start/terminal edges. */
    public AndroidLocalAudioPort(Resources resources,StartGate startGate,Observer observer) {
        if(resources==null || startGate==null) throw new IllegalArgumentException("VERIFIED_RESOURCES_REQUIRED");
        this.resources=resources;this.startGate=startGate;this.observer=observer;
    }

    /** Prepare one actual local MP3 while retaining silent PREPARED state. */
    @Override public void prepare(String assetRef) {
        release();
        attempt++;attemptPresent=true;startedOnce=false;notify(Event.PREPARE_REQUESTED);
        try {
            File file=resources.resolve(assetRef);
            if(file==null || !file.isFile() || file.length()<1) throw new IOException("RESOURCE_MISSING");
            MediaPlayer created=new MediaPlayer(); player=created;
            final long captured=generation;
            created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            created.setDataSource(file.getAbsolutePath()); created.setVolume(gain,gain);
            created.setOnSeekCompleteListener(completed -> {
                if(player!=created || captured!=generation) return;
                // Some extractors deliver an older queued seek completion first.
                if(Math.abs((long)completed.getCurrentPosition()-requestedOffset)>100L) {
                    completed.seekTo(requestedOffset,MediaPlayer.SEEK_CLOSEST); return;
                }
                seekPending=false;
                if(startRequested) start();
            });
            created.setOnCompletionListener(completed -> {
                if(player==created && captured==generation) release(Event.COMPLETED);
            });
            created.setOnErrorListener((failed,what,extra) -> {
                if(player==created && captured==generation) fail();
                return true;
            });
            created.prepare(); state=State.PREPARED;notify(Event.PREPARED);
        } catch(IOException | RuntimeException refused) { fail(); }
    }

    /** Start after native offset application, never before preparation. */
    @Override public void start() {
        if(player==null || state==State.ERROR || state==State.IDLE || state==State.STARTED) return;
        startRequested=true;
        if(seekPending) return;
        try { if(!startGate.run(()->{player.setVolume(gain,gain);player.start();state=State.STARTED;
            boolean first=!startedOnce;startedOnce=true;notify(first?Event.STARTED:Event.RESUMED);}) || state!=State.STARTED)release(Event.START_REFUSED); }
        catch(RuntimeException refused) { fail(); }
    }

    /** Freeze only SceneVibe's player and cancel a deferred audible start. */
    @Override public void pause() {
        startRequested=false;
        if(player==null || state==State.IDLE || state==State.ERROR) return;
        try { boolean changed=state!=State.PAUSED;if(state==State.STARTED) player.pause(); state=State.PAUSED;if(changed)notify(Event.PAUSED); }
        catch(RuntimeException refused) { fail(); }
    }

    /** Resume the same local MP3 at its retained native offset. */
    @Override public void resume() { start(); }

    /** Seek only the local resource; an incomplete seek remains inaudible. */
    @Override public void seekTo(long offsetMs) {
        if(player==null || offsetMs<0 || offsetMs>Integer.MAX_VALUE) throw new IllegalArgumentException("LOCAL_OFFSET_INVALID");
        try {
            boolean resume=state==State.STARTED || startRequested;
            if(state==State.STARTED) player.pause();
            requestedOffset=offsetMs; seekPending=true; startRequested=resume;
            if(state==State.STARTED) state=State.PAUSED;
            player.seekTo(offsetMs,MediaPlayer.SEEK_CLOSEST);
        } catch(RuntimeException refused) { fail(); }
    }

    /** Explicitly advertise implemented local freeze/seek operations. */
    @Override public boolean supportsNativeOffsets() { return true; }

    /** Stop immediately and release all held native resources. */
    @Override public void stop() { release(); }

    /** Invalidate callbacks before releasing a native player exactly once. */
    @Override public void release() {
        release(Event.STOPPED);
    }

    /** Retire a native generation before publishing its exact terminal cause once. */
    private void release(Event terminal) {
        ++generation; MediaPlayer previous=player; player=null;
        startRequested=false; seekPending=false; requestedOffset=0; state=State.IDLE;
        if(previous!=null) {
            previous.setOnSeekCompleteListener(null); previous.setOnCompletionListener(null); previous.setOnErrorListener(null);
            try { previous.reset(); } catch(RuntimeException ignored) { /* Teardown remains idempotent. */ }
            previous.release();
        }
        if(attemptPresent){attemptPresent=false;notify(terminal);}
    }

    /** Change the local player gain only, preserving the native system/Prime volume. */
    @Override public void setGain(float value) {
        if(!Float.isFinite(value) || value<0 || value>1) throw new IllegalArgumentException("LOCAL_GAIN_INVALID");
        gain=value;
        if(player!=null) {
            try { player.setVolume(value,value); } catch(RuntimeException refused) { fail(); }
        }
    }

    /** Expose truthful lifecycle state without requesting player transport authority. */
    @Override public State state() { return state; }

    /** Tear down a failed native operation and retain an explicit error diagnostic. */
    private void fail() { release(Event.ERROR); state=State.ERROR; }

    /** Publish only lifecycle facts after their native transition; no URLs, credentials or audio bytes. */
    private void notify(Event event){if(observer!=null)observer.changed(attempt,event);}
}
