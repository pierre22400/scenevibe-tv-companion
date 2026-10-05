package com.scenevibe.tvcompanionpoc.installation;

import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Versioned, deterministic newline framing with safe ASCII ids and canonical padded Base64.
 * Counts and encoded/decoded sizes are checked before substring/decode/copy. There is no
 * JSON recursion, Java serialization or product parser. ACK is not part of this encoding.
 */
final class InstallationSnapshotCodec {
    static final int MAX_ENCODED_CHARACTERS=4_001_024;
    private static final int MAX_ENCODED_ARTIFACT=3_200_000;

    /** Encode one bounded immutable snapshot with sorted artifacts and an explicit final newline. */
    static String encode(InstallationSnapshot snapshot) {
        StringBuilder text=new StringBuilder();
        text.append(InstallationStore.FORMAT_VERSION).append('\n').append(snapshot.revision()).append('\n')
                .append(snapshot.codecId()).append('\n').append(snapshot.handlerId()).append('\n')
                .append(snapshot.canonical().artifactCount()).append('\n');
        for (Map.Entry<String,byte[]> artifact:snapshot.canonical().artifacts().entrySet())
            text.append(artifact.getKey()).append('\n')
                    .append(Base64.getEncoder().encodeToString(artifact.getValue())).append('\n');
        if (text.length()>MAX_ENCODED_CHARACTERS) throw invalid();
        return text.toString();
    }

    /** Reject malformed/unknown/trailing/duplicate representations before constructing a snapshot. */
    static InstallationSnapshot decode(String encoded) {
        if (encoded==null||encoded.length()>MAX_ENCODED_CHARACTERS) throw invalid();
        Cursor cursor=new Cursor(encoded);
        if (!InstallationStore.FORMAT_VERSION.equals(cursor.line(64))) throw invalid();
        String rawRevision=cursor.line(19);
        long revision=decimal(rawRevision,false);
        if (!Long.toString(revision).equals(rawRevision)) throw invalid();
        String codec=InstallationBounds.id(cursor.line(128));
        String handler=InstallationBounds.id(cursor.line(128));
        String rawCount=cursor.line(1);
        int count=(int)decimal(rawCount,false);
        if (count>InstallationBounds.MAX_ARTIFACTS) throw invalid();
        Map<String,byte[]> artifacts=new TreeMap<>();
        long total=0;String previous=null;
        for (int i=0;i<count;i++) {
            String id=InstallationBounds.id(cursor.line(128));
            if (previous!=null&&previous.compareTo(id)>=0) throw invalid();
            previous=id;
            String base64=cursor.line(MAX_ENCODED_ARTIFACT);
            int size=decodedSize(base64);
            total+=size;
            if (total>InstallationBounds.MAX_PACKAGE_BYTES) throw invalid();
            byte[] bytes=Base64.getDecoder().decode(base64);
            if (bytes.length!=size||!Base64.getEncoder().encodeToString(bytes).equals(base64)) throw invalid();
            artifacts.put(id,bytes);
        }
        if (!cursor.atEnd()) throw invalid();
        return new InstallationSnapshot(new InstallRequest(revision,codec,artifacts),handler);
    }

    /** Bound a decimal storage field before parsing; ACK may be zero, installation may not. */
    static long decimal(String raw,boolean zeroAllowed) {
        if (raw==null||raw.isEmpty()||raw.length()>19) throw invalid();
        for (int i=0;i<raw.length();i++) if (raw.charAt(i)<'0'||raw.charAt(i)>'9') throw invalid();
        long value=Long.parseLong(raw);
        if (value<0||(!zeroAllowed&&value==0)) throw invalid();
        return value;
    }

    /** Bound the decoded allocation, accounting for mandatory padding, before invoking Base64. */
    private static int decodedSize(String value) {
        int length=value.length();
        if (length==0||length%4!=0) throw invalid();
        int padding=value.charAt(length-1)=='='?1:0;
        if (padding==1&&value.charAt(length-2)=='=') padding++;
        int size=(length/4)*3-padding;
        if (size<1||size>InstallationBounds.MAX_ARTIFACT_BYTES) throw invalid();
        return size;
    }

    /** Return only a fixed label: corrupt bytes/ids must never appear in an exception message. */
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Invalid installation encoding");}

    /** Cursor avoids an unbounded split/allocation ahead of the artifact-count check. */
    private static final class Cursor {
        private final String value;
        private int position;
        /** Retain the already outer-bounded immutable encoded string. */
        Cursor(String value) {this.value=value;}
        /** Locate a required newline, check the field bound, then allocate that field only. */
        String line(int maximum) {
            int end=value.indexOf('\n',position);
            if (end<0||end-position>maximum) throw invalid();
            String line=value.substring(position,end);position=end+1;return line;
        }
        /** Forbid trailing metadata, extra artifacts or a missing final delimiter. */
        boolean atEnd() {return position==value.length();}
    }
    /** Encoding has no stateful instance. */
    private InstallationSnapshotCodec() {}
}
