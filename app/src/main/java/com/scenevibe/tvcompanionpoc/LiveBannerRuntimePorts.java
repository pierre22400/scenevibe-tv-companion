package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.wall.WallCalendarScheduler;

/**
 * Banner (WALL) activation ports for the single principal common runtime owner. This is a direct
 * reuse of the {@link OverlayService.LiveVideoRuntimePorts} activation mechanic (section 13):
 * {@code pending/active/retiring} {@link Activation} records, a fresh unique token minted per ARM
 * (even for the same revision), the {@link SceneRuntimeController} presentation generation captured
 * ONCE after {@link SceneRuntimeController#replaceRevision}, {@code invalidate()}-before-retire,
 * {@code matching(token)} honoring only the active binding, {@link #selectActiveBanner} as the
 * final ARM/promotion step, and {@link #abortActivation} bounded cleanup. It does NOT create a
 * second, competing LiveBanner authority: it is bound by the owner alongside the Video ports as a
 * STATIC instance, and {@code MediaSyncedTrackScheduler} is never instantiated here.
 *
 * <p>Kept Android-independent exactly like the Video ports (a {@link java.util.function.BooleanSupplier}
 * owner predicate, a {@link java.util.function.Supplier} of the controller, {@link Runnable} retire
 * hooks, a {@link java.util.function.LongConsumer} selection sink, a {@link java.util.function.Consumer}
 * eligibility diagnostic), so it runs on the JVM with fakes. The Android WALL clock/driver/timer that
 * actually produces the {@link WallCalendarScheduler.Result} values is FEAT-003 and lives outside both
 * this class and the pure {@code wall/} package; this owner only routes an already-selected result.</p>
 *
 * <p>Section 7 alternating kinds: {@link #retireVideoVisualOwner} retires the opposite Video surface
 * (and the owner neutralizes the MEDIA path) before a Banner is promoted; a Video selection likewise
 * invalidates this WALL activation first. Section 6 eligibility: Banner local eligibility is initialized
 * explicitly after promotion because the controller's {@code eligible} flag survives loads (section 22),
 * and a regain re-evaluates the current window even if the eventId is identical. {@code pending} never
 * renders; a stale token or stale generation callback is a NO-OP.</p>
 */
final class LiveBannerRuntimePorts implements BannerInstallationRuntimePorts {

    /** Immutable activation identity and the generation captured at manifest ARM. */
    private static final class Activation {
        final BannerPreparedState state;
        final String token;
        final long generation;
        /** Retain memory-only prepared values and a fixed local token/generation. */
        Activation(BannerPreparedState state, String token, long generation) {
            this.state = state;
            this.token = token;
            this.generation = generation;
        }
    }

    private final java.util.function.BooleanSupplier owner;
    private final java.util.function.Supplier<SceneRuntimeController> controller;
    private final Runnable retireVideo;
    private final Runnable neutralizeMedia;
    private final Runnable retireScenes;
    private final java.util.function.LongConsumer selection;
    private final java.util.function.Consumer<Boolean> diagnostics;
    private long nextActivation;
    private Activation pending, active, retiring;

    /** Bind the common owner's existing seams; construction never loads, renders or persists. */
    LiveBannerRuntimePorts(java.util.function.BooleanSupplier owner,
            java.util.function.Supplier<SceneRuntimeController> controller,
            Runnable retireVideo, Runnable neutralizeMedia, Runnable retireScenes,
            java.util.function.LongConsumer selection,
            java.util.function.Consumer<Boolean> diagnostics) {
        if (owner == null || controller == null || retireVideo == null || neutralizeMedia == null
                || retireScenes == null || selection == null || diagnostics == null) {
            throw new IllegalArgumentException("Missing Banner owner");
        }
        this.owner = owner;
        this.controller = controller;
        this.retireVideo = retireVideo;
        this.neutralizeMedia = neutralizeMedia;
        this.retireScenes = retireScenes;
        this.selection = selection;
        this.diagnostics = diagnostics;
    }

    /** Android composition returns true only on the actual main/window owner thread. */
    @Override public boolean isOwnerThread() { return owner.getAsBoolean(); }

    /** Invalidate callback ownership before any synchronous native retirement can reenter. */
    private void invalidate() {
        if (active != null) retiring = active;
        else if (pending != null) retiring = pending;
        active = null;
        pending = null;
    }

    /**
     * Alternating-kind step: retire the opposite Video visual owner(s) and clear the MEDIA path
     * before any Banner can be promoted (section 7). Invalidates the current Banner binding first so
     * a late Video callback cannot reenter during the synchronous native retirement.
     */
    @Override public boolean retireVideoVisualOwner() {
        if (!isOwnerThread()) return false;
        invalidate();
        retireVideo.run();
        neutralizeMedia.run();
        return true;
    }

    /** Unload/invalidate the regie, then force immediate removal of its existing Banner window. */
    @Override public boolean retireManifestedVisualOwner() {
        if (!isOwnerThread()) return false;
        invalidate();
        SceneRuntimeController runtime = controller.get();
        if (runtime == null) return false;
        runtime.unload();
        retireScenes.run();
        return true;
    }

