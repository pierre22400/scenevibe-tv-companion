package com.scenevibe.tvcompanionpoc.installation;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static org.junit.Assert.*;

/**
 * Exercise only eager build-local binding and lookup. The test handlers deliberately
 * fail if any operation is invoked, so registry construction cannot quietly prepare,
 * restore or arm a package. No production compatibility handler exists in Phase B.
 */
public final class InstallationRegistryTest {
    /** Supply explicit trusted instances; registry metadata never discovers a class. */
    private static final class NeverInvoked implements InstallationHandler {
        int calls;
        /** Reject unintended validation during registry operations. */
        @Override public InstallationStatus validate(InstallRequest request,TvCapabilities caps) {
            calls++;throw new AssertionError("Registry invoked handler");
        }
        /** Reject unintended preparation during registry operations. */
        @Override public PreparedInstallation prepare(InstallRequest request,TvCapabilities caps) {
            calls++;throw new AssertionError("Registry invoked handler");
        }
        /** Reject unintended encoding during registry operations. */
        @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
            calls++;throw new AssertionError("Registry invoked handler");
        }
        /** Reject unintended restoration during registry operations. */
        @Override public PreparedInstallation restoreFromCache(InstallRequest snapshot,TvCapabilities caps) {
            calls++;throw new AssertionError("Registry invoked handler");
        }
        /** Reject unintended activation during registry operations. */
        @Override public InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports) {
            calls++;throw new AssertionError("Registry invoked handler");
        }
    }

    /** Create bounded registration metadata for an already-created test instance. */
    private static InstallationHandlerRegistry.Entry entry(String id,String codec,NeverInvoked handler) {
        return new InstallationHandlerRegistry.Entry(id,codec,handler);
    }

    /** Both lookup keys resolve the same eager binding and the same trusted instance. */
    @Test public void staticLookupPreservesBindingAndHandlerIdentity() {
        NeverInvoked handler=new NeverInvoked();
        InstallationHandlerRegistry.Entry overlay=entry("overlay",TvCapabilities.CODEC_TRACK_OVERLAY,handler);
        InstallationHandlerRegistry registry=new InstallationHandlerRegistry(overlay);
        assertSame(overlay,registry.findHandler("overlay"));
        assertSame(overlay,registry.findCodec(TvCapabilities.CODEC_TRACK_OVERLAY));
        assertSame(handler,registry.findHandler("overlay").handler());
    }

    /** Input ordering cannot affect iteration or either lookup result. */
    @Test public void registrationOrderDoesNotChangeDeterministicLookup() {
        InstallationHandlerRegistry.Entry a=entry("a",TvCapabilities.CODEC_TRACK_OVERLAY,new NeverInvoked());
        InstallationHandlerRegistry.Entry z=entry("z",TvCapabilities.CODEC_TRACK,new NeverInvoked());
        InstallationHandlerRegistry forward=new InstallationHandlerRegistry(a,z);
        InstallationHandlerRegistry reverse=new InstallationHandlerRegistry(z,a);
        assertEquals(forward.entries(),reverse.entries());
        assertSame(a,reverse.entries().get(0));assertSame(z,reverse.entries().get(1));
        assertSame(forward.findCodec(a.codecId()),reverse.findCodec(a.codecId()));
    }

    /** Unknown, empty and null keys stay absent instead of invoking fallback discovery. */
    @Test public void unknownAndNullKeysAreRejectedAsAbsent() {
        InstallationHandlerRegistry registry=new InstallationHandlerRegistry(
                entry("track",TvCapabilities.CODEC_TRACK,new NeverInvoked()));
        assertNull(registry.findHandler(null));assertNull(registry.findCodec(null));
        assertNull(registry.findHandler(""));assertNull(registry.findCodec("unknown"));
        assertNull(registry.findCodec(TvCapabilities.CODEC_TRACK_OVERLAY));
    }

    /** The Phase B production default registers no generic installer, including known codecs. */
    @Test public void defaultRegistryIsEmptyAndUnwired() {
        assertSame(InstallationHandlerRegistry.empty(),InstallationHandlerRegistry.empty());
        assertTrue(InstallationHandlerRegistry.empty().entries().isEmpty());
        assertNull(InstallationHandlerRegistry.empty().findCodec(TvCapabilities.CODEC_TRACK));
        assertNull(InstallationHandlerRegistry.empty().findCodec(TvCapabilities.CODEC_TRACK_OVERLAY));
    }

    /** Neither handler identity nor codec routing may have two competing owners. */
    @Test public void duplicateHandlerAndCodecBindingsAreRejected() {
        InstallationHandlerRegistry.Entry a=entry("a",TvCapabilities.CODEC_TRACK,new NeverInvoked());
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(a,
                entry("a",TvCapabilities.CODEC_TRACK_OVERLAY,new NeverInvoked())));
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(a,
                entry("b",TvCapabilities.CODEC_TRACK,new NeverInvoked())));
    }

    /** Unknown codecs, null entries and an oversized registration set fail before lookup. */
    @Test public void registryConstructionIsBoundedAndRejectsUnsupportedBindings() {
        InstallationHandlerRegistry.Entry known=entry("a",TvCapabilities.CODEC_TRACK,new NeverInvoked());
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(
                entry("unknown","unknown.codec",new NeverInvoked())));
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(
                (InstallationHandlerRegistry.Entry[])null));
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(
                (InstallationHandlerRegistry.Entry)null));
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry(known,known,known));
    }

    /** Caller arrays and exposed collections cannot change frozen routing. */
    @Test public void registryCannotBeMutatedThroughInputOrOutput() {
        InstallationHandlerRegistry.Entry known=entry("a",TvCapabilities.CODEC_TRACK,new NeverInvoked());
        InstallationHandlerRegistry.Entry[] input={known};
        InstallationHandlerRegistry registry=new InstallationHandlerRegistry(input);
        input[0]=null;
        assertSame(known,registry.findHandler("a"));
        assertThrows(UnsupportedOperationException.class,()->registry.entries().clear());
    }

    /** Construction, enumeration and repeated lookup perform no handler operation. */
    @Test public void registryNeverValidatesPreparesRestoresEncodesOrArms() {
        NeverInvoked first=new NeverInvoked(),second=new NeverInvoked();
        InstallationHandlerRegistry registry=new InstallationHandlerRegistry(
                entry("overlay",TvCapabilities.CODEC_TRACK_OVERLAY,first),
                entry("legacy",TvCapabilities.CODEC_TRACK,second));
        for (int i=0;i<10;i++) for (InstallationHandlerRegistry.Entry binding:registry.entries()) {
            assertSame(binding,registry.findHandler(binding.handlerId()));
            assertSame(binding,registry.findCodec(binding.codecId()));
        }
        assertEquals(0,first.calls);assertEquals(0,second.calls);
    }

    /** Binding metadata is bounded and final; handler state is not registry metadata. */
    @Test public void entryIdentifiersAreSafeAndMetadataFieldsAreImmutable() {
        assertThrows(IllegalArgumentException.class,()->entry("bad/id",TvCapabilities.CODEC_TRACK,new NeverInvoked()));
        assertThrows(IllegalArgumentException.class,()->entry("a","x".repeat(129),new NeverInvoked()));
        assertThrows(IllegalArgumentException.class,()->new InstallationHandlerRegistry.Entry(
                "a",TvCapabilities.CODEC_TRACK,null));
        for (Field field:InstallationHandlerRegistry.Entry.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }
}
