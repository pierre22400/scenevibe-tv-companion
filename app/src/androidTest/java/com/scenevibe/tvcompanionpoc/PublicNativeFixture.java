package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fixture réseau synthétique, explicitement distincte du fournisseur Cloud.
 * Les vrais AndroidKeyStore, MediaCodec, fichiers et moteurs sont conservés.
 * Les paquets publics proviennent de la réception PostgreSQL/Studio 001.
 */
final class PublicNativeFixture implements PublicCloudClient.Transport {
    final Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
    final String namespace;
    final TvFirst008State state;
    final byte[] profile;
    final JSONObject publicIdentity;
    final java.util.Map<String,byte[]> resources=new java.util.HashMap<>();
    final java.util.Map<String,String> resourceIds=new java.util.HashMap<>();
    String device=UUID.randomUUID().toString(),generation=UUID.randomUUID().toString();
    String token="PUBLIC_FIXTURE_POSSESSION_"+UUID.randomUUID().toString().replace("-","");
    String challengeId,secret,code;
    final String grantExpires=Instant.now().plusSeconds(86400).toString();
    boolean claimed,expired,revoked,corrupt,truncated,withdrawn;
    int audioRequests,cacheReceipts,armReceipts,claims;
    JSONObject command;
    private PublicCompanionOwner owner;

