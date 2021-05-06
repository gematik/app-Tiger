# Configure IDE

* Set main class in Cucumber run plugin template config net.serenitybdd.cucumber.cli.Main
* Make sure the actor glue code is not present:  net.serenitybdd.cucumber.actors
* add all required paths tp step/glue code packages
    * for Tiger steps: de.gematik.test.tiger.glue
    * for IDP: de.gematik.idp.test.steps
* optionally if classpath is too long switch to javaargs

# Configure maven

```
<!-- to be able to access the stepdefinitions -->
<dependency>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-test-lib</artifactId>
    <version>0.1-SNAPSHOT</version>
    <type>test-jar</type>
</dependency>
<!-- to resolve all references to classes of the lib -->
<dependency>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-test-lib</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>
```

# Docker config

make sure to have login configure to connect to gstopdr1.top.local

```
{"auths":{"gstopdr1.top.local":{}},"credStore":"desktop","credsStore":"desktop","stackOrchestrator":"swarm"}
```

## docker container creation fails
l
use

```
docekr system prune
```

# Dos and Donts

* don't use custom parameter types in steps that ought to become public / exported for reuse as intellij currently does
  not support reading those from external test jars.
* don't put your Hooks into the glue code but refactor them into a separate class / package so that one can deactivate
  your hooks but still is able to use your steps.
* always assume your testsuite is run in parallel sessions concurrently. Base your test context data management on the
  current thread / thread id

# TODOs

TestEnv reader from property file cfg4j based

# TODO Next release

# NOGOs

JANSI lib for colored output https://github.com/fusesource/jansijansi
(but serenity does not deal with this lib gracefully, changing all output to error level :)

