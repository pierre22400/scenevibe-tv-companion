import com.scenevibe.timebasecaptureg1.CapturePolicy;
import com.scenevibe.timebasecaptureg1.PixelProbe;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Portable deterministic scalar/buffer tests. These checks do not emulate Android capture. */
public final class CoreChecks {
    private static int checks;
    /** No instances; the executable harness owns its test data. */
    private CoreChecks() { }
    /** Count an invariant and fail the process immediately if it does not hold. */
    private static void require(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    /** Verify a validation failure without swallowing a different exception type. */
    private static void rejects(Runnable action) {
        boolean rejected=false; try { action.run(); } catch(IllegalArgumentException e) { rejected=true; } require(rejected);
    }
    /** Exercise finite normalized ROI, states, expiry, padding, truncation and pattern negatives. */
    public static void main(String[] args) {
        int[] r=CapturePolicy.roi(.05,.1,.9,.3,1920,1080); require(Arrays.equals(r,new int[]{96,108,1824,432}));
        require(Arrays.equals(CapturePolicy.roi(0,0,1,1,1,1),new int[]{0,0,1,1}));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})rejects(()->CapturePolicy.roi(bad,0,.5,.5,100,100));
        rejects(()->CapturePolicy.roi(0,0,0,.5,100,100));rejects(()->CapturePolicy.roi(.8,0,.3,.5,100,100));
        rejects(()->CapturePolicy.roi(0,0,1,1,0,100));rejects(()->CapturePolicy.roi(0,0,1,1,4097,100));
        CapturePolicy p=new CapturePolicy(); require(p.state==CapturePolicy.State.DISARMED);require(!p.accepts(0,0));
        rejects(()->p.arm(0,499));rejects(()->p.arm(0,5001));
        long g=p.arm(1000,3000); require(!p.begin(g+1,4000));require(!p.begin(g,999));
        rejects(()->p.arm(1000,3000));require(p.begin(g,4000));require(p.accepts(g,4000));
        require(!p.accepts(g+1,4000));require(!p.accepts(g,3999));require(p.accepts(g,13999));require(!p.accepts(g,14000));
        p.close();require(!p.accepts(g,5000));p.close();require(p.state==CapturePolicy.State.CLOSED);
        long next=p.arm(20000,500);require(next>g);require(!p.begin(g,20500));require(!p.begin(next,35000));p.close();
        CapturePolicy restarted=new CapturePolicy();require(restarted.state==CapturePolicy.State.DISARMED);require(!restarted.begin(next,0));
        ByteBuffer bytes=ByteBuffer.allocate(32);bytes.put(0,(byte)255);bytes.put(1,(byte)20);bytes.put(2,(byte)10);
        require(PixelProbe.pixel(bytes,0,0,2,2,4,16)==0xffff140a);
        require(PixelProbe.crop(bytes,2,2,4,16,new int[]{0,0,2,2}).length==4);
        rejects(()->PixelProbe.pixel(bytes,0,0,2,2,8,16));rejects(()->PixelProbe.pixel(bytes,0,0,2,2,4,7));
        rejects(()->PixelProbe.pixel(null,0,0,2,2,4,16));rejects(()->PixelProbe.pixel(bytes,2,0,2,2,4,16));
        rejects(()->PixelProbe.crop(bytes,2,2,4,16,new int[]{-1,0,2,2}));
        rejects(()->PixelProbe.crop(ByteBuffer.allocate(2),2,2,4,16,new int[]{0,0,2,2}));
        rejects(()->PixelProbe.crop(bytes,1000000,1,4,4000000,new int[]{0,0,1000000,1}));
        rejects(()->CapturePolicy.roi(0,0,Double.NaN,.5,100,100));
        rejects(()->CapturePolicy.roi(0,0,.5,Double.POSITIVE_INFINITY,100,100));
        require(PixelProbe.matches(new int[10000],100,100)==0);require(PixelProbe.matches(null,100,100)==0);
        int[] pattern=new int[10000];for(int i=0;i<4;i++)pattern[75*100+(int)(100*(.2+i*.2))]=PixelProbe.COLORS[i];
        require(PixelProbe.matches(pattern,100,100)==4);pattern[75*100+20]=0;require(PixelProbe.matches(pattern,100,100)==3);
        require(!PixelProbe.near(0xff000000,0xffffffff));require(PixelProbe.near(0xffffffff,0xfff0f0f0));
        System.out.println("PASS "+checks+" portable invariants; ANDROID_CAPTURE_NOT_TESTED_BY_THIS_HARNESS");
    }
}
