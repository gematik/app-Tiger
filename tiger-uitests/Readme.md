Run local Playwright UI tests with `runUiTestsLocally.sh`. It compiles the test code, starts the
corresponding Tiger dummy environment, and runs the Playwright tests.

```bash
./runUiTestsLocally.sh
```

Run an individual suite by passing one of these stages:

```bash
./runUiTestsLocally.sh base
./runUiTestsLocally.sh report
./runUiTestsLocally.sh replay
./runUiTestsLocally.sh sequencediagram
./runUiTestsLocally.sh testselector
```

To run a suite with a visible browser, start the dummy environment in one Git Bash window:

```bash
mvn -ntp test-compile -P start-tiger-dummy
export TGR_TESTENV_CFG_CHECK_MODE="myEnv"
export TGR_TESTENV_CFG_DELETE_MODE="deleteEnv"
export TGR_TESTENV_CFG_EDIT_MODE="editEnv"
mvn --no-transfer-progress \
  -DtgrTestPropCfgCheckMode=myProp \
  -DtgrTestPropCfgEditMode=editProp \
  -DtgrTestPropCfgDeleteMode=deleteProp \
  -P start-tiger-dummy failsafe:integration-test | tee mvn-playwright-log.txt
```

Wait until the dummy environment logs the Workflow UI URL, then run the Playwright tests in a
second window:

```bash
DEBUG=pw:api mvn -P run-playwright-test -Dtiger.test.headless=false \
  failsafe:integration-test failsafe:verify
```

Omit `DEBUG=pw:api` when verbose Playwright API logging is not needed.

To rerun trace archives

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace target/playwright-artifacts/XXXXXXXXXXXXXXXX.zip"
```

In order to run the discovered tests (tests that are only displayed but have not run yet) use the
following commands:

mvn call first to compile test code

```
mvn test-compile -P start-tiger-dummy-for-unstarted-tests
```

Wait and then run the dummy for unstarted tests

```
mvn --no-transfer-progress  -P start-tiger-dummy-for-unstarted-tests failsafe:integration-test | tee mvn-playwright-log.txt
```

then with no delay do in a second window to run the playwright tests

```
mvn -P run-playwright-test-for-unstarted-tests failsafe:integration-test failsafe:verify
```
