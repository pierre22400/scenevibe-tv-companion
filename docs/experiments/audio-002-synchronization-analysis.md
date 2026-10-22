# SceneVibe TV Companion POC - KIRO AUDIO 002 - Synchronization analysis

**Status: SOFTWARE analysis only. AUDIO 002 is NOT PHYSICALLY QUALIFIED. No physical sync precision is claimed from JVM tests.**

> This analysis separates **three categories** and never mixes them:
>
> 1. **THEORETICAL delays** - reasoned bounds derived from the design and the known cadence. Not measured anywhere.
> 2. **SOFTWARE measurements** - what the JVM timing hooks and tests actually observed in this sandbox. These are **sample-index** and ordering facts, never wall-clock milliseconds on hardware.
> 3. **STILL-NEEDED physical measurements** - what only a Sony device can establish, deferred to `audio-002-sony-handoff.md`.
>
> A green JVM test is **SOFTWARE PROOF ONLY**; it is never a physical Sony PASS. The A1 coexistence result is a separate **HISTORICAL PHYSICAL PASS A1**, not a product of this analysis.

## 1. Real precision available in the probe snapshots

The sole time authority is the observed media position, sampled passively. From `MediaSessionProbe` (unchanged by AUDIO 002):

- **Sampler cadence = 1000 ms** (`SAMPLE_INTERVAL_MS = 1000L`). A new `Snapshot` is forwarded once per second via `handler.postDelayed`.
- Each `Snapshot` carries `positionMs`, `estimatedPositionMs`, `speed`, and `updateAgeMs`.
- `estimatedPositionMs` interpolates between samples while playing:
  `estimatedPositionMs = max(0, positionMs + round(updateAgeMs * speed))`, applied only in `STATE_PLAYING` / `FAST_FORWARDING` / `REWINDING`; otherwise the raw `positionMs` is used.

**Consequence (THEORETICAL).** The scheduler learns of a crossed `startMs` boundary at the next 1 s tick, with the interpolation correcting for the age of the underlying `PlaybackState` sample. The quantum of position knowledge is therefore the **1 s sampling cadence**, partially compensated by the `speed * updateAge` interpolation. This is a property of the passive probe, not something AUDIO 002 changed.

## 2. How AUDIO events are triggered by the existing scheduler

AUDIO is driven by the **same** callback stream TEXT consumes; no second clock and no second probe were added (verified: no `Handler`/`postDelayed`/`Timer`/`Thread`/`SystemClock` in the audio package).

- As the observed position advances in `STATE_PLAYING`, the scheduler emits `onDue(token, eventId)` when a comment's window becomes due. `SceneVibeAudioRenderer.onDue` then issues `prepare` (unless pre-prepared) + `setGain` + `start`.
- Seek handling stays entirely in the scheduler using the verified `main` constants `FORWARD_SEEK_THRESHOLD_MS = 5000`, `BACKWARD_SEEK_THRESHOLD_MS = 2000`, `MAX_LATE_MS = 2000`. A forward seek past a window consumes it without an `onDue` (no stale comment). A backward seek re-arms and expires the visible window first, then re-emits `onDue`. The renderer re-implements none of this.
- Because AUDIO and TEXT share the one media clock, an AUDIO comment and a TEXT comment at the same `startMs` are evaluated at the **same media instant**; there is no independent audio timeline that could drift.

## 3. Software-measured delays (SOFTWARE measurements)

These are the only numbers this document asserts, and they are **sample-index / ordering facts** from the JVM timing hooks, not hardware latencies.

The renderer exposes `sampleCount()`, `prepareSample()`, `startSample()`, advanced by `tick()` (one tick models one 1 s sample; it drives no transition). `timingHooksExposePrepAndStartSamples` observed:

- `tick()` → `prePrepare("c1")` → `tick()` → `tick()` → `onDue(..., "c1")` yields `sampleCount() == 3`, `prepareSample() == 1`, `startSample() == 3`.
- Interpretation: **prepare happened two samples before start**, i.e. pre-preparation moved the player-prep work off the start path. In sample units that is a **2-sample** head start; with the probe's 1 s cadence that corresponds to roughly 2 s of lead available for the preparation to complete before audible start. This is a **model** figure in sample units, not a measured device latency.

