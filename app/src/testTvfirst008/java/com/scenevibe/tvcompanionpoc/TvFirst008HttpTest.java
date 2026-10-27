package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Execute the production HTTPS transport with a connection fixture only. No socket,
 * DNS or TLS handshake is opened; actual certificate-chain/hardware networking remain
 * external qualification. Per-class JVM forks isolate the URL handler from Robolectric.
 */
public final class TvFirst008HttpTest {
    private static Connection last;
    private static byte[] response;
    private static int status, declared;
    /** Intercept HTTPS only; no production trust override or injection hook is introduced. */
    @BeforeClass public static void fixture() {
        URL.setURLStreamHandlerFactory(protocol -> !"https".equals(protocol) ? null : new URLStreamHandler() {
            /** Return a deterministic connection without any external network operation. */
            @Override protected URLConnection openConnection(URL url) { last=new Connection(url);return last; }
        });
    }
    /** Reset each response independently of previous calls. */
    @Before public void setup() { last=null;response="{\"status\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8);status=200;declared=-1; }
    /** The actual transport preserves Bearer/body bytes, deadlines and no-redirect policy. */
    @Test public void actualPostSetsBoundedHeadersAndClosesConnection() throws Exception {
        JSONObject body=new JSONObject().put("profile",TvFirst008Wire.PROFILE).put("value","Été 😀");
        JSONObject result=new TvFirst008Http("https://qualification.invalid").request("POST",path(),"a".repeat(43),body);
        assertEquals("synthetic",result.getString("status"));assertFalse(last.getInstanceFollowRedirects());
        assertEquals(8000,last.getConnectTimeout());assertEquals(8000,last.getReadTimeout());
        assertEquals("Bearer "+"a".repeat(43),last.getRequestProperty("Authorization"));
        assertEquals(body.toString(),last.output.toString(StandardCharsets.UTF_8));assertTrue(last.closed);assertEquals(1,last.reads);
        assertSame(HttpsURLConnection.getDefaultHostnameVerifier(),last.getHostnameVerifier());
    }
    /** Redirects are refused before a response stream can be read. */
    @Test public void redirectIsNeverFollowedOrParsed() {
        status=302;
        TvFirst008Http.Failure error=assertThrows(TvFirst008Http.Failure.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));
        assertEquals(302,error.status);assertTrue(last.closed);assertEquals(0,last.reads);
    }
    /** Untrusted error payloads never enter the closed exception. */
    @Test public void refusedResponseDoesNotExposePayload() {
        status=403;response="synthetic-secret-must-not-escape".getBytes(StandardCharsets.UTF_8);
        TvFirst008Http.Failure error=assertThrows(TvFirst008Http.Failure.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));
        assertEquals("CLOUD_REQUEST_REFUSED",error.getMessage());assertEquals(0,last.reads);assertTrue(last.closed);
    }
    /** Both declared-length and chunked overflow are rejected and close their connection. */
    @Test public void oversizedResponsesAreBoundedEvenWithoutContentLength() {
        declared=2_000_001;
        assertThrows(IllegalArgumentException.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));assertEquals(0,last.reads);assertTrue(last.closed);
        declared=-1;response=new byte[2_000_001];
        assertThrows(IllegalArgumentException.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));assertTrue(last.closed);
    }
    /** Malformed JSON or UTF-8 cannot produce a success result. */
    @Test public void malformedSuccessCannotInventAResponse() {
        response=new byte[]{(byte)255};
        assertThrows(IllegalArgumentException.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));assertTrue(last.closed);
        response="not JSON".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class,()->new TvFirst008Http("https://qualification.invalid").request("GET",path(),null,null));assertTrue(last.closed);
    }
    /** No foreign route/profile or oversized command reaches the connection output. */
    @Test public void requestBoundsRefuseUnexpectedPathsAndBodies() throws Exception {
        TvFirst008Http client=new TvFirst008Http("https://qualification.invalid");
        assertThrows(IllegalArgumentException.class,()->client.request("GET","https://foreign.invalid/",null,null));assertNull(last);
        JSONObject huge=new JSONObject().put("value","a".repeat(16_385));
        assertThrows(IllegalArgumentException.class,()->client.request("POST",path(),null,huge));assertEquals(0,last.output.size());assertTrue(last.closed);
    }
    /** Use only the exact additive cache-profile route. */
    private static String path() { return "/api/v1/test?profile="+TvFirst008Wire.PROFILE; }
    /** Native connection fixture leaves the production request algorithm unchanged. */
    private static final class Connection extends HttpsURLConnection {
        final ByteArrayOutputStream output=new ByteArrayOutputStream();boolean closed;int reads;
        /** Retain the requested public URL only inside this synthetic test fixture. */
        Connection(URL url) {super(url);}
        /** No socket is opened. */
        @Override public void connect() {}
        /** Observe guaranteed finally cleanup. */
        @Override public void disconnect() {closed=true;}
        /** The fixture has no proxy. */
        @Override public boolean usingProxy() {return false;}
        /** Supply only a synthetic HTTP status. */
        @Override public int getResponseCode() {return status;}
        /** Exercise both declared and streaming bounds. */
        @Override public int getContentLength() {return declared;}
        /** Return fixture bytes only when the production code permits reading them. */
        @Override public InputStream getInputStream() {reads++;return new ByteArrayInputStream(response);}
        /** Record exact UTF-8 output without any network. */
        @Override public OutputStream getOutputStream() {return output;}
        /** No TLS handshake is claimed by the connection fixture. */
        @Override public String getCipherSuite() {return "SYNTHETIC_NO_TLS_HANDSHAKE";}
        /** No actual certificate is supplied or accepted. */
        @Override public Certificate[] getLocalCertificates() {return null;}
        /** No actual certificate is supplied or accepted. */
        @Override public Certificate[] getServerCertificates() {return new Certificate[0];}
    }
}
