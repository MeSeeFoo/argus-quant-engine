#!/usr/bin/env bash
# Stop hook: formats every Java file changed in the working tree when Claude
# finishes a turn.
#
# It runs spotless:apply once, on the changed and untracked .java files only,
# through the Maven Wrapper, so the formatter and its settings are exactly the
# ones ./mvnw verify checks. Formatting once per turn instead of after every
# edit avoids starting Maven for each change. The repository is found from the
# session's working directory, so it also works in git worktrees.
#
# A file that does not parse is reported back to Claude (exit code 2), which
# keeps working to fix it. If it still fails on the turn that fix produced, the
# error is only shown (exit code 1), so Claude never loops on it.
set -eu
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

root=$(json_string cwd)
cd "${root:-.}"
root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root"
[ -f mvnw ] && [ -d .mvn ] || exit 0

# Modified, added and untracked Java sources. -z keeps paths unquoted; the
# entry after a rename or a copy is its original path, which is skipped.
patterns=""
skip_next=false
while IFS= read -r -d '' entry; do
  if $skip_next; then skip_next=false; continue; fi
  status=${entry:0:2}
  path=${entry:3}
  case $status in *R* | *C*) skip_next=true ;; esac
  case $path in *.java) ;; *) continue ;; esac
  is_generated "$path" && continue
  [ -f "$path" ] || continue
  # Every character other than a letter, a digit, _ or - becomes ".", which
  # matches both path separators and needs no quoting.
  patterns="$patterns,.*$(printf '%s' "$path" | sed 's/[^A-Za-z0-9_-]/./g')"
done < <(git status --porcelain -z --untracked-files=all)
[ -n "$patterns" ] || exit 0

if output=$(./mvnw -q -B spotless:apply "-DspotlessFiles=${patterns#,}" 2>&1); then
  exit 0
fi
{
  echo "Could not format the changed Java files with Spotless:"
  case $output in
    *'[ERROR]'*) printf '%s\n' "$output" | grep -F '[ERROR]' | head -n 30 ;;
    *) printf '%s\n' "$output" | head -n 30 ;;
  esac
} >&2
if json_true stop_hook_active; then exit 1; else exit 2; fi
