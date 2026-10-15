package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Read/dispatch adapter adapted from the Spike's MediaSessionScanner and SessionCatalog.
 *
 * <p>Every read and command reacquires controllers and the original token. Metadata and position
 * come from the SAME controller, with a second read rejecting races. No controller or token is
 * stored for future commands. There is no focus request, polling scheduler, Cloud or autonomous PLAY.</p>
 */
final class AndroidNativeSessionPort implements NativeSessionPort {
    private final Context context;
    private final AndroidForegroundEvidence foreground;
    private final Consumer<NativeSelectionTrace> observer;
    private long selectionQuery;

    /** Bind coherent native observations and explicit foreground evidence for this profile only. */
    AndroidNativeSessionPort(Context context, AndroidForegroundEvidence foreground) {
        this(context, foreground, trace -> {});
    }

    /** Bind a void diagnostic sink that has no access to native controllers or decision inputs. */
    AndroidNativeSessionPort(Context context, AndroidForegroundEvidence foreground,
            Consumer<NativeSelectionTrace> observer) {
        this.context = context.getApplicationContext();
        this.foreground = foreground;
        this.observer = observer;
    }

    /** Select one VOD-attested Prime controller, refusing relevant competing sessions. */
    @Override public NativeSessionSnapshot select(boolean vodConfirmed) {
        Selection selection = choose(vodConfirmed, NativeSelectionTrace.Operation.SELECT);
        return selection == null ? null : selection.snapshot;
    }

    /** Reacquire the same token/program; a package-only replacement is not accepted. */
    @Override public NativeSessionSnapshot observe(NativeSessionSnapshot original) {
        Selection selected = choose(original != null && original.vodConfirmed,
                NativeSelectionTrace.Operation.OBSERVE);
        return selected != null && original.sameTarget(selected.snapshot) ? selected.snapshot : null;
    }

