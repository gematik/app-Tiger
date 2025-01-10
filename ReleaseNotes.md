# Changelog Tiger Test platform

# Release 3.5.1

## Features

* TGR-1100: cleaned up Tiger Proxy configuration properties in fileSaveInfo
* TGR-1608: Tiger-Proxy: Infinite loop prevention for HTTP-Requests. After 10 loops the Proxy will simply close the connection, saving an error to the Tiger-Proxy log.

# Release 3.5.0
* Serenity BDD 4.2.11
* Cucumber 7.20.1
* RestAssured 5.5.0
* Selenium 4.19.1
* Appium 9.3.0
* Spring Boot 3.4.1
* Logback 1.5.15

## Bugfixes

* TGR-1693: Tiger-Proxy: In the menu the correct information about a message is again being displayed.
* TGR-1694: Fixed missing messages in UI traffic visualization when using proxy mesh setup.

## Features

* TGR-1696: When JSON Schema validation fails, the error message now contains the validation messages.

# Release 3.4.7

## Breaking Changes

* TGR-1686: Stricter HTTP-Parsing added. This means messages without valid headers will not be parsed. Error messages
  will be added. To disable this feature set the following configuration key in the tiger.yaml:

```yaml
rbel.lenientHttpParsing: true
```

## Features

* TGR-1447, TGR-1656: Tiger-Proxy: Sequence-Numbers are assigned per tiger proxy, assuring monotonous increase. A
  history of sequence numbers per proxy is transmitted to the downstream proxy. This shall allow
  differentiation for messages originating from different proxies.
* TGR-1685: added glue step to support storing nodes of requests into Tiger Global Configuration properties.
* TGR-1672: improved display of tiger user manual on small screens (tables)

## Bugfixes

* TGR-783, TGR-1327: Tiger-Proxy: Refined the handling of trailing slashes in proxy-routes. This eliminates certain
  unwanted edge-cases. (route /foo and request /foobar will no longer match)

# Release 3.4.6

* Serenity BDD 4.2.3
* Cucumber 7.19.0
* RestAssured 5.5.0
* Selenium 4.19.1
* Appium 9.3.0
* Spring Boot 3.3.4
* Logback 1.5.8

## Breaking Changes

* TGR-1655: To add Basic Authentication to a TigerRoute you now have to use the following configuration:

```yaml
tigerProxy:
  routes:
    - from: /myRoute
      to: http://example.com
      authentication:
        username: itsame
        password: superSecret
```

* Tiger Maven Plugin: If you are using a custom driver template which includes the placeholder `${tags}`, you should
  change the full annotation to `${tagsAnnotation}`.
  E.g.:
  If your custom template has:

```
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "${tags}")
```

You have to change the full line to just contain:

```
$ {tagsAnnotation}
```

If you do not use a custom template, no action is necessary.

## Features

* TGR-1651: DisplayName of Scenario Outlines in RestAPI has a more descriptive name
* TGR-1673: updated `junit-jupiter` and `junit-platform-suite` dependencies to versions 5.11.0 and 1.11.0 respectively.
* KOB-19: Workflow UI: make button to expand and minimize Tiger Proxy Log pane more visible.
* TGR-1464: Rest API: Tiger has now an API which allows the starting of specific tests and the retrieval of the test
  results via REST. The Rest Api is disabled by default. To enable it set the following configuration key in the
  tiger.yaml:

```yaml
lib:
  enableTestManagementRestApi: true 
```

* TIMTS-658: default poll interval for waiting for external servers to be healthy is increased to 1000 ms and can be
  configured via configuration key `tiger.internal.externalServer.startupPollIntervalMs` or per server via the server
  property `startupPollIntervalMs`
* TGR-1517: Improved display of connection errors in the Tiger-Proxy traffic log
* TGR-1660: Change notification for jenkins release
* KOB-35: TigerProxy now will query listed "to"-targets in parallel. The first healthy target will be used for routing.
* TGR-1618: RbelParser: Added support for LDAP messages.
* TGR-1655: TigerRoutes can now not only add a Basic-Authentication but also a Bearer-Token:

```yaml
tigerProxy:
  routes:
    - from: /myRoute
      to: http://example.com
      authentication:
        bearerToken: blubblab
    - from: /myRoute
      to: http://example.com
      authentication:
        username: itsame
        password: superSecret
```

* TGR-1645: Tiger-Proxy: You can now choose if the generic fallback identity should be used. This can be toggled via
  `tigerProxy.tls.allowGenericFallbackIdentity`. The default is `false`, meaning that the generic fallback identity is
  only used when no other means is configured (serverIdentities, serverIdentity, serverRootCa).
* TGR-1442: PKI-Identites can now also be provided via a map, specifying the individual options:

```yaml
tls.forwardMutualTlsIdentity:
  filename: myIdentity.p12
  password: "changeit"
  storeType: P12
```

* TGR-1442: PKI-Identities can now also be provided without any password, even in the yaml. The default guessing
  mechanism will be used, which can be extended via `lib.additionalKeyStorePasswords`.

## Bugfixes

* TGR-1659: Workflow UI: fixed an issue where quiting the Workflow UI while a pause step was waiting, would not
  correctly shut down the background process.
* TGR-978: User Manual: Fixed the overlap of text from the table of contents when viewing the html manual on a small
  width screen.
* KOB-6: Fixed an issue where https-destinations could not be used as one of multiple "to"-targets.
* TGR-1670: TigerProxy: Correct host-header routing, which will now work case-insensitive
* TGR-1643: Treat testcases that have not been run (in case that lib.runTestsOnStart is false)
  as SKIPPED/IGNORED instead of PASSED in Serenity test report.
* TGR-1574: fixed an issue where defining the `cucumber.filter.tags` property via the failsafe configuration would not
  override the default setting of `not @Ignore`.

# Release 3.4.5

## Features

* KOB-4: Support fallback static identities for TLS server. This means you can configure a one or many server
  identities, and in case that the SNI during the TLS-handshake does match none of the given identities a matching,
  dynamic certificate will be generated either from a given or a generic Root-CA.
* TGR-1549: Configuration Editor has a refresh button now
* TGR-1635: A route in a Tiger-Proxy can have multiple "to"-targets. On startup the Tiger-Proxy will choose the first
  healthy target of the list. The configuration looks like this:

```yaml
tigerProxy:
  proxyRoutes:
    - from: /multiple/targets
      to:
        - http://example.com
        - http://example2.com
    - from: /only/one/target
      to: http://example.com
```

The old configuration with a single target is still supported. In that case no health checks are performed.

* TGR-1634: Workflow UI: when the backend shuts down, the frontend displays a message informing the user that the
  backend is no longer active.
* TGR-1636: TigerProxy IP from WorkflowUI is found dynamically

## Bugfixes

* KOB-7: Workflow UI: fixed that testcase discovery stops after first feature with matching tags
* KOB-13: Workflow UI: fixed an issue where replaying a test would incorrectly shutdown the tiger suite.

# Release 3.4.4

## Features

* TGR-538: NoProxyHosts can now be configured for the forwardToProxy-configuration of a TigerProxy:

```yaml
tigerProxy:
  forwardToProxy:
    hostname: 192.168.110.10
    port: 3128
    noProxyHosts:
      - "localhost"
```

In this example the TigerProxy will not use the forwardToProxy-configuration for requests to localhost (instead sending
the messages directly).

## Bugfixes

* TGR-1616: ForwardToProxy now correctly works when tunneling HTTPS traffic through an HTTP-Proxy
* TGR-1616: Reverse-Proxy-Routes now correctly delete the matching part from the path before forwarding the request. (
  `/foo/bar` which matches the route `/foo` will now be forwarded to `/bar` only)
* TGR-1061: Behavior of different RbelElements in WebUi fixed
* KOB-8: The ordering of Proxy-Routes now also takes into consideration the amount of hosts in the route. This means
  that a route with more hosts is now preferred over a route with fewer hosts (since it is considered to be more
  specific).
* TGR-1632: Fixed an issue which prevented Cucumber-error-messages to be thrown. (the
  `Failed to transform configuration parameter with key 'cucumber.snippet-type' and initial value 'UNDERSCORE'` message)

# Release 3.4.3

## Features

* TGR-1475: Allow the configuration of multiple TLS-server-certificates. The Tiger-Proxy will automatically select the
  most appropriate one:

```yaml
tigerProxy:
  tls:
    serverIdentities:
      - "someIdentity.p12;00"
      - "anotherIdentity.p12;changeit"
```

* TGR-1590: Host-Header matching is now Port-aware: If no ports are given, ports are ignored, if ports are given, they
  are matched.
* TGR-1598: Improved logging of routing decisions in Tiger-Proxy
* TGR-1561: Improved performance of fast-fail checks of SMTP/POP3 converters
* TGR-1612: binary content data is now stored in a chunked manner
  (via a new utility class ChunkedByteArray, available at RbelElement.getContent()),
  so that for accumulating large TCP messages that come in as many chunks,
  fast-fail checks for different converters can be done with less copying overhead.

## Bugfixes

* TGR-1460: Allow different ordering of parameters smime-type and name in content-type header of encrypted mail
* TGR-1496: RbelLogger: Empty standalone JSON- and CBOR-Elements are no longer converted
* TGR-1550: Rbel-Log messages are now displayed next to the sidebar correctly even when screen is small
* TGR-1606: Fixed openSidebar for the UI-Tests
* TIMTS-656: Fixed race condition on TLS connections with dynamic server identities

# Release 3.4.2

## Breaking Changes

* TGR-1547: Tiger-Test-Lib: the no-args constructor of the class RbelMessageValidator is no longer public. Use
  `de.gematik.test.tiger.lib.rbel.RbelMessageValidator.getInstance` if you need to get a reference to the default
  instance. You want to use the same instance across the code base, so that all validation actions operate in the same
  messages list. If you require a separate instance (e.g.: for Unit Tests), then use the constructor
  `RbelMessageValidator(TigerTestEnvMgr, TigerProxy, LocalProxyRbelMessageListener)`. You can initialize it with mock or
  real
  dependencies depending on your testing needs.
* RbelLogger: the `RbelX509Converter` is no longer active per default. If you need this converter in your project you
  need to activate it explicitly. This can be done in the tiger yaml:

```yaml
tigerProxy:
  activateRbelParsingFor:
    - X509
```

## Features

* TGR-905: WorkflowUI: Rbel-Path Tab in Inspect Modal has now color coding for true/false/invalid rbel path
* TGR-1551: Tiger-Proxy: Added configuration-option to add notes to messages in the RbelLog. This can be as follows:
    ```yaml
    tigerProxy:
      notes:
        - message: "This is a note on the HTTP method"
          jexlCriterion: "isRequest && path == '$.method'"
        - message: "Hackers were here..."
          jexlCriterion: "element.decryptedUsingKeyWithId == 'mySuperSecretKey'"
    ```
* TGR-1557: Rbel-Parser: Added support for comma-seperated values in HTTP-Headers. This is useful for headers like
  `Accept-Language` or `Accept-Encoding`. To look for a specific value in a comma-seperated list, you can use the
  following syntax: `$.header.Accept-Encoding.. == 'Value1'`.
