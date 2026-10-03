"""Guard the POC's narrow Android permission and component boundary."""

import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class PocContractTests(unittest.TestCase):
    """Detect accidental expansion beyond the overlay and narrow LAN transport."""

    def test_permissions_remain_limited_to_overlay_foreground_and_lan(self):
        """Allow only overlay, foreground service and the bounded LAN transport."""
        root = ET.parse(MANIFEST).getroot()
        permissions = {node.attrib[ANDROID + "name"] for node in root.findall("uses-permission")}
        self.assertEqual(
            permissions,
            {
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
                # Autostart opt-in (FEAT-005): required for the BootReceiver that listens only
                # to BOOT_COMPLETED / MY_PACKAGE_REPLACED and arms the overlay in boot-prepare
                # mode. Never launches UI; the opt-in defaults to off.
                "android.permission.RECEIVE_BOOT_COMPLETED",
            },
        )

    def test_boot_receiver_listens_only_to_allowed_boot_actions(self):
        """The autostart receiver may listen ONLY to BOOT_COMPLETED and MY_PACKAGE_REPLACED."""
        root = ET.parse(MANIFEST).getroot()
        application = root.find("application")
        receivers = {
            node.attrib[ANDROID + "name"]: node
            for node in application.findall("receiver")
        }
        boot = receivers[".BootReceiver"]
        # A system broadcast receiver must be exported to receive BOOT_COMPLETED.
        self.assertEqual(boot.attrib[ANDROID + "exported"], "true")
        # Must NOT be directBootAware (no Device Protected Storage / LOCKED_BOOT_COMPLETED).
        self.assertNotIn(ANDROID + "directBootAware", boot.attrib)
        actions = {
            node.attrib[ANDROID + "name"]
            for node in boot.findall("intent-filter/action")
        }
        self.assertEqual(
            actions,
            {
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
            },
        )

    def test_tv_launcher_and_private_service(self):
        """Keep the UI launchable on TV and the overlay service inaccessible externally."""
        root = ET.parse(MANIFEST).getroot()
        application = root.find("application")
        self.assertIsNotNone(application)
        services = {
            node.attrib[ANDROID + "name"]: node
            for node in application.findall("service")
        }
        overlay = services[".OverlayService"]
        self.assertEqual(overlay.attrib[ANDROID + "exported"], "false")
        self.assertEqual(overlay.attrib[ANDROID + "foregroundServiceType"], "specialUse")
        media_access = services[".MediaSessionAccessService"]
        self.assertEqual(media_access.attrib[ANDROID + "exported"], "false")
        self.assertEqual(
            media_access.attrib[ANDROID + "permission"],
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        )
        actions = {
            node.attrib[ANDROID + "name"]
            for node in media_access.findall("intent-filter/action")
        }
        self.assertIn(
            "android.service.notification.NotificationListenerService",
            actions,
        )
        activity = application.find("activity")
        categories = {
            element.attrib[ANDROID + "name"]
            for element in activity.findall("intent-filter/category")
        }
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", categories)
        features = {
            element.attrib[ANDROID + "name"]: element.attrib[ANDROID + "required"]
            for element in root.findall("uses-feature")
        }
        self.assertEqual(features["android.hardware.touchscreen"], "false")

    def test_commentary_transport_is_bounded_and_non_exported(self):
        """Pin the v0.3 message contract, image bounds and local renderer boundary."""
        server = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CommentaryServer.java").read_text()
        renderer = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java").read_text()
        self.assertIn("PORT = 8765", server)
        self.assertIn("MAX_BODY_BYTES = 3 * 1024 * 1024", server)
        self.assertIn("MAX_IMAGE_BYTES = 2 * 1024 * 1024", server)
        self.assertIn("MAX_RENDER_WIDTH = 1280", server)
        self.assertIn("MAX_RENDER_HEIGHT = 720", server)
        self.assertIn("scenevibe.commentary.v1", server)
        self.assertIn("scenevibe.commentary.ack.v1", server)
        self.assertIn("showCommentary", renderer)
        self.assertIn("ImageView", renderer)
        self.assertIn("media.dataBase64", server)
        self.assertIn("image/jpeg", server)
        self.assertIn("image/png", server)

    def test_latest_commentary_owns_expiry(self):
        """A replacement commentary must cancel the previous pending expiry."""
        renderer = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java").read_text()
        self.assertIn("private final Runnable commentaryExpiry", renderer)
        self.assertIn("handler.removeCallbacks(commentaryExpiry)", renderer)
        self.assertIn("handler.postDelayed(commentaryExpiry, durationMs)", renderer)
        self.assertNotIn("commentaryUntil", renderer)


    def test_mediasession_probe_is_passive_and_position_focused(self):
        """Read published playback state without sending media controls."""
        probe = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSessionProbe.java").read_text()
        listener = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSessionAccessService.java").read_text()
        self.assertIn("getActiveSessions", probe)
        self.assertIn("getPlaybackState", probe)
        self.assertIn("getPosition", probe)
        self.assertIn("getPlaybackSpeed", probe)
        self.assertIn("getLastPositionUpdateTime", probe)
        self.assertIn("SystemClock.elapsedRealtime", probe)
        self.assertNotIn("getTransportControls", probe)
        self.assertNotIn("dispatchMediaButtonEvent", probe)
        self.assertNotIn("onNotificationPosted", listener)

    def test_finaltrack_media_sender_keeps_asset_paths_external(self):
        """Resolve FinalTrack assetRef values through a separate sender-side asset map."""
        sender = (ROOT / "scripts/play-finaltrack-media.ps1").read_text()
        fixture = (ROOT / "examples/finaltrack-media-tv-poc.json").read_text()
        self.assertIn('schemaVersion -ne "1.1.0"', sender)
        self.assertIn("assetRef", sender)
        self.assertIn("AssetMapPath", sender)
        self.assertIn("asset-house-001", fixture)
        self.assertNotIn("C:\\Users\\DENIS", fixture)


    def test_mediasession_scheduler_loads_tracks_without_transport_control(self):
        """Drive bounded track events from passive playback snapshots only."""
        scheduler = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/MediaSyncedTrackScheduler.java").read_text()
        server = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CommentaryServer.java").read_text()
        parser = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/TrackParser.java").read_text()
        loader = (ROOT / "scripts/load-finaltrack-mediasession.ps1").read_text()
        self.assertIn("scenevibe.track.v1", parser)
        self.assertIn("TrackParser.parse(json", server)
        self.assertIn("scenevibe.track.ack.v1", server)
        self.assertIn("comments.length()>256", parser)
        self.assertIn("targetPackage", server)
        self.assertIn("FORWARD_SEEK_THRESHOLD_MS = 5000L", scheduler)
        self.assertIn("BACKWARD_SEEK_THRESHOLD_MS = 2000L", scheduler)
        self.assertIn("MAX_LATE_MS = 2000L", scheduler)
        self.assertIn("PlaybackState.STATE_PLAYING", scheduler)
        self.assertIn("COMMENT_DUE", scheduler)
        self.assertIn("MediaIdentityMatcher.matches", scheduler)
        self.assertIn("mediaIdentity", parser)
        self.assertNotIn("getTransportControls", scheduler)
        self.assertNotIn("dispatchMediaButtonEvent", scheduler)
        self.assertIn("/track", loader)
        self.assertIn("idealStartSec", loader)
        self.assertIn("TargetPackage", loader)
        self.assertIn("dataBase64", loader)
        self.assertNotIn("Stopwatch", loader)

    def test_lan_pairing_is_explicit_and_mutations_are_authenticated(self):
        """Prevent accidental regression to unauthenticated LAN writes."""
        java = ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc"
        server = (java / "CommentaryServer.java").read_text()
        policy = (java / "PairingPolicy.java").read_text()
        activity = (java / "MainActivity.java").read_text()
        self.assertIn('"/pair"', server)
        self.assertIn('pairing.authorized(headerValue(lines, "authorization"))', server)
        self.assertIn('"unauthorized"', server)
        self.assertIn('"0.6.0"', server)
        self.assertIn('"token"', server)
        self.assertIn("new byte[32]", policy)
        self.assertIn("MessageDigest.isEqual", policy)
        self.assertIn("MAX_FAILURES = 5", policy)
        self.assertIn("WINDOW_MS = 120_000L", policy)
        self.assertIn("this::resetPairing", activity)
        self.assertIn("Settings.ACTION_APPLICATION_SETTINGS", activity)
        for sender in ("load-finaltrack-mediasession.ps1", "send-media-commentary.ps1",
                       "play-finaltrack-media.ps1"):
            script = (ROOT / "scripts" / sender).read_text()
            self.assertIn('[Parameter(Mandatory=$true)][string]$Token', script)
            self.assertIn('Authorization = "Bearer $Token"', script)

    def test_cloud_client_uses_canonical_api_v1_and_not_the_obsolete_contract(self):
        """The outbound Cloud client must speak the canonical /api/v1 base, never /api/tv/v1."""
        client = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java").read_text()
        self.assertNotIn("/api/tv/v1/", client)
        self.assertIn("/api/v1/", client)

    def test_cloud_client_stays_outbound_only_with_no_inbound_receiver(self):
        """The Cloud client dials out over HTTPS only; there is no listening Cloud endpoint."""
        client = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CloudControlClient.java").read_text()
        self.assertIn("HttpsURLConnection", client)
        # No inbound socket/server surface for Cloud: the LAN receiver (port 8765) is the
        # only listener and lives in CommentaryServer, not in the outbound Cloud client.
        self.assertNotIn("ServerSocket", client)
        self.assertNotIn("BroadcastReceiver", client)
        self.assertNotIn("8765", client)

    def test_overlay_manifest_is_scene_data_and_renderer_stays_native(self):
        """Scene language must stay bounded data interpreted only by native Android primitives."""
        java = ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc"
        parser = (java / "OverlayManifestParser.java").read_text()
        renderer = (java / "SceneRenderer.java").read_text()
        model = (java / "OverlayManifest.java").read_text()
        # 0.10A runtime regie sources: the manifest is now actually interpreted at runtime, so
        # the Android-native + no-transport boundary must also hold over the new regie
        # controller and the cross-contract bridge.
        controller = (java / "SceneRuntimeController.java").read_text()
        bridge = (java / "VideoOverlayManifestBridge.java").read_text()

        self.assertIn("scenevibe.overlay-manifest.v1", model)
        for primitive in ("TEXT", "IMAGE", "RECTANGLE", "TABLE", "GROUP"):
            self.assertIn(primitive, model)
        self.assertIn("startMs", model)
        self.assertIn("durationMs", model)
        self.assertIn("CANVAS_WIDTH=1920", model)
        self.assertIn("TYPE_APPLICATION_OVERLAY", renderer)
        self.assertIn("TextView", renderer)
        self.assertIn("ImageView", renderer)
        self.assertIn("TableLayout", renderer)
        self.assertIn("FrameLayout", renderer)
        self.assertIn("GradientDrawable", renderer)
        self.assertIn("asset:", parser)

        for forbidden in (
            "Class.forName",
            "DexClassLoader",
            "PathClassLoader",
            "android.webkit",
            "WebView(",
            "java.net.URL",
            "HttpURLConnection",
        ):
            self.assertNotIn(forbidden, renderer)
            self.assertNotIn(forbidden, parser)
            # Extend the forbidden list to the new runtime regie sources so the Scene language
            # stays bounded data all the way through interpretation.
            self.assertNotIn(forbidden, controller)
            self.assertNotIn(forbidden, bridge)

        # The regie is Android-free orchestration: it never imports the scheduler clock as its
        # own and never reaches for the network, but it is still a separate interpreter class.
        self.assertIn("class SceneRuntimeController", controller)
        self.assertIn("class VideoOverlayManifestBridge", bridge)

    def test_scene_runtime_keeps_cloud_and_graphics_separated(self):
        """Section 22: the generic SceneRenderer / regie holds no Cloud logic, and the Cloud
        client holds no graphics logic; the legacy and scheduler/matcher seams all survive."""
        java = ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc"
        renderer = (java / "SceneRenderer.java").read_text()
        controller = (java / "SceneRuntimeController.java").read_text()
        client = (java / "CloudControlClient.java").read_text()

        # The temporal authority and media identity seams, plus the legacy overlay path, must
        # all remain present: the runtime regie is ADDED beside them, never a replacement.
        self.assertTrue((java / "MediaSyncedTrackScheduler.java").exists())
        self.assertTrue((java / "MediaIdentityMatcher.java").exists())
        self.assertTrue((java / "OverlayRenderer.java").exists())
        # The generic scene interpreter is a separate class from the legacy OverlayRenderer.
        self.assertTrue((java / "SceneRenderer.java").exists())
        self.assertIn("final class SceneRenderer", renderer)
        self.assertIn("final class OverlayRenderer",
                      (java / "OverlayRenderer.java").read_text())

        # SceneRenderer and the regie controller must carry NO Cloud logic: no cloud client,
        # no assignment/ACK handling, no device credential/token.
        for cloud_token in (
            "CloudControlClient",
            "CloudTrackRepository",
            "fetchAssignment",
            "deviceToken",
            "activationSecret",
            "/api/v1",
        ):
            self.assertNotIn(cloud_token, renderer)
            self.assertNotIn(cloud_token, controller)

        # CloudControlClient must carry NO graphics logic: no scene renderer, no Android View
        # containers, no drawable building. It only moves bytes.
        for graphics_token in (
            "SceneRenderer",
            "TableLayout",
            "FrameLayout",
            "GradientDrawable",
            "TextView",
            "ImageView",
            "WindowManager",
        ):
            self.assertNotIn(graphics_token, client)

    def test_scene_runtime_security_boundary_cases_33_to_37(self):
        """Section 20-F (cases 33-37): over the new + existing scene-runtime sources assert
        no network URL, no WebView, no dynamic class loading, no transport control, and that
        the AndroidManifest declares no new permission."""
        java = ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc"
        scene_sources = {
            name: (java / name).read_text()
            for name in (
                "SceneRenderer.java",
                "SceneRuntimeController.java",
                "VideoOverlayManifestBridge.java",
                "OverlayManifestParser.java",
                "OverlayManifest.java",
            )
        }

        # Case 33 (no network): the scene runtime never opens an HTTP(S) transport or a URL.
        # Case 34 (no WebView) + Case 35 (no dynamic class loading): no reflective/dex loaders
        # and no embedded web runtime anywhere on the scene path.
        # Case 36 (no transport control): the scene runtime never drives the media session.
        for name, source in scene_sources.items():
            for forbidden in (
                "java.net.URL",
                "HttpURLConnection",
                "HttpsURLConnection",
                "Socket",
                "android.webkit",
                "WebView",
                "Class.forName",
                "DexClassLoader",
                "PathClassLoader",
                "getTransportControls",
                "dispatchMediaButtonEvent",
            ):
                self.assertNotIn(forbidden, source, f"{forbidden} must not appear in {name}")

        # Case 37 (no new permission): the manifest permission set stays exactly the pinned
        # overlay / foreground / LAN / boot set. This mirrors the dedicated permission test and
        # keeps the scene-runtime cycle from smuggling in a new permission.
        root = ET.parse(MANIFEST).getroot()
        permissions = {node.attrib[ANDROID + "name"] for node in root.findall("uses-permission")}
        self.assertEqual(
            permissions,
            {
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
                "android.permission.RECEIVE_BOOT_COMPLETED",
            },
        )

    def test_track_pause_policy_reaches_renderer_without_player_controls(self):
        """Keep pause-aware display isolated from the direct commentary timer."""
        java = ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc"
        loader = (ROOT / "scripts/load-finaltrack-mediasession.ps1").read_text()
        service = (java / "OverlayService.java").read_text()
        renderer = (java / "OverlayRenderer.java").read_text()
        self.assertIn("playbackPolicy.pauseFreezesDisplay", loader)
        self.assertIn("showTrackedCommentary", service)
        self.assertIn("renderer.onPlayback(playing, freeze)", service)
        # "armed != visible" (FEAT-005): eligibility loss must reach the renderer to hide the
        # tracked card immediately. The service now only hides on ineligibility (and never
        # creates a renderer just to hide nothing), so it forwards onTrackEligibility(false).
        self.assertIn("renderer.onTrackEligibility(false)", service)
        self.assertIn("public void onTrackEligibility(boolean eligible)", renderer)
        self.assertIn("displayCountdown.update", renderer)
        self.assertIn("public void showCommentary(", renderer)
        self.assertNotIn("getTransportControls", service)

if __name__ == "__main__":
    unittest.main()
