package com.scenevibe.tvcompanionpoc;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

/** Share one process-global URL factory while keeping both historical and new HTTP tests socket-free. */
final class M4PhaseFHttps {
    /** A registered fixture alone may provide a synthetic connection for its reserved host. */
    interface ConnectionFactory {
        /** Open only the thread's active deterministic exchange; unexpected requests must fail closed. */
        URLConnection open(URL url) throws IOException;
    }
    private static final Map<String,ConnectionFactory> HOSTS=new HashMap<>();
    private static boolean installed;
    /** Install once and register only the two explicit reserved test hosts, regardless of suite order. */
    static synchronized void register(String host,ConnectionFactory factory) {
        if(!"m4-characterization.invalid".equals(host)&&!"m4-phase-f.invalid".equals(host))
            throw new IllegalArgumentException("Unexpected fixture host");
        HOSTS.put(host,factory);
        if(installed)return;
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler() {
            /** Intercept every HTTPS connection and forbid unregistered hosts; no socket fallback exists. */
            @Override protected URLConnection openConnection(URL url) throws IOException {
                ConnectionFactory selected;
                synchronized(M4PhaseFHttps.class) {selected=HOSTS.get(url.getHost());}
                if(selected==null)throw new IOException("Unexpected test transport target");
                return selected.open(url);
            }
        }:null);
        installed=true;
    }
    /** The factory is test-only and cannot be instantiated. */
    private M4PhaseFHttps() {}
}