* TGR-1561: performance improvements on Rbel parsing to avoid potentially expensive operations via fast-fail heuristics
    * Better handling of POP3 NOOP responses without header line
    * Tracing display of long string content during Rbel parsing can be configured via property
      tiger.rbel.rawstring.max.trace.length (default set to 1000)
* TGR-1567: Added Glue-Code step for selecting a request with a node matching a value regardless of the path:
  `And TGR find last request with "$.path.foobar.value" matching "22"`
* TGR-1573: resolve scenario outline parameters and expressions
    * in scenario title, outline table, step descriptions
    * found config/RBel/JEXL expressions are resolved, if possible
    * original (unreplaced) step description is available as title/tooltip
    * long step description lines in Web UI are abbreviated
        * configurable via tiger.lib.maxStepDescriptionDisplayLengthOnWebUi (default 300)

## Bugfixes

* TGR-1440: Dockerfile of tiger proxy does not set the MANAGEMENT_SERVER_PORT variable
* TGR-1497: Tiger Proxy Log - filter matching all says that none match
* TGR-1516: Corrected route-ordering, so that the most specific route is selected first. Forward-Proxy routes are now
  preferred over Reverse-Proxy routes. They are also always checked for matching hosts, either in the host-header or the
  target-url.
* TGR-1545: Tiger Proxy: a remote tiger proxy with rbel parsing inactive, now correctly still propagates the unparsed
  messages to the local tiger proxy. The local tiger proxy can then still parse them.
* TGR-1566: Workflow UI: fixed an issue where the combination of not running starts automatically on start up and
  pressing play on a test that uses the tiger glue to pause the execution would wrongly terminate the tiger test
* TGR-1593: Rollback of TGR-1440, as it introduced a breaking change for tiger proxies configured in mesh setup
    * ENV variable SERVER_MANAGEMENT_PORT is set to 8081 in the docker file and healthcheck uses that internal port

# Release 3.3.0

* TGR-1469: improve responses of UITests for true/false queries

## Breaking Changes

* TGR-1527: rbel validation steps now also support rbelpaths that return more than one node, by evaluating each. If the
  value should match, the step succeeds if there is at least ONE node matching. If it should not match then the step
  succeeds if NO node matches.
* TGR-1539: Multiple instances of RbelMessageValidator no longer share currentRequest/currentResponse as static
  fields.
  However, the class RbelMessageValidator has a new static getInstance() method which provides a global instance of this
  class which is used by default constructor instantiations of RbelValidatorGlue.

* With the implementation of TGR-1410, Tiger no longer supports JUnit 4 driver classes. This is a
  necessary change to take advantage of the JUnit Platform API that allows us finer control over the test execution.
  Running Tests via IntelliJ using the io.cucumber.junit.TigerCucumberRunner will work as previously.
  When running tests via maven or via the IDE over a custom driver class, only JUnit 5 driver classes will work. These
  are now automatically created and the option `<junit5Driver>true</junit5Driver>` is no longer needed in the
  tiger-maven-plugin configuration.
  When setting up custom driver classes, you need to use the junit platform annotations. For example:

```java
package de.gematik.test.tiger.integration.YOURPROJECT;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/YOURFEATURE.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "de.gematik.test.tiger.glue,ANY ADDITIONAL PACKAGES containing GLUE or HOOKS code")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "io.cucumber.core.plugin.TigerSerenityReporterPlugin,json:target/cucumber-parallel/1.json")
public class Driver1IT {

}
```

The following dependencies are included transitively with the tiger-test-lib package:

```xml

<dependencies>
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-junit-platform-engine</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-suite</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-suite-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-launcher</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.vintage</groupId>
        <artifactId>junit-vintage-engine</artifactId>
    </dependency>
</dependencies>
```

Even though we don't support JUnit 4 drivers, we still include the junit-vintage-engine dependency, because it is
required
by cucumbers internal `PickleRunner`. This means the JUnit 4 dependency will still show up in your classpath.

## Features

* TGR-1410: Workflow UI: it is now possible to configure the Workflow UI to not run the test scenario immediately on
  start up. The tests will be discovered and displayed on the Workflow UI and a play button allows to select specific
  tests to execute. To activate this functionality you need to configure the following property in the `tiger.yaml`:

```yaml
lib:
  runTestsOnStart: false # default is true
```

# Release 3.2.2

* Serenity BDD 4.1.14
* Cucumber 7.18.1
* RestAssured 5.4.0
* Selenium 4.18.1
* Appium 9.0.0
* Spring Boot 3.3.2
* Logback 1.5.6

## Breaking Changes

* TGR-1471: Activation and deactivation of various RbelConverters is now different. By default, all RbelConverters in
  the
  classpath are added, except those that define key-phrases for activation (This is done by
  adding `@ConverterInfo(onlyActivateFor = "pop3")`
  to the class). Currently, these optional converters are:

    * `pop3` for RbelPop3CommandConverter and RbelPop3ResponseConverter
    * `smtp` for RbelSmtpCommandConverter and RbelSmtpResponseConverter
    * `mime` for RbelMimeConverter and RbelEncryptedMailConverter
    * `asn1` for RbelAsn1Converter
    * `epa-vau` for RbelVauEpaConverter and RbelVauEpaKeyDeriver
    * `erp-vau` for RbelErpVauDecryptionConverter
    * `epa3-vau` for RbelVauEpa3Converter
    * `sicct` for RbelSicctCommandConverter and RbelSicctEnvelopeConverter

These options supersede the `activateEpaVauAnalysis` etc. configuration keys. All these conversions are deactivated by
default!

* TGR-1215: You can clear the traffic observed by the tiger proxy during test setup phase by using the configuration
  key `tiger.lib.clearEnvironmentStartupTraffic`. This is active by default.
* TGR-1502: add POP3/MIME specific prefix to facet fields
    * for POP3, the fields status, header, body are now named pop3Status, pop3Header, pop3Body
    * for MIME, the fields header, body are now named mimeHeader, mimeBody
* TGR-1508: Additional YAML files are now configured under `additionalConfigurationFiles` instead of `additionalYamls`.
  This is necessary for consistency, since now you can also read other types (namely `ENV` files, done via `type: ENV`).
  Default for type is `YAML`, so you can omit it for YAML files.
* TGR-1458: Add functionality to RbelMessageValidator to allow filtering for message pairs by response fields.
    * This change involves changing the former filtering of the existing steps for just HTTP requests to filter
      for ALL requests instead. This might break former testcase behavior, as potentially other requests that are
      not HTTP requests could be found that match the former criteria (though it is unlikely).
    * New steps to RbelValidatorGlue have been added
    * When a response is found by these steps, the corresponding request is set as the current request,
      if no corresponding request is found, the response is set as the current request, as well
    * For POP3/SMTP responses, they are paired with their requests.
    * Only if the found request has a facet implementing the new marker interface RbelRequestWithResponse
      (for now true for HTTP, POP3 and SMTP request), the corresponding response is searched for,
      if a request is queried and found, otherwise, the current response is cleared.
    * If a request is queried and not found, both the current request and the current response are cleared.
      This is another breaking change, as previously, the status of the current request/response pair was unchanged
      by a failed query.
    * If the previous search found a response, then the "find next" steps, when not searching explicitly for a request
      will start their search after that response, otherwise, they will start their search after the previously found
      request

```gherkin
Gegebensei TGR finde die erste Nachricht mit Knoten {tigerResolvedString} der mit {tigerResolvedString} übereinstimmt
Given TGR find message with {tigerResolvedString} matching {tigerResolvedString}
Gegebensei TGR finde die nächste Nachricht mit Knoten {tigerResolvedString} der mit {tigerResolvedString} übereinstimmt
Given TGR find next message with {tigerResolvedString} matching {tigerResolvedString}
Gegebensei TGR finde die letzte Nachricht mit Knoten {tigerResolvedString} der mit {tigerResolvedString} übereinstimmt
Given TGR find last message with {tigerResolvedString} matching {tigerResolvedString}
Gegebensei TGR finde die erste Nachricht mit Knoten {tigerResolvedString}
Given TGR find message with {tigerResolvedString}
Gegebensei TGR finde die nächste Nachricht mit Knoten {tigerResolvedString}
Given TGR find next message with {tigerResolvedString}
Gegebensei TGR finde die letzte Nachricht mit Knoten {tigerResolvedString}
Given TGR find last message with {tigerResolvedString}
```

# Features

* TGR-1385: in the WebUi sidebar the inner (crypted) requests and response codes will be displayed when available
* TGR-1449: Enable Host-Header Routing for reverse-proxies
* TGR-1423: Failure on setup of the test environment is shown on the Workflow UI with a large bottom banner
* TGR-1508: Configuration: Added support for additional non-yaml files. This is useful when trying to import values from
  a properties or .env file:

```yaml
additionalConfigurationFiles:
  - filename: myProperties.env
    type: ENV
```

* TGR-1237: Configuration Editor: it is now possible to export and import the tiger global configuration as a yaml file
  from the Workflow UI. Two new glue code steps also allow to trigger the import and export from a test scenario:

```gherkin
TGR save TigerGlobalConfiguration to file {tigerResolvedString}
TGR speichere TigerGlobalConfiguration in Datei {tigerResolvedString}
TGR load TigerGlobalConfiguration from file {tigerResolvedString}
TGR lade TigerGlobalConfiguration aus Datei {tigerResolvedString}
```

* TGR-1486: For CBOR decoded binary content, add binary facet and HTML note to distinguish from CBOR strings.
* TGR-1474: Rbel: Http-version is now parsed and stored in the Rbel tree
* TGR-1317: Tiger-Proxy: The masterSecrets can now be stored to file. Please set the configuration key
  `tiger.tigerProxy.tls.masterSecretsFile` to the desired location. The written files are compatible with Wireshark. To
  enable this feature the
  tiger-java-agent has to be attached to the JVM. For a standard
  maven-run this can be done by <goal>attach-tiger-agent</goal> to the
  tiger-maven-plugin. For a more detailed explanation please refer to the user manual, section "Tiger-Proxy > TLS > TLS
  Decryption in wireshark".
* TGR-1507: Tiger-Proxy: Added option to ignore offline traffic endpoints:

```yaml
tigerProxy:
  failOnOfflineTrafficEndpoints: false
```

* TGR-1371: Tiger-Test-Lib: it is now possible to set a custom failure message that will be displayed when an assertion
  fails. This is achieved with the following steps:

```gherkin
Given TGR the custom failure message is set to {tigerResolvedString}
Gegebensei TGR die benutzerdefinierte Fehlermeldung ist auf {tigerResolvedString} gesetzt
[...]
Then TGR clear the custom failure message
Dann TGR lösche die benutzerdefinierte Fehlermeldung 
```

* TGR-1198: Tiger-Test-Lib: a new inline method `rbel:encodeAs` can be used to explicitly encode a value as one of the
  RbelContentTypes: XML, JSON, JWT, JWE, URL, BEARER_TOKEN.
  Due to limitations on how the escaping of multiline strings in the bdd steps work, the value to encode must come from
  a configuration value or read directly from a file. Directly writing the string to be encoded in the BDD step does not
  work.

```gherkin
Feature: Example usage

  Scenario: save first to a variable
    Given TGR set global variable "exampleJWT" to "!{file('exampleJWT.json')}"
    Then TGR set global variable "encodedJWTFromGetValue" to "!{rbel:encodeAs(getValue('exampleJWT'), 'JWT')}"

  Scenario:
    Then TGR set global variable "encodedJWTFromFile" to "!{rbel:encodeAs(file('exampleJWT.json'), 'JWT')}"
```

