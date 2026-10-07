package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Real Cloud client/adapter/installer/store/Phase C handler and owner, with only HTTPS/native/disk seams. */
final class M6PackageFixtures {
    private static final ThreadLocal<Harness> HTTP=new ThreadLocal<>();
    /** Read the exact independently generated Cloud golden; it is never synthesized by a TV parser. */
    static JSONObject banner(long revision) throws Exception {
        try(InputStream input=M6PackageFixtures.class.getResourceAsStream("/m6/banner-package-assignment.json")) {
            if(input==null)throw new IllegalStateException("Missing M6 golden");
            JSONObject result=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));result.put("revision",revision);return result;
        }
    }
    /** Wrap the unchanged existing v1 fixture as an exact generic Video transport body. */
    static JSONObject video(boolean manifested,long revision) throws Exception {
        JSONObject original=M4PhaseFFixtures.envelope(manifested,revision);
        return wrap(original.getString("deviceId"),revision,"video",CloudPackageInstallationAdapter.VIDEO_CODEC,original.toString());
    }
    /** Independent wire-fixture hash binds the original logical UTF-8 body without normalizing Unicode. */
    static String digest(String codec,String version,String body) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((codec+"\0"+version+"\0"+body).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    /** Build only the closed transport shell, leaving actual semantic validation to production code. */
    static JSONObject wrap(String deviceId,long revision,String kind,String codec,String body) throws Exception {
        return new JSONObject().put("version",CloudPackageInstallationAdapter.VERSION).put("deviceId",deviceId).put("revision",revision)
                .put("kind",kind).put("codecId",codec).put("codecVersion","1.0.0").put("body",body).put("deliveryDigest",digest(codec,"1.0.0",body));
    }

    /** One owner and reused controller coordinate both real Video and Phase C Banner runtime ports. */
    static final class Ports implements VideoInstallationRuntimePorts,BannerInstallationRuntimePorts {
        final M4PhaseFFixtures.Runtime video;final LiveBannerRuntimePorts banner;
        final java.util.List<String> trace;final java.util.function.BooleanSupplier owner;
        boolean failSelection;int bannerLoads;
        /** Share owner/controller and retire the opposite temporal path synchronously. */
        Ports(java.util.List<String> trace,java.util.function.BooleanSupplier owner) {
            this.trace=trace;this.owner=owner;video=new M4PhaseFFixtures.Runtime(trace,owner);
            banner=new LiveBannerRuntimePorts(owner,()->video.controller,video.live::abortActivation,()->video.scheduler.clear(),
                    ()->{},revision->trace.add("banner-select:"+revision),eligible->{});
        }
        /** Both kinds execute on this one owner thread. */
        @Override public boolean isOwnerThread() {return owner.getAsBoolean();}
        /** Video ARM first neutralizes any active Banner binding. */
        @Override public boolean retireLegacyVisualOwner() {banner.abortActivation();return video.ports.retireLegacyVisualOwner();}
        /** Both kinds synchronously retire the previously manifested owner before preparation is loaded. */
        @Override public boolean retireManifestedVisualOwner() {banner.abortActivation();return video.ports.retireManifestedVisualOwner();}
        /** Banner ARM retires the exact Video runtime and MEDIA callbacks. */
        @Override public boolean retireVideoVisualOwner() {return banner.retireVideoVisualOwner();}
        /** Load only trusted already-restored Video state. */
        @Override public boolean loadPreparedVideo(VideoPreparedState state) {return video.ports.loadPreparedVideo(state);}
        /** Load only trusted Phase C Banner state under pending ownership. */
        @Override public boolean loadPreparedBanner(BannerPreparedState state) {bannerLoads++;trace.add("banner-load");return banner.loadPreparedBanner(state);}
        /** The loaded product decides which existing manifest ARM port owns the call. */
        @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
            trace.add("manifest-arm");return "banner".equals(manifest.product)?banner.armPreparedManifest(revision,manifest):video.ports.armPreparedManifest(revision,manifest);
        }
        /** Promote Video only after its real ordered ARM operations. */
        @Override public boolean selectActiveRevision(long revision,boolean manifested) {return !failSelection&&video.ports.selectActiveRevision(revision,manifested);}
        /** Promote Banner through the real Phase C owner; a refusal leaves the durable revision pending. */
        @Override public boolean selectActiveBanner(long revision) {return !failSelection&&banner.selectActiveBanner(revision);}
        /** Runtime callback remains the existing pure WALL-result route. */
        @Override public void onWallResult(String token,WallCalendarScheduler.Result result) {banner.onWallResult(token,result);}
        /** Partial activation retires both possible owners without touching credentials or storage. */
        @Override public void abortActivation() {banner.abortActivation();video.live.abortActivation();}
    }

    /** Run the actual generic fetch on io and the actual installation/proof/ACK persistence on a distinct owner. */
    static final class Harness implements AutoCloseable {
        final M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(new HashMap<>());
        final InstallationStore store=new InstallationStore(backend);
        final PackageInstaller installer=new PackageInstaller(store,OverlayInstallationHandlers.registry(),TvCapabilities.current());
        final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
        final ExecutorService owner=Executors.newSingleThreadExecutor();
        final AtomicBoolean current=new AtomicBoolean(true);
        final CloudControlClient client;final Ports ports;
        Thread ownerThread,installThread;JSONObject assignment,ackBody;
        int gets,acks,installCalls,ackStatus=200,getStatus=200;boolean badAck;
        String getPath;Runnable beforeInstall=()->{},afterInstall=()->{},onAck=()->{};
        /** Select PACKAGE_V1 on the existing client and reuse the established isolated HTTPS factory. */
        Harness(JSONObject assignment) throws Exception {
            this.assignment=assignment;owner.submit(()->ownerThread=Thread.currentThread()).get(3,TimeUnit.SECONDS);
            ports=new Ports(backend.trace,()->Thread.currentThread()==ownerThread);
            CloudDeviceCredentials credentials=new CloudDeviceCredentials(new M4PhaseAFixtures.Credentials(),new SecretStore.InMemorySecretStore());
            assertTrue(credentials.persistActivation(assignment.getString("deviceId"),"m6-device-token","activation","secret","123456"));assertTrue(credentials.confirmClaimed());
            AssignmentMutationGate gate=new AssignmentMutationGate(owner::execute,()->Thread.currentThread()==ownerThread);
            client=new CloudControlClient(io,credentials,store,(request,runtime)->{
                installThread=Thread.currentThread();installCalls++;backend.trace.add("install");beforeInstall.run();
                InstallationStatus result=installer.install(request,runtime);backend.trace.add("result:"+result);afterInstall.run();return result;
            },ports,ports::abortActivation,Runnable::run,()->{},gate,current::get,CloudControlClient.TransportMode.PACKAGE_V1);
            M4PhaseFFixtures.field(client,"origin","https://m4-phase-f.invalid");M4PhaseFFixtures.field(client,"running",true);
            M4PhaseFHttps.register("m4-phase-f.invalid",url->{Harness active=HTTP.get();if(active==null)throw new IOException("Unexpected M6 exchange");return active.connection(url);});
        }
        /** Fetch through the private actual client subroutine, with a bounded test deadline. */
        void fetch() throws Exception {
            M4PhaseFFixtures.Harness.await(io.submit(()->{HTTP.set(this);try {M4PhaseFFixtures.invoke(client,"fetchAssignment");return null;}finally {HTTP.remove();}}));
        }
        /** Direct offline install uses the same real owner and installer, without a network or ACK. */
        InstallationStatus install(InstallRequest request) throws Exception {return owner.submit(()->installer.install(request,ports)).get(3,TimeUnit.SECONDS);}
        /** Provide deterministic response bytes and assert exact durable/ARM-before-ACK ordering. */
        HttpsURLConnection connection(URL url) {
            return new HttpsURLConnection(url) {
                final ByteArrayOutputStream posted=new ByteArrayOutputStream();boolean counted;
                /** No socket is allocated by this isolated test transport. */
                @Override public void connect() {}
                /** The in-memory exchange has no remote resources. */
                @Override public void disconnect() {}
                /** No proxy or network fallback is allowed. */
                @Override public boolean usingProxy() {return false;}
                /** Capture exactly what the real HTTPS encoder posts. */
                @Override public OutputStream getOutputStream() {return posted;}
                /** The ACK boundary observes durability and actual successful owner installation. */
                @Override public int getResponseCode() throws IOException {
                    if(!counted) {
                        counted=true;
                        try {
                            if("GET".equals(getRequestMethod())) {gets++;getPath=url.getPath()+"?"+url.getQuery();backend.trace.add("GET");}
                            else {acks++;ackBody=new JSONObject(new String(posted.toByteArray(),StandardCharsets.UTF_8));
                                assertTrue(url.getPath().endsWith("/package-ack"));assertTrue(backend.trace.contains("result:ARMED"));
                                assertEquals(store.read().snapshot().revision(),ackBody.getLong("revision"));backend.trace.add("ACK");onAck.run();}
                        } catch(Exception invalid) {throw new IOException("Invalid M6 fixture",invalid);}
                    }
                    return "GET".equals(getRequestMethod())?getStatus:ackStatus;
                }
                /** Return the actual closed transport/ACK wire shell, not a copied installation implementation. */
                @Override public InputStream getInputStream() throws IOException {
                    try {JSONObject body="GET".equals(getRequestMethod())?assignment:new JSONObject().put("deviceId",assignment.getString("deviceId"))
                            .put("revision",ackBody.getLong("revision")+(badAck?1:0)).put("status","acknowledged");
                        return new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8));
                    } catch(Exception invalid) {throw new IOException("Invalid M6 fixture",invalid);}
                }
                /** The real client also parses bounded error responses through this byte seam. */
                @Override public InputStream getErrorStream() {try {return getInputStream();}catch(IOException invalid){throw new IllegalStateException("Fixture error");}}
                /** TLS is deliberately replaced by exact bytes in this JVM qualification. */
                @Override public String getCipherSuite() {return "TEST_ONLY";}
                /** There are no local certificates in the deterministic exchange. */
                @Override public Certificate[] getLocalCertificates() {return null;}
                /** There are no remote certificates or sockets in this exchange. */
                @Override public Certificate[] getServerCertificates() {return new Certificate[0];}
            };
        }
        /** Both executor lifetimes end with the fixture, preventing any second poller or leaked task. */
        @Override public void close() {client.stop();owner.shutdownNow();}
    }
    /** Require a bounded refusal and never treat failed delivery as a successful test. */
    static void refused(Harness harness) throws Exception {boolean refused=false;try {harness.fetch();}catch(Exception expected){refused=true;}assertTrue(refused);}
    /** Fixtures own no independent transport or installer policy. */
    private M6PackageFixtures() {}
}
