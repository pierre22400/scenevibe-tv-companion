package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Cache public séparé du privé, utilisant le même InstallationStore atomique.
 * Chaque ensemble est complet et immuable avant publication de son pointeur.
 * Un échec laisse le précédent ensemble intact ; aucun ACK historique n'est créé.
 */
final class PublicMultimediaCache {
    static final String CODEC="scenevibe.public-multimedia-cache.v1";
    private final File root;
    private final InstallationStore store;
    private final StudioMultimediaPackage.Decoder decoder;
    private final MultimediaCacheDirectories directories;

    private final MultimediaCacheDirectories.Writer writer;

    /** Paquet public entièrement relu, jamais une affectation privée fabriquée. */
    static final class Prepared {
        final PublicCatalogueCandidate.Binding binding;
        final StudioMultimediaPackage track;
        final MultimediaDeliveryProfile profile;
        private final File directory;
        private final Runnable collect;
        /** Conserve séparément les coordonnées publiques et l'adaptation interne vérifiée. */
        Prepared(PublicCatalogueCandidate.Binding binding,StudioMultimediaPackage track,MultimediaDeliveryProfile profile,File directory,Runnable collect){
            this.binding=binding;this.track=track;this.profile=profile;this.directory=directory;this.collect=collect;
        }
        /** Épingle les MP3 avant la construction du lecteur MEDIA. */
        MultimediaCacheDirectories.Lease pin() {
            return MultimediaCacheDirectories.at(directory.getParentFile()).pin(directory,collect);
        }
        /** Revérifie le fichier adressé par contenu juste avant chaque lecture AUDIO native. */
        File resource(String id) throws Exception {
            for(MultimediaDeliveryProfile.Resource resource:profile.resources)if(resource.id.equals(id)){
                File file=new File(directory,resource.sha256+".mp3");byte[] bytes=read(file,MultimediaDeliveryProfile.MAX_AUDIO);
                MultimediaDeliveryProfile.require(bytes.length==resource.byteLength && MultimediaDeliveryProfile.hash(bytes).equals(resource.sha256),"PUBLIC_MP3_INTEGRITY");return file;
            }
            throw new IllegalArgumentException("PUBLIC_RESOURCE_MISSING");
        }
    }

    /** Compose un namespace Android public par génération sans lire ni modifier le cache privé. */
    PublicMultimediaCache(Context context,String generation) throws Exception {
        this(new File(context.getFilesDir(),"public001b/"+TvFirst008Wire.uuid(generation)),
                new InstallationStore(new AndroidInstallationBackend(context,"public001b_cache_"+generation.replace("-",""))),new AndroidStudioAudioInspector(context));
    }
    /** Port de stockage partagé avec les bancs JVM ; les règles et parseurs restent identiques. */
    PublicMultimediaCache(File root,InstallationStore store,StudioMultimediaPackage.Decoder decoder) throws Exception {
        this(root,store,decoder,PublicMultimediaCache::write);
    }

    /** Utilise le même parcours atomique avec une frontière d’écriture instrumentable. */
    PublicMultimediaCache(File root,InstallationStore store,StudioMultimediaPackage.Decoder decoder,
            MultimediaCacheDirectories.Writer writer) throws Exception {
        this.writer=writer;
        if(!root.isDirectory() && !root.mkdirs())throw new IllegalArgumentException("PUBLIC_CACHE_UNAVAILABLE");
        MultimediaDeliveryProfile.require(!Files.isSymbolicLink(root.toPath()),"PUBLIC_CACHE_SYMLINK");
        this.root=root.getCanonicalFile();this.store=store;this.decoder=decoder;
        directories=MultimediaCacheDirectories.at(this.root);
        InstallationStore.ReadResult current=store.read();
        MultimediaDeliveryProfile.require(current.state()!=InstallationStore.ReadState.CORRUPT && current.acknowledgedRevision()==0
                && (current.snapshot()==null || CODEC.equals(current.snapshot().codecId())),"PUBLIC_CACHE_NAMESPACE");
    }