* TGR-1451: replace deprecated Jenkins commands
* TGR-1498:
    * Enhanced MIME body rendering for better presentation of lengthy content, including truncation and contextual
      titles.
    * Enhanced MIME body rendering for with rendering of contained elements.
    * Improved conversion logic for MIME data processing, ensuring proper handling of body elements.

# Bugfixes

* TGR-1455: implement conversion of POP3 AUTH command
* TGR-1454: Tiger-Proxy: fix issue where small messages were not being forwarded.
* TGR-1436: Fix HTTP message pairing for old tgr-files
* TGR-1456: Only interpret POP3 RETR response as MIME
* TGR-1500: Tiger-Proxy: Fixed infinite-loop when no matching route found
* TCLE-14: User Manual: improvements in Docker server type documentation.
* TGR-1480: Fix handling of SMTP AUTH command
* TGR-1490: Fix handling of POP3 LIST/UIDL responses
* TGR-1491: Only convert body of SMTP data command as MIME
* TGR-1492: Avoid missing converters inside RbelConverter in test code
* TGR-1493: Distinguish between different SMTP AUTH command handling
* TGR-1499: Treat POP3 TOP response similar to POP3 RETR response

# Release 3.1.3

## Breaking Changes

* TGR-1394: Tiger-Test-Lib: the glue code
  step `TGR filter requests based on host {tigerResolvedString}` /
  `TGR filtere Anfragen nach Server {tigerResolvedString}`
  no longer filters the messages based on the Host http
  header.
  Instead, it refers to the hostname or the server name as defined in the tiger.yaml. E.g.:

```yaml
servers:
  anotherServer:
    hostname: example.com # here the hostname is explicitly defined as 'example.com'
    type: externalUrl
  aServer: # here there is no explicit hostname, so the server name is defined as 'aServer'
    type: externalUrl
```

## Bugfixes

* TGR-1444: Bugfix for relaying multiline TCP messages to downstream tiger proxy
* TGR-1446: External-Jar: Solved race condition that could lead to a NPE when starting the server.
* TGR-1472: add misssing POP3/SMTP commands SASL,AUTH

## Features

* TGR-1411: Tiger-Proxy: Content- and Transfer-Encodings are no longer deleted on retransmission.
* TGR-1375: Rbel Parsing of SMTP messages
* TGR-1448: Tiger-Proxy: Added an option to deactivate TLS-Termination for a directReverseProxy.
* TGR-1395: Implement mail decryption
* TGR-1443: REST requests are now fully configurable via TigerHttpClient

# Release 3.1.2

## Bugfixes

* TGR-1438: Tiger-Test-Lib: make currentResponse and currentRequest in RbelMessageValidator static so that they are
  shared by multiple instances of RbelMessageValidator.
* TGR-1439: Tiger-Test-Lib: made usage of LocalProxyRbelMessageListener null safe also when the local tiger proxy is not
  active.

# Release 3.1.0

* Serenity BDD 4.1.14
* Cucumber 7.18.0
* RestAssured 5.4.0
* Selenium 4.18.1
* Appium 9.0.0
* Spring Boot 3.3.0
* Logback 1.5.6

## Breaking Changes

* Because of the new Spring Boot version the following adjustments have to be done
  add the following in your pom.xml
  ```
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>ch.qos.logback</groupId>
                <artifactId>logback-classic</artifactId>
                <version>1.5.6</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
  ```

* TGR-1425: Tiger-Test-Lib: the class LocalProxyRbelMessageListener is refactored to be easier to handle in tests. If
  you are using it directly in your test suite, you need to get an instance
  with `LocalProxyRbelMessageListener.getInstance()`. The previously existing static methods are now instance methods.
  To use in unit tests you now have two options:

1. Load the full TigerTestEnvironent in your test and get the instance
   of
   the LocalProxyRbelMessageListener.

2. If you don't need a full TigerTestEnvironment you can set a test instance of the
   LocalProxyRbelMessageListener with a custom messages supplier
   with `LocalProxyRbelMessageListener.setTestInstance(new LocalProxyRbelMessageListener(new MyMessagesSupplier()))`.

## Features

* TGR-1195: UX improvements for Rbel Inspect
* TGR-1196: Workflow UI improvements (sticky sidebar, quit test run message)
* TGR-1429: Tiger-Test-Lib: it is now possible to assert if a given Json matches a specified JSON Schema. This allows
  for easier validation of JSON structures.

Example usage:

```
TGR current response at "$.body" matches as JSON_SCHEMA:
"""
{ "type": "object",
    "properties": {
      "name": {
        "type": "string"
      },
      "age": {
        "type": "integer",
        "minimum": 0
      },
      "email": {
        "type": "string",
        "format": "email"
      }
    },
    "required": ["name", "age"]
  }
"""
```

For more details on how to specify a json schema refer to the external
resource https://jsoneditoronline.org/indepth/validate/json-schema-validator/ .
In https://json-schema.org/implementations#validators-web-(online) you can find a list of online validators which you
can use to prepare the schemas. In Tiger we use the **2020-12** version of the JSON Schema specification.

* TGR-1404: Tiger-Proxy: in direct reverse proxy mode the proxy opens immediately a connection to the remote target as
  soon as an incoming connection is established.

## Bugfixes

* TGR-1340: Maven: make build under Windows emit less warnings.
* TGR-1435: TigerProxy: Added support for nested Content- and Transfer-Encodings.
* TGR-1426: TigerProxy: Enable BrainPool curves again (stabilized the order of security providers)

# Release 3.0.5

## Features

* TGR-1400: RbelParser: Added support for XML-Processing Instructions.
* TGR-1406: Added the already parsed information in an Epa 3 message to the Rbel-tree.
* TGR-1408: Zion: Empty headers are no longer transmitted.
* TGR-1407: Zion: Added the possibility to delay responses for a fixed amount of time.
* TGR-1193: UX improvement in WebUi/TigerProxyLog
* TGR-1376: RbelParser: Conversion of POP3 Messages to Rbel trees

## Bugfixes

* TGR-1415: TigerProxy: Fixed a bug when multiple Tiger-Proxies would interfere with each other when selecting
  client-groups. Client-Groups can now be correctly selected, but not multiple settings in a single JVM. If that is
  desired you will have to boot the Tiger-Proxies through an external-JAR server.
* ANFEPA-2559: Rbel-Logger: Fixed the handling of the request-counter for EPA3-messages.
* TGR-1405: TigerProxy: Proxies in directReverseProxy-mode now report correct health status.
* TGR-1405: TigerProxy: More resilient re-connnect logic in mesh-setups.
* TGR-1405: TigerProxy: Fixed an issue where in a mesh-setup the order of the messages would sometimes be mixed up.

# Release 3.0.4

* Serenity BDD 4.1.10
* Cucumber 7.17.0
* RestAssured 5.4.0
* Selenium 4.18.1
* Appium 9.0.0
* Spring Boot 3.2.2
* Logback 1.4.9

## Bugfixes

* TGR-1398: Fixing reset of filter method in rbel validator glue code which caused an NPE if used in feature files (
  regression).
* TGR-1389: Fixed an issue where the RbelLogger would decrypt a VAU-EPA3-message multiple times.
* TGR-1387: Fixed an issue where in a meshed setup messages where not correctly paired.

# Release 3.0.3

## Breaking changes

* TGR-1362: HttpGlueCode now waits by default for the request to be received before continuing. This can be changed by
  setting the configuration
  key `tiger.httpClient.executeBlocking` to `false`.

## Features

* TGR-1372: Zion: The responseCode is now a string, meaning it now supports the use of TigerConfiguration values.
* TGR-1384: Tiger-Test-Lib: CBOR messages can now also be asserted using JSON
  structures (`And TGR current response at "$.body" matches as JSON:`)
* TGR-1377: Tiger-Test-Lib:
    * Added Request Validation Steps:
        * @Then("TGR current request body matches:")
        * @Then("TGR current request with attribute {tigerResolvedString} matches {tigerResolvedString}")
        * @Then("TGR current request contains node {tigerResolvedString}")
        * @Then("TGR current request at {tigerResolvedString} matches:")
        * @Then("TGR current request at {tigerResolvedString} matches as {modeType}:")
        * @Then("TGR current request with attribute {tigerResolvedString} does not match {tigerResolvedString}")
    * Updated user manual, see section "Tiger Test Lib > Validating requests"

## Bugfixes

* TGR-1373: Fixed an issue where in a meshed setup the sender and receiver addresses were switched in the receiving
  tiger proxy.
* TGR-1381: Tiger-Proxy: Use the correct length for IV and AD in AES-GCM decryption.

# Release 3.0.2

## Breaking changes

* TCLE-7: if you use the tiger-cloud-extension you need to update it to version 1.10.0 due to changes in organisation of
  some configuration classes. The new tiger-cloud-extension version adds support for copying files into the docker
  container created by the server
  type `docker`. You can copy the files by adding the following configuration to the `dockerOptions`:

```yaml
copyFiles:
  - sourcePath: ./example/path/file_to_copy.txt
    destinationPath: /path/in/container/file_to_copy.txt
    fileMode: 0633
``` 

## Bugfixes

* TGR-1174: when a content-type header is set in a request made with the tiger http client, the charset is no longer
  automatically appended. If no content-type header is set and `activateRbelWriter = true` the content type and charset
  are automatically generated.
* TGR-1342: Zion can now handle root arrays in JSON responses.
* TGR-1318: Byte-Arrays can now be stored and changed in TigerGlobalConfiguration
* TGR-327: Reasonphrase is no longer added implicitly in the TigerProxy (when the phrase is empty from the server it
  will stay empty)
* TGR-1314: Tiger-Proxy: The content-length headers are no longer removed from the responses logged.
* TGR-1334: fixed issue where responses were not correctly paired with the requests in the workflow ui.
* TGR-1194: Usability improvements for the configuration editor.
* TGR-1172: fixed issue where messages coming from a remote tiger proxy would display the timestamp in a different
  timezone.
* TGR-1330: fixed issue where messages coming from a remote tiger proxy on a meshed setup would have the sender and
  receiver addresses switched.

## Features

* TGR-1313: Added support for the new VAU 'Epa für alle' format (VauEpa3).
* TGR-1286: Tiger-Proxy: The number of open connections is now tracked and can be queried.
* TGR-1210: Tiger-Proxy: Connection problems to remote servers are now logged.
* TGR-1351: Tiger-Proxy: Competing routes are now supported. The proxy automatically selects the most specific route if
  multiple routes are matching.
* TGR-1353: You can set a defined port to be used for the WorkflowUI using the configuration
  key `tiger.lib.workflowUiPort`.
* TGR-1315:
  step ```TGR send {requestType} request to {tigerResolvedUrl} with contentType {string} and multiline body:``` added
* TGR-1320: TLS-Facets now also carry the TLS-Protocol and the Cipher-Suite used for the connection
* TGR-1319: New Gluecode added for starting & stopping servers:

```
    Given TGR stop server "remoteTigerProxy"
    And TGR start server "remoteTigerProxy"
```

