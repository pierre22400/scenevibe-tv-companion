package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import static org.junit.Assert.*;

/**
 * Execute the actual Cloud HTTP codec, adapter, generic installer/store and service-owned ports.
 * Only HTTPS, private disk and native windows are deterministic substitutes. The real owner
 * executor differs from Cloud io, and no Video parser, handler or revision policy is copied.
 */
final class M4PhaseFFixtures {
    private static final ThreadLocal<Harness> HTTP=new ThreadLocal<>();

    /** Preserve the frozen v1 projection while selecting manifested or historical legacy shape. */
    static JSONObject envelope(boolean manifested,long revision) throws Exception {
        JSONObject data=M4PhaseAFixtures.envelope(0,revision);
        if(!manifested){data.remove("overlayManifest");data.remove("visualMode");}
        return data;
    }
    /** Seed the exact frozen historical strings without rewriting their bytes or ACK. */
    static Map<String,String> historical(boolean manifested) throws Exception {
        return new HashMap<>(M4PhaseAFixtures.cacheFixture(manifested).values);
    }

    /** Observe one atomic candidate and separate confirmed ACK with deterministic disk failures. */
    static final class Backend implements InstallationStore.Backend {
        final Map<String,String> values;
        final List<String> trace=Collections.synchronizedList(new ArrayList<>());
        int candidateWrites,ackWrites,clears;
        boolean candidateWritable=true,ackWritable=true,clearWritable=true;
        Thread clearThread;
        /** Retain the explicit test preference file, leaving identity and credentials in other owners. */
        Backend(Map<String,String> values) {this.values=values;}
        /** Recreated stores share this preference-file consistency monitor. */
        @Override public Object monitor() {return values;}
        /** Reads have no migration/write or runtime side effect. */
        @Override public String get(String key) {return values.get(key);}
        /** Commit a complete batch atomically; a refusal retains the prior readable view. */
        @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {
            if(clear){clears++;clearThread=Thread.currentThread();trace.add("clear");if(!clearWritable)return false;}
            if(puts.containsKey(InstallationStore.SNAPSHOT_KEY)){candidateWrites++;trace.add("commit");if(!candidateWritable)return false;}
            if(puts.containsKey("ackRevision")){ackWrites++;trace.add("ack-persist");if(!ackWritable)return false;}
            Map<String,String> next=clear?new HashMap<>():new HashMap<>(values);
            next.putAll(puts);for(String key:removed)next.remove(key);
            values.clear();values.putAll(next);return true;
        }
    }

    /** Real media cores plus the actual nested OverlayService port implementation and fake windows. */
    static final class Runtime {
        final List<String> trace;
        final SceneRuntimeController controller;
        final MediaCalendarScheduler scheduler;
        final OverlayService.LiveVideoRuntimePorts live;
        final Spy ports;
        long active;
        int maxVisible,shows,loads;
        boolean legacyVisible,manifestVisible,available=true,ownerAllowed=true;
        String failAt;
        boolean throwing;
        ScheduledTrack loaded;
        OverlayManifest armed;
        Thread resetThread;

