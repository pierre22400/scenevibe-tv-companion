# SceneVibe TV 0.10A — OverlayManifest routing seam

## Scope

This branch is stacked on the still-pending 0.8.2 release-hardening branch so it cannot alter the
stable-signed 0.8.2 APK that will be physically qualified on the Sony.

0.10A adds only the TV-side routing seam for the additive
`scenevibe.overlay-manifest.v1` contract.

## Compatibility rule

Two assignment generations are accepted:

1. **Legacy 0.9D/0.8.2 assignment** — no `overlayManifest`: route explicitly as
   `LEGACY_COMMENTARY`.
2. **0.10A assignment** — manifest present: it must match the allow-listed commentary mapping.

A present-but-invalid manifest never falls back to legacy mode. It fails closed as a protocol
error, so a future or malicious renderer identifier can never be interpreted as commentary.

## Allow-listed 0.10A mapping

- manifest type: `scenevibe.overlay-manifest.v1`
- surface: `system_overlay`
- renderer: `scenevibe.renderer.commentary.v1`
- payload contract: `scenevibe.track.v1`
- payload ref: `assignment.runtimeTrack`
- payload id: must equal `runtimeTrack.trackId`

No URL is resolved, no class name is loaded dynamically and no renderer code is downloaded.

## Runtime behavior

`OverlayManifestRouter.resolve(...)` returns the already-present sibling `runtimeTrack`. The
existing `CloudTrackRepository`, `TrackParser`, `MediaSyncedTrackScheduler` and
`OverlayRenderer` path then runs unchanged.

The manifest is not cached as a new source of truth. For the current commentary renderer, the
durable cache remains the already-qualified runtime track + revision/ACK state.

## Explicitly unchanged

- `scenevibe.track.v1`
- FinalTrack
- cloud revision / ACK rules
- device authentication / pairing / revocation
- `MediaIdentityMatcher`
- `MediaSyncedTrackScheduler`
- `OverlayRenderer` visual behavior
- boot/autostart behavior
- stable signing
- Banner implementation

## Tests

Pure JVM tests lock:

- legacy assignment routing;
- canonical manifest routing;
- unknown renderer rejection;
- arbitrary payload-ref rejection;
- payload-id mismatch rejection;
- malformed manifest rejection;
- CloudProtocol acceptance of both legacy and canonical manifested assignments.

## Merge ordering

Do not merge this branch before PR #11 / 0.8.2 has passed its physical Sony gate and landed on
`main`. After that merge, retarget/rebase this PR onto `main` and run the standard Android CI.
