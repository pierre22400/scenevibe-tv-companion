package com.scenevibe.tvcompanionpoc.installation;

/**
 * Immutable durable candidate: one exact revision/codec/artifact set and one static handler
 * identity. Bytes remain opaque; this value neither parses a product nor implies an armed
 * runtime. Acknowledgement is deliberately held separately by InstallationStore.
 */
public final class InstallationSnapshot {
    private final InstallRequest canonical;
    private final String handlerId;

    /** Retain only an already-immutable bounded request and a bounded local handler id. */
    public InstallationSnapshot(InstallRequest canonical,String handlerId) {
        if (canonical==null) throw new IllegalArgumentException("Missing installation snapshot");
        this.canonical=canonical;this.handlerId=InstallationBounds.id(handlerId);
    }
    /** Return exact persisted revision without assigning a new one. */
    public long revision() {return canonical.revision();}
    /** Return the codec identity independently of the static handler identity. */
    public String codecId() {return canonical.codecId();}
    /** Return the selected local handler id without resolving or executing it. */
    public String handlerId() {return handlerId;}
    /** Return the immutable canonical request whose array accessors make defensive copies. */
    public InstallRequest canonical() {return canonical;}
}
