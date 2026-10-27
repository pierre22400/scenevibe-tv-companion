package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.json.JSONObject;

/**
 * Consommateur du vrai profil privé 003-D : le credential reste celui de
 * l'association TV-FIRST. Chaque requête et morceau reçu vérifie la durée de
 * vie locale ; le serveur revérifie la délégation et l'affectation courante.
 * Aucun retry d'affectation n'invente une nouvelle intention après une panne.
 */
final class MultimediaCloudClient {
    /** Current generation lease supplied by the existing encrypted TV-FIRST association. */
    interface Session {
        /** Return the stable associated TV id, never an account selected by a client. */
        String deviceId();
        /** Return the captured association generation. */
        String generation();
        /** Read the existing encrypted credential only for the transport request. */
        String token();
        /** Refuse a cancelled, expired, replaced or locally disassociated lifetime. */
        boolean current();
    }
    /** Bounded byte transport: production injects HTTPS; tests inject real loopback HTTP. */
    interface Port {
        /** Open one request without redirection or alternate-origin discovery. */
        Reply open(String method,String path,String token,byte[] body) throws Exception;
    }
    /** Own one response stream so cancellation always closes the actual connection. */
    static final class Reply implements AutoCloseable {
        final int status;final String mime;final long length;final InputStream input;private final Runnable close;
        /** Retain only the status/metadata/stream, never a request credential. */
        Reply(int status,String mime,long length,InputStream input,Runnable close) {this.status=status;this.mime=mime;this.length=length;this.input=input;this.close=close;}
        /** Release input and connection even when size, authorization or integrity fails. */
        @Override public void close() throws Exception {try {if(input!=null)input.close();}finally{close.run();}}
    }
    /** Immutable full cache coordinates; editorial revision is never used as device revision. */
    static final class Binding {
        final String device,generation,intention,publication,sha256;final long revision;private final String encoded;
        /** Validate the finite exact wire tuple before any disk or MP3 work. */
        Binding(JSONObject data) throws Exception {
            MultimediaDeliveryProfile.closed(data,"type","profile","deviceId","generation","intentionId","publicationId","revision","codecId","codecVersion","packageSha256");
            MultimediaDeliveryProfile.require("scenevibe.cloud.multimedia-assignment.v1".equals(data.get("type"))
                    && MultimediaDeliveryProfile.PROFILE.equals(data.get("profile")) && MultimediaDeliveryProfile.CODEC.equals(data.get("codecId"))
                    && MultimediaDeliveryProfile.VERSION.equals(data.get("codecVersion")),"ASSIGNMENT_PROFILE_REFUSED");
            device=TvFirst008Wire.uuid(data.getString("deviceId"));generation=TvFirst008Wire.uuid(data.getString("generation"));
            intention=TvFirst008Wire.uuid(data.getString("intentionId"));publication=TvFirst008Wire.uuid(data.getString("publicationId"));
            sha256=MultimediaDeliveryProfile.digest(data.get("packageSha256"));revision=MultimediaDeliveryProfile.integer(data,"revision",1,9_007_199_254_740_991L);encoded=data.toString();
        }
        /** Return a defensive JSON value for an exact cache receipt. */
        JSONObject json() throws Exception {return new JSONObject(encoded);}
        /** Compare every immutable authority coordinate, not a title or just a revision. */
        boolean same(Binding other) {return other!=null && revision==other.revision && device.equals(other.device) && generation.equals(other.generation)
                && intention.equals(other.intention) && publication.equals(other.publication) && sha256.equals(other.sha256);}
    }

