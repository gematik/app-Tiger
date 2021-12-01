# A Layman's guide to tiger Proxy

### Setting:

Eine Java-Testsuite möchte als Client einen Server testen. Client und Server kommunizieren dabei über http(s) oder über ein Protokoll, das auf http aufstetz (SOAP, FHIR, etc).

### Ziel:

Der Netzwerkverkehr soll über einen Proxy laufen und der Proxy soll den Verkehr auf http-Ebene mitschneiden. Zusätzliche Ziele könnten die Aufbereitung, Darstellung oder Modifikation der Daten sowie das Aufbrechen von 
Application-Layer-Verschlüsselungen sein.

### Was ist vor der Ausführung der Testsuite zu tun:

* Entwickler der Testsuite bindet *tiger-test-lib* ein
* Entwickler der Testsuite legt eine *tiger-testenv.yaml* an

### Was passiert wärend der Ausführung:

* TigerTestEnvMgr (aus tiger-test-lib) liest *tiger-testenv.yaml* mit proxy settings
* TigerTestEnvMgr startet lokalen tiger proxy auf *localhost:\<freier Port\>* und merkt sich *\<freier Port\>*
* TigerEnvironmentConfigurator (aus *tiger-test-lib*) setzt SystemProperties `http(s).proxyHost=localhost` und `http(s).proxyPort = <freier Port>`
* die Java-Testsuite kennt jetzt den Proxy und sendet http(s) über diesen

### Aber was ist mit SSL:

* Wenn der Server eine Client-Auth sehen will, muss ein p12-File für den Proxy hinterlegt werden (über *tiger-testenv.yaml* angegeben, siehe *tiger-standalone-proxy\README.md* für die Konfiguration)
* Wenn die Testsuite das Serverzertifikat und den FQDN prüfen will, dann muss man das deaktivieren (oder Arbeit ins faken stecken)   