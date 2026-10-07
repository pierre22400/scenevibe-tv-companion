package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual generic transport/Phase C lifecycle, with exact cross-repository Unicode and durable proof assertions. */
public final class M6PackageTransportTest {
    /** Adapt through the real static boundary with the current durable revision. */
    private static InstallRequest adapt(JSONObject wire,InstallationStore store) throws Exception {
        return CloudPackageInstallationAdapter.adapt(wire,wire.getString("deviceId"),store.read());
    }
    /** The independently generated Cloud golden reaches the actual Phase C handler and owner, before ACK. */
    @Test public void cloudGoldenAcceptsThroughRealHandlerAndExactArmedProof() throws Exception {
        JSONObject golden=M6PackageFixtures.banner(7);
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(golden)) {
            h.fetch();assertEquals(7,h.store.read().acknowledgedRevision());assertEquals(1,h.acks);
            assertEquals(h.ownerThread,h.installThread);assertEquals(1,h.backend.candidateWrites);
            String body=golden.getString("body");assertTrue(body.contains("ASCII | café | cafe\u0301 | 😀"));
            assertArrayEquals(body.getBytes(StandardCharsets.UTF_8),h.store.read().snapshot().canonical().artifact("banner"));
            assertEquals(4,h.ackBody.length());assertEquals(golden.getString("deliveryDigest"),h.ackBody.getString("deliveryDigest"));
            assertEquals(golden.getString("codecId"),h.ackBody.getString("codecId"));assertEquals("1.0.0",h.ackBody.getString("codecVersion"));
            assertFalse(h.ackBody.has("publicationId"));assertFalse(h.ackBody.has("finalTrackId"));
            assertTrue(h.backend.trace.indexOf("commit")<h.backend.trace.indexOf("banner-load"));
            assertTrue(h.backend.trace.indexOf("result:ARMED")<h.backend.trace.indexOf("ACK"));
            assertTrue(h.backend.trace.indexOf("ACK")<h.backend.trace.indexOf("ack-persist"));
            assertTrue(h.getPath.endsWith("/package-assignment?afterRevision=0"));
            assertNotNull(h.owner.submit(()->h.ports.banner.activeState()).get());assertFalse(h.ports.video.controller.hasVisibleScene());
        }
    }
    /** Both Video shapes preserve exact v1 bytes and the inherited one/two-artifact capability contract. */
    @Test public void genericVideoUsesExistingV1AdapterAndExactSingleArtifact() throws Exception {
        for(boolean manifested:new boolean[]{false,true}) {
            JSONObject wire=M6PackageFixtures.video(manifested,8);
            try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(wire)) {
                h.fetch();assertEquals(8,h.store.read().acknowledgedRevision());assertEquals(1,h.acks);
                InstallRequest saved=h.store.read().snapshot().canonical();assertEquals(manifested?2:1,saved.artifactCount());
                assertArrayEquals(wire.getString("body").getBytes(StandardCharsets.UTF_8),saved.artifact("cloud-video"));
                assertEquals(manifested,h.ports.video.controller.hasActiveManifest());
                assertEquals(wire.getString("deliveryDigest"),h.ackBody.getString("deliveryDigest"));
            }
        }
    }
    /** Same revision ignores changed kind/codec/body/digest and proves only the original restored Banner bytes. */
    @Test public void sameRevisionIgnoresAllIncomingSemanticDataAndRestoresDurableProof() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.ackStatus=500;M6PackageFixtures.refused(h);String digest=h.ackBody.getString("deliveryDigest");
            String bytes=h.backend.values.get(InstallationStore.SNAPSHOT_KEY);
            h.assignment.put("kind","video").put("codecId","unknown").put("codecVersion","99").put("body",new JSONObject()).put("deliveryDigest","forged");
            h.ackStatus=200;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(bytes,h.backend.values.get(InstallationStore.SNAPSHOT_KEY));
            assertEquals(digest,h.ackBody.getString("deliveryDigest"));assertEquals(TvCapabilities.CODEC_BANNER_WALL_OVERLAY,h.ackBody.getString("codecId"));
            assertEquals(7,h.store.read().acknowledgedRevision());
        }
    }
    /** Stale revision is rejected by the installer before handler, ARM, store mutation or network ACK. */
    @Test public void staleUnknownBodyNeverArmsOrAcknowledges() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.fetch();Map<String,String> before=new HashMap<>(h.backend.values);int loads=h.ports.bannerLoads;
            h.assignment.put("revision",6).put("kind","unknown").put("body",false);M6PackageFixtures.refused(h);
            assertEquals(before,h.backend.values);assertEquals(loads,h.ports.bannerLoads);assertEquals(1,h.acks);
            assertTrue(h.backend.trace.contains("result:STALE"));
        }
    }
    /** Commit refusal preserves prior durable/ACK/runtime state and permits no ACK. */
    @Test public void failedCommitNeverArmsOrAcknowledges() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.backend.candidateWritable=false;M6PackageFixtures.refused(h);
            assertEquals(InstallationStore.ReadState.EMPTY,h.store.read().state());assertEquals(0,h.ports.bannerLoads);assertEquals(0,h.acks);
        }
    }
    /** ARM refusal leaves a pending candidate; same revision recovers without repersisting. */
    @Test public void pendingArmFailureRecoversThenAcknowledgesExactSnapshot() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.ports.failSelection=true;M6PackageFixtures.refused(h);assertEquals(7,h.store.read().snapshot().revision());
            assertEquals(0,h.store.read().acknowledgedRevision());assertEquals(0,h.acks);
            h.ports.failSelection=false;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(7,h.store.read().acknowledgedRevision());
        }
    }
    /** A network ACK failure leaves the new revision locally pending and keeps the conditional floor at its ACK. */
    @Test public void networkFailureDoesNotAdvanceAcknowledgedRevision() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.ackStatus=500;M6PackageFixtures.refused(h);assertEquals(0,h.store.read().acknowledgedRevision());
            h.ackStatus=200;h.fetch();assertTrue(h.getPath.endsWith("?afterRevision=0"));assertEquals(1,h.backend.candidateWrites);
            assertEquals(7,h.store.read().acknowledgedRevision());
        }
    }
    /** Server confirmation must match the closed identity/revision/status before local ACK persistence. */
    @Test public void malformedAckNeverPersistsConfirmation() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.badAck=true;M6PackageFixtures.refused(h);assertEquals(0,h.store.read().acknowledgedRevision());assertEquals(0,h.backend.ackWrites);
        }
    }
    /** A failed separate ACK commit leaves the same exact candidate safely retryable. */
    @Test public void failedLocalConfirmationRemainsPending() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.backend.ackWritable=false;M6PackageFixtures.refused(h);assertEquals(0,h.store.read().acknowledgedRevision());
            h.backend.ackWritable=true;h.fetch();assertEquals(1,h.backend.candidateWrites);assertEquals(7,h.store.read().acknowledgedRevision());
        }
    }
    /** Replaced/stopped clients cannot send proof after installation or persist a returned server confirmation. */
    @Test public void lifetimeReplacementSuppressesAckAndLocalConfirmation() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.afterInstall=()->h.current.set(false);h.fetch();assertEquals(0,h.acks);assertEquals(0,h.store.read().acknowledgedRevision());
        }
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.onAck=()->h.current.set(false);M6PackageFixtures.refused(h);assertEquals(0,h.store.read().acknowledgedRevision());
        }
    }
    /** A new unknown kind/codec/version or altered logical body/digest is rejected before install or persistence. */
    @Test public void newerMalformedEnvelopeFailsBeforeInstallation() throws Exception {
        for(String field:new String[]{"kind","codecId","codecVersion","deliveryDigest","version","deviceId"}) {
            JSONObject wire=M6PackageFixtures.banner(7);wire.put(field,"unknown");
            try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
                h.assignment=wire;M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);assertEquals(0,h.acks);assertEquals(0,h.backend.candidateWrites);
            }
        }
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.assignment.put("body",h.assignment.getString("body").replace("café","cafe\u0301"));M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);
        }
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            JSONObject profile=new JSONObject(h.assignment.getString("body"));profile.getJSONArray("windows").getJSONObject(0).put("eventId","broken-bijection");
            h.assignment=M6PackageFixtures.wrap(h.assignment.getString("deviceId"),7,"banner",h.assignment.getString("codecId"),profile.toString());
            M6PackageFixtures.refused(h);assertEquals(1,h.installCalls);assertEquals(0,h.backend.candidateWrites);assertEquals(0,h.acks);
        }
    }
    /** Numeric outer bounds never accept unsafe/fractional/non-positive/coercible revisions. */
    @Test public void invalidRevisionsFailClosed() throws Exception {
        for(Object revision:new Object[]{0,-1,1.5,9_007_199_254_740_992d,"7"}) {
            try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
                h.assignment.put("revision",revision);M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);assertEquals(0,h.acks);
            }
        }
    }
    /** Closed transport forbids unknown and absent outer fields before semantic installation. */
    @Test public void extraAndMissingFieldsAreRejected() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.assignment.put("publicationId","internal");M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);
        }
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.assignment.remove("deliveryDigest");M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);
        }
    }
    /** A corrupt durable tuple cannot authorize adaptation, restoration or any proof ACK. */
    @Test public void corruptDurableBlocksAllInstallationAndAck() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.backend.values.put(InstallationStore.SNAPSHOT_KEY,"corrupt");M6PackageFixtures.refused(h);assertEquals(0,h.gets);assertEquals(0,h.acks);
        }
    }
    /** A historical Video snapshot can restore locally but lacks the exact original body required for generic proof. */
    @Test public void historicalVideoSameRevisionCannotInventGenericProof() throws Exception {
        JSONObject wire=M6PackageFixtures.video(true,7);
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(wire)) {
            JSONObject old=new JSONObject(wire.getString("body"));
            assertEquals(InstallationStatus.ARMED,h.install(CloudV1InstallationAdapter.adapt(old,old.getString("deviceId")).request()));
            M6PackageFixtures.refused(h);assertEquals(0,h.acks);assertEquals(0,h.store.read().acknowledgedRevision());
            assertEquals(1,h.backend.candidateWrites);
        }
    }
    /** Alternating kinds use one snapshot, one owner/controller and monotonically supplied revisions. */
    @Test public void videoBannerVideoReplaceOneDurableOwner() throws Exception {
        JSONObject first=M6PackageFixtures.video(true,5);String device=first.getString("deviceId");
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(first)) {
            h.fetch();h.assignment=M6PackageFixtures.banner(6).put("deviceId",device);h.fetch();
            assertNotNull(h.owner.submit(()->h.ports.banner.activeState()).get());assertEquals(0,h.ports.video.active);
            h.assignment=M6PackageFixtures.video(false,7);h.fetch();assertNull(h.owner.submit(()->h.ports.banner.activeState()).get());
            assertEquals(7,h.ports.video.active);assertEquals(7,h.store.read().acknowledgedRevision());assertEquals(3,h.backend.candidateWrites);
        }
    }
    /** Offline restoration uses the exact durable body and a fresh Phase C temporal owner with no implicit ACK. */
    @Test public void offlineRestoreHasFreshOwnerAndNoNetworkAck() throws Exception {
        try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
            h.ackStatus=500;M6PackageFixtures.refused(h);InstallRequest saved=h.store.read().snapshot().canonical();
            h.ports.abortActivation();assertEquals(InstallationStatus.ARMED,h.install(saved));
            assertFalse(h.ports.video.controller.hasVisibleScene());assertEquals(0,h.store.read().acknowledgedRevision());assertEquals(1,h.acks);
        }
    }
    /** Oversized UTF-8 and unpaired surrogates are rejected before a handler can see substituted bytes. */
    @Test public void unicodeAndBodyCeilingsFailClosedBeforeInstall() throws Exception {
        for(String body:new String[]{"é".repeat(524289),"\ud800"}) {
            try(M6PackageFixtures.Harness h=new M6PackageFixtures.Harness(M6PackageFixtures.banner(7))) {
                JSONObject wire=M6PackageFixtures.wrap(h.assignment.getString("deviceId"),7,"banner",h.assignment.getString("codecId"),body);
                assertThrows(IllegalArgumentException.class,()->adapt(wire,h.store));
                h.assignment=wire;M6PackageFixtures.refused(h);assertEquals(0,h.installCalls);assertEquals(0,h.acks);
            }
        }
    }
    /** Public capabilities and the live constructor retain the Phase C deferral to the separately authorized Phase E. */
    @Test public void liveWallClockCapabilityRemainsFalse() {assertFalse(TvCapabilities.current().supportsWallClockExecution());}
}
