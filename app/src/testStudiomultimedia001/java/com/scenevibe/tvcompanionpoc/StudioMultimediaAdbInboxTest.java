package com.scenevibe.tvcompanionpoc;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Standalone JUnit guards for the fixed ADB debug-package inbox. */
public final class StudioMultimediaAdbInboxTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private File inbox() throws IOException {
        File root=temp.newFolder("app-data");
        assertTrue(new File(root,"studio-inbox").mkdir());
        return root;
    }
    private void expect(String code,File root) throws Exception {
        try { StudioMultimediaAdbInbox.checkedFile(root);fail("Expected "+code); }
        catch(IOException refused) { assertEquals(code,refused.getMessage()); }
    }

    @Test public void refusesMissingRoot() throws Exception {
        expect("ADB_PRIVATE_FILES_UNAVAILABLE",new File(temp.getRoot(),"missing"));
    }
    @Test public void refusesMissingPackage() throws Exception {
        expect("ADB_PACKAGE_NOT_FOUND",inbox());
    }
    @Test public void acceptsOnlyFixedFilename() throws Exception {
        File root=inbox();
        Files.write(new File(root,"studio-inbox/other.zip").toPath(),new byte[]{1,2});
        expect("ADB_PACKAGE_NOT_FOUND",root);
        File expected=new File(root,"studio-inbox/track.zip");
        Files.write(expected.toPath(),new byte[]{42,43});
        assertEquals(expected.getCanonicalFile(),StudioMultimediaAdbInbox.checkedFile(root).getCanonicalFile());
    }
    @Test public void rejectsEmptyPackage() throws Exception {
        File root=inbox();File expected=new File(root,"studio-inbox/track.zip");
        assertTrue(expected.createNewFile());expect("ADB_PACKAGE_SIZE_REFUSED",root);
    }
    @Test public void rejectsOversizePackage() throws Exception {
        File root=inbox();File expected=new File(root,"studio-inbox/track.zip");
        try(RandomAccessFile f=new RandomAccessFile(expected,"rw")) {f.setLength(256L*1024*1024+1);}
        expect("ADB_PACKAGE_SIZE_REFUSED",root);
    }
    @Test public void rejectsSymbolicLinkPackage() throws Exception {
        File root=inbox();Path outside=temp.newFile("other.zip").toPath();
        Path link=new File(root,"studio-inbox/track.zip").toPath();
        try { Files.createSymbolicLink(link,outside); }
        catch(UnsupportedOperationException | IOException | SecurityException unavailable) { return; }
        expect("ADB_INBOX_SYMLINK_REFUSED",root);
    }
    @Test public void rejectsSymbolicLinkInbox() throws Exception {
        File root=temp.newFolder("another-app");
        File outside=temp.newFolder("outside");
        Files.write(new File(outside,"track.zip").toPath(),new byte[]{1});
        Path link=new File(root,"studio-inbox").toPath();
        try { Files.createSymbolicLink(link,outside.toPath()); }
        catch(UnsupportedOperationException | IOException | SecurityException unavailable) { return; }
        expect("ADB_INBOX_SYMLINK_REFUSED",root);
    }
}
