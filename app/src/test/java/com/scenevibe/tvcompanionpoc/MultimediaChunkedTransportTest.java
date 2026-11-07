package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A valid HTTP chunked response has no Content-Length. Accept its bounded bytes
 * without weakening declared-length checks, exact MP3 sizes, digests or cancellation.
 * All identities and payloads below are disposable test fixtures.
 */
public final class MultimediaChunkedTransportTest {
    private static final String UUID="11111111-1111-4111-a111-111111111111";

    /** Return a synthetic lease with no usable real-world credential or authority. */
    private static MultimediaCloudClient.Session session(boolean live) {
        return new MultimediaCloudClient.Session() {
            /** Return the fixture device id. */ public String deviceId(){return UUID;}
            /** Return the fixture association generation. */ public String generation(){return UUID;}
            /** Return only a fake local-test token. */ public String token(){return "synthetic-only";}
            /** Expose test-controlled cancellation. */ public boolean current(){return live;}
        };
    }

    /** Supply exact fixture bytes through the real client request and integrity code. */
    private static MultimediaCloudClient client(byte[] data,String mime,long length) {
        return new MultimediaCloudClient(session(true),(method,path,token,body)->
            new MultimediaCloudClient.Reply(200,mime,length,new ByteArrayInputStream(data),()->{}));
    }

    /** Require a fixed refusal code while retaining all actual production assertions. */
    private static void refuses(String code,Throwing action) throws Exception {
        try{action.run();fail("Invalid response accepted");}
        catch(IllegalArgumentException expected){assertEquals(code,expected.getMessage());}
    }

    /** Fixture callback for assertions whose actual client operation can throw. */
    private interface Throwing {
        /** Invoke the unchanged request boundary under test. */ void run() throws Exception;
    }

    /** Build a finite resource descriptor from fake bytes, without any provider object. */
    private static MultimediaDeliveryProfile.Resource resource(byte[] data) throws Exception {
        return new MultimediaDeliveryProfile.Resource(new JSONObject().put("resourceId","synthetic-audio").put("sha256",MultimediaDeliveryProfile.hash(data))
            .put("byteLength",data.length).put("mime","audio/mpeg").put("durationMs",3000));
    }

    /** Retain the exact closed assignment shape used by the resource request. */
    private static MultimediaCloudClient.Binding binding() throws Exception {
        return new MultimediaCloudClient.Binding(new JSONObject().put("type","scenevibe.cloud.multimedia-assignment.v1")
            .put("profile",MultimediaDeliveryProfile.PROFILE).put("deviceId",UUID).put("generation",UUID).put("intentionId",UUID)
            .put("publicationId",UUID).put("revision",1).put("codecId",MultimediaDeliveryProfile.CODEC)
            .put("codecVersion",MultimediaDeliveryProfile.VERSION).put("packageSha256","0".repeat(64)));
    }

    /** Valid chunked metadata is parsed after the complete bounded body arrives. */
    @Test public void chunkedLibraryWithoutLengthIsAccepted() throws Exception {
        byte[] bytes="{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        assertEquals(0,client(bytes,"application/json; charset=utf-8",-1).publications().getJSONArray("items").length());
    }

    /** The historical fixed-length success path is preserved. */
    @Test public void declaredLengthLibraryStillWorks() throws Exception {
        byte[] bytes="{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        assertEquals(0,client(bytes,"application/json",bytes.length).publications().getJSONArray("items").length());
    }

    /** Unknown length does not waive the finite total-body allocation budget. */
    @Test public void chunkedBodyOverBudgetIsRefused() throws Exception {
        refuses("HTTP_BYTE_BUDGET",()->client(new byte[200001],"application/json",-1).publications());
    }

    /** A known but truncated body remains invalid. */
    @Test public void declaredTruncatedBodyIsRefused() throws Exception {
        refuses("HTTP_TRUNCATED",()->client("{}".getBytes(StandardCharsets.UTF_8),"application/json",20).publications());
    }

    /** Unknown framing cannot admit an incorrect MIME type. */
    @Test public void chunkedWrongMimeIsRefused() throws Exception {
        refuses("HTTP_MIME_OR_SIZE_REFUSED",()->client(new byte[5],"text/html",-1).publications());
    }

    /** Values below the standard unknown-length sentinel remain invalid metadata. */
    @Test public void invalidNegativeLengthIsRefused() throws Exception {
        refuses("HTTP_MIME_OR_SIZE_REFUSED",()->client(new byte[5],"application/json",-2).publications());
    }

    /** A chunked resource must still have exactly its manifest-declared byte count. */
    @Test public void chunkedResourceSizeIsStillExact() throws Exception {
        byte[] expected=new byte[8];
        refuses("HTTP_TRUNCATED",()->client(new byte[7],"audio/mpeg",-1).resource(binding(),resource(expected)));
    }

    /** A same-size corrupted chunked resource cannot pass the original MP3 digest check. */
    @Test public void chunkedResourceDigestIsStillExact() throws Exception {
        byte[] expected=new byte[8],changed=new byte[8];changed[4]=1;
        refuses("MP3_INTEGRITY",()->client(changed,"audio/mpeg",-1).resource(binding(),resource(expected)));
        assertArrayEquals(expected,client(expected,"audio/mpeg",-1).resource(binding(),resource(expected)));
    }

    /** Cancellation refuses before transport, also for the newly accepted framing. */
    @Test public void cancellationStillPrecedesAnyNetwork() throws Exception {
        MultimediaCloudClient stopped=new MultimediaCloudClient(session(false),(m,p,t,b)->{fail("Cancelled transport opened");return null;});
        refuses("DOWNLOAD_CANCELLED_OR_STALE_GENERATION",stopped::publications);
    }
}

