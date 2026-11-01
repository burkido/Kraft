#!/usr/bin/env bash
# Uploads the :effects library to the Maven Central Portal. The deployment stops there until
# you press Publish on central.sonatype.com.
#
# Credentials come from local.properties (gitignored) and are passed to this one Gradle run
# only, so no other build on the machine can see them.
set -euo pipefail
cd "$(dirname "$0")/.."

prop() {
    local value
    value=$(grep -E "^$1=" local.properties | head -1 | cut -d= -f2-)
    if [ -z "$value" ]; then
        echo "error: $1 is empty in local.properties" >&2
        exit 1
    fi
    printf '%s' "$value"
}

ORG_GRADLE_PROJECT_mavenCentralUsername=$(prop mavenCentralUsername)
ORG_GRADLE_PROJECT_mavenCentralPassword=$(prop mavenCentralPassword)
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$(prop signingKeyPassword)
ORG_GRADLE_PROJECT_signingInMemoryKey=$(
    printf '%s' "$ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" |
        gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
            --export-secret-keys --armor "$(prop signingKeyId)"
)
export ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword \
    ORG_GRADLE_PROJECT_signingInMemoryKeyPassword ORG_GRADLE_PROJECT_signingInMemoryKey

# The configuration cache would store the signing key on disk, so it's off for this build.
./gradlew :effects:publishToMavenCentral --no-configuration-cache "$@"
