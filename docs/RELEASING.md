# Releasing

Kraft is published to Maven Central as `io.github.burkido:kraft`. `main` is the only long-lived branch: work happens on short branches that are squash-merged into `main` through pull requests, and a release is a tag on `main`.

## Versions

Kraft stays on `0.x` while the API can still change:

- A release with only bug fixes bumps the last number: `0.1.0` → `0.1.1`.
- Anything else, including new effects and API changes, bumps the middle one: `0.1.1` → `0.2.0`.
- `1.0.0` marks the point where the API settles.

## Cutting a release

1. Open a pull request that sets `VERSION_NAME` in [`gradle.properties`](../gradle.properties) and the version in the README's dependency snippet, then merge it.
2. Tag the merge commit and push the tag:

   ```sh
   git switch main && git pull
   git tag -a v0.1.1 -m "Kraft 0.1.1"
   git push origin v0.1.1
   ```

   The tag has to be `v` followed by `VERSION_NAME`, or the workflow stops.
3. The [Publish workflow](../.github/workflows/publish.yml) runs the tests on macOS, signs and uploads the artifacts to the Central Portal and creates the GitHub release with notes generated from the merged pull requests. It takes 10 to 15 minutes.
4. On [central.sonatype.com](https://central.sonatype.com), open **Publish**, then **Deployments**. When the deployment shows **Validated**, press **Publish**. Maven Central can't take a release back, so this step stays manual. **Drop** discards the upload instead.

The new version usually resolves from Maven Central 15 to 30 minutes later.

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

Only needed if the workflow is broken. With the same `local.properties` values, [`scripts/publish.sh`](../scripts/publish.sh) uploads the current version and stops at **Validated**, like the workflow.

To check the artifacts without uploading anything, run `./gradlew :effects:publishToMavenLocal` and look in `~/.m2/repository/io/github/burkido/`. CI does this on every pull request.
