# Changelog Tiger Testplattform

# Release 0.23.2

## Breaking changes
* TGR-487: glue code "TGR current response at {string} matches as {word}:" now accepts an enums (JSON|XML) instead of strings and deprecated glue code "TGR current response at {string} matches as {word}" is removed

## Features

* TGR-510: The CipherSuites which are used by the TigerProxy as a TLS client are now configurable

## Bugfixes

* TGR-553: Fixed race-condition during initial connection to remote tiger-proxies
* TGR-553: Fixe Race-Condition for multiple configured traffic sources

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

# Release 0.22.2

## Bugfixes

* TGR-523: Connection-lost issues for Tiger Proxy mesh-setups fixed.

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

# Release 0.19.1

## Bugfix

* Fixing module with incomplete -sources and -javadoc JAR files

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

# Release 0.18.1

## Bugfix

* Tiger admin UI allow to rename nodes to ""

## Features

* TGR-50: Tiger test environment configuration now has an admin Web UI. Check out the tiger-admin module
* TGR-306: Tiger admin UI now has "Duplicate node" feature

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

# Release 0.17.1

## Bugfix

* TGR-222: Trustmanager im AbstractExternalTigerServer lokal begrenzen

## Features

* TGR-269: Configuration for HTTPS forward proxy

## Enhancements

* TGR-250: Tests überarbeiten in TestTokenSubstituteHelper

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

# Release 0.16.4

## Bugfix

* TGR-219 bdd driver generator plugin created invalid feature path on windows

# Release 0.16.2

## Bugfix

* TGR-212 Content-Length now correctly after Rbel-Modifications

# Release 0.16.0

## Features

* TGR-136 Client-addresses are now correctly set in Rbel-messages
* TGR-120 The SSL-Suits of the servers are now configurable

# Release 0.15.0

## Features

* TGR-136 Client-addresses are now correctly set in Rbel-Messages
* TGR-186 First version of an UI test run monitor, displaying all banner and text messages to guide manual testers.

## Bugfixes

* TGR-183 German keyword "Gegeben sei" was not correctly detected by FeatureParser
* TGR-41 Competing routes are now correctly identified and refused when adding
* TGR-179 TGR Step "show color text" failed with unknown color name

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

# Release 0.13.0

* Modifications added to tiger-proxy (Documentation still outstanding)
* Fixed binary-transmission bug
* Documentation added

# Release 0.12.0

* Bugfix tiger-proxy: ASN1.Encodable parsing
* Extended TLS-configuration (now supports custom domain-names, useful for a reverse-proxy-setup)
* TLS-properties moved to tigerProxy.tls (will give error on startup if missused)
* Reverse-proxy-routes give the target-server as receiver/sender

# Release 0.11.0

Bug fix Release Lokale Resources fix (routeModal.html)
Messagelist index fixed im Webui WorkingDir wird automatisch angelegt

# Release 0.10.0

* Memory-Leak fixed
* UI Sequence-numbers fixed
* Manual Update-Bug fixed

# Release 0.9.0

* Longer chains are supported for Tiger Proxy TSL-Identities
* Chains are verified on startup
* Memory Management for tiger-standalone improved
* support for demis docker compose
* allow to use local resources
* bug fix for basic auth on reverse proxy
* proxy system prop detection in tiger director
* TigerProxy as type in TestEnv

# Release 0.8.1

Javadoc Fix

# Release 0.8.0

* Bugfix TigerRoute YAML instantiation

# Release 0.7.0

mvn deploy fix

# Release 0.5.0

mvn deploy fix

# Release 0.4.0

mvn central deploy fix

# Release 0.3.0

Initial release
