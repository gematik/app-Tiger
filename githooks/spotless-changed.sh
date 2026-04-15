#!/bin/bash

# This script runs our custom Tiger Spotless CLI wrapper on a given list of files.
# The CLI jar is built by the tiger-spotless-cli module during the Maven build.

set -e

if [ "$#" -eq 0 ]; then
  echo "No files passed to format."
  exit 0
fi

HOOKS_DIR="$(dirname "$0")"
WORKSPACE_ROOT="$(cd "$HOOKS_DIR/.." && pwd)"
CLI_JAR="$WORKSPACE_ROOT/tiger-spotless-cli/target/tiger-spotless-cli.jar"

# Check if the CLI jar exists
if [ ! -f "$CLI_JAR" ]; then
  echo "Tiger Spotless CLI jar not found at: $CLI_JAR"
  echo "Building it now (one-time setup)..."

  if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven (mvn) not found; cannot build Spotless CLI jar." >&2
    exit 1
  fi

  # Build just the spotless-cli module
  (cd "$WORKSPACE_ROOT" && mvn -ntp -q -pl tiger-spotless-cli -am clean package -DskipTests) || {
    echo "Failed to build Tiger Spotless CLI jar." >&2
    exit 1
  }

  if [ ! -f "$CLI_JAR" ]; then
    echo "Build succeeded but jar not found at expected location: $CLI_JAR" >&2
    exit 1
  fi

  echo "Tiger Spotless CLI built successfully."
fi

# Run the CLI on the provided files. This is fast.
java -jar "$CLI_JAR" apply "$@"
