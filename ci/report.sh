#!/usr/bin/env bash
# Opens an issue with the important part of build.log when CI fails.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

{
  echo "CI build #${GITHUB_RUN_NUMBER:-?} failed for commit ${GITHUB_SHA:-?}"
  echo ""
  echo "${RUN_URL:-}"
  echo ""
  echo '```'
  if [ -f build.log ]; then
    grep -E "^e: |error:|ERROR|What went wrong|Could not|FAILED|Exception" build.log | head -n 150
    echo "----- tail -----"
    tail -n 80 build.log
  else
    echo "build.log not found (failed before build step)"
  fi
  echo '```'
} > issue.md

gh issue create --repo "${GITHUB_REPOSITORY}" --title "CI build #${GITHUB_RUN_NUMBER:-?} failed" --body-file issue.md
