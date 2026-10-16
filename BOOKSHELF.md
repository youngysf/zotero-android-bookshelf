# Bookshelf (personal Zotero for Android build)

A personal build of Zotero for Android that turns it into a reading library.
It installs alongside the official Zotero app (app ID
`org.zotero.android.bookshelf`, name "Bookshelf") and syncs with the same
Zotero account.

## Branches

- `master` mirrors Zotero's `zotero/zotero-android`. Update it with GitHub's
  **Sync fork** button; don't commit to it.
- `bookshelf` holds all Bookshelf changes and is what gets built. Bring in
  Zotero's updates by merging `master` into `bookshelf`.

## Builds

Every push to `bookshelf` runs `.github/workflows/bookshelf.yml`, which builds
the `internalRelease` APK and publishes it as a GitHub Release named
`build-N`. The newest 8 builds are kept.

The workflow needs one repository secret, `BOOKSHELF_KEYSTORE`: the
base64-encoded signing key. Every build must be signed with the same key, or
Android won't install it over the previous one. Keep a copy of the key file.

What the workflow changes compared with Zotero's own build:

- Signs with the personal key instead of Zotero's.
- Replaces `app/google-services.json` with a placeholder on the build machine
  (`scripts/bookshelf/placeholder_google_services.py`). Crash reporting is
  turned off for this build.
- Version code is `10000 + run number`, so each build installs over the last.
- Only ARM phone ABIs are included.
- If the build fails, `scripts/bookshelf/report_errors.py` posts the errors as
  annotations on the run.

Zotero's own Play Store workflow (`android.yml`) only runs in Zotero's
repository.

## Where Bookshelf code lives

New code goes in separate files and packages (`org.zotero.android.bookshelf`)
where possible, so merging Zotero's updates stays easy. Edits to Zotero's own
files are kept small and marked with a `Bookshelf:` comment.