        /** Supply the same callbacks as the service; fake only native removal and owner identity. */
        Runtime(List<String> trace,java.util.function.BooleanSupplier owner) {
            this.trace=trace;
            controller=new SceneRuntimeController(new SceneRuntimeController.SceneSink() {
                /** Frozen text scenes require no asset acquisition. */
                @Override public boolean preflight(OverlayManifest.Scene scene) {return true;}
                /** Observe an actual regie due event, never ARM visibility. */
                @Override public void show(OverlayManifest.Scene scene) {manifestVisible=true;shows++;observe();}
                /** Native hide is synchronous at this test boundary. */
                @Override public void hide(OverlayManifest.Scene scene) {manifestVisible=false;observe();}
                /** Generation invalidation cannot leave the previous window visible. */
                @Override public void hideAll() {manifestVisible=false;observe();}
            });
            scheduler=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
                /** Route exact tokens through the actual service callback router. */
                @Override public void onDue(String token,String id) {live.onDue(token,id);}
                /** Preserve passive playback through the real binding router. */
                @Override public void onPlayback(String token,boolean playing,boolean freeze) {live.onPlayback(token,playing,freeze);}
                /** Expiry uses the generation captured at real manifest ARM. */
                @Override public void onExpire(String token,String id) {live.onExpire(token,id);}
                /** Mirror eligibility through the same controller/native retirement as production. */
                @Override public void onEligibility(String token,boolean eligible) {live.onEligibility(token,eligible);}
            });
            live=new OverlayService.LiveVideoRuntimePorts(()->ownerAllowed&&owner.getAsBoolean(),
                    ()->available?scheduler:null,()->available?controller:null,
                    ()->{legacyVisible=false;observe();},()->{manifestVisible=false;observe();},
                    revision->{active=revision;trace.add(revision==0?"abort-select":"select");observe();},
                    new OverlayService.LiveVideoRuntimePorts.LegacySink() {
                        /** Observe the exact legacy payload only when the active token matches. */
                        @Override public void due(ScheduledTrack.Event event) {legacyVisible=true;shows++;observe();}
                        /** The fixture owns no visual countdown. */
                        @Override public void playback(boolean playing,boolean freeze) {}
                        /** Loss retires the native substitute synchronously. */
                        @Override public void eligibility(boolean eligible) {if(!eligible)legacyVisible=false;observe();}
                    },eligible->{});
            ports=new Spy();
        }
        /** At every native/runtime transition at most one owner may remain visible. */
        void observe() {
            int visible=(legacyVisible?1:0)+(manifestVisible?1:0);maxVisible=Math.max(maxVisible,visible);
            assertTrue("single visual owner",visible<=1);
        }
        /** Drive the unchanged scheduler at the first prepared comment's exact media position. */
        void due() {
            ScheduledTrack.MediaIdentity media=loaded.mediaIdentity;
            long position=loaded.comments.get(0).startMs;
            live.onSnapshot(new MediaSessionProbe.Snapshot(loaded.targetPackage,
                    android.media.session.PlaybackState.STATE_PLAYING,"PLAYING",position,position,1.0f,0L,
                    media.videoId,media.title,"",media.durationMs));
        }
        /** Exceptional reset delegates to the actual live ports on their owner thread. */
        void reset() {resetThread=Thread.currentThread();trace.add("runtime-reset");live.abortActivation();}

        /** Observe arguments/faults only; every successful operation delegates to actual service ports. */
        final class Spy implements VideoInstallationRuntimePorts {
            /** Inject a refusal after the real operation so partial activation cleanup is exercised. */
            private boolean step(String name,boolean result) {
                trace.add(name);observe();
                if(name.equals(failAt)){if(throwing)throw new IllegalStateException("Injected owner refusal");return false;}
                return result;
            }
            /** Owner checks permit no cleanup/mutation when false. */
            @Override public boolean isOwnerThread() {trace.add("owner");return live.isOwnerThread();}
            /** Retire through the service adapter's native callback. */
            @Override public boolean retireLegacyVisualOwner() {return step("retire-legacy",live.retireLegacyVisualOwner());}
            /** Use the real controller unload and synchronous scene removal. */
            @Override public boolean retireManifestedVisualOwner() {return step("retire-manifested",live.retireManifestedVisualOwner());}
            /** Capture the handler's trusted object identity without parsing or copying it. */
            @Override public boolean loadPreparedVideo(VideoPreparedState state) {
                loaded=state.track;loads++;return step("load",live.loadPreparedVideo(state));
            }
            /** Capture the exact restored manifest passed to the actual controller. */
            @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
                armed=manifest;return step("manifest",live.armPreparedManifest(revision,manifest));
            }
            /** Selection remains the service adapter's exact final validation. */
            @Override public boolean selectActiveRevision(long revision,boolean manifested) {
                return step("selected",live.selectActiveRevision(revision,manifested));
            }
            /** No disk/network collaborator participates in runtime abort. */
            @Override public void abortActivation() {trace.add("abort");live.abortActivation();observe();}
        }
    }

    /** Full actual-client exchange with isolated network bytes and two distinct real executors. */
    static final class Harness implements AutoCloseable {
        final Backend backend;
        final InstallationStore store;
        final PackageInstaller installer;
        final Runtime runtime;
        final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
        final ExecutorService owner=Executors.newSingleThreadExecutor();
        final AtomicBoolean current=new AtomicBoolean(true);
        final CountDownLatch dispatched=new CountDownLatch(1);
        final CloudDeviceCredentials credentials;
        final CloudControlClient client;
        Thread ownerThread,fetchThread,installThread;
        JSONObject assignment,ackBody;
        int gets,acks,installCalls,assignmentStatus=200,ackStatus=200,rotations;
        String getPath;
        boolean badAck,forceOutcome,throwInstall;
        InstallationStatus outcome;
        Runnable beforeInstall=()->{},afterInstall=()->{},onAck=()->{};

        /** Compose frozen/empty storage and the real service ports without Android windows or sockets. */
        Harness(Map<String,String> values,JSONObject assignment) throws Exception {
            DiagnosticsStore.INSTANCE.resetCloudObservations();
            this.assignment=assignment;
            owner.submit(()->ownerThread=Thread.currentThread()).get(3,TimeUnit.SECONDS);
            backend=new Backend(values);store=new InstallationStore(backend);
            installer=new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current());
            runtime=new Runtime(backend.trace,()->Thread.currentThread()==ownerThread);
            credentials=new CloudDeviceCredentials(new M4PhaseAFixtures.Credentials(),new SecretStore.InMemorySecretStore());
            assertTrue(credentials.persistActivation(assignment.getString("deviceId"),"phase-f-device-token",
                    "activation","phase-f-secret","123456"));assertTrue(credentials.confirmClaimed());
            AssignmentMutationGate gate=new AssignmentMutationGate(work->{dispatched.countDown();owner.execute(work);},
                    ()->Thread.currentThread()==ownerThread);
            client=new CloudControlClient(io,credentials,store,(request,ports)->{
                installCalls++;installThread=Thread.currentThread();backend.trace.add("install");beforeInstall.run();
                if(throwInstall)throw new IllegalStateException("Injected installation failure");
                InstallationStatus result=forceOutcome?outcome:installer.install(request,ports);
                backend.trace.add("result:"+(result==null?"NULL":result.name()));afterInstall.run();return result;
            },runtime.ports,runtime::reset,work->{Thread worker=new Thread(work);worker.setDaemon(true);worker.start();},
                    ()->{rotations++;backend.trace.add("rotate");},gate,current::get);
            field(client,"origin","https://m4-phase-f.invalid");field(client,"running",true);
            M4PhaseFHttps.register("m4-phase-f.invalid",url->{
                Harness active=HTTP.get();if(active==null)throw new IOException("Unexpected test exchange");
                return active.connection(url);
            });
        }
        /** Empty durable state follows the same production pipeline. */
        Harness(boolean manifested,long revision) throws Exception {this(new HashMap<>(),envelope(manifested,revision));}
        /** Run the private production fetch on its actual io worker and expose the original bounded cause. */
        void fetch() throws Exception {await(fetchAsync("fetchAssignment"));}
        /** Run actual polling when error classification/offline diagnostics are part of the assertion. */
        void poll() throws Exception {await(fetchAsync("poll"));}
        /** Submit actual network work, permitting deterministic owner blocking/interruption tests. */
        Future<?> fetchAsync(String method) {
            return io.submit(()->{
                HTTP.set(this);fetchThread=Thread.currentThread();
                try {invoke(client,method);return null;}finally {HTTP.remove();}
            });
        }
        /** Complete a fixture call with a bounded timeout and preserve checked owner/transport failures. */
        static void await(Future<?> work) throws Exception {
            try {work.get(5,TimeUnit.SECONDS);}
            catch(java.util.concurrent.ExecutionException failed) {
                Throwable cause=failed.getCause();if(cause instanceof Exception)throw (Exception)cause;
                if(cause instanceof Error)throw (Error)cause;throw new AssertionError("Unexpected fixture failure");
            }
        }
        /** Run runtime observations on the same window owner as production installation. */
        <T> T onOwner(Callable<T> work) throws Exception {return owner.submit(work).get(3,TimeUnit.SECONDS);}
        /** Seed a server-confirmed prior revision outside the installer, then clear stage counts. */
        void prior(boolean manifested,long revision) throws Exception {
            assertEquals(InstallationStatus.ARMED,onOwner(()->installer.install(
                    CloudV1InstallationAdapter.adapt(envelope(manifested,revision),assignment.getString("deviceId")).request(),runtime.ports)));
            assertTrue(store.markAcknowledged(revision));resetCounts();
        }
        /** Keep durable/runtime state while starting an independent trace for the tested delivery. */
        void resetCounts() {backend.candidateWrites=backend.ackWrites=backend.clears=0;backend.trace.clear();runtime.loads=0;}
        /** Substitute only connection bytes; every request uses the production HTTPS serializer/decoder. */
        HttpsURLConnection connection(URL url) {
            return new HttpsURLConnection(url) {
                final ByteArrayOutputStream posted=new ByteArrayOutputStream();
                boolean counted;
                /** The substitute owns no socket. */
                @Override public void connect() {}
                /** There is no network connection to release. */
                @Override public void disconnect() {}
                /** The fixture does not proxy requests. */
                @Override public boolean usingProxy() {return false;}
                /** Capture the exact UTF-8 bytes written by the real client. */
                @Override public OutputStream getOutputStream() {return posted;}
                /** At the actual ACK boundary, prove durability/ARM precede any HTTP confirmation. */
                @Override public int getResponseCode() throws IOException {
                    if(counted)return "GET".equals(getRequestMethod())?assignmentStatus:ackStatus;
                    counted=true;
                    try {
                        if("GET".equals(getRequestMethod())) {
                            gets++;getPath=url.getPath()+"?"+url.getQuery();backend.trace.add("GET");return assignmentStatus;
                        }
                        acks++;ackBody=new JSONObject(new String(posted.toByteArray(),StandardCharsets.UTF_8));
                        assertEquals("POST",getRequestMethod());
                        assertEquals("/api/v1/devices/"+assignment.getString("deviceId")+"/ack",url.getPath());
                        assertEquals(assignment.getLong("revision"),store.read().snapshot().revision());
                        assertEquals(assignment.getLong("revision"),runtime.active);
                        backend.trace.add("ACK");onAck.run();return ackStatus;
                    } catch(org.json.JSONException malformed) {throw new IOException("Invalid fixture response");}
                }
                /** Return canonical server JSON, using the same UTF-8 wire boundary as production. */
                @Override public InputStream getInputStream() throws IOException {
                    try {
                        JSONObject body="GET".equals(getRequestMethod())?assignment:new JSONObject()
                                .put("deviceId",assignment.getString("deviceId"))
                                .put("revision",assignment.getLong("revision")+(badAck?1:0))
                                .put("deliveryStatus","acknowledged");
                        return new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
                    } catch(org.json.JSONException malformed) {throw new IOException("Invalid fixture response");}
                }
                /** Exercise the real error-response decoder without opening a socket. */
                @Override public InputStream getErrorStream() {
                    try {return getInputStream();}catch(IOException malformed){throw new IllegalStateException("Invalid fixture response");}
                }
                /** No TLS session is negotiated for deterministic substituted bytes. */
                @Override public String getCipherSuite() {return "TEST_ONLY";}
                /** No fixture private certificate is required. */
                @Override public Certificate[] getLocalCertificates() {return null;}
                /** The substitute has no remote certificate. */
                @Override public Certificate[] getServerCertificates() {return new Certificate[0];}
            };
        }
        /** Stop both workers so scheduled polls and cancelled owner tasks cannot escape the test. */
        @Override public void close() {client.stop();owner.shutdownNow();}
    }

    /** Access only existing private test entry points/flags; no production test API is added. */
    static void field(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    /** Invoke the actual private subroutine and unwrap its original exception rather than hiding failures. */
    static void invoke(Object target,String name) throws Exception {
        Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);
        try {method.invoke(target);}catch(InvocationTargetException failed) {
            Throwable cause=failed.getCause();if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;throw new AssertionError("Unexpected fixture failure");
        }
    }
    /** Require a bounded failure and forbid an unexpected installation/ACK success. */
    static void refused(Harness harness) throws Exception {
        boolean rejected=false;try {harness.fetch();}catch(Exception expected){rejected=true;}
        assertTrue("delivery must fail closed",rejected);
    }
    /** Static fixtures have no independent installation algorithm. */
    private M4PhaseFFixtures() {}
}
