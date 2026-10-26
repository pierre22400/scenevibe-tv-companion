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
    /** Resolve only a resource id already verified in the local package. */
    public interface Resources { File resolve(String id) throws IOException; }
    private final Resources resources;
    private final java.util.function.BooleanSupplier playbackAllowed;
    private MediaPlayer player;
    private State state=State.IDLE;
    private float gain=1f;
    private long generation;
    private long requestedOffset;
    private boolean seekPending;
    private boolean startRequested;

    /** Bind the verified resolver without opening a file or player. */
    public AndroidLocalAudioPort(Resources resources) {
        this(resources,()->true);
    }

    /** Recheck the owner's current admission before every synchronous or deferred audible start. */
    public AndroidLocalAudioPort(Resources resources,java.util.function.BooleanSupplier playbackAllowed) {
        if(resources==null || playbackAllowed==null) throw new IllegalArgumentException("VERIFIED_RESOURCES_REQUIRED");
        this.resources=resources;this.playbackAllowed=playbackAllowed;
    }

    /** Prepare one actual local MP3 while retaining silent PREPARED state. */
    @Override public void prepare(String assetRef) {
        release();
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
                if(player==created && captured==generation) release();
            });
            created.setOnErrorListener((failed,what,extra) -> {
                if(player==created && captured==generation) fail();
                return true;
            });
            created.prepare(); state=State.PREPARED;
        } catch(IOException | RuntimeException refused) { fail(); }
    }

    /** Start after native offset application, never before preparation. */
    @Override public void start() {
        if(!playbackAllowed.getAsBoolean()){release();return;}
        if(player==null || state==State.ERROR || state==State.IDLE) return;
        startRequested=true;
        if(seekPending) return;
        try { player.setVolume(gain,gain); player.start(); state=State.STARTED; }
        catch(RuntimeException refused) { fail(); }
    }

    /** Freeze only SceneVibe's player and cancel a deferred audible start. */
    @Override public void pause() {
        startRequested=false;
        if(player==null || state==State.IDLE || state==State.ERROR) return;
        try { if(state==State.STARTED) player.pause(); state=State.PAUSED; }
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
        ++generation; MediaPlayer previous=player; player=null;
        startRequested=false; seekPending=false; requestedOffset=0; state=State.IDLE;
        if(previous!=null) {
            previous.setOnSeekCompleteListener(null); previous.setOnCompletionListener(null); previous.setOnErrorListener(null);
            try { previous.reset(); } catch(RuntimeException ignored) { /* Teardown remains idempotent. */ }
            previous.release();
        }
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
    private void fail() { release(); state=State.ERROR; }
}
