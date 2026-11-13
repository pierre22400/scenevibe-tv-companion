package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Collections;

/**
 * Un pointeur de génération par domaine sur l'appareil. Les anciens pointeurs
 * d'installation restent le format de lecture et de récupération des APK M5.
 * Une génération perd sa place seulement après vérification du nouvel ensemble.
 */
final class MultimediaCacheDomain {
    private final Context context;
    private final File root;
    private final boolean publicCache;
    private final AndroidInstallationBackend preference;
    final Object writer;

    /** Partage la sérialisation des installations et collectes de ce domaine Android. */
    MultimediaCacheDomain(Context context, boolean publicCache) throws IOException {
        this.context=context.getApplicationContext();
        this.publicCache=publicCache;
        File candidate=new File(context.getFilesDir(), publicCache?"public001b":"multimedia003d");
        if(Files.isSymbolicLink(candidate.toPath()))throw new IOException("CACHE_DOMAIN_SYMLINK");
        root=candidate.getCanonicalFile();
        writer=MultimediaCacheDirectories.at(root).writer;
        preference=new AndroidInstallationBackend(this.context,publicCache?"public001q3_current":"private001q3_current");
    }

    /** Identifie une génération appartenant au format historique de ce seul domaine. */
    private boolean generation(String name) {
        return publicCache?name.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"):name.matches("[a-f0-9]{32}");
    }

    /** Une mise à jour sans marqueur laisse chaque ancien cache lisible jusqu'à sa reprise vérifiée. */
    boolean allows(File directory) {
        String current=current();
        return current==null || current.equals(directory.getName());
    }

    /** Distingue une première migration des lectures ordinaires. */
    boolean hasCurrent() {
        return current()!=null;
    }

    /** Capture le pointeur avant la transaction privée qui peut être annulée. */
    String current() { return preference.get("runtime"); }

    /** Restaure le pointeur précédent si la transaction privée échoue après publication. */
    boolean restore(String previous) {
        if(java.util.Objects.equals(previous,current()))return true;
        return preference.commit(previous==null?Collections.emptyMap():Collections.singletonMap("runtime",previous),
                previous==null?Collections.singleton("runtime"):Collections.emptySet(),false);
    }

    /** Publie la génération vérifiée avant toute suppression intergénération. */
    void publish(File directory) throws IOException {
        if(!Thread.holdsLock(writer) || !root.equals(directory.getParentFile()) || !generation(directory.getName()))
            throw new IllegalStateException("CACHE_DOMAIN_WRITER_REQUIRED");
        if(!restore(directory.getName()))
            throw new IOException("CACHE_DOMAIN_COMMIT_FAILED");
    }

    /** Gèle la collecte de ce domaine si le retour arrière ne peut être confirmé. */
    void holdRecovery() { MultimediaCacheDirectories.at(root).recoveryUncertain=true; }

    /** Retire les pointeurs obsolètes, puis les seuls dossiers possédés sans bail de lecture. */
    void collect() {
        if(!Thread.holdsLock(writer))throw new IllegalStateException("CACHE_DOMAIN_WRITER_REQUIRED");
        String current=current();
        if(current==null || !generation(current) || MultimediaCacheDirectories.at(root).recoveryUncertain)return;
        File[] children=root.listFiles();
        if(children==null)return;
        for(File child:children) {
            String name=child.getName();
            if(name.equals(current) || !generation(name)
                    || !Files.isDirectory(child.toPath(),LinkOption.NOFOLLOW_LINKS))continue;
            try {
                MultimediaCacheDirectories directories=MultimediaCacheDirectories.at(child);
                synchronized(directories.writer) {
                    if(directories.recoveryUncertain)continue;
                    String namespace=(publicCache?"public001b_cache_":"multimedia003d_cache_")+name.replace("-","");
                    InstallationStore store=new InstallationStore(new AndroidInstallationBackend(context,namespace));
                    if(store.read().state()==InstallationStore.ReadState.EMPTY || store.clearAll())
                        directories.collect(null,publicCache);
                }
            } catch(RuntimeException unavailable) {
                // Une prochaine lecture ou publication réessaie le nettoyage.
            }
        }
    }
}
