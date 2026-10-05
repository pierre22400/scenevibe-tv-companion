package com.scenevibe.tvcompanionpoc.installation;

import java.util.Arrays;
import java.util.Map;

/**
 * Own the local validate/prepare/encode/commit/readback/restore/arm lifecycle only.
 * Durable metadata selects an eager static binding; same-revision incoming bytes have no
 * authority. Only restored durable state can be armed. A later activation failure keeps
 * the committed revision pending; this class neither rolls back nor records confirmation.
 * Calls are synchronous and serialized per installer. The caller supplies runtime ports
 * on their eligible owner thread and must serialize access to that runtime and store.
 * This isolated Phase E core has no production caller or public boot/restore entry point.
 */
public final class PackageInstaller {
    private final InstallationStore store;
    private final InstallationHandlerRegistry registry;
    private final TvCapabilities capabilities;

    /** Retain only local durable, static binding and capability dependencies; perform no work. */
    public PackageInstaller(InstallationStore store,InstallationHandlerRegistry registry,
            TvCapabilities capabilities) {
        if (store==null||registry==null||capabilities==null)
            throw new IllegalArgumentException("Missing installation dependency");
        this.store=store;this.registry=registry;this.capabilities=capabilities;
    }

    /** Decide revision authority before invoking a handler; return only a closed local outcome. */
    public synchronized InstallationStatus install(InstallRequest request,InstallationHandler.RuntimePorts ports) {
        InstallationStore.ReadResult current=readDurable();
        if (current==null||current.state()==InstallationStore.ReadState.CORRUPT)
            return InstallationStatus.CACHE_FAILED;
        if (request==null) return InstallationStatus.INVALID_PACKAGE;
        if (current.state()==InstallationStore.ReadState.SNAPSHOT) {
            InstallationSnapshot durable=current.snapshot();
            if (request.revision()<durable.revision()) return InstallationStatus.STALE;
            if (request.revision()==durable.revision()) {
                InstallationHandlerRegistry.Entry binding=durableBinding(durable);
                return binding==null?InstallationStatus.CACHE_FAILED:restoreAndArm(durable,binding,ports);
            }
        }
        return installNewer(request,ports);
    }

    /** Validate and prepare without mutation, then commit once and verify exact durable readback. */
    private InstallationStatus installNewer(InstallRequest request,InstallationHandler.RuntimePorts ports) {
        InstallationHandlerRegistry.Entry binding=registry.findCodec(request.codecId());
        if (binding==null) return InstallationStatus.INVALID_PACKAGE;
        InstallationSnapshot candidate;
        try {
            InstallationStatus validated=binding.handler().validate(request,capabilities);
            if (validated!=InstallationStatus.VALIDATED) {
                // A premature stage/success claim must never escape as a completed installation.
                return validated==null||validated==InstallationStatus.PREPARED||validated==InstallationStatus.ARMED
                        ?InstallationStatus.INVALID_PACKAGE:validated;
            }
            PreparedInstallation prepared=binding.handler().prepare(request,capabilities);
            if (!coherent(prepared,request,binding)) return InstallationStatus.INVALID_PACKAGE;
            InstallRequest canonical=binding.handler().encodeForCache(prepared);
            if (canonical==null||canonical.revision()!=request.revision()
                    ||!binding.codecId().equals(canonical.codecId())
                    ||capabilities.validate(canonical,prepared.requirements())!=InstallationStatus.VALIDATED)
                return InstallationStatus.INVALID_PACKAGE;
            candidate=new InstallationSnapshot(canonical,binding.handlerId());
        } catch (RuntimeException rejected) {return InstallationStatus.INVALID_PACKAGE;}

        InstallationStore.CommitState committed;
        try {committed=store.commit(candidate);}
        catch (RuntimeException failed) {return InstallationStatus.CACHE_FAILED;}
        if (committed==InstallationStore.CommitState.INVALID_SNAPSHOT) return InstallationStatus.INVALID_PACKAGE;
        if (committed!=InstallationStore.CommitState.COMMITTED) return InstallationStatus.CACHE_FAILED;

        InstallationStore.ReadResult readback=readDurable();
        if (readback==null||readback.state()!=InstallationStore.ReadState.SNAPSHOT)
            return InstallationStatus.CACHE_FAILED;
        InstallationSnapshot durable=readback.snapshot();
        InstallationHandlerRegistry.Entry restoredBinding=durableBinding(durable);
        if (restoredBinding!=binding||durable.revision()!=candidate.revision()
                ||!sameArtifacts(candidate.canonical(),durable.canonical()))
            return InstallationStatus.CACHE_FAILED;
        return restoreAndArm(durable,restoredBinding,ports);
    }

    /** Read under the store's consistency contract; backend exceptions grant no write or activation. */
    private InstallationStore.ReadResult readDurable() {
        try {return store.read();}
        catch (RuntimeException failed) {return null;}
    }

    /** Resolve durable handler id first and require its exact codec/id binding, with no fallback. */
    private InstallationHandlerRegistry.Entry durableBinding(InstallationSnapshot snapshot) {
        InstallationHandlerRegistry.Entry binding=registry.findHandler(snapshot.handlerId());
        return binding!=null&&binding.handlerId().equals(snapshot.handlerId())
                &&binding.codecId().equals(snapshot.codecId())?binding:null;
    }

    /** Rebuild trusted state from durable bytes only; failure leaves the current durable revision intact. */
    private InstallationStatus restoreAndArm(InstallationSnapshot durable,
            InstallationHandlerRegistry.Entry binding,InstallationHandler.RuntimePorts ports) {
        try {
            PreparedInstallation restored=binding.handler().restoreFromCache(durable.canonical(),capabilities);
            if (!coherent(restored,durable.canonical(),binding)
                    ||!sameArtifacts(restored.canonical(),durable.canonical())||ports==null)
                return InstallationStatus.ARM_FAILED;
            return binding.handler().arm(restored,ports)==InstallationStatus.ARMED
                    ?InstallationStatus.ARMED:InstallationStatus.ARM_FAILED;
        } catch (RuntimeException failed) {return InstallationStatus.ARM_FAILED;}
    }

    /** Reject null/foreign metadata and non-executable requirements without inspecting opaque handler state. */
    private boolean coherent(PreparedInstallation prepared,InstallRequest expected,
            InstallationHandlerRegistry.Entry binding) {
        return prepared!=null&&prepared.status()==InstallationStatus.PREPARED
                &&prepared.revision()==expected.revision()&&binding.codecId().equals(prepared.codecId())
                &&binding.handlerId().equals(prepared.handlerId())
                &&capabilities.validate(prepared.canonical(),prepared.requirements())==InstallationStatus.VALIDATED;
    }

    /** Prove canonical bytes and artifact identities survived readback/restoration without normalization. */
    private static boolean sameArtifacts(InstallRequest expected,InstallRequest actual) {
        Map<String,byte[]> first=expected.artifacts(),second=actual.artifacts();
        if (!first.keySet().equals(second.keySet())) return false;
        for (Map.Entry<String,byte[]> artifact:first.entrySet())
            if (!Arrays.equals(artifact.getValue(),second.get(artifact.getKey()))) return false;
        return true;
    }
}
