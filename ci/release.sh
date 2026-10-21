#!/usr/bin/env bash
# Publishes dist/* as a GitHub Release (needs GH_TOKEN with contents:write).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# shellcheck disable=SC1090
source "${RUNNER_TEMP:-/tmp}/rayscope/version.env"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY not set}"
NOTES="$(mktemp)"
sed "s/{{NAME}}/${NAME}/g" ci/release-notes.md > "$NOTES"

if gh release view "$TAG" --repo "$REPO" > /dev/null 2>&1; then
  echo "Release $TAG exists, uploading assets"
  gh release upload "$TAG" dist/* --repo "$REPO" --clobber
else
  gh release create "$TAG" dist/* \
    --repo "$REPO" \
    --target "${GITHUB_SHA:-main}" \
    --title "RayScope ${NAME}" \
    --notes-file "$NOTES" \
    --latest
fi
echo "Release $TAG published"