* TGR-1325: Tiger-Proxy: Added new 'criterion' option for routes. This allows to match requests based on their content:

```yaml
tigerProxy:
  proxyRoutes:
    - from: /
      to: http://orf.at/blub/
      criterions:
        - $.header.foo == 'bar'
```

* TGR-1321: The variable set by the glue code
  step ```TGR set local variable {tigerResolvedString} to {tigerResolvedString}``` now gets automatically cleared after
  the test case run is finished. The new glue code
  step ```TGR set local feature variable {tigerResolvedString} to {tigerResolvedString}``` sets a variable that is
  cleared after the execution of the feature file is finished.
* TGR-1331: remove no longer necessary dependency tools checker.
* TGR-1238: refactored uses of Stream.peek().

# Release 3.0.1

* Serenity BDD 4.1.0
* Cucumber 7.15.0
* RestAssured 5.4.0
* Selenium 4.16.1
* Appium 9.0.0
* Spring Boot 3.2.2
* Logback 1.4.9

## Features

* TGR-1241: Added Case-Insensitive matching in RbelPath-Expressions: `$.body.[~'bar']`
* TGR-1309: CBOR added to Rbel
* TGR-1298: Updated frontend libraries.
* TGR-1310: migrated frontend build tool from vue-cli to vite.

## Bugfixes

* TGR-1306: Zion: When `bodyFile` references a non-existent file, the server now will not start and give a meaningful
  exception.
* TGR-1308: Server-sided certificates with Brainpool-curves are now supported again.
* TGR-1301: fixed an issue where clicking on a message in the traffic visualization diagram did not always open the
  Rbel-Log details pane and scrolled to the corresponding message.
* TGR-1312: fixed an issue where the execution pane would not render correctly.

# Release 3.0.0

* Serenity BDD 4.1.0
* Cucumber 7.15.0
* RestAssured 5.4.0
* Selenium 4.16.1
* Appium 9.0.0
* Spring Boot 3.2.2
* Logback 1.4.9

## Breaking changes

* TGR-1270: The order of messages in the TigerProxy is now chronologically. So pairs (request/response) no longer
  appear next to each other. In the RbelFlow a new icon has been added linking to a potential partner-message.
* TGR-1270: Messages are now parsed asynchronously. The order of the messages in the TigerProxy is the order of the
  messages as they were received. This asynchronous parsing means that messages are added to the TigerProxy before they
  are completely parsed. To minimize confusion the following changes were made:
    * 'getRbelMessagesList()' now only contains the messages up until the first non-parsed message. This means that the
      list might not be complete anymore. The RBelValidatorGlue-steps are prepared (They use a timeout to look for the
      requested message) and should not be affected.
    * 'getRbelMessages()' contains all messages, including the non-parsed ones. To minimize confusion the '
      .getLast()', '.getFirst()', '.peekLast()' and '.peekFirst()' methods now wait until the parsing of the requested
      message is finished. The Iterator ('for(RbelElement msg : tigerProxy.getRbelMessages())') will also wait for the
      requested message to be parsed.
* TGR-1264: Lombok packages no longer are provided to depending test suites. Given best practices and to avoid
  dependency issues in test suites you are enforced to depend on your own version of lombok IF you need it from this
  version on.
* TGR-1276: Ordering of XML-Nodes has changed (now attributes always come first, then child nodes).
* TGR-650: the configuration key `tigerProxyCfg` is renamed in `tigerProxyConfiguration`. You need to update your
  configuration yamls if you use this key.

## Features

* TGR-898: Tiger-Proxy now no longer depends on mockserver, which has been internalized and stripped down.
* TGR-1012: clicking a message in the traffic visualization diagram opens the Rbel Log Details pane and scrolls to the
  corresponding message.
* TGR-1245: Traffic-Logging added to Tiger-Proxy. Can be turned off via configuration key
  ```tiger.tigerProxy.activateTrafficLogging: false```
* TGR-1257: Traffic Visualization feature is documented in user manual.
* TGR-1273: Added support for local port identification of zion servers so that they can be correctly displayed in the
  sequence diagrams.
* TGR-1086: replace OS specific commands with OSHI library for finding ports being used by external jars.
* TGR-1292: Tiger-Proxy now supports OCSP-stapling. To enable it, set the configuration key
  ```tiger.tigerProxy.tls.ocspSigner: myOcspSigner.p12;password``` to the path of the OCSP-signer certificate to be used
  when generating the OCSP responses.
* TGR-1282: Traffic Visualization feature no longer experimental. Can be turned on via the configuration:

```yaml
lib:
  trafficVisualization: true
```

## Bugfixes

* TGR-949: Tiger-Proxy only adds trailing slashes to requests if the request explicitly demands it.
* TGR-938: XML-Messages with UTF-8 content can now be transmitted without alteration.
* TGR-1254: RbelPath-Expressions with a selector and a qualification immediately after (e.g. $..foo[?(content=='bar')])
  are now correctly parsed.
* TGR-1266: Invalid configurations for servers are not reported in console or workflow UI but abort the test env mgr
  silently.
* TGR-1304: Loop-Statements in Zion definitions are now correctly resolved.
* TGR-1276: The RbelWriter now correctly serializes XML-Namespaces.
* TGR-1262: Step "TGR pause test run execution with message {string}" and step "TGR pause test run execution
  with message {tigerResolvedString} and message in case of error {tigerResolvedString}" will now correctly
  be distinguished

# Release 2.3.2

## Breaking changes

* TGR-1173 removed the long deprecated step ```TGR current response at {string} matches {string}```. Replace it with the
  step ```TGR current response with attribute {tigerResolvedString} matches {tigerResolvedString}```.

## Bugfixes

* TGR-1173: refactorings all over the code (more than 500 sonarqube issues) to increase code maintainability
* TGR-1177: Empty fallback values can now be correctly resolved (to an empty string)
* TGR-1229: Added fallback for RbelPaths in JEXL-expressions that yield no results
* TGR-1242: JEXL-expressions that contain RbelPaths that terminate in a star (*) are now working again correctly
* TGR-1231: TigerProxy: The timestamps on messages are no longer wrong if asynchronous parsing is enabled

## Features

* TGR-1096: it is now possible to replay a test scenario from the Workflow UI.
* TGR-1040: we can now observe the traffic in a sequence diagram. This is an experimental feature that needs to be
  activated in the configuration with:

```yaml
lib:
  experimental:
    trafficVisualization: true
```

In unix systems the tool 'lsof' is required to be installed in the system.

# Release 2.3.1

## Bugfixes

* TGR-1125: Tiger-Zion: Fixed assignment-bugs
* TGR-1183: fixed an issue where TigerConfigurationKeys were wrongly ignoring parts of the key when they had a repeated
  subkey.
* TGR-1186: Fixed healthcheck-issues with TLS servers
* TGR-1201: Fixes unresolved environment variables in TigerConfig
* TGR-1180: fixed position of close button in the configuration editor in the WorkflowUi
* TGR-1143: in case of missing dependencies for a server type the exception is more clearly stating that there is a
  dependency missing
* TGR-1190: fixed an issue where when setting fallbacks ( e.g.: ${foo.bar|orThisValue} ) the fallback value would always
  be used also when the value to resolve was existing in the JexlContext.

# Features

* TGR-608: All Tiger core BDD steps are now implicitly using Variable/JEXL resolution on its parameters. So you now may
  use string values for any parameters containing TigerGlobalConfiguration references such as ```${tiger.myprop}``` and
  JEXL expressions like ```"!{resolve(file('testfile.json'))}"``` and they will be resolved by implicit magic. All Tiger
  extensions will be migrating to this automatic resolution with the next version.
* TGR-1214: Tiger Proxy tests are now run in 10 separate forks, this will put your machine under heavy load and requires
  at least 32 GB to run. On Linux using ```nice mvn verify``` may be a good idea

# Release 2.3.0

## Features

* TGR-912: Healthchecks for externalUrl-Servers now honor the configured forwardProxyInfo of the localTigerProxy
* TGR-1176: RbelPath-style retrieval now supported for TigerGlobalConfiguration:
  e.g. `${myMap..[?(@.target=='schmoo')].target}`
* TGR-1163: TigerProxy: The groups to be used for the TLS-Handshakes can now be set for client
* TGR-899: Migrated FAQ.md to FAQ.adoc, translated to english

## Bugfixes

* TGR-1139/TGR-1140: Fixed various minor problems with recursive descent and attributes in RbelPath-expressions
* TGR-1150: TigerProxy WebUI: Correct escaping in inspect-dialog for RbelPath-generation added
* TGR-1038: Changed assertThatNo()-exceptions to assertThatNo.isThrownBy so the tests are not ignored
* TGR-1170: TigerGlobalConfiguration: Fixes for very rare race conditions leading to ConcurrentModification-Exceptions
* TGR-1162: fixed an issue were the amount of test cases was not reported correctly by serenity.

-------

* Serenity BDD 4.0.12
* Cucumber 7.14.0
* RestAssured 5.3.1
* Selenium 4.12.1
* Appium 8.3.0
* Spring Boot 3.1.0
* Logback 1.4.9

# Release 2.2.1

## Features

* TGR-1000: RbelBuilder-Class to create Rbel objects from Files and Strings
* TGR-747: Fallback values can now be defined for configuration keys: ${foo.bar|orThisValue}
* TGR-1001: enable RbelPathExecutor functions for RbelContentTreeNode
* TGR-1002: Place objects and String values at given paths in Rbel objects using RbelBuilder
* TGR-1003: Jexl syntax for serialisation of Rbel objects
* TGR-1004: Expand lists/arrays in Rbel objects

## Bugfixes

* TGR-1110: Zion - XML-structures with nodes named 'text' can now be correctly serialized
* TGR-971: Tiger Proxy UI - when applying filters, the list of senders and receivers is limited to the hosts which send
  or receive more than two messages. An additional checkbox allows displaying the full list of senders and receivers.
* TGR-1098: Tiger Proxy UI: changed buttons "save" to "export" and changed image of the button accordingly
* TGR-1108: Allow nested JexlExpression in RbelPaths (up to one level!)
* TGR-1126: Zion - Honors the encoding information for XML
* TGR-1112: Fixed that log contained a lot of "curl command unable to parse log" lines
* TGR-1123: Fixed Rbellogs flood browser window with highlight js warnings rendering browser unusable for larger HTML
  report files
* TGR-1141: Fixed bug in Workflow Ui causing received CETP messages to stop displaying further messages. Also improved
  display of CETP messages (out of line title)
* All Spinner icons in Workflow UI are now animated

-------

* Serenity BDD 3.6.23
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 3.1.0
* Logback 1.4.9

# Release 2.2.0

## Features

* TGR-1022: Introduce a new httpbin server type. See User Manual section "Tiger test environment manager > Supported
  server nodes and their configuration"
* TGR-1032: With "activateLogs: false" in externalJarOptions for servers the logs are not sent to the workflow UI
  anymore but are still written to console and log files
* TGR-1044: During the execution of the playwright tests screenshots of the WorkflowUI and the WebUI are taken and
  stored in the doc/user_manual directory
* TGR-1041: a new configuration editor in the WorkflowUI allows to view and edit the global tiger configuration during
  the execution of a test suite.
