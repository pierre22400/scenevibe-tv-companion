package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationHandlerRegistry;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Prove eager Video composition is exact, stable and does not silently wire generic code. */
public final class M4PhaseDRegistryTest {
    /** Exactly the two Phase C identities bind to the corresponding qualified codec instances. */
    @Test public void exactlyTwoQualifiedHandlerBindingsExist() {
        InstallationHandlerRegistry registry=VideoInstallationHandlers.registry();assertEquals(2,registry.entries().size());
        assertSame(VideoInstallationHandlers.manifested(),registry.findHandler(InstallationStore.COMPAT_OVERLAY_HANDLER_ID).handler());
        assertSame(VideoInstallationHandlers.legacy(),registry.findHandler(InstallationStore.COMPAT_TRACK_HANDLER_ID).handler());
        assertSame(VideoInstallationHandlers.manifested(),registry.findCodec(TvCapabilities.CODEC_TRACK_OVERLAY).handler());
        assertSame(VideoInstallationHandlers.legacy(),registry.findCodec(TvCapabilities.CODEC_TRACK).handler());
    }
    /** Metadata and singleton handler instances are stable and sorted across every lookup. */
    @Test public void lookupDoesNotConstructHandlersOrChangeMetadata() {
        InstallationHandlerRegistry registry=VideoInstallationHandlers.registry();
        assertTrue(registry.entries().get(0).handlerId().compareTo(registry.entries().get(1).handlerId())<0);
        for (int i=0;i<10;i++) {assertSame(registry,VideoInstallationHandlers.registry());
            assertSame(VideoInstallationHandlers.manifested(),registry.findCodec(TvCapabilities.CODEC_TRACK_OVERLAY).handler());}
        try {registry.entries().clear();fail("Mutable registry");} catch (UnsupportedOperationException expected) { }
    }
    /** No hidden fallback resolves an unknown or null handler/codec. */
    @Test public void unknownAndNullBindingsRemainAbsent() {
        InstallationHandlerRegistry registry=VideoInstallationHandlers.registry();
        assertNull(registry.findHandler(null));assertNull(registry.findCodec(null));
        assertNull(registry.findHandler("unknown"));assertNull(registry.findCodec("unknown"));
    }
    /** Existing generic empty composition remains empty and does not auto-import Video. */
    @Test public void genericDefaultRegistryRemainsEmpty() {assertTrue(InstallationHandlerRegistry.empty().entries().isEmpty());}
}
