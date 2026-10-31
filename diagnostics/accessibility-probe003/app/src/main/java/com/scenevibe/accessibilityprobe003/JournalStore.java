package com.scenevibe.accessibilityprobe003;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Private bounded JSONL store, with explicit local-only copies after collection stops.
 * Filenames are generated UUIDs. No arbitrary paths, network, backup or production key exists.
 */
public final class JournalStore {
    private static final String NAME = "probe003-[a-f0-9-]{36}\\.jsonl";
    private final File directory;
    private File current;
    private FileOutputStream output;
    private long bytes;

    /** Use only the application-private journal directory supplied by the Android owner. */
    public JournalStore(File directory) throws IOException {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Journal unavailable");
    }

    /** Start a new UUID-named file without overwriting any existing campaign. */
    public void open(String uuid) throws IOException {
        if (!uuid.matches("[a-f0-9-]{36}") || output != null) throw new IOException("Invalid journal state");
        File candidate = new File(directory, "probe003-" + uuid + ".jsonl");
        if (!candidate.createNewFile()) throw new IOException("Journal exists");
        current = candidate;
        output = new FileOutputStream(current, false);
        bytes = 0;
    }

    /** Append one complete UTF-8 line while reserving room for an explicit terminal row. */
    public boolean append(String json, boolean terminal) throws IOException {
        byte[] encoded = (json + "\n").getBytes(StandardCharsets.UTF_8);
        long limit = ProbeLimits.BYTES - (terminal ? 0 : 1024);
        if (output == null || bytes + encoded.length > limit) return false;
        output.write(encoded);
        output.flush();
        bytes += encoded.length;
        return true;
    }

    /** Flush/close this store; absence of a terminal row after process death remains detectable. */
    public void close() throws IOException {
        if (output != null) {
            try { output.flush(); } finally { output.close(); output = null; }
        }
    }

    /** Return accepted bytes for the current campaign; never an inferred count. */
    public long bytes() { return bytes; }

    /** Return the most recently modified probe-owned file, including an explicitly incomplete run. */
    public File latest() {
        File latest = null;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            if (owned(file) && (latest == null || file.lastModified() > latest.lastModified())) latest = file;
        }
        return latest;
    }

    /** Copy one stopped campaign to app-specific local external files, without overwriting changes. */
    public File exportLocal(File destination) throws IOException {
        if (output != null) throw new IOException("Stop before export");
        File source = latest();
        if (source == null || destination == null) throw new IOException("No local export available");
        if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Export unavailable");
        File target = new File(destination, source.getName());
        if (target.exists()) {
            if (!sha256(source).equals(sha256(target))) throw new IOException("Export conflict");
            return target;
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
        } catch (IOException failure) {
            target.delete();
            throw failure;
        }
        if (!sha256(source).equals(sha256(target))) throw new IOException("Export integrity failure");
        return target;
    }

    /** Delete only recognized probe files after their local 24-hour retention interval. */
    public void prune(long wall) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            if (owned(file) && (output == null || !file.equals(current))
                && wall >= file.lastModified() && wall - file.lastModified() > ProbeLimits.RETENTION_MS) file.delete();
        }
    }

    /** Delete only recognized files in this app-owned directory, refusing an active collection. */
    public boolean deleteStopped() {
        if (output != null) return false;
        boolean success = true;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (owned(file)) success &= file.delete();
        return success;
    }

    /** Check a generated filename before any retention/delete operation. */
    private boolean owned(File file) { return file.isFile() && file.getName().matches(NAME); }

    /** Calculate a local file's SHA-256; no content digest of a platform title is ever taken. */
    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = in.read(buffer)) != -1) digest.update(buffer, 0, size);
            }
            StringBuilder hex = new StringBuilder();
            for (byte part : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", part & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA256 unavailable"); }
    }
}
