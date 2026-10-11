# Maven Central releases

This repository calls [PlaytikaOSS/playtika-workflows](https://github.com/PlaytikaOSS/playtika-workflows)
at immutable commit `414efe1258d11ba5dcd2f9de4a9fcf82bd915c38`. Both workflow SHA pins must remain identical.
Review and merge [the shared compatibility change](https://github.com/PlaytikaOSS/playtika-workflows/pull/6)
before enabling this caller.

The shared workflow checks allowed actors, protected source and an immutable
release tag, builds without publication secrets, attests the unsigned bundle,
then uses the official JReleaser action after independent Environment approval.
Repository build code does not run with publication/signing credentials.
Manual publication and release-time source pushes are removed.
+
| Setting | Value |
+| --- | --- |
+| Protected release branch | `develop` |
+| Build JDK | `8` |
+| Maven artifact group | `com.playtika.sleuth` |
+| Example published release tag | `2.1.1` |
+| Maven version for that tag | `2.1.1` |
+| Environment tag-only deployment policy | `[0-9]*.[0-9]*.[0-9]*` |
+
Follow the [caller onboarding instructions](https://github.com/PlaytikaOSS/playtika-workflows#onboard-each-caller-repository):
+
1. Allow the pinned shared workflow and its GitHub/JReleaser actions.
+2. Configure `RELEASE_ACTORS` with approved release authors, publishers and rerun actors, including an approved drafting bot when applicable.
+3. Create `maven-central` with required reviewers, prevent self-review, disable administrator bypass, and add only the tag policy above.
+4. Add Environment secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_TOKEN`, `GPG_PUBLIC_KEY`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`. Use Central Portal credentials, not retired OSSRH credentials.
+5. Remove repository-level publication/signing secret duplicates and exclude this repository from organization copies. Protect the release branch with reviews/checks and version tags against update/deletion.
+
The release build runs tests in its credential-free build job. Require the
project CI as a branch check as well.

This migration includes the Apache Maven 3.3.4 script-only wrapper with a
SHA-256-verified Maven 3.9.16 distribution so the shared build can invoke
`./mvnw` consistently.

+Publish the release only after the caller is merged into the release commit's
+branch. Review the validated source SHA and shared workflow pin before approving
+the `maven-central` deployment. Drafts, prereleases, snapshots and unapproved
+actors are rejected. Publication stays blocked until onboarding is complete.
