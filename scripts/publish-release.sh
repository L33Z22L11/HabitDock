#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${RELEASE_TAG:?Set RELEASE_TAG to the existing v-prefixed tag}"
python3 scripts/release.py validate "$RELEASE_TAG"

mkdir -p build
apk="dist/HabitDock-${RELEASE_TAG#v}.apk"
# A draft makes failed uploads recoverable without announcing an incomplete release.
if gh release view "$RELEASE_TAG" --json isDraft > build/release-state.json; then
    python3 - <<'PYTHON'
import json
from pathlib import Path

state = json.loads(Path("build/release-state.json").read_text())
if not state["isDraft"]:
    raise SystemExit("Release already published; use a new version tag.")
PYTHON
else
    flags=(--draft --verify-tag --generate-notes --title "HabitDock $RELEASE_TAG")
    if [[ "$RELEASE_TAG" == *-* ]]; then
        flags+=(--prerelease)
    fi
    gh release create "$RELEASE_TAG" "${flags[@]}"
fi

gh release upload "$RELEASE_TAG" "$apk" dist/SHA256SUMS --clobber
gh release edit "$RELEASE_TAG" --draft=false