    /** Revalidate at actual PAUSE dispatch; no stale controller is retained from selection. */
    @Override public boolean pause(NativeSessionSnapshot expected,
            NativeQualificationPolicy policy, long nowMs) {
        Selection selected = choose(expected != null && expected.vodConfirmed,
                NativeSelectionTrace.Operation.PAUSE);
        if (selected == null || !expected.sameTarget(selected.snapshot)
                || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())
                || selected.snapshot.state != NativeSessionSnapshot.PLAYING
                || selected.snapshot.speed != 1f
                || !selected.snapshot.canPause || !selected.snapshot.canPlay) return false;
        try {
            if (!foreground.primeForeground()
                    || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())) return false;
            selected.controller.getTransportControls().pause();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** Revalidate the exact raw anchor and provider update immediately before dispatching PLAY. */
    @Override public boolean play(NativeSessionSnapshot anchor,
            NativeQualificationPolicy policy, long nowMs) {
        Selection selected = choose(anchor != null && anchor.vodConfirmed,
                NativeSelectionTrace.Operation.PLAY);
        if (selected == null || !anchor.sameTarget(selected.snapshot)
                || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())
                || selected.snapshot.state != NativeSessionSnapshot.PAUSED
                || selected.snapshot.speed != 0f || !selected.snapshot.canPlay
                || selected.snapshot.updatedAtMs != anchor.updatedAtMs
                || Math.abs(selected.snapshot.positionMs - anchor.positionMs) > policy.positionToleranceMs) {
            return false;
        }
        try {
            if (!foreground.primeForeground()
                    || !policy.fresh(selected.snapshot, SystemClock.elapsedRealtime())) return false;
            selected.controller.getTransportControls().play();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** Query live controllers; explicit foreground proof also rejects HOME/app departure. */
    private Selection choose(boolean vodConfirmed, NativeSelectionTrace.Operation operation) {
        NativeSelectionTrace trace = new NativeSelectionTrace(operation, ++selectionQuery,
                SystemClock.elapsedRealtime());
        try {
            return chooseObserved(vodConfirmed, trace);
        } finally {
            trace.finishedAtMs = SystemClock.elapsedRealtime();
            try {
                // Memory-only sink in production: the existing E5 event emits the trace later.
                // A broken diagnostic observer cannot change the original return or exception.
                if (observer != null) observer.accept(trace.copy());
            } catch (RuntimeException diagnosticUnavailable) {
                // Preserve the original selection and transport decision, including exceptions.
            }
        }
    }

    /** Keep the original short-circuit order and native catch boundary while naming each exit. */
    private Selection chooseObserved(boolean vodConfirmed, NativeSelectionTrace trace) {
        if (!vodConfirmed) return denied(trace, NativeSelectionTrace.Code.VOD_NOT_ATTESTED);
        trace.stage = NativeSelectionTrace.Stage.FOREGROUND;
        try {
            if (!foreground.primeForeground(trace)) {
                return denied(trace, NativeSelectionTrace.Code.FOREGROUND_REFUSED);
            }
        } catch (RuntimeException unavailable) {
            trace.code = NativeSelectionTrace.Code.FOREGROUND_EXCEPTION;
            throw unavailable; // This exception was outside the legacy native-session catch.
        }
        trace.stage = NativeSelectionTrace.Stage.NOTIFICATION;
        try {
            if (!NotificationAccess.isGranted(context)) {
                return denied(trace, NativeSelectionTrace.Code.NOTIFICATION_ACCESS_DENIED);
            }
        } catch (RuntimeException unavailable) {
            trace.code = NativeSelectionTrace.Code.NOTIFICATION_ACCESS_EXCEPTION;
            throw unavailable; // Preserve the same pre-query exception behavior.
        }
        try {
            trace.stage = NativeSelectionTrace.Stage.MANAGER;
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            if (manager == null) return denied(trace, NativeSelectionTrace.Code.SESSION_MANAGER_UNAVAILABLE);
            trace.stage = NativeSelectionTrace.Stage.SESSION_LIST;
            List<MediaController> controllers = manager.getActiveSessions(
                    new ComponentName(context, MediaSessionAccessService.class));
            if (controllers == null) return denied(trace, NativeSelectionTrace.Code.SESSION_LIST_UNAVAILABLE);
            try {
                trace.sessionCount = controllers.size();
            } catch (RuntimeException diagnosticUnavailable) {
                trace.detailsComplete = false;
            }
            Selection prime = null;
            for (MediaController controller : controllers) {
                trace.stage = NativeSelectionTrace.Stage.CONTROLLER;
                trace.visitedSessions++;
                if (controller == null) return denied(trace, NativeSelectionTrace.Code.UNEXPECTED_CONTROLLER_ENTRY);
                String pkg = controller.getPackageName();
                if (pkg == null) return denied(trace, NativeSelectionTrace.Code.UNEXPECTED_PACKAGE);
                if (pkg.equals(context.getPackageName()) || pkg.startsWith(
                        "com.scenevibe.tvcompanionpoc.mediaexperiment")) {
                    trace.ignoredSessions++;
                    continue;
                }
                if (NativeQualificationPolicy.PRIME_PACKAGE.equals(pkg)) trace.primeCandidates++;
                trace.stage = NativeSelectionTrace.Stage.READS;
                trace.beginRead(trace.visitedSessions - 1, NativeQualificationPolicy.PRIME_PACKAGE.equals(pkg));
                NativeSessionSnapshot sample = snapshot(controller, vodConfirmed, trace);
                if (sample == null) return denied(trace, NativeSelectionTrace.Code.NATIVE_READ_REFUSED);
                if (NativeQualificationPolicy.PRIME_PACKAGE.equals(pkg)) {
                    if (prime != null) return denied(trace, NativeSelectionTrace.Code.MULTIPLE_PRIME_SESSIONS);
                    prime = new Selection(controller, sample);
                } else if (relevant(sample.state)) {
                    trace.competingSessions++;
                    return denied(trace, NativeSelectionTrace.Code.COMPETING_SESSION);
                }
            }
            trace.stage = NativeSelectionTrace.Stage.COMPLETE;
            trace.code = prime == null ? NativeSelectionTrace.Code.NO_PRIME_SESSION
                    : NativeSelectionTrace.Code.SELECTED;
            return prime;
        } catch (RuntimeException unavailable) {
            return denied(trace, NativeSelectionTrace.Code.SYSTEM_EXCEPTION);
        }
    }

    /** Annotate an existing null outcome; this helper cannot manufacture a native Selection. */
    private static Selection denied(NativeSelectionTrace trace, NativeSelectionTrace.Code code) {
        trace.code = code;
        return null;
    }

    /** Capture metadata and playback twice on this controller; mismatched reads fail closed. */
    private static NativeSessionSnapshot snapshot(MediaController controller, boolean vodConfirmed) {
        return snapshot(controller, vodConfirmed, null);
    }

    /** Name the first unchanged coherence failure; never log between the two Binder reads. */
    private static NativeSessionSnapshot snapshot(MediaController controller, boolean vodConfirmed,
            NativeSelectionTrace trace) {
        step(trace, NativeSelectionTrace.ReadStep.FIRST_METADATA);
        MediaMetadata firstMetadata = controller.getMetadata();
        if (trace != null) trace.firstMetadataPresent = firstMetadata != null;
        step(trace, NativeSelectionTrace.ReadStep.FIRST_STATE);
        PlaybackState first = controller.getPlaybackState();
        if (trace != null) trace.firstStatePresent = first != null;
        step(trace, NativeSelectionTrace.ReadStep.SECOND_METADATA);
        MediaMetadata metadata = controller.getMetadata();
        if (trace != null) trace.secondMetadataPresent = metadata != null;
        step(trace, NativeSelectionTrace.ReadStep.SECOND_STATE);
        PlaybackState state = controller.getPlaybackState();
        if (trace != null) trace.secondStatePresent = state != null;
        step(trace, NativeSelectionTrace.ReadStep.COMPARE);
        // This is the original admission predicate. Diagnostic enums are never consulted here.
        boolean incoherent = firstMetadata == null || metadata == null || first == null || state == null
                || !sameMetadata(firstMetadata, metadata) || first.getState() != state.getState()
                || first.getPosition() != state.getPosition()
                || first.getLastPositionUpdateTime() != state.getLastPositionUpdateTime()
                || first.getPlaybackSpeed() != state.getPlaybackSpeed()
                || first.getActions() != state.getActions();
        observeReads(trace, firstMetadata, first, metadata, state);
        if (incoherent) return null;
        step(trace, NativeSelectionTrace.ReadStep.BUILD);
        long actions = state.getActions();
        NativeSessionSnapshot sample = new NativeSessionSnapshot(controller.getSessionToken(), controller.getPackageName(),
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION), state.getPosition(),
                SystemClock.elapsedRealtime(), state.getLastPositionUpdateTime(),
                state.getPlaybackSpeed(), state.getState(),
                (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0,
                (actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0,
                vodConfirmed);
        step(trace, NativeSelectionTrace.ReadStep.DONE);
        return sample;
    }

    /** Isolate all extra diagnostic getters from native decisions, including observer failures. */
    private static void observeReads(NativeSelectionTrace trace, MediaMetadata firstMetadata,
            PlaybackState first, MediaMetadata metadata, PlaybackState state) {
        if (trace == null) return;
        try {
            classifyReads(trace, firstMetadata, first, metadata, state);
        } catch (RuntimeException diagnosticUnavailable) {
            trace.detailsComplete = false;
        }
    }

    /** Classify the unchanged first failing comparison solely for a bounded diagnostic record. */
    private static void classifyReads(NativeSelectionTrace trace, MediaMetadata firstMetadata,
            PlaybackState first, MediaMetadata metadata, PlaybackState state) {
        NativeSelectionTrace.Read read;
        if (firstMetadata == null || metadata == null) read = NativeSelectionTrace.Read.METADATA_MISSING;
        else if (first == null || state == null) read = NativeSelectionTrace.Read.STATE_MISSING;
        else if (!sameMetadata(firstMetadata, metadata)) read = NativeSelectionTrace.Read.METADATA_CHANGED;
        else if (first.getState() != state.getState()) read = NativeSelectionTrace.Read.STATE_CHANGED;
        else if (first.getPosition() != state.getPosition()) read = NativeSelectionTrace.Read.POSITION_CHANGED;
        else if (first.getLastPositionUpdateTime() != state.getLastPositionUpdateTime()) {
            read = NativeSelectionTrace.Read.UPDATE_TIME_CHANGED;
        } else if (first.getPlaybackSpeed() != state.getPlaybackSpeed()) read = NativeSelectionTrace.Read.SPEED_CHANGED;
        else if (first.getActions() != state.getActions()) read = NativeSelectionTrace.Read.ACTIONS_CHANGED;
        else read = NativeSelectionTrace.Read.COHERENT;
        captureReads(trace, read, firstMetadata, first, metadata, state);
    }

    /** Record query progress without changing controller calls or providing transport authority. */
    private static void step(NativeSelectionTrace trace, NativeSelectionTrace.ReadStep step) {
        if (trace != null) trace.readStep = step;
    }

    /** Copy only immutable public numeric playback fields; no metadata text or token is retained. */
    private static void captureReads(NativeSelectionTrace trace, NativeSelectionTrace.Read read,
            MediaMetadata firstMetadata, PlaybackState first, MediaMetadata metadata, PlaybackState state) {
        if (trace == null) return;
        trace.read = read;
        trace.firstMetadataPresent = firstMetadata != null;
        trace.secondMetadataPresent = metadata != null;
        trace.firstStatePresent = first != null;
        trace.secondStatePresent = state != null;
        if (firstMetadata != null && metadata != null) {
            trace.metadataDifference = metadataDifference(firstMetadata, metadata);
        }
        if (first != null) {
            trace.firstState = first.getState();
            trace.firstPositionMs = first.getPosition();
            trace.firstUpdatedAtMs = first.getLastPositionUpdateTime();
            trace.firstSpeed = first.getPlaybackSpeed();
        }
        if (state != null) {
            trace.secondState = state.getState();
            trace.secondPositionMs = state.getPosition();
            trace.secondUpdatedAtMs = state.getLastPositionUpdateTime();
            trace.secondSpeed = state.getPlaybackSpeed();
        }
    }

    /** Return only the first changed metadata key category, never the private old/new values. */
    private static NativeSelectionTrace.MetadataDifference metadataDifference(MediaMetadata first,
            MediaMetadata second) {
        if (!Objects.equals(first.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                second.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))) return NativeSelectionTrace.MetadataDifference.MEDIA_ID;
        if (!Objects.equals(first.getString(MediaMetadata.METADATA_KEY_TITLE),
                second.getString(MediaMetadata.METADATA_KEY_TITLE))) return NativeSelectionTrace.MetadataDifference.TITLE;
        if (!Objects.equals(first.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                second.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))) return NativeSelectionTrace.MetadataDifference.SUBTITLE;
        return first.getLong(MediaMetadata.METADATA_KEY_DURATION) != second.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ? NativeSelectionTrace.MetadataDifference.DURATION : NativeSelectionTrace.MetadataDifference.NONE;
    }

    /** Reject metadata changes during a coherent observation without logging the content. */
    private static boolean sameMetadata(MediaMetadata first, MediaMetadata second) {
        return Objects.equals(first.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                second.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
                && Objects.equals(first.getString(MediaMetadata.METADATA_KEY_TITLE),
                second.getString(MediaMetadata.METADATA_KEY_TITLE))
                && Objects.equals(first.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                second.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
                && first.getLong(MediaMetadata.METADATA_KEY_DURATION)
                == second.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    /** Treat another app playing/preparing/seeking as an ambiguous native ownership signal. */
    private static boolean relevant(int state) {
        return state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_STOPPED
                && state != PlaybackState.STATE_PAUSED && state != PlaybackState.STATE_ERROR;
    }

    /** A controller and its coherent snapshot are local to this one query/dispatch only. */
    private static final class Selection {
        final MediaController controller;
        final NativeSessionSnapshot snapshot;

        /** Keep matched values together until this invocation finishes. */
        Selection(MediaController controller, NativeSessionSnapshot snapshot) {
            this.controller = controller;
            this.snapshot = snapshot;
        }
    }
}
