# Changelog Tiger Testplattform

-------
* **Serenity BDD 3.6.20**
* Cucumber 7.11.0
* RestAssured 5.2.0
* Selenium 4.8.0
* Appium 8.3.0
* Spring Boot 2.7.5
* Logback 1.2.11

# Release 2.0.0

## Bugfixes

* TGR-769 Serenity reports show examples now with a collapse icon clearly indicating whether its folded or unfolded
* TGR-893: Saving of HTML-Traffic in Webui refactored, HTML rendering has moved from frontend to backend

## Features

* Upgraded Serenity BDD to latest 3.6.20
* Added first version of FAQ.md
* TGR-867: ExternalUrl server uses source URL as default health check URL if not set
* TGR-872: Added glue code to wait for non-paired messages (optional excluding existing messages)
* TGR-888: ${hostname} added as a configuration property
* TGR-890: Workflow Ui now has a quit button in the left sidebar to abort the currently active test execution
* TGR-890: Workflow Ui now has a pause button in the left sidebar to pause test execution. Please be aware that timeouts (e.g. while waiting for a rbel message to appear are not affected by the pause mode, thus using pause on these steps will cause a timeout and potentially subsequent test failure).

## Breaking changes

* tiger-<hostname>.yaml is now read additionally to the tiger.yaml, no longer instead of! Migration should be seamless unless you have both a tiger- and a tiger-<hostname>.yaml. In that case manual migration of the properties is required, depending on whether you want them as a default on all machines (tiger.yaml) or simply as a host-specific configuration (tiger-<hostname>.yaml).

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
* TGR-864: all params except for the HTTP method of steps from the http client extension are now resolving ${} expressions
* Made timeout for execution pause in workflow ui configurable via property tiger.lib.pauseExecutionTimeoutSeconds
* On quit the background of the sidebar of the workflow ui is now colored coral red to indicate that no backend server functionality is available anymore (reload page, backend calls on the rbel log details pane, access to the proxy webui page)

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

* TGR-592: we are resorting the JUnit driver class template back to run with CucumberWithSerenity ensuring the TigerCucumberListener as plugin is set. This listener will now initialize the Tiger on test run started event.
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

TGR-714: docker, docker compose server types have been merged with helm chart support into the tiger-cloud-extension a separate project to be published together with this release. **So to keep using docker functionality within tiger make sure to add the additional dependency to your project.** If you don't use docker or helm chart you should not be affected at all.

* TGR-817: major rewrite of Tiger Serenity integration. We now more closely are integrated with Serenity, fixing bugs like cucumber tag filter property not always being respected, using multiple examples (with Annotations) causes the Workflow UI to show wrong data in the test execution pane. When using the tiger-maven-plugin to generate your test driver classes no modification to your code is necessary. Else, please note that:
  * the package of the TigerCucumberRunner class has been changed
  * the TigerCucumberListener is now automatically added in standalone and mvn context, so remove it from your plugins cucumber options.

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
* TGR-705: Tiger Proxy health endpoint now also checks responsiveness of internal mock server and adds more details to the health status
* TGR-748: MavenTigerPlugin: add configuration property for opening the browser containing the serenity report when performing the generate-serenity-report goal

## Breaking changes

* TGR-745: VAU traffic is no longer decrypted by default. Please set the appropriate flags to do so (activateErpVauAnalysis and activateEpaVauAnalysis)
* TGR-640: when using the tiger yaml property ```localProxyActive: false``` the field localTigerProxy in the TigerTestEnvMgr is now returned as null to save test execution time as we don't start it up any longer if no local proxy is desired.

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

* if you update to 1.1.0 and your tests aren't executed anymore please add the 'junit-vintage-engine'-dependency to your project

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
* TGR-577: User can ignore scenarios or whole features by adding "@Ignore" above the scenario/feature in the feature file
* TGR-678: The log files in the target/rbellogs directory are now saved with a timestamp at the end of the file name
* TGR-596: WebUi: Text based Regex/Search as search filter
* TGR-683: WebUi: Button for binary content fixed
* TGR-544: TigerProxy: Added option for TLS-version
* TGR-463: re-/create TigerProxy ID from client certificate
* TGR-609: Tiger-Test-Library: A step to get the absolute last request (no path input)

## Breaking changes

* TGR-540: Migration unto the main-branch mockserver. This breaks the client-address. Rbel-Messages no longer carry the information who sent the request (or who received the response). This will be added back in a later version (Ticket TGR-651)

## Bugfixes

* TGR-662: trafficEndpointFilterString are now honored for standalone tiger proxies
* TGR-682: The TigerProxy no longer alters the Host-header for reverse-proxy routes
* TGR-679: mouse-over for long requests fixed
* TGR-660: Admin UI fixed

-------
# Release 0.24.0

## Features

* TGR-331: In the Workflow UI as well as in the WebUi there is a drop-up in the menu which allows the user to filter the message requests from and to a certain server. The corresponding JEXL expression will be added to the input field
* TGR-595: Spring boot health endpoints are added to the Tiger Proxy
* TGR-545: In der WebUI we use a WebSocket now to inform the frontend that new traffic is available instead of pulling regularly or manual

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

* TGR-580: Added two new steps "TGR pause test run execution with message {string}" and "TGR pause test run execution with message {string} and message in case of error {string}". The first one is the same as "TGR pause test run execution" but you can alter the message now. The second one gives you the choice to fail the testcase, if whatever you are waiting for or checking is not like you expected. All three steps are Workflow UI only now
* TGR-594: optimize vertical spacing of messages in workflow UI rbel log details pane overview. Also changed hide header button to hide details button in bottom nav

