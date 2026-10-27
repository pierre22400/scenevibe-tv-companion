package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.ByteArrayInputStream;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real cross-contract checks. No account token, native admission or renderer is fabricated. */
public final class CloudBridge003cBoundaryTest {
    private JSONObject prepared() throws Exception {
        return new JSONObject(Files.readString(Path.of(System.getProperty("scenevibe.cloud.prepared"))));
    }
    @Test public void preparedBodyIsNotAHistoricalCloudAssignment() throws Exception {
        JSONObject body=prepared();
        assertEquals("MULTIMEDIA_PREPARATION_ONLY",body.getString("scope"));
        assertFalse(body.getBoolean("cloudPublicationAuthorized"));
        assertFalse(body.getBoolean("androidMediaAdmissionAuthorized"));
        assertFalse(CloudProtocol.validAssignment(body,"qualification-tv",0));
    }
    @Test public void preparedCodecIsNotAdvertisedOrAcceptedAsTvFirstCache() throws Exception {
        JSONObject body=prepared();String codec=body.getString("codec");
        assertFalse(TvCapabilities.current().supportedCodecs().contains(codec));
        assertFalse(TvCapabilities.current().supportsRemoteAssetAcquisition());
        assertFalse(TvCapabilities.current().supportsSharedAssetCache());
        JSONObject binding=new JSONObject().put("profile",TvFirst008Wire.PROFILE)
                .put("deviceId","00000000-0000-4000-8000-000000000001")
                .put("generation","00000000-0000-4000-8000-000000000002")
                .put("intentionId","00000000-0000-4000-8000-000000000003")
                .put("finalTrackId","00000000-0000-4000-8000-000000000004")
                .put("codecId",codec).put("revision",1).put("digest","0".repeat(64));
        try { new TvFirst008Wire.Binding(binding); fail("prepared codec must be refused"); }
        catch(IllegalArgumentException expected) { assertEquals("INVALID_CACHE_CONTRACT",expected.getMessage()); }
    }
    @Test public void cloudSnapshotEqualsTheWholeStudioZipEditorialDocument() throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(System.getProperty("scenevibe.multimedia.package")));
        JSONObject track=null;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(entry.getName().equals("track.json")) track=new JSONObject(new String(zip.readAllBytes(),StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        assertNotNull(track);
        JSONObject snapshot=prepared().getJSONObject("proposal").getJSONObject("editorial").getJSONObject("snapshot");
        assertTrue("All editorial fields including timeline must match",snapshot.similar(track));
        int text=0,audio=0;
        for(int i=0;i<snapshot.getJSONArray("events").length();i++) {
            String type=snapshot.getJSONArray("events").getJSONObject(i).getString("type");
            if(type.equals("TEXT"))text++;if(type.equals("AUDIO"))audio++;
        }
        assertEquals(16,text);assertEquals(3,audio);
    }
    @Test public void r2PackageJsonIsNotTheAdbZipContract() throws Exception {
        byte[] bytes=prepared().toString().getBytes(StandardCharsets.UTF_8);
        try { StudioMultimediaPackage.read(bytes,b -> {throw new AssertionError("no decoder before format validation");}); fail("R2 JSON cannot be treated as a Studio ZIP"); }
        catch(IllegalArgumentException expected) { assertEquals("FILE_MISSING_OR_BUDGET",expected.getMessage()); }
    }
}
