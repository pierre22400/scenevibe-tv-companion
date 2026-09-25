# SceneVibe TV LAN pairing protocol v1 (v0.6.0 POC)

This document specifies the receiver for a future "Send to TV" client. The
implementation listens on `0.0.0.0:8765` only while the user-started overlay
foreground service runs. HTTP/1.1, JSON UTF-8, one request per connection;
`Connection: close`, `Cache-Control: no-store`. It uses **plain HTTP**, with no
TLS, NSD, account identity or certificate exchange. Use a trusted local network.
An attacker who can observe or modify LAN traffic can steal the code or token.

## TV consent and lifecycle

1. Grant Android "Display over other apps" and start the overlay. MediaSession
   synchronization also requires a separate user grant of Notification access.
   On the tested Sony Bravia enter through **Settings → Apps → Special app access
   → Notification access → SceneVibe**; the direct notification settings deep
   link traps remote focus on that firmware.
2. User explicitly selects **Start pairing** in the Companion. The TV shows a
   uniformly generated six-digit decimal code for 120 seconds, counted with
   elapsed time. No startup or service restart opens pairing automatically.
   Starting again invalidates the prior code. The fifth incorrect well-formed
   code closes the window; a new TV action is needed.
3. Sender POSTs `/pair` during the open window. A successful exchange creates
   a fresh 32-byte SecureRandom token, encoded Base64URL without padding, and
   invalidates the code. Only one client token is active. A new successful
   pairing replaces the earlier token. The deviceId is a random installation
   identifier saved by the app, not a hardware ID or MAC.
4. The token persists in app-private SharedPreferences across service stops and
   process restarts. The open code exists only in process memory. **Reset
   pairing** removes the token and code, including while the service runs;
   uninstalling the app also removes them. The TV logs neither code nor token.

## HTTP endpoints

| Request | Auth | Success | Description |
| --- | --- | --- | --- |
| `GET /health` | Public | 200 | `scenevibe.health.v1`, ready, version `0.6.0`, protocolVersion `"1"`, pairingRequired `true`, paired boolean, media `inline-image`, synchronization `media-session-clock`. No deviceId/code/token. |
| `POST /pair` | Code during TV window | 200 | `scenevibe.pair.ack.v1`, status `paired`, deviceId, token. |
| `POST /commentary` | `Authorization: Bearer <token>` | 200 | `scenevibe.commentary.ack.v1`, id, status `rendered`, mediaRendered boolean. |
| `POST /track` | Same Bearer | 200 | `scenevibe.track.ack.v1`, trackId, status `loaded`, targetPackage, commentCount. |

For `/pair`, JSON request:

~~~json
{"type":"scenevibe.pair.request.v1","code":"123456","clientName":"SceneVibe development sender"}
~~~

`code` must be exactly six ASCII decimal digits. `clientName` is required,
nonblank and at most 80 characters. The receiver does not store a client name.
The first five invalid code outcomes are `invalid_code` four times, then
`too_many_attempts`; malformed requests yield `invalid_pair_request` without
counting as a code guess. A closed or expired pairing window yields
`pairing_closed`.

For `/commentary`, JSON request `type: scenevibe.commentary.v1`, nonempty `id`
(≤128 chars), `text` (≤1000 chars), `durationMs` (1000–60000, default 10000).
Optional `media` is `{kind:"image",mimeType:"image/jpeg"|"image/png",
dataBase64:"..."}`. The receiver validates image bytes, bounds decoded
images to 2 MiB and downsamples to a 1280×720 working envelope.

For `/track`, runtime JSON request `type: scenevibe.track.v1`, `trackId`
(1–128 chars), `targetPackage` (1–200 chars), `comments` (1–256 unique IDs),
optional `pauseFreezesDisplay` boolean (default false). Each comment has
`id` (1–128), `text` (1–1000), `startMs` (0–43200000), `durationMs`
(1000–60000) and optional inline `media` in the above format. The development
loader converts canonical FinalTrack 1.1 `schedule.idealStartSec` to `startMs`,
resolves assetRef outside the canonical track, and projects
`playbackPolicy.pauseFreezesDisplay` to the runtime boolean. The receiver does
not change the canonical FinalTrack or command playback. The existing TV
MediaSession scheduler preserves 5s forward seek, 2s backward seek and 2s
lateness thresholds. If enabled, pause suspends an already displayed track
card's remaining time; direct `/commentary` cards use wall elapsed duration.

## Errors and bounds

All errors use `{ "type":"scenevibe.error.v1", "code":"...",
"message":"..." }`. Body size is 1–3 MiB; headers max 8192 bytes; decoded
image max 2 MiB. No request body is read for unauthorized mutations.

| Status | Codes | Meaning |
| --- | --- | --- |
| 400 | `bad_request`, `invalid_json` | Incomplete body or malformed JSON. |
| 401 | `unauthorized`, `invalid_code` | Missing/wrong/revoked Bearer token, or an incorrect TV code. |
| 404 | `not_found` | Unknown route/method. |
| 409 | `pairing_closed` | No active TV window or it expired. |
| 413 | `invalid_length` | Missing, zero or excessive Content-Length. |
| 422 | `invalid_type`, `invalid_pair_request`, `invalid_track`, `invalid_track_comment`, `invalid_commentary`, media errors | Invalid semantic payload. |
| 429 | `too_many_attempts` | Fifth wrong code; TV must reopen pairing. |

Do not send tokens in URL query strings; keep them out of logs and committed
files. `paired` in health indicates an active token, including when a new
pairing window is open. An authenticated old client may write until a new
successful pairing or Reset. This POC has no token expiry or remote revoke API.

## PowerShell qualification example

~~~powershell
$tvIp = "192.168.1.183" # Example only; use TV-displayed IPv4
$token = .\scripts\pair-tv.ps1 -TvIp $tvIp -Code "123456" # Use current TV code
Invoke-RestMethod -Uri "http://${tvIp}:8765/health"
.\scripts\load-finaltrack-mediasession.ps1 -TvIp $tvIp -Token $token `
  -TrackPath ".\examples\finaltrack-media-tv-poc.json" `
  -AssetMapPath ".\examples\asset-map.json"
~~~

The asset map is a local path mapping to a real image. Keep the returned
token only in the sender process; the pairing script writes no token file.
After Reset pairing, reusing `$token` against either mutating endpoint must
yield HTTP 401. Network eavesdropping and physical streaming behavior remain
outside automatic qualification.
