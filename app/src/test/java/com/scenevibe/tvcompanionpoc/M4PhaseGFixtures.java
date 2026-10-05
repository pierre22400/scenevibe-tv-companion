package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.*;

/**
 * Fresh startup composition with real store/installer/Video handlers and the actual service ports.
 * Native windows and disk alone are substituted. No Cloud client, credential or ACK collaborator
 * is constructed; the F runtime fixture delegates every successful port to OverlayService.
 */
final class M4PhaseGFixtures {
    /** Cover representation and Video profile independently, keeping the historical fixture bytes exact. */
    static Collection<Object[]> profiles() {
        return Arrays.asList(new Object[][]{{true,true},{false,true},{true,false},{false,false}});
    }

    /** Seed generic authority above exact historical residue through the real store, outside startup. */
    static Map<String,String> durable(boolean manifested, boolean generic) throws Exception {
        Map<String,String> values=M4PhaseFFixtures.historical(manifested);
        if(generic) {
            InstallationStore store=new InstallationStore(new M4PhaseFFixtures.Backend(values));
            InstallationSnapshot old=store.read().snapshot();
            assertEquals(InstallationStore.CommitState.COMMITTED,store.commit(new InstallationSnapshot(
                    new InstallRequest(15,old.codecId(),old.canonical().artifacts()),old.handlerId())));
        }
        return values;
    }

    /** Construct one changed inert request for refusal tests without implementing any parser/handler rules. */
    static InstallRequest request(InstallationSnapshot prior, String codec, String name, String value) {
        Map<String,byte[]> artifacts=new TreeMap<>(prior.canonical().artifacts());
        if(value==null)artifacts.remove(name);else artifacts.put(name,value.getBytes(StandardCharsets.UTF_8));
        return new InstallRequest(prior.revision(),codec,artifacts);
    }

    /** Actual startup seam into fresh media cores; none of these fields can send or persist an ACK. */
    static final class Startup {
        final M4PhaseFFixtures.Backend backend;
        final InstallationStore store;
        final PackageInstaller installer;
        final M4PhaseFFixtures.Runtime runtime;
        final DiagnosticsStore observed=new DiagnosticsStore();

        /** Recreated processes share only durable bytes and the preference consistency monitor. */
        Startup(Map<String,String> values) {
            backend=new M4PhaseFFixtures.Backend(values);
            store=new InstallationStore(backend);
            installer=new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current());
            runtime=new M4PhaseFFixtures.Runtime(backend.trace,()->true);
        }
        /** Restore exactly as the service does before probe/poll startup, recording only bounded observations. */
        InstallationStatus restore() {
            return OverlayService.restoreInstalledPackage(store,installer,runtime.ports,observed);
        }
        /** Use the same installation read/projection factory as Android capture without Android permissions. */
        RuntimeDiagnostics diagnostics() {return RuntimeDiagnostics.installationSnapshot(store,observed).build();}
        /** Require byte-exact durability and zero migration/clear/commit/ACK during startup/read paths. */
        void unchanged(Map<String,String> prior) {
            assertEquals(prior,backend.values);
            assertEquals(0,backend.candidateWrites+backend.ackWrites+backend.clears);
        }
        /** Fresh cores remain disarmed and have neither visual owner after a startup refusal. */
        void disarmed() {
            assertEquals(0,runtime.active);assertFalse(runtime.controller.hasActiveManifest());
            assertFalse(runtime.legacyVisible);assertFalse(runtime.manifestVisible);assertEquals(0,runtime.shows);
        }
    }

    /** Compare only named byte arrays, never emit private commentary in assertion messages. */
    static void exact(InstallationSnapshot expected, InstallationSnapshot actual) {
        assertEquals(expected.revision(),actual.revision());assertEquals(expected.codecId(),actual.codecId());
        assertEquals(expected.handlerId(),actual.handlerId());
        assertEquals(expected.canonical().artifacts().keySet(),actual.canonical().artifacts().keySet());
        for(String key:expected.canonical().artifacts().keySet())
            assertTrue("unchanged durable artifact",Arrays.equals(expected.canonical().artifact(key),actual.canonical().artifact(key)));
    }
    /** Test fixture has no implicit runtime or transport instance. */
    private M4PhaseGFixtures() {}
}
