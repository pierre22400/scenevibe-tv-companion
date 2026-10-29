package com.scenevibe.tvcompanionpoc.ad;

/** Exact publisher boundary: observation of another platform never qualifies its protection. */
public final class AdProtectionPlatformScope {
    public static final String PRIME="com.amazon.amazonvideo.livingroom";
    /** This policy has no instance or state and cannot alter the v26 guard machine. */
    private AdProtectionPlatformScope() {}
    /** The disposable debug publisher is the only explicit test exception, never Netflix or Disney+. */
    public static boolean permits(String packageName,boolean ownedDebugFixture){
        return PRIME.equals(packageName) || ownedDebugFixture && "com.scenevibe.tvcompanionpoc".equals(packageName);
    }
}
