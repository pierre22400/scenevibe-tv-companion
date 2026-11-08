package com.scenevibe.tvcompanionpoc;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * REAL TRACK — admission éditoriale explicite d'une source TV-FIRST importée.
 *
 * Un document source importé garde ses TEXT originaux, mais Studio lui donne
 * un identifiant éditorial `studio-imported:` distinct de `studio-manual:`.
 * La référence audiovisuelle est bornée au Columbo/Prime de qualification ;
 * l'absence de preuve de saison, d'édition et de mapping est conservée.
 *
 * Cette validation ne remplace jamais l'intégrité du cache, l'association TV,
 * la confirmation du propriétaire ni l'identification MEDIA effective.
 */
final class ImportedTrackIdentity {
    /** Vérifie la référence audiovisuelle et le profil éditorial sans fabriquer de preuve MEDIA. */
    static boolean matches(MultimediaDeliveryProfile profile) {
        if(profile==null)return false;
        try {
            JSONObject track=new JSONObject(TvFirst008Wire.utf8(profile.trackBytes));
            JSONObject target=track.getJSONObject("target");
            JSONObject program=target.getJSONObject("program"),platform=target.getJSONObject("platform");
            JSONObject runtime=new JSONObject(TvFirst008Wire.utf8(profile.runtimeBytes));
            JSONObject runtimeMedia=runtime.getJSONObject("mediaIdentity");
            JSONArray events=track.getJSONArray("events");
            int texts=0,audios=0;
            for(int index=0;index<events.length();index++) {
                String type=events.getJSONObject(index).getString("type");
                if("TEXT".equals(type))texts++;
                else if("AUDIO".equals(type))audios++;
                else return false;
            }
            String identity=track.getString("trackId");
            return identity.startsWith("studio-imported:video-")
                    && identity.length()>"studio-imported:video-".length()
                    && !identity.equals(runtime.getString("trackId"))
                    && "scenevibe.track.v1".equals(runtime.getString("type"))
                    && texts>0 && audios>0 && events.length()==texts+audios
                    && audios==profile.resources.size()
                    && "amzn1.dv.gti.baecadcc-c3ef-42a3-bce0-c241adaa992e".equals(program.getString("programId"))
                    && "Columbo — Eaux troubles".equals(program.getString("title"))
                    && program.has("season") && program.isNull("season")
                    && program.has("episode") && program.isNull("episode")
                    && program.has("year") && program.isNull("year")
                    && "prime_video".equals(platform.getString("id"))
                    && "com.amazon.amazonvideo.livingroom".equals(platform.getString("androidPackage"))
                    && MultimediaDeliveryProfile.integer(target,"horizonMs",5884768,5884768)==5884768
                    && target.has("durationMs") && target.isNull("durationMs")
                    && "UNKNOWN".equals(target.getJSONObject("edition").getString("status"))
                    && "UNKNOWN".equals(target.getJSONObject("mapping").getString("status"))
                    && "com.amazon.amazonvideo.livingroom".equals(runtime.getString("targetPackage"))
                    && "prime_video".equals(runtimeMedia.getString("platform"))
                    && program.getString("programId").equals(runtimeMedia.getString("videoId"))
                    && program.getString("title").equals(runtimeMedia.getString("title"))
                    && MultimediaDeliveryProfile.integer(runtimeMedia,"durationMs",5884768,5884768)==5884768;
        }catch(Exception refused){return false;}
    }

    /** Aucune instance ne peut accorder une autorisation de restitution. */
    private ImportedTrackIdentity() {}
}
