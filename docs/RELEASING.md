# Releasing

Kraft is published to Maven Central as `io.github.burkido:kraft`. `main` is the only long-lived branch: work happens on short branches that are squash-merged into `main` through pull requests, and a release is a GitHub release made from `main`.

## Versions

Kraft stays on `0.x` while the API can still change:

- A release with only bug fixes bumps the last number: `0.1.0` → `0.1.1`.
- Anything else, including new effects and API changes, bumps the middle one: `0.1.1` → `0.2.0`.
- `1.0.0` marks the point where the API settles.

## Cutting a release

A release is a GitHub release. Nothing in the repository changes for it.

1. On GitHub, open **Releases**, then **Draft a new release**.
2. Under **Choose a tag**, type the new tag, `v` followed by the version (for example `v0.1.1`), and create it on `main`.
3. Press **Generate release notes**, edit them if you like, then press **Publish release**.

Publishing the release starts the [Publish workflow](../.github/workflows/publish.yml). It reads the version from the tag, runs the tests on macOS, signs the artifacts, uploads them to the Central Portal and releases them. It takes 10 to 15 minutes, and the version usually resolves from Maven Central 15 to 30 minutes after that.

Maven Central can't take a release back. If a release turns out broken, fix it and publish the next version. If the workflow fails before the upload, fix the cause and press **Re-run jobs** on the failed run.

## One-time setup

The Publish workflow needs four repository secrets, under **Settings**, **Secrets and variables**, **Actions**:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_KEY` | The armored private key, `gpg --export-secret-keys --armor <key id>` |
| `SIGNING_KEY_PASSWORD` | The key's passphrase |

With the values in `local.properties` (`mavenCentralUsername`, `mavenCentralPassword`, `signingKeyId`, `signingKeyPassword`), [`scripts/set-github-secrets.sh`](../scripts/set-github-secrets.sh) sets all four without printing them. The public half of the signing key has to be on `keyserver.ubuntu.com` or `keys.openpgp.org`, where Maven Central checks signatures.

## Publishing from your machine

Only needed if the workflow is broken. With the same `local.properties` values, run [`scripts/publish.sh`](../scripts/publish.sh) with the version, for example `scripts/publish.sh 0.1.1`. It uploads and stops at **Validated**: on [central.sonatype.com](https://central.sonatype.com), open **Publish**, then **Deployments**, and press **Publish**, or **Drop** to discard the upload.

To check the artifacts without uploading anything, run `./gradlew :effects:publishToMavenLocal` and look in `~/.m2/repository/io/github/burkido/`. Local builds use the placeholder version `0.0.0-LOCAL` from `gradle.properties`. CI does this on every pull request.
