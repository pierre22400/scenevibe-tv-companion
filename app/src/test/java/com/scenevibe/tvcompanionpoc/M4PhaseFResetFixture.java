package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/** Retain the six original async-reset assertions while driving the new production client API. */
final class M4PhaseFResetFixture {
    /** Adapt only the test's old disk boundary, never production installation orchestration. */
    static CloudControlClient client(ScheduledExecutorService io,CloudDeviceCredentials identity,
            CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler) throws Exception {
        return client(io,identity,cache,scheduler,M4PhaseFResetFixture::fallback,()->{});
    }
    /** Preserve the existing deterministic rejected-primary-executor injection. */
    static CloudControlClient client(ScheduledExecutorService io,CloudDeviceCredentials identity,
            CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler,Executor fallback) throws Exception {
        return client(io,identity,cache,scheduler,fallback,()->{});
    }
    /** Preserve identity-rotation observation and the original scheduler/cache reset assertions. */
    static CloudControlClient client(ScheduledExecutorService io,CloudDeviceCredentials identity,
            CloudTrackRepository cache,MediaSyncedTrackScheduler scheduler,Executor fallback,
            Runnable rotation) throws Exception {
        Field field=CloudTrackRepository.class.getDeclaredField("storage");field.setAccessible(true);
        CloudTrackRepository.Storage disk=(CloudTrackRepository.Storage)field.get(cache);
        InstallationStore store=new InstallationStore(new InstallationStore.Backend() {
            /** Coordinate the same deterministic preference object as the original test. */
            @Override public Object monitor() {return disk;}
            /** The reset test retains the exact historical fields and does not install packages. */
            @Override public String get(String key) {return disk.get(key);}
            /** The real generic clearAll primitive must request a single whole-file clear. */
            @Override public boolean commit(Map<String,String> values,Set<String> removed,boolean clear) {
                if(!clear||!values.isEmpty()||!removed.isEmpty())throw new AssertionError("Reset-only disk boundary");
                disk.clear();return true;
            }
        });
        return new CloudControlClient(io,identity,store,(request,ports)->InstallationStatus.ARM_FAILED,
                new InstallationHandler.RuntimePorts(){},scheduler::clear,fallback,rotation,
                AssignmentMutationGate.direct(),()->true);
    }
    /** Match the production asynchronous fallback without introducing an Android caller wait. */
    private static void fallback(Runnable work) {
        Thread worker=new Thread(work,"phase-f-reset-fixture");worker.setDaemon(true);worker.start();
    }
    /** Test construction has no static mutable runtime. */
    private M4PhaseFResetFixture() {}
}
