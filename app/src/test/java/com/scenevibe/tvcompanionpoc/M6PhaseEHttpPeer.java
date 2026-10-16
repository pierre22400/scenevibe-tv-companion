package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Disposable cross-repository HTTP peer, never a Sony or production entry point.
 * The real Cloud client, common runtime ports, handler and installer run unchanged.
 * A reserved HTTPS test host forwards bytes over a real loopback HTTP socket to
 * the real Cloud handlers/Postgres. TLS is outside this loopback software proof.
 */
public final class M6PhaseEHttpPeer {
    /** Run three actual fetch/install/ARM/ACK cycles on one client, store and owner. */
    public static void main(String[] args) throws Exception {
        if(args.length!=2||!args[0].matches("http://127\\.0\\.0\\.1:[0-9]{1,5}"))
            throw new IllegalArgumentException("Disposable loopback origin required");
        JSONObject initial=M6PackageFixtures.video(true,1).put("deviceId",args[1]);
        List<Long> revisions=new ArrayList<>();List<String> digests=new ArrayList<>();
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(initial)) {
            OverlayRuntimePorts ports=new OverlayRuntimePorts(h.ports.video.live,h.ports.banner);
            PackageInstaller installer=new PackageInstaller(h.store,OverlayInstallationHandlers.registry(),TvCapabilities.packageQualification());
            M4PhaseFFixtures.field(h.client,"runtimePorts",ports);
            M4PhaseFFixtures.field(h.client,"installation",(CloudControlClient.InstallationOperation)(request,runtime)->{
                InstallationStatus result=installer.install(request,runtime);
                if(result!=InstallationStatus.ARMED)throw new IllegalStateException("Peer ARM refused");
                return result;
            });
            M4PhaseFHttps.register("m4-phase-f.invalid",url->bridge(url,args[0]));
            for(int step=0;step<3;step++) {
                h.fetch();long revision=h.store.read().acknowledgedRevision();
                if(revision!=step+1)throw new IllegalStateException("Peer revision mismatch");
                JSONObject proof=CloudPackageInstallationAdapter.proof(h.store.read().snapshot(),revision);
                revisions.add(revision);digests.add(proof.getString("deliveryDigest"));
                if(step==1&&h.owner.submit(()->h.ports.banner.activeState()).get()==null)
                    throw new IllegalStateException("Banner not active");
                if(step==2&&h.owner.submit(()->h.ports.banner.activeState()).get()!=null)
                    throw new IllegalStateException("Banner survived Video replacement");
            }
            System.out.println(new JSONObject().put("revisions",revisions).put("digests",digests)
                    .put("candidateWrites",h.backend.candidateWrites).put("wallCapability",true)
                    .put("finalAcknowledgedRevision",h.store.read().acknowledgedRevision()).toString());
        }
    }

    /** Forward the client's actual URL/method/headers/body to one bounded real HTTP socket. */
    private static HttpsURLConnection bridge(URL url,String loopback) throws java.io.IOException {
        HttpURLConnection actual=(HttpURLConnection)new URL(loopback+url.getFile()).openConnection();
        return new HttpsURLConnection(url) {
            boolean prepared;
            /** Copy actual client settings once before a request is sent. */
            private void prepare() throws java.io.IOException {
                if(prepared)return;prepared=true;
                actual.setRequestMethod(getRequestMethod());actual.setConnectTimeout(8000);actual.setReadTimeout(8000);
                actual.setDoOutput(getDoOutput());actual.setInstanceFollowRedirects(false);
                for(java.util.Map.Entry<String,List<String>> header:getRequestProperties().entrySet())
                    for(String value:header.getValue())actual.addRequestProperty(header.getKey(),value);
            }
            /** Open the genuine socket only after copying the client configuration. */
            @Override public void connect() throws java.io.IOException {prepare();actual.connect();}
            /** Release the sole disposable socket. */
            @Override public void disconnect() {actual.disconnect();}
            /** Loopback qualification uses no external proxy. */
            @Override public boolean usingProxy() {return false;}
            /** Write the exact proof bytes emitted by the real TV client. */
            @Override public OutputStream getOutputStream() throws java.io.IOException {prepare();return actual.getOutputStream();}
            /** Read the real server status rather than a fixture-controlled success. */
            @Override public int getResponseCode() throws java.io.IOException {prepare();return actual.getResponseCode();}
            /** Read the actual sealed envelope or Cloud ACK response. */
            @Override public InputStream getInputStream() throws java.io.IOException {prepare();return actual.getInputStream();}
            /** Keep bounded Cloud error responses on the existing client error path. */
            @Override public InputStream getErrorStream() {try {prepare();return actual.getErrorStream();}catch(java.io.IOException refused){return null;}}
            /** This disposable bridge explicitly does not claim TLS qualification. */
            @Override public String getCipherSuite() {return "LOOPBACK_HTTP_ONLY";}
            /** No private certificate is loaded into this software-only peer. */
            @Override public Certificate[] getLocalCertificates() {return null;}
            /** Certificate trust is exercised separately by the deployed HTTPS candidate. */
            @Override public Certificate[] getServerCertificates() {return new Certificate[0];}
        };
    }
    /** Static qualification runner owns no extra Android service or production polling loop. */
    private M6PhaseEHttpPeer() {}
}