    /** Exécute le candidat porté avec transport réel, validation native et commit Android atomique. */
    Prepared download(PublicCloudClient client,PublicCatalogueCandidate.Binding binding,java.util.function.BooleanSupplier active) throws Exception {
        synchronized(directories.writer){
            if(directories.recoveryUncertain)throw new IllegalStateException("PUBLIC_CACHE_ROLLBACK_FAILED");
            collect();
            byte[][] raw={null};
            PublicCatalogueCandidate candidate=new PublicCatalogueCandidate(new PublicCatalogueCandidate.Port(){
                /** Recontrôle le grant et la release à chaque frontière. */
                public void validate(PublicCatalogueCandidate.Binding b) throws Exception {client.validate(b);}
                /** Une session en cours interdit tout remplacement. */
                public boolean sessionActive(){return active.getAsBoolean();}
                /** Télécharge les octets originaux publics pour leur conservation exacte. */
                public byte[] packageBytes(PublicCatalogueCandidate.Binding b) throws Exception {
                    return raw[0]=client.bytes("package",new JSONObject().put("commandId",b.command));
                }
                /** Le manifeste choisit l'identifiant logique, jamais un chemin HTTP libre. */
                public byte[] resource(PublicCatalogueCandidate.Binding b,String id) throws Exception {
                    return client.bytes("resource",new JSONObject().put("commandId",b.command).put("resourceId",id));
                }
                /** Publie seulement un ensemble entièrement vérifié. */
                public void commit(PublicCatalogueCandidate.Binding b,StudioMultimediaPackage track) throws Exception {install(b,raw[0],track);}
                /** Le reçu suit le commit et sa relecture, sans conférer MEDIA. */
                public void cached(PublicCatalogueCandidate.Binding b) throws Exception {client.receipt(b,"cached");}
                /** La comparaison porte sur le binding complet du pointeur installé. */
                public boolean installed(PublicCatalogueCandidate.Binding b){return matches(b);}
                /** Les préférences sont appliquées par le propriétaire de service Android. */
                public void preferences(PublicCatalogueCandidate.Binding b,boolean text,boolean audio){throw new IllegalStateException("ANDROID_OWNER_REQUIRED");}
            },decoder);
            try {
                candidate.download(binding);
                return read();
            } finally {
                collect();
            }
        }
    }

    /** Écrit, fsync et relit un ensemble avant le commit du pointeur générique. */
    private void install(PublicCatalogueCandidate.Binding binding,byte[] bytes,StudioMultimediaPackage track) throws Exception {
        InstallationStore.ReadResult before=store.read();
        MultimediaDeliveryProfile.require(before.state()!=InstallationStore.ReadState.CORRUPT,"PUBLIC_CACHE_CORRUPT");
        String name="set-"+UUID.randomUUID().toString().replace("-","");File directory=new File(root,name+".partial");
        byte[] bindingBytes=binding.json().toString().getBytes(StandardCharsets.UTF_8);
        MultimediaDeliveryProfile profile=PublicCatalogueCandidate.profile(bytes,binding);
        directories.requireSpace(profile,bytes.length+bindingBytes.length);
        if(!directory.mkdir())throw new IllegalArgumentException("PUBLIC_STAGE_UNAVAILABLE");
        writer.write(new File(directory,"package.json"),bytes);writer.write(new File(directory,"binding.json"),bindingBytes);
        java.util.Set<String> written=new java.util.HashSet<>();
        for(MultimediaDeliveryProfile.Resource resource:profile.resources) {
            if(written.add(resource.sha256))writer.write(new File(directory,resource.sha256+".mp3"),track.resource(resource.id));
        }
        readSet(directory,binding);
        File complete=new File(root,name);Files.move(directory.toPath(),complete.toPath(),StandardCopyOption.ATOMIC_MOVE);
        long revision=before.snapshot()==null?1:before.snapshot().revision()+1;
        JSONObject descriptor=new JSONObject().put("format",CODEC).put("directory",name).put("binding",binding.json());
        InstallRequest request=new InstallRequest(revision,CODEC,Collections.singletonMap("cache",descriptor.toString().getBytes(StandardCharsets.UTF_8)));
        MultimediaDeliveryProfile.require(store.commit(new InstallationSnapshot(request,CODEC))==InstallationStore.CommitState.COMMITTED,"PUBLIC_POINTER_COMMIT_FAILED");
        try {
            Prepared verified=readPointer(store.read());
            MultimediaDeliveryProfile.require(verified!=null && binding.same(verified.binding),"PUBLIC_POINTER_READBACK_FAILED");
        } catch(Exception failedReadback) {
            boolean restored=before.snapshot()==null?store.clearAll():store.commit(before.snapshot())==InstallationStore.CommitState.COMMITTED;
            if(!restored) {
                directories.recoveryUncertain=true;
                throw new IllegalStateException("PUBLIC_CACHE_ROLLBACK_FAILED",failedReadback);
            }
            throw failedReadback;
        }
    }

    /** Relit intégralement le cache au démarrage, avec MediaCodec via le port existant. */
    Prepared read() throws Exception {
        synchronized(directories.writer){
            Prepared prepared=readPointer(store.read());
            if(!directories.recoveryUncertain)directories.collect(prepared==null?null:prepared.directory,true);
            return prepared;
        }
    }

    /** Préserve tout sur un pointeur corrompu ou ambigu. */
    private void collect() {
        synchronized(directories.writer) {
            if(directories.recoveryUncertain)return;
            try {
                directories.collect(pointedDirectory(store.read()),true);
            } catch(Exception refused) {
                // Conserver les dossiers tant que le pointeur ne peut être établi.
            }
        }
    }

