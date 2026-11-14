# Publishing Next Stop on Google Play

Checklist for the first release of the phone app (`app`) and the Wear OS app (`wear`) as an
**internal test**, then a **closed test**, then **production**. Researched on 2026-11-14 against the
Play Console help (`support.google.com/googleplay/android-developer`) and developer.android.com;
Google changes these rules often, so each step names its source. Sections marked *verify* were not
confirmed in the help text and should be checked in the Console when you reach them.

Package id in this document: `io.github.jnbp.nextstop` (to be renamed from `io.github.jnbp.nextstop`
before the first upload, see [step 0](#0-before-the-first-upload-in-the-repository)).

Companion files:

- `fastlane/metadata/android/{de-DE,en-US}/` – store texts, changelogs, feature graphic (SVG)
- [play-console-answers.md](play-console-answers.md) – prepared answers for every Console form
- [screenshots/play.md](screenshots/play.md) – screenshot plan and image sizes

## Requirements at a glance (as of 2026-11-14)

| Topic | Requirement | Source |
|---|---|---|
| Account | One-time fee US$25, age 18+, personal or organisation account | [6112435](https://support.google.com/googleplay/android-developer/answer/6112435) |
| Personal account verification | Legal name and address from the Google Payments profile (government ID if that profile is unverified), verified developer e-mail, **device verification with the Play Console mobile app before an app can be made available** | [10841920](https://support.google.com/googleplay/android-developer/answer/10841920), [6112435](https://support.google.com/googleplay/android-developer/answer/6112435) |
| Testing before production (personal accounts created after 2023-11-13) | **At least 12 testers opted in to a closed test, continuously for 14 days**, then "Apply for production" (Google answers usually within 7 days) | [14151465](https://support.google.com/googleplay/android-developer/answer/14151465) |
| Target API (new apps and updates, since 2026-08-31) | Phone **API 36** (Android 16), Wear OS **API 35** | [11926878](https://support.google.com/googleplay/android-developer/answer/11926878) |
| This repo | Phone `targetSdk = 37`, watch `targetSdk = 36`: both satisfy it | `app/build.gradle.kts`, `wear/build.gradle.kts` |
| Signing | Play App Signing; you keep an **upload key** (RSA, at least 2048 bit); Google holds the app signing key (RSA 4096) | [9842756](https://support.google.com/googleplay/android-developer/answer/9842756) |
| Internal test | Up to **100 testers** by e-mail; a new bundle is available "within minutes"; first upload shows temporary name and listing for up to 48 h | [9845334](https://support.google.com/googleplay/android-developer/answer/9845334) |
| Closed test | E-mail lists (max 200 lists per account, 50 per track, 2000 testers per list) or Google Groups | [9845334](https://support.google.com/googleplay/android-developer/answer/9845334) |
| Wear OS | Same package name as the phone app, separate APK/AAB, `uses-feature android.hardware.type.watch`, a release on a test track first, at least one Wear OS screenshot, "Wear OS" in the listing, review before it is discoverable | [developer.android.com/distribute/googleplay/wear](https://developer.android.com/distribute/googleplay/wear), [13295490](https://support.google.com/googleplay/android-developer/answer/13295490) |
| Version codes | A watch version code must be **unique across all form factors** | [developer.android.com/training/wearables/packaging](https://developer.android.com/training/wearables/packaging) |
| Foreground service | Each foreground service type is declared in **App content** with a description, the user impact if it is interrupted and a **video**; special use is reviewed | [13392821](https://support.google.com/googleplay/android-developer/answer/13392821) |
| Content rating | IARC questionnaire is mandatory; apps without a rating are removed | [9898843](https://support.google.com/googleplay/android-developer/answer/9898843) |
| Data safety | Form and a privacy policy link are mandatory, also for apps that collect nothing | [10787469](https://support.google.com/googleplay/android-developer/answer/10787469) |
| Listing assets | Icon 512 x 512 PNG (max 1024 KB), feature graphic 1024 x 500 (JPEG or 24-bit PNG, no alpha), 2 to 8 screenshots per device type, Wear OS screenshots 1:1 and at least 384 x 384 | [9866151](https://support.google.com/googleplay/android-developer/answer/9866151) |
| Text limits | Title 30, short description 80, full description 4000 characters | [9898842](https://support.google.com/googleplay/android-developer/answer/9898842), [9866151](https://support.google.com/googleplay/android-developer/answer/9866151) |
| EU trader status | New accounts declare "trader" or "non-trader" when created; non-trader means EEA/UK consumers are told consumer-protection rights do not apply; traders have their address (and phone) shown publicly | [17594752](https://support.google.com/googleplay/android-developer/answer/17594752) |
| Android developer verification | Apps on certified devices must come from verified developers; Play developers are registered automatically through the Play Console account. Enforcement began 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, global from 2027 | [developer.android.com/developer-verification/guides](https://developer.android.com/developer-verification/guides) |

## 0. Before the first upload (in the repository)

- [ ] Rename the package to `io.github.jnbp.nextstop`: `applicationId` in **both** `app/build.gradle.kts`
  and `wear/build.gradle.kts` (the watch app must use the same id and the same signing key as the
  phone app, otherwise the Data Layer link between them breaks), plus anything that mentions
  `io.github.jnbp.nextstop` as an id, for example the `io.github.jnbp.nextstop.END_TRIP` action string. The Kotlin
  `namespace` may stay as it is. The id cannot be changed after the first upload.
- [x] Give the two modules **different version codes** (rule above): phone `versionCode = n`,
  watch `versionCode = 1_000_000 + n`, with `n` counting up per release (now 1 and 1_000_001).
  `versionName` is only a label and is the same for both (now `0.1.0`).
- [ ] Build **without** `secrets.properties` (no built-in keys), or decide deliberately which keys
  may ship: a key inside a public app can be extracted, and the README says not to publish builds
  with keys built in. Keyed sources stay off until the user enters a key.
- [ ] Check that `nextstop.pte` is not `false` (the release should include public-transport-enabler).
- [ ] Open the merged manifest (Android Studio: `app/src/main/AndroidManifest.xml`, tab *Merged Manifest*)
  and confirm no unexpected permission slipped in from a library (for example `AD_ID`,
  background location). Today `app` declares INTERNET, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION,
  READ_CONTACTS, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE,
  POST_PROMOTED_NOTIFICATIONS and READ_CALENDAR; `wear` declares INTERNET, both location permissions,
  VIBRATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE.
  **No background location** (`ACCESS_BACKGROUND_LOCATION`) is requested anywhere.
- [ ] Privacy policy page online (see open points in [play-console-answers.md](play-console-answers.md)).
  `web/` has none yet.
- [ ] Optional: bump `docs/screenshots/README.md` and the README roadmap after the screenshots exist.

## 1. Developer account (the owner does this)

1. Go to the Play Console sign-up page with the Google account that should own the app, accept the
   Developer Distribution Agreement, choose **Personal**, pay the US$25 fee (no prepaid cards).
2. Identity: legal name and address come from the Google Payments profile; if it is not verified,
   upload a government ID (the name must match exactly). Verify the **developer e-mail** with the
   6-digit code. Name, address and developer e-mail are shown publicly on the store page for
   personal accounts.
3. **Device verification:** install the *Google Play Console* app on a real Android phone, sign in with the same
   account and complete the verification. Without it no app can be made available.
4. **Trader status** (EU Digital Services Act), *Account details > About you*: Next Stop is free, has no ads
   and no purchases and is a hobby project, which fits **non-trader**; Google does not decide this for you
   (see [17594752](https://support.google.com/googleplay/android-developer/answer/17594752)), so
   check it against your own situation (for example freelance work under the same developer name
   would argue for "trader"). Open point for the owner.
5. Optional: a developer website. Do not use a private phone number or address that is not already
   required.

## 2. Create the app

*Play Console > Create app*

- App name: `Next Stop – ÖPNV-Begleiter` (title text of the listing; the name under the icon on
  the phone stays `Next Stop`).
- Default language: **German (Germany) – de-DE**; add English (United States) as a second language.
- App or game: **App**. Free or paid: **Free** (cannot be changed to paid later).
- Accept the declarations (Developer Program Policies, US export laws).
- Then work through *Dashboard > Set up your app* in this order and use the prepared answers:
  privacy policy, app access, ads, content rating, target audience, data safety, government apps,
  financial features, health, news; then *Policy and programs > App content > Foreground service
  permissions*. Category *Maps & Navigation* (alternative: *Travel & Local*); contact e-mail and
  website for the store listing; store listing texts from `fastlane/metadata/android/`.

## 3. Upload key and Play App Signing

New apps are enrolled in Play App Signing automatically when the first bundle is uploaded. You only
create the **upload key**; Google keeps the real signing key
([9842756](https://support.google.com/googleplay/android-developer/answer/9842756)).

Create the keystore in Android Studio (the keystore stays **outside the repository**, for example in
a password manager folder or an encrypted backup; `.gitignore` already excludes `*.jks`,
`*.keystore` and `keystore.properties`):

1. *Build > Generate Signed App Bundle / APK…* > *Android App Bundle* > *Next*.
2. Module: `app`. Under *Key store path* click *Create new…*.
3. Pick a path outside the project, set a keystore password and a key alias (for example
   `upload`) with its own password. Algorithm RSA, **key size at least 2048**, validity 25 years
   or more. Certificate fields can be generic (for example `CN=Next Stop upload`); they are not
   shown to users.
4. Back up the keystore and both passwords in two places. If the upload key is lost or exposed, Play can
   reset it (*Release > Setup > App signing > Request upload key reset*), but that takes time.

Never commit the keystore, its passwords or a `keystore.properties`. CI is not needed for the
first releases.

## 4. Build the bundles

Two separate bundles, uploaded to two tracks of the same app: the phone bundle to the phone track,
the watch bundle to the dedicated Wear OS track
([13295490](https://support.google.com/googleplay/android-developer/answer/13295490)). Both use
the same `applicationId`, which is what links the Wear OS listing to the phone app's page and
reviews ([wear docs](https://developer.android.com/distribute/googleplay/wear)).

1. Phone: *Build > Generate Signed App Bundle / APK…* > *Android App Bundle*, module **app**,
   the upload keystore, build variant **release** > *Create*.
   Output: `app/release/app-release.aab`.
2. Watch: the same wizard with module **wear**, the **same** keystore and alias.
   Output: `wear/release/wear-release.aab`.
3. Command line alternative (needs a signing config or a later signing step, not set up in the repo):
   `./gradlew :app:bundleRelease :wear:bundleRelease`; sign unsigned bundles with
   `jarsigner` and the upload key.
4. Check size and contents: the phone bundle carries about 46 MB of station assets; Play's limit
   for the base module is far above that (*verify* the current limit in the upload dialog).
5. Install a release build once on a real phone and a real watch before uploading, and make sure
   the release build (optimisation on) still starts, searches, and shows the notification.

## 5. Internal test (first milestone)

Internal tests are not enough to unlock production for a personal account (see section 7), but
they are the fast way to test the whole path.

1. *Test and release > Testing > Internal testing > Testers*: create a list with the tester e-mail
   addresses (Google accounts, up to 100) and save.
2. *Create new release*: upload `app-release.aab`. Release name: `1 (0.1.0)`. Paste the
   release notes from `fastlane/metadata/android/de-DE/changelogs/1.txt` between
   `<de-DE>…</de-DE>` and the English text between `<en-US>…</en-US>` tags (limit 500 characters
   per language).
3. *Save > Go to overview > Send changes for review* (internal tests are usually available within minutes;
   the first upload shows temporary name and listing information for up to 48 hours).
4. When the status is *Published*, copy the **opt-in link** (*Testers* tab) and send it to the
   testers; they must accept it with the Google account on the list, then install from the Play Store.
   Internal tests cannot be found by search.
5. **Wear OS:** *Test and release > Advanced settings > Form factors > Add form factor > Wear OS*.
   Add Wear OS screenshots (section 8), then open the new Wear OS internal track and upload
   `wear-release.aab`. The store listing must mention "Wear OS" (it does) and the tiles and
   complication (it does).
6. Fill in all *App content* declarations (the prepared answers file), including the foreground service
   declaration with its video. Fix every warning on the *Dashboard*.

## 6. Closed test (needed for production)

1. *Closed testing > Create track* (for example "Beta"), testers by e-mail list or Google Group
   (a Google Group is easier for 12+ people: testers join the group, then the opt-in link).
2. Upload the same AAB (new `versionCode` if anything changed) and start the rollout.
3. **Get at least 12 testers opted in and keep them opted in for 14 consecutive days.** A tester who
   leaves and returns restarts their 14 days. Plan 15 to 20 people to be safe, and ask them to
   actually use the app now and then; Google asks about the feedback you collected.
4. Wear OS: release the watch bundle to the Wear OS closed track as well, opt in to Wear OS in *Advanced settings* and accept
   the review policy. Review status is shown under *Pricing and distribution > Wear OS*
   (*Pending*, *Approved*, *Not approved*; fix and publish again if rejected). Whether the watch
   track counts towards the 12 testers is not stated; count testers of the phone track.

## 7. Production access and production release

1. After the 14 days: *Dashboard > Apply for production*. Three sections: *About your closed test*
   (how you recruited testers, what feedback you received, what you changed), *About your app*
   (what it does, who it is for), *About your production readiness* (what is left to do).
   Google usually answers within 7 days; if it wants more testing, the closed test keeps running.
2. *Production > Create new release*: reuse the tested bundle (promote it) or upload a new one with a higher
   `versionCode`; choose a staged rollout (for example 20 %) for the first release; the Wear OS
   release goes out through its own track.
3. First review of a new app can take several days. Updates later are usually faster.
4. After the release: in the Play Console, use the *App signing key certificate* (*Setup > App
   signing*) SHA-1/SHA-256 if you register the app with any API that wants it (for example a
   keyed timetable provider), and keep the upload key safe.

## 8. Listing assets

See [screenshots/play.md](screenshots/play.md) for the shots and sizes.

- **Icon:** 512 x 512 PNG with alpha, up to 1024 KB, square without rounded corners (Play applies its mask).
  Easiest: Android Studio *File > New > Image Asset > Launcher Icons (Adaptive and Legacy)*,
  tick *Generate Play Store icon (512 x 512)* and take `ic_launcher-playstore.png`.
  Command-line alternative from the SVG: remove `rx="20"` from a copy of `docs/images/icon.svg` and
  run `rsvg-convert -w 512 -h 512 icon.svg -o icon.png` (or `inkscape`, or `cairosvg icon.svg -W 512 -H 512 -o icon.png`).
  For F-Droid copy it to `fastlane/metadata/android/<locale>/images/icon.png`.
- **Feature graphic:** `fastlane/metadata/android/de-DE/images/featureGraphic.svg` (and `en-US`).
  Export 1024 x 500, **no alpha**:
  `cairosvg featureGraphic.svg -W 1024 -H 500 -b '#1B4FD8' -o featureGraphic.png`, or open the SVG in
  Chrome/Inkscape, export PNG at 1024 x 500, and flatten any transparency
  (`magick in.png -background '#1B4FD8' -alpha remove -alpha off out.png`). The text uses a system font; install
  Google Sans Flex (or Roboto) before exporting, and check that no text overflows its pill.
  Do not add store badges, ranking or price text.
- **Screenshots:** phone 4 to 8, watch 2 to 4 (1:1, at least 384 x 384, no frames).

## 9. Policy reminders that apply to this listing

- No ranking or promotion words ("best", "#1", "free", "new") or calls to action in the title,
  short description or graphics; no emojis in the title; no brand names that imply an official app
  (the descriptions state that Next Stop is independent of BVG, VBB and Deutsche Bahn)
  ([9898842](https://support.google.com/googleplay/android-developer/answer/9898842)).
- Screenshots must show the real app; no personal data (no real contacts, appointments or home
  addresses; use Hauptbahnhof and Alexanderplatz).
- Keep the answers in the Data safety form and the privacy policy in line with each other and with
  the code; update them whenever a new data flow is added.
- Release the first test only after the privacy policy URL works.

## 10. Later: F-Droid and GitHub

The `fastlane/metadata/android/` layout is also read by F-Droid and by Triple-T/fastlane `supply`.
Screenshots then go to `<locale>/images/phoneScreenshots/` and `<locale>/images/wearScreenshots/`.
F-Droid builds from source, so the Play-specific parts (Play services Wearable dependency) may need a
separate check later.
