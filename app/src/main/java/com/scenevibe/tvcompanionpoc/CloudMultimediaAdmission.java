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
    private final PublicRuntimeReference publicReference;
    final ScheduledTrack identity;
    final String activation=java.util.UUID.randomUUID().toString();

    /** Retain one validated current cache; no public constructor can manufacture a permit. */
    private CloudMultimediaAdmission(TvFirst008Source.MultimediaReference reference,ScheduledTrack identity) {
        this.reference=reference;this.identity=identity;this.publicReference=null;
    }

    /** Conserve une autorité publique explicite sans fabriquer de référence privée. */
    private CloudMultimediaAdmission(PublicRuntimeReference reference,ScheduledTrack identity){
        this.reference=null;this.publicReference=reference;this.identity=identity;
    }

    /** Admission publique automatique après code TV : cache et programme exacts restent obligatoires. */
    static CloudMultimediaAdmission admitPublic(PublicRuntimeReference reference){
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B || !BuildConfig.CLOUD_MULTIMEDIA_SERVICE || reference==null || !reference.current(SystemClock.elapsedRealtime()))return null;
        try{
            PublicMultimediaCache.Prepared prepared=reference.prepared;
            JSONObject track=new JSONObject(TvFirst008Wire.utf8(prepared.profile.trackBytes));
            if(!prepared.binding.track.equals(track.getString("trackId")) || prepared.track.audio.isEmpty()
                    || prepared.track.calendar.events().size()<=prepared.track.audio.size())return null;
            ScheduledTrack identity=TrackParser.parse(new JSONObject(TvFirst008Wire.utf8(prepared.profile.runtimeBytes)),
                    media->{throw new TrackParser.Invalid("TEXT_ONLY_SOURCE","Unexpected source media");});
            if(!"prime_video".equals(identity.mediaIdentity.platform) || !"com.amazon.amazonvideo.livingroom".equals(identity.targetPackage))return null;
            if(ownedMediaSessionFixture)identity=new ScheduledTrack(identity.trackId,BuildConfig.APPLICATION_ID,identity.mediaIdentity,identity.comments,identity.pauseFreezesDisplay);
            return new CloudMultimediaAdmission(reference,identity);
        }catch(Exception refused){return null;}
    }

    /** Réutilise le matcher privé ; le public refuse en plus un mediaId absent ou contradictoire. */
    boolean matches(MediaSessionProbe.Snapshot snapshot){
        if(publicReference!=null && (snapshot==null || snapshot.mediaId==null || !identity.mediaIdentity.videoId.equals(snapshot.mediaId.trim())))return false;
        return MediaIdentityMatcher.matches(identity,snapshot);
    }
    /** Fournit le même calendrier et les mêmes ressources aux moteurs historiques. */
    StudioMultimediaPackage track(){return publicReference==null?reference.prepared.track:publicReference.prepared.track;}
    /** Résout uniquement une ressource du cache vérifié, sans changement du port AUDIO. */
    java.io.File resource(String id) throws Exception {return publicReference==null?reference.prepared.resource(id):publicReference.prepared.resource(id);}
    /** Sérialise les effets avec leur autorité privée ou publique respective. */
    boolean whileCurrent(long now,Runnable effect){return publicReference==null?reference.whileCurrent(now,effect):publicReference.whileCurrent(now,effect);}
    /** Conserve les diagnostics de validité de chaque namespace. */
    String invalidReason(long now){return publicReference==null?reference.invalidReason(now):publicReference.invalidReason(now);}
    /** Identifie le propriétaire public sans exposer de token ou d'affectation privée. */
    String publicCommand(){return publicReference==null?null:publicReference.prepared.binding.command;}

    /** Verify exact source mapping before accepting the separate, per-start owner confirmation. */
    static CloudMultimediaAdmission admit(TvFirst008Source.MultimediaReference reference,boolean ownerConfirmedReferenceEdition) {
        if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || !ownerConfirmedReferenceEdition || reference==null
                || !reference.current(SystemClock.elapsedRealtime()))return null;
        try {
            MultimediaPrivateCache.Prepared prepared=reference.prepared;
            boolean manual=BuildConfig.REAL_TRACK_E2E_001 && ManualTrackIdentity.matches(prepared.profile);
            boolean imported=BuildConfig.REAL_TRACK_E2E_001 && ImportedTrackIdentity.matches(prepared.profile);
            if(!manual && !imported && (!mapped(prepared.profile) || prepared.track.calendar.events().size()!=19 || prepared.track.audio.size()!=3))return null;
            if((manual || imported) && (prepared.track.audio.isEmpty()
                    || prepared.track.calendar.events().size()<=prepared.track.audio.size()))return null;
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
    boolean current() {return publicReference==null?reference.current(SystemClock.elapsedRealtime()):publicReference.current(SystemClock.elapsedRealtime());}

    /** Instrumentation may observe its OWN native MediaSession; a non-debuggable APK rejects this seam. */
    static void useOwnedMediaSessionFixture(boolean enabled) {
        if(!BuildConfig.DEBUG || !BuildConfig.CLOUD_MULTIMEDIA_SERVICE)throw new IllegalStateException("DEBUG_FIXTURE_ONLY");
        ownedMediaSessionFixture=enabled;
    }
}
