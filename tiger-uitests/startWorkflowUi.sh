#!/bin/bash
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


