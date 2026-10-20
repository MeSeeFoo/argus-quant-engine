# shellcheck shell=bash
# Shared by the Claude Code hooks: reads the JSON Claude Code sends on stdin
# without jq, which Git Bash does not ship.
#
# Hooks only need a few fields, each with a unique name (file_path, command,
# cwd, stop_hook_active). A key only matches where it really starts a key:
# inside another string value its quotes are escaped (\"file_path\"), so the
# pattern below does not match there.

# Reads the hook input once. JSON whitespace includes newlines; joining the
# lines keeps every field on one line for grep.
hook_input=$(tr '\n' ' ')

# json_string <name>: the value of a string field, with \" and \\ unescaped.
json_string() {
  printf '%s' "$hook_input" \
    | grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"\\\\]\|\\\\.\)*\"" \
    | head -n 1 \
    | sed -e "s/^\"$1\"[[:space:]]*:[[:space:]]*\"//" -e 's/"$//' \
          -e 's/\\"/"/g' -e 's/\\\\/\\/g'
}

# json_true <name>: succeeds if a boolean field is true.
json_true() {
  printf '%s' "$hook_input" | grep -q "\"$1\"[[:space:]]*:[[:space:]]*true"
}

# is_generated <path>: succeeds for build output and generated code.
is_generated() {
  case ${1//\\//} in
    */target/* | target/* | */generated-sources/* | generated-sources/*) return 0 ;;
    *) return 1 ;;
  esac
}
