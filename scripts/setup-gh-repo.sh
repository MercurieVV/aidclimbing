#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/setup-gh-repo.sh --repo OWNER/REPO [--op-item op://VAULT/ITEM]
  scripts/setup-gh-repo.sh --repo OWNER/REPO [--op-gpg-item op://VAULT/ITEM] [--op-sonatype-item op://VAULT/ITEM]
  scripts/setup-gh-repo.sh --repo MercurieVV/aidclimbing --op-gpg-item op://Personal/GPG_2 --op-sonatype-item op://Personal/SONATYPE_CREDS

This script configures a GitHub repository with:

Environments:
  github-pages -> branches master and gh-pages
  main         -> only branch main

Repository secrets:
  PGP_PASSPHRASE
  PGP_SECRET
  SONATYPE_CREDENTIAL_HOST
  SONATYPE_PASSWORD
  SONATYPE_USERNAME

Repository variable:
  SONATYPE_USERNAME=TSwfhAIy

Security settings:
  Enables vulnerability alerts, which also enables the dependency graph.

Secret sources:
1. Plain environment variables:
   PGP_PASSPHRASE
   PGP_SECRET
   SONATYPE_CREDENTIAL_HOST
   SONATYPE_PASSWORD
   SONATYPE_USERNAME

2. Explicit 1Password refs:
   OP_PGP_PASSPHRASE_REF
   OP_PGP_SECRET_REF
   OP_SONATYPE_CREDENTIAL_HOST_REF
   OP_SONATYPE_PASSWORD_REF
   OP_SONATYPE_USERNAME_REF

3. A single 1Password item base ref:
   --op-item op://VAULT/ITEM
   This derives refs like:
     op://VAULT/ITEM/PGP_PASSPHRASE
     op://VAULT/ITEM/PGP_SECRET
     ...

4. Two separate 1Password item base refs:
   --op-gpg-item op://VAULT/ITEM
   --op-sonatype-item op://VAULT/ITEM
   This derives refs like:
     op://VAULT/GPG_ITEM/PGP_PASSPHRASE
     op://VAULT/GPG_ITEM/PGP_SECRET
     op://VAULT/SONATYPE_ITEM/SONATYPE_CREDENTIAL_HOST
     op://VAULT/SONATYPE_ITEM/SONATYPE_PASSWORD
     op://VAULT/SONATYPE_ITEM/SONATYPE_USERNAME

Examples:
  scripts/setup-gh-repo.sh --repo MercurieVV/aidclimbing

  OP_PGP_PASSPHRASE_REF='op://ci/aidclimbing-release/PGP_PASSPHRASE' \
  OP_PGP_SECRET_REF='op://ci/aidclimbing-release/PGP_SECRET' \
  OP_SONATYPE_CREDENTIAL_HOST_REF='op://ci/aidclimbing-release/SONATYPE_CREDENTIAL_HOST' \
  OP_SONATYPE_PASSWORD_REF='op://ci/aidclimbing-release/SONATYPE_PASSWORD' \
  OP_SONATYPE_USERNAME_REF='op://ci/aidclimbing-release/SONATYPE_USERNAME' \
  scripts/setup-gh-repo.sh --repo MercurieVV/aidclimbing

  scripts/setup-gh-repo.sh --repo MercurieVV/aidclimbing --op-item 'op://ci/aidclimbing-release'

  scripts/setup-gh-repo.sh --repo MercurieVV/aidclimbing \
    --op-gpg-item 'op://ci/aidclimbing-gpg' \
    --op-sonatype-item 'op://ci/aidclimbing-sonatype'
EOF
}

repo=""
op_item=""
op_gpg_item=""
op_sonatype_item=""
repo_var_sonatype_username="${REPO_VAR_SONATYPE_USERNAME:-TSwfhAIy}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo="${2:-}"
      shift 2
      ;;
    --op-item)
      op_item="${2:-}"
      shift 2
      ;;
    --op-gpg-item)
      op_gpg_item="${2:-}"
      shift 2
      ;;
    --op-sonatype-item)
      op_sonatype_item="${2:-}"
      shift 2
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

