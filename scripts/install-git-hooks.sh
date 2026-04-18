#!/usr/bin/env bash

set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git config core.hooksPath .githooks
chmod +x .githooks/pre-push

echo "Configured git hooks path to .githooks"
echo "Installed pre-push hook"
