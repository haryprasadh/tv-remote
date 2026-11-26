# TV Remote v1 — design

Date: 2026-10-18 · Status: approved by Hari (after 3 spec-review passes). Next: implementation plan.

## Goal

A family phone remote for the OnePlus Y Series TV that connects by itself (no pairing code, no OK
on the TV, no Google sign-in) and puts no extra load on the TV. All work happens on the phone.

Done when, on Hari's Nothing Phone (2a) and one parent's Android phone:
- Opening the app with the TV on shows "Connected" in about 1 s, with no pairing code, no prompt
  on the TV and no sign-in. (Expected one-time Android prompts: notification permission on first
  launch on Android 13+, microphone on the first 🎤 tap. Nothing else.)
- Every v1 feature below works against the real TV.
- Nothing new is installed or running on the TV.

## Context

- TV: OnePlus Y Series (Skyworth, MediaTek m5621, Android TV 11, 925 MB RAM), 2.4 GHz Wi-Fi,
  host `Android.local` / 192.168.1.2 (DHCP), MAC a0:91:a2:a6:0f:4f, `network_wol=1`.
- TV already runs Google's Android TV Remote Service v2 (`com.google.android.tv.remote.service`,
  ~11 MB): mDNS `_androidtvremote2._tcp`, control port 6466, pairing port 6467.
- Family switches the TV off at the wall at night (cold boot each morning). Remote off/on in the
  day is sleep/resume.
- TV setup and history: `tv-vibehack/docs/discussions/oneplus-y-series-exploration.md`.
- Phones: all Android. Hari: Nothing Phone (2a).
- Reference protocol implementation: `tronikos/androidtvremote2` (Python, Apache-2.0); its
  `.proto` files (polo + remotemessage) are the wire format.

## Approach (chosen)

Speak Android TV Remote protocol v2 from the phone. Pair **once from the Mac** with a script that
reads the 6-digit code off the TV via adb, and embed the resulting trusted client certificate in
the app. Every phone with the app is trusted with no per-phone pairing.

Rejected: adb-over-Wi-Fi from the phone (each key spawns a Java process on the TV, ~0.3–0.5 s CPU;
exposes debug port); TV-side helper app (cannot inject D-pad keys without system permission; costs
TV RAM). A TV-side helper may come later for TV-display features.

## Components

### 1. Pairing tool (Mac) — `pairing/pair.py`
- Python venv with `androidtvremote2`. Generates `client_cert.pem` / `client_key.pem` if missing,
  calls start pairing, reads the code from the TV with `adb shell uiautomator dump` (fallback:
  prompt), finishes pairing, verifies with a connect.
- The pairing code is 6 hex characters (0-9, A-F); parse it as such from the dump.
- Writes the client cert/key as a PKCS#12 file and the TV's server-certificate SHA-256
  fingerprint to `app/src/main/assets/` (gitignored). Never committed.
- Re-run whenever the TV is reset or the remote service data is cleared.

### 2. Phone app (Kotlin, Jetpack Compose, minSdk 26, targetSdk 35)

| Unit | Job | Depends on |
|---|---|---|
| `protocol` | Protobuf messages, varint length framing, TLS client with embedded PKCS#12 cert (server cert pinned by fingerprint), key/text/app-link encoders, ping replies. Replies to RemoteConfigure / RemoteSetActive advertise the feature bitmask PING, KEY, IME, POWER, VOLUME, APP_LINK; leaving one out makes that feature fail silently. Pure Kotlin, no Android UI. | protobuf-javalite |
| `connection` | Find the TV (last-known IP and mDNS in parallel; re-resolve via mDNS after a wake because DHCP may change the IP), connect, keep alive, automatic reconnect (never wakes the TV), Wake-on-LAN only on user actions, expose the states in "Connection states" below plus TV info (is on, volume level). | protocol |
| `remote` | Actions the UI calls: `press(key)`, `hold(key)` / `release`, `type(text)`, `enter()`, `openApp(pkgOrLink)`, `power()`. Volume is changed only by VOLUME_UP/DOWN key presses (the protocol reports level but has no absolute set). | connection |
| `ui` | Three tabs: Remote / Apps / Type, plus status bar (connection state + power). | remote, connection |
| `share` | Share-sheet target (ACTION_SEND text/plain) that maps URLs to TV app links. | remote |
| `background` | Foreground service (type `connectedDevice`; manifest needs FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, CHANGE_WIFI_MULTICAST_STATE, all install-time) holding the connection, notification remote, MediaSession with remote VolumeProvider, Quick Settings tile (added by the user from the edit-tiles panel, no add-tile prompt). | connection, remote |

