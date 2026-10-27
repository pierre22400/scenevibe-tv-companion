package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * CLOUD BRIDGE 003-D : vérifie un profil fermé et ses documents originaux avant
 * toute écriture. Les trois identités AUDIO restent distinctes d'un éventuel
 * unique fichier physique. L'adaptation interne vers le lecteur de paquet
 * Studio réutilise ses validations et ne fabrique aucune admission MEDIA.
 */
final class MultimediaDeliveryProfile {
    static final String CODEC="scenevibe.multimedia.delivery.v1", VERSION="1.0.0", PROFILE="tvfirst-multimedia-cache-v1";
    static final int MAX_PROFILE=1_048_576, MAX_AUDIO=50*1024*1024;
    final byte[] encoded,trackBytes,runtimeBytes,manifestBytes,resourceManifestBytes;
    final JSONObject identity;
    final List<Resource> resources;
    final String packageSha256;

    /** Immutable bounded logical audio descriptor, without a path, URL or server credential. */
    static final class Resource {
        final String id,sha256;final int byteLength;final long durationMs;
        /** Validate all fields before a logical descriptor can enter a downloader. */
        Resource(JSONObject row) throws Exception {
            closed(row,"resourceId","sha256","byteLength","mime","durationMs");
            id=identifier(row.get("resourceId"));sha256=digest(row.get("sha256"));
            byteLength=(int)integer(row,"byteLength",1,MAX_AUDIO);durationMs=integer(row,"durationMs",1,60_000);
            require("audio/mpeg".equals(row.get("mime")),"MP3_MIME_REQUIRED");
        }
    }

    /** Parse bounded strict UTF-8, refusing extra fields and all implied rendering authority. */
    MultimediaDeliveryProfile(byte[] data) throws Exception {
        require(data!=null && data.length>0 && data.length<=MAX_PROFILE,"PROFILE_BUDGET");encoded=data.clone();packageSha256=hash(encoded);
        JSONObject object=new JSONObject(TvFirst008Wire.utf8(encoded));
        closed(object,"type","version","identity","preparationId","preparedPackageSha256","decisionId","trackBase64","runtimeBase64","runtimeSha256","manifestBase64","manifestSha256","resourceManifestBase64","mediaAdmissionAuthorized");
        require(CODEC.equals(object.get("type")) && VERSION.equals(object.get("version")) && Boolean.FALSE.equals(object.get("mediaAdmissionAuthorized")),"PROFILE_OR_AUTHORITY_REFUSED");
        identity=object.getJSONObject("identity");closed(identity,"accountId","trackId","editorialRevision","snapshotSha256","resourceManifestSha256","profileVersion");
        identifier(identity.get("accountId"));identifier(identity.get("trackId"));integer(identity,"editorialRevision",1,9_007_199_254_740_991L);
        require(VERSION.equals(identity.get("profileVersion")),"PROFILE_VERSION_REFUSED");
        identifier(object.get("preparationId"));identifier(object.get("decisionId"));digest(object.get("preparedPackageSha256"));
        trackBytes=base64(object,"trackBase64",MAX_PROFILE);runtimeBytes=base64(object,"runtimeBase64",400_000);manifestBytes=base64(object,"manifestBase64",800_000);
        require(hash(trackBytes).equals(digest(identity.get("snapshotSha256"))) && hash(runtimeBytes).equals(digest(object.get("runtimeSha256")))
                && hash(manifestBytes).equals(digest(object.get("manifestSha256"))),"SOURCE_DOCUMENT_INTEGRITY");
        JSONObject track=new JSONObject(TvFirst008Wire.utf8(trackBytes));
        require(identity.get("trackId").equals(track.get("trackId")) && integer(identity,"editorialRevision",1,Long.MAX_VALUE)==integer(track,"revision",1,Long.MAX_VALUE),"EDITORIAL_IDENTITY_MISMATCH");
        resourceManifestBytes=base64(object,"resourceManifestBase64",MAX_PROFILE);
        require(hash(resourceManifestBytes).equals(digest(identity.get("resourceManifestSha256"))),"RESOURCE_MANIFEST_INTEGRITY");
        JSONArray rows=new JSONArray(TvFirst008Wire.utf8(resourceManifestBytes));require(rows.length()>0 && rows.length()<=256,"RESOURCE_BUDGET");
        List<Resource> checked=new ArrayList<>();Set<String> ids=new HashSet<>();long total=0;String previous="";
        for(int index=0;index<rows.length();index++) {
            Resource row=new Resource(rows.getJSONObject(index));require(ids.add(row.id) && row.id.compareTo(previous)>0,"RESOURCE_ORDER_OR_DUPLICATE");previous=row.id;
            total+=row.byteLength;require(total<=250L*1024*1024,"RESOURCE_BYTE_BUDGET");checked.add(row);
        }
        resources=Collections.unmodifiableList(checked);
    }

