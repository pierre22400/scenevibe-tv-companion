package com.scenevibe.tvcompanionpoc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Transport Android/JVM sortant vers une seule origine HTTPS port 443. Aucun
 * redirect, cookie, trust override, SDK Neon/R2 ou mode HTTP de production.
 * Le banc local remplace uniquement ce port réseau par un vrai proxy loopback.
 */
final class MultimediaHttpsPort implements MultimediaCloudClient.Port {
    private final String origin;
    private final javax.net.ssl.SSLSocketFactory qualificationTrust;
    /** Pin an exact configured HTTPS origin before opening any socket. */
    MultimediaHttpsPort(String origin) {
        this(origin,null);
    }
    /** Trust a specific local CA only in debuggable instrumentation builds; hostname verification stays native. */
    MultimediaHttpsPort(String origin,javax.net.ssl.SSLSocketFactory qualificationTrust) {
        if(qualificationTrust!=null && !BuildConfig.DEBUG)throw new IllegalArgumentException("DEBUG_QUALIFICATION_ONLY");
        boolean localQualification=qualificationTrust!=null && ("https://localhost:8443".equals(origin) || "https://127.0.0.1:8443".equals(origin));
        if(!localQualification && (origin==null || !origin.matches("https://[A-Za-z0-9.-]+") || URI.create(origin).getHost()==null))throw new IllegalArgumentException("CLOUD_UNCONFIGURED");
        this.origin=origin;this.qualificationTrust=qualificationTrust;
    }
    /** Open one fixed private API request and transfer ownership of connection cleanup to Reply. */
    @Override public MultimediaCloudClient.Reply open(String method,String path,String token,byte[] body) throws Exception {
        if(!path.matches("/api/v1/devices/[a-f0-9-]{36}/multimedia/[a-z]+\\?.*") || path.contains("..") || path.contains("\\"))throw new IllegalArgumentException("CLOSED_ROUTE_REQUIRED");
        HttpsURLConnection connection=(HttpsURLConnection)URI.create(origin+path).toURL().openConnection();
        try {
            if(qualificationTrust!=null)connection.setSSLSocketFactory(qualificationTrust);
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15_000);connection.setReadTimeout(60_000);connection.setRequestMethod(method);
            connection.setRequestProperty("Accept","application/json, application/vnd.scenevibe.multimedia+json, audio/mpeg");connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null) {
                if(body.length>16_384)throw new IllegalArgumentException("REQUEST_BUDGET");
                connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);
                try(java.io.OutputStream stream=connection.getOutputStream()){stream.write(body);}
            }
            int status=connection.getResponseCode();
            return new MultimediaCloudClient.Reply(status,connection.getContentType(),connection.getContentLengthLong(),status==200?connection.getInputStream():null,connection::disconnect);
        } catch(Exception failure) {connection.disconnect();throw failure;}
    }
}
