# RoohuX — v0.4.0 (download-engine repair + detection fix + Ember refresh)

## READ FIRST — honest status
This session had **no Android SDK, no Gradle, no Kotlin compiler, no network, no emulator/device**.
- **Built successfully:** NO. Nothing was compiled. No APK was produced here. The GitHub Actions workflow is the first real build.
- **Unit-tested:** NO — tests were written but **never executed**.
- **Device-tested:** NO.
- What I did run: XML + workflow-YAML parsing, brace/paren balance and internal-import resolution over all 55 Kotlin files (only the generated `R` is unresolved, as expected), and a raster preview of the new icon.
- The codebase was already marked "never compiled" in v0.3.x, so compile errors from earlier sessions may also exist. If the first Actions run fails, send me the first error lines.

## Implemented (written, NOT verified by running)
### Detection (root causes found)
- **The JS→Kotlin bridge never existed.** The old page script dispatched an event that called `window.__roohuxNativeScan`, which was never defined, and no `addJavascriptInterface` was registered. So "scan on play / source change" never reached native code; only 7 timed scans after page load worked. Now: `MediaBridge` (`@JavascriptInterface report(json)`, bounded string only) + `WATCHER_JS` injected on page commit and finish. It reports `<video>/<audio>` `currentSrc`/`src`, child `<source>`, same-origin iframes, EME use and recent resource URLs on play/loadstart/loadedmetadata/durationchange/canplay/emptied, DOM `src` mutations, and a 3 s safety net while the page is visible.
- Single-page sites: `doUpdateVisitedHistory` resets the media list and re-arms the watcher when the address changes without a page load. Reports from a page we already left are ignored.
- Duplicates: one entry per URL ignoring `#fragment`.
- Chip: shown for any detected media (labelled "Download media" for direct files, "Media detected" for streams/blob/DRM so the user isn't left guessing), 48 dp touch target, re-appears if new downloadable media shows up after a dismissal.
- Server Content-Type (after probing) overrides page-based guesses: extensionless URLs that are HLS/DASH are shown as unsupported; HTML responses hide the Download button.
- Ordinary `<a>` links are never treated as media (only element sources, observed requests, and resource entries are classified).

### Download engine
- New pure `RangePolicy` decides 200 / 206 / 416: validates `Content-Range` start, end, total and body length; a 200 to a Range request restarts from byte 0 (never appends); 416 with matching size publishes, otherwise discards and restarts. Clean restarts are immediate and don't consume the retry budget (max 2).
- **Extension bug:** extensionless URLs produced files with no extension that other apps couldn't open. `FileNames.ensureExtension` now completes missing / server-script extensions (`.php`, `.aspx`, …) from a specific MIME type; ordinary extensions chosen by the server are never overridden.
- **Zombie-download race:** a download cancelled/paused while queued could still start. `DownloadDao.claim()` atomically moves QUEUED→DOWNLOADING; the engine returns if it loses.
- gzip-encoded responses (despite `Accept-Encoding: identity`) are decompressed and treated as non-resumable/unverified instead of saving compressed bytes.
- Free-space check before publishing (partial kept so Retry can publish later).
- `blob:`/`data:`/other schemes are refused with a clear message instead of creating a doomed entry.

### UI / branding
- New launcher icon "Play-drop" (down-pointing play triangle on a tray; adaptive background/foreground/monochrome, round variant), matching splash mark, "Ember" accent (amber→ember) used in progress, empty state and icon.
- Downloads screen: filter chips (All / Active / Completed / Failed with counts), status badge per card (Waiting, Downloading, Paused, Done, Failed, Cancelled), ember progress bar with accessibility description, designed empty state. Existing actions kept: pause (only when the server supports it), resume, cancel, retry, open, share, locate, delete with confirmation; speed/ETA shown only when computable.
- `network_security_config.xml` (cleartext allowed for direct downloads; system CAs only; TLS errors never bypassed). `enableOnBackInvokedCallback` enabled.
- Workflow: unit tests run first with `continue-on-error`, APK always built/validated/uploaded, test report artifact uploaded, run turned red afterwards if tests failed; read-only permissions; concurrency group.

### Tests added (never executed)
`RangePolicyTest` (13), `FileNameExtensionTest` (6), `MediaDetectionTest` (7) + existing suites. Added `org.json:json` as a test dependency (android.jar stubs would otherwise throw).

## NOT done from the brief (be aware)
A full redesign of every screen was **not** done. Home, Library, Settings, About, Player, Launch and the browser chrome (address bar, menus, tabs/bookmarks sheets) keep the v0.3.x layout with only the new palette/icon applied. Not done: light/dark audit of every screen, reduced-motion audit of new code, new icon family for toolbar/nav (existing Material icons remain), download-details screen, onboarding, skeleton loaders, font change (Poppins kept, bundled locally).
Not implemented, by design/limits: HLS/DASH downloading, blob stream capture, DRM (never bypassed), authenticated sites beyond browser cookies, parallel segmented downloads.

## Known limitations / risks
- Detection sees only what WebView exposes; cross-origin iframes can't be scanned by the page script (their requests are still observed natively).
- `addJavascriptInterface` is visible to all frames; the bridge accepts one bounded string and re-classifies natively, so a hostile page can at most add fake entries for itself.
- Publishing copies the finished file into MediaStore (two copies briefly).
- MediaProvider may rename a file if extension and MIME disagree; the stored name is read back and displayed.
- Android 15 `dataSync` foreground time limit pauses downloads cleanly; resume is manual.

## Manual test checklist (device)
1. Page with an MP4: press play → chip appears within ~1 s; change episode (SPA) → list resets and updates.
2. Extensionless signed media URL → saved with the right extension, opens.
3. Pause→Resume byte continuity; server without Range → restart, no corruption; kill network mid-download → retries then Failed → Retry continues.
4. Cancel a queued and an active download → nothing keeps running (notification disappears).
5. HLS page → labelled unsupported; blob page → explanation; DRM page → DRM notice.
6. Low storage → "not enough free storage", partial kept.
7. Filter chips on Downloads; delete with confirmation; Open/Share.
8. Install, check launcher + themed icon (Android 13+), splash.

## Build
Push to GitHub → Actions → **Build debug APK** → artifact `RoohuX-debug-apk` (`app/build/outputs/apk/debug/app-debug.apk`). JDK 17, Gradle 8.9 (installed by the workflow), AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 29.

---

# RoohuX — v0.3.1 additions (still NOT compiled or run; no CI/GitHub access in this session)

**New launch screen:** after the system splash (same background/mark, no flash) a Compose launch screen draws the ring, springs the play mark in, fades in the wordmark, and shows a thin progress line tied to REAL start-up steps ("Loading settings" → "Opening library" → "Ready"). It dismisses as soon as those finish, subject to a 0.9 s minimum so the animation can play; with Reduce Motion (or system animations off) it skips the wait.

**Filenames / MIME:** the name from Content-Disposition (else the final URL) is kept as-is with its original extension; unsafe characters are still replaced; **no extension is added from the content type any more**. MIME is stored separately (server Content-Type, or guessed from the extension only when the server says octet-stream) and passed to MediaStore. The stored name is read back from MediaStore after saving. *Unverified risk:* Android's MediaProvider may rename a file in some cases when extension and MIME disagree; the card shows whatever name Android actually stored.

**Download card (per your layout):** name, `VIDEO/MP4` line, progress bar + %, `750 MB / 1 GB · speed · ETA`, expandable **File details**: file size, format (from extension, MIME subtype only if no extension), content type, source website (referring page if known, else serving host), resolution (only if the filename contains e.g. 1080p / 1920x1080 / 4K, marked `*Filename hint only`), downloaded, remaining, resume support (Supported / Not supported / Not known yet). Pause shows only when the server supports it. Unit tests added for the helpers.

**Video player rebuilt:** own controls (stock controller off, so gestures never conflict). Swipe left half = brightness, right half = volume (system media volume), horizontal swipe = seek with on-screen preview, double-tap = ∓10 s, tap = show/hide. Controls: ±10 s, seek bar with times, speed chip (0.25x–3x), Fit/Fill/Stretch, Landscape/Portrait, Lock, **Settings sheet** (brightness and volume sliders, speed presets, aspect ratio), **Tracks** dialog for audio/subtitle tracks (only shown when there is something to choose). Screen stays awake while watching; brightness is handed back on exit. No sleep timer on video.

**Still not done:** long-press 2x, pinch zoom, picture-in-picture, remembering speed per video, brightness/volume HUD for hardware volume keys, thumbnails/grid in the library, skeleton loaders, instrumented tests.

# RoohuX — Development Progress (v0.3.0, redesign pass)

## READ FIRST — what this package is and is not
- **Base:** this is my own v0.2.0 project (the ZIP from earlier in this conversation) plus the v0.3.0 changes below. I had **no GitHub access, no network, no CI logs** in this session, so I could **not** inspect `roohukhan01-design/roohu_x`, the commits you listed (6ee6ae5…, fb56390…, 22f3f76…, 932d083…) or workflow run 40088071526.
- The mini-player and sleep timer described in your prompt were **not** written by me in this conversation; I implemented both **from scratch** here against my own code. If your repo has its own versions, **do not overwrite blindly**: diff first. The reusable, mostly self-contained pieces are `core/design/*`, `features/player/PlayerConnection.kt`, `PlaybackHistory.kt`, `MiniPlayer.kt`, `PlayerScreen.kt`, `features/settings/AboutScreen.kt`, `res/font/*`, and the resource changes.
- **Not compiled, not run, no build claimed.** Only static checks were run (XML parse; brace balance; internal-import resolution over 46 Kotlin files). No workflow URL / commit SHA exists to report.

## Done in v0.3.0 (written, unverified)
**Design system:** bundled Poppins (SIL OFL; Latin; ~630 KB) with a full Material 3 type scale (display/headline/title/body/label); `Spacing` 4/8dp tokens; shapes; new palette (near-black + one warm amber accent, charcoal surface tiers, warm light theme); calmer glass cards, hologram toned down; consistent screen titles; styled nav bar.
**Launch:** new ring+play mark (adaptive icon, monochrome layer, splash icon), window/splash background matches theme (no white flash); splash stays only until saved settings actually load (capped at 1.5 s), no fake delay.
**Home:** hero, search/browse entry, **Continue** (real playback history only), quick actions, downloads/recent downloads/recent sites shown only when they exist (fake stats removed).
**Player:** one shared `PlayerConnection` (single MediaController, connect onStart / release onStop). Reopening the same item does **not** reload or restart it. Resume position saved locally and restored (not if <5 s in or within 10 s of the end). Video: auto-hiding controls, ±10 s, speed/audio/subtitle via Media3, Fit/Fill, landscape toggle, pauses when you leave; **no sleep timer on video**. Audio: now-playing layout (no fabricated art), seek bar with elapsed/remaining, ±10 s, speed, repeat-one, **sleep timer (audio only; cleared on any new item)**. Friendly errors (missing file, network, unsupported format) with Retry.
**Mini-player:** audio only, hidden for video, state read from the shared connection, tap to expand, play/pause, close; never starts or resets media.
**Other:** Autoplay setting, Clear playback history, About screen (developer Roohu, support email with Copy/Email actions, privacy principles, licence acknowledgements), https/not-secure indicator in the browser address bar, library storage summary, downloads header with saved total.
**Tests added (not executed):** resume policy, history codec, upsert/cap.

## Previously (v0.2.0) — unchanged, still unverified
Download engine fixes (416/200/206, If-Range, integrity check, duplicate prevention, cookie jar, Android 15 FGS timeout), browser with tabs/bookmarks/history/find/desktop mode/fullscreen video/popups, media detection, library, Reduce Motion. See git history of this file in the earlier ZIP for details.

## NOT done from your prompt (be aware)
Skeleton/shimmer loaders, onboarding, double-tap seek and brightness/volume gestures, PiP, audio queue/playlists/shuffle, library grid view / thumbnails / rename / folders, batch download management, charging-only policy, configurable download folder, incognito mode, address suggestions, homepage choice, tracker blocking, file-upload chooser, per-site permissions, accent-colour choice, string resources (UI text is still hardcoded in composables), instrumented/UI tests, lint run, Android 11-specific testing (minSdk is 29 / Android 10).

## Install
Push the project to GitHub → Actions → **Build debug APK** → download artifact `RoohuX-debug-apk` → unzip on your phone → open `app-debug.apk` → allow "install unknown apps" for your browser/file manager. If the run fails, send me the log text of the first error.

## Build
Push to GitHub → Actions → **Build debug APK** → artifact `RoohuX-debug-apk`. The workflow assembles the debug APK, verifies it
exists/unzips/has package `com.roohu.roohux`, uploads it (fails if missing), then runs unit tests (so a failing test can't hide the APK).
No Gradle wrapper jar is committed; the workflow installs Gradle 8.9. JDK 17, AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 29.

## Priority 1 — fixes made to the Phase 1–2 code (all by code review; none verified by running)
- HTTP 416: if the server says the file is exactly what we already have, publish it instead of restarting; otherwise discard the partial and retry clean.
- HTTP 200 to a Range request (server ignored it): partial discarded, download restarts from byte 0 (no appended/corrupt file).
- HTTP 206: `Content-Range` start must equal our offset, else restart.
- `If-Range` (ETag / Last-Modified) sent on resume so a changed remote file can't be spliced into a stale partial.
- Size integrity check before publishing (mismatch → INTEGRITY error, partial removed so Retry is clean).
- Duplicate prevention: the same URL can't be queued twice while queued/running/paused. Duplicate filenames are handled by MediaStore ("name (1).ext").
- Cookies are sent per-host via a read-only WebView cookie jar (correct across redirects; nothing stored or logged).
- Android 15 `dataSync` foreground-service timeout handled: downloads pause cleanly and can be resumed.
- Foreground-service start failures no longer crash the service; Android 13+ notification permission requested on first download.
- `configChanges` on the activity so rotation (fullscreen video/player) doesn't recreate the UI.

## Implemented (written, unverified)
| Area | What exists |
|---|---|
| Downloads | Queue, N simultaneous, progress/speed/ETA, pause/resume (Range only), retry with backoff, cancel, delete, open/share/locate, notifications, MediaStore `Downloads/RoohuX`, plain-language errors |
| Browser | Multiple tabs (kept alive across app sections), address bar with URL-vs-search detection (DuckDuckGo/Google/Bing), back/forward/reload/stop, progress bar, desktop/mobile mode per tab, bookmarks, history (+delete/clear), find in page, share/copy/open-externally, fullscreen HTML5 video, user-initiated popups as tabs (script popups blocked), safe external-intent handling, error screens (offline/DNS/TLS/server), TLS errors never bypassed, website download callbacks → download engine (with referer/UA/cookies) |
| Media detection | Observes request URLs by extension + inspects `<video>/<audio>/<source>` after load; dismissible "Download available" chip (top-right, away from video controls); sheet with name, type, size (read from the server via HEAD/ranged GET, "unknown" if not available — resolutions are never invented); Download and Play for direct files; HLS/DASH and blob streams clearly labelled as not downloadable; DRM (EME) pages flagged |
| Player | Media3 ExoPlayer in a MediaSessionService (background audio, notification, lock-screen/Bluetooth controls); PlayerView controls (seek, speed, audio track, subtitles); landscape/fullscreen toggle; HLS/DASH playback modules included; hologram visual for audio-only; error message on failure |
| Library | Completed audio/video downloads + files added via system picker; search, sort (date/name/size), filter (video/audio/favourites), favourites, play, share, delete (downloads) / remove (imported files, never deleted) with confirmation |
| UI | Midnight/violet/cyan theme (dark/light/system), glass cards with spring press, animated hologram (Canvas rings + particles), page transitions, animated progress, pulsing loading dots, **Reduce Motion** (setting + system animation scale) |
| Settings | Theme, Reduce Motion, search engine, Wi-Fi only, simultaneous downloads, clear history/cookies/cache, About |
| Build | Gradle KTS, manifest, adaptive icon, splash screen, GitHub Actions workflow, unit tests |

## Actually tested
- **Not compiled, not installed, not run.** No emulator/device. No download, browser or playback test has been performed.
- Run by me: XML + workflow YAML parse; brace-balance and internal-import scan of the Kotlin sources.
- Unit tests written (not executed): URL normalising/resolution, filename sanitising, Content-Disposition, Content-Range, media classification.

## Untested (everything runtime) — use the checklist
Downloads: small/large file, pause→resume byte continuity, no-Range server, 403/404, airplane-mode retry, redirect, odd filenames, `.m3u8`, HTML page, Wi-Fi-only, queue limit, kill-and-reopen.
Browser: load/search, tabs survive switching sections, back/forward, desktop mode, fullscreen video, a site download, popup behaviour, TLS error page, find, bookmarks/history.
Detection: MP4/MP3 page → chip and sheet, size shown, download works; HLS page → labelled unsupported; blob/DRM page.
Player: local MP4/MP3, remote MP4/MP3, HLS, seek, speed, background audio + notification, rotate.
Library: downloaded items appear, add from device, favourites, delete/remove.
UI: small/large phones, long filenames, Reduce Motion, TalkBack basics, Android 13/14/15.

## Known limitations
- Not implemented: segmented/parallel downloads, checksum verification, HLS/DASH **downloading** (Media3 offline), cloud providers (Google Drive/TeraBox), playlists, playback-position memory, brightness/volume/seek gestures, Picture-in-Picture, bookmark edit/import/export/search, per-site permissions UI, accent colours, parallax cards, configurable download folder, tab persistence across app restarts.
- Website file-upload dialogs, camera/microphone/location are not supported (denied).
- Media detection only sees what WebView exposes: `shouldInterceptRequest` observes requests but cannot see everything; media-element scan is best-effort and runs 3 times after page load.
- Download Library shows only RoohuX downloads plus picker-added files (no broad storage scan, by design: no storage permission).
- Finished downloads are copied from private storage to MediaStore (briefly needs space for both).
- Unknown-length (chunked) downloads can't be size-verified or paused.
- Playing remote media doesn't send cookies/referer, so hotlink-protected files may fail to play.
- Audio keeps playing in the background; video pauses when leaving the player.
- English-only strings. Cleartext HTTP allowed (spec asks for HTTP downloads); typed bare hosts are upgraded to HTTPS.
- `allowBackup` is off, so app data isn't restored on a new phone.
