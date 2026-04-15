#!/bin/bash
# This script copies the versioned hooks from githooks/ to .git/hooks/ and makes them executable.

set -e

HOOKS=(pre-commit pre-push)
BACKUP_DIR=.git/hooks/.backup
TIMESTAMP=$(date +%Y%m%d%H%M%S)

if [ ! -d .git/hooks ]; then
  echo "Error: .git/hooks not found. Run this from the repo root." >&2
  exit 1
fi

for hook in "${HOOKS[@]}"; do
  if [ -f "$(dirname "$0")/$hook" ]; then
    SOURCE_HOOK="$(dirname "$0")/$hook"
    TARGET_HOOK=".git/hooks/$hook"

    if [ -f "$TARGET_HOOK" ]; then
      if cmp -s "$SOURCE_HOOK" "$TARGET_HOOK"; then
        echo "Existing $hook hook matches versioned hook; no backup or copy needed."
        continue
      else
        mkdir -p "$BACKUP_DIR"
        cp -f "$TARGET_HOOK" "$BACKUP_DIR/${hook}.${TIMESTAMP}.bak"
        echo "Backed up existing $hook to $BACKUP_DIR/${hook}.${TIMESTAMP}.bak"
      fi
    fi

    cp -f "$SOURCE_HOOK" "$TARGET_HOOK"
    chmod +x "$TARGET_HOOK"
    echo "Installed $hook hook."
  fi

done

echo "All git hooks installed."
