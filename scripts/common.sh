#!/usr/bin/env bash

SCRIPTS="$(dirname "$(readlink -f "$0")")"
ROOT="$(dirname "$SCRIPTS")"
TOP="$(realpath "$ROOT/../..")"

APPS="$ROOT/apps"
CONFIG="$ROOT/config"
MANIFESTS="$ROOT/manifests"
OVERLAY="$ROOT/overlay"
PATCHES="$ROOT/patches"
SEPOLICY="$ROOT/sepolicy"
