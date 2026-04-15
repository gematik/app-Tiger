#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PRECOMMIT_SRC="$ROOT_DIR/githooks/pre-commit"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_file_exists() {
  [ -f "$1" ] || fail "Expected file to exist: $1"
}

assert_file_not_exists() {
  [ ! -f "$1" ] || fail "Expected file to not exist: $1"
}

assert_file_contains() {
  grep -F -- "$2" "$1" >/dev/null 2>&1 || fail "Expected $1 to contain: $2"
}

assert_file_not_contains() {
  if grep -F -- "$2" "$1" >/dev/null 2>&1; then
    fail "Expected $1 to not contain: $2"
  fi
}

setup_repo() {
  local temp_dir
  temp_dir="$(mktemp -d -t tiger-precommit-test.XXXXXX)"

  (
    cd "$temp_dir"
    git init -q
    git config user.email "test@example.com"
    git config user.name "Test User"

    mkdir -p .git/hooks githooks
    cp "$PRECOMMIT_SRC" .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit

    cat > githooks/spotless-changed.sh <<'S'
#!/bin/bash
if [ "$#" -gt 0 ]; then
  printf "%s\n" "$@" > "$PWD/.spotless.args"
fi
S
    chmod +x githooks/spotless-changed.sh

    cat > githooks/npx <<'NPX'
#!/bin/bash
if [ "$#" -gt 0 ]; then
  printf "%s\n" "$@" > "$PWD/.eslint.args"
fi
NPX
    chmod +x githooks/npx

    cat > githooks/check-branch-name.sh <<'C'
#!/bin/bash
exit 0
C
    chmod +x githooks/check-branch-name.sh
  )

  echo "$temp_dir"
}

run_precommit() {
  local repo_dir="$1"
  (
    cd "$repo_dir"
    export PATH="$repo_dir/githooks:$PATH"
    .git/hooks/pre-commit
  )
}

cleanup_repo() {
  rm -rf "$1"
}

test_deleted_file_not_formatted_or_linted() {
  local repo_dir
  repo_dir="$(setup_repo)"

  (
    cd "$repo_dir"
    echo "base" > a.txt
    git add a.txt
    git commit --no-verify -m "base" -q

    rm a.txt
    git add -u
  )

  run_precommit "$repo_dir"


  assert_file_not_exists "$repo_dir/.spotless.args"
  assert_file_not_exists "$repo_dir/.eslint.args"

  cleanup_repo "$repo_dir"
  echo "OK: deleted file is skipped"
}

test_eslint_paths_are_relative_to_module() {
  local repo_dir
  repo_dir="$(setup_repo)"

  (
    cd "$repo_dir"
    mkdir -p tiger-testenv-mgr/src/frontend
    printf "const x = 1;\n" > tiger-testenv-mgr/src/frontend/x.ts
    git add tiger-testenv-mgr/src/frontend/x.ts
  )

  run_precommit "$repo_dir"

  assert_file_exists "$repo_dir/tiger-testenv-mgr/src/frontend/.eslint.args"
  assert_file_contains "$repo_dir/tiger-testenv-mgr/src/frontend/.eslint.args" "eslint"
  assert_file_contains "$repo_dir/tiger-testenv-mgr/src/frontend/.eslint.args" "--fix"
  assert_file_contains "$repo_dir/tiger-testenv-mgr/src/frontend/.eslint.args" "x.ts"
  assert_file_not_contains "$repo_dir/tiger-testenv-mgr/src/frontend/.eslint.args" "tiger-testenv-mgr/src/frontend/x.ts"

  cleanup_repo "$repo_dir"
  echo "OK: eslint receives relative paths"
}

test_deleted_file_not_formatted_or_linted
test_eslint_paths_are_relative_to_module

echo "All pre-commit tests passed."
