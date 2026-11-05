#!/usr/bin/env bash
# Copies the publishing credentials from local.properties and your GPG keychain into the
# GitHub repository's Actions secrets. The values go straight from here to GitHub: they are
# never printed, written to a file or put on the clipboard.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=${REPO:-burkido/Kraft}

prop() {
    local value
    value=$(grep -E "^$1=" local.properties | head -1 | cut -d= -f2-)
    if [ -z "$value" ]; then
        echo "error: $1 is empty in local.properties" >&2
        exit 1
    fi
    printf '%s' "$value"
}

prop mavenCentralUsername | gh secret set MAVEN_CENTRAL_USERNAME --repo "$REPO"
prop mavenCentralPassword | gh secret set MAVEN_CENTRAL_PASSWORD --repo "$REPO"
prop signingKeyPassword | gh secret set SIGNING_KEY_PASSWORD --repo "$REPO"
prop signingKeyPassword |
    gpg --batch --pinentry-mode loopback --passphrase-fd 0 \
        --export-secret-keys --armor "$(prop signingKeyId)" |
    gh secret set SIGNING_KEY --repo "$REPO"

gh secret list --repo "$REPO"
