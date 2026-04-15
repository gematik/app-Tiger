#!/bin/bash
#
# Copyright 2026 gematik GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# *******
#
# For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
#
# screenshots-generate-fallback.sh — On-demand screenshot generation fallback.
#
# Generates screenshots by running the same 4 Playwright suites as the
# tiger-uitests-playwright-tests Jenkinsfile, filtered to @Tag("screenshot")
# tests only. Reuses the existing Maven profiles — no duplication.
#
# Prerequisites:
#   - The project must be built (mvn install -DskipTests) before running this.
#   - Playwright browsers must be installed.
#
# Exit codes:
#   0 — Screenshots generated successfully.
#   1 — Error.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

log() { echo "[screenshots-generate-fallback] $*"; }

# Runs one suite using the same profile pair as the Jenkinsfile:
#   $1 — suite name (for logging)
#   $2 — Maven profile for the Tiger dummy (start-tiger-dummy*)
#   $3 — Maven profile for the Playwright tests (run-playwright-test*)
#   $4 — grep pattern to find the dummy process for killing
run_suite() {
    local suite_name="$1" dummy_profile="$2" test_profile="$3" kill_pattern="$4"

    log "── Suite: $suite_name ──"

    mvn -ntp test-compile -P "$dummy_profile" -Dlicense.skip=true

    rm -f mvn-playwright-log.txt
    mvn --no-transfer-progress -P "$dummy_profile" failsafe:integration-test \
        -Dlicense.skip=true 2>&1 | tee mvn-playwright-log.txt &
    local dummy_pid=$!

    local elapsed=0
    while ! grep -q "Workflow UI http://localhost:" mvn-playwright-log.txt 2>/dev/null; do
        sleep 2
        elapsed=$((elapsed + 2))
        if [ "$elapsed" -ge 120 ]; then
            log "  ERROR: Workflow UI did not start within 120s."
            kill "$dummy_pid" 2>/dev/null || true
            return 1
        fi
    done

    # Run the SAME test profile as the Jenkinsfile, just filtered to screenshot tag.
    # Note: Driver*IT classes run on the dummy side (background), not here.
    mvn --no-transfer-progress -P "$test_profile" failsafe:integration-test failsafe:verify \
        -Dlicense.skip=true -Dgroups=screenshot || true

    # Kill dummy (same pattern as the Jenkinsfile post-always blocks)
    local env_pid
    env_pid=$(ps -ef | grep java | grep "$kill_pattern" | awk '{ print $2 }') || true
    if [ -n "$env_pid" ]; then
        kill "$env_pid" 2>/dev/null || true
    fi
    wait "$dummy_pid" 2>/dev/null || true
    rm -f mvn-playwright-log.txt

    log "  Suite $suite_name done."
}

log "Starting fallback screenshot generation..."
cd "$PROJECT_ROOT/tiger-uitests"

# Same env vars as the Jenkinsfile's 'Integration Test' stage
export TGR_TESTENV_CFG_CHECK_MODE='myEnv'
export TGR_TESTENV_CFG_DELETE_MODE='deleteEnv'
export TGR_TESTENV_CFG_EDIT_MODE='editEnv'
export TGR_TESTENV_CFG_MULTILINE_CHECK_MODE='Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. ...'

# Same 4 suite pairs as tiger-uitests-playwright-tests.Jenkinsfile
run_suite "main" \
    "start-tiger-dummy" \
    "run-playwright-test" \
    "start-tiger-dummy"

run_suite "replay" \
    "start-tiger-dummy-for-unstarted-tests" \
    "run-playwright-test-for-unstarted-tests" \
    "start-tiger-dummy-for-unstarted-tests"

run_suite "sequencediagram" \
    "start-tiger-dummy-for-sequencediagram-tests" \
    "run-playwright-test-for-sequencediagram-tests" \
    "start-tiger-dummy-for-sequencediagram-tests"

run_suite "testselector" \
    "start-tiger-dummy-for-testselector-tests" \
    "run-playwright-test-for-testselector-tests" \
    "start-tiger-dummy-for-testselector-tests"

log "All suites complete. Screenshots generated in doc/user_manual/screenshots/."
