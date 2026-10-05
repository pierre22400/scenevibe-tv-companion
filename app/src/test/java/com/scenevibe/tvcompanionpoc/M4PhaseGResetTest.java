package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Stopped-service explicit reset uses real identity/credential owners and one real generic whole-file clear. */
@RunWith(Parameterized.class)
public final class M4PhaseGResetTest {
    private final boolean manifested,generic;
    /** Each persisted representation must be erased by the same reset action. */
    public M4PhaseGResetTest(boolean manifested, boolean generic) {this.manifested=manifested;this.generic=generic;}
    /** Generic/historical and legacy/manifested reset semantics are independent. */
    @Parameterized.Parameters(name="manifested={0},generic={1}") public static Collection<Object[]> profiles() {
        return M4PhaseGFixtures.profiles();
    }
    /** Successful explicit reset rotates only installation identity, drops credentials and clears every cache/ACK key. */
    @Test public void stoppedResetClearsWholeInstallationAndRotatesSeparateIdentity() throws Exception {
        Reset h=new Reset(M4PhaseGFixtures.durable(manifested,generic));String prior=h.identity.peekInstallationId();
        assertTrue(h.reset());assertNotEquals(prior,h.identity.peekInstallationId());assertEquals(1,h.identityWrites);
        assertNull(h.credentials.cloudDeviceId());assertNull(h.credentials.deviceToken());assertFalse(h.credentials.connected());
        assertEquals(1,h.startup.backend.clears);assertTrue(h.startup.backend.values.isEmpty());
        assertEquals(InstallationStore.ReadState.EMPTY,h.startup.store.read().state());assertEquals(0,h.startup.store.read().acknowledgedRevision());
        assertEquals(0,h.startup.backend.candidateWrites+h.startup.backend.ackWrites);
        assertEquals(java.util.Arrays.asList("rotate","credentials"),h.order);
        assertEquals("preserved-lan-pairing-id",h.pairing.get("deviceId"));
        assertEquals(0,h.startup.observed.lastAssignmentRevisionReceived());assertEquals(0,h.startup.observed.lastSuccessfulAckRevision());
        assertNull(h.startup.observed.lastStartupRestoreResult());h.startup.disarmed();
    }
    /** A refused clear never fabricates empty state, while deliberate identity/credential reset remains completed. */
    @Test public void stoppedResetClearRefusalReportsBoundedFailureAndPreservesDurableBytes() throws Exception {
        Reset h=new Reset(M4PhaseGFixtures.durable(manifested,generic));Map<String,String> prior=new HashMap<>(h.startup.backend.values);
        h.startup.backend.clearWritable=false;String identity=h.identity.peekInstallationId();
        assertFalse(h.reset());assertEquals(prior,h.startup.backend.values);
        assertEquals(InstallationStore.ReadState.SNAPSHOT,h.startup.store.read().state());assertEquals(13,h.startup.store.read().acknowledgedRevision());
        assertNotEquals(identity,h.identity.peekInstallationId());assertNull(h.credentials.deviceToken());
        assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,h.startup.observed.lastCloudErrorCode());
        assertNull(h.startup.observed.lastStartupRestoreResult());assertEquals(1,h.startup.backend.clears);
        h.startup.disarmed();
    }
    /** Identity persistence refusal is bounded and cannot go on to destroy credentials or installation data. */
    @Test public void identityRotationFailureStopsDestructiveResetAndDoesNotLeakCause() throws Exception {
        Reset h=new Reset(M4PhaseGFixtures.durable(manifested,generic));Map<String,String> prior=new HashMap<>(h.startup.backend.values);
        assertFalse(DiagnosticsActivity.resetStoppedInstallation(h.startup.store,
                ()->{throw new IllegalStateException("sensitive identity persistence cause");},h.credentials::reset,h.startup.observed));
        h.startup.unchanged(prior);assertNotNull(h.credentials.deviceToken());assertEquals(0,h.identityWrites);
        assertEquals(RuntimeDiagnostics.CloudErrorCode.NETWORK,h.startup.observed.lastCloudErrorCode());
        assertFalse(DiagnosticsActivity.render(h.startup.diagnostics()).contains("sensitive"));
    }
    /** Independently persisted identity, LAN pairing and encrypted Cloud credentials with no running service/client. */
    private static final class Reset {
        final M4PhaseGFixtures.Startup startup;
        final Map<String,String> identities=new HashMap<>(),pairing=new HashMap<>();
        final List<String> order=new ArrayList<>();
        final InstallationIdentity identity;
        final CloudDeviceCredentials credentials;
        int identityWrites;
        /** Preserve an already-paired identity and real private credentials before the exceptional action. */
        Reset(Map<String,String> values) {
            startup=new M4PhaseGFixtures.Startup(values);identities.put(InstallationIdentity.KEY_INSTALLATION_ID,"existing-installation-id");
            pairing.put("deviceId","preserved-lan-pairing-id");
            identity=new InstallationIdentity(new InstallationIdentity.Storage() {
                /** Read only the independent installation preference map. */
                @Override public String get(String key) {return identities.get(key);}
                /** Observe the real owner's one durable rotation, never cache clearing or pairing mutation. */
                @Override public void put(String key,String value) {identityWrites++;order.add("rotate");identities.put(key,value);}
            },()->pairing.get("deviceId"),new SecureRandom());
            credentials=new CloudDeviceCredentials(new M4PhaseAFixtures.Credentials(),new SecretStore.InMemorySecretStore());
            assertTrue(credentials.persistActivation("old-cloud-device","test-device-token","activation","test-secret","123456"));
            assertTrue(credentials.confirmClaimed());startup.observed.setLastAssignmentRevisionReceived(15);
            startup.observed.setLastSuccessfulAckRevision(13);startup.observed.setLastStartupRestoreResult(InstallationStatus.ARMED);
        }
        /** Invoke the actual screen's stopped-service seam, preserving production operation order. */
        boolean reset() {
            return DiagnosticsActivity.resetStoppedInstallation(startup.store,identity::rotateForCloudReset,
                    ()->{order.add("credentials");credentials.reset();},startup.observed);
        }
    }
}
