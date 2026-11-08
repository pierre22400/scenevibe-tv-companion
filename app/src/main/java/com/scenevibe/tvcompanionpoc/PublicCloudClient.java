package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Transport public anonyme côté humain, authentifié par possession côté TV.
 * Le bearer provient de TV-FIRST et reste dans AndroidKeyStore. Aucun cookie
 * humain, compte navigateur, redirect ou relâchement TLS n'est introduit.
 */
final class PublicCloudClient {
    /** Seul le transport réseau peut être remplacé dans les tests, pas les validateurs. */
    interface Transport {
        /** Retourne des octets bornés ; les erreurs ne doivent contenir aucun secret. */
        byte[] request(String action,JSONObject body,String token) throws Exception;
    }
    private final TvFirst008State state;
    private final Transport transport;

    /** Réutilise l'identité chiffrée 008 dans son namespace public dédié. */
    PublicCloudClient(TvFirst008State state,Transport transport){this.state=state;this.transport=transport;}

    /** Implémentation HTTPS de l'APK signé : origine exacte, TLS natif, budgets et délais. */
    static final class Https implements Transport {
        private final String origin;
        /** Refuse une origine manquante, un chemin ou un port non qualifié avant tout socket. */
        Https(String origin){
            if(origin==null || !origin.matches("https://[A-Za-z0-9.-]+") || URI.create(origin).getHost()==null)
                throw new IllegalArgumentException("PUBLIC_CLOUD_UNCONFIGURED");
            this.origin=origin;
        }
        /** Envoie une action fermée et vérifie MIME, taille, longueur et octets chunked. */
        @Override public byte[] request(String action,JSONObject body,String token) throws Exception {
            if(!action.matches("challenge|challenge-status|association|poll|package|resource|receipt|validate-cache"))throw new IllegalArgumentException("PUBLIC_ROUTE_REFUSED");
            byte[] request=body.toString().getBytes(StandardCharsets.UTF_8);
            if(request.length>16_384)throw new IllegalArgumentException("PUBLIC_REQUEST_BUDGET");
            HttpsURLConnection connection=(HttpsURLConnection)URI.create(origin+"/api/autonomous/"+action).toURL().openConnection();
            try{
                connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(15_000);connection.setReadTimeout(60_000);
                connection.setRequestProperty("Origin",origin);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept-Encoding","identity");
                if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
                connection.setDoOutput(true);connection.setFixedLengthStreamingMode(request.length);
                try(java.io.OutputStream output=connection.getOutputStream()){output.write(request);}
                if(connection.getResponseCode()!=200)throw new IllegalArgumentException("PUBLIC_HTTP_"+connection.getResponseCode());
                String expected=action.equals("resource")?"audio/mpeg":action.equals("package")?"application/vnd.scenevibe.public-multimedia+json":"application/json";
                String mime=connection.getContentType();if(mime==null || !expected.equals(mime.split(";",2)[0].trim()))throw new IllegalArgumentException("PUBLIC_HTTP_MIME");
                int maximum=action.equals("resource")?MultimediaDeliveryProfile.MAX_AUDIO:MultimediaDeliveryProfile.MAX_PROFILE;
                long length=connection.getContentLengthLong();if(length < -1 || length>maximum)throw new IllegalArgumentException("PUBLIC_HTTP_BUDGET");
                try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;){
                        if(Thread.currentThread().isInterrupted() || bytes.size()+n>maximum)throw new IllegalArgumentException("PUBLIC_DOWNLOAD_CANCELLED");bytes.write(buffer,0,n);
                    }
                    if(length>=0 && bytes.size()!=length)throw new IllegalArgumentException("PUBLIC_HTTP_TRUNCATED");return bytes.toByteArray();
                }
            }finally{connection.disconnect();}
        }
    }

    /** Expose seulement une copie de la génération et l'identifiant d'appareil, jamais le bearer. */
    JSONObject identity(){return state.read("identity");}
    /** Ajoute la possession à chaque requête sans permettre à un appelant de la remplacer. */
    byte[] bytes(String action,JSONObject extra) throws Exception {
        JSONObject id=identity();if(id==null)throw new IllegalStateException("PUBLIC_PAIR_REQUIRED");
        JSONObject body=new JSONObject(extra.toString()).put("deviceId",id.getString("deviceId")).put("generation",id.getString("generation"));
        return transport.request(action,body,state.token(id));
    }
    /** Décode strictement les réponses de contrôle, séparément des paquets et des MP3. */
    JSONObject json(String action,JSONObject extra) throws Exception {return new JSONObject(TvFirst008Wire.utf8(bytes(action,extra)));}
    /** Démarre/reprend le même challenge chiffré jusqu'à son expiration explicite. */
    JSONObject challenge() throws Exception {
        JSONObject value=state.challenge("new");JSONObject body=new JSONObject(value.toString()).put("profile",TvFirst008Wire.PROFILE).put("challengeSecret",state.challengeSecret(value));
        JSONObject result=new JSONObject(TvFirst008Wire.utf8(transport.request("challenge",body,null)));
        if(!value.getString("challengeId").equals(result.optString("challengeId")) || !result.optString("userCode").matches("[0-9]{6}"))throw new IllegalArgumentException("PUBLIC_CHALLENGE_REFUSED");
        state.write("displayChallenge",result);return result;
    }
    /** Seule la possession du secret TV reçoit le credential après saisie du code Web. */
    boolean finishChallenge() throws Exception {
        JSONObject c=state.read("challenge");if(c==null)return identity()!=null;
        JSONObject body=new JSONObject().put("challengeId",c.getString("challengeId")).put("challengeSecret",state.challengeSecret(c));
        JSONObject result=new JSONObject(TvFirst008Wire.utf8(transport.request("challenge-status",body,null)));
        if("expired".equals(result.optString("status"))){state.write("challenge",null);state.write("displayChallenge",null);return false;}
        if(!"claimed".equals(result.optString("status")))return false;
        state.claimed(result);state.write("publicGrant",new JSONObject().put("expiresAt",result.getString("consentExpiresAt")));
        state.write("displayChallenge",null);return true;
    }
    /** Valide toute la liaison après redémarrage, sans redemander un code ni consentement utilisateur. */
    void validate(PublicCatalogueCandidate.Binding binding) throws Exception {
        JSONObject result=json("validate-cache",new JSONObject().put("trackId",binding.track).put("revisionId",binding.revision).put("packageSha256",binding.digest));
        if(!"VALIDATED".equals(result.optString("status")) || !binding.device.equals(result.optString("deviceId"))
                || !binding.generation.equals(result.optString("generation")) || !binding.track.equals(result.optString("trackId"))
                || !binding.revision.equals(result.optString("revisionId")) || !binding.digest.equals(result.optString("packageSha256")))throw new IllegalArgumentException("PUBLIC_CACHE_AUTHORITY_REFUSED");
    }
    /** Une commande ne devient un reçu qu'après le vrai commit local ou les préférences appliquées. */
    void receipt(PublicCatalogueCandidate.Binding binding,String status) throws Exception {
        JSONObject value=json("receipt",new JSONObject().put("commandId",binding.command).put("revisionId",binding.revision).put("packageSha256",binding.digest).put("state",status));
        PublicCatalogueCandidate.Binding received=new PublicCatalogueCandidate.Binding(value,binding.device,binding.generation,0);
        if(!binding.same(received) || !status.equals(value.optString("state")))throw new IllegalArgumentException("PUBLIC_RECEIPT_MISMATCH");
    }
}
