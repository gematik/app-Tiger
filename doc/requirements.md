# Requirements

## SSL-Handshake

* Urte/Mirko welche Informationen werden gebraucht
* Welche Server-Zertifikate brauchen wir? (=> Use-Cases)
    * eventuelle Client-Zertifikate

## SSL-Handshake protokollieren

* Urte/Mirko fragen was gebraucht wird

# Notwendige Komponten

## Tiger-Proxy

* Wiremock-Capture
* RBel-Log
* RBel-Messaging-Interface (lokale-impl, netzwerk-impl)
* Echter Proxy-Server, Konfigurierbar welche URL zu welchem Produkt gehört
* Dynamische Anpassung?

## Basic Tiger-BDD Suite

### Testenvironment Verwaltung

* Reverse Proxy Konfiguration
    * SSL-Server-Zertifikate hinterlegen
    * Dynamischer Lookup? Zb. a-priori matching bekannte URLs zu bekannten Zertifikaten
* Docker Container hochreißen
* Bei anderen BDD-Komponenten Infos über Umgebung hinterlegen

### RBeL

* RBeL-Messaging-Interface (lokale-impl, netzwerk-impl)
* RBeL-Assertions
