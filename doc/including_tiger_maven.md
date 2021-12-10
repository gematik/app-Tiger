# Tiger in einem ![](images/mvn-logo.png) Projekt nutzen

Um Tiger in ein Projekt Maven einzubinden sind folgende Schritte notwendig:

* Tiger Abhängigkeiten zum Projekt hinzufügen
* JUnit4 Treiberklasse für die Feature Dateien im Projekt erstellen
* Maven Failsafe Plugin konfigurieren
* Maven Serenity Plugin konfigurieren

[Ein Beispiel pom.xml findet sich im examples Verzeichnis](examples/tigerOnly/pom.xml)

## Abhängigkeiten

Wichtig ist hierbei, dass Tiger ZWEIFACH eingebunden wird. Die erste Abhängigkeit ist zum Test jar Artefakt und
ermöglicht die Nutzung der TGR steps in Feature Dateien.

Das zweite Jar beinhaltet Hilfsklassen und die Cucumber Tiger Hooks Klasse.

```xml

<project>
    ...
    <properties>
        <!-- Entweder von maven central:
          https://mvnrepository.com/artifact/de.gematik.test/tiger-test-lib
          oder vom Gematik internen Nexus
          https://build.top.local/nexus/#nexus-search;quick~tiger-test-lib
        -->
        <version.tiger>0.12.0</version.tiger>
        <version.serenity.core>2.4.34</version.serenity.core>
        <version.serenity.maven.plugin>2.4.34</version.serenity.maven.plugin>
    </properties>

    <dependencies>
        <dependency>
            <groupId>de.gematik.test</groupId>
            <artifactId>tiger-test-lib</artifactId>
            <version>${version.tiger}</version>
            <type>test-jar</type>
            <exclusions>
                <exclusion>
                    <groupId>de.gematik</groupId>
                    <artifactId>rbelLogger</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>de.gematik.test</groupId>
            <artifactId>tiger-test-lib</artifactId>
            <version>${version.tiger}</version>
            <exclusions>
                <exclusion>
                    <groupId>de.gematik</groupId>
                    <artifactId>rbelLogger</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
    ...
</project>
```

## JUnit4 Treiberklasse(n)

Serenity unterstützt derzeit noch KEIN Junit5, weswegen für das Starten der Tigertestsuiten eine JUnit4 Treiberklasse
erstellt werden.

Bei Projekten wo auch JUnit5 verwendet wird, muss zusätzlich noch eine dependency zur Junit5 vintage engine hinzugefügt
werden, da sonst die JUnit4 Testtreiber nicht gefunden bzw. ausgeführt werden und ein leerer Serenity Report erstellt
wird.

```xml

<dependency>
    <groupId>org.junit.vintage</groupId>
    <artifactId>junit-vintage-engine</artifactId>
    <version>5.8.1</version>
</dependency>

```

### Manuell eine Treiberklasse für jedes Featurefile anlegen

```java
package de.gematik.test.tiger.integration.YOURPROJECT;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"src/test/resources/features/YOURFEATURE.feature"},
    plugin = {"json:target/cucumber-parallel/1.json"},
    monochrome = false,
    glue = {"de.gematik.test.tiger.hooks", "de.gematik.test.tiger.glue",
        "ANY ADDITIONAL PACKAGES containing GLUE or HOOKS code"})
public class Parallel1IT {

}
```

### oder dynamisch zur Laufzeit erzeugen

Unter Verwendung des tiger-bdd-driver-generator-maven-plugin plugins können die pro Feature Datei notwendigen Testtreiberklassen auch
automatisiert erstellt werden.

Hierfür muss im pom.xml das folgende Plugin eingefügt werden. Im Glue Segemnt sind alle projektspezifischen
weiteren packages hinzuzufügen und der Wert beim Attribute packageName ist entsprechend anzupassen.

```xml
<plugin>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-bdd-driver-generator-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <configuration>
                <!-- mandatory -->
                <includes>
                    <include>**/*.feature</include>
                </includes>
                <glues>
                    <glue>de.gematik.test.tiger.admin.bdd.steps</glue>
                </glues>
                <!-- optional -->
                <driverPackage>de.gematik.test.tiger.admin.bdd.drivers</driverPackage>
                <!-- optional -->
                <driverClassName>Parallel${ctr}IT</driverClassName>
                <!-- optional --> 
                <!-- <skip>false</skip> -->
                <!-- optional -->
                <!-- <templateFile>tiger-admin/src/test/jtmpl/SpringBootDriver.jtmpl</templateFile>-->
                <!-- optional -->
                <!-- <basedir>${project.basedir}/src/test/resources/features</basedir>-->
            </configuration>
            <phase>generate-test-sources</phase>
            <id>default-testSources</id>
            <goals>
                <goal>generate-drivers</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Maven failsafe plugin (deprecated)

Dieses Plugin triggert nun durch Angabe des entsprechenden Include Filters das Ausführen der JUnit4 Testtreiberklassen.

```xml

<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <groupId>org.apache.maven.plugins</groupId>
    <version>3.0.0-M5</version>
    <executions>
        <execution>
            <id>run-tests</id>
            <phase>integration-test</phase>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
            <configuration>
                <includes>
                    <include>**/Parallel*IT.java</include>
                    <!-- Diesen Ausdruck an die im Projekt verwendete Bezeichnung der Treiberklassen anpassen -->
                </includes>
                <environmentVariables>
                    <TIGER_ACTIVE>1</TIGER_ACTIVE>
                    <!-- Dieses Umgebungsvariable triggert die Hooks der Tiger Test Lib um die Testumgebung hochzufahren -->
                    <!-- und die Tiger Proxies zu aktivieren -->
                </environmentVariables>
                <skipITs>false</skipITs>
                <forkMode>never</forkMode>
                <!-- Um eine verspätete Ausgabe der Logs aus den externen Jar Knoten zu verhinden -->
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Maven surefire plugin (preferred)

Dieses Plugin triggert nun durch Angabe des entsprechenden Include Filters das Ausführen der JUnit4 Testtreiberklassen.
Durch das Setzen des testFailureIgnore flags, wird der maven run bei Fehlern nicht abgebrochen. Dadurch kann das nachfolgende Serenity plugin, den Bericht noch aktualisieren. Das in dem plugin definierte zweite goal (serenity:check),
bricht den maven run dann im Fehlerfall ab.

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0-M5</version>
    <configuration>
        <includes>
            <include>**/Parallel*IT.java</include>
        </includes>
        <environmentVariables>
            <TIGER_ACTIVE>1</TIGER_ACTIVE>
        </environmentVariables>
        <parallel>methods</parallel>
        <forkCount>4</forkCount>
        <useUnlimitedThreads>true</useUnlimitedThreads>
        <testFailureIgnore>true</testFailureIgnore>
    </configuration>
</plugin>
```

## Maven serenity reports plugin

Nach dem Testlauf erstellt dieses Plugin aus den Testergebnissen einen Serenity Testbericht und bricht im Fehlerfall den maven run ab.

```xml

<plugin>
    <groupId>net.serenity-bdd.maven.plugins</groupId>
    <artifactId>serenity-maven-plugin</artifactId>
    <version>${version.serenity.maven.plugin}</version>
    <dependencies>
        <dependency>
            <groupId>net.serenity-bdd</groupId>
            <artifactId>serenity-core</artifactId>
            <version>${version.serenity.core}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>serenity-reports</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>reports</goal>
                <goal>aggregate</goal>
            </goals>
        </execution>
        <execution>
            <id>serenity-check</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin> 
```
