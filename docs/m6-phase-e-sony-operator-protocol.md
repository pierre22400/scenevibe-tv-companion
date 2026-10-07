# M6 Phase E — Sony operator protocol

**Physical qualification: NOT EXECUTED.** API31/API35 emulators and JVM/HTTP evidence do not constitute Sony evidence. The target remains the existing paired Sony BRAVIA Android TV 12, Cloud device `a5e45f36-95af-41ab-841b-da596482436d`. Production stays SHADOW and both PRs stay OPEN/DRAFT/unmerged.

The two real historical bootstrap writes are complete. Do not repeat sealing or ACK reconciliation, recreate the publication, reallocate revision 15, reset, re-pair, uninstall, or edit the qualification DB manually.

## One immediate operator action

Open **Diagnostics on the currently installed Sony app** and record its current baseline: app version, Installation ID, Cloud device ID, installed/cached/acknowledged revisions, permissions, pairing and service/Cloud state. This is the sole next action, before an upgrade. Current physical values remain unobserved by WORK. Do not record tokens, secrets or content bodies.

The gated artifact `scenevibe-os-m6-phase-e-sony` contains the exact APK, `candidate.json`, its SHA-256, stable certificate, non-debuggable badging and software gate. Require version `0.8.3-m6-phase-e` / code 13, package `com.scenevibe.tvcompanionpoc`, certificate SHA-256 `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`, PACKAGE_V1, WALL capability true, LAN DEV false and the immutable isolated Preview origin in that manifest.

## Later phases — one at a time after reviewing the preceding evidence

No first real M6 Send until all software prerequisites are explicitly PASS and Sony upgrade/startup observations are reviewed. Every unexpected identity, revision, permission, ACK, callback or overlay is a STOP; no reset or pairing repair is implied.

| Phase | Controlled operation | Required observed evidence | Current state |
| --- | --- | --- | --- |
| A — Upgrade | Install the verified APK over the historical app, without uninstalling. | Compare the captured baseline: same Installation ID, Cloud device ID, permissions, pairing and durable history. Verify exact version and signer. | NOT EXECUTED |
| B — Startup | Start the service using the existing normal entry. | Cloud connection, named package build, one client, clean bounded diagnostics, historical Video restore, no new Send/ACK/revision. | NOT EXECUTED |
| C — Video PACKAGE_V1 | A later explicitly controlled account Send of the existing owned Video. | New revision N, install/readback/restore/ARM before exact ACK; normal comments; seek/pause/replay retain M5 behavior. | NOT EXECUTED |
| D — Video→Banner | Later Send Banner at N+1 with a future controlled window. | Video retired; exact ACK N+1; Banner independent of MediaSession; no residual Video comment. | NOT EXECUTED |
| E — Window | Observe before start, during the window and after end. | Invisible before, visible during, removed at end; no resurrection or replay of a passed window. Record actual timestamps. | NOT EXECUTED |
| F — Reboot Banner | Reboot with the durable Banner, without another Send. | Durable restore, fresh WALL anchor; only remaining current window may show; passed window invisible; revision/ACK retained; no stale callback. | NOT EXECUTED |
| G — Cloud interruption | Temporarily interrupt connectivity if it can be done without disturbing the fixture. | Durable Banner continues to horizon; reconnect without reset/re-pairing or duplicate display. | NOT EXECUTED |
| H — Banner→Video | Later Send Video N+2. | Banner removed immediately, Video alone, exact Video mirror and ACK N+2; no fallback to old Video N. | NOT EXECUTED |
| I — Service stop/start | Stop then start the service normally. | No stale callback or orphan overlay; exact durable restore. | NOT EXECUTED |
| J — Final reboot | Reboot after return to Video. | Video N+2 restored, no resurrected Banner, coherent installed/cached/ACK revision. | NOT EXECUTED |

For each relevant phase record the available bounded Diagnostics fields: app version, Installation ID, Cloud device ID, service/Cloud state, installed and durable revision, acknowledged revision, last assignment and successful ACK, codec/handler, startup restore, WALL anchored/wait/generation and last outcome. Record a selected event or next boundary only if the existing surface actually displays it. A field that is unavailable remains **NOT OBSERVED**. Logcat is complementary evidence; do not infer an internal transition from an unrelated line.

Sony PASS requires actual completion of A–J on the exact identified candidate. Software PASS and the historical database parity do not satisfy that physical verdict.