    /** Load the exact prepared Banner calendar/manifest under PENDING; pending cannot render. */
    @Override public boolean loadPreparedBanner(BannerPreparedState state) {
        if (!isOwnerThread() || state == null) return false;
        invalidate();
        if (nextActivation == Long.MAX_VALUE) {
            // Token counter cannot mint a fresh value without overflow: ARM fails closed,
            // never silently reusing a token (section 13).
            retiring = null;
            return false;
        }
        pending = new Activation(state, "banner-activation-" + (++nextActivation), -1L);
        retiring = null;
        return true;
    }

    /** Replace the exact manifest, then capture its generation once before selection. */
    @Override public boolean armPreparedManifest(long revision, OverlayManifest manifest) {
        if (!isOwnerThread() || revision < 1 || manifest == null || pending == null
                || pending.state.manifest != manifest) {
            return false;
        }
        SceneRuntimeController runtime = controller.get();
        if (runtime == null) return false;
        runtime.replaceRevision(revision, manifest);
        long captured = runtime.currentGeneration();
        if (captured < 0) {
            // Generation counter overflowed: refuse rather than route under a wrapped guard.
            return false;
        }
        pending = new Activation(pending.state, pending.token, captured);
        return runtime.isSceneRendererActiveFor(revision);
    }

    /**
     * Promote pending to active only after every previous ARM operation has succeeded, then
     * initialize Banner local eligibility explicitly (section 6/22: the controller eligible flag
     * survives loads, so the owner, not the controller, re-establishes eligibility at promotion).
     */
    @Override public boolean selectActiveBanner(long revision) {
        if (!isOwnerThread() || revision < 1 || pending == null) return false;
        SceneRuntimeController runtime = controller.get();
        if (runtime == null || pending.generation < 0
                || !runtime.isSceneRendererActiveFor(revision)) {
            return false;
        }
        // Establish the active binding BEFORE invoking the selection sink. The sink arms the
        // Android WALL driver, whose first fresh evaluation emits synchronously back through
        // onWallResult under this activation's token; matching(token) must already see the active
        // binding or that first DUE is dropped as a NO-OP (the idempotent <=1s heartbeat would then
        // never re-emit a window already live at promotion). Promotion has fully succeeded by this
        // point - every prior ARM step passed its guard - so active is correct here, and pending
        // still never renders: no callback was honored before this assignment.
        active = pending;
        pending = null;
        selection.accept(revision);
        // Banner local eligibility is initialized AFTER promotion; nothing is forced visible, a
        // subsequent DUE drives the first show (first WALL evaluation only after safe promotion).
        diagnostics.accept(true);
        runtime.onEligibility(true);
        return true;
    }

    /** An opaque callback token must match the immutable active activation, never a revision. */
    private Activation matching(String token) {
        return isOwnerThread() && active != null && active.token.equals(token) ? active : null;
    }

    /**
     * The active (or, during the selection callback before promotion completes, the pending) opaque
     * activation token, so the owning service can arm the Android WALL driver (FEAT-003) under the
     * exact token this core will route results for. Null when there is no activation. This exposes
     * no credential or content: the token is a bounded local {@code "banner-activation-N"} string.
     */
    String activeToken() {
        if (!isOwnerThread()) return null;
        if (active != null) return active.token;
        return pending != null ? pending.token : null;
    }

    /** The prepared Banner state backing the active/pending activation, so the service can arm the
     *  driver with its pure calendar. Memory-only, holds no clock/renderer/store. Null when none. */
    BannerPreparedState activeState() {
        if (!isOwnerThread()) return null;
        if (active != null) return active.state;
        return pending != null ? pending.state : null;
    }

    /**
     * Route one already-selected pure scheduler result under the active token only. Each EXIT then
     * each DUE is delivered to the reused controller with the generation captured at ARM, so a late
     * EXIT after a new ARM at the same eventId cannot hide the new scene (section 13 race table).
     * {@code pending} and stale tokens are refused; the controller independently drops a callback
     * whose generation no longer matches, so no stale wallGeneration result can resurrect a scene.
     */
    @Override public void onWallResult(String token, WallCalendarScheduler.Result result) {
        Activation binding = matching(token);
        if (binding == null || result == null) return;
        SceneRuntimeController runtime = controller.get();
        if (runtime == null) return;
        for (WallCalendarScheduler.Effect effect : result.effects()) {
            if (effect.kind() == WallCalendarScheduler.Kind.EXIT) {
                runtime.onEventExpired(effect.eventId(), binding.generation);
            } else {
                runtime.onEventDue(effect.eventId(), binding.generation);
            }
        }
    }

    /** Hide on suspension/overlay loss; a regain re-evaluates the current window on next result. */
    void onEligibility(String token, boolean eligible) {
        Activation binding = matching(token);
        if (binding == null) return;
        diagnostics.accept(eligible);
        SceneRuntimeController runtime = controller.get();
        if (runtime == null) return;
        runtime.onEligibility(eligible);
        if (!eligible) retireScenes.run();
    }

    /** Clear partial ownership idempotently, attempting every cleanup even after a refusal. */
    @Override public void abortActivation() {
        if (!isOwnerThread()) return;
        invalidate();
        cleanup(() -> { SceneRuntimeController runtime = controller.get(); if (runtime != null) runtime.unload(); });
        cleanup(retireScenes);
        pending = null;
        active = null;
        retiring = null;
        cleanup(() -> selection.accept(0));
    }

    /** A failed native removal cannot skip the remaining cleanup or expose raw exceptions. */
    private static void cleanup(Runnable work) {
        try { work.run(); } catch (RuntimeException refused) { /* Closed ARM_FAILED only. */ }
    }
}