Ordering facts proven by the matrix tests (SOFTWARE):

- `prepare` strictly precedes `start` on the normal path (`prepareOrderingAlwaysPrecedesStart`): the recorded port call sequence is `prepare:scenevibe_cue.aac`, `setGain:0.8`, `start`.
- Pre-preparation records exactly `prepare:scenevibe_cue.aac` with `startCount == 0` and then starts on the subsequent `onDue` **without a second prepare** (`prePrepareReadiesWithoutSound`): one `prepareCount`, one `startCount`.
- A repeated `onDue` for the active comment never restarts it (`repeatedDueDoesNotRestart`: `startCount == 1`), so there is no spurious re-trigger latency.

**What these numbers are NOT.** They do not express a sampling latency, a `MediaPlayer.prepare()` duration, or an audio-start delay in milliseconds on a Sony. The JVM has no `MediaPlayer` and no real decoder; the hooks count model samples. **No physical sync precision is claimed from these tests.**

## 4. Pre-preparation approach and its measured effect

- **Design (section 6):** `prePrepare(eventId)` lets the renderer `prepare` the local resource ahead of its due instant without emitting sound (`prepare != start`), so the `MediaPlayer.prepare()` cost is paid before the comment is due rather than on the start path.
- **SOFTWARE-measured effect:** with pre-preparation, `onDue` performs only `setGain` + `start` (no `prepare`), proven by `prePrepareReadiesWithoutSound` (one prepare total, start without a second prepare) and by the `prepareSample() < startSample()` gap in the timing-hooks test. Side-effect-free audibly: `startCount == 0` after `prePrepare`.
- **THEORETICAL benefit:** removing prepare from the start path should reduce the time between the due media instant and audible start by the device's `MediaPlayer.prepare()` cost for a short bundled ADTS/AAC cue. The actual magnitude is a **STILL-NEEDED physical measurement**.

## 5. Cadence-improvement proposal

**No cadence change is proposed.** The 1 s `SAMPLE_INTERVAL_MS` is the qualified TEXT cadence; the mandate requires that AUDIO stay TEXT-neutral and not introduce a second clock. The interpolation (`positionMs + round(updateAgeMs * speed)`) already compensates for sample age within the playing state, and pre-preparation already removes the dominant start-path cost in the model. Reducing the cadence would touch a path shared with the qualified TEXT engine and must not be done on the strength of JVM evidence alone. Any future proposal must be justified by **physical** measurement (category 3) and must preserve the TEXT path; it is explicitly out of scope for AUDIO 002.

## 6. Three categories, summarized

### 6.1. THEORETICAL delays (reasoned, not measured)

- Position knowledge is quantised to the 1 s sampling cadence, partly offset by `speed * updateAge` interpolation.
- The worst-case delay between a crossed `startMs` and the scheduler's `onDue` is bounded by one sampling quantum plus any device-side `MediaPlayer` start cost not removed by pre-preparation.
- Pre-preparation is expected to remove the `prepare()` cost from the audible-start path.

### 6.2. SOFTWARE measurements (JVM, this sandbox - sample-index/ordering only)

- `prepare` strictly precedes `start`; the port call order is `prepare → setGain → start`.
- With `prePrepare`, start happens with no second prepare; timing hooks show `prepareSample=1`, `startSample=3`, `sampleCount=3` (a 2-sample lead).
- No duplicate triggers (`startCount == 1` on repeated/concurrent due).
- These are software facts; they state nothing in device milliseconds.

### 6.3. STILL-NEEDED physical measurements (Sony, deferred)

- The real `MediaPlayer.prepare()` and audible-start latency on the Sony for the bundled ADTS/AAC cue.
- The real delay between the media instant and the audible comment onset (and whether pre-preparation reduces it in practice).
- Whether the comment is actually audible while Prime's sound stays audible and Prime stays PLAYING (coexistence - this is the A1 risk, physical only).
- Behaviour across the full matrix (pause/resume/forward-seek/backward-seek/episode-change/AUDIO-OFF) observed on hardware.

These remain **NOT PHYSICALLY QUALIFIED (AUDIO 002)** and are specified for execution in `audio-002-sony-handoff.md`.
