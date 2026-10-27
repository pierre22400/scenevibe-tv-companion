package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.AudioEvent;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Read-only adapter for the ZIP exported by the saved Studio project.
 * The editorial document remains scenevibe.multimedia-track draft.1; this class
 * adds no Cloud authority. All files are checked before a calendar is exposed.
 * Original TEXT scenes feed the existing M5 regie/SceneRenderer unchanged.
 */
final class StudioMultimediaPackage {
    /** Decode the actual MP3 bytes at the local native/software qualification boundary. */
    interface Decoder { long decodedDurationMs(byte[] bytes) throws Exception; }
    final String trackId;
    final long revision;
    final String trackSha256;
    final MediaCalendar calendar;
    final OverlayManifest originalManifest;
    final Map<String,AudioEvent> audio;
    private final Map<String,byte[]> resources;
    private final JSONObject document;

    /** Freeze validated values and privately own resource bytes. */
    private StudioMultimediaPackage(JSONObject document,String digest,MediaCalendar calendar,
            OverlayManifest manifest,Map<String,AudioEvent> audio,Map<String,byte[]> resources) throws Exception {
        this.document=document; this.trackId=document.getString("trackId"); this.revision=integer(document,"revision");
        this.trackSha256=digest; this.calendar=calendar; this.originalManifest=manifest;
        this.audio=Collections.unmodifiableMap(new LinkedHashMap<>(audio));
        this.resources=Collections.unmodifiableMap(resources);
    }

    /** Expose a defensive copy by stable resource id, never a URL or arbitrary path. */
    byte[] resource(String id) {
        byte[] bytes=resources.get(id);
        if(bytes==null) throw invalid("RESOURCE_MISSING");
        return bytes.clone();
    }

    /** UNKNOWN edition or mapping always refuses native MEDIA activation. */
    boolean hasReferencedIdentity() {
        JSONObject target=document.optJSONObject("target");
        JSONObject edition=target==null?null:target.optJSONObject("edition"),mapping=target==null?null:target.optJSONObject("mapping");
        return edition!=null && mapping!=null && "EVIDENCE_REFERENCED".equals(edition.optString("status"))
                && "EVIDENCE_REFERENCED".equals(mapping.optString("status"));
    }

