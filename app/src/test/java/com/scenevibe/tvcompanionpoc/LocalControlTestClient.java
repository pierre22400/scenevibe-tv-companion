package com.scenevibe.tvcompanionpoc;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import org.json.JSONObject;

/** Test-only loopback browser protocol; never an input injector or a production Cloud client. */
final class LocalControlTestClient {
    static final class Reply {
        final int status;final String body,headers;
        /** Preserve actual bounded HTTP evidence without printing an ephemeral bearer. */
        Reply(int status,String body,String headers){this.status=status;this.body=body;this.headers=headers;}
        /** Decode only a JSON API reply selected by this test. */
        JSONObject json() throws Exception{return new JSONObject(body);}
    }
    /** Exercise a same-origin request over a real socket, including malformed-origin negative cases. */
    static Reply send(int port,String path,String token,JSONObject body,String origin) throws Exception {
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);String host="127.0.0.1:"+port;
        String headers="POST "+path+" HTTP/1.1\r\nHost: "+host+"\r\nOrigin: "+(origin==null?"http://"+host:origin)+"\r\nContent-Type: application/json\r\nX-SceneVibe-Local: 1\r\nContent-Length: "+data.length+"\r\n"+(token==null?"":"Authorization: Bearer "+token+"\r\n")+"\r\n";
        try(Socket socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(5000);socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(data);socket.getOutputStream().flush();ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[2048];for(int n;(n=socket.getInputStream().read(buffer))!=-1;)output.write(buffer,0,n);
            String raw=new String(output.toByteArray(),StandardCharsets.UTF_8);int split=raw.indexOf("\r\n\r\n");return new Reply(Integer.parseInt(raw.split(" ")[1]),raw.substring(split+4),raw.substring(0,split));}
    }
    /** Read fixed bundled assets through the actual listener, without authentication or third-party navigation. */
    static Reply get(int port,String path) throws Exception {
        try(Socket socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(5000);socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[2048];for(int n;(n=socket.getInputStream().read(buffer))!=-1;)output.write(buffer,0,n);
            String raw=new String(output.toByteArray(),StandardCharsets.UTF_8);int split=raw.indexOf("\r\n\r\n");return new Reply(Integer.parseInt(raw.split(" ")[1]),raw.substring(split+4),raw.substring(0,split));}
    }
    /** Extract the test's own TV-displayed code, never a bearer or administrator secret. */
    static String code(String instructions){var match=java.util.regex.Pattern.compile("Code téléphone : ([0-9]{8})").matcher(instructions);if(!match.find())throw new IllegalStateException("OWN_PAIR_CODE_MISSING");return match.group(1);}
}
