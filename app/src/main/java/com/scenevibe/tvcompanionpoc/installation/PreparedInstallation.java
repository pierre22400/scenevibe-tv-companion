package com.scenevibe.tvcompanionpoc.installation;

import java.util.Map;

/**
 * Immutable memory-only PREPARE value. It retains canonical bounded artifacts, normalized
 * immutable scalar values, a static handler id and executable requirements. Phase B uses
 * values instead of accepting arbitrary mutable runtime objects or service/network owners.
 * PREPARED never implies a durable commit, activation, visible scene or ACK eligibility.
 * Phase D adds a trusted handler-owned typed state slot. The original scalar constructor
 * remains valid and inert; no concrete product, Android owner or arbitrary Object is imported.
 */
public final class PreparedInstallation {
    private final InstallRequest canonical;
    private final String handlerId;
    private final Map<String,String> values;
    private final ExecutionRequirements requirements;
    private final InstallationHandler.PreparedState preparedState;

    /** Require a coherent local profile, then freeze the already-prepared canonical values. */
    public PreparedInstallation(InstallRequest canonical,String handlerId,Map<String,String> values,
            ExecutionRequirements requirements,TvCapabilities capabilities) {
        this(canonical,handlerId,values,requirements,capabilities,null);
    }
    /** Retain trusted immutable handler state in memory only, preserving every original value check. */
    public PreparedInstallation(InstallRequest canonical,String handlerId,Map<String,String> values,
            ExecutionRequirements requirements,TvCapabilities capabilities,
            InstallationHandler.PreparedState preparedState) {
        this.handlerId=InstallationBounds.id(handlerId);
        if (capabilities==null||capabilities.validate(canonical,requirements)!=InstallationStatus.VALIDATED)
            throw new IllegalArgumentException("Invalid prepared installation");
        this.canonical=canonical;this.requirements=requirements;
        this.values=InstallationBounds.preparedValues(values);
        this.preparedState=preparedState;
    }
    /** Return the exact revision of the canonical artifact set. */
    public long revision() {return canonical.revision();}
    /** Return the selected static handler id without discovering or loading a handler. */
    public String handlerId() {return handlerId;}
    /** Return the exact canonical codec id. */
    public String codecId() {return canonical.codecId();}
    /** Return the immutable bounded candidate intended for future persistence. */
    public InstallRequest canonical() {return canonical;}
    /** Return immutable prepared scalar values; no opaque mutable references are exposed. */
    public Map<String,String> values() {return values;}
    /** Return the immutable capability requirements that passed the local profile check. */
    public ExecutionRequirements requirements() {return requirements;}
    /** Return the owning handler's typed trusted state, or null for the original inert constructor. */
    public InstallationHandler.PreparedState preparedState() {return preparedState;}
    /** State only memory preparation, never ARMED. */
    public InstallationStatus status() {return InstallationStatus.PREPARED;}
}
