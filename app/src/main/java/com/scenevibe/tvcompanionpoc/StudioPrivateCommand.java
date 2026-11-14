package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;

/**
 * Demande Studio Q2 authentifiée par la génération de la TV. Elle référence
 * le paquet à récupérer et son identifiant d'intention, sans admission MEDIA.
 */
final class StudioPrivateCommand {
    final String id, publication, title;
    final long revision;
    final Long expectedRevision;
    final boolean cacheConfirmed;

    /** Décode uniquement la forme fermée du suivi Q2. */
    StudioPrivateCommand(JSONObject reply) throws Exception {
        MultimediaDeliveryProfile.closed(reply,"command");
        if(reply.isNull("command")) {
            id=null; publication=null; title=""; revision=0; expectedRevision=null; cacheConfirmed=false;
            return;
        }
        JSONObject value=reply.getJSONObject("command");
        MultimediaDeliveryProfile.closed(value,"requestId","publicationId","title","revision","expectedRevision","cacheConfirmed");
        id=TvFirst008Wire.uuid(value.getString("requestId"));
        publication=TvFirst008Wire.uuid(value.getString("publicationId"));
        cacheConfirmed=value.getBoolean("cacheConfirmed");
        title=value.getString("title");
        revision=MultimediaDeliveryProfile.integer(value,"revision",1,2_147_483_647L);
        expectedRevision=value.isNull("expectedRevision")?null:MultimediaDeliveryProfile.integer(value,"expectedRevision",0,9_007_199_254_740_991L);
        MultimediaDeliveryProfile.require(!title.trim().isEmpty() && title.length()<=500,"Q2_TITLE_INVALID");
    }

    /** Lie la progression au numéro de demande exact et au credential du transport. */
    JSONObject progress(String state) throws Exception {
        return new JSONObject().put("requestId",id).put("state",state);
    }
}
