package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;

/**
 * Rattache un choix de Track à un jeton MEDIA Android pour la durée du choix.
 * Les métadonnées éditoriales ne participent ni au choix du jeton ni à la
 * validation de l'horloge. Une autre session exige un nouveau choix.
 */
final class PrimeVideoSessionBinding {
    enum State { WAITING, CLOCK_UNAVAILABLE, BUFFERING, READY, STOPPED, RESELECT_REQUIRED }
    private static final long MAX_POSITION_MS=43_200_000L;
    private static final long FRESH_CAPTURE_MS=2_500L;
    private Object token;
    private State retired;

    /** Vérifie l'origine Android déjà autorisée pour cette activation. */
    static boolean platform(String allowedPackage,MediaSessionProbe.Snapshot snapshot) {
        return snapshot!=null && allowedPackage.equals(snapshot.packageName) && snapshot.sessionKey!=null;
    }

    /** Donne la priorité au jeton choisi, y compris lorsqu'il est temporairement en pause. */
    boolean bound(MediaSessionProbe.Snapshot snapshot) {
        return snapshot!=null && token!=null && token.equals(snapshot.sessionKey);
    }

    /** Protège le choix contre un nouveau jeton et laisse le même jeton revenir après une interruption. */
    State observe(String allowedPackage,MediaSessionProbe.Snapshot snapshot,long now) {
        if(retired!=null)return retired;
        if(!platform(allowedPackage,snapshot))return State.WAITING;
        if(token!=null && !bound(snapshot))return retired=State.RESELECT_REQUIRED;
        if(snapshot.state==PlaybackState.STATE_STOPPED)
            return token==null?State.WAITING:(retired=State.STOPPED);
        if(snapshot.state==PlaybackState.STATE_BUFFERING)return State.BUFFERING;
        if(!validClock(snapshot,now))return State.CLOCK_UNAVAILABLE;
        if(token==null)token=snapshot.sessionKey;
        return State.READY;
    }

    /** Vérifie une vraie capture récente et l'ancre monotone qu'Android a fournie. */
    private static boolean validClock(MediaSessionProbe.Snapshot sample,long now) {
        boolean playing=sample.state==PlaybackState.STATE_PLAYING;
        return (playing || sample.state==PlaybackState.STATE_PAUSED)
                && sample.capturedAtMs>=0 && now>=sample.capturedAtMs && now-sample.capturedAtMs<=FRESH_CAPTURE_MS
                && sample.updatedAtMs>0 && sample.updatedAtMs<=sample.capturedAtMs
                && sample.updateAgeMs==sample.capturedAtMs-sample.updatedAtMs && sample.updateAgeMs<=MAX_POSITION_MS
                && sample.positionMs>=0 && sample.positionMs<=MAX_POSITION_MS
                && Float.isFinite(sample.speed) && (!playing || sample.speed>0f
                        && sample.estimatedPositionMs>=0 && sample.estimatedPositionMs<=MAX_POSITION_MS);
    }
}