Each unit is usable through its interface without reading its internals; `protocol` has no Android
dependencies.

### 3. TV
No change.

## Data flow

0. Home-network check (no permission needed; re-run on every network change): the phone is on
   Wi-Fi (ConnectivityManager) and its gateway + subnet (LinkProperties) match those saved at the
   last successful connect. First run has nothing saved. A mismatch **only blocks Wake-on-LAN**:
   the app still tries the last IP and mDNS, and any successful connect re-saves gateway + subnet
   (so a router swap fixes itself).
1. A **user action** → `Searching`: try the last-known IP and mDNS `_androidtvremote2._tcp` in
   parallel. User actions are: opening the app, tapping the tile, pressing Power with no live link
   (any state except `Connected` / `TvStandby`), Retry, and "Use here". A Power or tile tap with
   no live link means **turn on**: step 1, then POWER only if the TV reports `started=false`;
   never POWER-off on the same tap.
2. Port 6466 doesn't answer → if step 0 matched, send Wake-on-LAN to the TV MAC → `Waking`,
   re-resolve via mDNS, retry for up to ~40 s → still nothing → `Asleep` if mDNS sees the TV,
   else `Off`.
3. TLS handshake with the embedded client cert → TV sends `RemoteConfigure` → app replies →
   `RemoteSetActive` → TV sends `RemoteStart` (started). `started=true` → `Connected`;
   `started=false` (reachable but in standby) → `TvStandby`.
