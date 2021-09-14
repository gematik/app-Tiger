# Tiger Testenvironment Configuration

## Server Types

Die folgenden Typen werden derzeit unterstützt:

* docker
* externalJar
* tigerProxy
* externalUrl
* compose

Um nach dem Studium dieser Seite konkrete Beispiele einzusehen, verwenden Sie am Besten das [templates.yaml](../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml),
welches eine Liste von einfach zu verwendenden Knoten zur Verfügung stellt. 

## Docker Knoten

Um einen Docker Container zu instantieren muss folgende Konfiguration angegeben werden:

Attribtue in Klammern (Attribute: Wert) sind optional und haben einen Defaultwert, oder sind für diesen Knotentyp nicht relevant

```
  - name: dockerNode1
    type: docker
    
    source:
      - Adresse von der das Docker image gepullt werden soll z.B. gstopdr1.top.local/idp/idp-server
    (version: EMPTY )  Optionale Angabe einer Version für das Docker Image 
    
    (startupTimeoutSec: 20 ) Timeout in Sekunden um dem Knoten einen erfolgreichen Start zuzugestehen
    
    dockerOptions:
      (serviceHealthchecks: IGNORE for docker nodes)
      (proxied: true ) - Flag ob der Enrtypoint des zu startenden Container angepasst werden soll um TigerProxy Zertifikate in das OS des Containers einzuspielen und einen docker.host.internal Eintrag zu /etc/hosts einzufügen
      (oneShot: false ) - Flag ob eine one shot Strategie für den Health check des Containers verwendet werden soll 
      (entryPoint: EMPTY ) wenn gesetzt überschreibt dieser Eintrag den Entrypoint des zu startenden Containers
    
    (pkiKeys: [] ) Liste von Zertifikaten und Schlüsseln die dem RbelLogger des lokalen Tiger proxies zur Verfügung gestellt werden sollen 
    
    (environment: [] ) Liste von Umgebungsvariablen die an den zu startenden Container weitergeleitet werden sollen. Jeder Eintrag hat die Form: KEY=VALUE
    
    (urlMappings: [] ) Liste von URL mappings welche zum lokalen Tiger proxy hinzugefügt werden sollen. z.B. - http://tsl --> https://download-ref.tsl.ti-dienste.de
    
    (exports: [] ) Liste von System Properties, welche in allen folgenden Knoten gesetzt werden. Jeder Eintrag hat die Form: KEY=VALUE
```

## Tiger Proxy Knoten

```
  - name: tigerProxyNode1
    type: tigerProxy
    
    (source: 
      - nexus )  Ein Wert aus nexus, maven oder einer spezifischen URL von welcher das Tiger Standalone Proxy Jar runtergeladen werden soll
    version: 0.9.0-181
    (startupTimeoutSec: 20 ) Timeout in Sekunden um dem Knoten einen erfolgreichen Start zuzugestehen
   
    externalJarOptions:
      (workingDir: Betriebssystemspezifisches temporäres Verzeichnis ) In dieses Verzeichnis wird das JAR gespeichert und von dort aus auch ausgeführt
      (healthcheck: EMPTY ) Wird automatisch auf den Host http://127.0.0.1 und den Port des serverPort Eintrages gesetzt
      (options: [] ) Nicht benutzt, wird durch den Code überschrieben
      (arguments: [] ) Kann verwendet werden, wird um den spring boot profile parameter ergänzt, als aktives Profil wird der Knotenname verwendet
    
    tigerProxyCfg:
      serverPort: xxxx - Port an dem die Weboberfläche verfügbar ist
      (proxiedServer: node name ) Wenn gesetzt wird der Proxy als reverse Proxy konfiguriert und die Routen im proxyCfg so eingetragen, dass der Verkehr zum gegebenen Knoten weitergeleitet wird 
      (proxyPort: RANDOM ) Port zu dem Proxy Anfragen geschickt werden sollen. Wenn nicht gesetzt wird ein zufälliger Port verwendet. 
      (proxyProtocal: http )
      proxyCfg:
        **TODO** Siehe Konfiguration eines Tigerproxy
   
    (pkiKeys: [] ) Liste von Zertifikaten und Schlüsseln die dem RbelLogger des lokalen Tiger proxies zur Verfügung gestellt werden sollen 
    
    (environment: [] ) Liste von Umgebungsvariablen die an den zu startenden Container weitergeleitet werden sollen. Jeder Eintrag hat die Form: KEY=VALUE
    
    (urlMappings: [] ) Liste von URL mappings welche zum lokalen Tiger proxy hinzugefügt werden sollen. z.B. - http://tsl --> https://download-ref.tsl.ti-dienste.de
    
    (exports: [] ) Liste von System Properties, welche in allen folgenden Knoten gesetzt werden. Jeder Eintrag hat die Form: KEY=VALUE
```

## Externe URL Knoten

```
  - name: erxternalUrlNode1
    type: externalUrl
    
    source:
      - URL des externen Servers
    (startupTimeoutSec: 20 ) Timeout in Sekunden um dem Knoten einen erfolgreichen Start zuzugestehen
    
    externalJarOptions:
      healthcheck: URL am externen Knoten, an dem ein erfolgreicher Start abgelesen werden kann (es kommt eine Antwort auf eine Anfrage, der Status wird nicht geprüft)
      alle anderen Attribute werden nicht verwendet

    (pkiKeys: [] ) Liste von Zertifikaten und Schlüsseln die dem RbelLogger des lokalen Tiger proxies zur Verfügung gestellt werden sollen 
    
    (environment: [] ) Liste von Umgebungsvariablen die an den zu startenden Container weitergeleitet werden sollen. Jeder Eintrag hat die Form: KEY=VALUE
    
    (urlMappings: [] ) Liste von URL mappings welche zum lokalen Tiger proxy hinzugefügt werden sollen. z.B. - http://tsl --> https://download-ref.tsl.ti-dienste.de
    
    (exports: [] ) Liste von System Properties, welche in allen folgenden Knoten gesetzt werden. Jeder Eintrag hat die Form: KEY=VALUE
```