## Bugfixes

* TGR-561: Generated files from the tiger-maven-plugin now have leading zeros, so they execute in ascending order
* TGR-589: Massive amount of externalURL servers can lead to concurrentmodification errors in proxy addroute method and will cause the testenv mgr to abort on setup of test environment, fixed by synchronizing the addRoute method in Tiger Proxy
* TGR-593: Fixing lookup of docker compose files (located in classpath and using relative path references containing "../")
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
* TGR-524: Tiger local proxy and Tiger WorkflowUI are now running on separate ports not interfering with ports from the free.port.x range
* TGR-370: Fixed standalone proxy failing on startup with null pointer exception

## Features

* TGR-541: Tiger Proxy WebUI now displays message timestamp in the menu
* TGR-534: Traffic can now be uploaded from the Tiger Proxy WebUI
* TGR-548: JEXL Debugging/RbelPath Debugging: help text is now collapsible
* TGR-547: Tiger Workflow UI now links the scenarios in the feature list section in the sidebar with the scenario and its steps shown in the central execution pane for easier navigation
* TGR-564: Show timestampes for loaded messages too, some optimizations to reduce vertical spacing in rbel log report. Saving traffic data now is stored in a file with it's name containing the current date
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

* TGR-487: glue code "TGR current response at {string} matches as {word}:" now accepts an enums (JSON|XML) instead of strings and deprecated glue code "TGR current response at {string} matches as {word}" is removed
* TGR-493: The Test library no longer produces a test jar as we moved all BDD stuff to src/main, thus including the tiger-test-lib jar will suffice. Please adapt your pom.xml to **not any more depend on tiger-test-lib test-jar**

## Features

* TGR-510: The CipherSuites which are used by the TigerProxy as a TLS client are now configurable

## Bugfixes

* TGR-553: Fixed race-condition during initial connection to remote tiger-proxies
* TGR-553: Fixed Race-Condition for multiple configured traffic sources
* TGR-553: Major resilience overhaul for reconnection in TigerProxy-Mesh-Setups

-------
# Release 0.23.0

## Breaking changes

* TGR-483 rbel validator steps using a doc string as parameter and not ending with a colon ":" are marked deprecated and are replaced with a correct version ending with ":" The old version will be removed with 0.23.1

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

* TGR-483 adding new steps to RbelValidator, allowing to check for existence of node in request / response and allowing to check for mismatching node in response

-------
# Release 0.22.2

## Bugfixes

* TGR-523: Connection-lost issues for Tiger Proxy mesh-setups fixed.

-------
# Release 0.22.1

## Breaking Changes
* Tiger maven plugin now purges the target/generated-test-sources/tigerbdd folder to avoid left over driver class files from previous runs.
* Tiger maven plugin will by default only search for feature files in src/test/resources/features folder tree from now on, to avoid duplicate feature files also found in target subfolders.

## Features
* A new step "TGR pause test run execution" will pause the execution and if workflow UI is active will display a nice banner with a continue button. Without workflow UI you can enter "next" in the console and press ENTER to continue test execution

## Bugfixes
* TGR-516 "TGR wait for user abort" is now also working when workflow UI is disabled. You must have a console like environment like Bash or GitBash to be able to enter "quit" and press Enter
* TGR-519: Fixed propagation in aggregating tiger proxies
* TGR-521: Race-Condition fixed for heavy-duty TigerProxy settings

## Features
* TGR-236: Logs of Tiger server instances are now also written to server specific log files

-------
# Release 0.21.0

## Breaking Changes
* TGR-480: Healthchecks for docker compose services are not any more supported
* TGR-480: Upgrade testcontainers 1.17.0, exposed ports behaviour has changed
* TGR-513: The way tests are started and teared down have changed to prepare for workflow UI landing. The quit step is not functional at the moment, but the workflow UI will pause before shutdown and show a quit button to finish the test run (shutting down the test env). If no workflow UI is active the test will finish normally.
* TGR-248: removed OSEnvironment class, functionality is replaced by TigerGlobalConfiguration

## Features
* TGR-482: TGR findLastRequest added in the glue code

## Bugfixes
* TGR-480: docker compose is not working, in the fix a complete rewrite of the compose code part has been done, dropping the healthchecks property.

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
* TGR-503: The Testenv-Mgr now by default uses the own JVM to start externalJar-Servers. This can be overriden by setting `tiger.lib.javaHome`
* TGR-508: Remote Tiger-Proxies can now also be supplied with TLS-Identities

-------
# Release 0.20.1

## Breaking Changes

* RBEL-54: RbelElement now returns RbelMultiMap instead of List<Entry<String, RbelElement>>. Conceptually nothing changed
* TGR-469: clean up the yaml file
  * serverPort is renamed in adminPort
  * port is renamed to proxyPort
  * proxyCfg under tigerProxyCfg is omitted

## Features
* TGR-461: Smaller improvements in TigerGlobalConfiguration:
  * `TigerGlobalConfiguration.localScope()` allows scoped value addition
  * Placeholders in keys are now resolved
  * `TigerGlobalConfiguration.putValue()` now resolves nested values from objects
  * instantiateConfigurationBean now returns an optional, allowing greater control when key was not found in configuration.
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
  ...
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
