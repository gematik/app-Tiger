#!/bin/bash
#
# Copyright 2024 gematik GmbH
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

export TGR_TESTENV_CFG_CHECK_MODE=myEnv
export TGR_TESTENV_CFG_DELETE_MODE=deleteEnv
export TGR_TESTENV_CFG_EDIT_MODE=editEnv
export TGR_TESTENV_CFG_MULTILINE_CHECK_MODE="Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. ..."
rm -f mvn-playwright-log.txt
mvn --no-transfer-progress \
    -DtgrTestPropCfgCheckMode=myProp \
    -DtgrTestPropCfgEditMode=editProp \
    -DtgrTestPropCfgDeleteMode=deleteProp \
    -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt


