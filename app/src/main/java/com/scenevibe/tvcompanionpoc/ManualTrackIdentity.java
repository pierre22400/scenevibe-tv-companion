package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;

/**
 * Référence technique bornée de la première campagne manuelle. Les documents
 * TEXT et les MP3 ont déjà été contrôlés par le cache atomique ; cette comparaison
 * ne remplace ni son grant courant ni le consentement local à chaque départ.
 * L'édition et le mapping éditoriaux restent UNKNOWN, sans preuve inventée.
 */
final class ManualTrackIdentity {
    /** Vérifie le programme explicite et le profil fermé, sans empreinte de corpus imposée. */
    static boolean matches(MultimediaDeliveryProfile profile) {
        if(profile==null)return false;
        try {
            JSONObject track=new JSONObject(TvFirst008Wire.utf8(profile.trackBytes));
            JSONObject target=track.getJSONObject("target"),program=target.getJSONObject("program"),platform=target.getJSONObject("platform");
            JSONObject runtime=new JSONObject(TvFirst008Wire.utf8(profile.runtimeBytes));
            return track.getString("trackId").startsWith("studio-manual:")
                    && track.getString("trackId").equals(runtime.getString("trackId"))
                    && "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e".equals(program.getString("programId"))
                    && "Columbo — Eaux troubles".equals(program.getString("title"))
                    && MultimediaDeliveryProfile.integer(program,"season",4,4)==4
                    && MultimediaDeliveryProfile.integer(program,"episode",4,4)==4
                    && MultimediaDeliveryProfile.integer(program,"year",1975,1975)==1975
                    && "prime_video".equals(platform.getString("id"))
                    && "com.amazon.amazonvideo.livingroom".equals(platform.getString("androidPackage"))
                    && MultimediaDeliveryProfile.integer(target,"horizonMs",5884768,5884768)==5884768
                    && target.isNull("durationMs")
                    && "UNKNOWN".equals(target.getJSONObject("edition").getString("status"))
                    && "UNKNOWN".equals(target.getJSONObject("mapping").getString("status"));
        }catch(Exception refused){return false;}
    }
}
