package com.scenevibe.tvcompanionpoc.installation;

import java.util.Map;

/**
 * Product-neutral bounded handoff: positive revision, codec id and inert artifact bytes.
 * Bytes are copied on input and output and kept in deterministic order. No transport ACK
 * binding, credentials, producer model or live-state owner is accepted by this value object.
 */
public final class InstallRequest {
    private final long revision;
    private final String codecId;
    private final Map<String,byte[]> artifacts;
    private final int packageBytes;

    /** Validate all outer bounds and copy artifacts before retaining any caller-owned data. */
    public InstallRequest(long revision,String codecId,Map<String,byte[]> artifacts) {
        this.revision=InstallationBounds.revision(revision);
        this.codecId=InstallationBounds.id(codecId);
        this.artifacts=InstallationBounds.artifacts(artifacts);
        int size=0;
        for (byte[] bytes:this.artifacts.values()) size+=bytes.length;
        this.packageBytes=size;
    }
    /** Return the exact incoming revision without incrementing or persisting it. */
    public long revision() {return revision;}
    /** Return the bounded shape id; recognizing it is a registry/capability responsibility. */
    public String codecId() {return codecId;}
    /** Return a fresh deep immutable-map snapshot; arrays in it are detached from this request. */
    public Map<String,byte[]> artifacts() {return InstallationBounds.artifacts(artifacts);}
    /** Return one detached artifact without exposing internal mutable arrays. */
    public byte[] artifact(String id) {
        byte[] bytes=id==null?null:artifacts.get(id);return bytes==null?null:bytes.clone();
    }
    /** Return a bounded artifact count without copying payloads. */
    public int artifactCount() {return artifacts.size();}
    /** Return the precomputed exact total byte count. */
    public int packageBytes() {return packageBytes;}
}
