# Geneo Clock Overlay

A floating clock + period-change announcer for Geneo smart boards that:
- Sits **on top of every app** (system overlay), not inside any one app
- Shows **HH:MM**, updating every minute
- Is **draggable** anywhere on screen with a finger/stylus
- Shows **black text on a white translucent pill** for consistent readability
- **Auto-starts** every time the board boots, after one-time setup
- Announces period changes: a **popup + 2-second alarm sound** reading
  **"This period is over, its N period now"** (or the slot's name, for
  Diary Checking / Extra Class) the moment each period ends
- The daily schedule (period times) is **editable from the app's UI** — no
  rebuild needed to change times

## Why this ships as a project, not a ready .apk

An `.apk` has to be compiled and cryptographically signed by a real Android build
toolchain. I don't have one available here to hand you a finished binary. What's in
this folder is the complete, working source — turning it into an installable `.apk`
takes about 2 minutes with free tools, no coding required from you.

## Build the APK (pick one)

### Option A — GitHub Actions (least internet use on your side)
All the heavy downloading (Gradle, Android SDK, dependencies — a few GB) happens on
GitHub's free cloud servers, not your connection. You only upload this small folder
and later download the finished ~5 MB APK. No git install needed.

1. Go to [github.com](https://github.com) → sign in (free account) → **New repository**
   (any name, e.g. `geneo-clock`, Public or Private, don't add a README).
2. On the new repo's page, click **"uploading an existing file"** (or `Add file → Upload files`).
3. Drag in every file/folder from this `GeneoClockOverlay` folder (keep the folder
   structure — including the hidden `.github` folder; reveal hidden files in your file
   manager first, or use "Create new file" and type the full path
   `.github/workflows/build.yml` if the folder gets skipped), then **Commit changes**.
4. Click the **Actions** tab at the top of the repo → you'll see "Build APK" running
   automatically (takes ~2–3 minutes).
5. When it finishes (green check), click into that run → scroll to **Artifacts** →
   download **GeneoClockOverlay-debug-apk** → unzip it to get `app-debug.apk`.

Re-run any time by uploading new/changed files — it rebuilds automatically on every
upload, or you can hit **Run workflow** in the Actions tab manually.

### Option B — Android Studio (easiest locally, biggest download)
1. Install [Android Studio](https://developer.android.com/studio) (free).
2. `File → Open` → select this `GeneoClockOverlay` folder.
3. Let it sync (first sync downloads the Android SDK bits it needs).
4. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
5. Click the "locate" link in the notification, or find it at
   `app/build/outputs/apk/debug/app-debug.apk`.

### Option C — Command line (if you already have the Android SDK + JDK 17)
```
cd GeneoClockOverlay
gradle assembleDebug
```
The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Install it on the Geneo board
1. Copy `app-debug.apk` to the board via USB drive, or `adb install app-debug.apk`
   if the board has USB debugging / ADB enabled.
2. On the board: Settings → allow install from this source (or "Unknown apps") if
   prompted, then open the APK file to install.

## One-time setup on the board (about 30 seconds)
1. Open **Geneo Clock Overlay** from the app drawer.
2. Tap **Grant overlay permission** → allow "display over other apps" for this app.
3. Tap **Keep clock running in background** → exclude it from battery optimization
   (prevents the board's OEM software from killing it after inactivity).
4. Tap **Start clock now**.
5. Tap **Test Popup Now** to confirm the popup + alarm sound work on this device.
6. Optionally tap **Edit Schedule** to adjust period times to match your actual day.

That's it — from now on the clock reappears by itself every time the board powers on,
with no need to open the app again.

## Using it
- Drag the clock pill anywhere on the screen; its position is remembered even after
  a reboot.
- It stays visible over any app running on the board.
- The default daily schedule is: Period 1–4 (before lunch), Period 5–8 (after lunch),
  Diary Checking (13:15–13:30), Extra Class (13:30–14:15). Same schedule every day —
  no per-day timetable.
- When a period's end time is reached, a popup + 2-second alarm sound announces the
  next slot: **"This period is over, its 5 period now"** for numbered periods, or by
  name for Diary Checking / Extra Class.
- To change any time, open the app → **Edit Schedule** → edit a slot's start/end
  (4-digit 24-hour, no colon — e.g. `0835` for 8:35 AM, `1315` for 1:15 PM) → **Save
  Schedule**. Takes effect immediately, no reinstall needed. **Reset to defaults**
  restores the original times.
- **Test Popup Now** on the setup screen fires the popup + alarm instantly, useful
  for confirming sound/permissions work without waiting for a real period boundary.

## Project layout
```
app/src/main/java/com/geneo/clockoverlay/
  MainActivity.kt              – setup screen (permissions, start/stop, test popup, link to editor)
  ClockOverlayService.kt       – floating clock: draw, drag, tick, and the period-end popup + alarm
  ScheduleEditorActivity.kt    – full-screen editor: edit each slot's start/end time
  BootReceiver.kt              – restarts the clock automatically on every boot
  Prefs.kt                     – position + daily schedule storage, defaults
app/src/main/res/
  layout/                      – setup screen, clock overlay, popup, schedule editor
  drawable/                    – clock pill background, app icon
  values/                      – strings, theme
```
