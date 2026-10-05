package com.scenevibe.tvcompanionpoc.installation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generic deterministic fault boundaries, with opaque bytes and real durable encoding.
 * Traces contain stage names only. A fake handler changes one contract edge at a time;
 * the final real store/model/registry types enforce their own invariants without mocks.
 */
final class M4PhaseEInstallerFixtures {
    static final String HANDLER="local.track-handler.v1";
    static final String OTHER_HANDLER="local.overlay-handler.v1";
    static final TvCapabilities CAPS=TvCapabilities.current();
    static final InstallationHandler.RuntimePorts PORTS=new InstallationHandler.RuntimePorts(){};

    /** Construct bounded opaque data; two artifacts exercise the other accepted generic shape. */
    static InstallRequest request(long revision,String codec,String bytes) {
        Map<String,byte[]> artifacts=new TreeMap<>();
        artifacts.put("body",bytes.getBytes(StandardCharsets.UTF_8));
        if (TvCapabilities.CODEC_TRACK_OVERLAY.equals(codec)) artifacts.put("extra",new byte[]{1});
        return new InstallRequest(revision,codec,artifacts);
    }

    /** Bind a coherent executable profile without a product parser or stateful runtime. */
    static PreparedInstallation prepared(InstallRequest request,String handler) {
        boolean overlay=TvCapabilities.CODEC_TRACK_OVERLAY.equals(request.codecId());
        ExecutionRequirements needs=new ExecutionRequirements(overlay?TvCapabilities.OVERLAY_CONTRACT:TvCapabilities.LEGACY_CONTRACT,
                ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
        return new PreparedInstallation(request,handler,Collections.emptyMap(),needs,CAPS);
    }

    /** Atomic readable memory backend; deliberate readback faults model storage trust failures only. */
    static final class Memory implements InstallationStore.Backend {
        final Map<String,String> values=new HashMap<>();
        final List<String> trace;
        int commits,ackWrites,clearWrites;
        boolean writable=true,throwCommit,throwRead;
        String readbackFault;

        /** Share only inert test storage and bounded stage names. */
        Memory(List<String> trace) {this.trace=trace;}
        /** Use one consistent monitor for every recreated real store. */
        @Override public Object monitor() {return this;}
        /** Observe snapshot reads without parsing or recording artifact contents. */
        @Override public String get(String key) {
            if (InstallationStore.SNAPSHOT_KEY.equals(key)) {
                trace.add("read");
                if (throwRead) throw new IllegalStateException("Injected read failure");
            }
            return values.get(key);
        }
        /** Publish one batch or leave the entire prior view intact; ACK attempts are independently counted. */
        @Override public boolean commit(Map<String,String> puts,Set<String> removed,boolean clear) {
            commits++;trace.add("commit");
            if (puts.containsKey("ackRevision")) ackWrites++;
            if (clear) clearWrites++;
            if (throwCommit) throw new IllegalStateException("Injected commit failure");
            if (!writable) return false;
            Map<String,String> next=clear?new HashMap<>():new HashMap<>(values);
            next.putAll(puts);for (String key:removed) next.remove(key);
            values.clear();values.putAll(next);
            if (readbackFault!=null&&puts.containsKey(InstallationStore.SNAPSHOT_KEY)) corruptReadback();
            return true;
        }
        /** Replace only the test backend's returned representation after a claimed successful commit. */
        private void corruptReadback() {
            if ("corrupt".equals(readbackFault)) {values.put(InstallationStore.SNAPSHOT_KEY,"broken");return;}
            if ("empty".equals(readbackFault)) {values.clear();return;}
            if ("read-throw".equals(readbackFault)) {throwRead=true;return;}
            InstallationSnapshot original=InstallationSnapshotCodec.decode(values.get(InstallationStore.SNAPSHOT_KEY));
            long revision=original.revision();String codec=original.codecId(),handler=original.handlerId();
            InstallRequest changed=original.canonical();
            if ("revision".equals(readbackFault)) revision++;
            if ("handler".equals(readbackFault)) handler=OTHER_HANDLER;
            if ("unknown-handler".equals(readbackFault)) handler="unknown.handler.v1";
            if ("codec".equals(readbackFault)||"binding".equals(readbackFault)) codec=TvCapabilities.CODEC_TRACK_OVERLAY;
            if ("binding".equals(readbackFault)) handler=OTHER_HANDLER;
            if ("bytes".equals(readbackFault)) changed=request(revision,codec,"changed durable bytes");
            if ("artifact-id".equals(readbackFault)) changed=new InstallRequest(revision,codec,
                    Collections.singletonMap("different",original.canonical().artifact("body")));
            changed=new InstallRequest(revision,codec,changed.artifacts());
            values.put(InstallationStore.SNAPSHOT_KEY,InstallationSnapshotCodec.encode(new InstallationSnapshot(changed,handler)));
        }
    }

    /** Static fake keeps the generic installer adversarially observable without interpreting its state. */
    static final class Handler implements InstallationHandler {
        final List<String> trace;
        final String handlerId,codec;
        int validations,preparations,encodings,restores,arms;
        String fault;
        InstallationStatus validation=InstallationStatus.VALIDATED,armed=InstallationStatus.ARMED;
        InstallRequest encodedOverride,restoredRequest;
        PreparedInstallation initial,restored,armInput;
        Runnable beforeEncode=()->{};

        /** Construct exactly one eager instance; lookup never invokes this constructor. */
        Handler(List<String> trace,String handlerId,String codec) {this.trace=trace;this.handlerId=handlerId;this.codec=codec;}
        /** Return or throw only the injected validation edge, recording no artifact content. */
        @Override public InstallationStatus validate(InstallRequest request,TvCapabilities capabilities) {
            validations++;trace.add("validate");
            if ("validate-throw".equals(fault)) throw new IllegalStateException("Injected handler failure");
            return validation;
        }
        /** Prepare bounded state, or inject a null, exception or foreign metadata contract. */
        @Override public PreparedInstallation prepare(InstallRequest request,TvCapabilities capabilities) {
            preparations++;trace.add("prepare");
            initial=value(request,"prepare");return initial;
        }
        /** Canonicalize independently so tests can prove the caller copy is not the ARM authority. */
        @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
            encodings++;trace.add("encode");beforeEncode.run();
            if ("encode-throw".equals(fault)) throw new IllegalStateException("Injected encode failure");
            if ("encode-null".equals(fault)) return null;
            if ("encode-revision".equals(fault)) return request(prepared.revision()+1,codec,"encoded");
            if ("encode-codec".equals(fault)) return request(prepared.revision(),TvCapabilities.CODEC_TRACK_OVERLAY,"encoded");
            if ("encode-shape".equals(fault)) return new InstallRequest(prepared.revision(),codec,
                    Map.of("body",new byte[]{1},"extra",new byte[]{2}));
            return encodedOverride==null?prepared.canonical():encodedOverride;
        }
        /** Retain the exact durable copy received for restoration and build a fresh prepared object. */
        @Override public PreparedInstallation restoreFromCache(InstallRequest snapshot,TvCapabilities capabilities) {
            restores++;trace.add("restore");restoredRequest=snapshot;
            restored=value(snapshot,"restore");return restored;
        }
        /** Observe exact restored object identity; non-success statuses cannot bypass installer discipline. */
        @Override public InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports) {
            arms++;trace.add("arm");armInput=prepared;
            if ("arm-throw".equals(fault)) throw new IllegalStateException("Injected arm failure");
            return armed;
        }
        /** Inject one coherent-but-foreign value, or let final model constructors reject impossible profiles. */
        private PreparedInstallation value(InstallRequest request,String stage) {
            if ((stage+"-throw").equals(fault)) throw new IllegalStateException("Injected preparation failure");
            if ((stage+"-null").equals(fault)) return null;
            if ((stage+"-revision").equals(fault)) request=request(request.revision()+1,codec,"foreign");
            if ((stage+"-codec").equals(fault)) request=request(request.revision(),TvCapabilities.CODEC_TRACK_OVERLAY,"foreign");
            if ((stage+"-bytes").equals(fault)) request=request(request.revision(),codec,"foreign");
            if ((stage+"-capability").equals(fault)) {
                ExecutionRequirements wall=new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,
                        ExecutionRequirements.ClockMode.WALL,ExecutionRequirements.PauseBehavior.FREEZE,1,false,false);
                return new PreparedInstallation(request,handlerId,Collections.emptyMap(),wall,CAPS);
            }
            return prepared(request,(stage+"-handler").equals(fault)?OTHER_HANDLER:handlerId);
        }
    }

    /** Compose two eager generic fakes with the real registry, capability model and durable store. */
    static final class Harness {
        final List<String> trace=new ArrayList<>();
        final Memory memory=new Memory(trace);
        final InstallationStore store=new InstallationStore(memory);
        final Handler handler=new Handler(trace,HANDLER,TvCapabilities.CODEC_TRACK);
        final Handler other=new Handler(trace,OTHER_HANDLER,TvCapabilities.CODEC_TRACK_OVERLAY);
        final InstallationHandlerRegistry registry=new InstallationHandlerRegistry(
                new InstallationHandlerRegistry.Entry(HANDLER,TvCapabilities.CODEC_TRACK,handler),
                new InstallationHandlerRegistry.Entry(OTHER_HANDLER,TvCapabilities.CODEC_TRACK_OVERLAY,other));
        final PackageInstaller installer=new PackageInstaller(store,registry,CAPS);

        /** Seed one real encoded snapshot and an explicitly test-confirmed prior ACK, then reset observation. */
        void seed(long revision,long acknowledged) {
            store.commit(new InstallationSnapshot(request(revision,TvCapabilities.CODEC_TRACK,"durable"),HANDLER));
            if (acknowledged>0) store.markAcknowledged(acknowledged);
            memory.commits=0;memory.ackWrites=0;memory.clearWrites=0;trace.clear();
        }
        /** Install inert input through the real orchestration using a compile-time-only runtime boundary. */
        InstallationStatus install(long revision) {return installer.install(request(revision,TvCapabilities.CODEC_TRACK,"incoming"),PORTS);}
    }

    /** Test fixture holder creates no implicit production composition. */
    private M4PhaseEInstallerFixtures() {}
}
