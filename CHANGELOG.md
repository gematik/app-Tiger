# Changelog Tiger Testplattform

## Release 0.16.0

## Features
* TGR-136 Client-Adressen werden nun in Rbel-Nachrichten korrekt gesetzt

## Release 0.15.0

### Features
* TGR-136 Client-Adressen werden nun in Rbel-Nachrichten korrekt gesetzt
* TGR-186 First version of an UI test run monitor, displaying all banner and text messages to guide manual testers.
* TGR-136 Client-Adressen werden nun in Rbel-Nachrichten korrekt gesetzt

### Breaking Changes

### Bugfixes
* TGR-183 German keyword "Gegeben sei" was not correctly detected by FeatureParser
* TGR-41  Competing routes are now correctly identified and refused when adding
* TGR-179 TGR Step "show color text" failed with unknown color name

## Release 0.14.0

### Neues

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

### Änderungen

* **BREAKING** TGR-87 Die Serverliste im tiger-testenv.yml wurde angepasst. Das Attribut 'name' wurde entfernt und durch das optionale Attribut 'hostname' ersetzt.
  Sollte 'hostname' nicht definiert werden, wird es auf den Keywert des Mapeintrages gesetzt. Diese Änderung bedeutet, dass zwar der Hostname bei mehreren
  Servereinträgen identisch sein kann, allerdings muss der Keywert **eindeutig** sein.
  Details zu der Migration befinden sich weiter unten.
* 
### Entfernt

* TGR-173 Die Ausgabe der Testschritte erfolgt nun nicht mehr über Tiger, sondern kann im [serenity.properties](https://serenity-bdd.github.io/theserenitybook/latest/serenity-system-properties.html)  
  über serenity.logging=VERBOSE aktiviert werden.

### Fehlerbehebungen

* TGR-159 Null TLS attribute in tiger-testenv.yml führten zu Startabbruch
* TGR-166 Concurrent Modification Exceptions traten im Bereich Tiger Proxy Nachrichten auf

### Migrationsdetails

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