* TGR-567: Tiger Proxy UI - when downloading traffic data, it is now possible to download only the data of the current
  applied filter.
* TGR-1047: Finalising Playwight tests

## Bugfixes

* TGR-1048: Resolved the problems with steps not being found in IntelliJ's Gherkin plugin. Also they were executed, one
  was not able to look them up with auto complete and if entered manually the step was marked as undefined
* TGR-1045: With "activateLogs: false" in externalJarOptions for servers deactivates the logs completely, with "
  activateWorkflowLogs: false" deactivates sending logs to the workflow UI only
* TGR-1057: modified the test httpclientenv.yaml so that it uses the new server type httpbin instead of winstone.
* TGR-1058: RbelPath in the Inspect modal on selected element fixed
* TGR-623: Tiger Proxy UI - when importing a .tgr traffic file, the previously displayed traffic is removed.
* TGR-1074: Scenario Outlines counter fixed
* TGR-873: Tiger Proxy UI - when the connection with the backend is lost a visible error dialog is shown in the UI to
  inform the user.
* TGR-1079: Tiger Proxy UI - when importing a .tgr traffic file, the request/response message pairs are correctly
  identified as such and are displayed together when one of the messages matches the applied filter.

-------

* Serenity BDD 3.6.23
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 3.1.0
* Logback 1.4.8

# Release 2.1.6

## Features

* TGR-1027(2): Extend inline jexl to enable eRp Migration
    * getValue(string) -> string : returns the value of the given variable name. This is extremely helpful for variables
      with multiline content, where using ${variable} would break the JEXL parsing
    * subStringBefore(string) -> string
    * subStringAfter(string) -> string
* TGR-1039: support generation of JUnit5 driver classes by the Tiger maven plugin. For this a new property has been
  added to the configuration: ```<junit5Driver>true</junit5Driver>``` which will create driver classes based on Junit5.
  The following additional dependencies are needed for these:

```xml

<dependencies>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-suite</artifactId>
        <version>1.9.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-junit-platform-engine</artifactId>
        <version>7.11.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

* TGR-990: first mvp of playwright tests for testing the WorkflowUI added

## Bugfixes

* TGR-1028: Fixed bug where the RbelWriter did not serialize an empty json array correctly.
* TGR-1029: Fixed bug where rewriting of Location headers on redirects would remove other headers from the response.
* TGR-1032: Fixed a bug where `additionalCriterions` of a Zion mock response was not correctly serialized into the zion
  configuration properties.
* TGR-915: adding missing content popup buttons to JWE/JWT/VAU elements in rbel logs
* fixed doc example tigerOnly pom.xml so that it runs out of the box

-------

* Serenity BDD 3.6.23
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 3.1.0
* Logback 1.4.8

# Release 2.1.3

## Breaking changes

* with TGR-1015 we fixed the http client steps sending form data (GET/POST) to be not URL encoded.
    * The affected steps are:
        * @When("TGR send {requestType} request to {string} with:")
        * @When("TGR eine {requestType} Anfrage an {string} mit den folgenden Daten sendet:")
        * @Then("TGR sende eine {requestType} Anfrage an {string} mit folgenden Daten:")
    * and
        * @When("TGR send {requestType} request to {string} without waiting for the response with:")
        * @Then("TGR sende eine {requestType} Anfrage an {string} ohne auf Antwort zu warten mit folgenden Daten:")
    * Please be aware that validation of the params needs to be handled differently now.
        * For GET requests you can validate the value of the get request via: $.path.paramname.value which returns the
          URL decoded value
        * For POST requests the value is URL encoded in the body, so we can not easily validate it ULR decoded.
          $.body.paramname will therefore contain the URL encoded value. To ease with validation we have introduced a
          new JEXL helper function !{rbel:urlEncocded('value')} which will return the URL encoded value of the given
          string.

## Bugfixes

* Wildcard file filters are now also supported for windows users
* TGR-956: Fixed a bug for correctly handling concurrent requests in Zion
* TGR-979: Fixed bug that failed BDD tests in tiger-test-lib were ignored and the build was labelled green. Workaround
  was to disable the serenity maven plugin in tiger-test-utils
* TGRFHIR-8: URL-Query parameters are no longer twice URL-encoded when using HTTP Glue Code
* TGR-999: Fixed scenario outlines trigger displaying all subsequent scenarios with data variant counters in titles of
  workflow UI
* TGR-1015: Datatable based param/values of http client GET and POST request steps are now URL encoded per default.

## Features

* TGR-976: Zion-assignments can now contain any string (including Configuration-Placeholders, JEXL-expression and any
  combination thereof)
* TGR-976: New annotation `@TigerSkipEvaluation` which skips the evaluation of configuration values.
* TGR-1010: Added Http client step to send a request with a multiline body (for more complex json requests)
* TGR-1015: To ease with validation of POST form parameters, we have introduced a new JEXL helper function !
  {urlEncocded('value')} which will return the URL encoded value of the given string.
* TGR-977: Zion can now match path variables and assigns them to the values given in the request URL. See User Manual
  section "Tiger Zion > Matching path variables"
* TGR-1020: added http client step to clear all default headers.
    * @When("TGR clear all default headers")
    * @When("TGR lösche alle default headers")
* TGR-1024: added http client step to allow to specify multiple default headers via key value pairs as doc string
    * @When("TGR set default headers:")
    * @Then("TGR setze folgende default headers:")
    * @When("TGR folgende default headers gesetzt werden:")
* TGR-1027: Extend tiger glue code and inline jexl to enable eRp Migration
    * new step to print out the value of a stored variable
        * @Dann("TGR gebe variable {string} aus")
        * @Then("TGR print variable {string}")
    * new inline jexl methods:
        * resolve(string) -> string : resolves all placeholders and jexl expression in string. Very useful in
          combination with the file() method
        * randomHex(int size) -> string : produces a random hex string with size characters
        * currentLocalDate() -> string : produces a string of format YYYY-MM-DD of today

-------

* Serenity BDD 3.6.23
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 3.0.6
* Logback 1.4.7

# Release 2.1.2

## Features

* TGR-954: Rewrite for location Headers in case of forwards with routes with nested paths added:

```yaml
tigerProxy:
  rewriteLocationHeader: true # default value
# activates the rewrite. To deactivate set to false
```

-------

* Serenity BDD 3.6.23
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 3.0.6
* Logback 1.4.7

# Release 2.0.1

## Breaking changes

* TGR-924: German BDD steps to set local and global variables were grammatically incorrect and have been fixed. So "TGR
  setze lokale/globale Variable {string} auf {string} setzen" has been changed to "TGR setze lokale/globale Variable
  {string} auf {string}"
* TGR-909: all **Gherkin / Feature parser code has been removed** from Tiger as Polarion Toolbox (its only usage) has
  been refactored to use the Cucumber internal Gherkin parser. If you based your code on the self written parser, check
  POTO to see how to replace the parsing code in
  the ```polarion-toolbox-client/src/main/java/de/gematik/polarion/toolbox/worker/FeatureFileParser.java``` source file.
* TGR-864: The order of parameters for the "TGR send {} request to {} with body {}" has been changed (body now comes as
  the final parameter). This is done to ensure consistency across languages and to always relegate the potentially
  longest parameter to the last place, improving readability.
* Removed Tiger Admin-UI (Insufficient users)
* TGR-931: For externalJar-Servers that use local-jars: The actual path now starts immediately after the colon.
  While `local://blub.jar` was a working solution as well before, now only `local:blub.jar` is accepted (the slashes
  were discarded before, now everything after the colon is taken the path).
* TGR-948: Changed the behavior for trailing slashes in tiger proxy routes: When either the target-path or the
  request-path end in a slash, a slash will be added to the resulting request. This implicitly fixes a bug that lead to
  doubled slashes in some resulting requests. Caveat: When the request is for a nested target the trailing slash will
  only be added if it was present in the actual request (as the route-target references another entity in this case)

## Bugfixes

* TGR-911: Since 2.0.0 the rbel log files included font awesome v5.4 whereas tiger proxy generated HTML including font
  awesome 6.4. This caused icons to be not displayed or being displayed as invalid icons.
* TGR-922: Waiting for non-paired messages now works correctly
* TGR-841: Non-XML parts in MTOM-Messages are now converted correctly. They are stored in conjunction with their
  respective XPath-Locations. A sample tree is shown here:

```
   └──body (------=_Part_2_1927395369.1677073618377\r\nContent...) (RbelMtomFacet)
   |  ├──contentType (application/xop+xml; charset=utf-8; type="text/xml...) (RbelValueFacet)
   |  ├──reconstructedMessage (<?xml version="1.0" encoding="UTF-8"?>\n<SOAP-ENV:...) (RbelXmlFacet)
   |  |  ├──Envelope (<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas....) (RbelXmlFacet)
   |  |  |  ├──SOAP-ENV (http://schemas.xmlsoap.org/soap/envelope/) (RbelUriFacet)
   |  |  |  |  └──basicPath (http://schemas.xmlsoap.org/soap/envelope/) (RbelValueFacet)
   |  |  |  ├──Header (<SOAP-ENV:Header xmlns:SOAP-ENV="http://schemas.xm...) (RbelXmlFacet)
   |  |  |  |  └──text ()
   |  |  |  ├──Body (<SOAP-ENV:Body xmlns:SOAP-ENV="http://schemas.xmls...) (RbelXmlFacet)
   |  |  |  |  ├──SignDocument (<ns4:SignDocument xmlns:ns4="http://ws.gematik.de/...) (RbelXmlFacet)
   (...)
   |  └──dataParts (<null>)
   |     └──0 (<null>) (RbelMtomDataPartFacet)
   |        ├──xpath (/SOAP-ENV:Envelope/SOAP-ENV:Body/ns4:SignDocument/...) (RbelValueFacet)
   |        └──content (%PDF-1.6\r\n%????\r\n1361 0 obj\r\n<</Linearized 1...) (RbelBinaryFacet) 

```

***** TGR-651: Sender (Client) addresses for messages are now again included in parsed messages of the Tiger Proxy.

## Features

* TGR-920: Non-Blocking mode added for the TGR http steps
* TGR-934: RbelPath-Expressions are now trimmed before parsing. If you have spaces in keys, escape them like
  so: `$.body.['foo bar'].key`
* Custom logos can now be defined for your RBel-Logs. Please use the configuration key "tiger.lib.rbelLogoFilePath" to
  specify a PNG
  file to be used in your logs.
* TGR-931: Local jar-Files can now be found (via the `source`-attribute) relative to the working directory. Wildcards
  are also supported now. So a source-attribute of `../target/app-*.jar` can now be used.
* TGR-950: To find alternating values, concatenate them using the pipe symbols, like so:
  `$.body.['foo'|'bar'].key`
* TGR-869: When multiple properties in either System-Properties or Environment-Variables map to the same value and
  differ in value the startup will
  be aborted with an exception pointing to the conflicting values. This is done to follow the "fail fast" philosophy and
  give the user the chance
  to resolve the conflict instead of choosing an arbitrary value automatically.

-------

* **Serenity BDD 3.6.23**
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* **Spring Boot 3.0.6**
* Logback 1.4.7

# Release 2.0.0

## Bugfixes

