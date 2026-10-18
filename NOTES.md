# DevinHealth — food photo → Health Connect → Google Health

Personal-use Android project: photograph food, get an AI nutrition estimate, and
have it show up as a food log in the Google Health app (via Health Connect).

## Decisions so far

- **Personal use only**, sideloaded on one modern Android phone (Health Connect installed).
- **No paid AI API.** Use the Claude app on the phone (existing subscription) to do the
  estimating. Subscription logins can't be used from a custom app (Anthropic policy, Feb 2026).
- **No review/edit step** — tap and it logs.
- Google Health's own photo logging is a Premium feature and isn't available on this account.

## Chosen flow (option B)

```
Claude app (photo + Project instructions)
   └─ replies with an https link, e.g.
      https://ender22.github.io/DevinHealth/?name=Chicken%20salad&kcal=520&protein=38&carbs=20&fat=30&meal=lunch
         └─ docs/index.html (GitHub Pages) bounces to devinhealth://log?...
               └─ DevinHealth Android app writes a NutritionRecord to Health Connect
                     └─ Google Health reads it from Health Connect
```

Why the bounce page: tapping a custom-scheme link (`atiomapp://...`) inside the Claude
app does nothing; the same link works from the browser. So Claude emits an `https://`
link and the page forwards it to the app.

## Status

- [x] `docs/index.html` bounce page. `?atiom=1` = test mode that opens a sample Atiom event
      (`atiomapp://open?url=...`) to prove the Claude → browser → app hop works before
      our app exists. Shows "Log it" (custom scheme) and "alternate" (`intent://`) buttons.
- [x] GitHub Pages enabled (2026-10-18): https://ender22.github.io/DevinHealth/ serves `/docs` from
      this branch. Git pushes as Ender22 via local `credential.username` (same trick as GuitarLegend).
- [x] **Android App Links verified** on the Pixel 7: the https link opens DevinHealth directly, no
      Chrome hop. Android needs `assetlinks.json` at the *host root*, so a second repo
      `Ender22/Ender22.github.io` hosts https://ender22.github.io/.well-known/assetlinks.json
      (the copy under `docs/.well-known/` is redundant but harmless). If the signing key changes,
      update the fingerprint in both places.
- [ ] (old) Enable GitHub Pages: Settings → Pages → Deploy from a branch →
      `claude/food-photo-nutrition-google-health-4qea1b`, folder `/docs`.
- [ ] Test https://ender22.github.io/DevinHealth/?atiom=1 from inside the Claude app:
      does Atiom open automatically, or only via a button?
- [x] Android app (Kotlin + Compose) in `app/`: handles `devinhealth://log?...` and
      `https://ender22.github.io/DevinHealth/?...` (App Link), requests nutrition permission,
      writes a `NutritionRecord`, logs automatically with no review step. Has "Log test meal".
      **Verified 2026-10-18 on the Pixel 7:** a `devinhealth://log?...` link produced a record
      in Health Connect (attributed to DevinHealth) and Google Health showed "520 cals in".
- [x] GitHub Actions workflow builds the debug APK on push (`.github/workflows/android.yml`).
- [x] `docs/.well-known/assetlinks.json` with the debug-keystore fingerprint so the https link
      can open the app directly once Pages is live (App Links verification).
- [x] Claude Project instructions: `docs/CLAUDE_PROJECT_INSTRUCTIONS.md` (paste into the Project).

## Build / install

```
./gradlew assembleDebug            # needs JDK 17+, Android SDK 36
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "devinhealth://log?name=Test&kcal=100&meal=snack"
```

Signed with the local debug keystore (SHA-256 `CC:AB:53:94:...:3E:DE`, see assetlinks.json).
Reinstalling an APK built on another machine (e.g. CI) needs an uninstall first.

## Open questions / unverified

- ~~Do custom `NutritionRecord`s show in Google Health?~~ **Yes** — confirmed on device.
- ~~Whether Chrome lets the bounce page open the app automatically.~~ **Yes** when Chrome is
  launched with the page URL (tested via a local server + `adb reverse`): it jumped straight
  to the app and the meal was logged, no button tap. Still to check from a tap inside Claude.
- ~~Android App Links might make it one tap.~~ Verified working from an intent; still unknown
  whether a tap inside the Claude app honours them (if not, Chrome auto-bounces anyway).
- Repo is **public** — the page and any built APK would be publicly visible.

## Link parameters (planned)

`name`, `kcal`, `protein`, `carbs`, `fat`, `fiber`, `sugar` (g), `sodium` (mg),
`meal` (`breakfast` | `lunch` | `dinner` | `snack`; app guesses from time if missing).
