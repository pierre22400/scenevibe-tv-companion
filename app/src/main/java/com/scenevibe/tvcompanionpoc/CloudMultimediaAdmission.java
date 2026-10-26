package com.scenevibe.tvcompanionpoc;

import android.os.SystemClock;
import org.json.JSONObject;

/**
 * Admission locale explicite du même export Studio que la qualification TEXT.
 * Le mapping technique est une égalité de documents immuables, pas une preuve
 * juridique ou une reconnaissance automatique d'édition. L'utilisateur doit
 * confirmer l'édition de référence et l'absence de publicité à chaque départ.
 * UNKNOWN reste UNKNOWN dans le document éditorial d'origine.
 */
final class CloudMultimediaAdmission {
    private static boolean ownedMediaSessionFixture;
    static final String SNAPSHOT="7d206682eaf441426b411fcc4c2ee3599c024a4936794167fd4c6a7003c11351";
    static final String RUNTIME="061ae038ee56e528d4a05531579d06c82c89c630f93ab31d0e0f3848b361468c";
    static final String MANIFEST="9b034c30a6d68a8f822dd39696fe95cc8435450ba3c1deaeb0ce910bffb71554";
    final TvFirst008Source.MultimediaReference reference;
    final ScheduledTrack identity;
    final String activation=java.util.UUID.randomUUID().toString();

    /** Retain one validated current cache; no public constructor can manufacture a permit. */
    private CloudMultimediaAdmission(TvFirst008Source.MultimediaReference reference,ScheduledTrack identity) {
        this.reference=reference;this.identity=identity;
    }

    /** Verify exact source mapping before accepting the separate, per-start owner confirmation. */
    static CloudMultimediaAdmission admit(TvFirst008Source.MultimediaReference reference,boolean ownerConfirmedReferenceEdition) {
        if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || !ownerConfirmedReferenceEdition || reference==null
                || !reference.current(SystemClock.elapsedRealtime()))return null;
        try {
            MultimediaPrivateCache.Prepared prepared=reference.prepared;
            if(!mapped(prepared.profile) || prepared.track.calendar.events().size()!=19 || prepared.track.audio.size()!=3)return null;
            ScheduledTrack identity=TrackParser.parse(new JSONObject(TvFirst008Wire.utf8(prepared.profile.runtimeBytes)),
                    media->{throw new TrackParser.Invalid("TEXT_ONLY_SOURCE","Unexpected source media");});
            if(ownedMediaSessionFixture)identity=new ScheduledTrack(identity.trackId,BuildConfig.APPLICATION_ID,
                    identity.mediaIdentity,identity.comments,identity.pauseFreezesDisplay);
            return new CloudMultimediaAdmission(reference,identity);
        }catch(Exception refused){return null;}
    }

    /** Derive technical provenance from the exact saved Studio source, without changing its edition status. */
    static boolean mapped(MultimediaDeliveryProfile profile) {
        try{return profile!=null && SNAPSHOT.equals(MultimediaDeliveryProfile.hash(profile.trackBytes))
                && RUNTIME.equals(MultimediaDeliveryProfile.hash(profile.runtimeBytes))
                && MANIFEST.equals(MultimediaDeliveryProfile.hash(profile.manifestBytes));}
        catch(Exception refused){return false;}
    }

    /** Revoke local effects when a different cache, generation or consent supersedes this permit. */
    boolean current() {return reference.current(SystemClock.elapsedRealtime());}

    /** Instrumentation may observe its OWN native MediaSession; a non-debuggable APK rejects this seam. */
    static void useOwnedMediaSessionFixture(boolean enabled) {
        if(!BuildConfig.DEBUG || !BuildConfig.CLOUD_MULTIMEDIA_SERVICE)throw new IllegalStateException("DEBUG_FIXTURE_ONLY");
        ownedMediaSessionFixture=enabled;
    }
}
