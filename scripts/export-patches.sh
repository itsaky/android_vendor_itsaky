#!/usr/bin/env bash

set -euo pipefail
source "$(dirname "$(readlink -f "$0")")/common.sh"

repo_path="${1:?usage: export-patches.sh <tree path, e.g. frameworks/base> <number of commits>}"
count="${2:?number of commits to export}"

out="$PATCHES/$repo_path"

mkdir -p "$out"
rm -f "$out"/*.patch
git -C "$TOP/$repo_path" format-patch --no-signature "-$count" -o "$out"
