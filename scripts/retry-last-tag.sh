#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/retry-last-tag.sh [--push]

Moves the highest existing semver tag in the form vX.Y.Z to the current HEAD commit.

Intended use:
- The latest release tag triggered CI/publish.
- The attempt failed before the version should be considered final.
- You committed a fix and want to retry the same version from the new HEAD.

Rules:
- Requires a clean git worktree.
- Recreates the latest annotated v* tag locally at HEAD.
- With --push, force-pushes the updated tag to origin.

Warning:
- Do not use this after that version has been successfully published for different code.
- Reusing a released version for a new commit creates an invalid release history.
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_clean_git() {
  if [[ -n "$(git status --short)" ]]; then
    echo "Git worktree is not clean. Commit or stash changes before retagging." >&2
    exit 1
  fi
}

latest_git_tag() {
  git tag --list 'v*' | rg '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1
}

push_tag="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --push)
      push_tag="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd git
require_cmd rg
require_clean_git

tag="$(latest_git_tag)"

if [[ -z "$tag" ]]; then
  echo "No semver tag matching vX.Y.Z found." >&2
  exit 1
fi

old_commit="$(git rev-list -n 1 "$tag")"
new_commit="$(git rev-parse HEAD)"

if [[ "$old_commit" == "$new_commit" ]]; then
  echo "Tag ${tag} already points to HEAD (${new_commit})."
  exit 0
fi

git tag -d "$tag" >/dev/null
git tag -a "$tag" -m "Retry ${tag}" "$new_commit"
echo "Moved ${tag} from ${old_commit} to ${new_commit}"

if [[ "$push_tag" == "true" ]]; then
  git push origin ":refs/tags/${tag}"
  git push origin "$tag"
  echo "Force-updated ${tag} on origin"
fi
