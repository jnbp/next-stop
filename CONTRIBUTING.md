# Contributing to Next Stop

Bug reports, ideas and pull requests are welcome. This page is the short version; the details are
in the developer docs.

## Before you start

- Read [ARCHITECTURE.md](ARCHITECTURE.md) for how the project is put together, and
  [docs/conventions.md](docs/conventions.md) for the rules every change follows (solve it for all of
  Germany and check against the data, animations that respect "animations off", English and German
  strings, no private names that clash with public ones, English code and commits, privacy).
- [docs/development.md](docs/development.md) has the setup, the build and test commands, the
  watch and emulator instructions and how the station data is rebuilt.
- For a larger change open an issue first so the direction can be agreed.

## Making a change

1. Branch from the main branch and keep the change to one idea.
2. Build and run the tests that fit your change (all of them are listed in
   [docs/development.md](docs/development.md#tests)): `./gradlew test`, the web tests when you touch
   `web/`, the Python self-tests and the audits when you touch `tools/` or a rule they check.
3. Add a user-visible string in **both** `values/` and `values-de/` (and in `wear/` for the watch).
4. Write the commit message in English: a short subject, and in the body why and, for data rules,
   the audit numbers before and after.
5. Open a pull request that says what changed for the user and how you checked it. Screenshots must
   show sample data only (well-known central stations, no real contacts, appointments or addresses).

## Ground rules

- No personal data, credentials, keys or home and work addresses in code, sample data, logs,
  issues or screenshots. Never commit `secrets.properties` or a keystore
  ([docs/keys.md](docs/keys.md)).
- No tracking, analytics or ads, and no new network host without updating the privacy text in the README.
- Everything in the repository is written in English; the app itself has English and German texts.

## Licence

Next Stop is licensed under the [GNU General Public License v3.0](LICENSE) with the
[app store permission](LICENSE-EXCEPTION.md) (an additional permission under section 7 that lets the
project's own code be distributed through app stores, as long as the full source stays free and
the app says where to find it). By opening a pull request you agree that your contribution is
licensed under the same terms: GPLv3 with that permission. Third-party components keep their own
licences ([NOTICE](NOTICE)).
