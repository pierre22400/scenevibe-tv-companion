package com.scenevibe.tvcompanionpoc;

import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Frontière entre HTTPS produit et qualification Android locale. Le constructeur
 * standard ne peut accepter aucun port alternatif ; une CA de test n'autorise
 * que loopback:8443 dans un build debuggable, jamais une origine externe.
 */
public final class MultimediaTransportOriginTest {
    /** The normal product constructor remains pinned to HTTPS on implicit port 443. */
    @Test public void productConstructorRefusesAlternativePorts() {
        for(String origin:new String[]{"https://localhost:8443","https://example.org:443","http://localhost","https://example.org/path",null}) {
            try{new MultimediaHttpsPort(origin);fail("Unsafe product origin accepted");}catch(IllegalArgumentException expected){assertEquals("CLOUD_UNCONFIGURED",expected.getMessage());}
        }
    }
    /** Debug qualification retains a strict loopback-only high-port exception with an explicit trust store. */
    @Test public void debugQualificationAcceptsOnlyLoopbackHighPort() {
        assertTrue(BuildConfig.DEBUG);
        assertNotNull(new MultimediaHttpsPort("https://localhost:8443",(SSLSocketFactory)SSLSocketFactory.getDefault()));
        assertNotNull(new MultimediaHttpsPort("https://127.0.0.1:8443",(SSLSocketFactory)SSLSocketFactory.getDefault()));
        for(String origin:new String[]{"https://example.org:8443","https://localhost:8444","http://localhost:8443"}) {
            try{new MultimediaHttpsPort(origin,(SSLSocketFactory)SSLSocketFactory.getDefault());fail("Qualification escaped loopback");}
            catch(IllegalArgumentException expected){assertEquals("CLOUD_UNCONFIGURED",expected.getMessage());}
        }
    }
    /** Outbound path validation still rejects foreign URLs before any bearer or socket is used. */
    @Test public void foreignRouteIsRejectedBeforeNetwork() throws Exception {
        var port=new MultimediaHttpsPort("https://qualification.example");
        try{port.open("GET","https://foreign.example/asset","synthetic",null);fail("Foreign route accepted");}
        catch(IllegalArgumentException expected){assertEquals("CLOSED_ROUTE_REQUIRED",expected.getMessage());}
    }
}
