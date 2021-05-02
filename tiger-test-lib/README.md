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

## docker container creation fails

use

```
docekr system prune
```

# TODOs

TestEnv reader from property file

Banner component with ANSI support

# TODO Next release


# NOGOs

JANSI lib for colored output https://github.com/fusesource/jansijansi
(but serenity does not deal with this lib gracefully, changing all output to error level :)