    /** Parse a bounded package atomically; a partial AUDIO/TEXT subset is never returned. */
    static StudioMultimediaPackage read(byte[] zip,Decoder decoder) throws Exception {
        if(zip==null || zip.length>256*1024*1024 || decoder==null) throw invalid("PACKAGE_BUDGET_OR_DECODER_MISSING");
        Map<String,byte[]> files=unzip(zip);
        JSONObject manifest=json(required(files,"manifest.json",1_048_576));
        keys(manifest,"format","version","source","track","originalRuntime","originalManifest","files","mediaAdmissionAuthorized","cloudPublicationAuthorized");
        require("scenevibe.studio-tv-qualification".equals(manifest.getString("format")) && integer(manifest,"version")==1,"PACKAGE_FORMAT_UNSUPPORTED");
        require(!manifest.getBoolean("mediaAdmissionAuthorized") && !manifest.getBoolean("cloudPublicationAuthorized"),"PACKAGE_CANNOT_AUTHORIZE_MEDIA");
        byte[] trackBytes=verified(files,manifest.getJSONObject("track"),1_048_576);
        JSONObject track=json(trackBytes), source=manifest.getJSONObject("source");
        keys(track,"contract","schemaVersion","trackId","revision","title","editorialLanguage","target","synchronization","requirements","events","resources");
        require("scenevibe.multimedia-track".equals(track.getString("contract")) && "1.0.0-draft.1".equals(track.getString("schemaVersion")),"CONTRACT_UNSUPPORTED");
        require(integer(track,"revision")>=1 && integer(track,"revision")==integer(source,"revision"),"REVISION_MISMATCH");
        policies(track.getJSONObject("synchronization"));
        JSONObject requirements=track.getJSONObject("requirements");
        require("REJECT_WHOLE_TRACK".equals(requirements.getString("onUnsupported")) && requirements.getJSONArray("imageMimes").length()==0,"CAPABILITY_UNSUPPORTED");
        JSONObject runtime=json(verified(files,manifest.getJSONObject("originalRuntime"),400_000));
        OverlayManifest original=OverlayManifestParser.parse(json(verified(files,manifest.getJSONObject("originalManifest"),800_000)));
        JSONObject target=track.getJSONObject("target"), identity=runtime.getJSONObject("mediaIdentity");
        require(integer(target,"horizonMs")==integer(identity,"durationMs") && integer(target,"horizonMs")<=43_200_000L,"HORIZON_MISMATCH");
        require(target.getJSONObject("program").getString("programId").equals(identity.getString("videoId"))
                && target.getJSONObject("program").getString("title").equals(identity.getString("title"))
                && target.getJSONObject("platform").getString("id").equals(identity.getString("platform"))
                && target.getJSONObject("platform").getString("androidPackage").equals(runtime.getString("targetPackage")),"PROGRAM_IDENTITY_MISMATCH");
        require("com.amazon.amazonvideo.livingroom".equals(runtime.getString("targetPackage")) && runtime.getBoolean("pauseFreezesDisplay"),"SOURCE_PROFILE_UNSUPPORTED");
        Map<String,JSONObject> originals=new LinkedHashMap<>();
        JSONArray originalEvents=runtime.getJSONArray("comments");
        for(int i=0;i<originalEvents.length();i++) { JSONObject event=originalEvents.getJSONObject(i); require(originals.put(event.getString("id"),event)==null,"DUPLICATE_SOURCE_ID"); }
        require(original.scenes.size()==originals.size(),"SOURCE_MANIFEST_MISMATCH");
        for(OverlayManifest.Scene scene:original.scenes) {
            JSONObject event=originals.get(scene.id);
            require(event!=null && scene.startMs==integer(event,"startMs") && scene.durationMs==integer(event,"durationMs")
                    && sceneText(scene.elements).equals(event.getString("text")),"SOURCE_MANIFEST_MISMATCH");
        }
        JSONArray descriptions=manifest.getJSONArray("files"), resources=track.getJSONArray("resources");
        require(resources.length()==descriptions.length() && resources.length()<=256,"RESOURCE_MANIFEST_MISMATCH");
        Map<String,JSONObject> descriptors=new HashMap<>();
        Set<String> expectedFiles=new HashSet<>(); Collections.addAll(expectedFiles,"manifest.json",manifest.getJSONObject("track").getString("path"),manifest.getJSONObject("originalRuntime").getString("path"),manifest.getJSONObject("originalManifest").getString("path"));
        for(int i=0;i<descriptions.length();i++) {
            JSONObject row=descriptions.getJSONObject(i); require(descriptors.put(row.getString("resourceId"),row)==null,"DUPLICATE_RESOURCE_ID");
            require(expectedFiles.add(row.getString("path")),"DUPLICATE_RESOURCE_PATH");
        }
        require(expectedFiles.equals(files.keySet()),"UNEXPECTED_OR_MISSING_PACKAGE_FILE");
        Map<String,byte[]> payloads=new HashMap<>(); Map<String,Long> durations=new HashMap<>();
        for(int i=0;i<resources.length();i++) {
            JSONObject resource=resources.getJSONObject(i), row=descriptors.get(resource.getString("id"));
            require(row!=null && "AUDIO".equals(resource.getString("type")) && "PASSTHROUGH".equals(resource.getString("derivation")),"RESOURCE_TYPE_UNSUPPORTED");
            JSONObject originalPayload=resource.getJSONObject("original"), payload=resource.getJSONObject("distributed");
            require(samePayload(originalPayload,payload) && "audio/mpeg".equals(payload.getString("mime")) && "audio/mpeg".equals(row.getString("mime")),"MP3_PASSTHROUGH_REQUIRED");
            String id=resource.getString("id"), hash=payload.getString("sha256"); long duration=integer(payload,"durationMs");
            byte[] bytes=required(files,row.getString("path"),50*1024*1024);
            require(duration>0 && duration<=60_000 && duration==integer(row,"durationMs"),"AUDIO_DURATION_PROFILE_UNSUPPORTED");
            require(bytes.length==integer(row,"byteLength") && bytes.length==integer(payload,"byteLength")
                    && hash.equals(row.getString("sha256")) && hash.equals(sha256(bytes))
                    && payload.getString("storageRef").equals("svasset:"+hash),"RESOURCE_HASH_OR_SIZE_MISMATCH");
            require(decoder.decodedDurationMs(bytes.clone())==duration,"NATIVE_DECODE_DURATION_MISMATCH");
            require(payloads.put(id,bytes)==null,"DUPLICATE_RESOURCE_ID"); durations.put(id,duration);
        }
        JSONArray events=track.getJSONArray("events");
        require(events.length()>0 && events.length()<=256,"EVENT_BUDGET");
        List<SceneEvent> calendar=new ArrayList<>(); Map<String,AudioEvent> audio=new LinkedHashMap<>();
        Set<String> textIds=new HashSet<>(), eventIds=new HashSet<>(), used=new HashSet<>(), eventTypes=new HashSet<>();
        long previous=-1, audioEnd=-1;
        for(int i=0;i<events.length();i++) {
            JSONObject event=events.getJSONObject(i); String id=event.getString("id"), type=event.getString("type");
            long start=integer(event,"startMs"), duration=integer(event,"durationMs");
            require(eventIds.add(id) && start>=previous && duration>0 && duration<=60_000 && start<=integer(target,"horizonMs")-duration,"EVENT_ORDER_OR_WINDOW_INVALID"); previous=start; eventTypes.add(type);
            if("TEXT".equals(type)) {
                JSONObject expected=originals.get(id);
                require(expected!=null && integer(expected,"startMs")==start && integer(expected,"durationMs")==duration
                        && expected.getString("text").equals(event.getJSONObject("content").getString("text")),"ORIGINAL_TEXT_CHANGED");
                textIds.add(id); calendar.add(new SceneEvent(id,start,duration));
            } else if("AUDIO".equals(type)) {
                String resourceId=event.getString("resourceId"); double gain=event.getJSONObject("presentation").getDouble("gain");
                require(durations.containsKey(resourceId) && durations.get(resourceId)==duration && start>=audioEnd
                        && Double.isFinite(gain) && gain>=0 && gain<=1,"AUDIO_RESOURCE_OR_OVERLAP_INVALID"); audioEnd=start+duration; used.add(resourceId);
                AudioEvent value=new AudioEvent(id,AudioEvent.Type.AUDIO,resourceId,start,duration,(float)gain,AudioEvent.PausePolicy.FREEZE_AND_RESUME);
                audio.put(id,value); calendar.add(new SceneEvent(id,start,duration));
            } else throw invalid("EVENT_TYPE_UNSUPPORTED");
        }
        require(textIds.equals(originals.keySet()) && used.equals(payloads.keySet()),"INCOMPLETE_TEXT_OR_AUDIO");
        require(stringSet(requirements.getJSONArray("eventTypes")).equals(eventTypes)
                && stringSet(requirements.getJSONArray("audioMimes")).equals(audio.isEmpty()?Collections.emptySet():Collections.singleton("audio/mpeg")),"REQUIREMENT_MISMATCH");
        return new StudioMultimediaPackage(track,sha256(trackBytes),new MediaCalendar(calendar,true),original,audio,payloads);
    }

