# SceneVibe TV 0.10A — OverlayManifest scene foundation

## Scope

This branch is stacked on the still-pending 0.8.2 release-hardening branch. It must not alter the
stable-signed 0.8.2 APK that will be physically qualified on the Sony.

0.10A starts the architectural transition from a commentary-specific overlay renderer to a
generic local graphical and temporal regie.

## Companion role

SceneVibe Companion remains responsible for:

- TV identity and pairing;
- Cloud communication;
- assignment and ACK lifecycle;
- local validation/cache;
- timing and priorities;
- media synchronisation when required;
- handing validated scenes to the graphical renderer.

The existing Android overlay foundation is extended, not rewritten.

## OverlayManifest is a scene language

`scenevibe.overlay-manifest.v1` describes:

- a logical 1920 × 1080 canvas;
- product/source identity;
- media or wall clock;
- pause behaviour;
- timed scenes;
- graphical primitives and layout/style.

Initial primitive family:

- text
- image
- rectangle
- table
- group

The contract contains no renderer class name or executable plugin reference.

## Parser

`OverlayManifestParser` is a strict, fail-closed parser. It bounds:

- scene and primitive counts;
- recursive group depth;
- x/y/width/height inside the parent coordinate space;
- zIndex and opacity;
- text/table sizes;
- colors;
- fade animation durations;
- asset references.

Image refs must be opaque `asset:...` identifiers. Direct network URLs fail validation.

## SceneRenderer

`SceneRenderer` is the first generic Android interpreter. It uses only native Android primitives:

- FrameLayout
- TextView
- ImageView
- TableLayout / TableRow
- View + GradientDrawable
- TYPE_APPLICATION_OVERLAY

It scales the logical canvas to the TV display, applies position/z-order/opacity, supports bounded
fade entry/exit and recursively renders groups.

The renderer never evaluates HTML/JavaScript, never dynamically loads classes and never performs
network requests.

Images come through an `AssetResolver` local-cache seam. Asset transport/download is a later
bounded cycle; the current text-only Cloud qualification remains intact.

## Video migration

The existing FinalTrack/runtime path remains valid while the generic scene path is introduced.

For the 0.10A transition:

```text
FinalTrack
   |\
   | \----> scenevibe.track.v1 -> MediaSyncedTrackScheduler
   |
   +-------> OverlayManifest -> SceneRenderer
```

`scenevibe.track.v1` is not removed in this cycle. MediaIdentityMatcher and
MediaSyncedTrackScheduler remain untouched.

A Video manifest carried on the current FinalTrack assignment must:

- have `source.product = video`;
- have `source.sourceId = runtimeTrack.trackId`;
- use `clock.mode = media`.

Legacy 0.8.2 assignments with no manifest remain accepted.

## Banner

Banner is no longer modelled as a special renderer id. It is another producer of
OverlayManifest scenes.

The first Cloud-side fixture is `fixtures/overlay/banner-sony-demo-001.json`. It uses wall-clock
timing and demonstrates group, rectangle, image, text and table.

The image uses only an opaque asset id. Until the asset cache/transport cycle lands, physical
Banner qualification can use the text/rectangle/table subset or pre-seeded local assets.

## What is deliberately not wired yet

This foundation creates the parser/model/renderer boundary but does not yet replace the existing
OverlayRenderer path inside OverlayService. That hand-off must happen after 0.8.2 has passed the
Sony stable-signing gate so the qualified release is not contaminated.

The next TV-side step is to cache the validated manifest alongside the assignment revision and let
the regie select the scene due at a scheduler event / wall-clock tick before invoking
SceneRenderer.

## Security invariants retained

- device token / account ownership / revocation unchanged;
- assignment revision and ACK unchanged;
- no arbitrary URL rendering;
- no WebView/Rive/Lottie dependency;
- no dynamic code/plugin loading;
- no player transport controls;
- no FinalTrack/LLM/pipeline changes.

## Merge ordering

Do not merge this branch before PR #11 / 0.8.2 has passed its physical Sony gate and landed on
`main`.

After that, retarget/rebase this PR onto `main`, run normal Android CI, then qualify the scene
path together with the Cloud/contracts OverlayManifest PR.
