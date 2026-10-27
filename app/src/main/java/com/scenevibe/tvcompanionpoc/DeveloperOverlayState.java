package com.scenevibe.tvcompanionpoc;

/**
 * Process-local developer UI state, independent of all media clocks and permits.
 * Preparing never grants focus. Only the explicit Activity launch handoff can
 * authorize interactivity; elapsed time alone can only expire that authorization.
 * Closing a panel keeps the mode enabled, while disabling removes both intents.
 */
final class DeveloperOverlayState {
    enum Stage { OFF, ENABLED, PREPARED, HANDOFF, WAITING_PLAYER, INTERACTIVE }
    private Stage stage=Stage.OFF;
    private long generation,deadline;
    private String ticket;

    /** Start closed after a process restart; no preference can reopen a control window. */
    DeveloperOverlayState() {}
    /** Expose the one service-owned UI state without persisting a playback authority. */
    Stage stage(){return stage;}
    /** Distinguish enabling the developer tools from opening their window. */
    boolean enabled(){return stage!=Stage.OFF;}
    /** Enable without attaching or focusing a window. */
    void enable(){if(stage==Stage.OFF)stage=Stage.ENABLED;}
    /** Revoke every outstanding handoff when the user disables the mode. */
    void disable(){stage=Stage.OFF;ticket=null;deadline=0;}
    /** Record an actually attached passive window, cancelling an earlier pending launch. */
    void prepared(){if(enabled()){stage=Stage.PREPARED;ticket=null;deadline=0;}}
    /** Bind one explicit foreground Activity launch to a bounded, one-use UI handoff. */
    String begin(long now) {
        if(stage!=Stage.PREPARED || now<0 || now>Long.MAX_VALUE-15000 || generation==Long.MAX_VALUE)return null;
        ticket="developer-handoff-"+(++generation);deadline=now+15000;stage=Stage.HANDOFF;return ticket;
    }
    /** Read eligibility without promoting any window or touching the media calendar. */
    boolean accepts(String value,long now) {
        expire(now);return stage==Stage.HANDOFF && ticket!=null && ticket.equals(value) && now>=deadline-15000;
    }
    /** Confirm interactivity only after the same handoff has updated the existing window successfully. */
    boolean commit(String value,long now) {
        if(!accepts(value,now))return false;
        stage=Stage.INTERACTIVE;ticket=null;deadline=0;return true;
    }
    /** An active track waits passively until its existing probe sees compatible PLAYING media. */
    boolean waitForPlayer(String value,long now) {
        if(!accepts(value,now) || now>Long.MAX_VALUE-300000)return false;
        stage=Stage.WAITING_PLAYER;ticket=null;deadline=now+300000;return true;
    }
    /** Waiting never takes focus because time passed; only the qualified MEDIA callback may promote it. */
    boolean awaitingPlayer(long now){expire(now);return stage==Stage.WAITING_PLAYER;}
    /** A MEDIA observation cached before the Activity stopped cannot authorize this handoff. */
    long playerWaitStarted(){return stage==Stage.WAITING_PLAYER?deadline-300000:Long.MAX_VALUE;}
    /** Failed/cancelled launches keep the passive window and leave all remote keys free. */
    void cancel(String value){if(stage==Stage.HANDOFF && ticket!=null && ticket.equals(value))prepared();}
    /** Secondary, explicit notification selection may show tools while the mode is already enabled. */
    void shown(){if(enabled()){stage=Stage.INTERACTIVE;ticket=null;deadline=0;}}
    /** Back/cross closes only the window; mode and business switches remain independent. */
    void closed(){if(enabled()){stage=Stage.ENABLED;ticket=null;deadline=0;}}
    /** A late lifecycle callback cannot unexpectedly take focus over another application. */
    void expire(long now){
        if(stage==Stage.HANDOFF && (now<deadline-15000 || now>=deadline))prepared();
        else if(stage==Stage.WAITING_PLAYER && (now<deadline-300000 || now>=deadline))prepared();
    }
}
