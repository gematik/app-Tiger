# Changelog Tiger Testplattform

# Release 0.17.0

## Breaking Changes
* TGR-208: The startup-order of the servers is now ONLY determined by the "dependsUpon"-Flag. This can give a 
 comma separated list of servers that need to be started prior to the given server. Please review & change all 
 testenv.ymls accordingly.

## Features
* TGR-208: Refactoring Testenv-Mgr
* TGR-208: Startup of services by Testenv-Mgr can now be parallel and in given sequence (dependsUpon-Flag added)
* TGR-218 added REST-Interface for Modifications
* TGR-96: support basic auth for forward proxy

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
* TGR-41  Competing routes are now correctly identified and refused when adding
* TGR-179 TGR Step "show color text" failed with unknown color name

# Release 0.14.0

## Neues

* TGR-173 Die TGR BDD Testschritte stehen nun auch auf Deutsch zur Verfügung
* TGR-131 Der RbelPath Executor unterstützt nun einen Debug Modus um bei fehlerhaften RbelPath Ausdrücken 
  die Fehlersuche zu erleichtern. [Mehr Details...](doc/testlib-config.md)
* TGR-133 Release des mvn plugins um die Generierung der Treiberklassen für die Serenity tests auch in nicht englischer
  Sprache zu unterstützen. [Mehr Details...](tiger-driver-generator-maven-plugin/README.md)
* TGR-165 EPA VAU Schlüssel ist fest im Tiger Proxy hinterlegt
* TGR-168 Proxy modifications unterstützt nun auch Query Parameter modifications
* TGR-112 Dokumentation für Modifications Feature [Mehr Details...](tiger-standalone-proxy/README.md)
* TGR-63  Exceptions, die in einem Upstream Tiger-Proxy auftreten werden über die WS-Schnittstelle an downstream Proxies
  kommuniziert.

## Änderungen

* **BREAKING** TGR-87 Die Serverliste im tiger-testenv.yml wurde angepasst. Das Attribut 'name' wurde entfernt und durch das optionale Attribut 'hostname' ersetzt.
  Sollte 'hostname' nicht definiert werden, wird es auf den Keywert des Mapeintrages gesetzt. Diese Änderung bedeutet, dass zwar der Hostname bei mehreren
  Servereinträgen identisch sein kann, allerdings muss der Keywert **eindeutig** sein.
  Details zu der Migration befinden sich weiter unten.

## Entfernt

* TGR-173 Die Ausgabe der Testschritte erfolgt nun nicht mehr über Tiger, sondern kann im [serenity.properties](https://serenity-bdd.github.io/theserenitybook/latest/serenity-system-properties.html)  
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
Bug fix Release
Lokale Resources fix (routeModal.html)
Messagelist index fixed im Webui
WorkingDir wird automatisch angelegt

# Release 0.10.0
* Memory-Leak fixed
* UI Sequence-numbers fixed
* Manual Update-Bug fixed

# Release 0.9.0
* Longer chains are supported for Tiger-Proxy TSL-Identities
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
