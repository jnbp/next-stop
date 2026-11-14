# Keys and secrets

Some data sources of Next Stop need an access key. A key can come from two places:

1. **Entered by the user** in the app's settings. It stays on the device, in a preferences file
   that is left out of every backup (`source_keys.xml`, `lift_keys.xml`). The rest of the
   settings is part of Android's backup (see `res/xml/data_extraction_rules.xml` and
   "Privacy" in the README), so after a restore the settings are back but every key has to be
   entered again.
2. **Built into the app** from a local file `secrets.properties` at build time. Builds made
   without that file simply have no built-in keys.

**A key the user enters always wins over the built-in one.** Clearing the field goes back to
the built-in key. The settings show "Integrierter Schlüssel aktiv" / "Built-in key active"
under a field while a built-in key is in use, and "Dein Schlüssel ersetzt den integrierten" /
"Your key replaces the built-in one" when an entered key overrides it. The app never shows the
built-in key itself.

## Rules

- **Never commit keys.** `secrets.properties` is in `.gitignore`; only
  `secrets.properties.example` (with empty values) is in the repository.
- **Never put keys into logs**, crash reports, issues, screenshots or shared links. The code
  logs at most whether a key is set (see `SourceChoice.toString`), never its value.
- A key built into an APK can be extracted from that APK. Only share such builds with people
  the key's terms allow (e.g. accessibility.cloud tokens are for non-commercial use), and do
  not publish APKs with built-in keys.

## The keys

| Key in `secrets.properties` | What it is for | Built in? | Where to get it |
|---|---|---|---|
| `accessibilityCloudToken` | App token of accessibility.cloud: lifts of BVG, S-Bahn and DB stations in Berlin and Brandenburg (the data of brokenlifts.org) | yes | accessibility.cloud account → organization "Next Stop" → API client "Next Stop" → app token |
| `vbbAccessId` | `accessId` of the official VBB HAFAS API (data source "VBB") | yes | Requested by mail to api@vbb.de (request sent on 2026-11-13); the VBB sends the accessId by mail |
| `fastaClientId`, `fastaApiKey` | Optional: DB API Marketplace, FaSta API (lifts and escalators at DB stations) | yes, both or none | developers.deutschebahn.com (DB API Marketplace): create an application, subscribe it to "FaSta – Station Facilities Status", use its client id and API key |
| — | HAFAS client authorization for public-transport-enabler (JSON such as `{"type":"AID","aid":"…"}`) | **no**, entered by the user only | — |

The FaSta pair is used only as a pair: if the user enters only one half, the built-in pair is
used as a whole (parts of different pairs are never mixed).

## How a build picks them up

1. Copy `secrets.properties.example` to `secrets.properties` in the project root and fill in
   what you have. Empty or missing keys are fine.
2. `app/build.gradle.kts` reads the file (through a Gradle provider, so a change is noticed
   by the configuration cache) and turns each key into a `BuildConfig` field:
   `ACCESSIBILITY_CLOUD_TOKEN`, `VBB_ACCESS_ID`, `FASTA_CLIENT_ID`, `FASTA_API_KEY`
   (`buildFeatures.buildConfig = true`). A missing key becomes `""`.
3. If a key is empty in the file, the build looks for an environment variable
   `NEXTSTOP_<KEY_IN_SNAKE_CASE>` instead: `NEXTSTOP_ACCESSIBILITY_CLOUD_TOKEN`,
   `NEXTSTOP_VBB_ACCESS_ID`, `NEXTSTOP_FASTA_CLIENT_ID`, `NEXTSTOP_FASTA_API_KEY`.
4. In the app, `data/BuiltInKeys.kt` is the only place that reads these fields;
   `effectiveKey` / `effectiveKeyPair` pick the entered key first, else the built-in one.
   `LiftKeys.sources()` and `SourceChoice.vbbKey` use them.

### A build without secrets

Builds and runs as before: no source has a built-in key. The lift information stays off until
a token is entered, and the "VBB" data source shows "not set up" and is skipped until a key is
entered; the other sources (BVG REST, Transitous, public-transport-enabler with an entered
authorization) work as usual. Nothing fails at build time.

## CI (GitHub Actions)

There is no workflow yet. When one is added:

1. Repository → Settings → Secrets and variables → Actions → *New repository secret*, one per
   key: `NEXTSTOP_ACCESSIBILITY_CLOUD_TOKEN`, `NEXTSTOP_VBB_ACCESS_ID`, and optionally
   `NEXTSTOP_FASTA_CLIENT_ID`, `NEXTSTOP_FASTA_API_KEY`.
2. Hand them to the build step as environment variables; the build reads them (step 3 above):

   ```yaml
   - name: Build
     run: ./gradlew assembleDebug
     env:
       NEXTSTOP_ACCESSIBILITY_CLOUD_TOKEN: ${{ secrets.NEXTSTOP_ACCESSIBILITY_CLOUD_TOKEN }}
       NEXTSTOP_VBB_ACCESS_ID: ${{ secrets.NEXTSTOP_VBB_ACCESS_ID }}
       NEXTSTOP_FASTA_CLIENT_ID: ${{ secrets.NEXTSTOP_FASTA_CLIENT_ID }}
       NEXTSTOP_FASTA_API_KEY: ${{ secrets.NEXTSTOP_FASTA_API_KEY }}
   ```

3. Do not upload APKs built with keys as public artifacts or releases (see the rules above).
   Pull requests from forks get no secrets and build without built-in keys, which is fine.
