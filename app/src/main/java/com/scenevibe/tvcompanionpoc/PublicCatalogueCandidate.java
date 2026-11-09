package com.scenevibe.tvcompanionpoc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * PARCOURS AUTONOME 001-B : adaptateur public porté dans le Companion.
 * Les ports Android assurent possession, transport et commit atomique ;
 * les parseurs v31 restent les validateurs uniques des documents et des MP3.
 * Un armement local n'est jamais une admission MEDIA ni une preuve d'affichage.
 */
final class PublicCatalogueCandidate {
    /** Ports Android du candidat public, distincts des contrôles privés historiques. */
    interface Port {
        /** Vérifie génération, consentement, révocation et disponibilité courants. */
        void validate(Binding binding) throws Exception;
        /** Indique une session active afin d'interdire le remplacement du cache. */
        boolean sessionActive();
        /** Fournit les octets bornés du paquet via le bearer de possession TV. */
        byte[] packageBytes(Binding binding) throws Exception;
        /** Lit un identifiant logique du manifeste, jamais une URL reçue du Web. */
        byte[] resource(Binding binding, String resourceId) throws Exception;
        /** Engage atomiquement le cache public vérifié, séparé du cache privé. */
        void commit(Binding binding, StudioMultimediaPackage track) throws Exception;
        /** Confirme uniquement le cache vérifié, sans ACK d'installation historique. */
        void cached(Binding binding) throws Exception;
        /** Vérifie que ce binding complet est encore celui du cache installé. */
        boolean installed(Binding binding);
        /** Conserve des préférences ; l'admission MEDIA native reste séparée. */
        void preferences(Binding binding, boolean text, boolean audio) throws Exception;
    }

    /** Coordonnées publiques immuables d'une commande, attachées à une TV et sa génération. */
    static final class Binding {
        final String device, generation, command, track, revision, digest;
        final long expires;
        final long version;
        private final String encoded;
        /** Refuse les champs supplémentaires et un profil privé ou une autre possession. */
        Binding(JSONObject value, String device, String generation, long now) throws Exception {
            JSONObject fields=new JSONObject(value.toString());fields.remove("version");
            MultimediaDeliveryProfile.closed(fields,"profile","commandId","deviceId","generation","trackId","revisionId","packageSha256","state","text","audio","expiresAt");
            if(value.has("version")){
                Object supplied=value.get("version");
                MultimediaDeliveryProfile.require(supplied instanceof Number,"PUBLIC_COMMAND_VERSION");
                double number=((Number)supplied).doubleValue();
                MultimediaDeliveryProfile.require(Double.isFinite(number) && number>=0 && number<=9007199254740991L
                        && Math.rint(number)==number,"PUBLIC_COMMAND_VERSION");
                version=((Number)supplied).longValue();
            }else version=-1;
            MultimediaDeliveryProfile.require("scenevibe.public-tv-command.v1".equals(value.get("profile")),"PUBLIC_COMMAND_PROFILE");
            MultimediaDeliveryProfile.require(java.util.Arrays.asList("requested","cached","armed","failed").contains(value.get("state"))
                    && value.get("text") instanceof Boolean && value.get("audio") instanceof Boolean,"PUBLIC_COMMAND_STATE");
            this.device=MultimediaDeliveryProfile.identifier(value.get("deviceId"));
            this.generation=MultimediaDeliveryProfile.identifier(value.get("generation"));
            MultimediaDeliveryProfile.require(this.device.equals(device) && this.generation.equals(generation),"PUBLIC_POSSESSION_MISMATCH");
            command=MultimediaDeliveryProfile.identifier(value.get("commandId"));track=MultimediaDeliveryProfile.identifier(value.get("trackId"));
            revision=MultimediaDeliveryProfile.identifier(value.get("revisionId"));digest=MultimediaDeliveryProfile.digest(value.get("packageSha256"));
            expires=Instant.parse(value.getString("expiresAt")).toEpochMilli();
            MultimediaDeliveryProfile.require(expires>now,"PUBLIC_COMMAND_EXPIRED");
            encoded=value.toString();
        }
        /** Détache le binding public sans identifiant privé ou credential. */
        JSONObject json() throws Exception {return new JSONObject(encoded);}
        /** Compare les coordonnées complètes sans confondre commande et révision éditoriale. */
        boolean same(Binding other) {return other!=null && device.equals(other.device) && generation.equals(other.generation)
                && command.equals(other.command) && track.equals(other.track) && revision.equals(other.revision) && digest.equals(other.digest);}
    }

    /** Résultat vérifié conservant les octets publics et le calendrier M5 réutilisé. */
    static final class Prepared {
        final Binding binding;
        final StudioMultimediaPackage track;
        /** Retient seulement un paquet complet déjà passé par le parseur Studio historique. */
        Prepared(Binding binding, StudioMultimediaPackage track) {this.binding=binding;this.track=track;}
    }

    private final Port port;
    private final StudioMultimediaPackage.Decoder decoder;

    /** Injecte les ports sans allouer d'autorité, de réseau ou de renderer Android. */
    PublicCatalogueCandidate(Port port, StudioMultimediaPackage.Decoder decoder) {
        if(port==null || decoder==null)throw new IllegalArgumentException("PUBLIC_PORTS_REQUIRED");
        this.port=port;this.decoder=decoder;
    }

