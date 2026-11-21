#!/usr/bin/env bash
# Micro-tests of spec-writing wording: one prompt, one fresh agent, no tools.
#   spec-micro.sh <label> <skill-file> <runs>
# Output: results/spec-micro-<label>/p<n>-r<run>.md
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
label="$1" skill="$2" runs="$3"
out="$HERE/results/spec-micro-$label"
mkdir -p "$out"
area=$(mktemp)
{ echo "# Auth"; echo; echo "*Informative.* The auth service issues and rotates tokens."; echo
  for i in $(seq 1 150); do echo "- The auth service MUST keep rule number $i. \`[auth.rule-$i]\`"; echo; done; } >"$area"
for n in 1 2 3 4; do
    prompt=$(cat "$HERE/prompts/spec-micro-$n.md")
    [[ $n -eq 4 ]] && prompt="$prompt"$'\n\n'"$(cat "$area")"
    for r in $(seq 1 "$runs"); do
        (cd "$(mktemp -d)" && claude -p "$prompt" --model sonnet --system-prompt "$(cat "$skill")" --tools "" \
            --setting-sources project --disable-slash-commands --strict-mcp-config </dev/null \
            >"$out/p$n-r$r.md" 2>/dev/null) &
    done
done
wait
rm -f "$area"
echo "spec-micro: $out"
