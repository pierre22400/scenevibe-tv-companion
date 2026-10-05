package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Empty/corrupt/opaque metadata never exposes submitted content or fabricates a runtime/ACK result. */
public final class M4PhaseGDiagnosticsStateTest {
    /** Empty state has neutral generic metadata and coherent old aliases with no disk/runtime work. */
    @Test public void emptyIsExplicitAndZeroValued() {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(new HashMap<>());RuntimeDiagnostics d=h.diagnostics();
        assertEquals(RuntimeDiagnostics.InstallationState.EMPTY,d.installationState);neutral(d);
        assertTrue(DiagnosticsActivity.render(d).contains("Installed package: no"));
        assertEquals("Offline",MainActivity.cloudStatus(null,true,true,h.store.read()));h.unchanged(new HashMap<>());h.disarmed();
    }
    /** Corrupt generic authority cannot leak valid historical residue or appear as usable saved content. */
    @Test public void corruptHasNoContentMetadataOrSavedContentStatus() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(true,false);values.put(InstallationStore.SNAPSHOT_KEY,"sensitive invalid bytes");
        Map<String,String> prior=new HashMap<>(values);M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        RuntimeDiagnostics d=h.diagnostics();assertEquals(RuntimeDiagnostics.InstallationState.CORRUPT,d.installationState);neutral(d);
        assertTrue(DiagnosticsActivity.render(d).contains("Installed package: corrupt"));
        assertFalse(DiagnosticsActivity.render(d).contains("sensitive"));
        assertEquals("Offline",MainActivity.cloudStatus(null,true,true,h.store.read()));h.unchanged(prior);h.disarmed();
    }
    /** A structurally durable opaque artifact stays opaque: even an apparent trackId is never parsed for diagnostics. */
    @Test public void genericOpaqueArtifactsCannotPopulateCompatibilityTrackId() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(false,true));
        InstallationSnapshot prior=h.store.read().snapshot();
        InstallRequest request=M4PhaseGFixtures.request(prior,prior.codecId(),"runtime","{\"trackId\":\"sensitive-content-id\"}");
        assertEquals(InstallationStore.CommitState.COMMITTED,h.store.commit(new InstallationSnapshot(request,prior.handlerId())));
        h.backend.candidateWrites=0;Map<String,String> bytes=new HashMap<>(h.backend.values);
        RuntimeDiagnostics d=h.diagnostics();assertTrue(d.installationPresent);assertNull(d.cachedTrackId);
        assertFalse(DiagnosticsActivity.render(d).contains("sensitive-content-id"));h.unchanged(bytes);h.disarmed();
    }
    /** Metadata readiness never claims a handler exists or executable ARM succeeded. */
    @Test public void readyMetadataAndFailedStartupRemainDistinct() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        InstallationSnapshot prior=h.store.read().snapshot();
        assertEquals(InstallationStore.CommitState.COMMITTED,h.store.commit(new InstallationSnapshot(prior.canonical(),"unknown.local.handler")));
        h.backend.candidateWrites=0;Map<String,String> bytes=new HashMap<>(h.backend.values);
        assertEquals(InstallationStatus.CACHE_FAILED,h.restore());RuntimeDiagnostics d=h.diagnostics();
        assertEquals(RuntimeDiagnostics.InstallationState.READY,d.installationState);assertEquals(15,d.installedRevision);
        assertEquals(13,d.acknowledgedRevision);assertEquals(InstallationStatus.CACHE_FAILED,d.lastStartupRestoreResult);
        assertTrue(DiagnosticsActivity.render(d).contains("Last startup restore: CACHE_FAILED"));h.unchanged(bytes);h.disarmed();
    }
    /** Reflection proves capture retains no snapshot/store/request, byte array, map or parser value. */
    @Test public void diagnosticFieldsAreOnlyImmutableScalarsOrClosedEnums() {
        for(Field field:RuntimeDiagnostics.class.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers()))continue;
            Class<?> type=field.getType();assertTrue(type.isPrimitive()||type==String.class||type.isEnum());
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }
    /** Full identities, artifact text and injected secret-shaped strings never reach the actual rendered snapshot. */
    @Test public void screenNeverLeaksIdentitiesOrArtifactAndSecretValues() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        String installation="installation-full-sensitive-0123456789",device="cloud-full-sensitive-0123456789";
        RuntimeDiagnostics d=RuntimeDiagnostics.installationSnapshot(h.store,h.observed)
                .installationId(installation).cloudDeviceId(device).build();String rendered=DiagnosticsActivity.render(d);
        assertTrue(rendered.contains(RuntimeDiagnostics.abbreviate(installation)));assertTrue(rendered.contains(RuntimeDiagnostics.abbreviate(device)));
        assertFalse(rendered.contains(installation));assertFalse(rendered.contains(device));assertFalse(rendered.contains(M4PhaseAFixtures.UNICODE));
        assertFalse(rendered.contains(new String(h.store.read().snapshot().canonical().artifact("runtime"),StandardCharsets.UTF_8)));
        for(String banned:new String[]{"deviceToken","activationSecret","https://","raw exception"})assertFalse(rendered.contains(banned));
    }
    /** Projection overwrites obsolete compatibility inputs when the authoritative durable read is empty/corrupt. */
    @Test public void aliasesCannotCarryAnOldCacheThroughNewProjection() {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(new HashMap<>());
        RuntimeDiagnostics d=new RuntimeDiagnostics.Builder().cachedTrackPresent(true).cachedTrackId("obsolete")
                .cachedRevision(99).lastAcknowledgedRevision(99).installation(h.store.read()).build();neutral(d);
    }
    /** Exceptional reset neutralizes the historical startup observation but preserves last boot-policy evidence. */
    @Test public void observationalResetClearsStartupWithoutRewritingBootDecision() {
        DiagnosticsStore observed=new DiagnosticsStore();observed.setLastStartupRestoreResult(InstallationStatus.ARMED);
        observed.setLastAutostartDecision(AutostartPolicy.Decision.START);observed.resetCloudObservations();
        assertNull(observed.lastStartupRestoreResult());assertEquals(AutostartPolicy.Decision.START,observed.lastAutostartDecision());
    }
    /** No-content projection has neutral metadata and exact compatibility aliases. */
    private static void neutral(RuntimeDiagnostics d) {
        assertFalse(d.installationPresent);assertFalse(d.cachedTrackPresent);assertEquals(0,d.installedRevision);
        assertEquals(0,d.acknowledgedRevision);assertEquals(0,d.cachedRevision);assertEquals(0,d.lastAcknowledgedRevision);
        assertNull(d.packageCodecId);assertNull(d.packageHandlerId);assertNull(d.cachedTrackId);
    }
}
