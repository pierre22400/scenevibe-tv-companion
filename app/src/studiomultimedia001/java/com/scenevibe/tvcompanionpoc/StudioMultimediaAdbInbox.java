package com.scenevibe.tvcompanionpoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Fixed, application-private inbox for an owner-initiated ADB debug transfer.
 * Neither the source path nor a Cloud URL comes from intents, network or package JSON.
 * Package bytes must still pass StudioMultimediaPackage.read() and native MP3 inspection.
 */
final class StudioMultimediaAdbInbox {
    static final String RELATIVE_PATH = "studio-inbox/track.zip";
    private static final long MAX_ZIP_BYTES = 256L * 1024 * 1024;

    private StudioMultimediaAdbInbox() { }

    static File checkedFile(File appFilesDir) throws IOException {
        if (appFilesDir == null || !appFilesDir.isDirectory())
            throw new IOException("ADB_PRIVATE_FILES_UNAVAILABLE");
        Path root = appFilesDir.getCanonicalFile().toPath();
        Path inbox = root.resolve("studio-inbox");
        Path file = inbox.resolve("track.zip");
        // A debug ADB transfer writes a normal file. Do not follow symlinks supplied
        // through another app or treat a different directory as an inbox.
        if (Files.isSymbolicLink(inbox) || Files.isSymbolicLink(file))
            throw new IOException("ADB_INBOX_SYMLINK_REFUSED");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("ADB_PACKAGE_NOT_FOUND");
        long bytes = Files.size(file);
        if (bytes < 1 || bytes > MAX_ZIP_BYTES)
            throw new IOException("ADB_PACKAGE_SIZE_REFUSED");
        if (!file.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root))
            throw new IOException("ADB_INBOX_ESCAPE_REFUSED");
        return file.toFile();
    }
}
