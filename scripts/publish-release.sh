#!/usr/bin/env bash
set -euo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
release_tag=${1:?Usage: publish-release.sh <tag> <asset-directory>}
asset_directory=${2:?Usage: publish-release.sh <tag> <asset-directory>}
release_info=$(node "$script_directory/release-info.mjs" "$release_tag" "$asset_directory")
prerelease=$(jq -r '.prerelease' <<< "$release_info")
mapfile -t assets < <(jq -r '.assets[]' <<< "$release_info")

if release_state=$(gh release view "$release_tag" --json isDraft); then
  if [[ $(jq -r '.isDraft' <<< "$release_state") != true ]]; then
    echo "Release is already published; refusing to replace its assets" >&2
    exit 1
  fi
else
  options=()
  if [[ "$prerelease" == true ]]; then options+=(--prerelease); fi
  gh release create "$release_tag" --verify-tag --draft --generate-notes --title "$release_tag" "${options[@]}"
fi

gh release upload "$release_tag" "${assets[@]}" --clobber
# 両成果物の添付が成功してから公開する。失敗時は再実行可能なdraftを残す。
latest=true
if [[ "$prerelease" == true ]]; then latest=false; fi
gh release edit "$release_tag" --draft=false --prerelease="$prerelease" --latest="$latest"