    /** Relit uniquement le descripteur durable avant de sélectionner un dossier. */
    private File pointedDirectory(InstallationStore.ReadResult result) throws Exception {
        MultimediaDeliveryProfile.require(result.state()!=InstallationStore.ReadState.CORRUPT && result.acknowledgedRevision()==0,"PUBLIC_CACHE_CORRUPT");
        if(result.state()==InstallationStore.ReadState.EMPTY)return null;
        InstallationSnapshot snapshot=result.snapshot();
        MultimediaDeliveryProfile.require(CODEC.equals(snapshot.codecId()) && CODEC.equals(snapshot.handlerId())
                && snapshot.canonical().artifactCount()==1,"PUBLIC_CACHE_PROFILE");
        JSONObject descriptor=new JSONObject(TvFirst008Wire.utf8(snapshot.canonical().artifact("cache")));
        MultimediaDeliveryProfile.closed(descriptor,"format","directory","binding");
        String directory=descriptor.getString("directory");
        MultimediaDeliveryProfile.require(CODEC.equals(descriptor.getString("format")) && MultimediaCacheDirectories.isSet(directory),"PUBLIC_CACHE_DIRECTORY");
        File selected=new File(root,directory);
        MultimediaDeliveryProfile.require(Files.isDirectory(selected.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS),"PUBLIC_CACHE_DIRECTORY");
        return selected;
    }

    /** Vérifie les données publiées par le pointeur sans collecter pendant un retour arrière. */
    private Prepared readPointer(InstallationStore.ReadResult result) throws Exception {
        File directory=pointedDirectory(result);
        if(directory==null)return null;
        JSONObject b=new JSONObject(TvFirst008Wire.utf8(result.snapshot().canonical().artifact("cache"))).getJSONObject("binding");
        // Expiration bounds the INSTALL command; it cannot erase content or shorten a movie session.
        PublicCatalogueCandidate.Binding binding=new PublicCatalogueCandidate.Binding(b,b.getString("deviceId"),b.getString("generation"),0);
        return readSet(directory,binding);
    }

    /** Vérifie tous les octets sans toucher au pointeur ou aux préférences. */
    private Prepared readSet(File directory,PublicCatalogueCandidate.Binding binding) throws Exception {
        byte[] bytes=read(new File(directory,"package.json"),MultimediaDeliveryProfile.MAX_PROFILE);
        JSONObject stored=new JSONObject(TvFirst008Wire.utf8(read(new File(directory,"binding.json"),16_384)));
        MultimediaDeliveryProfile.require(binding.same(new PublicCatalogueCandidate.Binding(stored,binding.device,binding.generation,0)),"PUBLIC_CACHE_BINDING");
        MultimediaDeliveryProfile profile=PublicCatalogueCandidate.profile(bytes,binding);Map<String,byte[]> physical=new HashMap<>();
        for(MultimediaDeliveryProfile.Resource resource:profile.resources)physical.put(resource.sha256,read(new File(directory,resource.sha256+".mp3"),MultimediaDeliveryProfile.MAX_AUDIO));
        return new Prepared(binding,profile.prepare(physical,decoder),profile,directory,this::collect);
    }

    /** Contrôle léger du pointeur courant pour chaque effet, sans décoder les MP3 sur le thread UI. */
    boolean matches(PublicCatalogueCandidate.Binding binding){
        synchronized(directories.writer){try{
            InstallationStore.ReadResult r=store.read();if(r.state()!=InstallationStore.ReadState.SNAPSHOT || r.acknowledgedRevision()!=0 || !CODEC.equals(r.snapshot().codecId()))return false;
            JSONObject descriptor=new JSONObject(TvFirst008Wire.utf8(r.snapshot().canonical().artifact("cache")));JSONObject b=descriptor.getJSONObject("binding");
            return binding.same(new PublicCatalogueCandidate.Binding(b,binding.device,binding.generation,0));
        }catch(Exception refused){return false;}}
    }

    /** Lecture bornée d'un fichier régulier non symbolique appartenant au cache. */
    private static byte[] read(File file,int maximum) throws Exception {
        MultimediaDeliveryProfile.require(!Files.isSymbolicLink(file.toPath()) && file.isFile() && file.length()>0 && file.length()<=maximum,"PUBLIC_FILE_BUDGET");
        byte[] bytes=Files.readAllBytes(file.toPath());MultimediaDeliveryProfile.require(bytes.length==file.length() && bytes.length<=maximum,"PUBLIC_FILE_CHANGED");return bytes;
    }
    /** Rend le contenu durable avant publication du pointeur. */
    private static void write(File file,byte[] bytes) throws Exception {
        try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.getFD().sync();}
    }
}
