package com.scenevibe.tvcompanionpoc.installation;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

/**
 * Pure limits for the additive installation model. Counts and sizes are checked before
 * copying data; errors contain fixed labels only. UTF-8 byte ceilings do not replace the
 * existing compatibility parsers' UTF-16 limits. No parsing, persistence or runtime occurs.
 */
final class InstallationBounds {
    static final int MAX_ID_LENGTH=128;
    static final int MAX_ARTIFACTS=2;
    static final int MAX_PACKAGE_BYTES=3_000_000;
    // An 800,000 UTF-16-unit artifact needs at most 2,400,000 UTF-8 bytes.
    static final int MAX_ARTIFACT_BYTES=2_400_000;
    static final int MAX_PREPARED_VALUES=256;
    static final int MAX_PREPARED_VALUE_UTF16=4_000;
    private static final Pattern ID=Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    /** Validate a local identifier without echoing it, normalizing it or truncating it. */
    static String id(String value) {
        if (value==null||value.length()>MAX_ID_LENGTH||!ID.matcher(value).matches())
            throw new IllegalArgumentException("Invalid installation identifier");
        return value;
    }

    /** Require a positive device-local revision without inventing a new revision authority. */
    static long revision(long value) {
        if (value<1) throw new IllegalArgumentException("Invalid installation revision");
        return value;
    }

    /** Deep-copy the bounded artifact set into deterministic order, retaining exact bytes. */
    static Map<String,byte[]> artifacts(Map<String,byte[]> source) {
        if (source==null||source.isEmpty()||source.size()>MAX_ARTIFACTS)
            throw new IllegalArgumentException("Invalid artifact count");
        Map<String,byte[]> result=new TreeMap<>();
        long total=0;int count=0;
        for (Map.Entry<String,byte[]> entry:source.entrySet()) {
            if (++count>MAX_ARTIFACTS) throw new IllegalArgumentException("Invalid artifact count");
            String key=id(entry.getKey());byte[] bytes=entry.getValue();
            if (bytes==null||bytes.length==0||bytes.length>MAX_ARTIFACT_BYTES)
                throw new IllegalArgumentException("Invalid artifact size");
            total+=bytes.length;
            if (total>MAX_PACKAGE_BYTES) throw new IllegalArgumentException("Invalid package size");
            result.put(key,bytes.clone());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Freeze normalized prepared scalar values; arbitrary mutable runtime objects are excluded. */
    static Map<String,String> preparedValues(Map<String,String> source) {
        if (source==null||source.size()>MAX_PREPARED_VALUES)
            throw new IllegalArgumentException("Invalid prepared value count");
        Map<String,String> result=new TreeMap<>();
        long bytes=0;int count=0;
        for (Map.Entry<String,String> entry:source.entrySet()) {
            if (++count>MAX_PREPARED_VALUES) throw new IllegalArgumentException("Invalid prepared value count");
            String key=id(entry.getKey()),value=entry.getValue();
            if (value==null||value.length()>MAX_PREPARED_VALUE_UTF16)
                throw new IllegalArgumentException("Invalid prepared value size");
            bytes+=key.length()+value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes>MAX_PACKAGE_BYTES) throw new IllegalArgumentException("Invalid prepared values size");
            result.put(key,value);
        }
        return Collections.unmodifiableMap(result);
    }

    /** No instances: all operations are bounded value construction. */
    private InstallationBounds() {}
}
