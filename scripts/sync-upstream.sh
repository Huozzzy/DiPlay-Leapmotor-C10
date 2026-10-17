#!/usr/bin/env bash
# Report what upstream DiPlay has changed since this repository's base version.
#
# Upstream: https://github.com/shihabal3amri/DiPlay  (GPL-3.0)
# All source code originates from upstream; this script only looks at its changes.
#
# Usage: bash scripts/sync-upstream.sh [upstream tag]     e.g. bash scripts/sync-upstream.sh v0.2.21
#
# This repository does not merge upstream. The two histories are unrelated (the tree was
# re-created when this repository was set up), so a merge turns every untouched file into a
# conflict — 140 of them for v0.2.18 -> v0.2.20. The sync is done instead by replacing the
# tracked tree with the upstream tag and re-applying the Leapmotor files and hunks by hand.
# This script fetches upstream and reports what that will involve; it changes nothing.
set -euo pipefail

UPSTREAM_URL="https://github.com/shihabal3amri/DiPlay.git"

# Upstream's tags are fetched into their own namespace. This repository carries tags of the same
# names (v0.2.10 ... v0.2.18) that point at its own history, so fetching them directly is refused.
# Pass plain tag names such as v0.2.21; the script adds the namespace.
BASE_TAG="upstream/v0.2.20"

# Upstream files this branch modifies, per branch.
FILES_2123=(
  "common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt"
  "common/src/main/java/com/shilapi/xcertplay/ClusterMapPresentation.kt"
  "shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaButton.kt"
)
FILES_2427=(
  "common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt"
  "common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt"
  "mobile/src/main/AndroidManifest.xml"
)

TARGET_TAG="${1:-}"

if ! git remote get-url upstream >/dev/null 2>&1; then
  echo "Adding upstream remote: $UPSTREAM_URL"
  git remote add upstream "$UPSTREAM_URL"
fi

echo "==> Fetching upstream tags into refs/tags/upstream/"
git fetch upstream "+refs/tags/*:refs/tags/upstream/*"

branch="$(git rev-parse --abbrev-ref HEAD)"
case "$branch" in
  leapmotor-24-27) FILES=("${FILES_2427[@]}") ;;
  *)               FILES=("${FILES_2123[@]}") ;;
esac
echo "==> Branch: $branch (${#FILES[@]} upstream files modified here)"

if [ -z "$TARGET_TAG" ]; then
  echo
  echo "==> Latest upstream tags:"
  git tag --sort=-v:refname --list 'upstream/v0.*' | head -10
  echo
  echo "Pass a tag to compare against the current base, e.g.: bash scripts/sync-upstream.sh v0.2.21"
  exit 0
fi
TARGET_TAG="upstream/$TARGET_TAG"

echo
echo "==> Upstream $BASE_TAG..$TARGET_TAG"
echo "  commits: $(git rev-list --count "$BASE_TAG".."$TARGET_TAG")"
echo "  files:   $(git diff --name-only "$BASE_TAG" "$TARGET_TAG" | wc -l | tr -d ' ')"
echo
echo "==> Upstream commits, newest first:"
git log --oneline "$BASE_TAG".."$TARGET_TAG" | head -25
echo
echo "==> Overlap with the files this branch modifies:"
for f in "${FILES[@]}"; do
  if [ -n "$(git diff --name-only "$BASE_TAG" "$TARGET_TAG" -- "$f")" ]; then
    echo "  [re-apply by hand] $f"
  else
    echo "  [unchanged upstream] $f"
  fi
done
echo
echo "==> Files added upstream under the paths this branch keeps:"
git diff --name-status "$BASE_TAG" "$TARGET_TAG" -- common shared mobile docs scripts \
  | awk '$1 == "A" {print "  " $2}' | head -20
echo
echo "To sync: replace the tracked tree with $TARGET_TAG, then re-apply the Leapmotor files"
echo "and hunks using the semantic anchors in docs/LEAPMOTOR.md (21-23) or docs/LEAPMOTOR24.md (24-27)."
echo "Do not rely on line numbers - upstream editing moves them."
