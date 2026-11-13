package com.scenevibe.tvcompanionpoc;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Cache public et privé sur Android TV jetable. Les profils et MediaCodec
 * restent réels. Les pannes sont injectées à l'écriture et au pointeur durable.
 */
@RunWith(AndroidJUnit4.class)
public final class MultimediaCacheLifecycleAndroidTest {
    /** Accumule plusieurs commandes, conserve un MP3 épinglé et récupère après une panne. */
    @Test public void publicReplacementsAndDiskFailure() throws Exception {
        requireEmulator();
        PublicNativeFixture fixture=new PublicNativeFixture();
        try {
            fixture.pair();
            File root=new File(fixture.target.getFilesDir(),"public001b/"+fixture.generation);
            AndroidInstallationBackend backend=new AndroidInstallationBackend(fixture.target,
                    "public001b_cache_"+fixture.generation.replace("-",""));
            Fault fault=new Fault(backend,root);
            PublicMultimediaCache cache=new PublicMultimediaCache(root,new InstallationStore(fault),
                    new AndroidStudioAudioInspector(fixture.target),fault::write);
            PublicCloudClient client=new PublicCloudClient(fixture.state,fixture);
            PublicMultimediaCache.Prepared first=nextPublic(fixture,cache,client);
            String resource=first.profile.resources.get(0).id;
            File pinned=first.resource(resource);
            try(MultimediaCacheDirectories.Lease lease=first.pin()) {
                for(int i=0;i<3;i++)nextPublic(fixture,cache,client);
                assertEquals(2,sets(root));
                assertEquals(first.profile.resources.get(0).sha256,MultimediaDeliveryProfile.hash(Files.readAllBytes(pinned.toPath())));
            }
            PublicMultimediaCache.Prepared retained=cache.read();
            assertFalse(pinned.exists());
            abandoned(root,"set-00000000000000000000000000000001.partial");
            abandoned(root,"set-00000000000000000000000000000002");
            fault.full=true;
            int receipts=fixture.cacheReceipts;
            assertThrows(FileSystemException.class,()->nextPublic(fixture,cache,client));
            assertEquals(receipts,fixture.cacheReceipts);
            assertTrue(retained.binding.same(cache.read().binding));
            assertEquals(1,sets(root));
            assertEquals(0,stages(root));
            fault.full=false;
            fault.rejectPointer=true;
            assertThrows(IllegalArgumentException.class,()->nextPublic(fixture,cache,client));
            assertTrue(fault.sawUnpublishedDirectory);
            assertEquals(receipts,fixture.cacheReceipts);
            assertTrue(retained.binding.same(cache.read().binding));
            fault.rejectPointer=false;
            assertFalse(retained.binding.same(nextPublic(fixture,cache,client).binding));
            assertEquals(receipts+1,fixture.cacheReceipts);
            assertEquals(1,sets(root));
        } finally {
            fixture.close(true);
        }
    }

