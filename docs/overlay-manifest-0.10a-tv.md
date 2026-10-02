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

As of this cycle the Video manifest path is actually wired at runtime; see
"Runtime regie (now wired)" below for the end-to-end flow.

## Banner

Banner is no longer modelled as a special renderer id. It is another producer of
OverlayManifest scenes.

The first Cloud-side fixture is `fixtures/overlay/banner-sony-demo-001.json`. It uses wall-clock
timing and demonstrates group, rectangle, image, text and table.

The image uses only an opaque asset id. Until the asset cache/transport cycle lands, physical
Banner qualification can use the text/rectangle/table subset or pre-seeded local assets.

## Runtime regie (now wired)

The foundation parser/model/renderer boundary is now connected end-to-end. The manifest is no
longer merely parsed and cached: a manifested Video revision is interpreted at runtime by an
Android-free regie that drives `SceneRenderer`. The two visual paths are mutually exclusive per
comment, so a double overlay can never occur.

### Final architecture

```text
CloudControlClient (bytes only, no graphics)
   |  assignment { runtimeTrack [, overlayManifest] }
   v
ManifestInstaller seam (owned by OverlayService)
   |  atomic same-revision install + cross-contract validation
   v
VideoOverlayManifestBridge  --->  CloudTrackRepository (durable, atomic)
   |                                   |
   | arms the regie                    | loads the scheduler (sole clock)
   v                                   v
SceneRuntimeController (Android-free)  MediaSyncedTrackScheduler.Listener
   |  SceneSink seam (preflight/show/hide/hideAll)
   v
SceneRenderer (native Android primitives only)
```

- **Legacy Case A (no manifest).** An assignment with no `overlayManifest` behaves exactly as
  0.8.2: the scheduler drives the legacy `OverlayRenderer`. Nothing in that path changed.
- **Manifested Case B (Video manifest present).** The regie owns the visual. On each scheduler
  `onRender` event the controller looks up the scene whose id matches the event, preflights it and
  shows it through `SceneRenderer`. `OverlayService.isSceneRendererActiveFor(revision)` is the
  single deterministic switch the scheduler `Listener` consults, so Case A and Case B are never
  both visible for one comment ("exactly one visual path").

### Atomic same-revision cache

`CloudTrackRepository` persists the `runtimeTrack` and its `overlayManifest` together, under one
revision, as a single durable commit. A manifested revision is never half-stored: either both land
(and the scheduler is loaded) or the prior cache is left intact. `restoreWithManifest(scheduler)`
re-arms a durable manifested revision on process restart in the armed-not-visible state (the regie
holds the manifest but draws nothing until a comment is actually due).

### ACK boundary

The ACK means **the assignment was accepted durably**, NOT that a first scene was displayed. A
manifested revision is acknowledged only after the whole chain succeeds: bridge cross-contract
validation, atomic durable persist, scheduler accept, and regie accept. Any failure leaves the
prior cache intact, records a bounded diagnostic code, and the client does NOT ACK. The ACK
protocol fields themselves (`deviceId`, `revision`, `finalTrackId`) are unchanged.

### Cross-contract validation rules

A Video manifest carried on a FinalTrack assignment is accepted only when, at the envelope level
(`CloudProtocol.validAssignment`) and in `VideoOverlayManifestBridge`:

- `source.product = video`;
- `source.sourceId = runtimeTrack.trackId`;
- `clock.mode = media`;
- every manifest scene id maps to a `runtimeTrack` comment with matching timing, with no missing
  or extra scene.

A structural/contract failure records `MANIFEST_INVALID`; a `runtimeTrack`↔manifest mismatch
records `MANIFEST_INCONSISTENT`; a durable persistence failure records `MANIFEST_CACHE_FAILED`.

### Scene lifecycle

- **Window expiry (media-driven).** A shown Case B scene disappears at the end of its own window.
  When the scheduler renders a comment it arms a media-time window `startMs + durationMs`; once the
  MediaSession position reaches that end the scheduler fires `Listener.onExpire(event)` and the
  regie hides the scene via `onCommentExpired` under the generation guard. This is driven by the
  existing media clock, not a wall-clock timer: a pause (position does not advance) freezes the
  window so the qualified Video freeze behavior holds (section 12), a forward seek past the end
  expires the scene, and a backward seek before the start re-arms it. Case A (legacy
  `OverlayRenderer`) is unchanged: it self-expires via its own freeze-aware countdown and ignores
  `onExpire`.
- **Eligibility hide.** When the scheduler reports the media identity is no longer eligible, the
  regie hides the scene immediately (`hideAll`). The service never creates a renderer just to hide
  nothing.
- **Stale-callback neutralization.** The controller carries a generation guard: a callback from a
  superseded revision/scene is ignored, so a late async tick can never resurrect a dismissed scene.
- **Revision replacement.** Installing a new manifested revision replaces the armed manifest; the
  previous scene is dismissed and the new revision arms armed-not-visible. A newer LEGACY
  no-manifest revision takes the opposite transition explicitly: after its runtimeTrack is
  durable and loaded in the scheduler, the regie unloads the prior manifest, removes any
  SceneRenderer window immediately, transfers active visual ownership to the legacy revision,
  and only then permits ACK. Re-delivery of the cached legacy revision re-confirms that state.
- **Teardown.** `onDestroy`/reset unload the regie and `dismissNow()` so a stopped service leaves
  no overlay window behind.

## Limits deliberately NOT built this cycle

### Wall clock remains parsed, not executed (section 16)

