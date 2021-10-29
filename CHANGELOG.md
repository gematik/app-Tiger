# Changelog Tiger Testplattform

## Release 0.14.0

### Neues

* TGR-133 Release des mvn plugins um die Generierung der Treiberklassen für die Serenity tests auch in nicht englischer
  Sprache zu unterstützen. [Mehr Details...](tiger-driver-generator-maven-plugin/README.md)
* TGR-165 EPA VAU Schlüssel ist fest im Tiger Proxy hinterlegt
* TGR-168 Proxy modifications unterstützt nun auch Query Parameter modifications
* TGR-112 Dokumentation für Modifications Feature [Mehr Details...](tiger-standalone-proxy/README.md)

### Änderungen

* **BREAKING** TGR-87 Die Serverliste im tiger-testenv.yml wurde angepasst. Das Attribut 'name' wurde entfernt und durch das optionale Attribut 'hostname' ersetzt.
  Sollte 'hostname' nicht definiert werden, wird es auf den Keywert des Mapeintrages gesetzt. Diese Änderung bedeutet, dass zwar der Hostname bei mehreren
  Servereinträgen identisch sein kann, allerdings muss der Keywert **eindeutig** sein.
  Details zu der Migration befinden sich weiter unten.


### Entfernt

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