if [[ -z "$repo" ]]; then
  echo "--repo is required" >&2
  usage >&2
  exit 1
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd gh

gh_api() {
  gh api -H "Accept: application/vnd.github+json" "$@"
}

gh_secret_set() {
  local name="$1"
  local value="$2"
  gh secret set "$name" --repo "$repo" --body "$value"
}

gh_variable_set() {
  local name="$1"
  local value="$2"
  gh variable set "$name" --repo "$repo" --body "$value"
}

enable_dependency_graph() {
  gh_api --method PUT "/repos/${repo}/vulnerability-alerts" >/dev/null
  echo "Enabled vulnerability alerts and dependency graph"
}

resolve_value() {
  local env_name="$1"
  local op_ref_name="$2"
  local derived_op_ref=""
  local item_base=""

  if [[ -n "${!env_name:-}" ]]; then
    printf '%s' "${!env_name}"
    return 0
  fi

  if [[ -n "${!op_ref_name:-}" ]]; then
    require_cmd op
    op read "${!op_ref_name}"
    return 0
  fi

  case "$env_name" in
    PGP_PASSPHRASE|PGP_SECRET)
      item_base="$op_gpg_item"
      ;;
    SONATYPE_CREDENTIAL_HOST|SONATYPE_PASSWORD|SONATYPE_USERNAME)
      item_base="$op_sonatype_item"
      ;;
  esac

  if [[ -n "$item_base" ]]; then
    require_cmd op
    derived_op_ref="${item_base}/${env_name}"
    op read "$derived_op_ref"
    return 0
  fi

  if [[ -n "$op_item" ]]; then
    require_cmd op
    derived_op_ref="${op_item}/${env_name}"
    op read "$derived_op_ref"
    return 0
  fi

  echo "Missing value for $env_name. Set $env_name, $op_ref_name, --op-item, or the matching --op-gpg-item/--op-sonatype-item." >&2
  exit 1
}

set_secret() {
  local secret_name="$1"
  local op_ref_name="OP_${secret_name}_REF"
  local value

  value="$(resolve_value "$secret_name" "$op_ref_name")"
  gh_secret_set "$secret_name" "$value"
  echo "Set repo secret $secret_name"
}

ensure_environment() {
  local env_name="$1"

  gh_api --method PUT "/repos/${repo}/environments/${env_name}" \
    -F "deployment_branch_policy[protected_branches]=false" \
    -F "deployment_branch_policy[custom_branch_policies]=true" \
    >/dev/null

  echo "Ensured environment ${env_name}"
}

clear_branch_policies() {
  local env_name="$1"
  local ids

  ids="$(gh_api "/repos/${repo}/environments/${env_name}/deployment-branch-policies" --jq '.branch_policies[]?.id')"

  if [[ -z "$ids" ]]; then
    return 0
  fi

  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    gh_api --method DELETE "/repos/${repo}/environments/${env_name}/deployment-branch-policies/${id}" >/dev/null
  done <<< "$ids"
}

set_branch_policy() {
  local env_name="$1"
  local branch_name="$2"

  clear_branch_policies "$env_name"
  gh_api --method POST "/repos/${repo}/environments/${env_name}/deployment-branch-policies" \
    -f name="$branch_name" \
    -f type="branch" \
    >/dev/null

  echo "Set environment ${env_name} branch policy to ${branch_name}"
}

ensure_environment "github-pages"
set_branch_policy "github-pages" "master"
gh_api --method POST "/repos/${repo}/environments/github-pages/deployment-branch-policies" \
  -f name="gh-pages" \
  -f type="branch" \
  >/dev/null
echo "Set environment github-pages branch policy to gh-pages"

ensure_environment "main"
set_branch_policy "main" "main"

set_secret "PGP_PASSPHRASE"
set_secret "PGP_SECRET"
set_secret "SONATYPE_CREDENTIAL_HOST"
set_secret "SONATYPE_PASSWORD"
set_secret "SONATYPE_USERNAME"

gh_variable_set "SONATYPE_USERNAME" "$repo_var_sonatype_username"
echo "Set repo variable SONATYPE_USERNAME=${repo_var_sonatype_username}"

enable_dependency_graph
