package com.scenevibe.tvcompanionpoc.installation;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/**
 * Locks executable local truth independently of parser acceptance. Wall, asset acquisition
 * and shared asset cache remain unsupported; pause semantics are checked per rendering
 * contract, so the legacy countdown does not become a native wall-clock feature.
 */
public final class TvCapabilitiesTest {
    /** Build pure needs without invoking a compatibility parser. */
    private static ExecutionRequirements needs(String contract,ExecutionRequirements.ClockMode clock,
            ExecutionRequirements.PauseBehavior pause,boolean remote,boolean cache) {
        return new ExecutionRequirements(contract,clock,pause,1,remote,cache);
    }
    /** The same immutable descriptor is returned with deterministically ordered identifiers. */
    @Test public void canonicalDescriptorIsDeterministicAndCollectionsImmutable() {
        TvCapabilities caps=TvCapabilities.current();assertSame(caps,TvCapabilities.current());
        assertEquals(Arrays.asList(TvCapabilities.CODEC_TRACK_OVERLAY,TvCapabilities.CODEC_TRACK),
                new java.util.ArrayList<>(caps.supportedCodecs()));
        assertThrows(UnsupportedOperationException.class,()->caps.supportedCodecs().clear());
        assertThrows(UnsupportedOperationException.class,()->caps.supportedRenderingContracts().clear());
        assertThrows(UnsupportedOperationException.class,()->caps.supportedClocks().clear());
        assertThrows(UnsupportedOperationException.class,()->caps.supportedPauseBehaviors().clear());
    }
    /** Only the media clock is advertised or supported by native requirements. */
    @Test public void mediaClockSupportedAndWallClockExplicitlyRejected() {
        TvCapabilities caps=TvCapabilities.current();assertEquals(Collections.singleton(ExecutionRequirements.ClockMode.MEDIA),caps.supportedClocks());
        assertTrue(caps.supports(TvCapabilities.CODEC_TRACK_OVERLAY,needs(TvCapabilities.OVERLAY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,false,false)));
        assertFalse(caps.supportsWallClockExecution());
        for (String codec:caps.supportedCodecs())
            assertFalse(caps.supports(codec,needs(codec.equals(TvCapabilities.CODEC_TRACK)?TvCapabilities.LEGACY_CONTRACT:TvCapabilities.OVERLAY_CONTRACT,
                    ExecutionRequirements.ClockMode.WALL,ExecutionRequirements.PauseBehavior.FREEZE,false,false)));
    }
    /** Native rendering and legacy compatibility are present without implying generic installation is wired. */
    @Test public void renderingContractsDescribeBothQualifiedPaths() {
        TvCapabilities caps=TvCapabilities.current();
        assertTrue(caps.supportedRenderingContracts().contains(TvCapabilities.OVERLAY_CONTRACT));
        assertTrue(caps.supportedRenderingContracts().contains(TvCapabilities.LEGACY_CONTRACT));
        assertEquals(2,caps.supportedRenderingContracts().size());
    }
    /** Neither deferred asset feature can pass a local executable-profile check. */
    @Test public void remoteAssetsAndSharedAssetCacheAreNotAdvertisedOrAccepted() {
        TvCapabilities caps=TvCapabilities.current();
        assertFalse(caps.supportsRemoteAssetAcquisition());assertFalse(caps.supportsSharedAssetCache());
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK,needs(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,true,false)));
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK,needs(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,false,true)));
    }
    /** Existing legacy continue semantics must not be advertised for native media-clock scenes. */
    @Test public void pauseSemanticsAreCheckedPerContract() {
        TvCapabilities caps=TvCapabilities.current();
        for (ExecutionRequirements.PauseBehavior pause:ExecutionRequirements.PauseBehavior.values())
            assertTrue(caps.supports(TvCapabilities.CODEC_TRACK,needs(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,pause,false,false)));
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK_OVERLAY,needs(TvCapabilities.OVERLAY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.CONTINUE,false,false)));
    }
    /** Closed profile matching rejects cross-contract and unknown/null identifiers safely. */
    @Test public void unknownNullAndMismatchedProfilesAreRejected() {
        TvCapabilities caps=TvCapabilities.current();ExecutionRequirements overlay=needs(TvCapabilities.OVERLAY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,false,false);
        assertFalse(caps.supports(null,overlay));assertFalse(caps.supports("unknown.codec",overlay));
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK,null));
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK,overlay));
        assertFalse(caps.supports(TvCapabilities.CODEC_TRACK,needs("unknown.contract",ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,false,false)));
    }
    /** Descriptor limits retain exact parser/transport ceilings and their units. */
    @Test public void qualifiedLimitsPreserveBytesUtf16AndPerSceneUnits() {
        TvCapabilities caps=TvCapabilities.current();
        assertEquals(3_000_000,caps.maximumPackageBytes());assertEquals(2_400_000,caps.maximumArtifactBytes());assertEquals(2,caps.maximumArtifactCount());
        assertEquals(400_000,caps.maximumArtifactUtf16Units(TvCapabilities.LEGACY_CONTRACT));
        assertEquals(800_000,caps.maximumArtifactUtf16Units(TvCapabilities.OVERLAY_CONTRACT));assertEquals(0,caps.maximumArtifactUtf16Units(null));
        assertEquals(256,caps.maximumScenes());assertEquals(256,caps.maximumPrimitivesPerScene());
        assertEquals(65_536,caps.maximumPrimitivesPerManifest());assertEquals(4,caps.maximumGroupDepth());
        assertEquals(1920,caps.canvasWidth());assertEquals(1080,caps.canvasHeight());assertEquals(2000,caps.maximumTextCodePoints());
    }
    /** Requirements themselves retain no invalid revision-equivalent count or absent enum values. */
    @Test public void requirementsRejectInvalidCountsAndEnumValues() {
        for (int count:new int[]{0,-1,257,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,count,false,false));
        assertThrows(IllegalArgumentException.class,()->new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,null,ExecutionRequirements.PauseBehavior.FREEZE,1,false,false));
        assertThrows(IllegalArgumentException.class,()->new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,null,1,false,false));
        ExecutionRequirements maximum=new ExecutionRequirements(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,256,false,false);
        assertEquals(256,maximum.timedSceneCount());
    }
    /** An overlay profile cannot claim a missing second artifact. */
    @Test public void shapeValidationRequiresArtifactsForTheDeclaredProfile() {
        InstallRequest one=new InstallRequest(13,TvCapabilities.CODEC_TRACK_OVERLAY,Collections.singletonMap("data",new byte[]{1}));
        ExecutionRequirements req=needs(TvCapabilities.OVERLAY_CONTRACT,ExecutionRequirements.ClockMode.MEDIA,ExecutionRequirements.PauseBehavior.FREEZE,false,false);
        assertEquals(InstallationStatus.INVALID_PACKAGE,TvCapabilities.current().validate(one,req));
        assertThrows(IllegalArgumentException.class,()->new PreparedInstallation(one,"handler",Collections.emptyMap(),req,TvCapabilities.current()));
        assertEquals(InstallationStatus.INVALID_PACKAGE,TvCapabilities.current().validate(null,req));
    }
    /** Capability checking returns a bounded code instead of promoting unsupported values to PREPARED. */
    @Test public void unsupportedNeedsReturnBoundedLocalDecision() {
        InstallRequest one=new InstallRequest(13,TvCapabilities.CODEC_TRACK,Collections.singletonMap("data",new byte[]{1}));
        ExecutionRequirements wall=needs(TvCapabilities.LEGACY_CONTRACT,ExecutionRequirements.ClockMode.WALL,ExecutionRequirements.PauseBehavior.FREEZE,false,false);
        assertEquals(InstallationStatus.UNSUPPORTED_CAPABILITY,TvCapabilities.current().validate(one,wall));
        assertEquals(InstallationStatus.INVALID_PACKAGE,TvCapabilities.current().validate(one,null));
    }
}