## Externer Jar Knoten

```
  - name: erxternalJarNode1
    type: externalJar
    
    source:
      - URL von der das Jar Archive geladen werden soll
    (startupTimeoutSec: 20 ) Timeout in Sekunden um dem Knoten einen erfolgreichen Start zuzugestehen
    
    externalJarOptions:
      healthcheck: URL am externen Knoten, an dem ein erfolgreicher Start abgelesen werden kann (es kommt eine Antwort auf eine Anfrage, der Status wird nicht geprüft)
      (workingDir: Betriebssystemspezifisches temporäres Verzeichnis ) In dieses Verzeichnis wird das JAR gespeichert und von dort aus auch ausgeführt
      (options: [] ) Optionen für die Ausführung des Java programmes
      (arguments: [] ) Argumente welche beim Aufruf des Jar Archives verwendet werden sollen


    (pkiKeys: [] ) Liste von Zertifikaten und Schlüsseln die dem RbelLogger des lokalen Tiger proxies zur Verfügung gestellt werden sollen 
    
    (environment: [] ) Liste von Umgebungsvariablen die an den zu startenden Container weitergeleitet werden sollen. Jeder Eintrag hat die Form: KEY=VALUE
    
    (urlMappings: [] ) Liste von URL mappings welche zum lokalen Tiger proxy hinzugefügt werden sollen. z.B. - http://tsl --> https://download-ref.tsl.ti-dienste.de
    
    (exports: [] ) Liste von System Properties, welche in allen folgenden Knoten gesetzt werden. Jeder Eintrag hat die Form: KEY=VALUE
```

## Docker compose Knoten

Der Docker compose Knoten ist sehr speziell und derzeit nur für die EPA2 Module des EPA Fachdienstes verfügbar.
Als Nächstes ist Unterstützung für das Demis Meldeportal in der Planung.

```
  - name: epa
    template: epa2
    serviceHealthchecks: sind entsprechend den konfigureirten Ports anzupassen
      - http://epa-gateway:8001/
      - http://epa-docv-fdv:8005/
```

## Weitere Informationen

### Token- / Variablenersetzung

Einträge in der exports Liste eines Knoten werden geparsed und folgende Tokens werden ersetzt:
* ${PORT:xxxx} to be replaced with the port on the docker host interface
* ${NAME} to be replaced with the name of the node

Alle exports Einträge werden gesammelt und für die Token/Variablenersetzung bei Attributen von nachfolgenden Knoten verwendet:

Docker Knoten:
* source Liste, erster Eintrag - Name des zu pullenden Docker images
* environment Liste für den Docekr Container

Tiger Proxy Knoten:
* from/to Routen Urls

Externer Url Knoten:
* source Liste, erster Eintrag - URL des externen Servers

Externer Jar Knoten:
* options Liste

### pkiKeys Einträge

Die PkiKeys Liste beinhaltet eine Liste von Zertifikaten und Schlüsseln die folgendermaßen definiert werden können:

```
  pkiKeys:
    - id: disc_sig
      type: cert
      pem: "MIICsTCCAligAwIBAgIHA61I5ACUjTAKBggqhkjOPQQDAjCBhDELMAkGA1UEBhMC
  REUxHzAdBgNVBAoMFmdlbWF0aWsgR21iSCBOT1QtVkFMSUQxMjAwBgNVBAsMKUtv
  bXBvbmVudGVuLUNBIGRlciBUZWxlbWF0aWtpbmZyYXN0cnVrdHVyMSAwHgYDVQQD
  DBdHRU0uS09NUC1DQTEwIFRFU1QtT05MWTAeFw0yMDA4MDQwMDAwMDBaFw0yNTA4
  MDQyMzU5NTlaMEkxCzAJBgNVBAYTAkRFMSYwJAYDVQQKDB1nZW1hdGlrIFRFU1Qt
  T05MWSAtIE5PVC1WQUxJRDESMBAGA1UEAwwJSURQIFNpZyAxMFowFAYHKoZIzj0C
  AQYJKyQDAwIIAQEHA0IABJZQrG1NWxIB3kz/6Z2zojlkJqN3vJXZ3EZnJ6JXTXw5
  ZDFZ5XjwWmtgfomv3VOV7qzI5ycUSJysMWDEu3mqRcajge0wgeowHQYDVR0OBBYE
  FJ8DVLAZWT+BlojTD4MT/Na+ES8YMDgGCCsGAQUFBwEBBCwwKjAoBggrBgEFBQcw
  AYYcaHR0cDovL2VoY2EuZ2VtYXRpay5kZS9vY3NwLzAMBgNVHRMBAf8EAjAAMCEG
  A1UdIAQaMBgwCgYIKoIUAEwEgUswCgYIKoIUAEwEgSMwHwYDVR0jBBgwFoAUKPD4
  5qnId8xDRduartc6g6wOD6gwLQYFKyQIAwMEJDAiMCAwHjAcMBowDAwKSURQLURp
  ZW5zdDAKBggqghQATASCBDAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDRwAw
  RAIgVBPhAwyX8HAVH0O0b3+VazpBAWkQNjkEVRkv+EYX1e8CIFdn4O+nivM+XVi9
  xiKK4dW1R7MD334OpOPTFjeEhIVV"
    - id: disc_enc
      type: key
      pem: "ISUADOGBESBXEZOBXWEDHBXOU..."
```
