# Distribution: GitHub Releases, Obtainium, and F-Droid

BumpDesk ships APKs from GitHub when you push a `v*` tag (see `.github/workflows/create_release.yml`). This document explains how that relates to **Obtainium** and **F-Droid**.

## GitHub Releases (current setup)

- Tag format: `v1.52.24` (must match `v*` for the workflow).
- Artifacts: `app-debug.apk` and signed `app-release.apk`.
- Release notes are generated from git history via `.github/scripts/generate-release-notes.sh`.

This is the source of truth for prebuilt binaries.

## Obtainium (recommended for testers)

**Obtainium is not a store.** There is nothing to “submit.” Users install [Obtainium](https://github.com/ImranR98/Obtainium) and add your repo as an update source.

**Pros**

- Works with your existing GitHub Releases workflow immediately.
- Users get update notifications when you push new tags.
- No review queue, no separate build pipeline.

**Cons**

- Users must enable “Install unknown apps” / sideloading.
- APKs are signed with **your** release key, not F-Droid’s.
- You maintain release hygiene (changelog, asset names, tag discipline).

**Typical config**

| Field | Value |
|-------|--------|
| Source | GitHub |
| Repo | `electrikjesus/BumpDesk` |
| Release filter | `v*` tags |
| APK filter | `app-release.apk` (or `app-debug.apk` for debug builds) |

Share the Obtainium import link or these values in release notes so testers can one-tap subscribe.

## F-Droid (separate onboarding project)

**F-Droid is not triggered by your GitHub release workflow.** Inclusion is a **one-time merge request** to the [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository, plus ongoing metadata maintenance.

**What F-Droid does**

- Clones **source** from your public repo.
- Builds the APK on F-Droid infrastructure.
- Signs with an **F-Droid-specific** key (different fingerprint from GitHub release APKs).
- Publishes to [f-droid.org](https://f-droid.org) after review.

**What you need before submitting**

1. **FOSS compliance** — all dependencies and bundled assets must be acceptable under F-Droid policy (no proprietary blobs, no non-free network services required for core use, etc.).
2. **Reproducible build recipe** — a YAML file in `metadata/com.bass.bumpdesk.yml` with Gradle build steps. Use `fdroid import --url https://github.com/electrikjesus/BumpDesk` as a starting point.
3. **Fastlane metadata** (screenshots, description, categories) under `fastlane/metadata/android/en-US/` in **this** repo helps reviewers.
4. **Version discipline** — `versionCode` / `versionName` in `app/build.gradle.kts` must bump on each release; tags help autoupdate detection.

**After acceptance**

- F-Droid’s update checker can pick up new tags automatically (`UpdateCheckMode: Tags` is common for GitHub projects).
- You do **not** upload APKs to F-Droid; they rebuild from source.
- Failed builds show on the [F-Droid wiki build page](https://f-droid.org/wiki/) for your app id — fix metadata or source and wait for the next cycle (often daily).

**Can GitHub Actions “submit” to F-Droid?**

Not to the **official** F-Droid repo in any push-button sense. Practical options:

| Approach | Effort | Notes |
|----------|--------|-------|
| Official F-Droid | High (first time) | MR to fdroiddata; human review; builds on F-Droid servers |
| Self-hosted F-Droid repo | Medium | Your own `fdroid update` index + GitHub Pages; users add your repo URL in the F-Droid client |
| Nightly/community actions | Medium | e.g. community workflows that open fdroiddata MRs — still needs maintainer merge |

Automating an **fdroiddata merge request** on every tag is possible in theory but rarely worth it until the app is stable and policy-clean; most projects open an MR manually when they cut a release they want prioritized.

## Recommendation

| Audience | Channel |
|----------|---------|
| Developers & early testers | GitHub Releases + Obtainium |
| Privacy-focused users who want F-Droid builds | Plan a dedicated fdroiddata MR when dependencies and versioning are stable |
| Play Store | Out of scope for this repo’s current workflow |

For now, document Obtainium in release notes and treat F-Droid as a follow-up milestone with its own checklist (metadata file, lint, `fdroid build -l com.bass.bumpdesk`, AntiFeatures, etc.).
