package com.scenevibe.tvcompanionpoc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/**
 * Explicit ten-minute trusted-LAN fallback for developer commands, never Cloud.
 * One TV-displayed pairing code admits one ephemeral browser bearer. Host/Origin
 * checks, a custom same-origin header, bounded input/concurrency and one-use
 * command sequence numbers prevent cross-site/replayed actions. HTTP is local
 * plaintext: use only a trusted private network, not a production remote API.
 * No credential, token, address or pairing code is logged or persisted.
 */
final class LocalDeveloperServer implements AutoCloseable {
    interface Commands {
        /** Execute only bounded SceneVibe commands; no activation, download, transport or filesystem path. */
        JSONObject apply(String command,String value) throws Exception;
    }
    interface Pages {
        /** Read a fixed bundled page; arbitrary paths never reach the asset loader. */
        byte[] read(String name) throws Exception;
    }
    private final Commands commands;
    private final Pages pages;
    private final LongSupplier clock;
    private final List<String> addresses=new ArrayList<>();
    private final ThreadPoolExecutor clients=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(3));
    private final SecureRandom random=new SecureRandom();
    private ServerSocket server;
    private volatile boolean live;
    private long deadline,lastSequence;
    private int badCodes;
    private String code,token;

    /** Production lifetime uses monotonic time; no playback clock or Cloud lease is created. */
    LocalDeveloperServer(Commands commands,Pages pages){this(commands,pages,()->System.nanoTime()/1_000_000L);}
    /** Inject only elapsed time for expiry qualification, not command authority. */
    LocalDeveloperServer(Commands commands,Pages pages,LongSupplier clock){this.commands=commands;this.pages=pages;this.clock=clock;}
    /** Bind an ephemeral port only after the TV user explicitly enables local commands. */
    synchronized void start() throws Exception {
        if(live)return;
        addresses.add("127.0.0.1");
        for(NetworkInterface face:Collections.list(NetworkInterface.getNetworkInterfaces()))
            for(InetAddress address:Collections.list(face.getInetAddresses()))
                if(address instanceof Inet4Address && address.isSiteLocalAddress())addresses.add(address.getHostAddress());
        server=new ServerSocket();server.bind(new InetSocketAddress(0));server.setSoTimeout(1000);
        code=String.format(Locale.ROOT,"%08d",random.nextInt(100_000_000));deadline=clock.getAsLong()+600_000L;live=true;
        Thread thread=new Thread(this::accept,"scenevibe-local-controls");thread.setDaemon(true);thread.start();
    }
    /** Return public connection instructions only to the TV UI; never put a bearer in a URL. */
    synchronized String accessInfo(){if(!isLive())return "Commandes locales arrêtées.";return "Réseau privé de confiance uniquement · HTTP local · expiration 10 min.\nhttp://"+advertisedAddress()+":"+port()+"\n"+(token==null?"Code téléphone : "+code:"Téléphone associé · garde sa page ouverte.");}
    /** Prefer an actual private interface, while loopback remains available to the owned native bank. */
    private String advertisedAddress(){return addresses.size()>1?addresses.get(1):"127.0.0.1";}
    /** Expose only the bound port for native socket qualification. */
    synchronized int port(){return server==null?-1:server.getLocalPort();}
    /** Expiry never renews itself because a browser polls status. */
    boolean isLive(){return live && clock.getAsLong()<deadline;}
    /** Bound the accepted queue; slow clients cannot allocate an unbounded worker pool. */
    private void accept(){try{while(isLive())try{Socket socket=server.accept();
        if(!socket.getInetAddress().isSiteLocalAddress() && !socket.getInetAddress().isLoopbackAddress()){socket.close();continue;}
        socket.setSoTimeout(2000);try{clients.execute(()->handle(socket));}catch(java.util.concurrent.RejectedExecutionException busy){socket.close();}
    }catch(SocketTimeoutException tick){/* Check the absolute lifetime again. */}}catch(Exception stopped){/* No private exception text is logged. */}finally{close();}}
    /** Parse a single bounded HTTP request and close its connection after every response. */
    private void handle(Socket socket){try(Socket client=socket){
        InputStream input=client.getInputStream();ByteArrayOutputStream raw=new ByteArrayOutputStream();int tail=0;
        while(raw.size()<8192){int value=input.read();if(value<0)return;raw.write(value);tail=(tail<<8)|value;if(tail==0x0d0a0d0a)break;}
        if(raw.size()>=8192){reply(client,413,"application/json",bytes("{}"));return;}
        String[] lines=new String(raw.toByteArray(),StandardCharsets.US_ASCII).split("\r\n");String[] request=lines[0].split(" ");
        if(request.length!=3 || !"HTTP/1.1".equals(request[2])){reply(client,400,"application/json",bytes("{}"));return;}
        Map<String,String> headers=new HashMap<>();
        for(int i=1;i<lines.length;i++){int split=lines[i].indexOf(':');if(split<1){reply(client,400,"application/json",bytes("{}"));return;}
            if(headers.put(lines[i].substring(0,split).toLowerCase(Locale.ROOT),lines[i].substring(split+1).trim())!=null){reply(client,400,"application/json",bytes("{}"));return;}}
        String host=headers.get("host");boolean known=false;for(String address:addresses)if((address+":"+port()).equals(host))known=true;
        if(!isLive() || !known){reply(client,403,"application/json",bytes("{}"));return;}
        if("GET".equals(request[0]) && ("/".equals(request[1]) || "/app.js".equals(request[1]) || "/style.css".equals(request[1]))){
            String name="/".equals(request[1])?"index.html":request[1].substring(1);reply(client,200,name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css":"text/html; charset=utf-8",pages.read(name));return;}
        if(!"POST".equals(request[0]) || !java.util.Arrays.asList("/pair","/status","/command").contains(request[1])){reply(client,404,"application/json",bytes("{}"));return;}
        if(!("http://"+host).equals(headers.get("origin")) || !"1".equals(headers.get("x-scenevibe-local")) || !"application/json".equals(headers.get("content-type"))
                || headers.containsKey("transfer-encoding")){reply(client,403,"application/json",bytes("{}"));return;}
        int length=Integer.parseInt(headers.getOrDefault("content-length","-1"));if(length<2 || length>1024){reply(client,413,"application/json",bytes("{}"));return;}
        byte[] body=new byte[length];for(int offset=0;offset<length;){int count=input.read(body,offset,length-offset);if(count<0)return;offset+=count;}
        JSONObject json=new JSONObject(new String(body,StandardCharsets.UTF_8));int status=200;JSONObject result=new JSONObject();String dispatch=null,dispatchValue="";
        synchronized(this){
            if(!isLive()){status=403;result=new JSONObject();}
            else if("/pair".equals(request[1])){
                if(token!=null || badCodes>=5 || json.length()!=1 || !json.has("code")){status=403;result=new JSONObject();}
                else if(!equal(code,json.optString("code"))){badCodes++;status=403;result=new JSONObject();}
                else {byte[] secret=new byte[32];random.nextBytes(secret);token=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);code=null;result=new JSONObject().put("token",token);}
            }else if(token==null || !equal("Bearer "+token,headers.getOrDefault("authorization",""))){status=403;result=new JSONObject();}
            else if("/status".equals(request[1])){dispatch="status";}
            else {
                long sequence=json.optLong("seq",-1);String command=json.optString("command"),value=json.optString("value");
                if(json.length()!=3 || sequence<=lastSequence || sequence>1_000_000 || !java.util.Arrays.asList("text","audio","adsync","suspend","resume","calibrate","panel").contains(command) || value.length()>32){status=409;result=new JSONObject();}
                else {lastSequence=sequence;dispatch=command;dispatchValue=value;}
            }
        }
        // Never hold this listener's monitor while awaiting the Android main
        // thread: the TV UI may be reading its pairing state on that same thread.
        if(dispatch!=null)result=commands.apply(dispatch,dispatchValue);
        reply(client,status,"application/json",bytes(result.toString()));
    }catch(Exception invalid){/* Invalid, incomplete or timed-out requests have no fallback command. */}}
    /** Compare secret values without an early character mismatch. */
    private static boolean equal(String left,String right){return left!=null && right!=null && MessageDigest.isEqual(bytes(left),bytes(right));}
    /** Encode fixed UI/protocol text, never a private media payload. */
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    /** Return same-origin, uncached assets/results with no CORS, embedding or external script capability. */
    private void reply(Socket client,int status,String type,byte[] body) throws Exception {
        String headers="HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n\r\n";
        client.getOutputStream().write(bytes(headers));client.getOutputStream().write(body);client.getOutputStream().flush();
    }
    /** Revoke code and bearer immediately; stopping this listener never stops comments or changes preferences. */
    @Override public synchronized void close(){live=false;code=null;token=null;try{if(server!=null)server.close();}catch(Exception ignored){/* Already closed. */}clients.shutdownNow();}
}
