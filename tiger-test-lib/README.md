# Configure IDE

* Set main class in Cucumber run plugin template config net.serenitybdd.cucumber.cli.Main
* Make sure the actor glue code is not present:  net.serenitybdd.cucumber.actors
* add all required paths tp step/glue code packages
    * for Tiger steps: de.gematik.test.tiger.glue
    * for IDP: de.gematik.idp.test.steps
* optionally if classpath is too long switch to javaargs in the build dialog

# Configure maven

```
<!-- to be able to access the stepdefinitions -->
<dependency>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-test-lib</artifactId>
    <!-- replace with current version! -->
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

You might have to add additional dependencies!

[For a standalone example see here in our examples section](../doc/examples/tigerOnly)

# Docker config

make sure to have login configure to connect to gstopdr1.top.local

```
{"auths":{"gstopdr1.top.local":{}},"credStore":"desktop","credsStore":"desktop","stackOrchestrator":"swarm"}
```

## docker container creation fails

use the command below to remove all unused containers. Or look for containers starting with "tiger", stop and remove them.

```
docekr system prune
```

Last resort:

```
netcfg -d
```
and restart docker

# Dos and Donts

* don't use custom parameter types in steps that ought to become public / exported for reuse as intellij currently does
  not support reading those from external test jars.
* don't put your Hooks into the glue code but refactor them into a separate class / package so that one can deactivate
  your hooks but still is able to use your steps.
* always assume your testsuite is run in parallel sessions concurrently. Base your test context data management on the
  current thread / thread id
