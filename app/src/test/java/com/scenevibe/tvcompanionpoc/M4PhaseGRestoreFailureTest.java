package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Durable representation, binding and capability faults cannot clear/migrate/fallback or become active. */
@RunWith(Parameterized.class)
public final class M4PhaseGRestoreFailureTest {
    private final String fault;
    /** Choose a real durable fault independently of the startup algorithm. */
    public M4PhaseGRestoreFailureTest(String fault) {this.fault=fault;}
    /** Cover generic marker precedence, separate ACK coherence, historical semantics and durable handler authority. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> faults() {
        return Arrays.asList(new Object[][]{{"marker"},{"empty-marker"},{"future-format"},{"future-ack"},
                {"invalid-ack"},{"orphan-ack"},{"partial-historical"},{"historical-invalid-runtime"},
                {"historical-invalid-manifest"},{"generic-invalid-runtime"},{"generic-invalid-manifest"},
                {"unknown-handler"},{"mismatched-handler"},{"unknown-codec"},{"missing-artifact"},
                {"generic-continue"},{"historical-continue"},{"generic-wall"},{"historical-wall"}});
    }
    /** No refusal rewrites a durable byte or confirms ACK, even if historical residue looks executable. */
    @Test public void startupRefusalKeepsExactDurableBytesAckAndNoActiveRevision() throws Exception {
        boolean generic=!fault.startsWith("historical")&&!"partial-historical".equals(fault);
        Map<String,String> values=M4PhaseGFixtures.durable(true,generic);
        InstallationStatus expected=InstallationStatus.CACHE_FAILED;
        if("marker".equals(fault))values.put(InstallationStore.SNAPSHOT_KEY,"corrupt-sensitive-input");
        else if("empty-marker".equals(fault))values.put(InstallationStore.SNAPSHOT_KEY,"");
        else if("future-format".equals(fault))values.put(InstallationStore.SNAPSHOT_KEY,
                values.get(InstallationStore.SNAPSHOT_KEY).replace(InstallationStore.FORMAT_VERSION,"future-format"));
        else if("future-ack".equals(fault))values.put("ackRevision","99");
        else if("invalid-ack".equals(fault))values.put("ackRevision","raw-sensitive-ack");
        else if("orphan-ack".equals(fault)){values.clear();values.put("ackRevision","13");}
        else if("partial-historical".equals(fault))values.remove("runtime");
        else {
            InstallationStore store=new InstallationStore(new M4PhaseFFixtures.Backend(values));
            InstallationSnapshot snapshot=store.read().snapshot();InstallRequest request=snapshot.canonical();
            String handler=snapshot.handlerId();
            if("unknown-handler".equals(fault))handler="unknown.local.handler";
            else if("mismatched-handler".equals(fault))handler=InstallationStore.COMPAT_TRACK_HANDLER_ID;
            else if("unknown-codec".equals(fault))request=new InstallRequest(snapshot.revision(),"unknown.local.codec",request.artifacts());
            else {
                expected=InstallationStatus.ARM_FAILED;
                String name=fault.endsWith("runtime")?"runtime":"manifest";
                String artifact;
                if(fault.endsWith("continue")||fault.endsWith("wall")) {
                    JSONObject manifest=new JSONObject(values.get("manifest"));
                    manifest.getJSONObject("clock").put(fault.endsWith("continue")?"pauseBehavior":"mode",
                            fault.endsWith("continue")?"continue":"wall");artifact=manifest.toString();
                } else artifact="missing-artifact".equals(fault)?null:"{raw-sensitive-invalid-artifact";
                if(generic)request=M4PhaseGFixtures.request(snapshot,snapshot.codecId(),name,artifact);
                else values.put(name,artifact);
            }
            if(generic)assertEquals(InstallationStore.CommitState.COMMITTED,
                    store.commit(new InstallationSnapshot(request,handler)));
        }
        Map<String,String> prior=new HashMap<>(values);
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        assertEquals(expected,h.restore());h.disarmed();h.unchanged(prior);
        assertEquals(0,h.runtime.loads);assertEquals(expected,h.observed.lastStartupRestoreResult());
        RuntimeDiagnostics diagnostics=h.diagnostics();
        if(h.store.read().state()==InstallationStore.ReadState.CORRUPT) {
            assertEquals(RuntimeDiagnostics.InstallationState.CORRUPT,diagnostics.installationState);
            assertFalse(diagnostics.installationPresent);assertEquals(0,diagnostics.installedRevision);
            assertNull(diagnostics.packageCodecId);assertNull(diagnostics.packageHandlerId);
        }
        assertFalse(DiagnosticsActivity.render(diagnostics).contains("raw-sensitive"));h.unchanged(prior);
    }
}
