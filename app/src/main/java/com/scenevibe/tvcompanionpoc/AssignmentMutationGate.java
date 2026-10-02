package com.scenevibe.tvcompanionpoc;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;

/**
 * Serializes assignment application on the owner of Android windows.
 *
 * Network work remains on Cloud io. Only validated installation, scheduler load,
 * regie arm and visual retirement cross this gate. Returning means the mutation
 * finished; merely posting work never permits an ACK. This is not a media clock.
 */
final class AssignmentMutationGate {
    private final Executor owner;
    private final BooleanSupplier onOwner;

    /** Bind an owner executor and a predicate that avoids waiting on its own thread. */
    AssignmentMutationGate(Executor owner, BooleanSupplier onOwner) {
        this.owner=owner;
        this.onOwner=onOwner;
    }

    /** Preserve synchronous Android-free test constructors without creating a worker. */
    static AssignmentMutationGate direct() {
        return new AssignmentMutationGate(Runnable::run,()->true);
    }

    /**
     * Complete one mutation on its owner before returning to the network caller.
     * If shutdown interrupts a waiting caller, cancel queued work so it cannot
     * later install an obsolete revision. Owner exceptions remain failures.
     */
    <T> T call(Callable<T> mutation) throws Exception {
        if(onOwner.getAsBoolean())return mutation.call();
        FutureTask<T> task=new FutureTask<>(mutation);
        owner.execute(task);
        try {
            return task.get();
        } catch(InterruptedException interrupted) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch(ExecutionException failed) {
            Throwable cause=failed.getCause();
            if(cause instanceof Exception)throw (Exception)cause;
            if(cause instanceof Error)throw (Error)cause;
            throw new IllegalStateException("Assignment owner failed",cause);
        }
    }
}
