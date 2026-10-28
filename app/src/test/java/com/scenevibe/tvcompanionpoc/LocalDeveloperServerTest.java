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
    /** Polling never extends the absolute ten-minute control lifetime. */
    @Test public void expiryRevokesBearerAndCode() throws Exception{pair();time.set(601000);assertFalse(server.isLive());assertEquals("Commandes locales arrêtées.",server.accessInfo());assertEquals(0,effects.get());}
    /** Five wrong pairing attempts cannot be bypassed by a sixth correct code. */
    @Test public void pairingAttemptsAreBounded() throws Exception{String code=LocalControlTestClient.code(server.accessInfo());String wrong=code.equals("00000000")?"11111111":"00000000";for(int i=0;i<5;i++)assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",wrong),null).status);assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",code),null).status);}
    /** A second browser cannot claim the code after the first has paired. */
    @Test public void pairCodeIsOneUse() throws Exception{String code=LocalControlTestClient.code(server.accessInfo());pair();assertEquals(403,LocalControlTestClient.send(server.port(),"/pair",null,new JSONObject().put("code",code),null).status);}
}