* TGR-769: Serenity reports show examples now with a collapse icon clearly indicating whether its folded or unfolded
* TGR-887: Jexl-Selectors in RbelPath-Expressions now differentiate between the current (@.) and the root ($.) element.
* TGR-893: Saving of HTML-Traffic in Webui refactored, HTML rendering has moved from frontend to backend
* TGR-947: Link from Scenario in Sidebar fixed

## Features

* Upgraded Serenity BDD to latest 3.6.X
* Upgraded Spring Boot to latest 3.0.X
* Upgraded to Java 17
* Added first version of FAQ.md
* TGR-867: ExternalUrl server uses source URL as default health check URL if not set
* TGR-872: Added glue code to wait for non-paired messages (optional excluding existing messages)
* TGR-866: Glue-Code to change TLS-certificates for local TigerProxy during test execution
* TGR-888: ${hostname} added as a configuration property
* TGR-890: Workflow Ui now has a quit button in the left sidebar to abort the currently active test execution
* TGR-890: Workflow Ui now has a pause button in the left sidebar to pause test execution. Please be aware that
  timeouts (e.g. while waiting for a rbel message to appear are not affected by the pause mode, thus using pause on
  these steps will cause a timeout and potentially subsequent test failure).
* TGR-901: New server-type "zion" added. Supports adding and configuring zion-Servers directly in a tiger.yaml
* TGR-730: support changing log level similar to spring boot by specifying package or class name and level in tiger.yaml
  under logging.level

```yaml
servers:
  server1:
    ......
logging:
  level:
    de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr: TRACE
    de.gematik.test.tiger.lib.TigerDirector: TRACE
    de.gematik.test.tiger.proxy: TRACE
    localTigerProxy: TRACE
```

## Breaking changes

* tiger-<hostname>.yaml is now read additionally to the tiger.yaml, no longer instead of! Migration should be seamless
  unless you have both a tiger- and a tiger-<hostname>.yaml. In that case manual migration of the properties is
  required, depending on whether you want them as a default on all machines (tiger.yaml) or simply as a host-specific
  configuration (tiger-<hostname>.yaml).

-------

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

# Release 1.3.2

## Bugfixes

* TGR-743: TigerGlobalConfiguration.putValue supports String values supplied via Generics
* TGR-874: Background steps show up in the workflow ui at the end of a scenario
* TGR-874: Data tables show up in workflow ui as simple toString() text
* Fixed ConcurrentModificationException while waiting for request in rbel validation steps

## Features

* TGR-855: Currently executed step is now highlighted in workflow ui
* TGR-822: Added a step to read .tgr files
* TGR-864: all params except for the HTTP method of steps from the http client extension are now resolving ${}
  expressions
* Made timeout for execution pause in workflow ui configurable via property tiger.lib.pauseExecutionTimeoutSeconds
* On quit the background of the sidebar of the workflow ui is now colored coral red to indicate that no backend server
  functionality is available anymore (reload page, backend calls on the rbel log details pane, access to the proxy webui
  page)

-------

# Release 1.3.1

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Bugfixes

## Features

* TGR-760: Einfache Serenity-Evidences aus Extensions heraus.
* TGR-760: FHIR Validierungen mit der Tiger-Extension "Tiger-on-FHIR möglich"
* TGR-000: Fixed special scenario name causes evidence reporter to fail saving file and to abort test run
* TGR-000: Workflow ui does not show keywords of steps
* TGR-000: Shutdown of helm charts is broken

## Breaking changes

-------

# Release 1.3.0

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Bugfixes

* TGR-838: workflow UI links to messages in Rbel details pane did not work for messages not on current page

## Features

* Small workflow UI improvements
* Tiger show message steps now replace ${} tokens with content from TigerGlobalConfiguration
* Integration tests added for new projects and the test for erp-testsuite removed

## Breaking changes

-------

# Release 1.2.1

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Bugfixes

* TGR-592: we are resorting the JUnit driver class template back to run with CucumberWithSerenity ensuring the
  TigerCucumberListener as plugin is set. This listener will now initialize the Tiger on test run started event.
* TGR-780: outputs of external jar type servers will now be logged
* TGR-829: When using the RbelKeyFolderInitializer the used files are not used via filenames but rather directly
* TGR-809: error during selection of nodes in rbel tree in WebUi fixed
* TGR-823: fixed a regression in the workflow UI not showing the rbel log details pane correctly.
* TGR-768: flag createRbelHtmlReports added for creation of the RBEL HTML reports in Tiger testsuites
* TGR-810: improve JEXL context display
* TGR-792: Fixed failures in startup phase not causing the test run to abort
* TGR-792: Fixed Workflow ui not showing docstring and datatable data of steps

## Features

* TGR-814: Interface for remote traffic sources added in Tiger Proxy
* TGR-826: Support for Form-Parameter added to HTTP-Client-Extension
* TGR-574: CETP messages can now also be transmitted in a mesh-setup

## Breaking changes

TGR-714: docker, docker compose server types have been merged with helm chart support into the tiger-cloud-extension a
separate project to be published together with this release. **So to keep using docker functionality within tiger make
sure to add the additional dependency to your project.** If you don't use docker or helm chart you should not be
affected at all.

* TGR-817: major rewrite of Tiger Serenity integration. We now more closely are integrated with Serenity, fixing bugs
  like cucumber tag filter property not always being respected, using multiple examples (with Annotations) causes the
  Workflow UI to show wrong data in the test execution pane. When using the tiger-maven-plugin to generate your test
  driver classes no modification to your code is necessary. Else, please note that:
    * the package of the TigerCucumberRunner class has been changed
    * the TigerCucumberListener is now automatically added in standalone and mvn context, so remove it from your plugins
      cucumber options.

-------

# Release 1.1.1

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Bugfixes

* TGR-789: Fixed escaping-problems for Rbel-Path expressions embedded in JEXL
* TGR-763: Fix for varying Base64-Encryption methods used for token_key

## Features

* TGR-745: Small performance boost for JEXL-execution
* TGR-745: More compact Rbel-messages in WebUI
* TGR-745: Ability added to filter TGR-files
* TGR-793: TigerTypedConfigurationKey added
* TGR-788: Negated JEXL filters are now correctly applied when reading TGR files in the Tiger Proxy
* TGR-758: Pairs are no longer discarded when filtering remote Traffic
* TGR-782: Version is now displayed correctly for Standalone-Proxies
* TGR-766: Added support for Java 8 Date/Time Types to TigerGlobalConfiguration
* TGR-757: WebUI: Return feedback on filtered messages to the user
* TGR-800: Added HTTP client to test-lib
* TGR-575: Added support for CETP messages in tiger-rbel
* TGR-796: Delayed evaluation for TigerGlobalConfiguration added
* TGR-559: Code optimized in TigerDirector
* TGR-696: Added helm chart to User Manual
* TGR-640: Logs out tiger configuration and environment variables when debug log level is set
* TGR-732: WorkflowUi: LokalTigerProxy is shown in server list on the left and also appears in the logs
* TGR-705: Tiger Proxy health endpoint now also checks responsiveness of internal mock server and adds more details to
  the health status
* TGR-748: MavenTigerPlugin: add configuration property for opening the browser containing the serenity report when
  performing the generate-serenity-report goal

## Breaking changes

* TGR-745: VAU traffic is no longer decrypted by default. Please set the appropriate flags to do so (
  activateErpVauAnalysis and activateEpaVauAnalysis)
* TGR-640: when using the tiger yaml property ```localProxyActive: false``` the field localTigerProxy in the
  TigerTestEnvMgr is now returned as null to save test execution time as we don't start it up any longer if no local
  proxy is desired.

-------

# Release 1.1.0

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Features

* TGR-741: tiger-commons now has minimal dependencies (no more mock server et al.)
* TGR-697: TigerProxy dynamically creates CA certificate, EE-certs now have 13 month validity
* TGR-704: TigerProxy WebUI now displays complete P-Header for EPA-VAU-messages
* TGR-584: removing obsolete dependencies from all tiger modules
* TGR-412: ObjectMapper of TigerGlobalConfiguration can now be accessed (and customized)
* TGR-634: WebUi: Remove all filters with port > 32768 for better clarity

## Bugfixes

* TGR-742: Fixed NPE for some ForwardProxy routes
* TGR-750: Fixed rare race condition when multiple TestEnvMgrs where created with faulty configuration in a single JVM

## Breaking changes

* if you update to 1.1.0 and your tests aren't executed anymore please add the 'junit-vintage-engine'-dependency to your
  project

-------

# Release 1.0.0

* Serenity BDD 3.3.10
* Cucumber 7.4.1
* RestAssured 5.2.0
* Selenium 4.5.2
* Appium 8.1.1
* Spring Boot 2.7.5

## Features

* TGR-727: WebUi: Filter in modal outsourced
* TGR-707, TGR-691: Tiger now supports Helm charts (you need to include the tiger-helm-extensions to your project)
* TGR-718: Added option (rewriteHostHeader) to rewrite host headers for reverse proxy routes
* RBEL-69: Keys from JWKs structures are now added to the key store
* TGR-685: DirectReverseProxy added
* TGR-510: The CipherSuites which are used by the TigerProxy as a TLS client are now configurable
* TGR-708: Maximum parseable and displayable message size of the Tiger Proxy can now be configured
* TGR-664: WebUi: Copies body of response via click on button
* TGR-694: JSON-Arrays can now be used as root-objects in JsonChecker-Assertions
* TGR-525: Rbel moved into Tiger
* TGR-710: FileWriter now saves Pairing Information
* TGR-526: Traffic-Parsing no longer blocks traffic-forwarding
* TGR-710: Undecipherable VAU-Messages logged more concisely
* TGR-688: /rbel endpoint removed from Tiger Proxy
* TGR-687: Rbel-Namespace extended (lastRequest/lastMessage/getValueAtLocationAsString)
* TGR-701: Paged view added to Tiger Proxy WebUI
* TGR-694: JsonChecker can now correctly check nested structures

## Breaking changes

* TGR-700: RbelMessageValidator is now enforcing singleton pattern

## Bugfixes

* TGR-706: Traffic filter for EPA-VAU KVNR works
* TGR-680: The Tiger-Proxy can again use MutualTls as a client
* TigerConfigurationHelper now correctly parses multiple keys in a single YAML-line

-------

# Release 0.24.1/0.24.2

* Serenity BDD 3.2.5
* RestAssured 5.0.1
* Selenium 4.1.4
* Appium 8.0.0
* Spring Boot 2.7.0

## Features

* TGR-627: Removed unnecessary extra caching inside the mockserver, reducing the memory-footprint.
* TGR-657: Enum-Values in TigerProxy are now parsed case-insensitive
* TGR-638: Workflow UI: It is now possible the show HTML text in the workflow message
* TGR-663: WebUi: All headers are now collapsable
* TGR-577: User can ignore scenarios or whole features by adding "@Ignore" above the scenario/feature in the feature
  file
* TGR-678: The log files in the target/rbellogs directory are now saved with a timestamp at the end of the file name
* TGR-596: WebUi: Text based Regex/Search as search filter
* TGR-683: WebUi: Button for binary content fixed
* TGR-544: TigerProxy: Added option for TLS-version
* TGR-463: re-/create TigerProxy ID from client certificate
* TGR-609: Tiger-Test-Library: A step to get the absolute last request (no path input)

## Breaking changes

