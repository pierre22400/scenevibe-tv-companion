# Physical qualification — v0.5.0 MediaSession-synced FinalTrack

Date: 25 September 2026

Device under test: Sony Bravia Android TV / Google TV (physical device)

Streaming app under test: Prime Video

SceneVibe build: v0.5.0

## Scope

This qualification verifies the complete TV-side synchronization path:

`FinalTrack 1.1 -> sender-side assetRef resolution -> POST /track -> passive Prime Video MediaSession clock -> TV scheduler -> rich overlay`.

It does not generalize the result to every Android TV / Google TV model, firmware, or streaming application.

## Preconditions observed

- The SceneVibe overlay was running on the TV.
- `GET /health` returned:
  - `type: scenevibe.health.v1`
  - `status: ready`
  - `version: 0.5.0`
  - `media: inline-image`
  - `synchronization: media-session-clock`
- MediaSession access was user-granted in Android settings.
- The runtime track loader returned:
  - `type: scenevibe.track.ack.v1`
  - `status: loaded`
  - `trackId: tv-poc-finaltrack-media-001`
  - `targetPackage: com.amazon.amazonvideo.livingroom`
  - `commentCount: 3`

## Fixture

The FinalTrack 1.1 test fixture contains three events:

1. text-only commentary at 0 s;
2. image-backed commentary at 8 s;
3. text-only commentary at 18 s.

The image was resolved sender-side through an external asset map; no machine-local path was written into the canonical FinalTrack.

## Physical results

### Normal playback

PASS.

All three comments rendered at their expected media positions during normal Prime Video playback:

- approximately 0 s: text-only;
- approximately 8 s: image + text;
- approximately 18 s: text-only.

This verifies that the TV scheduler is following the Prime Video MediaSession clock rather than a sender-side stopwatch.

### Pause

PASS for scheduling.

While Prime Video was paused, no new due comment was triggered. Resuming playback resumed media-position-driven scheduling.

Observation: a comment already visible when pause was pressed kept its own renderer expiry timer and disappeared after its requested display duration. Therefore the scheduler freezes correctly, but the current renderer does not yet implement the FinalTrack `pauseFreezesDisplay: true` policy for an already visible card.

### Backward seek / replay

PASS.

Seeking backward re-armed comments at or after the new media position. The comments could be replayed repeatedly by revisiting their timeline positions.

### Forward seek

PASS.

A forward seek from before the 8 s event to beyond the 18 s event did not burst-render the crossed comments. The scheduler consumed the skipped events according to the v0.5 deterministic seek policy.

## Verdict

The complete v0.5.0 MediaSession-synced FinalTrack path is physically qualified on the tested Sony Bravia with Prime Video for:

- runtime FinalTrack loading;
- text and image commentary rendering;
- media-position-driven scheduling;
- pause-aware scheduling;
- backward-seek replay;
- forward-seek suppression of crossed comments.

Known limitation: `pauseFreezesDisplay: true` is not yet enforced for a commentary card that is already visible when playback is paused.
