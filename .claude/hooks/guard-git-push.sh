#!/usr/bin/env bash
# PreToolUse hook for Bash and PowerShell: blocks the pushes that
# CONTRIBUTING.md forbids.
#
# A force-push rewrites commits that are already pushed, and main only changes
# through pull requests. The deny rules in settings.json catch the usual
# spellings; this hook also reads the arguments, so it catches -fu, +branch
# refspecs and a bare "git push" while main is checked out. The ruleset that
# protects main on GitHub remains the real boundary.
set -eu
# shellcheck source=lib.sh
. "$(dirname "$0")/lib.sh"

cwd=$(json_string cwd)
[ -n "$cwd" ] || cwd=.

block() {
  {
    echo "Blocked: $1."
    echo "Pushed commits are never rewritten and main only changes through a pull request:"
    echo "see CONTRIBUTING.md (Branches). To update a branch with main, merge main into it."
  } >&2
  exit 2
}

current_branch() {
  git -C "$1" branch --show-current 2>/dev/null || true
}

is_main() {
  [ "$1" = main ] || [ "$1" = refs/heads/main ]
}

# check_push <word>...: blocks if the words are a forbidden git push.
check_push() {
  while [ $# -gt 0 ] && [ "$1" != git ]; do shift; done
  [ $# -gt 0 ] || return 0
  shift

  # git's own options, before the subcommand.
  local dir=$cwd
  while [ $# -gt 0 ]; do
    case $1 in
      -C)
        case ${2:-} in
          /* | [A-Za-z]:*) dir=${2:-} ;;
          *) dir=$cwd/${2:-} ;;
        esac
        shift 2 || shift
        ;;
      -c | --git-dir | --work-tree | --namespace) shift 2 || shift ;;
      -*) shift ;;
      *) break ;;
    esac
  done
  [ "${1:-}" = push ] || return 0
  shift

  # The options of git push; the remote and the refspecs are kept in order.
  local remote="" refspecs=""
  while [ $# -gt 0 ]; do
    case $1 in
      --force* | --mirror) block "force-pushes are not allowed ($1)" ;;
      --all | --branches) block "pushing every branch would push main too ($1)" ;;
      -o | --push-option | --repo | --receive-pack | --exec) shift ;;
      --*) ;;
      -*f*) block "force-pushes are not allowed ($1)" ;;
      -*) ;;
      *)
        if [ -z "$remote" ]; then remote=$1; else refspecs="$refspecs $1"; fi
        ;;
    esac
    [ $# -gt 0 ] && shift
  done

  local refspec destination
  # shellcheck disable=SC2086 # one refspec per word
  for refspec in $refspecs; do
    case $refspec in
      +*) block "force-pushes are not allowed ($refspec)" ;;
    esac
    destination=${refspec#*:}
    if [ "$destination" = HEAD ] || [ "$destination" = @ ]; then
      destination=$(current_branch "$dir")
    fi
    if is_main "$destination"; then
      block "direct pushes to main are not allowed ($refspec)"
    fi
  done
  if [ -z "$refspecs" ] && is_main "$(current_branch "$dir")"; then
    block "this would push main, which is checked out"
  fi
}

# One command per line: split on &&, ||, ;, | and escaped newlines. Process
# substitution keeps the loop in this shell, so block() exits the hook.
set -f
while IFS= read -r segment; do
  # shellcheck disable=SC2086 # split into words on purpose
  set -- $segment
  words=""
  for word in "$@"; do
    word=${word#[\"\']}
    words="$words ${word%[\"\']}"
  done
  # shellcheck disable=SC2086
  check_push $words
done < <(json_string command | awk '{ gsub(/&&|\|\||[;|]|\\n/, "\n"); print }')