* TGR-540: Migration unto the main-branch mockserver. This breaks the client-address. Rbel-Messages no longer carry the
  information who sent the request (or who received the response). This will be added back in a later version (Ticket
  TGR-651)

## Bugfixes

* TGR-662: trafficEndpointFilterString are now honored for standalone tiger proxies
* TGR-682: The TigerProxy no longer alters the Host-header for reverse-proxy routes
* TGR-679: mouse-over for long requests fixed
* TGR-660: Admin UI fixed

-------

# Release 0.24.0

## Features

* TGR-331: In the Workflow UI as well as in the WebUi there is a drop-up in the menu which allows the user to filter the
  message requests from and to a certain server. The corresponding JEXL expression will be added to the input field
* TGR-595: Spring boot health endpoints are added to the Tiger Proxy
* TGR-545: In der WebUI we use a WebSocket now to inform the frontend that new traffic is available instead of pulling
  regularly or manual

## Breaking changes

* TGR-613: We removed the deprecated Cucumber-Steps in RBelValidatorGlue for good

## Bugfixes

* TGR-624, TGR-630, TGR-633: Small fixes in the WebUi

-------

# Release 0.23.4

## Dependencies

* Serenity BDD 3.2.5
* RestAssured 5.0.1
* Selenium 4.1.4
* Appium 8.0.0

## Features

* TGR-580: Added two new steps "TGR pause test run execution with message {string}" and "TGR pause test run execution
  with message {string} and message in case of error {string}". The first one is the same as "TGR pause test run
  execution" but you can alter the message now. The second one gives you the choice to fail the testcase, if whatever
  you are waiting for or checking is not like you expected. All three steps are Workflow UI only now
* TGR-594: optimize vertical spacing of messages in workflow UI rbel log details pane overview. Also changed hide header
  button to hide details button in bottom nav

## Bugfixes

* TGR-561: Generated files from the tiger-maven-plugin now have leading zeros, so they execute in ascending order
* TGR-589: Massive amount of externalURL servers can lead to concurrentmodification errors in proxy addroute method and
  will cause the testenv mgr to abort on setup of test environment, fixed by synchronizing the addRoute method in Tiger
  Proxy
* TGR-593: Fixing lookup of docker compose files (located in classpath and using relative path references
  containing "../")
* TGR-594: Fixing raw modal popup not working in all scenarios (workflow UI, Proxy webui, rbel log HTML file)
* TGR-536: Filtering on the website no longer splits up message-pairs

## Breaking changes

* TGR-590: we removed tiger-aforeporter-plugin from Tiger

-------

# Release 0.23.3

## Dependencies

* Serenity BDD 3.2.4
* RestAssured 5.0.1
* Selenium 4.1.3
* Appium 8.0.0

## Bugfixes

* TGR-534: When a shutdown is triggered during Startup External-Jars are now terminated correctly
* TGR-486: Requirements are now correctly reported when using the Tiger maven plugin to create the serenity report
* TGR-550: Fixed scenario outlines contain background steps multiple times
* TGR-524: Tiger local proxy and Tiger WorkflowUI are now running on separate ports not interfering with ports from the
  free.port.x range
* TGR-370: Fixed standalone proxy failing on startup with null pointer exception

## Features

* TGR-541: Tiger Proxy WebUI now displays message timestamp in the menu
* TGR-534: Traffic can now be uploaded from the Tiger Proxy WebUI
* TGR-548: JEXL Debugging/RbelPath Debugging: help text is now collapsible
* TGR-547: Tiger Workflow UI now links the scenarios in the feature list section in the sidebar with the scenario and
  its steps shown in the central execution pane for easier navigation
* TGR-564: Show timestampes for loaded messages too, some optimizations to reduce vertical spacing in rbel log report.
  Saving traffic data now is stored in a file with it's name containing the current date
* TGR-578: Headers im Rbel-Log in the WorkFlowUI and in the Tiger Proxy WebUI are now collapsable
* TGR-551: Improve JEXL Debugging - each node is now linked to update filter

-------

# Release 0.23.2

## Dependencies

* Serenity BDD 3.2.4
* RestAssured 5.0.1
* Selenium 4.1.3
* Appium 8.0.0

## Breaking changes

* TGR-487: glue code "TGR current response at {string} matches as {word}:" now accepts an enums (JSON|XML) instead of
  strings and deprecated glue code "TGR current response at {string} matches as {word}" is removed
* TGR-493: The Test library no longer produces a test jar as we moved all BDD stuff to src/main, thus including the
  tiger-test-lib jar will suffice. Please adapt your pom.xml to **not any more depend on tiger-test-lib test-jar**

## Features

* TGR-510: The CipherSuites which are used by the TigerProxy as a TLS client are now configurable

## Bugfixes

* TGR-553: Fixed race-condition during initial connection to remote tiger-proxies
* TGR-553: Fixed Race-Condition for multiple configured traffic sources
* TGR-553: Major resilience overhaul for reconnection in TigerProxy-Mesh-Setups

-------

# Release 0.23.0

## Breaking changes

* TGR-483 rbel validator steps using a doc string as parameter and not ending with a colon ":" are marked deprecated and
  are replaced with a correct version ending with ":" The old version will be removed with 0.23.1

## Bugfixes

* TGR-528: Solved Race-Condition on out-of-order Message-Parts
* TGR-528: Solved ConcurrentModificationException in WebUi
* TGR-530: Eliminate extraneous VauSessionFacet-Instances
* TGR-530: Only apply modifications when needed
* TGR-530: Standalone Tiger Proxy now respect tigerProxy.adminPort
* TGR-529: Serverless test runs caused Workflow UI to not update
* TGR-530: Sender & Receiver are transmitted via tracing/mesh-setup
* TGR-532: When reading values directly from TigerGlobalConfiguration placeholders are now always implicitly resolved

## Features

* TGR-483 adding new steps to RbelValidator, allowing to check for existence of node in request / response and allowing
  to check for mismatching node in response

-------

# Release 0.22.2

## Bugfixes

* TGR-523: Connection-lost issues for Tiger Proxy mesh-setups fixed.

-------

# Release 0.22.1

## Breaking Changes

* Tiger maven plugin now purges the target/generated-test-sources/tigerbdd folder to avoid left over driver class files
  from previous runs.
* Tiger maven plugin will by default only search for feature files in src/test/resources/features folder tree from now
  on, to avoid duplicate feature files also found in target subfolders.

## Features

* A new step "TGR pause test run execution" will pause the execution and if workflow UI is active will display a nice
  banner with a continue button. Without workflow UI you can enter "next" in the console and press ENTER to continue
  test execution

## Bugfixes

* TGR-516 "TGR wait for user abort" is now also working when workflow UI is disabled. You must have a console like
  environment like Bash or GitBash to be able to enter "quit" and press Enter
* TGR-519: Fixed propagation in aggregating tiger proxies
* TGR-521: Race-Condition fixed for heavy-duty TigerProxy settings

## Features

* TGR-236: Logs of Tiger server instances are now also written to server specific log files

-------

# Release 0.21.0

## Breaking Changes

* TGR-480: Healthchecks for docker compose services are not any more supported
* TGR-480: Upgrade testcontainers 1.17.0, exposed ports behaviour has changed
* TGR-513: The way tests are started and teared down have changed to prepare for workflow UI landing. The quit step is
  not functional at the moment, but the workflow UI will pause before shutdown and show a quit button to finish the test
  run (shutting down the test env). If no workflow UI is active the test will finish normally.
* TGR-248: removed OSEnvironment class, functionality is replaced by TigerGlobalConfiguration

## Features

* TGR-482: TGR findLastRequest added in the glue code

## Bugfixes

* TGR-480: docker compose is not working, in the fix a complete rewrite of the compose code part has been done, dropping
  the healthchecks property.

-------

# Release 0.20.2

## Breaking Changes

* TigerDirector.getProxySettings is renamed to TigerDirector.getLocalTigerProxyUrl. Function remains unchanged.
* During Startup TigerDirector now sets the localTigerProxy as the default-Proxy.
* TGR-479: clean up the yaml file
    * healthcheck renamed to healthcheckUrl and move up one level

## Features

* TGR-473: local tiger proxy now starts as springboot app thus providing also the webui interface.
* TGR-489: New test-steps: Print current request/response as rbel-tree
* TGR-509: Server-Health-checks can now also be verified with given return code

## Bugfixes

* TigerProxy-WebUI can now display traffic again
* TGR-503: The Testenv-Mgr now by default uses the own JVM to start externalJar-Servers. This can be overriden by
  setting `tiger.lib.javaHome`
* TGR-508: Remote Tiger-Proxies can now also be supplied with TLS-Identities

-------

# Release 0.20.1

## Breaking Changes

* RBEL-54: RbelElement now returns RbelMultiMap instead of List<Entry<String, RbelElement>>. Conceptually nothing
  changed
* TGR-469: clean up the yaml file
    * serverPort is renamed in adminPort
    * port is renamed to proxyPort
    * proxyCfg under tigerProxyCfg is omitted

## Features

* TGR-461: Smaller improvements in TigerGlobalConfiguration:
    * `TigerGlobalConfiguration.localScope()` allows scoped value addition
    * Placeholders in keys are now resolved
    * `TigerGlobalConfiguration.putValue()` now resolves nested values from objects
    * instantiateConfigurationBean now returns an optional, allowing greater control when key was not found in
      configuration.
* TGR-450,434,347: Updates im User manual (Failsafe plugin, chapter 5.4, smaller glitches)
* TGR-440: Serenity dependencies are now provided to allow using Tiger without SerenityBDD
* TGR-456: Useability review of admin ui
* Tiger maven plugin has a second goal to replace the SerenityBDD maven plugin for generation of reports.
* RBEL-54: RbelMessages now contain transmission timestamps.

## Bugfixes

* TGR-485: fix IndexOutOfBoundsException when using identical names for feature scenarios
* TGR-411: pki keys saved correctly via admin ui
* TGR-308: order of nodes now restored on load in admin ui
* TGR-461: Base key in `additionalYamls` is now honored

-------

# Release 0.20.0

## Breaking Changes

* TGR-392: The tiger-bdd-driver-generator-maven-plugin is being replaced by the tiger-maven-plugin.
    * The configuration is downward compatible
    * The configuration is no longer strictly required. Sane defaults are supplied and can be used by convention over
      configuration.

## Features

* TGR-438: Improved JEXL-Debugging-Dialog
* TGR-453: Additional YAML-files can be loaded when references in tiger.yaml
* TGR-448: Inline-JEXL-expressions added

## Bugfixes

* TigerProxy-WebUI no longer reloads already loaded messages
* ExternalJar-Server now correctly configures internal system properties

-------

# Release 0.19.4

## Breaking Changes

* TGR-188: tiger-testenv.yaml wird nun zunächst in der tiger.yaml gesucht. Um den Umstieg zu erleichtern wird die
  tiger-testenv.yaml weiterhin eingelesen aber eine Warnung ausgeben. Probleme gibt es nur für Nutzer, die schon vorher
  eine tiger.yaml (oder tiger.yml) in ihrem Projekt liegen haben: Diese muss umbenannt werden.

## Bugfix