    /** Projette l'enveloppe publique vers le validateur pur existant, sans identité de compte réelle. */
    static MultimediaDeliveryProfile profile(byte[] bytes, Binding binding) throws Exception {
        MultimediaDeliveryProfile.require(bytes!=null && bytes.length<=MultimediaDeliveryProfile.MAX_PROFILE
                && MultimediaDeliveryProfile.hash(bytes).equals(binding.digest),"PUBLIC_PACKAGE_INTEGRITY");
        JSONObject value=new JSONObject(TvFirst008Wire.utf8(bytes));
        MultimediaDeliveryProfile.closed(value,"contract","identity","trackBase64","runtimeBase64","runtimeSha256","manifestBase64","manifestSha256","resourceManifestBase64","mediaAdmissionAuthorized");
        MultimediaDeliveryProfile.require("scenevibe.public-multimedia.v1".equals(value.get("contract"))
                && Boolean.FALSE.equals(value.get("mediaAdmissionAuthorized")),"PUBLIC_PACKAGE_PROFILE");
        JSONObject id=value.getJSONObject("identity");
        MultimediaDeliveryProfile.closed(id,"trackId","channelId","revisionId","editorialRevision","snapshotSha256");
        MultimediaDeliveryProfile.require(binding.track.equals(id.get("trackId")) && binding.revision.equals(id.get("revisionId")),"PUBLIC_RELEASE_MISMATCH");
        byte[] manifest=java.util.Base64.getDecoder().decode(value.getString("resourceManifestBase64"));
        JSONObject legacyIdentity=new JSONObject().put("accountId",id.get("channelId")).put("trackId",id.get("trackId"))
                .put("editorialRevision",id.get("editorialRevision")).put("snapshotSha256",id.get("snapshotSha256"))
                .put("resourceManifestSha256",MultimediaDeliveryProfile.hash(manifest)).put("profileVersion","1.0.0");
        JSONObject legacy=new JSONObject().put("type",MultimediaDeliveryProfile.CODEC).put("version","1.0.0")
                .put("identity",legacyIdentity).put("preparationId",binding.revision).put("decisionId",binding.revision)
                .put("preparedPackageSha256",id.get("snapshotSha256")).put("mediaAdmissionAuthorized",false);
        for(String key:new String[]{"trackBase64","runtimeBase64","runtimeSha256","manifestBase64","manifestSha256","resourceManifestBase64"})legacy.put(key,value.get(key));
        // Channel occupies an INTERNAL pure-codec label only; no grant/account is created or sent.
        MultimediaDeliveryProfile validated=new MultimediaDeliveryProfile(legacy.toString().getBytes(StandardCharsets.UTF_8));
        // v31's Studio ZIP parser expects its INTERNAL content-addressed labels.
        // First verify the exact public documents; then adapt only these labels in a detached copy.
        JSONObject track=new JSONObject(TvFirst008Wire.utf8(validated.trackBytes));
        org.json.JSONArray resources=track.getJSONArray("resources");
        for(int index=0;index<resources.length();index++) {
            JSONObject resource=resources.getJSONObject(index);
            for(String kind:new String[]{"original","distributed"}) {
                JSONObject payload=resource.getJSONObject(kind);
                String hash=MultimediaDeliveryProfile.digest(payload.get("sha256"));
                MultimediaDeliveryProfile.require(("svasset:public-"+hash).equals(payload.get("storageRef")),"PUBLIC_ASSET_LABEL");
                payload.put("storageRef","svasset:"+hash);
            }
        }
        byte[] internal=track.toString().getBytes(StandardCharsets.UTF_8);
        legacy.put("trackBase64",java.util.Base64.getEncoder().encodeToString(internal));
        legacyIdentity.put("snapshotSha256",MultimediaDeliveryProfile.hash(internal));
        return new MultimediaDeliveryProfile(legacy.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Télécharge et valide l'ensemble ; une panne ou session active interdit le commit. */
    Prepared download(Binding binding) throws Exception {
        port.validate(binding);
        MultimediaDeliveryProfile.require(!port.sessionActive(),"PUBLIC_ACTIVE_SESSION");
        MultimediaDeliveryProfile profile=profile(port.packageBytes(binding),binding);
        Map<String,byte[]> physical=new HashMap<>();
        for(MultimediaDeliveryProfile.Resource resource:profile.resources) {
            port.validate(binding);
            byte[] bytes=port.resource(binding,resource.id);
            MultimediaDeliveryProfile.require(bytes!=null && bytes.length==resource.byteLength
                    && MultimediaDeliveryProfile.hash(bytes).equals(resource.sha256),"PUBLIC_MP3_INTEGRITY");
            physical.put(resource.sha256,bytes);
        }
        StudioMultimediaPackage track=profile.prepare(physical,decoder);
        port.validate(binding);
        MultimediaDeliveryProfile.require(!port.sessionActive(),"PUBLIC_ACTIVE_SESSION");
        port.commit(binding,track);port.cached(binding);
        return new Prepared(binding,track);
    }

    /** Applique automatiquement les préférences Web après revalidation du cache et de la possession. */
    void arm(Prepared prepared, boolean text, boolean audio) throws Exception {
        MultimediaDeliveryProfile.require(prepared!=null && port.installed(prepared.binding)
                && !port.sessionActive(),"PUBLIC_CURRENT_CACHE_REQUIRED");
        port.validate(prepared.binding);
        port.preferences(prepared.binding,text,audio);
    }
}
