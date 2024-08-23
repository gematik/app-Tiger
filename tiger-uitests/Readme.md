For Ui tests you can set the system property tiger.test.headless to false to see the browser in your local test
environment

mvn call first to compile test code

```
mvn test-compile -P start-tiger-dummy
```

Wait and in a second git bash window do

```
mvn -P run-playwright-test failsafe:integration-test failsafe:verify
```

then with no delay do in the first window to run the dummy test

```
./startWorkflowUi.sh
```

This is needed as the shell script will NOT compile the test code so whenever you change something on
the test code do the verify call before hand and abort before execution

Add DEBUG=pw:api before the second mvn call for more details on the playwright execution

To run the tests in head mode, just run the mvn command without the -Dtiger.test.headless=false flag (true is the
default)
To enable tracing of the playwright execution, run the mvn command with -Dtiger.test.trace=true, which is the default

To rerun trace archives

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace target/playwright-artifacts/XXXXXXXXXXXXXXXX.zip"
```
