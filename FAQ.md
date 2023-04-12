# Frequently asked questions

## Themen

* [Maven](#maven)
* [Extensions](#extensions)
* [Workflow UI](#workflow-ui)

## Maven

### Welches Serenity benutzen wir aktuell
Das zu jeder Tiger Version kompatible Serenity findet ihr in den [ReleaseNotes](ReleaseNotes.md)

### Bei der Nutzung von maven, werden keine Tests ausgeführt

Bitte stell zuerst sicher, dass entweder das surefire oder das failsafe plugin aktiviert ist und auch in der Konsole als ausgeführt angezeigt wird. Solltest Du Junit4 Test Annotationen verwenden so musst Du noch sicherstellen, dass die junit vintage engine aus der Junit5 Library in den dependencies mit angeführt ist.

```xml
<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <version>${version.junit5}</version>
</dependency>
```


### Beim Ausführen von Tests im Tiger bricht der Testlauf mit einem java.lang.NoSuchMethodError ab

Genauer geht es um folgenden Fehler:
```java
Exception in thread "main" java.lang.NoSuchMethodError: 'java.util.Set org.json.JSONObject.keySet()'
```
    
Der Grund hierfür ist ein Dependency Konflikt und kann durch eine Exklusion in der tiger-test-lib dependency aufgelöst werden:
```xml
<exclusion>
    <groupId>com.vaadin.external.google</groupId>
    <artifactId>android-json</artifactId>
</exclusion>
```

### Ich sehe keine Log-Ausgabe, lediglich am Anfang stehen Warnungen über veraltete Versionen

Du hast anscheinend Dependencies zu SLF4J V2 eingebunden.
Wir verwenden derzeit den logback classic 1.2.x branch, da dieser in der von uns verwendeten Spring Boot Version mitgeliefert wird. Dieses ist NICHT kompatibel zu SLF4J 2.x.x!  

### Wenn ich in meinem Projekt Spring Boot und Tiger mit Selenium nutzen will, gibt es Versionskonflikte bei Selenium

Spring Boot liefert eine veraltete Version von Selenium aus. Um die Konflikte zu lösen, bitte die in den ReleaseNotes angeführten Versionen über DependencyManagement im maven pom.xml lösen.

## Extensions

### Wenn ich in der tiger.yaml ein Docker image starten will, so schlägt der Startup des TestenvironmentManagers fehl.
Stelle sicher, dass du die tiger-cloud-extension in der aktuellsten Version als dependency hinzugefügt hast.

```xml
<dependency>
    <groupId>de.gematik</groupId>
    <artifactId>tiger-cloud-extension</artifactId>
    <version>x.y.z</version>
</dependency>
```

## Workflow UI

### In der Workflow UI sind die Szenarios doppelt aufgeführt und werden auch zeitgleich aktualisiert (es scheint, als ob sie parallel ablaufen)
Passiert eigentlich nur, wenn die Testsuite aus Intellij gestartet wurde und in der RuntimeConfiguration der TigerCucumberListener als plugin mitgegeben wird. Dies ist seit v1.3 nicht mehr notwendig, weil der Listener automatisch hinzugefügt wird. Durch den manuellen Eintrag laufen also dann zwei Listener, welche die Szenarien dann auch doppelt an die Workflow UI kommunizieren ...
Sollte dieser Effekt auch bei einem mvn call auftreten, dann bitte die Konfiguration des tiger-maven-plugins überprüfen, bzw. die generierten Treiberklassen bezüglich zusätzlicher Plugins in den CucumberOptions checken.

### Nachdem ich auf Quit in der Workflow UI gedrückt habe, kann ich die Nachrichten in der RbelLog Details Pane nicht mehr ansehen

Durch das Beenden des Testlaufs ist das Backend der Workflow UI auch beendet worden. Dies kannst Du auch daran erkennen, dass die linke Seitenleiste nun blass rot eingefärbt ist. Das Navigieren in der RbelLog Details Pane benötigt aber das Backend und klappt daher zum jetzigen Zeitpunkt nicht mehr. Auch die RbelPath- und JEXL Inspect Dialoge sind nicht mehr funktional.
