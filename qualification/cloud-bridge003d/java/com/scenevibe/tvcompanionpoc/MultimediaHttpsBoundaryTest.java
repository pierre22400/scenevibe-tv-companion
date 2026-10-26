package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Frontières du transport de production, sans socket : seule une origine HTTPS
 * exacte est constructible et les chemins étrangers sont refusés avant réseau.
 * La qualification TLS Android effective reste une vérification ultérieure.
 */
public final class MultimediaHttpsBoundaryTest {
    /** Refuse loopback HTTP, ports, userinfo and path-bearing origins in production. */
    @Test public void unsafeOriginsAreRejected() {
        for(String origin:new String[]{"http://127.0.0.1:1234","https://host.example:443","https://user@host.example","https://host.example/path","https://host.example?x=1",""}) {
            try {new MultimediaHttpsPort(origin);fail(origin);}catch(IllegalArgumentException expected){assertEquals("CLOUD_UNCONFIGURED",expected.getMessage());}
        }
    }
    /** A valid configured origin performs no work at construction. */
    @Test public void exactHttpsOriginIsInertAtConstruction(){assertNotNull(new MultimediaHttpsPort("https://qualification.example"));}
    /** Arbitrary paths cannot turn the pinned transport into a generic proxy. */
    @Test public void foreignPathsFailBeforeAnyConnection() throws Exception {
        var port=new MultimediaHttpsPort("https://qualification.example");
        for(String path:new String[]{"https://foreign.example/file","/api/v1/finaltracks","/api/v1/devices/00000000-0000-4000-8000-000000000001/multimedia/../resource?x=1"}) {
            try {port.open("GET",path,"synthetic",null);fail(path);}catch(IllegalArgumentException expected){assertEquals("CLOSED_ROUTE_REQUIRED",expected.getMessage());}
        }
    }
}
