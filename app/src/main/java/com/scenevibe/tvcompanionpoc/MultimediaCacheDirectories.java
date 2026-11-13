package com.scenevibe.tvcompanionpoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Gère les dossiers immuables d'un cache privé ou public, dans son namespace
 * existant. Le verrou d'écriture protège aussi le pointeur et la restauration.
 * Un verrou distinct protège les baux MEDIA et leur exclusion du nettoyage.
 */
final class MultimediaCacheDirectories {
    private static final Map<File, MultimediaCacheDirectories> ROOTS = new HashMap<>();
    private static final Pattern SET = Pattern.compile("set-[0-9a-f]{32}");
    private static final Pattern PUBLIC_STAGE = Pattern.compile("set-[0-9a-f]{32}\\.partial");
    private static final Pattern PRIVATE_STAGE = Pattern.compile("stage-[0-9a-f]{32}");

    private static final ExecutorService CLEANUP = Executors.newSingleThreadExecutor();

    final Object writer = new Object();
    // Shared under writer until process restart can recover the durable pointer.
    boolean recoveryUncertain;
    private final File root;
    private final Map<File, Integer> pins = new HashMap<>();

    /** Frontière de panne disque commune aux deux caches, sans modifier le protocole de reçu. */
    interface Writer {
        /** Écrit durablement un nouveau fichier du dossier de préparation. */
        void write(File target, byte[] data) throws Exception;
    }

    /** Associe un même verrou et les mêmes baux à tous les accès au dossier canonique. */
    static synchronized MultimediaCacheDirectories at(File root) {
        return ROOTS.computeIfAbsent(root, MultimediaCacheDirectories::new);
    }

    /** Conserve le chemin canonique déjà validé par le cache appelant. */
    private MultimediaCacheDirectories(File root) {
        this.root = root;
    }

    /** Vérifie le nom existant avant de résoudre un pointeur, sans suivre de lien. */
    static boolean isSet(String name) {
        return SET.matcher(name).matches();
    }

    /** Épingle le dossier jusqu'à la libération du lecteur natif, y compris ses callbacks. */
    synchronized Lease pin(File directory,Runnable collect) {
        if (!root.equals(directory.getParentFile()) || !isSet(directory.getName())
                || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("CACHE_SET_UNAVAILABLE");
        }
        pins.merge(directory, 1, Integer::sum);
        return new Lease(this, directory, collect);
    }

    /** Libère le bail exactement une fois. */
    static final class Lease implements AutoCloseable {
        private final MultimediaCacheDirectories owner;
        private final File directory;
        private final Runnable collect;
        private boolean closed;

        /** Lie la restitution à un seul dossier vérifié. */
        private Lease(MultimediaCacheDirectories owner, File directory, Runnable collect) {
            this.owner = owner;
            this.directory = directory;
            this.collect = collect;
        }

        /** Autorise une future collecte lorsque le dernier lecteur a terminé. */
        @Override public void close() {
            synchronized (owner) {
                if (closed) return;
                closed = true;
                int count = owner.pins.get(directory);
                if (count > 1) {
                    owner.pins.put(directory, count - 1);
                    return;
                }
                owner.pins.remove(directory);
            }
            // Le lecteur se ferme sur le Looper. Un transfert peut attendre ce Looper.
            CLEANUP.execute(collect);
        }
    }

    /**
     * Collecte les dossiers de ce format seulement. L'appelant détient writer,
     * connaît le pointeur durable et a terminé tout transfert ou retour arrière.
     */
    synchronized void collect(File current, boolean publicCache) {
        if (!Thread.holdsLock(writer)) throw new IllegalStateException("CACHE_WRITER_REQUIRED");
        if (!Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        File[] entries = root.listFiles();
        if (entries == null) return;
        Pattern stage = publicCache ? PUBLIC_STAGE : PRIVATE_STAGE;
        for (File entry : entries) {
            String name = entry.getName();
            if ((!isSet(name) && !stage.matcher(name).matches()) || entry.equals(current)
                    || pins.containsKey(entry) || !Files.isDirectory(entry.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                removeDirectory(entry.toPath());
            } catch (IOException | SecurityException retryLater) {
                // Un nettoyage différé ne change ni le pointeur ni le résultat du transfert.
            }
        }
    }

    /** Compare le poids des octets à écrire, sans compter deux fois un même MP3 physique. */
    void requireSpace(MultimediaDeliveryProfile profile, long metadataBytes) throws IOException {
        Set<String> counted = new HashSet<>();
        long needed = metadataBytes;
        for (MultimediaDeliveryProfile.Resource resource : profile.resources) {
            if (counted.add(resource.sha256)) needed += resource.byteLength;
        }
        if (root.getUsableSpace() < needed) {
            throw new java.nio.file.FileSystemException(root.getName(), null, "ENOSPC");
        }
    }

    /** Supprime les fichiers d'un dossier possédé sans traverser les liens symboliques. */
    private static void removeDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            /** Supprime une entrée locale, ou le lien lui-même. */
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            /** Supprime le dossier après ses entrées. */
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
