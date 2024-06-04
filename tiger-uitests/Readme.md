For Ui tests you can set the system property tiger.test.headless to false to see the browser in your local test
environment

To run the dummy test mvn call first do a
mvn test-compile -P start-tiger-dummy

then do
startWorkflowUi.sh

in a second git bash window do in parallel
mvn -P run-playwright-test failsafe:integration-test failsafe:verify

This is needed as the shell script will NOT compile the test code so whenever you change something on
the test code do the verify call before hand and abort before execution
