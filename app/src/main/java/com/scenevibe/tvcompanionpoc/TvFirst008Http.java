package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Bounded outbound HTTPS only, to one build-selected origin. Redirects and alternate
 * hosts are refused. No cookie, account credential, trust override, logging or fallback.
 */
final class TvFirst008Http implements TvFirst008HttpPort {
    private final String origin;
    /** Validate the exact port-443 origin before opening a connection. Empty means unavailable. */
    TvFirst008Http(String origin) {
        if (origin == null || !origin.matches("https://[A-Za-z0-9.-]+") || URI.create(origin).getHost() == null)
            throw new IllegalArgumentException("CLOUD_UNCONFIGURED");
        this.origin = origin;
    }
    /** Open one bounded request; only explicit cache-profile routes are accepted. */
    @Override public JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        if (!path.startsWith("/api/v1/") || path.contains("..") || !path.contains("profile=" + TvFirst008Wire.PROFILE)) throw TvFirst008Wire.invalid();
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(origin + path).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(8_000); connection.setReadTimeout(8_000);
            connection.setRequestMethod(method); connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 16_384) throw TvFirst008Wire.invalid();
                connection.setRequestProperty("Content-Type", "application/json"); connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new Failure(status);
            int declared = connection.getContentLength();
            if (declared > 2_000_000) throw TvFirst008Wire.invalid();
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > 2_000_000) throw TvFirst008Wire.invalid();
                    output.write(buffer, 0, count);
                }
                return new JSONObject(TvFirst008Wire.utf8(output.toByteArray()));
            }
        } finally { connection.disconnect(); }
    }
    /** Closed status class contains no response payload, URL, secret or identity. */
    static final class Failure extends Exception {
        final int status;
        /** Retain only a numeric HTTP status for bounded user-safe handling. */
        Failure(int status) { super("CLOUD_REQUEST_REFUSED"); this.status = status; }
    }
}
