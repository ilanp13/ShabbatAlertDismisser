# /release — Build, publish and push a new release

Triggered when user says "/release" or asks to release/publish/deploy.

## Steps

1. **Bump version** in `app/build.gradle.kts`:
   - Increment `versionCode` by 1
   - Bump `versionName` (patch by default, or as user specifies)

2. **Update release notes**:
   - Update `RELEASE_NOTES.md` — move current to Previous, write new current (EN + HE)
   - Update `ROADMAP.md` — mark completed items
   - Write Google Play release notes (max 500 chars per language):
     - `app/src/main/play/release-notes/en-US/internal.txt`
     - `app/src/main/play/release-notes/iw-IL/internal.txt`

3. **Commit and push**:
   - Stage all changed files
   - Commit with message: `Bump to vX.Y.Z (versionCode N): <summary>`
   - Push to `origin main`

4. **Build and publish to Google Play**:
   - Run `./gradlew publishReleaseBundle` (builds AAB + uploads to internal testing)
   - Verify success in output

5. **Copy AAB** to project root:
   - `cp app/build/outputs/bundle/release/app-release.aab ./app-release-vX.Y.Z.aab`

6. **Report** the version, versionCode, and Play Console status to user.

## Notes
- The Gradle Play Publisher plugin is configured in `app/build.gradle.kts`
- Service account key: `play-publisher.json` (gitignored)
- Track: `internal`, status: `COMPLETED` (auto-publishes)
- Always use `Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>` in commits
