import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/** Report only real JUnit outcomes; fail on ignored tests and retain per-class totals. */
public final class EvidenceRunner {
    public static void main(String[] args) throws Exception {
        int run=0,failed=0,ignored=0;JSONArray cases=new JSONArray();
        for(String name:Files.readAllLines(Path.of(args[0]))) {
            if(name.isBlank())continue;
            Result result=JUnitCore.runClasses(Class.forName(name));
            run+=result.getRunCount();failed+=result.getFailureCount();ignored+=result.getIgnoreCount();
            JSONObject item=new JSONObject().put("class",name).put("run",result.getRunCount()).put("failed",result.getFailureCount()).put("ignored",result.getIgnoreCount());
            JSONArray errors=new JSONArray();result.getFailures().forEach(f -> errors.put(f.toString()));item.put("failures",errors);cases.put(item);
            System.out.println(name+" : "+result.getRunCount()+" / failed "+result.getFailureCount()+" / ignored "+result.getIgnoreCount());
            result.getFailures().forEach(f -> System.out.println(f.getTrace()));
        }
        JSONObject total=new JSONObject().put("run",run).put("failed",failed).put("ignored",ignored).put("classes",cases).put("scope","LOCAL_JVM_NOT_ANDROID_SDK_OR_SONY");
        Files.writeString(Path.of(args[1]),total.toString(2)+"\n");System.out.println("TOTAL "+run+" failed="+failed+" ignored="+ignored);
        if(failed!=0 || ignored!=0 || run==0)System.exit(1);
    }
}
