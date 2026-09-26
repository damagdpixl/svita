#!/usr/bin/env bash
# IP hygiene guard for the Svita public repository.
# Fails (exit 1) if private research artifacts, third-party IP, or secrets
# appear in tracked files or in git history. Wired into CI.
set -u
fail=0

forbidden_path_patterns=(
  '^re/'
  '\.apk$'
  '^backend/'
  '^pipeline/'
  'api_contract\.md$'
  'outfit_algorithm_spec\.md$'
  'reference_Frontend'
  'svita-repo'
  'taxonomy_dump'
  '\.jks$'
  '\.keystore$'
  'keystore\.properties$'
)

echo "== tracked files scan =="
for p in "${forbidden_path_patterns[@]}"; do
  hits=$(git ls-files | grep -iE -- "$p" || true)
  if [ -n "$hits" ]; then
    echo "FORBIDDEN PATH tracked (pattern: $p):"
    echo "$hits"
    fail=1
  fi
done

echo "== tracked content scan =="
if git grep -iq 'cluise' -- . 2>/dev/null; then
  echo "FORBIDDEN STRING 'Cluise' found in tracked files:"
  git grep -il 'cluise' -- .
  fail=1
fi

echo "== history scan (files ever added) =="
hist=$(git log --all --diff-filter=A --name-only --format= | grep -iE "$(IFS='|'; echo "${forbidden_path_patterns[*]}")" | sort -u || true)
if [ -n "$hist" ]; then
  echo "FORBIDDEN PATH present in history:"
  echo "$hist"
  fail=1
fi

if [ "$fail" -eq 0 ]; then echo "IP hygiene: CLEAN"; fi
exit $fail
