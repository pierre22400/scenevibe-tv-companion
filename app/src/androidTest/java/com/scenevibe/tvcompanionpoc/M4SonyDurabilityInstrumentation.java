package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONObject;

/**
 * Test-APK-only Android disk/process qualification, with no production entry point or network.
 * A shell gate runs seed and reload in different target processes and records distinct PIDs.
 * All persistence uses the real application-private cloud_track SharedPreferences and backend.
 * Fixture setup is explicitly destructive on the disposable emulator, never on a physical TV.
 * Only native windows/media inputs are substituted; the actual service ports and handlers ARM.
 * This source uses pre-corrective APIs so the identical test can execute against the pinned old APK.
 */
public final class M4SonyDurabilityInstrumentation extends Instrumentation {
    private Bundle arguments;
    private String checkpoint="SETUP";
    private Context target;
    private SharedPreferences preferences;

    /** Start the test worker without launching an Activity, service, credential owner or Cloud client. */
    @Override public void onCreate(Bundle values) {
        super.onCreate(values);
        arguments=values==null?new Bundle():values;
        start();
    }

    /** Emit only bounded results/PID/scenario; original exceptions and all preference values stay private. */
    @Override public void onStart() {
        Bundle result=new Bundle();
        String phase=arguments.getString("phase",""),scenario=arguments.getString("scenario","");
        boolean baseline="baseline".equals(arguments.getString("build","fixed"));
        try {
            target=getTargetContext();
            preferences=target.getSharedPreferences("cloud_track",Context.MODE_PRIVATE);
            if ("seed".equals(phase)) seed(scenario);
            else if ("reload".equals(phase)) reload(scenario,baseline);
            else check(false,"INVALID_PHASE");
            result.putString("result","PASS");
            result.putString("checkpoint",baseline&&"reload".equals(phase)?"BASELINE_CORRUPTION_REPRODUCED":"COMPLETE");
            result.putString("scenario",scenario);
            result.putInt("pid",Process.myPid());
            finish(-1,result);
        } catch (Exception|AssertionError failed) {
            result.putString("result","FAIL");result.putString("checkpoint",checkpoint);
            result.putInt("pid",Process.myPid());finish(0,result);
        }
    }

