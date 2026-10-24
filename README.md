# RoohuX

Android download manager (Kotlin, Jetpack Compose, Room, DataStore, OkHttp). **Developer:** Roohu · **Support:** roohukhan01@gmail.com

Kotlin + Jetpack Compose app (v0.3.0 redesign) combining a download manager, a browser with media detection, a Media3 player and an offline library.

## Build

Phone-friendly: push to GitHub, open **Actions → Build debug APK**, download the `RoohuX-debug-apk` artifact.
The workflow installs JDK 17 + Gradle 8.9, builds `:app:assembleDebug`, checks the APK exists / unzips cleanly / has
package `com.roohu.roohux`, uploads it, then runs unit tests. No Gradle wrapper jar is committed; the workflow installs Gradle.
Locally: JDK 17 + Android SDK 35, then `gradle :app:assembleDebug` (or `gradle wrapper` first).

## Status
See **DEVELOPMENT_PROGRESS.md** for the full, honest status. In short: v0.2.0 adds a browser, media detection, Media3 player, library and a premium UI on top of the download engine. **None of it has been compiled or run yet.**

## Manual test checklist (to run on a device)

1. Start a small public file download → appears in Downloads, completes, **Open** works, file visible in Files → Downloads/RoohuX.
2. Large file → progress, speed, ETA update; **Pause** then **Resume** continues from the same byte count.
3. A server without Range support → Pause button hidden, note shown.
4. 404 / 403 URLs → clear failure message + Retry. Airplane mode mid-download → retries, then Failed; Retry resumes.
5. Redirecting URL, filename with unusual characters, `.m3u8` URL (should say playlists unsupported), HTML page URL (should say it's a web page).
6. Wi-Fi-only on + mobile data → clear failure. Max simultaneous = 1 with 3 links → run one at a time.
7. Kill the app mid-download → on reopen items show Paused (resumable) or Failed.
8. Browser: open two tabs, switch to Downloads and back (tabs intact); desktop mode; fullscreen a video; a page with an MP4 shows "Download available".
9. Player: play a downloaded MP3 and leave the screen (audio continues, notification controls work); play an MP4 and rotate.
10. Library: downloaded media appears; Add from device; favourite; delete.

Run unit tests: `gradle :app:testDebugUnitTest`.
