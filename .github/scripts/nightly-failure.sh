#!/usr/bin/env bash
# Turns the failures of nightly.yml into issues labelled nightly-failure, one per
# job and distinct error. See docs/ci.md.
#
#   nightly-failure.sh describe <fingerprint> <details file>
#       In a failing job: sets the step outputs `key` (a hash of the fingerprint)
#       and `details` (the Markdown file, shortened if needed) and adds the details
#       to the job summary. The fingerprint names the error: advisory IDs, broken
#       URLs, failing test classes.
#
#   nightly-failure.sh report <job id> <job name>
#       In the report job, once per checked job, with RESULT, KEY and DETAILS from
#       that job, and RUN_URL, GITHUB_REPOSITORY and GH_TOKEN:
#       - failure, same key as an open issue: comment on it, nothing new;
#       - failure, new key: open an issue, close the job's other open ones;
#       - success: close the job's open issues.
set -euo pipefail

readonly label=nightly-failure
# GitHub rejects issue bodies above 65,536 characters; leave room for the rest.
readonly max_details=60000

describe() {
  local fingerprint=$1 details_file=$2 key details delimiter
  key=$(printf '%s' "$fingerprint" | sha256sum | cut -c1-12)
  details=$(head -c "$max_details" "$details_file")
  if [ "$(wc -c < "$details_file")" -gt "$max_details" ]; then
    details+=$'\n\n*(Shortened: the full report is in the run.)*'
  fi
  delimiter="EOF_$key$RANDOM$RANDOM"
  {
    echo "key=$key"
    echo "details<<$delimiter"
    printf '%s\n' "$details"
    echo "$delimiter"
  } >> "$GITHUB_OUTPUT"
  printf '%s\n' "$details" >> "$GITHUB_STEP_SUMMARY"
}

# Open issues of the job, one "number key" line each.
open_issues() {
  JOB_MARKER="<!-- nightly-job: $1 -->" gh issue list --repo "$GITHUB_REPOSITORY" \
    --label "$label" --state open --limit 100 --json number,body \
    --jq '.[] | select(.body | contains(env.JOB_MARKER))
          | "\(.number) \((.body | capture("nightly-key: (?<k>[0-9a-f]+)") | .k) // "none")"'
}

report() {
  local job=$1 name=$2 number key url
  case "$RESULT" in
    success)
      open_issues "$job" | while read -r number key; do
        gh issue close "$number" --repo "$GITHUB_REPOSITORY" \
          --comment "Fixed: **$name** passed in $RUN_URL."
      done
      ;;
    failure)
      local current=${KEY:-unknown}
      local issues
      issues=$(open_issues "$job")
      while read -r number key; do
        if [ "$key" = "$current" ]; then
          gh issue comment "$number" --repo "$GITHUB_REPOSITORY" \
            --body "Still failing with the same error: $RUN_URL"
          return
        fi
      done <<< "$issues"

      url=$(gh issue create --repo "$GITHUB_REPOSITORY" --label "$label" \
        --title "Nightly: $name fails" \
        --body "<!-- nightly-job: $job -->
<!-- nightly-key: $current -->
**$name** failed in the nightly pipeline: $RUN_URL

${DETAILS:-No details: see the run.}

---
*Managed by \`nightly.yml\`. The same error comments here, a different one opens a new issue and closes this one, and the next run where **$name** passes closes it.*")
      echo "Opened $url"

      while read -r number key; do
        [ -n "$number" ] || continue
        gh issue close "$number" --repo "$GITHUB_REPOSITORY" \
          --comment "Superseded by #${url##*/}: **$name** now fails with a different error."
      done <<< "$issues"
      ;;
    *)
      echo "$name: $RESULT, nothing to report."
      ;;
  esac
}

command=$1
shift
case "$command" in
  describe) describe "$@" ;;
  report) report "$@" ;;
  *) echo "Unknown command: $command" >&2; exit 2 ;;
esac
