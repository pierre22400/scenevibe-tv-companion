package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.AudioEvent;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Test-only synthetic calendar shared by JVM and Android instrumentation.
 * Reflection constructs only a software fixture; it grants no Cloud/native
 * admission and is excluded from every production APK. No AUDIO is generated:
 * native tests resolve a previously committed SceneVibe MP3 test asset.
 */
final class AdSyncTestFixture {
    /** Compose the actual existing runtime/renderers over explicit test drawing and local audio ports. */
    static StudioMultimediaRuntime runtime(SceneRuntimeController.SceneSink drawing,LocalAudioPort port) throws Exception {
        List<OverlayManifest.Scene> scenes=List.of(
                new OverlayManifest.Scene("text1",1000,1000,List.of()),
                new OverlayManifest.Scene("text2",5000,1000,List.of()));
        OverlayManifest manifest=new OverlayManifest("video:synthetic","video","synthetic","media","freeze",scenes);
        List<SceneEvent> events=new ArrayList<>();
        for(OverlayManifest.Scene s:scenes)events.add(new SceneEvent(s.id,s.startMs,s.durationMs));
        Map<String,AudioEvent> audio=new LinkedHashMap<>();
        for(int i=0;i<3;i++){String id="audio"+(i+1);long start=new long[]{2000,6000,9000}[i];
            events.add(new SceneEvent(id,start,3000));audio.put(id,AudioEvent.audio(id,"existing-test-tone",start,3000,1f));}
        var constructor=StudioMultimediaPackage.class.getDeclaredConstructor(JSONObject.class,String.class,
                MediaCalendar.class,OverlayManifest.class,Map.class,Map.class);
        constructor.setAccessible(true);
        StudioMultimediaPackage track=constructor.newInstance(new JSONObject().put("trackId","synthetic").put("revision",1),
                "synthetic-software-only",new MediaCalendar(events,true),manifest,audio,Map.of());
        StudioMultimediaRuntime runtime=new StudioMultimediaRuntime(drawing,port);
        runtime.loadForSoftwareQualification(track,"synthetic-activation");return runtime;
    }
    /** Prevent accidental stateful fixture ownership. */
    private AdSyncTestFixture() {}
}