    /** Le namespace fixe n'est utilisé que par les deux phases de redémarrage de l'émulateur. */
    PublicNativeFixture(String namespace) throws Exception {
        this.namespace=namespace;
        state=new TvFirst008State(target.getSharedPreferences(namespace,0),new SecretStore.AndroidKeyStoreSecretStore(target));
        profile=asset("public001b/package.json");JSONObject p=new JSONObject(new String(profile,StandardCharsets.UTF_8));publicIdentity=p.getJSONObject("identity");
        JSONArray manifest=new JSONArray(new String(java.util.Base64.getDecoder().decode(p.getString("resourceManifestBase64")),StandardCharsets.UTF_8));
        for(int i=0;i<manifest.length();i++){
            JSONObject resource=manifest.getJSONObject(i);String hash=resource.getString("sha256");
            resources.put(hash,ManualNativeFixture.asset(hash+".mp3"));resourceIds.put(resource.getString("resourceId"),hash);
        }
        JSONObject saved=state.read("identity");
        if(saved!=null){device=saved.getString("deviceId");generation=saved.getString("generation");token=state.token(saved);claimed=true;}
    }
    /** Chaque test ordinaire possède un espace Android neuf, distinct des données privées. */
    PublicNativeFixture() throws Exception {this("public001b_test_"+UUID.randomUUID());}
    /** Lit les seuls octets synthétiques du paquet de test, sans fallback démo. */
    private static byte[] asset(String name) throws Exception {
        try(java.io.InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);return bytes.toByteArray();
        }
    }
    /** Compose le vrai propriétaire Android avec sa seule frontière réseau remplacée. */
    PublicCompanionOwner owner(){if(owner==null)owner=new PublicCompanionOwner(target,state,this);return owner;}
    /** Reproduit l'unique saisie Web ; un code faux, expiré ou consommé ne vaut pas association. */
    synchronized void claim(String value){
        if(expired || claimed || code==null || !code.equals(value))throw new IllegalArgumentException("CODE_REFUSED");claimed=true;claims++;
    }
    /** Exécute le handshake automatique sans aucune validation TV après le code. */
    void pair() throws Exception {owner().pollOnce();claim(owner().code());owner().pollOnce();owner().pollOnce();}
    /** Le choix de Track représente l'action Web et fixe une révision publique. */
    synchronized void select() throws Exception {
        command=new JSONObject().put("profile","scenevibe.public-tv-command.v1").put("commandId",UUID.randomUUID().toString())
                .put("deviceId",device).put("generation",generation).put("trackId",publicIdentity.getString("trackId"))
                .put("revisionId",publicIdentity.getString("revisionId")).put("packageSha256",MultimediaDeliveryProfile.hash(profile))
                .put("state","requested").put("text",false).put("audio",false).put("expiresAt",Instant.now().plusSeconds(900).toString());
    }
    /** Les préférences sont une action Web, sans confirmation locale du spectateur. */
    synchronized void activate(boolean text,boolean audio) throws Exception {command.put("text",text).put("audio",audio).put("state","cached");}
    /** Ouvre la même implémentation de cache sur les vrais fichiers/SharedPreferences de la génération. */
    PublicMultimediaCache cache() throws Exception {return new PublicMultimediaCache(target,generation);}
    /** Télécharge par le propriétaire de production et confirme uniquement après commit. */
    void download() throws Exception {select();owner().pollOnce();}

    /** Émule les contrats Cloud sans générer de compte humain ou de référence privée. */
    @Override public synchronized byte[] request(String action,JSONObject body,String bearer) throws Exception {
        if(action.equals("challenge")){
            challengeId=body.getString("challengeId");secret=body.getString("challengeSecret");code=String.format(java.util.Locale.ROOT,"%06d",100000+(claims*13+challengeId.hashCode()&0x7fffffff)%900000);
            return bytes(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("challengeId",challengeId).put("userCode",code).put("expiresAt",Instant.now().plusSeconds(120).toString()));
        }
        if(action.equals("challenge-status")){
            if(!java.util.Objects.equals(challengeId,body.optString("challengeId")) || !java.util.Objects.equals(secret,body.optString("challengeSecret")))throw new IllegalArgumentException("POSSESSION_REFUSED");
            if(expired){expired=false;return bytes(new JSONObject().put("status","expired"));}
            if(!claimed)return bytes(new JSONObject().put("status","open"));
            return bytes(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("status","claimed").put("deviceId",device).put("generation",generation).put("deviceToken",token)
                    .put("consentExpiresAt",grantExpires));
        }
        if(revoked)throw new IllegalArgumentException("REVOKED");
        if(!claimed || !device.equals(body.optString("deviceId")) || !generation.equals(body.optString("generation")) || !token.equals(bearer))throw new IllegalArgumentException("POSSESSION_REFUSED");
        if(action.equals("association"))return bytes(new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("deviceId",device).put("generation",generation)
                .put("consentExpiresAt",grantExpires));
        if(action.equals("poll"))return bytes(new JSONObject().put("commands",command==null?new JSONArray():new JSONArray().put(new JSONObject(command.toString()))));
        if(withdrawn)throw new IllegalArgumentException("PUBLIC_WITHDRAWN");
        if(action.equals("validate-cache")){
            if(!publicIdentity.getString("trackId").equals(body.getString("trackId")) || !publicIdentity.getString("revisionId").equals(body.getString("revisionId"))
                    || !MultimediaDeliveryProfile.hash(profile).equals(body.getString("packageSha256")))throw new IllegalArgumentException("PUBLIC_BINDING_REFUSED");
            return bytes(new JSONObject().put("status","VALIDATED").put("deviceId",device).put("generation",generation).put("trackId",body.get("trackId"))
                    .put("revisionId",body.get("revisionId")).put("packageSha256",body.get("packageSha256")));
        }
        if(command==null || !command.getString("commandId").equals(body.optString("commandId")))throw new IllegalArgumentException("COMMAND_REFUSED");
        if(action.equals("package"))return truncated?java.util.Arrays.copyOf(profile,profile.length/2):profile.clone();
        if(action.equals("device-preferences")){command.put("text",body.getBoolean("text")).put("audio",body.getBoolean("audio"));return bytes(new JSONObject(command.toString()));}
        if(action.equals("resource")){
            String hash=resourceIds.get(body.getString("resourceId"));if(hash==null)throw new IllegalArgumentException("RESOURCE_REFUSED");
            byte[] data=resources.get(hash).clone();audioRequests++;if(corrupt)data[0]^=1;return data;
        }
        if(action.equals("receipt")){
            if(!command.get("revisionId").equals(body.get("revisionId")) || !command.get("packageSha256").equals(body.get("packageSha256")))throw new IllegalArgumentException("RECEIPT_REFUSED");
            String status=body.getString("state");
            if(status.equals("cached") && command.getString("state").equals("requested"))command.put("expiresAt",grantExpires);
            command.put("state",status);if(status.equals("cached"))cacheReceipts++;if(status.equals("armed"))armReceipts++;
            return bytes(new JSONObject(command.toString()));
        }
        throw new IllegalArgumentException("PUBLIC_FIXTURE_ROUTE_REFUSED");
    }
    /** Encodage strict du port de contrôle, jamais sérialisation d'un credential dans un rapport. */
    private static byte[] bytes(JSONObject value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    /** Ferme seulement le worker et les données synthétiques de ce test. */
    void close(boolean remove) throws Exception {
        if(owner!=null)owner.close();
        if(remove){ManualNativeFixture.remove(new java.io.File(target.getFilesDir(),"public001b/"+generation));target.getSharedPreferences(namespace,0).edit().clear().commit();target.getSharedPreferences("public001b_cache_"+generation.replace("-",""),0).edit().clear().commit();}
    }
}
