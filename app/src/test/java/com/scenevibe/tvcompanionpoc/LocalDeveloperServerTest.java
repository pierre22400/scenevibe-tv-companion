package com.scenevibe.tvcompanionpoc;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real local sockets prove denial, opt-in lifetime and replay protection; no Android or Cloud authority is fabricated. */
public final class LocalDeveloperServerTest {
    private LocalDeveloperServer server;private final AtomicInteger effects=new AtomicInteger();private final AtomicLong time=new AtomicLong(1000);
    /** Create an explicitly owned recording command endpoint with a deterministic expiry clock. */
    @Before public void before() throws Exception{server=new LocalDeveloperServer((command,value)->{effects.incrementAndGet();return new JSONObject().put("ok",true);},name->"own page".getBytes(java.nio.charset.StandardCharsets.UTF_8),time::get);server.start();}
    /** Revoke only this test listener; no other application, preference or data is modified. */
    @After public void after(){server.close();}
    /** Perform the same one-use pairing as the bundled phone page. */
    private String pair() throws Exception{return LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",LocalControlTestClient.code(server.accessInfo())),null).json().getString("token");}
    /** Fixed assets are served same-origin with embedding/CSP protection; arbitrary paths stay closed. */
    @Test public void assetsAreFixedAndCannotBeUsedAsAnArbitraryFileServer() throws Exception {
        for(String path:new String[]{"/","/app.js","/style.css"}){var response=LocalControlTestClient.get(server.port(),path);assertEquals(200,response.status);assertEquals("own page",response.body);assertTrue(response.headers.contains("Content-Security-Policy:"));assertTrue(response.headers.contains("X-Frame-Options: DENY"));}
        assertEquals(404,LocalControlTestClient.get(server.port(),"/../private").status);assertEquals(0,effects.get());
    }
    /** Unauthenticated status and mutations never reach the effect owner. */
    @Test public void unauthenticatedRequestsHaveNoEffect() throws Exception{assertEquals(403,LocalControlTestClient.send(server.port(),"/command",null,new JSONObject().put("seq",1).put("command","text").put("value","false"),null).status);assertEquals(0,effects.get());}
    /** Even a valid bearer cannot turn a cross-site browser request into a control command. */
    @Test public void wrongOriginIsDeniedBeforeEffects() throws Exception{String token=pair();assertEquals(403,LocalControlTestClient.send(server.port(),"/command",token,new JSONObject().put("seq",1).put("command","text").put("value","false"),"https://unrelated.invalid").status);assertEquals(0,effects.get());}
    /** Unique increasing command ids make retry/replay non-mutating. */
    @Test public void duplicateCommandCannotToggleTwice() throws Exception{String token=pair();JSONObject command=new JSONObject().put("seq",1).put("command","text").put("value","false");assertEquals(200,LocalControlTestClient.send(server.port(),"/command",token,command,null).status);assertEquals(409,LocalControlTestClient.send(server.port(),"/command",token,command,null).status);assertEquals(1,effects.get());}
    /** The closed command surface contains no upload, download, activation or Prime transport route. */
    @Test public void privilegedCommandsAreNotExposed() throws Exception{String token=pair();for(String command:new String[]{"play","pause","seek","activate","download","reset","sql","focus"})assertEquals(409,LocalControlTestClient.send(server.port(),"/command",token,new JSONObject().put("seq",1).put("command",command).put("value",""),null).status);assertEquals(0,effects.get());}
    /** Real HTTP requests remain authorized past ten minutes, then expire at the original 120-minute boundary. */
    @Test public void expiryRevokesBearerAndCode() throws Exception{
        String token=pair();assertEquals(7_200_000L,LocalDeveloperServer.LIFETIME_MS);
        for(int minute=1;minute<=119;minute++){
            time.set(1000+minute*60_000L);
            var reply=LocalControlTestClient.send(server.port(),"/command",token,new JSONObject().put("seq",minute).put("command","text").put("value","true"),null);
            assertEquals("Minute "+minute,200,reply.status);assertEquals(7_200_000L-minute*60_000L,reply.json().getLong("remainingMs"));
        }
        time.set(7_200_999L);assertTrue(server.isLive());assertEquals(200,LocalControlTestClient.send(server.port(),"/status",token,new JSONObject(),null).status);
        int before=effects.get();time.set(7_201_000L);assertFalse(server.isLive());assertEquals(0,server.remainingMs());assertEquals("Commandes locales arrêtées.",server.accessInfo());
        try{assertEquals(403,LocalControlTestClient.send(server.port(),"/command",token,new JSONObject().put("seq",120).put("command","text").put("value","false"),null).status);}
        catch(java.io.IOException closed){/* A fully closed expired socket also rejects the old bearer. */}
        assertEquals(before,effects.get());
    }
    /** Pairing late grants only the remainder, never a fresh two-hour browser lease. */
    @Test public void latePairingDoesNotRenewPeriod() throws Exception{
        time.set(7_000_000L);String token=pair();var status=LocalControlTestClient.send(server.port(),"/status",token,new JSONObject(),null);
        assertEquals(200,status.status);assertEquals(201000,status.json().getLong("remainingMs"));
    }
    /** Voluntary stop revokes immediately without invoking or mutating the commentary runtime. */
    @Test public void voluntaryStopRevokesWithoutEffects() throws Exception{pair();server.close();assertFalse(server.isLive());assertEquals(0,server.remainingMs());assertEquals(0,effects.get());}
    /** Five wrong pairing attempts cannot be bypassed by a sixth correct code. */
    @Test public void pairingAttemptsAreBounded() throws Exception{String code=LocalControlTestClient.code(server.accessInfo());String wrong=code.equals("00000000")?"11111111":"00000000";for(int i=0;i<5;i++)assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",wrong),null).status);assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",code),null).status);}
    /** A second browser cannot claim the code after the first has paired. */
    @Test public void pairCodeIsOneUse() throws Exception{String code=LocalControlTestClient.code(server.accessInfo());pair();assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",code),null).status);}
}
