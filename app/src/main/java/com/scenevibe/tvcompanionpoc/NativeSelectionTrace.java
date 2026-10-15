package com.scenevibe.tvcompanionpoc;

/**
 * E5-041 bounded, observation-only account of one native selection query.
 *
 * <p>No controller, token, package history or media text is retained. None of these fields is
 * read by admission or transport guards. A copy is handed to diagnostics only after the query;
 * diagnostic failures cannot grant a target or suppress the original query exception.</p>
 */
final class NativeSelectionTrace {
    /** Identify why the unchanged native selector was queried. */
    enum Operation { SELECT, OBSERVE, PAUSE, PLAY }
    /** Stable codes cover every existing normal/exceptional exit without replacing TARGET. */
    enum Code {
        PENDING, SELECTED, VOD_NOT_ATTESTED, FOREGROUND_REFUSED, FOREGROUND_EXCEPTION,
        NOTIFICATION_ACCESS_DENIED, NOTIFICATION_ACCESS_EXCEPTION,
        SESSION_MANAGER_UNAVAILABLE, SESSION_LIST_UNAVAILABLE, NO_PRIME_SESSION,
        MULTIPLE_PRIME_SESSIONS, COMPETING_SESSION, NATIVE_READ_REFUSED,
        UNEXPECTED_CONTROLLER_ENTRY, UNEXPECTED_PACKAGE, SYSTEM_EXCEPTION
    }
    /** Keep foreground absence, insufficient history and concurrent resumed apps distinct. */
    enum Foreground {
        NOT_EVALUATED, PRIME_ALONE, ACCESS_DENIED, MANAGER_UNAVAILABLE,
        EVENTS_UNAVAILABLE, NO_RELEVANT_EVENTS, EVENT_LIMIT, AMBIGUOUS,
        PRIME_NOT_FOREGROUND, SYSTEM_EXCEPTION, HISTORY_INCOMPLETE, DEVICE_NOT_VISIBLE
    }
    /** Indicate the exact original short-circuit comparison that rejected the double read. */
    enum Read {
        NOT_EVALUATED, COHERENT, METADATA_MISSING, STATE_MISSING, METADATA_CHANGED,
        STATE_CHANGED, POSITION_CHANGED, UPDATE_TIME_CHANGED, SPEED_CHANGED, ACTIONS_CHANGED
    }
    /** Identify the changed metadata field without retaining either field's private value. */
    enum MetadataDifference { NOT_EVALUATED, NONE, MEDIA_ID, TITLE, SUBTITLE, DURATION }
    /** Locate an exception without recording its message, class payload or private content. */
    enum Stage { START, FOREGROUND, NOTIFICATION, MANAGER, SESSION_LIST, CONTROLLER, READS, COMPLETE }
    /** Locate partial native reads when an Android API throws before producing both samples. */
    enum ReadStep { NOT_STARTED, FIRST_METADATA, FIRST_STATE, SECOND_METADATA, SECOND_STATE, COMPARE, BUILD, DONE }

    final Operation operation;
    final long query;
    final long selectedAtMs;
    long finishedAtMs;
    Code code = Code.PENDING;
    Foreground foreground = Foreground.NOT_EVALUATED;
    Read read = Read.NOT_EVALUATED;
    MetadataDifference metadataDifference = MetadataDifference.NOT_EVALUATED;
    Stage stage = Stage.START;
    ReadStep readStep = ReadStep.NOT_STARTED;
    boolean detailsComplete = true;
    int eventsExamined;
    int relevantEvents;
    int resumedApps = -1;
    long lastRelevantEventAgeMs = -1;
    long foregroundWindowMs = -1;
    int sessionCount = -1;
    int visitedSessions;
    int ignoredSessions;
    int primeCandidates;
    int competingSessions;
    int readControllerIndex = -1;
    boolean readWasPrime;
    boolean firstMetadataPresent;
    boolean secondMetadataPresent;
    boolean firstStatePresent;
    boolean secondStatePresent;
    int firstState = -1;
    int secondState = -1;
    long firstPositionMs = -1;
    long secondPositionMs = -1;
    long firstUpdatedAtMs = -1;
    long secondUpdatedAtMs = -1;
    float firstSpeed;
    float secondSpeed;

    /** Start a query with an execution-clock timestamp, never a media or editorial clock. */
    NativeSelectionTrace(Operation operation, long query, long selectedAtMs) {
        this.operation = operation;
        this.query = query;
        this.selectedAtMs = selectedAtMs;
    }

    /** Reset the per-controller evidence before reading another live controller in this query. */
    void beginRead(int controllerIndex, boolean isPrime) {
        readControllerIndex = controllerIndex;
        readWasPrime = isPrime;
        read = Read.NOT_EVALUATED;
        metadataDifference = MetadataDifference.NOT_EVALUATED;
        readStep = ReadStep.NOT_STARTED;
        firstMetadataPresent = secondMetadataPresent = firstStatePresent = secondStatePresent = false;
        firstState = secondState = -1;
        firstPositionMs = secondPositionMs = firstUpdatedAtMs = secondUpdatedAtMs = -1;
        firstSpeed = secondSpeed = 0f;
    }

    /** Snapshot primitive diagnostics so no observer receives mutable native/session objects. */
    NativeSelectionTrace copy() {
        NativeSelectionTrace result = new NativeSelectionTrace(operation, query, selectedAtMs);
        result.finishedAtMs = finishedAtMs;
        result.code = code;
        result.foreground = foreground;
        result.read = read;
        result.metadataDifference = metadataDifference;
        result.stage = stage;
        result.readStep = readStep;
        result.detailsComplete = detailsComplete;
        result.eventsExamined = eventsExamined;
        result.relevantEvents = relevantEvents;
        result.resumedApps = resumedApps;
        result.lastRelevantEventAgeMs = lastRelevantEventAgeMs;
        result.foregroundWindowMs = foregroundWindowMs;
        result.sessionCount = sessionCount;
        result.visitedSessions = visitedSessions;
        result.ignoredSessions = ignoredSessions;
        result.primeCandidates = primeCandidates;
        result.competingSessions = competingSessions;
        result.readControllerIndex = readControllerIndex;
        result.readWasPrime = readWasPrime;
        result.firstMetadataPresent = firstMetadataPresent;
        result.secondMetadataPresent = secondMetadataPresent;
        result.firstStatePresent = firstStatePresent;
        result.secondStatePresent = secondStatePresent;
        result.firstState = firstState;
        result.secondState = secondState;
        result.firstPositionMs = firstPositionMs;
        result.secondPositionMs = secondPositionMs;
        result.firstUpdatedAtMs = firstUpdatedAtMs;
        result.secondUpdatedAtMs = secondUpdatedAtMs;
        result.firstSpeed = firstSpeed;
        result.secondSpeed = secondSpeed;
        return result;
    }
}
