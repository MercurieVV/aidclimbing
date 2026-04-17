#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/check-push-workflow.sh [--workflow NAME] [--branch BRANCH] [--repo OWNER/REPO]
  scripts/check-push-workflow.sh --all [--branch BRANCH] [--repo OWNER/REPO]

Waits for the latest push-triggered GitHub Actions workflow run to complete.
Exits with status 0 if it succeeds, nonzero otherwise.

Defaults:
  workflow: Continuous Integration
  branch: current git branch
  repo: inferred from the current git remote via gh

With --all, checks both:
  Clean
  Continuous Integration
EOF
}

workflow_name="Continuous Integration"
branch_name=""
repo=""
check_all="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workflow)
      workflow_name="${2:-}"
      shift 2
      ;;
    --branch)
      branch_name="${2:-}"
      shift 2
      ;;
    --repo)
      repo="${2:-}"
      shift 2
      ;;
    --all)
      check_all="true"
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

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd gh
require_cmd git

if [[ -z "$branch_name" ]]; then
  branch_name="$(git branch --show-current)"
fi

if [[ -z "$branch_name" ]]; then
  echo "Could not determine branch name. Pass --branch explicitly." >&2
  exit 1
fi

gh_run_list() {
  if [[ -n "$repo" ]]; then
    gh run list --repo "$repo" "$@"
  else
    gh run list "$@"
  fi
}

gh_run_watch() {
  local run_id="$1"
  shift

  if [[ -n "$repo" ]]; then
    gh run watch --repo "$repo" "$run_id" "$@"
  else
    gh run watch "$run_id" "$@"
  fi
}

watch_workflow() {
  local wf_name="$1"
  local run_id

  run_id="$(
    gh_run_list \
      --workflow "$wf_name" \
      --branch "$branch_name" \
      --event push \
      --limit 1 \
      --json databaseId \
      --jq '.[0].databaseId'
  )"

  if [[ -z "$run_id" || "$run_id" == "null" ]]; then
    echo "No push-triggered workflow run found for workflow '$wf_name' on branch '$branch_name'." >&2
    return 1
  fi

  echo "Watching workflow '$wf_name' run $run_id on branch '$branch_name'..."
  gh_run_watch "$run_id" --exit-status
}

if [[ "$check_all" == "true" ]]; then
  watch_workflow "Clean"
  watch_workflow "Continuous Integration"
else
  watch_workflow "$workflow_name"
fi
