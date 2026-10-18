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
- [ ] Enable GitHub Pages: Settings → Pages → Deploy from a branch →
      `claude/food-photo-nutrition-google-health-4qea1b`, folder `/docs`.
- [ ] Test https://ender22.github.io/DevinHealth/?atiom=1 from inside the Claude app:
      does Atiom open automatically, or only via a button?
- [ ] Android app (Kotlin + Compose): handle `devinhealth://log?...`, request
      `WRITE_NUTRITION`, write `NutritionRecord`. Include a "log test meal" button to confirm
      entries appear in Google Health.
- [ ] Claude Project instructions that make Claude reply with the link format above.
- [ ] Optional: build APK with GitHub Actions so it can be installed from the phone.

## Open questions / unverified

- Google Health officially names MyFitnessPal/Cronometer as Health Connect food sources; an
  open-source app reports custom `NutritionRecord`s also appear. Needs a real-device test.
- Whether Chrome lets the bounce page open the app automatically, or requires the button tap.
- Android App Links (verified https → app directly) might make it one tap; unknown whether
  the Claude app honours them.
- Repo is **public** — the page and any built APK would be publicly visible.

## Link parameters (planned)

`name`, `kcal`, `protein`, `carbs`, `fat`, `fiber`, `sugar` (g), `sodium` (mg),
`meal` (`breakfast` | `lunch` | `dinner` | `snack`; app guesses from time if missing).
