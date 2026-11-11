# EMLII for Android — native Kotlin version

A real Android app (Kotlin + Jetpack Compose), not the Python-in-a-WebView version from before.
**I have no Kotlin compiler or Android SDK in my own environment, so I could not build or run
this myself** — I read through every file and checked that brackets/parens balance, but the
first build may still turn up a mistake. If it fails, send me the red error lines from the
build log (Actions tab → the failed step) and I'll fix them.

## Get the APK (no computer needed — GitHub builds it for free)
1. Create a free GitHub account → **New repository** (e.g. `emlii-kotlin`, **Private**).
2. **Add file → Upload files** → drag in *everything* from this folder, including the hidden
   `.github` folder.
   - If `.github` doesn't upload: **Add file → Create new file**, name it
     `.github/workflows/build-apk.yml`, paste in the contents shown in this project.
3. **Actions** tab → **Build EMLII APK (Kotlin)** → **Run workflow**. Takes roughly 10–15 minutes.
4. Open the finished run → **Artifacts → EMLII-kotlin-apk** → download, unzip →
   `app-debug.apk`.
5. Send it to your phone and install it (HyperOS will ask to allow installing unknown apps).

## Or build locally (Linux / WSL2)
Needs a JDK 17, Gradle itself, and the Android SDK command-line tools (platform 34,
build-tools 34.0.0) already installed — there's no `./gradlew` bundled here, see "Why no
gradlew" below. Then:
```bash
./build_apk.sh
```

## What's different from the Python/WebView version
|                        | This (Kotlin)                       | The earlier WebView version        |
|------------------------|--------------------------------------|-------------------------------------|
| Voice input            | **Works** — Android's own speech recognizer | Doesn't work (no speech API in a WebView) |
| Spoken replies         | Works (Android TextToSpeech)         | N/A (wasn't built for the WebView version) |
| Learned memory         | Its own store, Kotlin-only           | Its own store, Python-only          |
| Tools are plugins: hash, trade, waifu, ai built in, plus your own .json plugins
| `!download`, `!crack`  | Not included (too heavy for a phone) | Not included either                 |
| Shares data with desktop/web EMLII | **No** — separate app, separate memory | No, also separate |

This is a parallel, Kotlin-only implementation of the chat engine and those four tools — not a
wrapper around `EMLII.py`. If you change one version's behaviour, the other doesn't follow;
keep that in mind if you ask me to tweak something later and tell me which one you mean.

## AI model
The app can't run Ollama itself. Run Ollama on a PC on the same Wi-Fi, or `pkg install ollama`
in Termux on this same phone, then in the chat:
```
!ai url http://192.168.1.20:11434      (or http://127.0.0.1:11434 for Termux on this phone)
!ai model llama3.2:1b
!ai status
!ai test
```

## Why no `gradlew`
Gradle's wrapper needs a small binary jar file to bootstrap itself, and I have no network
access to fetch the genuine one — shipping anything else in its place would be exactly the
kind of untrustworthy binary you shouldn't run. Instead, both the GitHub Action and
`build_apk.sh` call a Gradle that's installed by the action / by you, not one bundled here.

## If the first build fails
Common things to check first:
- **"SDK location not found"**: the `android-actions/setup-android` step didn't finish — rerun
  the workflow once; if it keeps failing, tell me the exact error.
- **A dependency version "not found"**: Maven repositories occasionally retire a very old
  version; tell me which one and I'll bump it.
- **Compose compiler / Kotlin version mismatch**: tell me the error — these two versions have
  to match exactly, and I may have the pairing slightly wrong.
