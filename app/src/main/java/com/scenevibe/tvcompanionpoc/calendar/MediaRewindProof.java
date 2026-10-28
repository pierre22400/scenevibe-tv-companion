package com.scenevibe.tvcompanionpoc.calendar;

/**
 * Evidence-only confirmation of a lower public MEDIA position. No playback time
 * is generated: capture time bounds coherence, while the supplied content time
 * alone drives the existing calendar. A new session is not proof of user SEEK.
 */
final class MediaRewindProof {
    enum Result { NORMAL, WAITING, CONFIRMED }
    private Object session;
    private long position=-1,candidateStart=-1,firstCapture,lastCapture,lastPosition;
    private float lastSpeed;
    private boolean lastPlaying;
    private int captures;
    private long ambiguousCeiling=-1;
    private String reason="NO_REWIND";

    /** Consume one already-compatible observation and actual capture metadata, never a substitute clock. */
    Result observe(MediaObservation observation,Object sessionKey,long capturedMs,float speed){
        long next=observation.positionMs();
        if(sessionKey==null || capturedMs<0 || !Float.isFinite(speed)){
            interrupted();reason="REPLAY_EVIDENCE_UNAVAILABLE";return Result.NORMAL;
        }
        if(session==null){session=sessionKey;position=next;return Result.NORMAL;}
        if(!session.equals(sessionKey)){
            if(next<position-5000){ambiguousCeiling=position;reason="NEW_SESSION_REPLAY_UNCERTAIN";}
            session=sessionKey;position=next;interrupted();return Result.NORMAL;
        }
        if(next>=position-5000){
            interrupted();position=next;
            if(ambiguousCeiling>=0 && next>=ambiguousCeiling){ambiguousCeiling=-1;reason="NO_REWIND";}
            return Result.NORMAL;
        }
        if(candidateStart<0){begin(next,capturedMs,speed,observation.playing());return Result.WAITING;}
        long elapsed=capturedMs-lastCapture,delta=next-lastPosition;
        if(elapsed==0)return Result.WAITING;
        double expected=lastPlaying?elapsed*(double)lastSpeed:0;
        if(elapsed<0 || elapsed>2500 || delta<0 || Math.abs(delta-expected)>750){
            begin(next,capturedMs,speed,observation.playing());return Result.WAITING;
        }
        captures++;lastCapture=capturedMs;lastPosition=next;lastSpeed=speed;lastPlaying=observation.playing();
        if(captures<3 || capturedMs-firstCapture<750 || next-candidateStart<500 || !observation.playing())return Result.WAITING;
        position=next;ambiguousCeiling=-1;reason="BACKWARD_CONFIRMED";return Result.CONFIRMED;
    }

    /** Begin a new bounded sequence below the previous compatible coordinate. */
    private void begin(long next,long captured,float speed,boolean playing){candidateStart=next;firstCapture=captured;lastCapture=captured;
        lastPosition=next;lastSpeed=speed;lastPlaying=playing;captures=1;reason="BACKWARD_CONFIRMING";}

    /** Invalid/absent MEDIA breaks consecutive evidence without discarding the last compatible coordinate. */
    void interrupted(){candidateStart=-1;captures=0;}

    /** A policy/calibration change is not a user SEEK and establishes a fresh comparison baseline. */
    void rebase(){session=null;position=-1;ambiguousCeiling=-1;interrupted();reason="NO_REWIND";}

    /** Report the confirmed landing's first coordinate, not an inferred new timestamp. */
    long landing(){return candidateStart;}

    /** Clear only candidate state after the calendar has applied this confirmation exactly once. */
    void applied(){interrupted();}

    /** Closed diagnostic vocabulary; no opaque session identity is exposed. */
    String reason(){return reason;}
}
