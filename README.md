# RegiaOBS

**English** · [Italiano](README.it.md)

A native Android broadcast controller and Macro Deck for **OBS Studio** (`obs-websocket` v5, OBS 28–33+), built with Kotlin and Jetpack Compose.

Control your entire OBS setup from your phone or tablet over local Wi-Fi or VPN — no PC companion software required.

---

## Features

- **Broadcast Console (`Console`)**
  - Live Program and Studio Mode Preview video monitors (configurable 1–8 FPS refresh).
  - Full **Studio Mode** switching: tap a scene for Preview, long-press to cut directly to Program, switch transition type & duration (`ms`), trigger transitions, or slide the interactive **T-Bar**.
  - Output transport strip: **Stream (LIVE)**, **Record** (with Pause/Resume and Chapter markers), **Replay Buffer** (with instant Save Clip), and **Virtual Camera**, with optional confirmation guards before stopping live outputs.
  - Quick per-scene source visibility and lock toggles.
- **Customizable Macro Deck (`Deck`)**
  - Stream Deck–style multi-page pad grid with live status LEDs, custom colors, and Material icons.
  - Multi-step macro builder supporting 19 action types (scene switches, transitions, outputs, audio mute/dB, source visibility, filters, media controls, live text updates, browser cache refresh, hotkeys, profiles, scene collections, and delays).
  - **Automatic OBS Sync**: maps starter pads to your real OBS special audio inputs, scenes, and browser sources (works across any OBS UI language), plus one-tap **Generate page from OBS Scenes** and JSON export/import.
- **Audio Mixer Pro (`Mixer`)**
  - Hardware-style **Vertical Fader Console** or compact **Horizontal List** with OBS cubic deflection curve and exact dB readouts.
  - 20 Hz Canvas **VU meters** with segmented green/amber/red levels and peak hold (zero layout recomposition).
  - **Live Hardware Device Switcher**: change Microphone (`device_id`), Desktop Speakers, or per-window capture targets directly from your phone via `Switch Mic` / `Switch Speaker` or the per-channel inspector.
  - Monitoring mode (`Off` / `Mon` / `M+O`), audio sync offset (`ms`), L/R stereo balance, and audio filter toggles.
- **Sources, Media & Transform (`Sources`)**
  - Inspect any scene, reorder layers, expand groups, toggle filters, edit Text / Browser Source settings live, refresh browser caches, and nudge/scale/rotate source transforms.
  - Inline **Media Player** bar with Play/Pause/Restart/Stop and scrub bar.
- **System, Telemetry & Fast Pairing (`System` & `Connect`)**
  - **Offline QR Scanner** (CameraX + ZXing) for OBS WebSocket connection QR codes (`obsws://...`) and **Wi-Fi LAN Scanner** on port `4455`.
  - Live telemetry (CPU, active FPS, frame render time, dropped frames, RAM, free disk space), Profile & Scene Collection switching, searchable OBS Hotkeys, and an advanced raw WebSocket v5 command console.
  - Built-in **Demo Mode (Simulator)** to explore and configure Macro Decks offline.

---

## OBS Studio Setup

OBS Studio 28+ includes `obs-websocket` v5 out of the box:

1. On your PC, open **OBS Studio**.
2. Go to **Tools → WebSocket Server Settings**.
3. Check **Enable WebSocket server** (default port `4455`).
4. Click **Show Connect Info** to display the QR Code.
5. Open **RegiaOBS** on Android (on the same Wi-Fi network or VPN) and tap **Scan OBS QR** or **Scan Wi-Fi**.

---

## Install or Build from Source

### Install Prebuilt APK
Download the latest APK from [GitHub Releases](https://github.com/RidiConClaudio/regia-obs/releases), or install over USB with ADB:

```bash
adb install -r RegiaOBS-v1.0.0-debug.apk
```

### Build with Gradle
Requires **JDK 17+** and **Android SDK API 35** (`minSdk = 26`, `targetSdk = 35`):

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## Project Structure

- `app/src/main/java/com/regiaobs/app/obs/` — obs-websocket v5 client, SHA-256 auth, URI parser, cubic audio math, LAN scanner, and offline Demo simulator
- `app/src/main/java/com/regiaobs/app/macro/` — multi-step Macro Engine, JSON export/import, and automatic OBS target sync
- `app/src/main/java/com/regiaobs/app/data/` — DataStore persistence for server profiles, deck pages, and app settings
- `app/src/main/java/com/regiaobs/app/service/` — persistent foreground connection notification service
- `app/src/main/java/com/regiaobs/app/ui/` — Jetpack Compose UI (`connect`, `console`, `deck`, `mixer`, `sources`, `system`)
- `app/src/test/java/com/regiaobs/app/` — JVM unit tests (`ObsProtocolTest`, `AudioCurveTest`, `MacroEngineTest`)

---

## License

[MIT](LICENSE)
