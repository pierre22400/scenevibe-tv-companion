package com.scenevibe.tvcompanionpoc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Contrats publics purs : mêmes octets que la réception native, parseurs v31
 * réutilisés. Le décodeur est un port de faits de fixture, jamais une preuve
 * de MediaCodec ; cette preuve appartient exclusivement aux tests Android.
 */
public final class PublicCatalogueCandidateTest {
    private Port port;
    private PublicCatalogueCandidate candidate;
    private JSONObject command;
    private final String device=UUID.randomUUID().toString(),generation=UUID.randomUUID().toString();
    /** Recharge un paquet immuable avant chaque scénario, sans port Android ou fournisseur. */
    @Before public void before() throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of("src/androidTest/assets/public001b/package.json"));JSONObject identity=new JSONObject(TvFirst008Wire.utf8(bytes)).getJSONObject("identity");
        command=new JSONObject().put("profile","scenevibe.public-tv-command.v1").put("commandId",UUID.randomUUID().toString()).put("deviceId",device).put("generation",generation)
                .put("trackId",identity.get("trackId")).put("revisionId",identity.get("revisionId")).put("packageSha256",MultimediaDeliveryProfile.hash(bytes))
                .put("state","requested").put("text",false).put("audio",false).put("expiresAt",Instant.now().plusSeconds(900).toString());
        port=new Port(bytes);candidate=new PublicCatalogueCandidate(port,data->3000);
    }
    /** Produit un binding de commande neuf, séparé d'une éventuelle affectation privée. */
    private PublicCatalogueCandidate.Binding binding() throws Exception {return new PublicCatalogueCandidate.Binding(command,device,generation,System.currentTimeMillis());}
    /** Refuse une action sans confondre son échec avec une réussite partielle. */
    private void refuse(Action action) throws Exception {try{action.run();fail("Expected refusal");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}}
    /** Action de contrat pouvant traverser le parseur fermé. */
    interface Action {/** Exécute une seule opération du candidat. */void run() throws Exception;}
    /** Port de qualification sans Service/renderer ni commit applicatif réel. */
    private static final class Port implements PublicCatalogueCandidate.Port {
        byte[] bytes;boolean active,revoked,installed,preferences,corrupt;int commits,receipts;
        /** Conserve les octets du scénario. */
        Port(byte[] bytes){this.bytes=bytes;}
        /** Contrôle une révocation synthétique indépendante des digests. */
        public void validate(PublicCatalogueCandidate.Binding b){if(revoked)throw new IllegalArgumentException("REVOKED");}
        /** Modélise une restitution déjà active. */
        public boolean sessionActive(){return active;}
        /** Fournit une copie du paquet, éventuellement altéré par le scénario. */
        public byte[] packageBytes(PublicCatalogueCandidate.Binding b){return bytes.clone();}
        /** Résout le manifeste exact puis les seuls fichiers synthétiques de la v31. */
        public byte[] resource(PublicCatalogueCandidate.Binding b,String id) throws Exception {
            JSONArray rows=new JSONArray(new String(java.util.Base64.getDecoder().decode(new JSONObject(TvFirst008Wire.utf8(bytes)).getString("resourceManifestBase64")),java.nio.charset.StandardCharsets.UTF_8));
            for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("resourceId"))){
                byte[] data=Files.readAllBytes(Path.of("src/androidTest/assets/realtrack001/"+rows.getJSONObject(i).getString("sha256")+".mp3"));if(corrupt)data[0]^=1;return data;
            }throw new IllegalArgumentException("RESOURCE_MISSING");
        }
        /** Observe le commit seulement après validation de tous les octets. */
        public void commit(PublicCatalogueCandidate.Binding b,StudioMultimediaPackage t){commits++;installed=true;}
        /** Observe un reçu distinct de toute permission MEDIA. */
        public void cached(PublicCatalogueCandidate.Binding b){receipts++;}
        /** Lie l'armement automatique au cache technique actuel. */
        public boolean installed(PublicCatalogueCandidate.Binding b){return installed;}
        /** Observe les préférences, sans paramètre de consentement humain. */
        public void preferences(PublicCatalogueCandidate.Binding b,boolean text,boolean audio){preferences=text&&audio;}
    }
    /** La nouvelle enveloppe réutilise vraiment le parseur Studio et ses six événements. */
    @Test public void completePublicPackageReusesOriginalParsers() throws Exception {var p=candidate.download(binding());assertEquals(6,p.track.calendar.events().size());assertEquals(3,p.track.audio.size());assertEquals(1,port.commits);assertEquals(1,port.receipts);}
    /** L'activation Web ne requiert aucun deuxième consentement local. */
    @Test public void currentCacheAppliesPreferencesWithoutLocalConfirmation() throws Exception {var p=candidate.download(binding());candidate.arm(p,true,true);assertTrue(port.preferences);}
    /** Une possession étrangère ne devient jamais un binding public valide. */
    @Test public void foreignGenerationIsRefused() throws Exception {command.put("generation",UUID.randomUUID().toString());refuse(()->binding());}
    /** L'expiration borne l'installation, pas le film en cours ni les archives. */
    @Test public void expiredInstallCommandIsRefused() throws Exception {command.put("expiresAt","2000-01-01T00:00:00Z");refuse(()->binding());}
    /** Une corruption d'enveloppe ne produit ni commit ni reçu. */
    @Test public void corruptPackageNeverCommits() throws Exception {var b=binding();port.bytes[0]^=1;refuse(()->candidate.download(b));assertEquals(0,port.commits);assertEquals(0,port.receipts);}
    /** Un MP3 corrompu empêche l'admission de tout l'ensemble. */
    @Test public void corruptAudioNeverCommits() throws Exception {port.corrupt=true;refuse(()->candidate.download(binding()));assertEquals(0,port.commits);}
    /** Une session active conserve son cache immuable. */
    @Test public void activeSessionPreventsReplacement() throws Exception {port.active=true;refuse(()->candidate.download(binding()));assertEquals(0,port.commits);}
    /** Le grant reste vérifié après l'unique association, sans nouvelle action humaine. */
    @Test public void revokedPossessionPreventsPreferences() throws Exception {var p=candidate.download(binding());port.revoked=true;refuse(()->candidate.arm(p,true,true));assertFalse(port.preferences);}
}