    /** Réutilise le stockage privé historique et vérifie restauration, espace et empreintes. */
    @Test public void privateRevisionsAndDiskFailure() throws Exception {
        requireEmulator();
        ManualNativeFixture fixture=new ManualNativeFixture();
        try {
            Fault fault=new Fault(new AndroidInstallationBackend(fixture.target,fixture.namespace),fixture.root);
            MultimediaPrivateCache cache=new MultimediaPrivateCache(fixture.root,new InstallationStore(fault),
                    new AndroidStudioAudioInspector(fixture.target),fault::write);
            MultimediaPrivateCache.Prepared first=fixture.download();
            File pinned=first.resource(first.profile.resources.get(0).id);
            try(MultimediaCacheDirectories.Lease lease=first.pin()) {
                for(int i=0;i<3;i++){fixture.replacement();cache.download(fixture.client,fixture.binding);}
                assertEquals(2,sets(fixture.root));
                assertEquals(first.profile.resources.get(0).sha256,MultimediaDeliveryProfile.hash(Files.readAllBytes(pinned.toPath())));
            }
            MultimediaPrivateCache.Prepared retained=fixture.reopen().read();
            assertFalse(pinned.exists());
            abandoned(fixture.root,"stage-00000000000000000000000000000001");
            abandoned(fixture.root,"set-00000000000000000000000000000002");
            fixture.replacement();
            int receipts=fixture.port.receipts;
            fault.full=true;
            assertThrows(FileSystemException.class,()->cache.download(fixture.client,fixture.binding));
            assertEquals(0,stages(fixture.root));
            assertEquals(receipts,fixture.port.receipts);
            assertTrue(retained.binding.same(fixture.reopen().read().binding));
            fault.full=false;
            fault.rejectPointer=true;
            assertThrows(IllegalArgumentException.class,()->cache.download(fixture.client,fixture.binding));
            assertTrue(fault.sawUnpublishedDirectory);
            assertEquals(receipts,fixture.port.receipts);
            assertTrue(retained.binding.same(fixture.reopen().read().binding));
            fault.rejectPointer=false;
            cache.download(fixture.client,fixture.binding);
            assertEquals(receipts+1,fixture.port.receipts);
            assertTrue(fixture.binding.same(fixture.reopen().read().binding));
            assertEquals(1,sets(fixture.root));
        } finally {
            fixture.cleanup();
        }
    }

    /** Refuse un appareil physique et conserve les données dans des générations synthétiques. */
    private static void requireEmulator() throws Exception {
        assertTrue(BuildConfig.DEBUG);
        assertEquals("1",UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("getprop ro.kernel.qemu").trim());
    }

    /** Crée une commande isolée en réutilisant le même appareil associé. */
    private static PublicMultimediaCache.Prepared nextPublic(PublicNativeFixture fixture,
            PublicMultimediaCache cache,PublicCloudClient client) throws Exception {
        fixture.select();
        return cache.download(client,new PublicCatalogueCandidate.Binding(fixture.command,
                fixture.device,fixture.generation,System.currentTimeMillis()),()->false);
    }

    /** Simule un dossier d'un processus interrompu avant la reprise. */
    private static void abandoned(File root,String name) throws Exception {
        File directory=new File(root,name);
        assertTrue(directory.mkdir());
        Files.write(new File(directory,"incomplete").toPath(),new byte[]{1});
    }

    /** Compte uniquement les ensembles finaux du namespace synthétique. */
    private static int sets(File root) {
        return root.listFiles((dir,name)->MultimediaCacheDirectories.isSet(name)).length;
    }

    /** Compte les formats de préparation des deux caches. */
    private static int stages(File root) {
        return root.listFiles((dir,name)->name.startsWith("stage-")||name.endsWith(".partial")).length;
    }

    /** Injecte les erreurs après un octet écrit ou après renommage, avant publication du pointeur. */
    private static final class Fault implements InstallationStore.Backend {
        private final InstallationStore.Backend backend;
        private final File root;
        boolean full,rejectPointer,sawUnpublishedDirectory;

        /** Préserve le même backend Android, avec des pannes activées seulement par le test. */
        Fault(InstallationStore.Backend backend,File root) {
            this.backend=backend;
            this.root=root;
        }

        /** Simule ENOSPC pendant une vraie écriture de fichier partiel. */
        void write(File file,byte[] data) throws Exception {
            try(FileOutputStream output=new FileOutputStream(file)) {
                if(full) {
                    output.write(data[0]);
                    throw new FileSystemException(file.getName(),null,"ENOSPC");
                }
                output.write(data);
                output.getFD().sync();
            }
        }

        /** Partage la sérialisation réelle du backend. */
        @Override public Object monitor() { return backend.monitor(); }

        /** Lit le pointeur réellement persistant. */
        @Override public String get(String key) { return backend.get(key); }

        /** Refuse le nouveau pointeur après observation du dossier final sur le système de fichiers. */
        @Override public boolean commit(Map<String,String> values,Set<String> removed,boolean clear) {
            if(!rejectPointer)return backend.commit(values,removed,clear);
            sawUnpublishedDirectory=sets(root)==2;
            return false;
        }
    }
}
