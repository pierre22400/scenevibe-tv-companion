package com.scenevibe.tvcompanionpoc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

/** Browser-only recording owner; this is not Android service, Cloud admission or Prime compatibility evidence. */
public final class LocalBrowserFixture {
    private boolean text=true,audio=true,adsync;
    private boolean suspended,adProbe;
    private int annotations;
    /** Launch the real HTTP server and exact bundled assets against a recording command port. */
    public static void main(String[] args) throws Exception {LocalBrowserFixture fixture=new LocalBrowserFixture();Path assets=Path.of(args[0]);
        var clock=new java.util.concurrent.atomic.AtomicLong(1000);
        LocalDeveloperServer server=new LocalDeveloperServer(fixture::command,name->Files.readAllBytes(assets.resolve(name)),clock::get);server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Files.writeString(Path.of(args[1]),new JSONObject().put("port",server.port()).put("instructions",server.accessInfo()).toString());
        try(var input=new java.util.Scanner(System.in)){while(input.hasNextLong())clock.set(input.nextLong());}
    }
    /** Record only channel preferences and return honest simulated service/focus state. */
    private JSONObject command(String command,String value) throws Exception {
        if("text".equals(command))text=Boolean.parseBoolean(value);if("audio".equals(command))audio=Boolean.parseBoolean(value);if("adsync".equals(command))adsync=Boolean.parseBoolean(value);
        if("adprobe".equals(command))adProbe=Boolean.parseBoolean(value);if("admark".equals(command)&&adProbe)annotations++;
        if("suspend".equals(command))suspended=true;if("resume".equals(command))suspended=false;
        return new JSONObject().put("text",text).put("audio",audio).put("adsync",adsync).put("active",true).put("focus","PASSIVE")
                .put("adObservation",new JSONObject().put("enabled",adProbe).put("candidate","UNCERTAIN").put("reason","NO_PUBLIC_AD_MARKER").put("adStarts",0).put("adEnds",0).put("annotations",annotations))
                .put("suspended",suspended).put("commentState",suspended?"USER_PAUSED":"ACTIVE")
                .put("runtime","OWN BROWSER RECORDING FIXTURE · no Android/Prime").put("sync",suspended?"USER_SUSPENDED":"MEDIA_FIXTURE_ONLY").put("result",command+":"+value);
    }
}
