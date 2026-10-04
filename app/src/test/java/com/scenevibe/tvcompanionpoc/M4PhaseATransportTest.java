package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.json.JSONException;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.Assert.*;

/**
 * Exercises the real CloudControlClient fetch/apply/ACK method and UTF-8 HTTP codec.
 * HTTPS connections are intercepted only for a reserved .invalid test host. No sockets,
 * credentials, private editorial fixtures or new production transport seams are introduced.
 */
public final class M4PhaseATransportTest {
    private static final ThreadLocal<Exchange> EXCHANGE=new ThreadLocal<>();

    /** Install a process-local URL boundary once; every unexpected connection fails closed. */
    @BeforeClass public static void isolateHttpsTransport() {
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler() {
            /** The only permitted URL is the current deterministic fixture exchange. */
            @Override protected URLConnection openConnection(URL url) throws java.io.IOException {
                Exchange exchange=EXCHANGE.get();
                if (exchange==null||!"m4-characterization.invalid".equals(url.getHost()))
                    throw new java.io.IOException("Unexpected test transport target");
                return exchange.connection(url);
            }
        }:null);
    }

    /** Release the exchange so a subsequent test cannot inherit response state. */
    @After public void clearExchange() {EXCHANGE.remove();}

    /** Observe real request boundaries and return a canonical server confirmation by default. */
    private static final class Exchange {
        final M4PhaseAFixtures.Runtime runtime;
        JSONObject assignment;
        JSONObject ackBody;
        int requests,ackRequests,ackStatus=200;
        boolean badConfirmation;
        String getPath;

        /** Bind one received assignment to the current production installation cores. */
        Exchange(M4PhaseAFixtures.Runtime runtime,JSONObject assignment) {
            this.runtime=runtime;this.assignment=assignment;
        }

        /** Substitute bytes at HttpsURLConnection, after the client's actual codec has run. */
        HttpsURLConnection connection(URL url) {
            requests++;
            return new HttpsURLConnection(url) {
                final ByteArrayOutputStream posted=new ByteArrayOutputStream();
                boolean observed;
                /** The transport substitute holds no network resource. */
                @Override public void disconnect() {}
                /** No proxy is involved in the intercepted request. */
                @Override public boolean usingProxy() {return false;}
                /** Connection opening has no side effect. */
                @Override public void connect() {}
                /** Capture exactly the bytes serialized by the real client. */
                @Override public OutputStream getOutputStream() {return posted;}
                /** Observe ACK only after the durable/live state has become coherent. */
                @Override public int getResponseCode() throws IOException {
                    try {
                    if ("GET".equals(getRequestMethod())) {
                        getPath=url.getPath()+"?"+url.getQuery();return 200;
                    }
                    if (!observed) {
                        observed=true;ackRequests++;
                        ackBody=new JSONObject(new String(posted.toByteArray(),StandardCharsets.UTF_8));
                        long revision=assignment.getLong("revision");
                        assertEquals(revision,runtime.cache.revision());
                        if (assignment.has("overlayManifest"))
                            assertTrue("ACK requires armed manifest",runtime.controller.isSceneRendererActiveFor(revision));
                        else assertFalse("ACK requires retired manifested owner",runtime.controller.hasActiveManifest());
                        assertEquals(0,runtime.sink.visible);
                        runtime.memory.trace.add("ack-request:"+revision);
                        assertEquals("/api/v1/devices/"+assignment.getString("deviceId")+"/ack",url.getPath());
                    }
                    return ackStatus;
                    } catch (JSONException invalid) {
                        throw new IOException("Invalid test response fixture");
                    }
                }
                /** Return fixture bytes with the same UTF-8 boundary as the real server. */
                @Override public InputStream getInputStream() throws IOException {
                    try {
                    JSONObject body="GET".equals(getRequestMethod())?assignment:new JSONObject()
                            .put("deviceId",assignment.getString("deviceId"))
                            .put("revision",assignment.getLong("revision")+(badConfirmation?1:0))
                            .put("deliveryStatus","acknowledged");
                    return new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (JSONException invalid) {
                        throw new IOException("Invalid test response fixture");
                    }
                }
                /** The real client's error path reads this bounded synthetic response. */
                @Override public InputStream getErrorStream() {
                    try {return getInputStream();}
                    catch (IOException invalid) {throw new IllegalStateException("Invalid test response fixture");}
                }
                /** There is no negotiated TLS session in an in-process byte substitute. */
                @Override public String getCipherSuite() {return "TEST_ONLY";}
                /** No local certificate is used by the fixture transport. */
                @Override public Certificate[] getLocalCertificates() {return null;}
                /** No remote certificate is used by the fixture transport. */
                @Override public Certificate[] getServerCertificates() {return new Certificate[0];}
            };
        }
    }

    /** Invoke the unchanged private poll subroutine through the existing injectable client. */
    private static void fetch(Exchange exchange) throws Exception {
        M4PhaseAFixtures.Credentials metadata=new M4PhaseAFixtures.Credentials();
        SecretStore.InMemorySecretStore secrets=new SecretStore.InMemorySecretStore();
        CloudDeviceCredentials credentials=new CloudDeviceCredentials(metadata,secrets);
        assertTrue(credentials.persistActivation(exchange.assignment.getString("deviceId"),
                "synthetic-device-token-for-tests","synthetic-activation-id",
                "synthetic-activation-secret-for-tests","123456"));
        assertTrue(credentials.confirmClaimed());
        ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
        try {
            CloudControlClient client=new CloudControlClient(io,credentials,exchange.runtime.cache,
                    exchange.runtime.scheduler,exchange.runtime.installer);
            Field origin=CloudControlClient.class.getDeclaredField("origin");origin.setAccessible(true);
            origin.set(client,"https://m4-characterization.invalid");
            Field running=CloudControlClient.class.getDeclaredField("running");running.setAccessible(true);
            running.setBoolean(client,true);
            Method fetch=CloudControlClient.class.getDeclaredMethod("fetchAssignment");fetch.setAccessible(true);
            EXCHANGE.set(exchange);
            try {fetch.invoke(client);} catch (InvocationTargetException failure) {
                Throwable cause=failure.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw new AssertionError("Unexpected fetch exception type");
            }
        } finally {io.shutdownNow();EXCHANGE.remove();}
    }

    /** Confirm rejection without printing the exception message or submitted payload. */
    private static void rejected(Exchange exchange) throws Exception {
        try {fetch(exchange);fail("Expected fetch rejection");}
        catch (Exception expected) {assertNotNull(expected.getClass());}
    }

    /** The network ACK is sent after commit and arm, then its confirmation is persisted. */
    @Test public void realFetchOrdersValidateCommitArmAckAndUsesCanonicalWire() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();runtime.memory.trace.clear();
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));
        fetch(exchange);
        assertEquals(2,exchange.requests);assertEquals(1,exchange.ackRequests);
        assertEquals("/api/v1/devices/"+exchange.assignment.getString("deviceId")+"/assignment?afterRevision=13",exchange.getPath);
        assertEquals(2,exchange.ackBody.length());
        assertTrue(exchange.ackBody.has("revision"));assertTrue(exchange.ackBody.has("finalTrackId"));
        assertFalse(exchange.ackBody.has("trackId"));
        assertEquals(14,exchange.ackBody.getLong("revision"));
        assertEquals(exchange.assignment.getString("finalTrackId"),exchange.ackBody.getString("finalTrackId"));
        assertTrue(runtime.memory.trace.indexOf("commit:14")<runtime.memory.trace.indexOf("arm:14"));
        assertTrue(runtime.memory.trace.indexOf("arm:14")<runtime.memory.trace.indexOf("ack-request:14"));
        assertTrue(runtime.memory.trace.indexOf("ack-request:14")<runtime.memory.trace.indexOf("ack-save:14"));
        assertEquals(14,runtime.cache.acknowledged());
        assertEquals(0,runtime.sink.shows);
    }

    /** Envelope validation fails before persistence, arming and any outbound ACK. */
    @Test public void invalidEnvelopeNeverInstallsOrAcknowledges() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        JSONObject envelope=M4PhaseAFixtures.envelope(0,14).put("type","unsupported.assignment");
        Map<String,String> before=new HashMap<>(runtime.memory.values);
        Exchange exchange=new Exchange(runtime,envelope);
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(0,runtime.memory.commits);
        assertTrue("invalid envelope preserves tuple",before.equals(runtime.memory.values));
    }

    /** Structural envelope acceptance is insufficient when the actual bridge rejects timings. */
    @Test public void bridgeFailureNeverAcknowledges() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        JSONObject envelope=M4PhaseAFixtures.envelope(0,14);
        JSONObject scene=envelope.getJSONObject("overlayManifest").getJSONArray("scenes").getJSONObject(0);
        scene.put("durationMs",scene.getLong("durationMs")+1);
        assertTrue(CloudProtocol.validAssignment(envelope,envelope.getString("deviceId"),13));
        Exchange exchange=new Exchange(runtime,envelope);
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(0,runtime.memory.commits);
        assertEquals(13,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
    }

    /** A failed durable installation is never followed by a network ACK. */
    @Test public void durableCommitFailureNeverAcknowledges() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();runtime.memory.writable=false;
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(0,runtime.memory.commits);
        assertEquals(13,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
        assertEquals(13,runtime.controller.activeRevision());
    }

    /** A post-commit retirement failure refuses ACK; it does not pretend the durable commit rolled back. */
    @Test public void activationFailureAfterCommitNeverAcknowledges() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();runtime.due();runtime.sink.failHide=true;
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(1,runtime.memory.commits);
        assertEquals(14,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
        assertFalse(runtime.controller.isSceneRendererActiveFor(14));
        assertEquals(1,runtime.sink.maxVisible);
        runtime.sink.failHide=false;
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(runtime.memory);
        assertEquals(14,recreated.restore());
        assertTrue(recreated.controller.isSceneRendererActiveFor(14));
        assertFalse(recreated.controller.hasVisibleScene());
    }

    /** A stale revision is rejected by the real transport path without changing the current owner. */
    @Test public void staleEnvelopeNeverAcknowledgesOrReplaces() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,12));
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(0,runtime.memory.commits);
        assertEquals(13,runtime.controller.activeRevision());
        assertEquals(13,runtime.cache.acknowledged());
    }

    /** A manifested redelivery cannot ACK a same-revision legacy cache that has no durable manifest. */
    @Test public void sameRevisionWithoutDurableManifestCannotAcknowledgeManifestedDelivery() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(false));
        runtime.restore();
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));
        rejected(exchange);
        assertEquals(0,exchange.ackRequests);assertEquals(0,runtime.memory.commits);
        assertEquals(14,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
        assertFalse(runtime.controller.hasActiveManifest());
        assertNull(runtime.memory.values.get("manifest"));
    }

    /** The pre-manifest Cloud v1 envelope remains accepted and ACKs after legacy activation. */
    @Test public void legacyWireWithoutVisualModeOrManifestStillWorks() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();runtime.due();
        JSONObject legacy=M4PhaseAFixtures.envelope(0,14);
        legacy.remove("overlayManifest");legacy.remove("visualMode");
        Exchange exchange=new Exchange(runtime,legacy);
        fetch(exchange);
        assertEquals(1,exchange.ackRequests);assertEquals(14,runtime.cache.acknowledged());
        assertNull(runtime.memory.values.get("manifest"));
        assertFalse(runtime.controller.hasActiveManifest());
        runtime.due();assertEquals(1,runtime.legacyRenders);assertEquals(1,runtime.sink.shows);
    }

    /** A lost/rejected ACK leaves a durable pending revision; redelivery confirms it without rewriting. */
    @Test public void failedAckCanBeRetriedIdempotentlyFromDurableCache() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.restore();
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));exchange.ackStatus=500;
        rejected(exchange);
        assertEquals(14,runtime.cache.revision());assertEquals(13,runtime.cache.acknowledged());
        assertEquals(1,runtime.memory.commits);assertEquals(0,runtime.memory.ackCommits);
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(runtime.memory);
        assertEquals(14,recreated.restore());
        Exchange retry=new Exchange(recreated,M4PhaseAFixtures.envelope(0,14));
        fetch(retry);
        assertEquals(1,runtime.memory.commits);assertEquals(1,runtime.memory.ackCommits);
        assertEquals(14,recreated.cache.acknowledged());assertEquals(1,retry.ackRequests);
    }

    /** A mismatched server confirmation never advances the locally acknowledged revision. */
    @Test public void wrongAckConfirmationKeepsPreviousAcknowledgedRevision() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));exchange.badConfirmation=true;
        rejected(exchange);
        assertEquals(1,exchange.ackRequests);assertEquals(14,runtime.cache.revision());
        assertEquals(13,runtime.cache.acknowledged());assertEquals(0,runtime.memory.ackCommits);
    }

    /** Failure to persist a confirmed ACK is reported, retaining a retryable installed revision. */
    @Test public void failedAckPersistenceLeavesRevisionPending() throws Exception {
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(M4PhaseAFixtures.cacheFixture(true));
        runtime.memory.ackWritable=false;
        Exchange exchange=new Exchange(runtime,M4PhaseAFixtures.envelope(0,14));
        rejected(exchange);
        assertEquals(1,exchange.ackRequests);assertEquals(14,runtime.cache.revision());
        assertEquals(13,runtime.cache.acknowledged());assertEquals(0,runtime.memory.ackCommits);
    }

    /** Incoming UTF-8 text survives HTTP decoding, atomic persistence, parsing and process recreation. */
    @Test public void unicodeSurvivesTheRealHttpCodecAndCacheRestore() throws Exception {
        M4PhaseAFixtures.Memory fixture=M4PhaseAFixtures.cacheFixture(true);
        JSONObject envelope=M4PhaseAFixtures.envelope(0,14)
                .put("runtimeTrack",new JSONObject(fixture.values.get("runtime")))
                .put("overlayManifest",new JSONObject(fixture.values.get("manifest")));
        M4PhaseAFixtures.Runtime runtime=new M4PhaseAFixtures.Runtime(new M4PhaseAFixtures.Memory());
        fetch(new Exchange(runtime,envelope));
        assertTrue("HTTP text preserved",M4PhaseAFixtures.UNICODE.equals(runtime.firstEvent().text));
        String manifestBytes=runtime.memory.values.get("manifest");
        JSONObject text=new JSONObject(manifestBytes).getJSONArray("scenes").getJSONObject(0)
                .getJSONArray("elements").getJSONObject(0).getJSONArray("children").getJSONObject(1);
        assertTrue("manifest Unicode preserved",M4PhaseAFixtures.UNICODE.equals(text.getString("text")));
        M4PhaseAFixtures.Runtime recreated=new M4PhaseAFixtures.Runtime(runtime.memory);
        assertEquals(14,recreated.restore());assertEquals(14,recreated.cache.acknowledged());
        assertTrue("restored text preserved",M4PhaseAFixtures.UNICODE.equals(recreated.firstEvent().text));
        assertTrue("restored manifest bytes preserved",manifestBytes.equals(runtime.memory.values.get("manifest")));
        assertFalse(recreated.controller.hasVisibleScene());
    }
}
