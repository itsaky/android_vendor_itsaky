#!/usr/bin/env bash

set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"

while IFS= read -r dir; do
  rel="${dir#$"$PATCHES"/}"
  echo "== applying patches to $rel"
  git -C "$TOP/$rel" am --3way "$dir"/*.patch
done < <(find "$PATCHES" -name '*.patch' -printf '%h\n' | sort -u)
