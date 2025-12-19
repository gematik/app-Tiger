#!/bin/bash
# Copyright 2021-2025 gematik GmbH
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
#

# Usage: ./run-tiger-tests.sh [stage]
# Stages: base, replay, sequencediagram, testselector
trap 'echo "Interrupted. Killing background jobs..."; jobs -p | xargs -r kill; exit 130' INT

STAGE="$1"

run_base() {
  echo "Running: Base tests"
  mvn -ntp test-compile -P start-tiger-dummy
  export TGR_TESTENV_CFG_CHECK_MODE="myEnv"
  export TGR_TESTENV_CFG_DELETE_MODE="deleteEnv"
  export TGR_TESTENV_CFG_EDIT_MODE="editEnv"
  export TGR_TESTENV_CFG_MULTILINE_CHECK_MODE="Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. ..."
  rm -f mvn-playwright-log.txt
  mvn --no-transfer-progress -DtgrTestPropCfgCheckMode=myProp -DtgrTestPropCfgEditMode=editProp -DtgrTestPropCfgDeleteMode=deleteProp -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt &
  mvn --no-transfer-progress -P run-playwright-test failsafe:integration-test failsafe:verify &
  wait
}


run_replay() {
  echo "Running: Replay tests"
  mvn -ntp test-compile -P start-tiger-dummy-for-unstarted-tests
  rm -f mvn-playwright-log.txt
  mvn --no-transfer-progress -P start-tiger-dummy-for-unstarted-tests failsafe:integration-test | tee mvn-playwright-log.txt &
  mvn --no-transfer-progress -P run-playwright-test-for-unstarted-tests failsafe:integration-test failsafe:verify &
  wait
}

run_sequencediagram() {
  echo "Running: Sequencediagram tests"
  mvn -ntp test-compile -P start-tiger-dummy-for-sequencediagram-tests
  rm -f mvn-playwright-log.txt
  mvn --no-transfer-progress -P start-tiger-dummy-for-sequencediagram-tests failsafe:integration-test | tee mvn-playwright-log.txt &
  mvn --no-transfer-progress -P run-playwright-test-for-sequencediagram-tests failsafe:integration-test failsafe:verify &
  wait
}

run_testselector() {
  echo "Running: Testselector tests"
  mvn -ntp test-compile -P start-tiger-dummy-for-testselector-tests
  rm -f mvn-playwright-log.txt
  mvn --no-transfer-progress -P start-tiger-dummy-for-testselector-tests failsafe:integration-test | tee mvn-playwright-log.txt &
  mvn --no-transfer-progress -P run-playwright-test-for-testselector-tests failsafe:integration-test failsafe:verify &
  wait
}

if [[ -z "$STAGE" ]]; then
  run_base
  run_replay
  run_sequencediagram
  run_testselector
else
  case "$STAGE" in
    base) run_base ;;
    replay) run_replay ;;
    sequencediagram) run_sequencediagram ;;
    testselector) run_testselector ;;
    *) echo "Unknown stage: $STAGE"; exit 1 ;;
  esac
fi