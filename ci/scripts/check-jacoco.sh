#!/usr/bin/env bash
# Fails unless the JaCoCo XML report shows line >= MIN_LINE_COVERAGE and branch >= MIN_BRANCH_COVERAGE.
# Usage: check-jacoco.sh <path/to/jacoco.xml>
set -euo pipefail
report="${1:?usage: check-jacoco.sh <jacoco.xml>}"
min_line="${MIN_LINE_COVERAGE:-95}"
min_branch="${MIN_BRANCH_COVERAGE:-90}"

if [ ! -f "$report" ]; then
  echo "::error::JaCoCo report not found at $report. The backend build must produce a merged unit+IT report (backend Phase 0 task)."
  exit 1
fi

# Report-level counters are the last <counter> elements directly under <report>.
read -r line_missed line_covered branch_missed branch_covered < <(
  tr '\n' ' ' < "$report" | sed 's/<package .*<\/package>//g' |
  awk '{
    lm=lc=bm=bc=0
    while (match($0, /<counter type="(LINE|BRANCH)" missed="[0-9]+" covered="[0-9]+"\/>/)) {
      c=substr($0, RSTART, RLENGTH); $0=substr($0, RSTART+RLENGTH)
      split(c, a, "\""); if (a[2]=="LINE") {lm=a[4]; lc=a[6]} else {bm=a[4]; bc=a[6]}
    }
    print lm, lc, bm, bc
  }'
)

pct() { awk -v c="$1" -v m="$2" 'BEGIN { t=c+m; if (t==0) print "100.00"; else printf "%.2f", 100*c/t }'; }
line_pct=$(pct "$line_covered" "$line_missed")
branch_pct=$(pct "$branch_covered" "$branch_missed")
echo "Backend coverage: line ${line_pct}% (min ${min_line}%), branch ${branch_pct}% (min ${min_branch}%)"
echo "### Backend coverage: line ${line_pct}% · branch ${branch_pct}%" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

fail=0
awk -v p="$line_pct" -v m="$min_line" 'BEGIN { exit !(p+0 < m+0) }' && { echo "::error::Backend line coverage ${line_pct}% < ${min_line}%"; fail=1; }
awk -v p="$branch_pct" -v m="$min_branch" 'BEGIN { exit !(p+0 < m+0) }' && { echo "::error::Backend branch coverage ${branch_pct}% < ${min_branch}%"; fail=1; }
exit $fail