    /** Publish exact frozen residue 13 and the requested candidate/ACK using the real Android disk boundary. */
    private void seed(String scenario) throws Exception {
        Map<String,String> historical=historical();
        SharedPreferences.Editor editor=preferences.edit().clear();
        for (Map.Entry<String,String> entry:historical.entrySet()) editor.putString(entry.getKey(),entry.getValue());
        check(editor.commit(),"HISTORICAL_DISK_SEED");
        AndroidInstallationBackend backend=new AndroidInstallationBackend(target);
        InstallationStore store=new InstallationStore(backend);
        if (scenario.startsWith("generic-")) {
            InstallationSnapshot candidate=candidate(scenario);
            Runtime runtime=new Runtime();
            runOnMainSync(()->check(new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current())
                    .install(candidate.canonical(),runtime.ports)==InstallationStatus.ARMED,"REAL_GENERIC_INSTALL_ARM"));
            if (scenario.endsWith("-confirmed")) check(store.markAcknowledged(15),"REAL_CONFIRMED_ACK_COMMIT");
            InstallationStore.ReadResult current=store.read();
            assertSnapshot(current,candidate,scenario.endsWith("-confirmed")?15:13);
            check("13".equals(preferences.getString("revision",null)),"HISTORICAL_RESIDUE_RETAINED");
        } else if ("maximum".equals(scenario)) {
            InstallationSnapshot candidate=candidate(scenario);
            check(store.commit(candidate)==InstallationStore.CommitState.COMMITTED,"MAXIMUM_GENERIC_COMMIT");
            check(store.markAcknowledged(15),"MAXIMUM_ACK_COMMIT");assertSnapshot(store.read(),candidate,15);
        } else if ("failed-commit".equals(scenario)) {
            File directory=new File(target.getApplicationInfo().dataDir,"shared_prefs");
            int mode=android.system.Os.stat(directory.getPath()).st_mode&0777;
            byte[] before=diskDigest();Runtime runtime=new Runtime();
            InstallationSnapshot candidate=candidate("generic-manifested-confirmed");
            try {
                android.system.Os.chmod(directory.getPath(),0500);
                runOnMainSync(()->check(new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current())
                        .install(candidate.canonical(),runtime.ports)==InstallationStatus.CACHE_FAILED,"REAL_DISK_COMMIT_REFUSED"));
                check(!store.markAcknowledged(15),"FAILED_COMMIT_NO_FALSE_ACK");
                check(store.read().state()==InstallationStore.ReadState.SNAPSHOT&&store.read().snapshot().revision()==13
                        &&store.read().acknowledgedRevision()==13,"FAILED_COMMIT_PRIOR_READ_VIEW");
                check(runtime.selected==0&&runtime.shows==0,"FAILED_COMMIT_NO_ARM");
                check(Arrays.equals(before,diskDigest()),"FAILED_COMMIT_PRIOR_DISK_BYTES");
            } finally {android.system.Os.chmod(directory.getPath(),mode);}
        } else if ("invalid-generic".equals(scenario)) {
            check(preferences.edit().putString(InstallationStore.SNAPSHOT_KEY,"invalid").commit(),"INVALID_DISK_SEED");
        } else if ("ack-ahead-without-generic".equals(scenario)) {
            check(preferences.edit().putString("ackRevision","15").commit(),"ACK_AHEAD_DISK_SEED");
        } else if ("historical-legacy".equals(scenario)) {
            check(preferences.edit().remove("manifest").commit(),"HISTORICAL_LEGACY_DISK_SEED");
        } else if ("empty".equals(scenario)) {
            check(preferences.edit().clear().commit(),"EMPTY_DISK_SEED");
        } else check("historical-manifested".equals(scenario),"INVALID_SCENARIO");
        String logical=backend.get(InstallationStore.SNAPSHOT_KEY);
        save("m4-sony-logical",(logical==null?"":logical).getBytes(StandardCharsets.UTF_8));
        save("m4-sony-seed-pid",Integer.toString(Process.myPid()).getBytes(StandardCharsets.US_ASCII));
        save("m4-sony-disk-digest",diskDigest());
    }

    /** Read in a fresh OS process, prove exact bytes/ACK/handler, then invoke actual zero-write startup restore. */
    private void reload(String scenario,boolean baseline) throws Exception {
        int seedPid=Integer.parseInt(new String(load("m4-sony-seed-pid"),StandardCharsets.US_ASCII));
        check(seedPid!=Process.myPid(),"DISTINCT_ANDROID_PROCESS");
        byte[] initialDisk=diskDigest();
        check(Arrays.equals(load("m4-sony-disk-digest"),initialDisk),"DISK_UNCHANGED_BETWEEN_PROCESSES");
        CountingBackend backend=new CountingBackend(new AndroidInstallationBackend(target));
        InstallationStore store=new InstallationStore(backend);
        InstallationStore.ReadResult durable=store.read();
        String expected=new String(load("m4-sony-logical"),StandardCharsets.UTF_8);
        if (baseline) {
            check(scenario.startsWith("generic-"),"BASELINE_SCENARIO");
            check(durable.state()==InstallationStore.ReadState.CORRUPT,"BASELINE_POST_PROCESS_CORRUPT");
            check((expected+"    ").equals(backend.get(InstallationStore.SNAPSHOT_KEY)),"BASELINE_EXACT_XML_PADDING");
            check("13".equals(preferences.getString("revision",null)),"BASELINE_HISTORICAL_13");
            check((scenario.endsWith("-confirmed")?"15":"13").equals(preferences.getString("ackRevision",null)),"BASELINE_RAW_ACK_EXACT");
            Runtime runtime=new Runtime();
            runOnMainSync(()->check(OverlayService.restoreInstalledPackage(store,
                    new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current()),
                    runtime.ports,DiagnosticsStore.INSTANCE)==InstallationStatus.CACHE_FAILED,"BASELINE_STARTUP_FAIL_CLOSED"));
            check(runtime.selected==0&&runtime.shows==0,"BASELINE_NO_RUNTIME_ACTIVATION");
        } else if (scenario.startsWith("generic-")||"maximum".equals(scenario)) {
            InstallationSnapshot candidate=candidate(scenario);
            long ack="maximum".equals(scenario)||scenario.endsWith("-confirmed")?15:13;
            assertSnapshot(durable,candidate,ack);
            check(expected.equals(backend.get(InstallationStore.SNAPSHOT_KEY)),"EXACT_LOGICAL_ENCODING_RELOADED");
            Map<String,String> historical=historical();
            for (String key:new String[]{"revision","runtime","manifest"})
                check(historical.get(key).equals(preferences.getString(key,null)),"EXACT_HISTORICAL_RESIDUE");
            if (!"maximum".equals(scenario)) assertRestore(store,15);
        } else if (scenario.startsWith("historical-")||"failed-commit".equals(scenario)) {
            Map<String,String> original=historical();Map<String,byte[]> artifacts=new TreeMap<>();
            artifacts.put("runtime",original.get("runtime").getBytes(StandardCharsets.UTF_8));
            boolean manifested=!"historical-legacy".equals(scenario);
            if (manifested) artifacts.put("manifest",original.get("manifest").getBytes(StandardCharsets.UTF_8));
            String codec=manifested?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK;
            assertSnapshot(durable,new InstallationSnapshot(new InstallRequest(13,codec,artifacts),codec),13);
            check(backend.get(InstallationStore.SNAPSHOT_KEY)==null,"NO_HISTORICAL_MIGRATION");assertRestore(store,13);
        } else if ("empty".equals(scenario)) {
            check(durable.state()==InstallationStore.ReadState.EMPTY,"EMPTY_REMAINS_EMPTY");
            Runtime runtime=new Runtime();
            runOnMainSync(()->check(OverlayService.restoreInstalledPackage(store,
                    new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current()),
                    runtime.ports,DiagnosticsStore.INSTANCE)==null,"EMPTY_NO_RESTORE"));
            check(runtime.selected==0&&runtime.shows==0,"EMPTY_NO_RUNTIME");
        } else {
            check(durable.state()==InstallationStore.ReadState.CORRUPT,"INVALID_REMAINS_CORRUPT");
            Runtime runtime=new Runtime();
            runOnMainSync(()->check(OverlayService.restoreInstalledPackage(store,
                    new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current()),
                    runtime.ports,DiagnosticsStore.INSTANCE)==InstallationStatus.CACHE_FAILED,"CORRUPT_NO_RESTORE"));
            check(runtime.selected==0&&runtime.shows==0,"CORRUPT_NO_RUNTIME");
        }
        check(backend.commits==0&&backend.acks==0&&backend.clears==0,"ZERO_RESTORE_WRITES_OR_ACK");
        check(Arrays.equals(initialDisk,diskDigest()),"BYTE_EXACT_DISK_AFTER_RESTORE");
    }

    /** ARM on the real main owner through the service's production ports; no due event or visual surface is created. */
    private void assertRestore(InstallationStore store,long revision) {
        Runtime runtime=new Runtime();
        runOnMainSync(()->check(OverlayService.restoreInstalledPackage(store,
                new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current()),
                runtime.ports,DiagnosticsStore.INSTANCE)==InstallationStatus.ARMED,"SAME_REVISION_STARTUP_ARMED"));
        check(runtime.selected==revision&&runtime.shows==0,"ARMED_NOT_VISIBLE_EXACT_REVISION");
    }

    /** Verify all opaque artifacts and durable metadata using the same invariant for both native lifecycles. */
    private void assertSnapshot(InstallationStore.ReadResult read,InstallationSnapshot expected,long ack) {
        check(read.state()==InstallationStore.ReadState.SNAPSHOT,"SNAPSHOT_PRESENT");
        InstallationSnapshot actual=read.snapshot();
        check(actual.revision()==expected.revision()&&read.acknowledgedRevision()==ack,"EXACT_REVISION_AND_ACK");
        check(actual.codecId().equals(expected.codecId())&&actual.handlerId().equals(expected.handlerId()),"EXACT_CODEC_AND_HANDLER");
        Map<String,byte[]> artifacts=expected.canonical().artifacts();
        check(actual.canonical().artifacts().keySet().equals(artifacts.keySet()),"EXACT_ARTIFACT_NAMES");
        for (Map.Entry<String,byte[]> entry:artifacts.entrySet())
            check(Arrays.equals(entry.getValue(),actual.canonical().artifact(entry.getKey())),"EXACT_ARTIFACT_BYTES");
    }

    /** Load the existing frozen manifested historical fixture, with all original strings and ACK 13 intact. */
    private Map<String,String> historical() throws Exception {
        JSONObject fixture=fixture("video-manifested-cache-v1.json");Map<String,String> values=new TreeMap<>();
        for (String key:new String[]{"revision","runtime","manifest","ackRevision"}) values.put(key,fixture.getString(key));
        return values;
    }

    /** Construct revision 15 from frozen bytes; maximum exercises the unchanged three-million-byte generic ceiling. */
    private InstallationSnapshot candidate(String scenario) throws Exception {
        if ("maximum".equals(scenario)) {
            byte[] first=new byte[1_500_000],second=new byte[1_500_000];
            for (int i=0;i<first.length;i++) {first[i]=(byte)i;second[i]=(byte)(255-i);}
            return new InstallationSnapshot(new InstallRequest(15,"bounded.test.v1",Map.of("a",first,"b",second)),"bounded.test.v1");
        }
        boolean manifested=scenario.startsWith("generic-manifested");
        JSONObject fixture=fixture(manifested?"video-manifested-cache-v1.json":"video-legacy-cache-v1.json");
        Map<String,byte[]> artifacts=new TreeMap<>();artifacts.put("runtime",fixture.getString("runtime").getBytes(StandardCharsets.UTF_8));
        if (manifested) artifacts.put("manifest",fixture.getString("manifest").getBytes(StandardCharsets.UTF_8));
        String codec=manifested?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK;
        return new InstallationSnapshot(new InstallRequest(15,codec,artifacts),codec);
    }

    /** Read test-APK assets only; no fixture or private cache content is emitted in instrumentation output. */
    private JSONObject fixture(String name) throws Exception {
        try (InputStream input=getContext().getAssets().open(name)) {
            return new JSONObject(new String(readBytes(input),StandardCharsets.UTF_8));
        }
    }

    /** Hash the actual Android preference XML without parsing or publishing its contents. */
    private byte[] diskDigest() throws Exception {
        File file=new File(target.getApplicationInfo().dataDir,"shared_prefs/cloud_track.xml");
        try (InputStream input=new FileInputStream(file)) {
            return MessageDigest.getInstance("SHA-256").digest(readBytes(input));
        }
    }

    /** Keep test-only expectations in a separate private file and sync them before process termination. */
    private void save(String name,byte[] bytes) throws Exception {
        try (FileOutputStream output=target.openFileOutput(name,Context.MODE_PRIVATE)) {
            output.write(bytes);output.getFD().sync();
        }
    }

    /** Reload expectations from disk rather than retaining a Java object across instrumentation invocations. */
    private byte[] load(String name) throws Exception {
        try (InputStream input=target.openFileInput(name)) {return readBytes(input);}
    }

    /** Use Android 12-compatible stream APIs; the isolated fixture files are bounded by the existing package ceiling. */
    private static byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
        return output.toByteArray();
    }

    /** Record a fixed source-defined checkpoint before throwing; never use raw exception text as a diagnostic. */
    private void check(boolean condition,String label) {
        checkpoint=label;if (!condition) throw new AssertionError(label);
    }

    /** Count mutation batches while delegating every read/write to the real Android backend and monitor. */
    private static final class CountingBackend implements InstallationStore.Backend {
        final AndroidInstallationBackend delegate;
        int commits,acks,clears;
        /** Bind the newly created real preference owner without copying any cached state. */
        CountingBackend(AndroidInstallationBackend delegate) {this.delegate=delegate;}
        /** Keep the actual preference object's coordination monitor. */
        @Override public Object monitor() {return delegate.monitor();}
        /** Read through Android's native storage lifecycle and the production transport decoder. */
        @Override public String get(String key) {return delegate.get(key);}
        /** Observe, then delegate the exact native batch; restore must never call this operation. */
        @Override public boolean commit(Map<String,String> values,Set<String> removed,boolean clear) {
            commits++;if (values.containsKey("ackRevision")) acks++;if (clear) clears++;
            return delegate.commit(values,removed,clear);
        }
    }

    /** Compose real media cores and production ports, substituting only Android visual callbacks. */
    private static final class Runtime {
        long selected;
        int shows;
        final SceneRuntimeController controller=new SceneRuntimeController(new SceneRuntimeController.SceneSink() {
            /** No remote/native asset is requested; no preflight runs without a media due event. */
            @Override public boolean preflight(OverlayManifest.Scene scene) {return true;}
            /** Count a due scene only; restoration must not invoke this visual callback. */
            @Override public void show(OverlayManifest.Scene scene) {shows++;}
            /** Retirement has no window in this storage qualification fixture. */
            @Override public void hide(OverlayManifest.Scene scene) {}
            /** Owner abort/retirement invokes the same production controller without creating a window. */
            @Override public void hideAll() {}
        });
        final MediaSyncedTrackScheduler scheduler=new MediaSyncedTrackScheduler(new MediaSyncedTrackScheduler.Listener() {
            /** An ARM without a media due event must not display a legacy card. */
            @Override public void onRender(ScheduledTrack.Event event) {shows++;}
            /** No playback input is injected during offline restoration. */
            @Override public void onPlayback(boolean playing,boolean freeze) {}
            /** No media eligibility event is needed to restore the exact package. */
            @Override public void onEligibility(boolean eligible) {}
        });
        final OverlayService.LiveVideoRuntimePorts ports=new OverlayService.LiveVideoRuntimePorts(
                ()->android.os.Looper.myLooper()==android.os.Looper.getMainLooper(),()->scheduler,()->controller,
                ()->{},()->{},revision->selected=revision);
        /** Construct no native window, credential, Cloud client, socket or second handler registry. */
        Runtime() {}
    }
}
