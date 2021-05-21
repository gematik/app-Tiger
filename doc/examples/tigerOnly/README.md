# Configure IDE

* Set main class in Cucumber run plugin template config net.serenitybdd.cucumber.cli.Main
* Make sure the actor glue code is not present:  net.serenitybdd.cucumber.actors
* add env variable TIGER_ACTIVE=1
* add all required paths tp step/glue code packages
    * for Tiger steps: de.gematik.test.tiger.glue
    * for IDP: de.gematik.idp.test.steps
* optionally if classpath is too long switch to javaargs in the build dialog
