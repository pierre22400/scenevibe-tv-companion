package com.scenevibe.tvcompanionpoc.installation;

/**
 * Statically composed contract only: Phase B provides no compatibility handler or caller.
 * Implementations added later must keep validate/prepare/encode/restore memory-only and
 * invoke arm on the runtime owner thread. Inert snapshots reuse InstallRequest so no store,
 * persistence API or premature runtime orchestration is introduced here.
 * Phase D implementations remain Video-side; the additive prepared-state marker below
 * retains trusted immutable memory only, without importing a concrete product type.
 */
public interface InstallationHandler {
    /** Purely validate package semantics and local requirements, returning a bounded code. */
    InstallationStatus validate(InstallRequest request,TvCapabilities capabilities);
    /** Produce immutable normalized values, with no persistence, rendering or ACK. */
    PreparedInstallation prepare(InstallRequest request,TvCapabilities capabilities);
    /** Return the bounded canonical snapshot; encoding performs no storage write. */
    InstallRequest encodeForCache(PreparedInstallation prepared);
    /** Validate and prepare inert durable-snapshot values, with no activation or storage mutation. */
    PreparedInstallation restoreFromCache(InstallRequest snapshot,TvCapabilities capabilities);
    /** Later activate using trusted owner-thread ports; Phase B supplies no implementation. */
    InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports);

    /** Minimal compile-time port boundary; concrete runtime ports belong to subsequent phases. */
    interface RuntimePorts {}

    /**
     * Handler-owned immutable bounded preparation, never an arbitrary Object or serialized value.
     * Implementations must retain no Context, service, scheduler, renderer, client, secret or store.
     * Only their owning handler may interpret this slot; generic orchestration cannot inspect it.
     */
    interface PreparedState {}
}
