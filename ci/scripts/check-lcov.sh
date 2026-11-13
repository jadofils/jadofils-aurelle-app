#!/usr/bin/env bash
# Fails unless lcov line coverage >= MIN_LINE_COVERAGE (and branch >= MIN_BRANCH_COVERAGE when branch data exists).
# Generated files are excluded, matching sonar.coverage.exclusions.
# Usage: check-lcov.sh <path/to/lcov.info>
set -euo pipefail
report="${1:?usage: check-lcov.sh <lcov.info>}"
min_line="${MIN_LINE_COVERAGE:-95}"
min_branch="${MIN_BRANCH_COVERAGE:-90}"

if [[ ! -f "$report" ]]; then
  echo "::error::lcov report not found at $report (run: flutter test --coverage)."
  exit 1
fi

read -r lf lh brf brh < <(awk '
  /^SF:/ { skip = ($0 ~ /\.(g|freezed|graphql|gr)\.dart$/ || $0 ~ /lib\/main\.dart$/) }
  !skip && /^LF:/  { lf  += substr($0, 4) }
  !skip && /^LH:/  { lh  += substr($0, 4) }
  !skip && /^BRF:/ { brf += substr($0, 5) }
  !skip && /^BRH:/ { brh += substr($0, 5) }
  END { print lf+0, lh+0, brf+0, brh+0 }' "$report")

pct() { local covered="$1" total="$2"; awk -v c="$covered" -v t="$total" 'BEGIN { if (t==0) print "100.00"; else printf "%.2f", 100*c/t }'; }
line_pct=$(pct "$lh" "$lf")
echo "Frontend coverage: line ${line_pct}% (${lh}/${lf}, min ${min_line}%)"
echo "### Frontend coverage: line ${line_pct}%" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

fail=0
awk -v p="$line_pct" -v m="$min_line" 'BEGIN { exit !(p+0 < m+0) }' && { echo "::error::Frontend line coverage ${line_pct}% < ${min_line}%"; fail=1; }
if [[ "$brf" -gt 0 ]]; then
  branch_pct=$(pct "$brh" "$brf")
  echo "Frontend branch coverage: ${branch_pct}% (min ${min_branch}%)"
  awk -v p="$branch_pct" -v m="$min_branch" 'BEGIN { exit !(p+0 < m+0) }' && { echo "::error::Frontend branch coverage ${branch_pct}% < ${min_branch}%"; fail=1; }
fi
exit $fail
