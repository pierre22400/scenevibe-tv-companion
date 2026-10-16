package com.scenevibe.tvcompanionpoc.installation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eager immutable registry assembled explicitly from build-local handler instances.
 * At most three current shape slots are permitted: the two Video handlers plus one Banner
 * handler (section 8). Lookup never constructs, discovers, registers or invokes a handler.
 * The extension is explicit and finite; there is no discovery, reflection or remote handler.
 */
public final class InstallationHandlerRegistry {
    private static final InstallationHandlerRegistry EMPTY=new InstallationHandlerRegistry();
    private final Map<String,Entry> byHandler,byCodec;
    private final List<Entry> entries;

    /** Immutable registration metadata remains stable even when a test handler changes its state. */
    public static final class Entry {
        private final String handlerId,codecId;
        private final InstallationHandler handler;
        /** Bind explicit identifiers to an existing trusted instance without invoking it. */
        public Entry(String handlerId,String codecId,InstallationHandler handler) {
            this.handlerId=InstallationBounds.id(handlerId);this.codecId=InstallationBounds.id(codecId);
            if (handler==null) throw new IllegalArgumentException("Missing installation handler");
            this.handler=handler;
        }
        /** Return fixed local registration identity. */
        public String handlerId() {return handlerId;}
        /** Return fixed codec/package-kind identity. */
        public String codecId() {return codecId;}
        /** Return the already-created handler; lookup never executes it. */
        public InstallationHandler handler() {return handler;}
    }

    /** Freeze at most three explicit registrations, rejecting unknown or ambiguous shape slots. */
    public InstallationHandlerRegistry(Entry... registrations) {
        if (registrations==null||registrations.length>3)
            throw new IllegalArgumentException("Invalid handler registry count");
        Map<String,Entry> handlers=new TreeMap<>(),codecs=new TreeMap<>();
        for (Entry entry:registrations) {
            if (entry==null||!TvCapabilities.current().supportedCodecs().contains(entry.codecId())
                    ||handlers.containsKey(entry.handlerId())||codecs.containsKey(entry.codecId()))
                throw new IllegalArgumentException("Invalid handler registry binding");
            handlers.put(entry.handlerId(),entry);codecs.put(entry.codecId(),entry);
        }
        byHandler=Collections.unmodifiableMap(handlers);byCodec=Collections.unmodifiableMap(codecs);
        entries=Collections.unmodifiableList(new ArrayList<>(handlers.values()));
    }
    /** Return the unwired empty registry, without advertising an installed generic handler. */
    public static InstallationHandlerRegistry empty() {return EMPTY;}
    /** Resolve an explicit static handler id; null and unknown ids are rejected as absent. */
    public Entry findHandler(String handlerId) {return handlerId==null?null:byHandler.get(handlerId);}
    /** Resolve an explicit static codec id; null and unknown ids are rejected as absent. */
    public Entry findCodec(String codecId) {return codecId==null?null:byCodec.get(codecId);}
    /** Expose a stable sorted immutable list of immutable binding metadata. */
    public List<Entry> entries() {return entries;}
}
