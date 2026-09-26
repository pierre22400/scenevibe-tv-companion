# Physical qualification — v0.6.0 pairing, authenticated LAN and pause-visible timer

Date: 25 September 2026

Device under test: Sony Bravia Android TV / Google TV (physical device)

Streaming app under test for synchronized playback: Prime Video

SceneVibe build: v0.6.0, branch `work/tv-pairing-authenticated-lan-001`

Qualified commit before this documentation commit: `d3047da3a53773c8e4a8c54276589c3f570c7962`

## Scope

This record covers the v0.6 deltas that were exercised on the physical TV:

- explicit TV-initiated LAN pairing;
- Bearer-token enforcement for a mutating endpoint;
- authenticated direct commentary rendering;
- authenticated runtime track loading;
- `pauseFreezesDisplay: true` for a card that is already visible when Prime Video is paused;
- the Sony-safe manual route for MediaSession notification access.

It does not generalize the result to every Android TV / Google TV model, firmware, streaming application or LAN environment.

## Installation and permissions

The v0.6 debug APK could not update the previously installed build because the debug signatures differed (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The old package was uninstalled, then the v0.6 APK installed successfully.

On the TV:

- Display over other apps reported granted.
- MediaSession access was re-granted through the Sony-safe route:
  Settings -> Apps -> Special app access -> Notification access -> SceneVibe.
- Returning to SceneVibe reported the required permissions granted.
- The Apps settings route remained navigable with the physical remote.

## Pairing

PASS for the exercised happy path.

The user selected Start pairing on the TV and a six-digit code was displayed. A PC on the same LAN POSTed `scenevibe.pair.request.v1` to `/pair`.

Observed acknowledgement:

- `type: scenevibe.pair.ack.v1`
- `status: paired`
- a deviceId was returned;
- a Base64URL token was returned with length 43 characters;
- the TV state changed to paired.

The 120-second expiry, five-wrong-attempt closure and token replacement paths were not physically exercised in this run; they remain covered by automated tests rather than this physical record.

## Authentication gate

PASS for the exercised mutation path.

A POST to `/commentary` without a Bearer token returned HTTP 401 and nothing appeared on the TV.

The same endpoint with the paired token and a valid commentary payload returned:

- `type: scenevibe.commentary.ack.v1`
- `status: rendered`
- `mediaRendered: false`

The commentary was visibly rendered on the TV.

Reset pairing / old-token revocation was not physically exercised in this run.

## Runtime track loading

PASS.

An authenticated `scenevibe.track.v1` pause-freeze fixture was POSTed to `/track`.

Observed acknowledgement:

- `type: scenevibe.track.ack.v1`
- `trackId: tv-poc-v060-pause-freeze`
- `status: loaded`
- `targetPackage: com.amazon.amazonvideo.livingroom`
- `commentCount: 2`

This fixture was a direct runtime-track qualification payload. It was not a new canonical FinalTrack contract.

## Prime Video pause-visible timer

PASS.

The first test card was scheduled near 8 seconds of Prime Video media time with a nominal 10-second display duration and `pauseFreezesDisplay: true`.

Physical sequence:

1. Prime Video played until the card appeared.
2. Playback was paused while the card was visible.
3. The TV remained paused for more than 15 seconds, longer than the card's full nominal duration.
4. The card remained visible throughout the pause.
5. Playback resumed.
6. The card remained visible for its remaining unconsumed display time, then disappeared normally.

This closes the v0.5 limitation where an already-visible card continued to expire on wall time while playback was paused.

## Verdict

The v0.6.0 changes are physically qualified on the tested Sony Bravia for the exercised paths:

- explicit pairing happy path;
- unauthenticated mutation rejection;
- authenticated commentary rendering;
- authenticated runtime track loading;
- Sony-safe MediaSession access navigation;
- pause-aware lifetime of an already-visible synchronized Prime Video commentary card.

Not physically exercised in this v0.6 run:

- Reset pairing and reuse of the old token;
- five invalid pairing attempts and pairing-window expiry;
- token replacement by a later pairing;
- v0.6 image rendering;
- v0.6 backward/forward seek regression checks;
- Netflix / Disney+ v0.6 playback behavior.

Those omissions do not invalidate the specific v0.6 deltas above, but they must not be represented as physically qualified by this record.
