package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;

/** Testable transport seam; production binds only the strict HTTPS implementation. */
interface TvFirst008HttpPort {
    /** Perform one explicit request without implicit retries, identity reset or service action. */
    JSONObject request(String method, String path, String token, JSONObject body) throws Exception;
}