* TGR-430: Requests without Deep-Paths are now forwarded from remote Tiger-Proxies
* TGR-424: Disabled Rbel-Parsing will no longer impact traffic forwarding
* TGR-422: Find request to path "http://server" now also matches "/"
* TGR-427: JSON-Checker now correctly reports mismatched types and ill-formatted JSONs
* TGR-425: Configuration Property for the local tigerProxy.port added when using free ports
* TGR-423: Default-VAU-Key now correctly included

## Features

* TGR-188: TigerTest-Extension for JUnit-Jupiter added
* TGR-414: Add an example project for the use of tiger
* TGR-431: Tiger can now also read traffic-files on startup

-------

# Release 0.19.3

## Bugfix

* TGR-294: Tiger hook causes null pointer exception before steps execution
* TGR-380: Tiger-User-Manual (HTML) now has pictures
* TGR-419: Tiger-Proxy WebUI now also functions in Test-Env Standalone configuration
* TGR-415: Potential memory-leaks in Tiger-Proxy fixed

## Features

* TGR-357: Tiger-Proxy traffic endpoints can now filter incoming traffic (reducing load, improving usability)
* TGR-294: Rbel Log html page now shows data variant values when clicking on button in subtitle section
* TGR-389: EPA-VAU messages are now always tagged with the target account KVNR (only when tigerProxy.activateVauAnalysis
  is activated)
* TGR-358: Tiger-Proxy WebUI can now filter messages
* TGR-416: Traffic-Log can now be downloaded in the Tiger-Proxy WebUI
* TGR-420: Jexl-Debugging dialog added to Tiger-Proxy WebUI

-------

# Release 0.19.2

## Bugfix

* TGR-381: Opening new testenv does not clear previous testenv nodes in admin UI
* TGR-379: Exception on admin UI backend are not handled appropriately
* TGR-319: Fixed admin UI sidebar is not scrollable
* TGR-367: Show file dialog in admin UI only if backend is available
* TGR-360: Save as... is not disabled on new testenv in admin UI
* TGR-399: Large messages will now be split in tracing

## Features

* Sidebar in Admin UI is now collapsable
* TGR-394: Added configuration variables for free ports (${free.port.0-255})
* TGR-388: add curl command details to REST calls in the SerenityBDD report
* TGR-395: TIGER_ACTIVE is no longer necessary

-------

# Release 0.19.1

## Bugfix

* Fixing module with incomplete -sources and -javadoc JAR files

-------

# Release 0.19.0

## Breaking Change

* TGR-113: Test-Context, Test-Config and Test-Variables are deprecated. All values are now stored
  using `TigerGlobalConfiguration`. This supersedes all uses of domains and context, of which there are no known
  instances. If your migration is difficult, please contact the team.
* The migration entails a complete rethink of configuration and value-stores in tiger. If you have any troubles please
  read the user-manual (Chapter 6) and don't hesitate to ask us.
* TGR-299: The configuration flag `proxyProtocol` from the server-type tigerProxy has been renamed
  to `proxiedServerProtocol` to clarify usage and avoid confusion.
* If a `proxiedServer` did have a Healthcheck-URL (or source-URL for `externalUrlServer`) previously the reverse-proxy
  would target the deep-path. Now it will target the domain only.
* TGR-197: PKIIdentities must not any longer be specified using Windows specific paths. Please use linux "/" folder
  separator character and relative paths to ensure cross-platform support of your test environments.

## Bugfix

* TGR-350: Multiple Scenariooutlines in feature file break test execution.
* RBEL-41: Too restrictive validation of hostnames in RbelHostname element

## Features

* TGR-113: Configuration has been refactored/simplified and all placeholders in tiger-servers are now successfully
  resolved.
* TGR-332: Tiger proxies are no longer instantiated as external Jar downloaded from maven central but are started in
  process. This dramatically reduces startup time and reduces bandwidth demands.
* TGR-341: Upgrade to Serenity BDD v3.1.16 and thus Selenium 4

-------

# Release 0.18.1

## Bugfix

* Tiger admin UI allow to rename nodes to ""

## Features

* TGR-50: Tiger test environment configuration now has an admin Web UI. Check out the tiger-admin module
* TGR-306: Tiger admin UI now has "Duplicate node" feature

-------

# Release 0.18.0

## Breaking Change

* TGR-113: Major rework in TigerConfiguration. The Blackbox behavior is unchanged in all known cases. However the API
  changed a lot! TigerConfigurationHelper was deleted.
* If you want to read configuration files please use `TigerGlobalConfiguration` (in cases where you want to use the
  global configuration store) or instantiate a new `TigerConfigurationLoader` (in cases where you only need the
  configuration functionality and not the actual global configuration).
* The serialization/deserialization Utils from TigerConfigurationHelper please refer to TigerSerializationUtil (
  JSON/YAML conversion).

## Bugfix

* TGR-288: Make snakeyaml/jackson work with default values in Configuration object
* TGR-305: Concurrent downloads of the same external JAR can now coexist peacefully
* TGR-325: Parallel startup now waits correctly for long-running startups

## Features

* TGR-42: Make Proxy Cert available at runtime
* TGR-264: add support for reason phrases in RBEL

-------

# Release 0.17.1

## Bugfix

* TGR-222: Trustmanager im AbstractExternalTigerServer lokal begrenzen

## Features

* TGR-269: Configuration for HTTPS forward proxy

## Enhancements

* TGR-250: Tests überarbeiten in TestTokenSubstituteHelper

-------

# Release 0.17.0

## Breaking Changes

* TGR-208: The startup-order of the servers is now ONLY determined by the "dependsUpon"-Flag. This can give a comma
  separated list of servers that need to be started prior to the given server. Please review & change all testenv.ymls
  accordingly.
* TGR-193:
    * PKI Keys' type have been changed from "cert / key" to "Certificate / Key"
    * disableRbelParsing attribute has been renamed to activateRbelParsing with default value true

## Features

* TGR-208: Refactoring Testenv-Mgr
* TGR-208: Startup of services by Testenv-Mgr can now be parallel and in given sequence (dependsUpon-Flag added)
* TGR-218 added REST-Interface for Modifications
* TGR-96: support basic auth for forward proxy
* TGR-226: Set proxy username, proxy password as environment variables
* TGR-238: Additional TGR step to store value of rbel path node to context
* TGR-238: Support TGR validation steps to support "${..}" tokens in TGR verification steps

## Bugfix

* TGR-171: Banner Helper Klasse unterstützt keine deutschen Umlaute

-------

# Release 0.16.4

## Bugfix

* TGR-219 bdd driver generator plugin created invalid feature path on windows

-------

# Release 0.16.2

## Bugfix

* TGR-212 Content-Length now correctly after Rbel-Modifications

-------

# Release 0.16.0

## Features

* TGR-136 Client-addresses are now correctly set in Rbel-messages
* TGR-120 The SSL-Suits of the servers are now configurable

-------

# Release 0.15.0

## Features

* TGR-136 Client-addresses are now correctly set in Rbel-Messages
* TGR-186 First version of an UI test run monitor, displaying all banner and text messages to guide manual testers.

## Bugfixes

* TGR-183 German keyword "Gegeben sei" was not correctly detected by FeatureParser
* TGR-41 Competing routes are now correctly identified and refused when adding
* TGR-179 TGR Step "show color text" failed with unknown color name

-------

# Release 0.14.0

## Neues

* TGR-173 Die TGR BDD Testschritte stehen nun auch auf Deutsch zur Verfügung
* TGR-131 Der RbelPath Executor unterstützt nun einen Debug Modus um bei fehlerhaften RbelPath Ausdrücken die
  Fehlersuche zu erleichtern. [Mehr Details...](doc/testlib-config.md)
* TGR-133 Release des mvn plugins um die Generierung der Treiberklassen für die Serenity tests auch in nicht englischer
  Sprache zu unterstützen. [Mehr Details...](tiger-maven-plugin/README.md)
* TGR-165 EPA VAU Schlüssel ist fest im Tiger Proxy hinterlegt
* TGR-168 Proxy modifications unterstützt nun auch Query Parameter modifications
* TGR-112 Dokumentation für Modifications Feature [Mehr Details...](tiger-standalone-proxy/README.md)
* TGR-63 Exceptions, die in einem Upstream Tiger Proxy auftreten werden über die WS-Schnittstelle an downstream Proxies
  kommuniziert.

## Änderungen

* **BREAKING** TGR-87 Die Serverliste im tiger-testenv.yml wurde angepasst. Das Attribut 'name' wurde entfernt und durch
  das optionale Attribut 'hostname' ersetzt. Sollte 'hostname' nicht definiert werden, wird es auf den Keywert des
  Mapeintrages gesetzt. Diese Änderung bedeutet, dass zwar der Hostname bei mehreren Servereinträgen identisch sein
  kann, allerdings muss der Keywert **eindeutig** sein. Details zu der Migration befinden sich weiter unten.

## Entfernt

* TGR-173 Die Ausgabe der Testschritte erfolgt nun nicht mehr über Tiger, sondern kann
  im [serenity.properties](https://serenity-bdd.github.io/theserenitybook/latest/serenity-system-properties.html)  
  über serenity.logging=VERBOSE aktiviert werden.

## Fehlerbehebungen

* TGR-159 Null TLS attribute in tiger-testenv.yml führten zu Startabbruch
* TGR-166 Concurrent Modification Exceptions traten im Bereich Tiger Proxy Nachrichten auf

## Migrationsdetails

Aufgrund des breaking changes sind **ALLE** tiger-testenv.yml Dateien im Bereich servers anzupassen:

```yaml
tigerProxy:
# ...
servers:
  # ALTE VERSION
  - name: idp
  # NEUE VERSION
  idp1:
    hostname: idp
    #
    type: externalUrl
      ...
      active: true

        # ALTE VERSION
        - name: idp
  # NEUE VERSION
  idp2:
    hostname: idp
    #
    type: docker
    ...
    active: false  
```

-------

# Release 0.13.0

* Modifications added to tiger-proxy (Documentation still outstanding)
* Fixed binary-transmission bug
* Documentation added

-------

# Release 0.12.0

* Bugfix tiger-proxy: ASN1.Encodable parsing
* Extended TLS-configuration (now supports custom domain-names, useful for a reverse-proxy-setup)
* TLS-properties moved to tigerProxy.tls (will give error on startup if missused)
* Reverse-proxy-routes give the target-server as receiver/sender

-------

# Release 0.11.0

Bug fix Release Lokale Resources fix (routeModal.html)
Messagelist index fixed im Webui WorkingDir wird automatisch angelegt

-------

# Release 0.10.0

* Memory-Leak fixed
* UI Sequence-numbers fixed
* Manual Update-Bug fixed

-------

# Release 0.9.0

* Longer chains are supported for Tiger Proxy TSL-Identities
* Chains are verified on startup
* Memory Management for tiger-standalone improved
* support for demis docker compose
* allow to use local resources
* bug fix for basic auth on reverse proxy
* proxy system prop detection in tiger director
* TigerProxy as type in TestEnv

-------

# Release 0.8.1

Javadoc Fix

-------

# Release 0.8.0

* Bugfix TigerRoute YAML instantiation

-------

# Release 0.7.0

mvn deploy fix

-------

# Release 0.5.0

mvn deploy fix

-------

# Release 0.4.0

mvn central deploy fix

-------

# Release 0.3.0

Initial release
