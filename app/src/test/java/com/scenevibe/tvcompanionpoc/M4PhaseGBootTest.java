package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise BootReceiver's actual metadata seam while retaining the byte-unchanged AutostartPolicy. */
public final class M4PhaseGBootTest {
    /** Both generic profiles and both frozen historical profiles count as durable content without credentials. */
    @Test public void completeSnapshotsPermitBootPrepareWithNoCloudCredential() throws Exception {
        for(Object[] profile:M4PhaseGFixtures.profiles()) {
            Map<String,String> values=M4PhaseGFixtures.durable((boolean)profile[0],(boolean)profile[1]);
            M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);Map<String,String> prior=new HashMap<>(values);
            assertEquals(AutostartPolicy.Decision.START,BootReceiver.decide(true,true,true,false,h.store.read()));
            h.unchanged(prior);h.disarmed();assertEquals(0,h.runtime.loads);
        }
    }
    /** Disabled opt-in and missing overlay/media permissions keep their original first-failure order. */
    @Test public void preferenceAndPermissionsTakePrecedenceOverGenericPresence() throws Exception {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(M4PhaseGFixtures.durable(true,true));
        InstallationStore.ReadResult durable=h.store.read();
        assertEquals(AutostartPolicy.Decision.AUTOSTART_DISABLED,BootReceiver.decide(false,false,false,true,durable));
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_OVERLAY_PERMISSION,BootReceiver.decide(true,false,false,true,durable));
        assertEquals(AutostartPolicy.Decision.AUTOSTART_BLOCKED_MEDIA_PERMISSION,BootReceiver.decide(true,true,false,true,durable));
        h.disarmed();assertEquals(0,h.backend.candidateWrites+h.backend.ackWrites+h.backend.clears);
    }
    /** Clean empty metadata is not saved content, even when an old observation said ARMED. */
    @Test public void emptyWithoutCredentialCannotStart() {
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(new HashMap<>());
        assertEquals(AutostartPolicy.Decision.AUTOSTART_NOTHING_TO_RESTORE,
                BootReceiver.decide(true,true,true,false,h.store.read()));h.disarmed();
        assertEquals(0,h.backend.candidateWrites+h.backend.ackWrites+h.backend.clears);
    }
    /** A corrupt present marker never resurrects the otherwise valid historical cache underneath it. */
    @Test public void corruptWithoutCredentialCannotStartOrRepair() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(true,false);values.put(InstallationStore.SNAPSHOT_KEY,"corrupt");
        Map<String,String> prior=new HashMap<>(values);M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        assertEquals(AutostartPolicy.Decision.AUTOSTART_NOTHING_TO_RESTORE,
                BootReceiver.decide(true,true,true,false,h.store.read()));h.unchanged(prior);h.disarmed();
    }
    /** Usable credentials independently permit START over EMPTY or CORRUPT; the service still refuses corruption. */
    @Test public void usableCredentialIndependentlyPermitsStartWithoutContent() {
        for(boolean corrupt:new boolean[]{false,true}) {
            Map<String,String> values=new HashMap<>();if(corrupt)values.put(InstallationStore.SNAPSHOT_KEY,"corrupt");
            M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
            assertEquals(AutostartPolicy.Decision.START,BootReceiver.decide(true,true,true,true,h.store.read()));
            h.disarmed();h.unchanged(new HashMap<>(values));
        }
    }
    /** Boot reads structural metadata without resolving capabilities; unsupported restore is the installer's job. */
    @Test public void bootNeverParsesOrArmsUnsupportedHistoricalArtifact() throws Exception {
        Map<String,String> values=M4PhaseGFixtures.durable(true,false);values.put("manifest","opaque-unexecutable-bytes");
        M4PhaseGFixtures.Startup h=new M4PhaseGFixtures.Startup(values);
        assertEquals(InstallationStore.ReadState.SNAPSHOT,h.store.read().state());
        assertEquals(AutostartPolicy.Decision.START,BootReceiver.decide(true,true,true,false,h.store.read()));
        h.disarmed();assertEquals(0,h.runtime.loads);h.unchanged(new HashMap<>(values));
    }
}
