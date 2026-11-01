import com.scenevibe.timebasecaptureg2.CapturePolicy;
import com.scenevibe.timebasecaptureg2.PixelProbe;
import com.scenevibe.timebasecaptureg2.RoiLease;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Explicit-clock portable checks, not Android or physical acquisition evidence. */
public final class CoreChecks {
    private static int count;
    /** The executable harness owns all synthetic data. */
    private CoreChecks() { }
    /** Count one invariant and fail immediately on contradiction. */
    private static void check(boolean b) {count++;if(!b)throw new AssertionError("Invariant "+count);}
    /** Require a validation failure of the documented type. */
    private static void rejects(Runnable r) {boolean bad=false;try{r.run();}catch(IllegalArgumentException|IllegalStateException e){bad=true;}check(bad);}
    /** Exercise generation, consent, delays, one-frame gate, lease erasure and buffer validation. */
    public static void main(String[] args) {
        CapturePolicy p=new CapturePolicy();check(!p.active());check(!p.accepts(0,0));
        rejects(()->p.arm(0,499));rejects(()->p.arm(0,60001));rejects(()->p.arm(-1,500));
        long g=p.arm(1000,3000);check(p.active());rejects(()->p.arm(1000,3000));
        check(!p.consent(g+1,1100));check(!p.consent(g,999));check(p.consent(g,1100));check(!p.consent(g,1200));
        check(!p.begin(g,4100,false));p.left();check(!p.begin(g,4100,true));check(!p.begin(g,4099,false));check(p.begin(g,4100,false));
        check(!p.accepts(g,4349));check(p.accepts(g,4350));check(!p.frame(g+1,4350));check(p.frame(g,4350));check(!p.frame(g,4351));
        p.close();check(!p.active());check(!p.accepts(g,4400));check(!p.consent(g,4400));
        long next=p.arm(20000,500);check(next>g);check(p.consent(next,20100));p.left();check(!p.begin(next,22601,false));p.close();
        next=p.arm(30000,500);check(!p.consent(next,150001));p.close();
        next=p.arm(200000,60000);check(p.consent(next,200100));p.left();check(p.begin(next,260100,false));
        check(p.accepts(next,270099));check(!p.accepts(next,270100));p.close();
        check(new CapturePolicy().state==CapturePolicy.State.DISARMED);
        int[] r=CapturePolicy.roi(.05,.1,.9,.3,1920,1080);check(Arrays.equals(r,new int[]{96,108,1824,432}));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})rejects(()->CapturePolicy.roi(bad,0,.5,.5,100,100));
        rejects(()->CapturePolicy.roi(0,0,0,1,100,100));rejects(()->CapturePolicy.roi(.8,0,.3,1,100,100));rejects(()->CapturePolicy.roi(0,0,1,1,4097,100));
        ByteBuffer b=ByteBuffer.allocate(32);b.put(0,(byte)255);check(PixelProbe.pixel(b,0,0,2,2,4,16)==0xffff0000);
        check(PixelProbe.crop(b,2,2,4,16,new int[]{0,0,2,2}).length==4);
        rejects(()->PixelProbe.crop(null,2,2,4,16,r));rejects(()->PixelProbe.pixel(b,0,0,2,2,8,16));
        rejects(()->PixelProbe.pixel(b,0,0,2,2,4,7));rejects(()->PixelProbe.crop(ByteBuffer.allocate(2),2,2,4,16,new int[]{0,0,2,2}));
        rejects(()->PixelProbe.crop(b,2,2,4,16,new int[]{-1,0,2,2}));
        RoiLease lease=new RoiLease();int[] pixels={1,2,3,4};lease.put(7,pixels,2,2,1000);
        check(lease.get(8,1001)==null);check(lease.get(7,30999)==pixels);check(lease.get(7,31000)==null);check(Arrays.equals(pixels,new int[4]));
        pixels=new int[]{5};lease.put(8,pixels,1,1,32000);lease.clear();check(pixels[0]==0);check(lease.get(8,32001)==null);
        rejects(()->lease.put(9,new int[1],2,2,0));check(new RoiLease().get(8,0)==null);
        int[] pattern=new int[10000];for(int i=0;i<4;i++)pattern[7500+(int)(100*(.2+i*.2))]=PixelProbe.COLORS[i];
        check(PixelProbe.matches(pattern,100,100)==4);check(PixelProbe.matches(new int[10000],100,100)==0);
        System.out.println("PASS "+count+" portable invariants; NOT_ANDROID_CAPTURE");
    }
}