    private final Session session;private final Port port;
    /** Capture explicit collaborators only; construction performs no HTTP or secret read. */
    MultimediaCloudClient(Session session,Port port) {if(session==null||port==null)throw new IllegalArgumentException("MISSING_TRANSPORT_PORT");this.session=session;this.port=port;}
    /** Check cancellation and the current association before every effect. */
    void check() {MultimediaDeliveryProfile.require(session.current(),"DOWNLOAD_CANCELLED_OR_STALE_GENERATION");}
    /** Construct only fixed same-origin paths and encoded query values. */
    private String path(String action,Binding binding,String resource) throws Exception {
        check();String result="/api/v1/devices/"+TvFirst008Wire.uuid(session.deviceId())+"/multimedia/"+action+"?profile="+MultimediaDeliveryProfile.PROFILE;
        result+="&generation="+URLEncoder.encode(session.generation(),"UTF-8");
        if(binding!=null)result+="&intentionId="+URLEncoder.encode(binding.intention,"UTF-8");
        if(resource!=null)result+="&resourceId="+URLEncoder.encode(resource,"UTF-8");return result;
    }
    /** Read one exact MIME/size-bounded response and preserve raw bytes for digest verification. */
    private byte[] request(String method,String action,Binding binding,String resource,byte[] body,String mime,int maximum,long expected) throws Exception {
        check();
        try(Reply reply=port.open(method,path(action,binding,resource),session.token(),body)) {
            MultimediaDeliveryProfile.require(reply.status==200,"MULTIMEDIA_HTTP_REFUSED");
            String media=reply.mime==null?"":reply.mime.split(";",2)[0].trim();
            MultimediaDeliveryProfile.require(mime.equals(media) && reply.length>=0 && reply.length<=maximum && (expected<0||reply.length==expected),"HTTP_MIME_OR_SIZE_REFUSED");
            try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=reply.input.read(buffer))!=-1) {check();MultimediaDeliveryProfile.require(bytes.size()+count<=maximum,"HTTP_BYTE_BUDGET");bytes.write(buffer,0,count);}
                check();MultimediaDeliveryProfile.require(bytes.size()==reply.length && (expected<0||bytes.size()==expected),"HTTP_TRUNCATED");return bytes.toByteArray();
            }
        }
    }
    /** Discover a bounded owner-authorized private catalogue, without granting rendering. */
    JSONObject publications() throws Exception {return new JSONObject(TvFirst008Wire.utf8(request("GET","publications",null,null,null,"application/json",200_000,-1)));}
    /** Read the actual OS revision rather than assuming continuity with a legacy SHADOW counter. */
    long currentRevision() throws Exception {
        JSONObject current=new JSONObject(TvFirst008Wire.utf8(request("GET","association",null,null,null,"application/json",16_384,-1)));
        MultimediaDeliveryProfile.closed(current,"profile","deviceId","generation","currentRevision","mediaAdmissionAuthorized");
        MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.PROFILE.equals(current.get("profile")) && session.deviceId().equals(current.get("deviceId"))
                && session.generation().equals(current.get("generation")) && Boolean.FALSE.equals(current.get("mediaAdmissionAuthorized")),"ASSOCIATION_BINDING_MISMATCH");
        return MultimediaDeliveryProfile.integer(current,"currentRevision",0,9_007_199_254_740_991L);
    }
    /** Reuse a caller-persisted confirmed intention and exact expected OS revision. */
    Binding select(JSONObject command) throws Exception {
        MultimediaDeliveryProfile.closed(command,"profile","generation","intentionId","publicationId","expectedRevision","confirmed");
        MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.PROFILE.equals(command.get("profile")) && session.generation().equals(command.get("generation"))
                && Boolean.TRUE.equals(command.get("confirmed")),"INTENTION_CONFIRMATION_REQUIRED");
        Binding binding=new Binding(new JSONObject(TvFirst008Wire.utf8(request("POST","intentions",null,null,command.toString().getBytes(StandardCharsets.UTF_8),"application/json",16_384,-1))));
        MultimediaDeliveryProfile.require(binding.device.equals(session.deviceId()) && binding.generation.equals(session.generation())
                && binding.intention.equals(command.get("intentionId")) && binding.publication.equals(command.get("publicationId")),"INTENTION_BINDING_MISMATCH");return binding;
    }
    /** Obtain the actual sealed bytes and their resource manifest through the authenticated API. */
    MultimediaDeliveryProfile profile(Binding binding) throws Exception {
        verify(binding);
        byte[] data=request("GET","package",binding,null,null,"application/vnd.scenevibe.multimedia+json",MultimediaDeliveryProfile.MAX_PROFILE,-1);
        MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.hash(data).equals(binding.sha256),"PACKAGE_INTEGRITY");
        MultimediaDeliveryProfile profile=new MultimediaDeliveryProfile(data);
        byte[] manifest=request("GET","manifest",binding,null,null,"application/json",MultimediaDeliveryProfile.MAX_PROFILE,profile.resourceManifestBytes.length);
        MultimediaDeliveryProfile.require(java.util.Arrays.equals(manifest,profile.resourceManifestBytes),"RESOURCE_MANIFEST_INTEGRITY");return profile;
    }
    /** Download one logical MP3; callers deduplicate only after the bytes prove identical. */
    byte[] resource(Binding binding,MultimediaDeliveryProfile.Resource resource) throws Exception {
        byte[] bytes=request("GET","resource",binding,resource.id,null,"audio/mpeg",MultimediaDeliveryProfile.MAX_AUDIO,resource.byteLength);
        MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.hash(bytes).equals(resource.sha256),"MP3_INTEGRITY");return bytes;
    }
    /** Recheck the complete current server tuple immediately around cache publication. */
    void verify(Binding binding) throws Exception {
        check();MultimediaDeliveryProfile.require(binding.device.equals(session.deviceId()) && binding.generation.equals(session.generation()),"STALE_GENERATION");
        Binding current=new Binding(new JSONObject(TvFirst008Wire.utf8(request("GET","validate",binding,null,null,"application/json",16_384,-1))));
        MultimediaDeliveryProfile.require(binding.same(current),"STALE_ASSIGNMENT");
    }
    /** Send cache evidence only after commit/readback; never call a historical ACK endpoint. */
    void receipt(Binding binding) throws Exception {
        JSONObject reply=new JSONObject(TvFirst008Wire.utf8(request("POST","receipt",binding,null,binding.json().toString().getBytes(StandardCharsets.UTF_8),"application/json",16_384,-1)));
        MultimediaDeliveryProfile.closed(reply,"binding","status","mediaAdmissionAuthorized","installationAcknowledged");
        MultimediaDeliveryProfile.require("CACHE_CONFIRMED".equals(reply.get("status")) && Boolean.FALSE.equals(reply.get("mediaAdmissionAuthorized"))
                && Boolean.FALSE.equals(reply.get("installationAcknowledged")) && binding.same(new Binding(reply.getJSONObject("binding"))),"CACHE_RECEIPT_REFUSED");
    }
}