    /** Reuse the exact Studio parser and native-inspection seam after every resource is verified. */
    StudioMultimediaPackage prepare(Map<String,byte[]> physical,StudioMultimediaPackage.Decoder decoder) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            JSONObject manifest=new JSONObject().put("format","scenevibe.studio-tv-qualification").put("version",1)
                    .put("source",new JSONObject().put("revision",identity.get("editorialRevision")))
                    .put("track",descriptor("track.json",trackBytes)).put("originalRuntime",descriptor("source/runtime.json",runtimeBytes))
                    .put("originalManifest",descriptor("source/overlay-manifest.json",manifestBytes))
                    .put("mediaAdmissionAuthorized",false).put("cloudPublicationAuthorized",false);
            JSONArray files=new JSONArray();
            for(int index=0;index<resources.size();index++) {
                Resource row=resources.get(index);byte[] data=physical.get(row.sha256);
                require(data!=null && data.length==row.byteLength && hash(data).equals(row.sha256),"MP3_INTEGRITY");
                String path="resources/"+index+".mp3";
                files.put(new JSONObject().put("resourceId",row.id).put("path",path).put("mime","audio/mpeg")
                        .put("sha256",row.sha256).put("byteLength",row.byteLength).put("durationMs",row.durationMs));
                entry(zip,path,data);
            }
            manifest.put("files",files);entry(zip,"manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
            entry(zip,"track.json",trackBytes);entry(zip,"source/runtime.json",runtimeBytes);entry(zip,"source/overlay-manifest.json",manifestBytes);
        }
        return StudioMultimediaPackage.read(output.toByteArray(),decoder);
    }

    /** Bind one exact original document without reserializing its editorial content. */
    private static JSONObject descriptor(String path,byte[] data) throws Exception {return new JSONObject().put("path",path).put("sha256",hash(data));}
    /** Write one fixed adapter-owned ZIP entry; incoming paths never reach the filesystem. */
    private static void entry(ZipOutputStream zip,String path,byte[] data) throws Exception {zip.putNextEntry(new ZipEntry(path));zip.write(data);zip.closeEntry();}
    /** Decode bounded canonical base64 without alternate spellings or malformed allocation sizes. */
    private static byte[] base64(JSONObject object,String key,int maximum) throws Exception {
        Object value=object.get(key);require(value instanceof String && ((String)value).length()<=((maximum+2L)/3)*4,"BASE64_BUDGET");
        byte[] bytes=Base64.getDecoder().decode((String)value);require(bytes.length>0 && bytes.length<=maximum && Base64.getEncoder().encodeToString(bytes).equals(value),"BASE64_INVALID");return bytes;
    }
    /** Use the existing exact-byte SHA-256 implementation. */
    static String hash(byte[] bytes) throws Exception {return StudioMultimediaPackage.sha256(bytes);}
    /** Reject ambiguous numeric strings, fractional values and unsafe integers. */
    static long integer(JSONObject object,String key,long min,long max) throws Exception {
        Object value=object.get(key);require(value instanceof Number,"EXACT_INTEGER_REQUIRED");double number=((Number)value).doubleValue();
        require(Double.isFinite(number) && number==Math.rint(number) && number>=min && number<=max && number<=9_007_199_254_740_991d,"EXACT_INTEGER_REQUIRED");return ((Number)value).longValue();
    }
    /** Require an exact bounded logical id, never a path or URL. */
    static String identifier(Object value) {require(value instanceof String && ((String)value).matches("[A-Za-z0-9._:-]{1,512}"),"IDENTIFIER_INVALID");return (String)value;}
    /** Validate the SHA-256 alphabet and length before using it as a content-addressed filename. */
    static String digest(Object value) {require(value instanceof String && ((String)value).matches("[a-f0-9]{64}"),"DIGEST_INVALID");return (String)value;}
    /** Reject every missing and undeclared JSON field. */
    static void closed(JSONObject object,String... fields) {
        require(object!=null && object.length()==fields.length,"CLOSED_CONTRACT_REQUIRED");for(String field:fields)require(object.has(field),"CLOSED_CONTRACT_REQUIRED");
    }
    /** Throw a content-free refusal that cannot expose a credential or raw payload. */
    static void require(boolean value,String code) {if(!value)throw new IllegalArgumentException(code);}
}
