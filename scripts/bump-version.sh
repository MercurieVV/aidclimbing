#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/bump-version.sh [patch|minor|major] [--push]

Creates the next annotated semver tag in the form vX.Y.Z.

Rules:
- Uses the highest existing v* semver tag if present.
- If no v* semver tag exists, creates ThisBuild / tlBaseVersion from build.sbt as the first release tag.
- Creates the tag locally.
- With --push, also pushes the new tag to origin.
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
    echo "Git worktree is not clean. Commit or stash changes before tagging." >&2
    exit 1
  fi
}

normalize_version() {
  local version="$1"
  IFS='.' read -r major minor patch <<< "$version"
  : "${major:=0}"
  : "${minor:=0}"
  : "${patch:=0}"
  printf '%s.%s.%s' "$major" "$minor" "$patch"
}

latest_git_tag_version() {
  git tag --list 'v*' | sed 's/^v//' | rg '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1
}

base_version_from_build() {
  local raw
  raw="$(sed -n 's/.*ThisBuild \/ tlBaseVersion *:= *"\([^"]*\)".*/\1/p' build.sbt | head -n 1)"

  if [[ -z "$raw" ]]; then
    echo "Could not determine tlBaseVersion from build.sbt" >&2
    exit 1
  fi

  normalize_version "$raw"
}

next_version() {
  local current="$1"
  local bump="$2"
  local major minor patch

  IFS='.' read -r major minor patch <<< "$current"

  case "$bump" in
    patch)
      patch=$((patch + 1))
      ;;
    minor)
      minor=$((minor + 1))
      patch=0
      ;;
    major)
      major=$((major + 1))
      minor=0
      patch=0
      ;;
    *)
      echo "Unknown bump type: $bump" >&2
      exit 1
      ;;
  esac

  printf '%s.%s.%s' "$major" "$minor" "$patch"
}

bump_type="${1:-}"
push_tag="false"

if [[ "$bump_type" == "-h" || "$bump_type" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$bump_type" ]]; then
  usage >&2
  exit 1
fi

shift || true

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

current_version="$(latest_git_tag_version)"
if [[ -z "$current_version" ]]; then
  new_version="$(base_version_from_build)"
else
  new_version="$(next_version "$current_version" "$bump_type")"
fi

new_tag="v${new_version}"

if git rev-parse -q --verify "refs/tags/${new_tag}" >/dev/null; then
  echo "Tag ${new_tag} already exists." >&2
  exit 1
fi

git tag -a "$new_tag" -m "Release ${new_tag}"
echo "Created tag ${new_tag}"

if [[ "$push_tag" == "true" ]]; then
  git push origin "$new_tag"
  echo "Pushed tag ${new_tag} to origin"
fi