    /** Validate the exact supported profile; no STOP_ON_PAUSE substitution is allowed. */
    private static void policies(JSONObject policy) throws Exception {
        String[] names={"clock","pause","seek","textAdmission","audioSeek","unknownPosition","eligibilityLoss","lateCallback","advertisements","audioOverlap","visualOverlap"};
        String[] values={"MEDIA_POSITION_MS","FREEZE_AND_RESUME","TYPE_AWARE_MEDIA_RECONCILIATION","M5_QUALIFIED_NO_LATE_REPLAY","RESUME_NATIVE_OFFSET_INSIDE_WINDOW","STOP_ALL","STOP_ALL","REJECT_OLD_ACTIVATION","UNQUALIFIED_STOP","REJECT","REJECT"};
        keys(policy,names); for(int i=0;i<names.length;i++) require(values[i].equals(policy.getString(names[i])),"PLAYBACK_POLICY_UNSUPPORTED");
    }

    /** Flatten only TEXT/groups from the already validated original scene. */
    private static String sceneText(List<OverlayManifest.Element> elements) {
        StringBuilder text=new StringBuilder();
        for(OverlayManifest.Element element:elements) {
            if(element.type==OverlayManifest.PrimitiveType.TEXT) text.append(element.text);
            else if(element.type==OverlayManifest.PrimitiveType.GROUP) text.append(sceneText(element.children));
        }
        return text.toString();
    }

