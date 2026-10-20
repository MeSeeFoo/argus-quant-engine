#!/usr/bin/env bash
# Runs every hook the way Claude Code does (the JSON input on stdin) and checks
# its verdict. Run it from the repository root with
# "bash .claude/hooks/test-hooks.sh"; CI runs it on Linux and Windows.
#
# Like the Stop hook it tests, it also formats any other Java file changed in
# the working tree.
set -eu

hooks=$(cd "$(dirname "$0")" && pwd)
root=$(git rev-parse --show-toplevel)
failures=0

# A string as a JSON value.
quote() {
  printf '"%s"' "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
}

# expect <name> <exit code> <text in stderr> <hook> <json>
expect() {
  local status=0 stderr
  stderr=$(printf '%s' "$5" | bash "$hooks/$4" 2>&1 >/dev/null) || status=$?
  if [ "$status" != "$2" ] || [[ $stderr != *"$3"* ]]; then
    echo "FAIL $1: exit $status, expected $2"
    [ -z "$stderr" ] || printf '%s\n' "$stderr" | sed 's/^/    /'
    failures=$((failures + 1))
  fi
}

edit() {
  printf '{"tool_name":"Edit","tool_input":{"file_path":%s,"old_string":"a"}}' "$(quote "$1")"
}

command_in() {
  printf '{"tool_name":"Bash","tool_input":{"command":%s},"cwd":%s}' "$(quote "$1")" "$(quote "$2")"
}

stop() {
  printf '{"hook_event_name":"Stop","stop_hook_active":%s,"cwd":%s}' "$1" "$(quote "$root")"
}

# protect-generated-code.sh
sbe='C:\repo\contracts\target\generated-sources\sbe\com\argus\contracts\sbe\Trade.java'
proto=/repo/contracts/target/generated-sources/protobuf/com/argus/Trade.java
expect "edit an SBE codec (Windows path)" 2 argus-fastpath.xml protect-generated-code.sh "$(edit "$sbe")"
expect "edit a Protobuf class" 2 contracts/src/main/protobuf protect-generated-code.sh "$(edit "$proto")"
expect "edit build output" 2 "./mvnw verify" protect-generated-code.sh "$(edit /repo/libs/a/target/classes/a.txt)"
expect "edit a schema" 0 "" protect-generated-code.sh "$(edit /repo/contracts/src/main/protobuf/a.proto)"
expect "a path in the content does not count" 0 "" protect-generated-code.sh \
  '{"tool_name":"Write","tool_input":{"content":"\"file_path\": \"/x/target/y\"","file_path":"/repo/A.java"}}'

# guard-git-push.sh
blocked=(
  "git push --force"
  "git push -f origin feat/1-x"
  "git push -fu origin feat/1-x"
  "git push --force-with-lease origin feat/1-x"
  "git push origin +feat/1-x"
  "git push origin main"
  "git push origin HEAD:main"
  "git push origin feat/1-x:refs/heads/main"
  "git push --all origin"
  "git status && git -C . push --mirror"
  "cd /tmp; git push --force"
)
for command in "${blocked[@]}"; do
  expect "$command" 2 Blocked guard-git-push.sh "$(command_in "$command" "$root")"
done
allowed=(
  "git push -u origin feat/1-x"
  "git push origin feat/1-fix-main-link"
  "git push -o ci.skip origin feat/1-x"
  "git status"
  "./mvnw verify"
)
for command in "${allowed[@]}"; do
  expect "$command" 0 "" guard-git-push.sh "$(command_in "$command" "$root")"
done
repo=$(mktemp -d)
git init -q -b main "$repo"
expect "git push while main is checked out" 2 main guard-git-push.sh "$(command_in "git push" "$repo")"
expect "git push origin while main is checked out" 2 main guard-git-push.sh "$(command_in "git push origin" "$repo")"
rm -rf "$repo"

# format-java.sh
sample=$root/libs/architecture-rules/src/test/java/com/argus/architecture/HookTestSample.java
trap 'rm -f "$sample"' EXIT
printf 'package com.argus.architecture;\nclass HookTestSample {   int   x =1;  }\n' >"$sample"
expect "format changed Java files" 0 "" format-java.sh "$(stop false)"
expected=$(printf 'package com.argus.architecture;\n\nclass HookTestSample {\n  int x = 1;\n}\n')
if [ "$(cat "$sample")" != "$expected" ]; then
  echo "FAIL format changed Java files: unexpected result"
  sed 's/^/    /' "$sample"
  failures=$((failures + 1))
fi
printf 'package com.argus.architecture;\nclass HookTestSample {\n' >"$sample"
expect "report a file that does not parse" 2 Spotless format-java.sh "$(stop false)"
expect "do not loop on that file" 1 Spotless format-java.sh "$(stop true)"

if [ "$failures" -gt 0 ]; then
  echo "$failures hook check(s) failed."
  exit 1
fi
echo "Every hook behaves as expected."