4. While connected: answer `RemotePingRequest`; read `RemoteImeShowRequest` / batch-edit counters
   (needed to send text, along with the TV field's current text) and volume level updates.
5. UI actions → `remote` → encoded messages on the open socket.
6. Link closes → classify, **first match wins**: `NeedsRepair` (TLS/auth rejected) → phone's
   Wi-Fi down → `NotHomeWifi` → `Asleep` → `InUseElsewhere` → `Reconnecting`.
7. **Automatic connects never send Wake-on-LAN** (reconnect backoff, `Off`'s mDNS listen, a
   network change that makes step 0 match). So turning the TV off can't make the app turn it
   back on. An automatic connect ends in `Connected`, `TvStandby`, `Asleep` or `Off`.
8. The MediaSession (phone volume keys) is active only in `Connected` and `TvStandby`.

## Connection states

| State | When | Shown | Power button / tile | Auto-retry |
|---|---|---|---|---|
| `Searching` | after a user action, before an answer | "Finding TV…" | — | — |
| `Waking` | WoL sent, waiting | "Waking TV…" | — | yes, ~40 s |
| `Connected` | `started=true` | "Connected" | sends POWER (TV off) | — |
| `TvStandby` | connected, `started=false` | "TV is in standby" | sends POWER (TV on) | — |
| `Asleep` | link closed within ~5 s of the app sending POWER-off, or after the TV reported `started=false`, or retries ended while mDNS still sees the TV | "TV is asleep" | turn on (step 1) | no |
| `Reconnecting` | link closed for any other reason while the phone's Wi-Fi is up | "Reconnecting…" bar, other buttons disabled | turn on (step 1, cancels the backoff) | yes, backoff for ~30 s, no WoL → then `Asleep` or `Off` |
| `Off` | nothing answers and mDNS doesn't see the TV | "TV is off. Switch it on at the wall" + Retry | turn on (step 1) | slow mDNS listen while the app is open; once the TV appears, keep retrying until port 6466 answers (a cold boot takes ~1 min) |
| `NotHomeWifi` | phone's Wi-Fi is down, or step 0 mismatched and the connect failed | "Can't reach the TV. Are you on home Wi-Fi?" | turn on (step 1) | automatic connect (no WoL) when a network change makes step 0 match or Wi-Fi returns |
| `InUseElsewhere` | only if open question 2 confirms the TV is single-client, the TV had not reported `started=false`, the app didn't just send POWER, and port 6466 still accepts a plain TCP connect right after the close | "In use on another phone" + "Use here" | take control, no POWER | no; only explicit actions take control back (Use here, opening the app, the tile), never notification buttons or volume keys |
| `NeedsRepair` | TLS/auth rejected | "TV needs re-pairing" | — | no |

## Features (v1)

**Remote tab:** D-pad, OK, Back, Home, Play/Pause, Vol −/+, Mute, Power. Holding a D-pad key
sends START_LONG/END_LONG so it scrolls. Haptic tick per press. Volume bar shows the TV's level.

**Apps tab:** tiles for Netflix (`com.netflix.ninja`), Prime Video
(`com.amazon.amazonvideo.livingroom`), YouTube (`com.google.android.youtube.tv`), Hotstar
(`in.startv.hotstar`), YouTube Music (`com.google.android.youtube.tvmusic`), opened via
`market://launch?id=<pkg>`. List defined in one place for easy edits. All five package names
verified installed on the TV on 2026-10-18.

**Type tab:** text field mirrored live into the TV's focused field (IME batch edit), Enter key,
🎤 button using the phone's SpeechRecognizer (prefer offline) → inserted as text. When the TV
reports its keyboard opened, the app switches to the Type tab, pre-fills the field with the TV
field's current text and focuses it. When no TV text field is focused, the field is disabled with
the hint "Open a search box on the TV first". The TV's keyboard is Gboard (verified); confirm
batch edits land in it.

**Share to TV:** YouTube URLs (`youtube.com/watch`, `youtu.be`, shorts) → app link
`https://www.youtube.com/watch?v=<id>`; Netflix/Prime/Hotstar URLs → passed through as app links;
anything else → toast "TV can't open this link". Verify each mapping on the real TV.

**Outside the app:** foreground-service notification with Play/Pause, Vol −, Vol +, Mute, Power;
MediaSession with remote volume (VolumeProvider, session active with a playing state) so phone
volume keys control TV volume even when locked; Quick Settings tile "TV" (opens remote and acts
like the Power button per "Connection states"). Service runs while the app is open and for 15 min after last
use, then stops. Volume keys while locked depend on the phone's Android build, so verify them on
the Nothing Phone early (open question 4).

## Security

- Embedded client cert/key and the APK signing keystore stay out of git (gitignored paths) and are
  never published. The APK is shared only within the family.
- Anyone with the APK on the home Wi-Fi can control the TV. Revoke all phones by clearing the TV
  remote service data (`pm clear com.google.android.tv.remote.service`) and re-pairing.

## Testing

Manual checks on the real TV + Hari's phone (no automated tests, by choice), one checklist per
feature: connect from cold boot, from sleep (WoL), reconnect after Wi-Fi drop, every Remote button,
hold-to-scroll, each app tile, live typing + Enter, voice to text, auto-switch to Type, each share
mapping, notification buttons, volume keys while locked, tile, 15-min auto-stop, two phones at once,
off home Wi-Fi. On a parent's phone at least: connect, notification remote, volume keys (phone
makers' battery savers may stop background services). Also confirm TV memory and CPU are
unchanged while the app is connected.

## Distribution

- Hari's phone: install from the Mac via wireless debugging (one-time enable), `adb install -r`.
- Parents' phones: send the APK over WhatsApp, allow "install unknown apps" once.
- Updates: same signing key so new APKs install over old ones.

## Open questions to settle during the build

1. Does Wake-on-LAN wake this TV from sleep, and how long until port 6466 answers?
2. Does the TV accept two clients with the same certificate at the same time? If not, does it send
   anything distinctive when another client takes over (to tell a takeover from a ping timeout)?
3. Which share URLs each TV app accepts as app links.
4. Do phone volume keys reach the remote-volume MediaSession while locked on the Nothing Phone
   (Android 14/15 restrictions on remote sessions)? If not, volume keys work only with the app open.

## Later (separate designs)

Send phone photos/videos to the TV (Cast), touchpad mode, Telugu labels, family shortcuts
("Bedtime": TV off in 30 min), TV-side helper for display features, in-app updates.
