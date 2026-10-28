package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Validate session selection on captured facts, without claiming a Prime session ordering or hardware result. */
public final class Runtime003SelectionTest {
    /** Supply bounded snapshots with opaque test session keys, never platform credentials. */
    private MediaSessionProbe.Snapshot sample(String session,String identity,boolean playing){return new MediaSessionProbe.Snapshot("own",playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,"own",1000,1000,playing?1f:0f,0,identity,"","",100000,session,1000,1000);}
    /** Reordering two equally compatible PLAYING sessions must not cause needless session churn. */
    @Test public void compatiblePlayingTieKeepsCurrentSession(){var a=sample("a","valid",true);var b=sample("b","valid",true);
        assertSame(b,MediaSessionProbe.select(List.of(a,b),s->s.mediaId.equals("valid"),"b"));}
    /** A previous token never overrides a real identity mismatch. */
    @Test public void staleSessionPreferenceCannotBypassIdentity(){var wrong=sample("old","other",true);var valid=sample("new","valid",true);
        assertSame(valid,MediaSessionProbe.select(List.of(wrong,valid),s->s.mediaId.equals("valid"),"old"));}
    /** PLAYING on a new compatible session beats a paused former session. */
    @Test public void compatiblePlaybackBeatsPausedPrevious(){var old=sample("old","valid",false);var playing=sample("new","valid",true);
        assertSame(playing,MediaSessionProbe.select(List.of(old,playing),s->s.mediaId.equals("valid"),"old"));}
    /** Unknown identity is still forwarded as incompatible for the separate renderer guard to refuse. */
    @Test public void incompatiblePrimaryIsNotRelabelledCompatible(){var wrong=sample("other","other",true);
        assertSame(wrong,MediaSessionProbe.select(List.of(wrong),s->s.mediaId.equals("valid"),"other"));}
    /** Frozen historical constructors retain the first-playing selection without a preferred identity. */
    @Test public void historicalSelectionRemainsFirstPlaying(){var a=sample("a","valid",true);var b=sample("b","valid",true);assertSame(a,MediaSessionProbe.select(List.of(a,b),null,"b"));}
}
