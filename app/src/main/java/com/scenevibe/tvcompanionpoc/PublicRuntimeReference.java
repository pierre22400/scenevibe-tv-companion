package com.scenevibe.tvcompanionpoc;

import android.os.SystemClock;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/**
 * Autorité technique publique issue de l'unique association par code.
 * Elle lie grant/génération, cache installé et durée du grant ; elle ne crée
 * aucune seconde validation humaine ni preuve d'identité de l'œuvre.
 */
final class PublicRuntimeReference {
    final PublicMultimediaCache.Prepared prepared;
    private final PublicMultimediaCache cache;
    private final TvFirst008State state;
    private final BooleanSupplier valid;
    private final long issued=SystemClock.elapsedRealtime();
    private long deadline;
    private final Object lock=new Object();

    /** Capture la possession courante et son expiration monotone après vérification Cloud. */
    PublicRuntimeReference(PublicMultimediaCache.Prepared prepared,PublicMultimediaCache cache,TvFirst008State state,long deadline,BooleanSupplier valid){
        this.prepared=prepared;this.cache=cache;this.state=state;this.deadline=deadline;this.valid=valid;
    }
    /** Contrôle la génération et le pointeur installé avant tout effet natif. */
    String invalidReason(long now){
        synchronized(lock){try{
            if(now<issued)return "CONSENT_CLOCK_INVALID";
            if(now>=deadline)return "CONSENT_EXPIRED";
            if(!valid.getAsBoolean())return "AUTHORITY_SUPERSEDED";
            JSONObject identity=state.read("identity");
            if(identity==null || !prepared.binding.device.equals(identity.optString("deviceId")) || !prepared.binding.generation.equals(identity.optString("generation")))return "IDENTITY_CHANGED";
            return cache.matches(prepared.binding)?"":"CACHE_BINDING_CHANGED";
        }catch(Exception refused){return "CACHE_METADATA_UNAVAILABLE";}}
    }
    /** La validité technique ne dépend jamais d'une case de consentement locale. */
    boolean current(long now){return invalidReason(now).isEmpty();}
    /** Prolonge seulement depuis une revalidation de possession Cloud de même génération. */
    void renewed(long verifiedDeadline){synchronized(lock){if(valid.getAsBoolean() && verifiedDeadline>SystemClock.elapsedRealtime())deadline=verifiedDeadline;}}
    /** Sérialise un effet avec le contrôle complet, comme le chemin privé 008. */
    boolean whileCurrent(long now,Runnable effect){synchronized(lock){if(!current(now))return false;effect.run();return true;}}
}
