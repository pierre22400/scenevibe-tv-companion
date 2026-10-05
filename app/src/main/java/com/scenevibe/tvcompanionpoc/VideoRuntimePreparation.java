package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Shared pure Video text preparation: exact package shape, strict UTF-8 and the qualified
 * text-only parser. Generic bytes are bounded; historical String callers retain their exact
 * parser behavior. No transport, persistence, clock or live runtime owner is retained here.
 */
final class VideoRuntimePreparation {
    /** Closed preparation rejection never retains an input, parser exception or arbitrary message. */
    static class Invalid extends IllegalArgumentException {
        final InstallationStatus status;
        /** Expose only the fixed closed rejection status, without a submitted-content cause. */
        Invalid(InstallationStatus status) {super(status.name());this.status=status;}
    }

    /** Validate the exact codec/key vocabulary and obtain one detached bounded artifact map. */
    static Map<String,byte[]> artifacts(InstallRequest request,String codec,String... names) {
        if (request==null||!codec.equals(request.codecId())) throw new Invalid(InstallationStatus.INVALID_PACKAGE);
        Map<String,byte[]> artifacts=request.artifacts();
        if (!artifacts.keySet().equals(new TreeSet<>(Arrays.asList(names))))
            throw new Invalid(InstallationStatus.INVALID_PACKAGE);
        return artifacts;
    }

    /** Decode without replacement/normalization and retain the historical UTF-16 semantic ceiling. */
    static String utf8(byte[] bytes,int maximumUtf16) {
        if (bytes==null||bytes.length>maximumUtf16*3) throw new Invalid(InstallationStatus.INVALID_PACKAGE);
        try {
            String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (value.length()>maximumUtf16) throw new Invalid(InstallationStatus.INVALID_PACKAGE);
            return value;
        } catch (Exception invalid) {throw new Invalid(InstallationStatus.INVALID_PACKAGE);}
    }

    /** Apply the original Cloud text-only check and TrackParser without changing its coercion rules. */
    static ScheduledTrack parseRuntime(String json) throws Exception {
        JSONObject envelope=new JSONObject(json);
        JSONArray comments=envelope.optJSONArray("comments");
        if(comments==null)throw new IllegalArgumentException("Invalid cloud comments");
        for(int i=0;i<comments.length();i++) {
            JSONObject item=comments.optJSONObject(i);
            if(item==null||item.has("media"))throw new IllegalArgumentException("Cloud media unsupported");
        }
        return TrackParser.parse(envelope, media -> {
            throw new TrackParser.Invalid("invalid_media","Cloud v1 is text-only");
        });
    }

    /** Reject unsupported executable requirements before any durable or live operation. */
    static void requireCapabilities(InstallRequest request,ExecutionRequirements requirements,TvCapabilities capabilities) {
        InstallationStatus status=capabilities==null?InstallationStatus.INVALID_PACKAGE:capabilities.validate(request,requirements);
        if (status!=InstallationStatus.VALIDATED) throw new Invalid(status);
    }

    /** Pure helper has no mutable instance. */
    private VideoRuntimePreparation() {}
}