    /** Read bounded ZIP entries without extracting paths onto a filesystem. */
    private static Map<String,byte[]> unzip(byte[] bytes) throws IOException {
        Map<String,byte[]> files=new LinkedHashMap<>(); long total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry entry; byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                String path=entry.getName();
                require(!entry.isDirectory() && !path.startsWith("/") && !path.contains("..") && !path.contains("\\") && files.size()<260 && !files.containsKey(path),"ZIP_PATH_OR_ENTRY_BUDGET");
                ByteArrayOutputStream output=new ByteArrayOutputStream(); int count;
                while((count=zip.read(buffer))!=-1) { total+=count; require(total<=250L*1024*1024 && output.size()+count<=50*1024*1024,"ZIP_BYTE_BUDGET"); output.write(buffer,0,count); }
                files.put(path,output.toByteArray()); zip.closeEntry();
            }
        }
        return files;
    }

    /** Retrieve a required bounded object before parsing or decoding it. */
    private static byte[] required(Map<String,byte[]> files,String path,int maximum) {
        byte[] bytes=files.get(path); require(bytes!=null && bytes.length>0 && bytes.length<=maximum,"FILE_MISSING_OR_BUDGET"); return bytes;
    }

    /** Compare a manifest identity against the actual package bytes. */
    private static byte[] verified(Map<String,byte[]> files,JSONObject descriptor,int maximum) throws Exception {
        byte[] bytes=required(files,descriptor.getString("path"),maximum);
        require(sha256(bytes).equals(descriptor.getString("sha256")),"DOCUMENT_HASH_MISMATCH"); return bytes;
    }

    /** Decode strict UTF-8 through a JSON object parser. */
    private static JSONObject json(byte[] bytes) throws Exception { return new JSONObject(new String(bytes,StandardCharsets.UTF_8)); }

    /** Hash the exact stored bytes with the platform SHA-256 implementation. */
    static String sha256(byte[] bytes) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder result=new StringBuilder();
        for(byte value:hash) result.append(String.format(java.util.Locale.ROOT,"%02x",value&255)); return result.toString();
    }

    /** Reject duplicate capability declarations rather than normalizing them. */
    private static Set<String> stringSet(JSONArray array) throws Exception {
        Set<String> result=new HashSet<>(); for(int i=0;i<array.length();i++) require(result.add(array.getString(i)),"DUPLICATE_REQUIREMENT"); return result;
    }

    /** Refuse unrecognized keys at the container and policy boundaries. */
    private static void keys(JSONObject object,String... names) {
        Set<String> expected=new HashSet<>(),actual=new HashSet<>(); Collections.addAll(expected,names);
        java.util.Iterator<String> iterator=object.keys();while(iterator.hasNext()) actual.add(iterator.next());
        require(expected.equals(actual),"UNEXPECTED_DOCUMENT_FIELD");
    }

    /** Compare bounded passthrough metadata with APIs present on Android's org.json implementation. */
    private static boolean samePayload(JSONObject original,JSONObject distributed) {
        String[] fields={"storageRef","mime","byteLength","sha256","durationMs","bitrateKbps"};
        keys(original,fields);keys(distributed,fields);
        for(String field:fields) if(!java.util.Objects.equals(original.opt(field),distributed.opt(field))) return false;
        return true;
    }

    /** Convert a failed condition into a bounded diagnostic with no private content. */
    private static void require(boolean valid,String code) { if(!valid) throw invalid(code); }

    /** Reject fractional/coerced JSON numbers rather than silently truncating timecodes. */
    private static long integer(JSONObject object,String key) {
        Object value=object.opt(key);
        require(value instanceof Number,"EXACT_INTEGER_REQUIRED");
        double number=((Number)value).doubleValue();
        require(Double.isFinite(number) && number==Math.rint(number) && Math.abs(number)<=9_007_199_254_740_991d,"EXACT_INTEGER_REQUIRED");
        return ((Number)value).longValue();
    }

    /** Construct a content-free qualification failure. */
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