`clock.mode = wall` is still parsed and the model/`SceneRenderer` stay Banner-compatible, and pure
JVM tests may use a Banner manifest. But there is **NO wall-clock execution in production** in this
cycle, because the exact `startMs` time anchor for a wall-clock scene is not specified: it is not
yet fixed whether `startMs` is an absolute epoch time, an offset from activation, an offset from
assignment, or another epoch.

> **Required future contract (do NOT invent here):** before any wall-clock scene executes in
> production, the `startMs` time anchor MUST be explicitly specified and agreed with the Cloud
> contract. This cycle deliberately does not invent it. Only media-clock (`clock.mode = media`)
> scenes execute in production today.

### Cloud asset transport is NOT built (section 15)

There is no download manager, CDN, HTTP prefetch or any network asset path. `SceneRenderer`
resolves images only through the local `AssetResolver` seam, which currently returns `null` for
every reference. A scene that needs an image whose asset does not resolve locally is suppressed as
a **bounded scene failure** (`SCENE_ASSET_UNAVAILABLE`) — never a crash, never a partial render,
and never a manifest inconsistency. A dedicated `SCENE_RENDER_FAILED` code covers an abandoned
draw. These scene codes are observational only and never gate logic.

### Priorities are NOT added (section 17)

No priority semantics are introduced in this cycle. The architecture is kept extensible so a
priority dimension can be layered on later without reworking the regie.

## Security invariants retained

- device token / account ownership / revocation unchanged;
- assignment revision and ACK unchanged;
- no arbitrary URL rendering;
- no WebView/Rive/Lottie dependency;
- no dynamic code/plugin loading;
- no player transport controls;
- no FinalTrack/LLM/pipeline changes;
- no new Android permission (the permission set is unchanged and pinned by the Python boundary
  test);
- applicationId / package / versionCode / versionName / FGS type / signing chain unchanged.

### Bounded diagnostics (section 19)

Diagnostics codes are observational only; they are never consulted to make a start, connect, ACK,
render, reset or scheduling decision, and they never carry comment/scene content, the full manifest
JSON, a stack trace, or any credential/token/PII. The bounded scene-runtime codes are:

- `SCENE_ASSET_UNAVAILABLE` — a required local asset did not resolve, so the show was suppressed;
- `SCENE_RENDER_FAILED` — a scene draw was abandoned before any partial overlay appeared.

A missing asset is a bounded SCENE failure (`SCENE_ASSET_UNAVAILABLE`), NOT a manifest
inconsistency.

## Boundary test coverage (section 22)

`tests/test_poc_contract.py` statically asserts, over both the existing and the new runtime regie
sources, that: `SceneRenderer` stays Android-native (no `WebView`, no network URL/HTTP, no dynamic
class loading); the new `SceneRuntimeController` and `VideoOverlayManifestBridge` carry the same
boundary; `MediaSyncedTrackScheduler`, `MediaIdentityMatcher` and the legacy `OverlayRenderer`
remain present; `SceneRenderer`/regie hold no Cloud logic; `CloudControlClient` holds no graphics
logic; and no new Android permission appears. Section 20-F security cases 33–37 (no network URL, no
WebView, no dynamic class loading, no transport control, no new permission) are enforced as static
checks over the scene-runtime sources.

## Gates executed in this cycle

Run locally with Gradle 8.9 / JDK 17 and the Android SDK:

- `python3 -m unittest discover -s tests` (Python boundary);
- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
- `SCENEVIBE_ENABLE_LAN_DEV=true :app:assembleDebug` (LAN DEV compile);
- `SCENEVIBE_CLOUD_ORIGIN=https://interface-scenevibe-3wxg.vercel.app :app:assembleDebug`
  (Consumer Cloud compile);
- best-effort `:app:assembleRelease` (unsigned; signing chain untouched).

## Physical qualification still required (sections 27 & 31)

No JVM or emulator run equals a physical Android TV qualification. An Android CI run (including any
api35 smoke) proves only the already-documented platform invariants; it does NOT prove an Android
TV 15/12 qualification. The physical Sony BRAVIA is Android TV 12. The following remain
HUMAN/PHYSICAL REQUIRED and must never be declared PASS from this sandbox:

- real Sony BRAVIA; APK install; real Cloud; real Prime media;
- a real Video OverlayManifest; a scene actually displayed; absence of double overlay;
- pause; resume; seek backward; seek forward; media change;
- immediate disappearance on ineligibility;
- hard reboot; cache/revision restoration;
- eventually, a first real Banner later (once the wall-clock anchor and asset transport exist).

## Merge ordering

This branch (`kiro/overlay-manifest-runtime-integration-0.10a-001`) is stacked on
`work/overlay-manifest-foundation-0.10a-tv-001`; its DRAFT PR targets that foundation branch, NOT
`main`. Do not merge it into `main` directly, and do not merge the foundation branch before 0.8.2
has passed its physical Sony gate and landed on `main`.

After 0.8.2 lands, the foundation branch retargets/rebases onto `main`, normal Android CI runs,
and the runtime regie is qualified together with the Cloud/contracts OverlayManifest work. The
previous foundation note referenced PR #11 / 0.8.2 as the blocking stable gate; that ordering is
preserved — the runtime integration simply stacks on top of the foundation rather than on `main`.

### CI note (temporary, section 26)

The debug workflow (`.github/workflows/android-debug.yml`) push trigger temporarily includes
`kiro/overlay-manifest-runtime-integration-0.10a-001` so debug CI runs for 0.10A runtime
integration. This is a temporary addition for this cycle only. The stable release workflow
(`android-release.yml`) and the api35 smoke workflow (`android-15-smoke.yml`) are untouched, no
secrets are modified or exposed, and no other trigger is broadened.
