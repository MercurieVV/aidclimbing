#!/usr/bin/env bash

set -euo pipefail

scripts/bump-version.sh minor "$@"
